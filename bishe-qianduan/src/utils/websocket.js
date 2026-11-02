import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { getToken } from '@/utils/auth'

let client = null
const privateHandlers = new Set()
const groupHandlers = new Map()
const subscribedGroups = new Map()

function notifyPrivate(payload) {
  privateHandlers.forEach((fn) => {
    try {
      fn(payload)
    } catch (e) {
      console.error('[ws] private handler error', e)
    }
  })
}

function notifyGroup(groupId, payload) {
  const set = groupHandlers.get(String(groupId))
  if (!set) return
  set.forEach((fn) => {
    try {
      fn(payload)
    } catch (e) {
      console.error('[ws] group handler error', e)
    }
  })
}

function parseBody(message) {
  try {
    return JSON.parse(message.body)
  } catch {
    return null
  }
}

/**
 * 建立 STOMP 连接（幂等）。鉴权仅放在 STOMP CONNECT 头，避免 token 进入 URL/日志。
 */
export function connectChatWs() {
  const token = getToken()
  if (!token) return null
  if (client?.active) return client

  client = new Client({
    webSocketFactory: () => new SockJS('/ws'),
    connectHeaders: {
      Authorization: token.startsWith('Bearer ') ? token : `Bearer ${token}`,
    },
    reconnectDelay: 4000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      client.subscribe('/user/queue/chat', (message) => {
        const payload = parseBody(message)
        if (!payload) return
        notifyPrivate(payload)
        const data = payload.data
        if (payload.type === 'NEW_MESSAGE' || payload.type === 'UPDATE') {
          const gid = data?.groupId
          if (gid && Number(data?.isPrivate) !== 1) {
            notifyGroup(gid, payload)
          }
        }
      })
      client.subscribe('/user/queue/chat-ack', () => {})
      // 重连后恢复群主题订阅
      subscribedGroups.forEach((_, groupId) => {
        ensureGroupTopic(groupId)
      })
    },
    onStompError: (frame) => {
      console.error('[ws] STOMP error', frame.headers?.message || frame.body)
    },
  })

  client.activate()
  return client
}

function ensureGroupTopic(groupId) {
  if (!client?.connected || !groupId) return
  const key = String(groupId)
  if (subscribedGroups.get(key)?.unsubscribe) return
  const sub = client.subscribe(`/topic/group/${key}`, (message) => {
    const payload = parseBody(message)
    if (payload) notifyGroup(key, payload)
  })
  subscribedGroups.set(key, sub)
}

/** 订阅私聊推送（含群消息经 user queue 的副本） */
export function onChatEvent(handler) {
  privateHandlers.add(handler)
  connectChatWs()
  return () => privateHandlers.delete(handler)
}

/** 订阅指定群主题（撤回/修改主要走 topic） */
export function onGroupEvent(groupId, handler) {
  const key = String(groupId)
  if (!groupHandlers.has(key)) groupHandlers.set(key, new Set())
  groupHandlers.get(key).add(handler)
  connectChatWs()
  ensureGroupTopic(key)
  return () => {
    const set = groupHandlers.get(key)
    if (!set) return
    set.delete(handler)
    if (set.size === 0) {
      groupHandlers.delete(key)
      const sub = subscribedGroups.get(key)
      sub?.unsubscribe?.()
      subscribedGroups.delete(key)
    }
  }
}

/** 预订阅多个群主题 */
export function watchGroups(groupIds = []) {
  connectChatWs()
  groupIds.forEach((id) => ensureGroupTopic(id))
}

export function disconnectChatWs() {
  privateHandlers.clear()
  groupHandlers.clear()
  subscribedGroups.forEach((sub) => sub?.unsubscribe?.())
  subscribedGroups.clear()
  if (client) {
    client.deactivate()
    client = null
  }
}

import request from './request'

/** 发送私聊 */
export function sendPrivateMessage(data) {
  return request.post('/api/chat/private/send', data)
}

/** 私聊历史 */
export function fetchPrivateHistory(friendId) {
  return request.get(`/api/chat/private/${friendId}`)
}

/** 发送群聊 */
export function sendGroupMessage(data) {
  return request.post('/api/chat/group/send', data)
}

/** 群聊历史 */
export function fetchGroupHistory(groupId) {
  return request.get(`/api/chat/group/${groupId}`)
}

/** 撤回消息（软删除） */
export function recallMessage(messageId) {
  return request.post('/api/chat/recall', { messageId })
}

/** 群管理员修改消息内容 */
export function updateGroupMessage(data) {
  return request.post('/api/chat/group/update', data)
}

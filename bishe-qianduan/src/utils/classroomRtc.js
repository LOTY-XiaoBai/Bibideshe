import { onChatEvent, onChatConnected } from './websocket'
import { sendClassSignal, fetchIceConfig } from '@/api/classroom'

/** 每位学生一条接收连接，摄像头/共享画面独立媒体流；不采集学生设备。 */
export async function createClassroomRtc({ sessionId, teacher, teacherId, streams, onRemote, onState, onCount }) {
  const config = (await fetchIceConfig()).data
  const peers = new Map(), earlyIce = new Map()
  let closed = false, offEvent, offConnect, retryTimer, chain = Promise.resolve()
  const send = (target, kind, payload) => sendClassSignal(sessionId, target, kind, payload)
  function remove(id) {
    const old = peers.get(String(id))
    if (old) { old.pc.onconnectionstatechange = null; old.pc.onicecandidate = null; old.pc.close(); peers.delete(String(id)) }
    onCount?.([...peers.values()].filter(p => p.pc.connectionState === 'connected').length)
  }
  function peer(id, rtcId) {
    remove(id)
    const pc = new RTCPeerConnection(config), item = { pc, rtcId, outgoing: [], remoteReady: false, incoming: [], descriptionSent: false }
    peers.set(String(id), item)
    pc.onicecandidate = e => {
      if (!e.candidate || closed || peers.get(String(id)) !== item) return
      const payload = { rtcId, candidate: e.candidate.toJSON() }
      if (item.descriptionSent) send(id, 'ICE', payload).catch(() => {})
      else item.outgoing.push(payload)
    }
    pc.onconnectionstatechange = () => {
      if (closed) return
      onCount?.([...peers.values()].filter(p => p.pc.connectionState === 'connected').length)
      if (!teacher) onState?.(pc.connectionState)
      if (pc.connectionState === 'failed' || pc.connectionState === 'closed') remove(id)
    }
    return item
  }
  async function description(item, id, kind, payload) {
    await send(id, kind, { ...payload, rtcId: item.rtcId })
    item.descriptionSent = true
    for (const candidate of item.outgoing.splice(0)) await send(id, 'ICE', candidate)
  }
  async function offer(id, force = false) {
    if (closed) return
    if (!force && peers.get(String(id))?.pc.connectionState === 'connected') return
    const item = peer(id, crypto.randomUUID()), media = streams()
    for (const stream of Object.values(media).filter(Boolean)) for (const track of stream.getTracks()) if (track.readyState === 'live') item.pc.addTrack(track, stream)
    const sdp = await item.pc.createOffer()
    if (closed || peers.get(String(id)) !== item) return
    await item.pc.setLocalDescription(sdp)
    await description(item, id, 'OFFER', { sdp: item.pc.localDescription.toJSON(), cameraId: media.camera?.id, screenId: media.screen?.id })
  }
  async function receive(event) {
    if (closed || event.type !== 'CLASS_SIGNAL' || event.data?.sessionId !== sessionId) return
    const { senderId, kind, payload = {} } = event.data
    if (teacher && kind === 'JOIN') { await offer(senderId); return }
    if (teacher && kind === 'LEAVE') { remove(senderId); return }
    if (!teacher && String(senderId) !== String(teacherId)) return
    if (!teacher && kind === 'CLOSE') { remove(senderId); onRemote?.('camera', null); onRemote?.('screen', null); return }
    if (!teacher && kind === 'OFFER') {
      const item = peer(senderId, payload.rtcId)
      onRemote?.('camera', null); onRemote?.('screen', null)
      item.pc.ontrack = event => {
        if (closed || peers.get(String(senderId)) !== item) return
        const stream = event.streams[0]
        if (stream) onRemote?.(stream.id === payload.screenId ? 'screen' : 'camera', stream)
      }
      await item.pc.setRemoteDescription(payload.sdp)
      item.remoteReady = true
      for (const candidate of earlyIce.get(payload.rtcId) || []) await item.pc.addIceCandidate(candidate)
      earlyIce.delete(payload.rtcId)
      const answer = await item.pc.createAnswer(); await item.pc.setLocalDescription(answer)
      await description(item, senderId, 'ANSWER', { sdp: item.pc.localDescription.toJSON() })
      return
    }
    const item = peers.get(String(senderId))
    if (kind === 'ICE') {
      if (!item || item.rtcId !== payload.rtcId) {
        if (!earlyIce.has(payload.rtcId)) earlyIce.set(payload.rtcId, [])
        const queued = earlyIce.get(payload.rtcId)
        if (queued.length < 100) queued.push(payload.candidate)
        if (earlyIce.size > 20) earlyIce.delete(earlyIce.keys().next().value)
      } else if (item.remoteReady) await item.pc.addIceCandidate(payload.candidate)
      else item.incoming.push(payload.candidate)
    } else if (teacher && kind === 'ANSWER' && item?.rtcId === payload.rtcId) {
      await item.pc.setRemoteDescription(payload.sdp); item.remoteReady = true
      for (const candidate of item.incoming.splice(0)) await item.pc.addIceCandidate(candidate)
    }
  }
  async function join() {
    if (!closed && !teacher && !['connected', 'connecting'].includes(peers.get(String(teacherId))?.pc.connectionState)) {
      onState?.('connecting')
      try { await send(teacherId, 'JOIN', {}) } catch { onState?.('failed') }
    }
  }
  offEvent = onChatEvent(event => {
    chain = chain.then(() => receive(event)).catch(() => { if (!teacher && !closed) onState?.('failed') })
  })
  offConnect = onChatConnected(() => {
    if (teacher) { const ids = [...peers.keys()]; ids.forEach(id => remove(id)); ids.forEach(id => offer(id).catch(() => {})) }
    else { remove(teacherId); join() }
  })
  retryTimer = setInterval(join, 8000)
  return {
    async update() { for (const id of [...peers.keys()]) await offer(id, true) },
    close() {
      if (closed) return
      closed = true; clearInterval(retryTimer); offEvent?.(); offConnect?.()
      if (!teacher) send(teacherId, 'LEAVE', {}).catch(() => {})
      else for (const id of peers.keys()) send(id, 'CLOSE', {}).catch(() => {})
      for (const id of [...peers.keys()]) remove(id)
      earlyIce.clear()
    },
  }
}

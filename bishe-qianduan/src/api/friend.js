import request from './request'

/** 按用户 ID 搜索 */
export function searchUser(userId) {
  return request.get('/api/friend/search', { params: { userId } })
}

/** 按昵称/姓名搜索（同名全部列出） */
export function searchUserByNickname(nickname) {
  return request.get('/api/friend/search/nickname', { params: { nickname } })
}

/** 发起好友申请 */
export function sendFriendRequest(receiverId) {
  return request.post('/api/friend/request', { receiverId })
}

/** 收到的待处理申请 */
export function fetchReceivedRequests() {
  return request.get('/api/friend/requests/received')
}

/** 已发出的申请 */
export function fetchSentRequests() {
  return request.get('/api/friend/requests/sent')
}

/** 同意好友申请 */
export function acceptFriendRequest(requestId) {
  return request.post('/api/friend/request/accept', { requestId })
}

/** 拒绝好友申请 */
export function rejectFriendRequest(requestId) {
  return request.post('/api/friend/request/reject', { requestId })
}

/** 好友列表 */
export function fetchFriendList() {
  return request.get('/api/friend/list')
}

/** 删除好友 */
export function deleteFriend(friendId) {
  return request.post('/api/friend/delete', { friendId })
}

/**
 * 与好友成绩 PK
 * @param {number|string} friendId
 * @param {number|string} [courseId] 不传则比综合评分
 */
export function pkWithFriend(friendId, courseId) {
  const params = { friendId }
  if (courseId != null && courseId !== '') {
    params.courseId = courseId
  }
  return request.get('/api/friend/pk', { params })
}

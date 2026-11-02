import request from './request'

/** 创建群聊 */
export function createGroup(data) {
  return request.post('/api/group/create', data)
}

/** 按群号或群名搜索 */
export function searchGroup(keyword) {
  return request.get('/api/group/search', { params: { keyword } })
}

/** 我加入的群聊 */
export function fetchMyGroups() {
  return request.get('/api/group/mine')
}

/** 群聊详情 */
export function fetchGroupDetail(groupId) {
  return request.get(`/api/group/${groupId}`)
}

/** 群成员列表（含管理员等级） */
export function fetchGroupMembers(groupId) {
  return request.get(`/api/group/${groupId}/members`)
}

/** 加入群聊 */
export function joinGroup(groupId) {
  return request.post('/api/group/join', { groupId })
}

/** 退出群聊 */
export function leaveGroup(groupId) {
  return request.post('/api/group/leave', { groupId })
}

/** 解散群聊（仅群主） */
export function dissolveGroup(groupId) {
  return request.post('/api/group/dissolve', { groupId })
}

/** 修改群名/头像/简介 */
export function updateGroup(data) {
  return request.post('/api/group/update', data)
}

/** 群主任命普通管理员 */
export function appointAdmin(groupId, targetUserId) {
  return request.post('/api/group/appoint-admin', { groupId, targetUserId })
}

/** 群主撤销普通管理员 */
export function removeAdmin(groupId, targetUserId) {
  return request.post('/api/group/remove-admin', { groupId, targetUserId })
}

/** 群主/管理员移出成员，权限层级由服务端校验 */
export function removeGroupMember(groupId, targetUserId) {
  return request.post('/api/group/remove-member', { groupId, targetUserId })
}

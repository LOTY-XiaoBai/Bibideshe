import request from './request'

export function createAdmin(data) {
  return request.post('/api/admin/create', data)
}

export function fetchAdminRegisterAvailable() {
  return request.get('/api/admin/register/available')
}

export function registerAdmin(data) {
  return request.post('/api/admin/register', data)
}

export function deregisterAccount(data) {
  return request.post('/api/admin/account/deregister', data)
}

export function deleteCourse(courseId) {
  return request.post('/api/admin/course/delete', null, { params: { courseId } })
}

export function fetchPendingQuestions() {
  return request.get('/api/admin/question/pending')
}

export function auditQuestion(data) {
  return request.post('/api/admin/question/audit', data)
}

export function deleteQuestion(data) {
  return request.post('/api/admin/question/delete', data)
}

export function fetchChatRecords(page = 1, size = 20, isPrivate = null) {
  const params = { page, size }
  if (isPrivate !== null && isPrivate !== undefined && isPrivate !== '') {
    params.isPrivate = isPrivate
  }
  return request.get('/api/admin/chat/records', { params })
}

export function fetchGroupChatRecords(groupId, page = 1, size = 20) {
  return request.get('/api/admin/chat/group', { params: { groupId, page, size } })
}

export function fetchPrivateChatRecords(userA, userB, page = 1, size = 50) {
  return request.get('/api/admin/chat/private', { params: { userA, userB, page, size } })
}

export function updateChatMessage(data) {
  return request.post('/api/admin/chat/update', data)
}

export function deleteChatMessage(messageId) {
  return request.post('/api/admin/chat/delete', null, { params: { messageId } })
}

export function fetchGroupList(page = 1, size = 20) {
  return request.get('/api/admin/group/list', { params: { page, size } })
}

export function softDeleteGroup(groupId) {
  return request.post('/api/admin/group/soft-delete', null, { params: { groupId } })
}

export function banGroup(data) {
  return request.post('/api/admin/group/ban', data)
}

export function unbanGroup(groupId) {
  return request.post('/api/admin/group/unban', null, { params: { groupId } })
}

export function fetchSpecialWords() {
  return request.get('/api/admin/special-word/list')
}

export function addSpecialWord(data) {
  return request.post('/api/admin/special-word/add', data)
}

export function enableSpecialWord(wordId, enabled) {
  return request.post('/api/admin/special-word/enable', null, { params: { wordId, enabled } })
}

export function deleteSpecialWord(wordId) {
  return request.post('/api/admin/special-word/delete', null, { params: { wordId } })
}

export function fetchSpecialWordViolations(status = null, page = 1, size = 20) {
  const params = { page, size }
  if (status !== null && status !== undefined && status !== '') {
    params.status = status
  }
  return request.get('/api/admin/special-word/violations', { params })
}

export function handleSpecialWordViolation(data) {
  return request.post('/api/admin/special-word/handle', data)
}

export function banAccount(data) {
  return request.post('/api/admin/account/ban', data)
}

export function unbanAccount(accountType, accountId) {
  return request.post('/api/admin/account/unban', null, { params: { accountType, accountId } })
}

export function softDeleteAccount(accountType, accountId, reason) {
  return request.post('/api/admin/account/soft-delete', null, {
    params: { accountType, accountId, reason },
  })
}

export function fetchAdminStudents(keyword = '', page = 1, size = 10) {
  return request.get('/api/admin/account/students', { params: { keyword, page, size } })
}

export function fetchAdminTeachers(keyword = '', page = 1, size = 10) {
  return request.get('/api/admin/account/teachers', { params: { keyword, page, size } })
}

export function fetchAdminCourses(keyword = '', page = 1, size = 10) {
  return request.get('/api/admin/course/list', { params: { keyword, page, size } })
}

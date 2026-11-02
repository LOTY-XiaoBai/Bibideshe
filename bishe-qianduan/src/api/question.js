import request from './request'

/** 教师创建题目 */
export function createQuestion(data) {
  return request.post('/api/question/create', data)
}

/** 当前教师题库 */
export function fetchMyQuestions() {
  return request.get('/api/question/mine')
}

/** 公共题库（审核通过） */
export function fetchPublicQuestions() {
  return request.get('/api/question/public')
}

import request from './request'

/** 查看错题本 */
export function fetchErrorBook(studentId) {
  return request.get(`/api/error-book/${studentId}`)
}

/** 移除错题 */
export function removeErrorBookItem({ questionId, questionType }) {
  return request.delete('/api/error-book/remove', {
    data: { questionId, questionType },
  })
}

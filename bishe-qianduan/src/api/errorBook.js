import request from './request'

/** 查看错题本 */
export function fetchErrorBook(studentId) {
  return request.get(`/api/error-book/${studentId}`)
}

export function revealErrorBookAnswers(questions) {
  return request.post('/api/error-book/answers', { questions })
}

export function startErrorBookRedo(questions) {
  return request.post('/api/error-book/redo', { questions })
}

export function fetchErrorBookRedo(id) {
  return request.get(`/api/error-book/redo/${id}`)
}

export function discardErrorBookRedo(id) {
  return request.delete(`/api/error-book/redo/${id}`)
}

export function submitErrorBookRedo(id, answers) {
  return request.post(`/api/error-book/redo/${id}/submit`, { answers })
}

export function fetchErrorBookHistory() {
  return request.get('/api/error-book/redo/history')
}

export function fetchErrorBookReview(id) {
  return request.get(`/api/error-book/redo/history/${id}`)
}

/** 移除错题 */
export function removeErrorBookItem({ questionId, questionType }) {
  return request.delete('/api/error-book/remove', {
    data: { questionId, questionType },
  })
}

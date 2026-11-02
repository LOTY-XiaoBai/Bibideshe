import request from './request'

export function publishExam(data) {
  return request.post('/api/exam/publish', data)
}

export function fetchExamsByCourse(courseId) {
  return request.get(`/api/exam/course/${courseId}`)
}

export function fetchExamsByTeacher(teacherId) {
  return request.get(`/api/exam/teacher/${teacherId}`)
}

export function fetchExamDetail(examId) {
  return request.get(`/api/exam/${examId}`)
}

export function startExam(examId) {
  return request.post(`/api/exam/${examId}/start`)
}

export function submitExam(data) {
  return request.post('/api/exam/submit', data)
}

export function reviewExam(examId) {
  return request.get(`/api/exam/${examId}/review`)
}

export function fetchStudentExamRecords(studentId) {
  return request.get(`/api/exam/student/${studentId}/records`)
}

export function fetchExamPendingGrade(examId) {
  return request.get(`/api/exam/${examId}/pending-grade`)
}

export function gradeExamAnswer(data) {
  return request.post('/api/exam/grade', data)
}

export function publishExamResult(examId) {
  return request.post(`/api/exam/${examId}/publish-result`)
}

export function fetchExamScores(examId) {
  return request.get(`/api/exam/${examId}/scores`)
}

export function reviewExamForTeacher(examId, studentId) {
  return request.get(`/api/exam/${examId}/student/${studentId}/review`)
}

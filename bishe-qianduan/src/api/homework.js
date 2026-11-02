import request from './request'

export function publishHomework(data) {
  return request.post('/api/homework/publish', data)
}

export function fetchHomeworksByCourse(courseId) {
  return request.get(`/api/homework/course/${courseId}`)
}

export function fetchHomeworksByTeacher(teacherId) {
  return request.get(`/api/homework/teacher/${teacherId}`)
}

export function fetchHomeworkDetail(homeworkId) {
  return request.get(`/api/homework/${homeworkId}`)
}

export function submitHomework(data) {
  return request.post('/api/homework/submit', data)
}

export function reviewHomework(homeworkId) {
  return request.get(`/api/homework/${homeworkId}/review`)
}

export function fetchStudentHomeworkRecords(studentId) {
  return request.get(`/api/homework/student/${studentId}/records`)
}

export function fetchHomeworkPendingGrade(homeworkId) {
  return request.get(`/api/homework/${homeworkId}/pending-grade`)
}

export function gradeHomeworkAnswer(data) {
  return request.post('/api/homework/grade', data)
}

export function publishHomeworkResult(homeworkId) {
  return request.post(`/api/homework/${homeworkId}/publish-result`)
}

export function fetchHomeworkScores(homeworkId) {
  return request.get(`/api/homework/${homeworkId}/scores`)
}

export function reviewHomeworkForTeacher(homeworkId, studentId) {
  return request.get(`/api/homework/${homeworkId}/student/${studentId}/review`)
}

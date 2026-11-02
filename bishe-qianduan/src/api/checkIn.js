import request from './request'

export function publishCheckIn(data) {
  return request.post('/api/check-in/publish', data)
}

export function endCheckIn(checkInId) {
  return request.post('/api/check-in/end', null, { params: { checkInId } })
}

export function fetchCourseCheckIns(courseId, page = 1, size = 20) {
  return request.get(`/api/check-in/course/${courseId}`, { params: { page, size } })
}

export function fetchCheckInDetail(checkInId) {
  return request.get(`/api/check-in/detail/${checkInId}`)
}

export function fetchAbsentStudents(checkInId) {
  return request.get(`/api/check-in/absent/${checkInId}`)
}

export function updateCheckInStatus(data) {
  return request.post('/api/check-in/update-status', data)
}

export function signCheckIn(checkInId) {
  return request.post('/api/check-in/sign', { checkInId })
}

export function fetchStudentCheckInRecords(studentId, page = 1, size = 20) {
  return request.get(`/api/check-in/student/${studentId}/records`, { params: { page, size } })
}

export function fetchMissedByCourse(studentId, courseId) {
  return request.get(`/api/check-in/student/${studentId}/missed/${courseId}`)
}

export function fetchAllMissed(studentId, page = 1, size = 20) {
  return request.get(`/api/check-in/student/${studentId}/missed`, { params: { page, size } })
}

export function fetchMyCheckInStatus(checkInId) {
  return request.get(`/api/check-in/status/${checkInId}`)
}

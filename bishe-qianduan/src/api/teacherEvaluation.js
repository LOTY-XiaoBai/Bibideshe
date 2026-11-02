import request from './request'

/** 学生给教师打分 */
export function rateTeacher(data) {
  return request.post('/api/teacher-evaluation/rate', data)
}

/** 查看已选课程教师信息与评分 */
export function fetchCourseTeacher(courseId) {
  return request.get(`/api/teacher-evaluation/course/${courseId}`)
}

/** 可打分的课程教师列表 */
export function fetchRateableTeachers() {
  return request.get('/api/teacher-evaluation/rateable')
}

/** 查看某教师综合评分 */
export function fetchTeacherScore(teacherId) {
  return request.get(`/api/teacher-evaluation/score/${teacherId}`)
}

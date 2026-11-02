import request from './request'

/** 教师创建课程 */
export function createCourse(data) {
  return request.post('/api/course/create', data)
}

/** 教师结束课程 */
export function finishCourse(courseId) {
  return request.post('/api/course/finish', { courseId })
}

/** 教师取消结课 */
export function unfinishCourse(courseId) {
  return request.post('/api/course/unfinish', { courseId })
}

/** 教师删除课程 */
export function deleteCourse(courseId) {
  return request.post('/api/course/delete', { courseId })
}

/** 教师移除班级学生 */
export function removeStudent(courseId, studentId) {
  return request.post('/api/course/remove-student', { courseId, studentId })
}

/** 当前教师课程列表 */
export function fetchTeacherCourses() {
  return request.get('/api/course/teacher/mine')
}

/** 按课程号搜索 */
export function searchCourse(courseId) {
  return request.get('/api/course/search', { params: { courseId } })
}

/** 学生选课 */
export function joinCourse(courseId) {
  return request.post('/api/course/join', { courseId })
}

/** 学生退课 */
export function leaveCourse(courseId) {
  return request.post('/api/course/leave', { courseId })
}

/** 当前学生课程列表 */
export function fetchStudentCourses() {
  return request.get('/api/course/student/mine')
}

/** 班级成员 */
export function fetchCourseMembers(courseId) {
  return request.get(`/api/course/${courseId}/members`)
}

/** 当前学生全部课程得分 */
export function fetchMyCourseScores() {
  return request.get('/api/course/student/mine/scores')
}

/** 当前学生某门课程得分 */
export function fetchMyCourseScore(courseId) {
  return request.get('/api/course/student/mine/score', { params: { courseId } })
}

/** 班级成绩排名 */
export function fetchMyClassRank(courseId) {
  return request.get('/api/course/student/mine/rank', { params: { courseId } })
}

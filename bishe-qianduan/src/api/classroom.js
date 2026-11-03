import request from './request'

export const startClass = (courseId, mode) => request.post('/api/classroom/start', { courseId, mode })
export const activeClass = (courseId) => request.get(`/api/classroom/course/${courseId}`)
export const classroomDetail = (id) => request.get(`/api/classroom/${id}`)
export const heartbeatClass = (id) => request.post(`/api/classroom/${id}/heartbeat`, null, { silent: true })
export const endClass = (id) => request.post(`/api/classroom/${id}/end`)
export const sendClassSignal = (id, targetId, kind, payload = {}) => request.post(`/api/classroom/${id}/signal`, { targetId, kind, payload }, { silent: true })
export const fetchIceConfig = () => request.get('/api/classroom/ice-config')
export const studentActiveClasses = () => request.get('/api/classroom/student/active', { silent: true })
export const pendingCheckIns = () => request.get('/api/classroom/student/check-ins', { silent: true })
export const fetchApplications = (role) => request.get(`/api/course/applications/${role}`)
export const reviewApplication = (courseId, studentId, approve) => request.post('/api/course/applications/review', { courseId, studentId, approve })

import request from './request'
import { ROLES, detectRoleFromId } from '@/utils/role'

/**
 * 按 ID 前缀自动选择登录接口。
 * 学生 /api/student/login · 教师 /api/teacher/login · 管理员 /api/admin/login
 * forceAdmin=true 时强制走管理员登录（隐藏入口模式）。
 */
export function loginById({ id, password, forceAdmin = false, captchaKey, captchaCode }) {
  const body = { id, password, captchaKey, captchaCode }
  if (forceAdmin) {
    return request.post('/api/admin/login', body)
  }

  const role = detectRoleFromId(id)
  if (!role) {
    return Promise.reject(new Error('无法识别账号类型，请检查 ID 格式'))
  }

  const pathMap = {
    [ROLES.STUDENT]: '/api/student/login',
    [ROLES.TEACHER]: '/api/teacher/login',
    [ROLES.ADMIN]: '/api/admin/login',
  }

  return request.post(pathMap[role], body)
}

export function registerStudent(data) {
  return request.post('/api/student/register', data)
}

export function registerTeacher(data) {
  return request.post('/api/teacher/register', data)
}

export function fetchStudentProfile() {
  return request.get('/api/student/profile')
}

export function fetchTeacherProfile() {
  return request.get('/api/teacher/profile')
}

export function updateStudentProfile(data) {
  return request.put('/api/student/profile', data)
}

export function updateTeacherProfile(data) {
  return request.put('/api/teacher/profile', data)
}

export function revokeCurrentToken() {
  return request.post('/api/auth/logout')
}

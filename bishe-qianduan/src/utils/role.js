/** 角色常量，与后端 UserRole 保持一致 */
export const ROLES = {
  STUDENT: 'STUDENT',
  TEACHER: 'TEACHER',
  ADMIN: 'ADMIN',
}

/**
 * 根据登录 ID 推断角色主题。
 * 学生 ID 以 1 开头，教师以 2 开头，管理员为 admin + 数字。
 */
export function detectRoleFromId(id) {
  const value = String(id || '').trim()
  if (!value) return null
  if (/^admin\d+$/i.test(value)) return ROLES.ADMIN
  if (/^1\d*$/.test(value)) return ROLES.STUDENT
  if (/^2\d*$/.test(value)) return ROLES.TEACHER
  return null
}

export function roleHomePath(role) {
  switch (role) {
    case ROLES.STUDENT:
      return '/student/home'
    case ROLES.TEACHER:
      return '/teacher/home'
    case ROLES.ADMIN:
      return '/admin/home'
    default:
      return '/login'
  }
}

export function roleLabel(role) {
  switch (role) {
    case ROLES.STUDENT:
      return '学生'
    case ROLES.TEACHER:
      return '教师'
    case ROLES.ADMIN:
      return '管理员'
    default:
      return ''
  }
}

export function themeRoleAttr(role) {
  switch (role) {
    case ROLES.STUDENT:
      return 'student'
    case ROLES.TEACHER:
      return 'teacher'
    case ROLES.ADMIN:
      return 'admin'
    default:
      return 'student'
  }
}

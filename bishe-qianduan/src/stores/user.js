import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  getToken,
  getRole,
  getProfile,
  saveAuth,
  clearAuth,
} from '@/utils/auth'
import { ROLES, roleHomePath, themeRoleAttr } from '@/utils/role'
import {
  loginById,
  registerStudent,
  registerTeacher,
  fetchStudentProfile,
  fetchTeacherProfile,
  updateStudentProfile,
  updateTeacherProfile,
  revokeCurrentToken,
} from '@/api/auth'
import { disconnectChatWs } from '@/utils/websocket'
import { ElMessage } from 'element-plus'

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken())
  const role = ref(getRole())
  const profile = ref(getProfile())

  const isLoggedIn = computed(() => Boolean(token.value))
  const displayName = computed(() => {
    if (!profile.value) return ''
    return (
      profile.value.nickname ||
      profile.value.name ||
      profile.value.displayName ||
      profile.value.adminId ||
      ''
    )
  })
  const userId = computed(() => {
    if (!profile.value) return ''
    return (
      profile.value.merchantId ||
      profile.value.studentId ||
      profile.value.teacherId ||
      profile.value.userId ||
      profile.value.adminId ||
      ''
    )
  })
  const avatar = computed(() => profile.value?.avatar || '')
  const themeAttr = computed(() => themeRoleAttr(role.value))

  function applyAuth(payload) {
    const next = {
      token: payload.token,
      role: payload.role,
      profile: payload.profile,
    }
    token.value = next.token
    role.value = next.role
    profile.value = next.profile
    saveAuth(next)
  }

  async function login(form) {
    const res = await loginById(form)
    const profile = res.data.profile || {}
    applyAuth({
      token: res.data.token,
      role: res.data.role,
      profile: {
        ...profile,
        studentId: profile.studentId,
        teacherId: profile.teacherId,
        adminId: profile.adminId,
        nickname: profile.nickname || profile.displayName,
        name: profile.name || profile.displayName,
      },
    })
    return res.data.role === ROLES.MERCHANT && Number(profile.mustChangePassword) ? '/merchant/settings?welcome=1' : roleHomePath(res.data.role)
  }

  async function register(form) {
    const captcha = {
      captchaKey: form.captchaKey,
      captchaCode: form.captchaCode,
    }
    if (form.roleType === 'teacher') {
      const res = await registerTeacher({
        name: form.name,
        password: form.password,
        signature: form.signature || undefined,
        ...captcha,
      })
      return res
    }
    const res = await registerStudent({
      nickname: form.nickname,
      password: form.password,
      signature: form.signature || undefined,
      ...captcha,
    })
    return res
  }

  async function refreshProfile() {
    if (!token.value) return null
    if (role.value === ROLES.MERCHANT) {
      const { merchantProfile } = await import('@/api/merchant')
      profile.value = (await merchantProfile()).data
      saveAuth({ token: token.value, role: role.value, profile: profile.value })
      return profile.value
    }
    if (role.value === ROLES.STUDENT) {
      const res = await fetchStudentProfile()
      profile.value = {
        ...profile.value,
        ...res.data,
        nickname: res.data.displayName,
        studentId: res.data.userId,
      }
      saveAuth({ token: token.value, role: role.value, profile: profile.value })
      return profile.value
    }
    if (role.value === ROLES.TEACHER) {
      const res = await fetchTeacherProfile()
      profile.value = {
        ...profile.value,
        ...res.data,
        name: res.data.displayName,
        teacherId: res.data.userId,
      }
      saveAuth({ token: token.value, role: role.value, profile: profile.value })
      return profile.value
    }
    return profile.value
  }

  async function updateProfile(payload) {
    if (role.value === ROLES.STUDENT) {
      const res = await updateStudentProfile(payload)
      profile.value = {
        ...profile.value,
        ...res.data,
        nickname: res.data.displayName,
        studentId: res.data.userId,
      }
    } else if (role.value === ROLES.TEACHER) {
      const res = await updateTeacherProfile(payload)
      profile.value = {
        ...profile.value,
        ...res.data,
        name: res.data.displayName,
        teacherId: res.data.userId,
      }
    } else {
      return profile.value
    }
    saveAuth({ token: token.value, role: role.value, profile: profile.value })
    return profile.value
  }

  function logout(notify = false) {
    disconnectChatWs()
    if (token.value) {
      revokeCurrentToken(token.value).catch(() => {})
    }
    token.value = ''
    role.value = ''
    profile.value = null
    clearAuth()
    if (notify) ElMessage.success('已退出登录')
  }

  return {
    token,
    role,
    profile,
    isLoggedIn,
    displayName,
    userId,
    avatar,
    themeAttr,
    login,
    register,
    refreshProfile,
    updateProfile,
    logout,
    applyAuth,
  }
})

import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, clearAuth } from '@/utils/auth'
import { showBanDialog } from '@/utils/banDialog'

const request = axios.create({
  baseURL: '',
  timeout: 15000,
})

function redirectToLogin() {
  clearAuth()
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

function isAccountBanned(payload) {
  return (
    payload &&
    payload.code === 403 &&
    payload.data &&
    payload.data.type === 'ACCOUNT_BANNED'
  )
}

request.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && typeof res.code === 'number') {
      if (res.code === 200) {
        return res
      }
      if (isAccountBanned(res)) {
        showBanDialog(res.data)
        const err = new Error(res.message || '该账号已经被永久封禁')
        err.isBanned = true
        err.banInfo = res.data
        err.skipToast = true
        return Promise.reject(err)
      }
      const message = res.message || '请求失败'
      if (res.code === 401) {
        ElMessage.error(message)
        redirectToLogin()
        return Promise.reject(new Error(message))
      }
      ElMessage.error(message)
      return Promise.reject(new Error(message))
    }
    return res
  },
  (error) => {
    const payload = error.response?.data
    if (isAccountBanned(payload)) {
      showBanDialog(payload.data)
      clearAuth()
      const err = new Error(payload.message || '该账号已经被永久封禁')
      err.isBanned = true
      err.banInfo = payload.data
      err.skipToast = true
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
      return Promise.reject(err)
    }
    const status = error.response?.status
    const message =
      payload?.message || error.message || '网络异常，请稍后重试'
    if (status === 401) {
      redirectToLogin()
    }
    if (!error.skipToast) {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  },
)

export default request

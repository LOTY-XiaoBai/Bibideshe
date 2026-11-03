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

function staleSession(config) {
  return config?.authTokenSnapshot && config.authTokenSnapshot !== getToken()
}

request.interceptors.request.use(
  (config) => {
    const token = config.headers.Authorization?.replace(/^Bearer /, '') || getToken()
    config.authTokenSnapshot = token
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

request.interceptors.response.use(
  (response) => {
    if (staleSession(response.config)) {
      return Promise.reject(new Error('会话已结束'))
    }
    const res = response.data
    if (res && typeof res.code === 'number') {
      if (res.code === 200) {
        if (!['get','head'].includes(response.config.method) && !response.config.url?.startsWith('/api/unread')) window.dispatchEvent(new Event('unread-changed'))
        return res
      }
      if (response.config.silent || staleSession(response.config)) {
        return Promise.reject(new Error(res.message || '请求已结束'))
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
    if (error.config?.silent || staleSession(error.config)) {
      return Promise.reject(error)
    }
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

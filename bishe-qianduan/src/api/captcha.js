import request from './request'

/** 获取图形验证码 */
export function fetchCaptcha() {
  return request.get('/api/captcha')
}

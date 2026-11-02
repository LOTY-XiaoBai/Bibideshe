import request from './request'

/** 上传图片，返回 { url } */
export function uploadImage(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/api/file/upload/image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

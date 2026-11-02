import request from './request'

export function fetchUnreadWarnings() {
  return request.get('/api/warning/unread')
}

export function fetchVisibleWarnings() {
  return request.get('/api/warning/list')
}

export function markWarningRead(warningId = null) {
  return request.post('/api/warning/read', null, {
    params: warningId == null ? {} : { warningId },
  })
}

export function dismissWarning(warningId) {
  return request.post('/api/warning/dismiss', null, { params: { warningId } })
}

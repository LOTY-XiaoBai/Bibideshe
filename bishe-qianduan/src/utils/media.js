/** 将后端返回的相对资源路径转为可访问 URL */
export function resolveMediaUrl(url) {
  if (!url) return ''
  if (/^https?:\/\//i.test(url) || url.startsWith('data:') || url.startsWith('blob:')) {
    return url
  }
  if (url.startsWith('/')) return url
  return `/${url}`
}

export function defaultAvatar(name = 'U', role = 'student') {
  const bg = role === 'teacher' || String(name).startsWith('2') ? 'EE0000' : '66ccff'
  const text = encodeURIComponent(String(name).slice(0, 2) || 'U')
  return `https://placehold.co/80x80/${bg}/ffffff?text=${text}`
}

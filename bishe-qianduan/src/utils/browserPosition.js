export function getBrowserPosition() {
  if (!window.isSecureContext || !navigator.geolocation) {
    return Promise.reject(new Error('当前位置需要在 HTTPS 或 localhost 下获取'))
  }
  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(({ coords }) => {
      const { latitude, longitude, accuracy } = coords
      resolve({ latitude, longitude, accuracy })
    }, error => {
      const messages = {
        1: '定位权限未获允许，请在浏览器设置中开启后重试',
        2: '暂时无法获取当前位置，请检查设备定位设置',
        3: '获取当前位置超时，请重试',
      }
      reject(new Error(messages[error.code] || '定位失败，请重试'))
    }, { enableHighAccuracy: true, maximumAge: 0, timeout: 10000 })
  })
}

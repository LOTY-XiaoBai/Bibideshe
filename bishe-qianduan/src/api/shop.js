import request from './request'

/** 浏览在售广告商品 */
export function fetchAdList(keyword = '', filters = {}) {
  return request.get('/api/shop/ad/list', { params: { keyword, ...filters } })
}

export function fetchCatalogOptions() {
  return request.get('/api/shop/catalog-options')
}

export function shopLocationAvailable() {
  return request.get('/api/shop/location/available', { silent: true })
}

export function locateShopCity(lat, lon) {
  return request.get('/api/shop/location/city', { params: { lat, lon }, silent: true })
}

/** 广告/商品详情 */
export function fetchAdDetail(adId) {
  return request.get(`/api/shop/ad/${adId}`)
}

/** 下单购买 */
export function createOrder(adId) {
  return request.post('/api/shop/order/create', { adId })
}

/** 我的订单列表 */
export function fetchMyOrders() {
  return request.get('/api/shop/order/mine')
}

/** 订单详情（含物流） */
export function fetchOrderDetail(orderId) {
  return request.get(`/api/shop/order/${orderId}`)
}

/** 确认收货 */
export function confirmOrder(orderId) {
  return request.post('/api/shop/order/confirm', null, {
    params: { orderId },
  })
}

/** 提交评价反馈 */
export function submitFeedback(data) {
  return request.post('/api/shop/order/feedback', data)
}

/** 提交售后申请 */
export function submitAfterSale(data) {
  return request.post('/api/shop/order/after-sale', data)
}

export const deleteMyOrder = id => request.delete(`/api/shop/order/${id}`)

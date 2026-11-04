import { h } from 'vue'
import { ElMessageBox } from 'element-plus'

let pending

export function showMerchantBanDialog(info = {}) {
  if (pending) return pending
  const until = info.permanent ? '永久封禁' : `至 ${String(info.banUntil || '').replace('T', ' ').replace(/\.0$/, '')}`
  const content = h('div', { class: 'merchant-ban-notice-content' }, [
    h('p', { class: 'merchant-ban-notice-title' }, '商家账号已被封禁'),
    h('p', [h('strong', '封禁原因：'), info.reason || '请联系管理员了解详情']),
    h('p', [h('strong', '封禁时间：'), `${info.durationLabel || ''} · ${until}`]),
    h('p', '所有商品已下架，广告已撤销，订单信息保留。'),
    h('p', { class: 'merchant-ban-notice-note' }, '如有疑问请联系管理员。解禁后商品和广告需重新提交审核。'),
  ])
  pending = ElMessageBox.alert(content, '商家封禁通知', {
    confirmButtonText: '我知道了',
    customClass: 'merchant-ban-notice',
    closeOnClickModal: false,
  }).catch(() => {}).finally(() => { pending = undefined })
  return pending
}

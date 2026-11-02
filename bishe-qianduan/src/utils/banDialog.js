import { ElMessageBox } from 'element-plus'

let showing = false

/**
 * 中等大小封禁弹窗: 永久封禁理由 + 申诉 QQ。
 */
export async function showBanDialog(banInfo = {}) {
  if (showing) return
  showing = true
  const reason = banInfo.banReason || banInfo.reason || '违规'
  const qq = banInfo.adminQq || '3088184559'
  const html = `
    <div style="line-height:1.7;color:#374151;font-size:14px;">
      <p style="margin:0 0 12px;font-size:16px;font-weight:600;color:#b91c1c;">该账号已经被永久封禁</p>
      <p style="margin:0 0 8px;"><strong>封禁理由：</strong>${escapeHtml(reason)}</p>
      <p style="margin:0 0 8px;">联系网站管理员进行申诉</p>
      <p style="margin:0;"><strong>管理员QQ：</strong>${escapeHtml(qq)}</p>
    </div>
  `
  try {
    await ElMessageBox.alert(html, '账号封禁通知', {
      dangerouslyUseHTMLString: true,
      confirmButtonText: '我知道了',
      customClass: 'ban-dialog-box',
      center: false,
    })
  } catch {
    // closed
  } finally {
    showing = false
  }
}

function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

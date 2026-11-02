import { ElMessageBox } from 'element-plus'
import { fetchUnreadWarnings, markWarningRead } from '@/api/warning'

/**
 * 拉取未读警告并以可关闭临时弹窗依次展示。
 */
export async function showUnreadWarningPopups() {
  try {
    const res = await fetchUnreadWarnings()
    const list = res.data || []
    for (const item of list) {
      try {
        await ElMessageBox.alert(item.content || '请注意文明用语', '平台警告', {
          confirmButtonText: '我知道了',
          type: 'warning',
          dangerouslyUseHTMLString: false,
          closeOnClickModal: true,
          closeOnPressEscape: true,
          showClose: true,
        })
      } catch {
        // 用户关闭也算已读
      }
      try {
        await markWarningRead(item.warningId)
      } catch {
        // ignore
      }
    }
  } catch {
    // ignore network errors on boot
  }
}

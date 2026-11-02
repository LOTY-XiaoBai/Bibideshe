import { onChatEvent, connectChatWs } from '@/utils/websocket'
import { showUnreadWarningPopups } from '@/utils/warningPopup'
import { showBanDialog } from '@/utils/banDialog'
import { ElMessageBox } from 'element-plus'
import { markWarningRead } from '@/api/warning'
import { useUserStore } from '@/stores/user'
import { clearAuth } from '@/utils/auth'
import { disconnectChatWs } from '@/utils/websocket'

/**
 * 在师生布局挂载时建立会话监听:
 * - USER_WARNING: 在线即时弹窗
 * - FORCE_LOGOUT: 被封禁立即踢出
 * 同时拉取离线期间未读警告。
 */
export function startSessionGuard() {
  connectChatWs()
  showUnreadWarningPopups()

  return onChatEvent(async (payload) => {
    if (!payload || !payload.type) return

    if (payload.type === 'USER_WARNING') {
      const item = payload.data || {}
      try {
        await ElMessageBox.alert(item.content || '请注意文明用语', '平台警告', {
          confirmButtonText: '我知道了',
          type: 'warning',
          closeOnClickModal: true,
          closeOnPressEscape: true,
          showClose: true,
        })
      } catch {
        // 关闭也算已读
      }
      if (item.warningId) {
        try {
          await markWarningRead(item.warningId)
        } catch {
          // ignore
        }
      }
      return
    }

    if (payload.type === 'FORCE_LOGOUT') {
      const banInfo = payload.data || {}
      try {
        const store = useUserStore()
        store.logout()
      } catch {
        disconnectChatWs()
        clearAuth()
      }
      await showBanDialog(banInfo)
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
  })
}

<template>
  <div class="app-shell" data-role="admin">
    <aside class="sidebar">
      <div class="sidebar__brand">
        <div class="brand-icon">
          <i class="ph ph-shield-check" />
        </div>
        <div>
          <h2>管理控制台</h2>
          <p>ID: {{ userStore.userId || '-' }}</p>
        </div>
      </div>

      <nav class="sidebar__nav">
        <div class="nav-group">核心数据</div>
        <RouterLink
          v-for="item in menus"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          active-class="is-active"
        >
          <i :class="item.icon" />
          <span>{{ item.label }}</span>
          <el-badge
            v-if="item.path === '/admin/ads' && pendingOrderCount > 0"
            :value="pendingOrderCount"
            class="nav-badge"
          />
        </RouterLink>
      </nav>

      <div class="sidebar__footer">
        <button type="button" class="logout-btn" @click="handleLogout">
          <i class="ph ph-sign-out" />
          <span>退出登录</span>
        </button>
      </div>
    </aside>

    <main class="main-area">
      <RouterView v-slot="{ Component }">
        <Transition name="page-fade" mode="out-in">
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { fetchPendingOrderCount } from '@/api/admin'

const router = useRouter()
const userStore = useUserStore()
const pendingOrderCount = ref(0)
let pollTimer = null

const menus = [
  { path: '/admin/home', label: '管理首页', icon: 'ph ph-squares-four' },
  { path: '/admin/accounts', label: '账号管理', icon: 'ph ph-user-minus' },
  { path: '/admin/courses', label: '课程管理', icon: 'ph ph-books' },
  { path: '/admin/ads', label: '广告与订单', icon: 'ph ph-megaphone' },
  { path: '/admin/audit', label: '题库审核', icon: 'ph ph-seal-check' },
  { path: '/admin/chat', label: '聊天监管', icon: 'ph ph-chats-circle' },
]

async function refreshPendingOrders() {
  try {
    const res = await fetchPendingOrderCount()
    pendingOrderCount.value = res.data?.count || 0
  } catch {
    pendingOrderCount.value = 0
  }
}

function handleLogout() {
  userStore.logout()
  router.replace('/login')
}

onMounted(() => {
  refreshPendingOrders()
  pollTimer = window.setInterval(refreshPendingOrders, 30000)
})

onUnmounted(() => {
  if (pollTimer) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<style scoped>
.app-shell {
  display: flex;
  height: 100%;
  background: var(--bg-light);
}

.sidebar {
  width: var(--sidebar-width);
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-right: 1px solid #e2e8f0;
}

.sidebar__brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 24px 20px;
  border-bottom: 1px solid #f1f5f9;
}

.brand-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: var(--theme-gradient);
  color: #fff;
  font-size: 22px;
  box-shadow: var(--theme-shadow);
}

.sidebar__brand h2 {
  margin: 0;
  font-size: 16px;
  color: #1e293b;
}

.sidebar__brand p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #94a3b8;
}

.sidebar__nav {
  flex: 1;
  overflow-y: auto;
  padding: 16px 12px;
}

.nav-group {
  margin: 0 0 8px 8px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #94a3b8;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 12px;
  margin-bottom: 4px;
  border-radius: 12px;
  color: #64748b;
  font-weight: 500;
  transition: all 0.2s ease;
}

.nav-item i {
  font-size: 20px;
  color: var(--theme-primary);
}

.nav-item:hover {
  background: var(--theme-hover-bg);
  color: #334155;
}

.nav-item.is-active {
  background: var(--theme-gradient);
  color: #fff;
  box-shadow: var(--theme-shadow);
}

.nav-item.is-active i {
  color: #fff;
}

.nav-badge {
  margin-left: auto;
}

.sidebar__footer {
  padding: 16px;
  border-top: 1px solid #f1f5f9;
}

.logout-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 10px 16px;
  border: none;
  border-radius: 12px;
  background: #f8fafc;
  color: #64748b;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.logout-btn:hover {
  background: var(--theme-hover-bg);
  color: var(--theme-primary);
}

.main-area {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding: 28px;
}
</style>

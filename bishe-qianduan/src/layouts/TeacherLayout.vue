<template>
  <div class="app-shell" data-role="teacher">
    <aside class="sidebar">
      <div class="sidebar__profile" @click="goSettings">
        <div class="sidebar__profile-bg" />
        <div class="sidebar__profile-body">
          <div class="avatar-wrap">
            <img :src="avatarSrc" alt="avatar" class="avatar" />
            <span class="online-dot" />
          </div>
          <h2 class="name">{{ userStore.displayName || '教师用户' }}</h2>
          <p class="meta">
            <span class="id-badge">ID: {{ userStore.userId || '-' }}</span>
          </p>
        </div>
      </div>

      <nav class="sidebar__nav">
        <div class="nav-group-title">导航栏</div>
        <RouterLink
          v-for="item in workMenus"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          active-class="is-active"
        >
          <i :class="item.icon" />
          <span>{{ item.label }}</span><CountBadge :count="menuCount(item.path)" />
        </RouterLink>

        <div class="nav-group-title">教学与考核</div>
        <RouterLink
          v-for="item in teachMenus"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          active-class="is-active"
        >
          <i :class="item.icon" />
          <span>{{ item.label }}</span><CountBadge :count="menuCount(item.path)" />
        </RouterLink>

        <div class="nav-group-title">生态服务</div>
        <RouterLink
          v-for="item in serviceMenus"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          active-class="is-active"
        >
          <i :class="item.icon" />
          <span>{{ item.label }}</span><CountBadge :count="menuCount(item.path)" />
        </RouterLink>
      </nav>

      <div class="sidebar__footer">
        <button type="button" class="logout-btn" @click="handleLogout">
          <i aria-hidden="true" class="ph ph-sign-out" />
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
import CountBadge from '@/components/CountBadge.vue'
import { useUnread } from '@/stores/unread'
const unread = useUnread()
function menuCount(path){return path.endsWith('/activity')?unread.activityCount:path.endsWith('/chat')?(path.startsWith('/admin')?unread.data.violations:unread.chatCount):path.endsWith('/audit')?unread.data.questions:0}

import { computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { startSessionGuard } from '@/utils/sessionGuard'

const router = useRouter()
const userStore = useUserStore()

const workMenus = [
  { path: '/teacher/home', label: '主页概览', icon: 'ph ph-chalkboard-teacher' },
  { path: '/teacher/activity', label: '动态', icon: 'ph ph-newspaper' },
  { path: '/teacher/courses', label: '我的授课', icon: 'ph ph-books' },
  { path: '/teacher/chat', label: '交际与答疑', icon: 'ph ph-chats' },
]

const teachMenus = [
  { path: '/teacher/tasks', label: '发布作业/考试', icon: 'ph ph-file-text' },
  { path: '/teacher/grade', label: '人工批阅', icon: 'ph ph-check-square-offset' },
  { path: '/teacher/questions', label: '题库管理', icon: 'ph ph-database' },
]

const serviceMenus = [
  { path: '/teacher/shop', label: '周边商城', icon: 'ph ph-storefront' },
  { path: '/teacher/settings', label: '个人设置', icon: 'ph ph-gear' },
]

const avatarSrc = computed(() => {
  if (userStore.avatar) return userStore.avatar
  return `https://placehold.co/100x100/EE0000/ffffff?text=Tea`
})

let stopGuard = null

onMounted(() => {
  userStore.refreshProfile().catch(() => {})
  stopGuard = startSessionGuard()
})

onUnmounted(() => {
  if (typeof stopGuard === 'function') stopGuard()
})

function goSettings() {
  router.push('/teacher/settings')
}

function handleLogout() {
  userStore.logout(true)
  router.replace('/login')
}
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
  border-right: 1px solid var(--border-color);
  box-shadow: 2px 0 16px rgba(15, 23, 42, 0.03);
  z-index: 10;
}

.sidebar__profile {
  position: relative;
  padding: 24px 20px 16px;
  border-bottom: 1px solid #f9fafb;
  cursor: pointer;
  transition: background 0.2s ease;
}

.sidebar__profile:hover {
  background: #f9fafb;
}

.sidebar__profile-bg {
  position: absolute;
  inset: 0 0 auto 0;
  height: 64px;
  background: linear-gradient(90deg, var(--brand-blue), var(--brand-red));
  opacity: 0.22;
}

.sidebar__profile-body {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: 12px;
}

.avatar-wrap {
  position: relative;
}

.avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  border: 4px solid #fff;
  object-fit: cover;
  box-shadow: 0 6px 16px rgba(238, 0, 0, 0.18);
  background: #ffe5e5;
}

.online-dot {
  position: absolute;
  right: 4px;
  bottom: 4px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--brand-red);
  border: 2px solid #fff;
}

.name {
  margin: 12px 0 0;
  font-size: 18px;
  font-weight: 700;
}

.meta {
  margin: 6px 0 0;
}

.id-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  background: rgba(238, 0, 0, 0.1);
  color: var(--brand-red);
  font-size: 10px;
  font-weight: 700;
}

.sidebar__nav {
  flex: 1;
  overflow-y: auto;
  padding: 16px 12px;
}

.nav-group-title {
  margin: 16px 0 8px 12px;
  color: #9ca3af;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.nav-group-title:first-child {
  margin-top: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 12px;
  margin-bottom: 4px;
  border-radius: 12px;
  color: #4b5563;
  font-weight: 500;
  transition: all 0.2s ease;
}

.nav-item i {
  font-size: 20px;
}

.nav-item:hover {
  background: rgba(238, 0, 0, 0.05);
  color: var(--brand-red);
}

.nav-item.is-active {
  background: rgba(238, 0, 0, 0.08);
  color: var(--brand-red);
}

.sidebar__footer {
  padding: 16px;
  border-top: 1px solid #f9fafb;
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
  background: transparent;
  color: #6b7280;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s ease;
}

.logout-btn:hover {
  background: #f3f4f6;
}

.main-area {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding: 28px;
}
</style>

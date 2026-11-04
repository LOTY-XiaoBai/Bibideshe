<template>
  <div class="home-page">
    <AdCarousel />
    <h2>系统首页</h2>
    <p class="subtitle">欢迎回来，{{ userStore.displayName || '同学' }}。请从左侧导航进入各功能模块。</p>

    <div class="quick-grid">
      <RouterLink
        v-for="item in quickLinks"
        :key="item.path"
        :to="item.path"
        class="quick-card"
      >
        <div class="quick-card__icon">
          <i :class="item.icon" />
        </div>
        <div>
          <h3>{{ item.label }}</h3>
          <p>{{ item.desc }}</p>
        </div>
      </RouterLink>
    </div>
    <CommunityFeed compact />
  </div>
</template>

<script setup>
import { useUserStore } from '@/stores/user'
import CommunityFeed from '@/components/CommunityFeed.vue'
import AdCarousel from '@/components/AdCarousel.vue'

const userStore = useUserStore()

const quickLinks = [
  { path: '/student/courses', label: '我的课程', desc: '选课、成绩与班级', icon: 'ph ph-books' },
  { path: '/student/error-book', label: '我的错题本', desc: '自动收录的错题复习', icon: 'ph ph-notebook' },
  { path: '/student/tasks', label: '考试/作业/小测', desc: '待办任务、成绩与答题复盘', icon: 'ph ph-pencil-simple-line' },
  { path: '/student/settings', label: '个人设置', desc: '资料与课程得分', icon: 'ph ph-gear' },
]
</script>

<style scoped>
.home-page h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
}

.subtitle {
  margin: 8px 0 24px;
  color: #6b7280;
  font-size: 14px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}

.quick-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid #f3f4f6;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.04);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.quick-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px rgba(102, 204, 255, 0.18);
}

.quick-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: rgba(102, 204, 255, 0.12);
  color: var(--brand-blue);
  font-size: 22px;
  flex-shrink: 0;
}

.quick-card h3 {
  margin: 0 0 4px;
  font-size: 16px;
  color: #1f2937;
}

.quick-card p {
  margin: 0;
  font-size: 12px;
  color: #9ca3af;
}
</style>

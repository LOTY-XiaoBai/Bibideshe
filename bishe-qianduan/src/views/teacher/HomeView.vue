<template>
  <div class="page">
    <section class="hero">
      <div class="hero__text">
        <p class="hero__hello">欢迎回来</p>
        <h2>{{ userStore.displayName || '教师' }}</h2>
        <p>管理课程、题库与班级，从左侧导航进入各功能模块。</p>
      </div>
      <div class="rating-card">
        <p class="rating-card__label">综合评分</p>
        <div v-if="hasRating" class="rating-card__score">
          {{ formatScore(comprehensiveScore) }}
          <span>/ 10</span>
        </div>
        <div v-else class="rating-card__empty">{{ scoreTip || '暂未有人打分' }}</div>
        <p class="rating-card__meta">已开课程 {{ courseCount }} / 3</p>
      </div>
    </section>

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
import { onMounted, ref } from 'vue'
import CommunityFeed from '@/components/CommunityFeed.vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const comprehensiveScore = ref(null)
const hasRating = ref(false)
const scoreTip = ref('')
const courseCount = ref(0)

const quickLinks = [
  { path: '/teacher/courses', label: '我的授课', desc: '创建课程、结课与班级管理', icon: 'ph ph-books' },
  { path: '/teacher/questions', label: '题库管理', desc: '上传选择题/判断题/解答题', icon: 'ph ph-database' },
  { path: '/teacher/tasks', label: '发布作业/考试', desc: '从题库组卷发布考核', icon: 'ph ph-file-text' },
  { path: '/teacher/settings', label: '个人设置', desc: '资料修改与评分详情', icon: 'ph ph-gear' },
]

function formatScore(score) {
  if (score === null || score === undefined || score === '') return '-'
  return Number(score).toFixed(1)
}

onMounted(async () => {
  try {
    const profile = await userStore.refreshProfile()
    comprehensiveScore.value = profile?.comprehensiveScore ?? null
    hasRating.value = Boolean(profile?.hasRating)
    scoreTip.value = profile?.scoreTip || ''
    courseCount.value = profile?.courseCount ?? 0
  } catch {
    const profile = userStore.profile
    comprehensiveScore.value = profile?.comprehensiveScore ?? null
    hasRating.value = Boolean(profile?.hasRating)
    scoreTip.value = profile?.scoreTip || ''
    courseCount.value = profile?.courseCount ?? 0
  }
})
</script>

<style scoped>
.hero {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 28px;
  border-radius: 20px;
  background: linear-gradient(135deg, #2b2d42, #ee0000);
  color: #fff;
  box-shadow: 0 12px 32px rgba(238, 0, 0, 0.18);
  margin-bottom: 22px;
}

.hero__hello {
  margin: 0;
  opacity: 0.8;
  font-size: 13px;
}

.hero__text h2 {
  margin: 8px 0 10px;
  font-size: 28px;
}

.hero__text p:last-child {
  margin: 0;
  opacity: 0.85;
  font-size: 14px;
  max-width: 420px;
}

.rating-card {
  min-width: 180px;
  padding: 18px 20px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(8px);
  text-align: right;
}

.rating-card__label {
  margin: 0;
  font-size: 12px;
  opacity: 0.8;
}

.rating-card__score {
  margin-top: 8px;
  font-size: 40px;
  font-weight: 800;
  line-height: 1;
}

.rating-card__score span {
  font-size: 14px;
  font-weight: 500;
  opacity: 0.8;
}

.rating-card__empty {
  margin-top: 12px;
  font-size: 16px;
  font-weight: 600;
}

.rating-card__meta {
  margin: 14px 0 0;
  font-size: 12px;
  opacity: 0.75;
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
  box-shadow: 0 10px 24px rgba(238, 0, 0, 0.12);
}

.quick-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: rgba(238, 0, 0, 0.08);
  color: var(--brand-red);
  font-size: 22px;
  flex-shrink: 0;
}

.quick-card h3 {
  margin: 0 0 4px;
  font-size: 16px;
}

.quick-card p {
  margin: 0;
  font-size: 12px;
  color: #9ca3af;
}

@media (max-width: 720px) {
  .hero {
    flex-direction: column;
  }

  .rating-card {
    text-align: left;
  }
}
</style>

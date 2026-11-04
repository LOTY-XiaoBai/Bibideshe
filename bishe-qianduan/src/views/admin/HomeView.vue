<template>
  <div class="page">
    <section class="hero">
      <h1>系统运行总览</h1>
      <p>管理账号、课程、商城、题库审核与聊天监管</p>
    </section>

    <div class="card-grid">
      <article v-for="item in cards" :key="item.path" class="stat-card" @click="go(item.path)">
        <i :class="item.icon" />
        <div>
          <span>{{ item.label }}</span>
          <strong>{{ item.desc }}</strong>
        </div>
      </article>
    </div>
    <CommunityFeed compact />
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import CommunityFeed from '@/components/CommunityFeed.vue'

const router = useRouter()

const cards = [
  { path: '/admin/merchants', label: '合作方商家', desc: '邀请与入驻管理', icon: 'ph ph-storefront' },
  { path: '/admin/accounts', label: '账号管理', desc: '注销师生账号', icon: 'ph ph-user-minus' },
  { path: '/admin/courses', label: '课程管理', desc: '删除课程', icon: 'ph ph-books' },
  { path: '/admin/ads', label: '广告与商品审核', desc: '审批商家投放内容', icon: 'ph ph-megaphone' },
  { path: '/admin/audit', label: '题库审核', desc: '审核与删除题目', icon: 'ph ph-seal-check' },
  { path: '/admin/chat', label: '聊天监管', desc: '消息与群聊封禁', icon: 'ph ph-chats-circle' },
]

function go(path) {
  router.push(path)
}
</script>

<style scoped>
.hero {
  padding: 28px;
  border-radius: 20px;
  background: var(--theme-gradient);
  color: #fff;
  margin-bottom: 20px;
}

.hero h1 {
  margin: 0 0 8px;
  font-size: 26px;
}

.hero p {
  margin: 0;
  opacity: 0.9;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 14px;
}

.stat-card {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 18px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid #f3f4f6;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--theme-shadow);
}

.stat-card i {
  font-size: 28px;
  color: var(--theme-primary);
}

.stat-card span {
  display: block;
  font-size: 13px;
  color: #6b7280;
}

.stat-card strong {
  font-size: 15px;
  color: #111827;
}
</style>

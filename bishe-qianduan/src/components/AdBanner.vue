<template>
  <div class="ad-banner" @click="$emit('click')">
    <div class="ad-banner__glow" />
    <div class="ad-banner__content">
      <span class="ad-banner__tag">官方推荐广告</span>
      <h3 class="ad-banner__title">{{ title }}</h3>
      <p class="ad-banner__desc">{{ description }}</p>
      <button type="button" class="ad-banner__btn" @click.stop="$emit('action')">
        {{ actionText }}
      </button>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    default: '校园精选好物上新',
  },
  description: {
    type: String,
    default: '浏览广告商品，支持系统内下单并跟踪物流。',
  },
  actionText: {
    type: String,
    default: '前往商城查看',
  },
})

defineEmits(['click', 'action'])
</script>

<style scoped>
.ad-banner {
  position: relative;
  overflow: hidden;
  border-radius: 16px;
  padding: 28px 32px;
  background: linear-gradient(120deg, #2b2d42, #1a1b26);
  color: #fff;
  cursor: pointer;
  box-shadow: 0 12px 30px rgba(26, 27, 38, 0.18);
}

.ad-banner__glow {
  position: absolute;
  right: -40px;
  bottom: -40px;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: var(--brand-blue);
  filter: blur(48px);
  opacity: 0.35;
  pointer-events: none;
}

.ad-banner__content {
  position: relative;
  z-index: 1;
  max-width: 560px;
}

.ad-banner__tag {
  display: inline-block;
  margin-bottom: 10px;
  padding: 4px 8px;
  border-radius: 6px;
  background: var(--brand-mint);
  color: #1a1b26;
  font-size: 12px;
  font-weight: 700;
}

.ad-banner__title {
  margin: 0 0 8px;
  font-size: 26px;
  font-weight: 700;
}

.ad-banner__desc {
  margin: 0 0 18px;
  color: rgba(255, 255, 255, 0.72);
  font-size: 14px;
  line-height: 1.5;
}

.ad-banner__btn {
  border: none;
  border-radius: 10px;
  padding: 10px 18px;
  background: linear-gradient(90deg, var(--brand-blue), var(--brand-mint));
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.ad-banner__btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 18px rgba(102, 204, 255, 0.35);
}
</style>

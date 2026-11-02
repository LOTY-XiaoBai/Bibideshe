<template>
  <div class="empty-page">
    <h2>{{ title }}</h2>
    <p>{{ description }}</p>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => route.meta.title || '功能页面')
const description = computed(
  () => route.meta.description || '该功能页面待后续实现。',
)
</script>

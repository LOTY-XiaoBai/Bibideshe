<template>
  <div class="blocks">
    <template v-for="(b, i) in parsed" :key="i"
      ><p v-if="b.type === 'text'">{{ b.value }}</p>
      <img
        v-else-if="
          b.type === 'image' && /^\/uploads\/image\/[\w.-]+$/.test(b.value)
        "
        :src="b.value"
        alt="内容图片"
        loading="lazy"
    /></template>
  </div>
</template>
<script setup>
import { computed } from "vue";
const props = defineProps({ value: { type: String, default: "[]" } });
const parsed = computed(() => {
  try {
    const x = JSON.parse(props.value);
    return Array.isArray(x) ? x : [];
  } catch {
    return [];
  }
});
</script>
<style scoped>
.blocks p {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.8;
}
.blocks img {
  display: block;
  max-width: 100%;
  max-height: 700px;
  object-fit: contain;
  margin: 12px 0;
  border-radius: 10px;
}
</style>

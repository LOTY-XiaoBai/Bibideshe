<template><span v-if="Number(count) > 0" class="count-badge" :aria-label="`${count}条未处理或未读`">{{ Number(count) > 99 ? '99+' : count }}</span></template>
<script setup>defineProps({ count: { type: [Number, String], default: 0 } })</script>
<style scoped>.count-badge{display:inline-flex;align-items:center;justify-content:center;min-width:18px;height:18px;padding:0 5px;box-sizing:border-box;border-radius:20px;background:#f04452;color:white;font-size:11px;font-weight:600;line-height:18px;flex-shrink:0;vertical-align:middle;margin-left:6px}</style>

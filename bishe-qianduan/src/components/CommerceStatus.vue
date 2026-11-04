<template><span class="commerce-status" :class="status?.toLowerCase()"><span />{{ labels[status] || status }}</span></template>
<script setup>
defineProps({ status: String })
const labels = { PENDING: '等待审核', APPROVED: '已通过', REJECTED: '已驳回 / 已下架', WITHDRAWN: '已撤回', LEGACY: '历史商品' }
</script>
<style scoped>
.commerce-status{display:inline-flex;align-items:center;gap:6px;border-radius:30px;padding:6px 11px;font-size:12px;font-weight:600;background:#f1f3f5;color:#66716c;white-space:nowrap}.commerce-status>span{width:5px;height:5px;border-radius:50%;background:currentColor}.pending{background:#fff2da;color:#99601f}.approved{background:#e6f4ea;color:#2d6d48}.rejected{background:#ffebe6;color:#b64d34}.withdrawn{background:#edf0f2;color:#6c7480}
</style>

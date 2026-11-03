<template><video ref="video" autoplay playsinline :muted="muted" :controls="controls" @click="play" /></template>
<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
const props = defineProps({ stream: Object, muted: Boolean, controls: Boolean })
const video = ref(null)
function play() { video.value?.play().catch(() => {}) }
watch([() => props.stream, video], () => { if (video.value) { video.value.srcObject = props.stream || null; play() } }, { flush: 'post' })
onBeforeUnmount(() => { if (video.value) video.value.srcObject = null })
</script>

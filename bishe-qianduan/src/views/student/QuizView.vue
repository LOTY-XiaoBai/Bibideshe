<template><div class="quiz-page"><el-button text @click="router.push('/student/tasks?tab=quiz')">← 返回考试/作业/小测</el-button><StudentQuizWorkspace :key="route.params.quizId" :quiz-id="route.params.quizId" /></div></template>
<script setup>
import {useRoute,useRouter} from 'vue-router'
import StudentQuizWorkspace from '@/components/StudentQuizWorkspace.vue'
const route=useRoute(),router=useRouter()
</script>
<style scoped>.quiz-page { background:white;border:1px solid #edf0f4;border-radius:16px;padding:24px; }.quiz-page > .el-button { margin-bottom:20px; }</style>

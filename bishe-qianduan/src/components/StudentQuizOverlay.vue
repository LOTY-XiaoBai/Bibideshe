<template>
  <el-dialog :model-value="!!classroom.dialogQuizId" title="课堂小测 · 请完成作答" width="min(960px, 96vw)" :close-on-click-modal="false" destroy-on-close append-to-body @update:model-value="value => { if (!value) classroom.closeQuizDialog() }">
    <StudentQuizWorkspace v-if="classroom.dialogQuizId" :key="classroom.dialogQuizId" :quiz-id="classroom.dialogQuizId" @submitted="classroom.refresh" />
    <template #footer><el-button @click="classroom.closeQuizDialog()">收起小测</el-button></template>
  </el-dialog>
</template>
<script setup>
import { useClassroomStore } from '@/stores/classroom'
import StudentQuizWorkspace from './StudentQuizWorkspace.vue'
const classroom=useClassroomStore()
</script>

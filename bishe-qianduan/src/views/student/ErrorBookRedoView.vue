<template>
  <section class="redo-page" v-loading="loading">
    <el-button text @click="router.replace({ name: 'StudentErrorBook' })">返回错题本</el-button>
    <h1>重做错题</h1>
    <template v-if="paper">
      <p>共 {{ paper.questions.length }} 题 · 本标签页刷新后可继续作答</p>
      <QuestionPaper ref="questionPaper" v-model="answers" :questions="paper.questions" practice />
      <footer>
        <el-button type="primary" :loading="submitting" @click="submit">提交重做</el-button>
      </footer>
    </template>
    <el-empty v-else-if="!loading" description="这次重做已结束或不存在" />
  </section>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { discardErrorBookRedo, fetchErrorBookRedo, submitErrorBookRedo } from '@/api/errorBook'
import QuestionPaper from '@/components/QuestionPaper.vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const id = route.params.id
const paper = ref(null)
const questionPaper = ref(null)
const answers = ref({})
const loading = ref(true)
const submitting = ref(false)
const submitted = ref(false)
const draftKey = `error-book-redo:${user.userId}:${id}`

onMounted(async () => {
  try {
    paper.value = (await fetchErrorBookRedo(id)).data
    const draft = JSON.parse(sessionStorage.getItem(draftKey))
    if (draft && typeof draft === 'object' && !Array.isArray(draft)) answers.value = draft
  } catch {
    // 接口错误由请求拦截器提示。无法读取暂存时仍可继续作答。
  } finally {
    loading.value = false
  }
})

watch(answers, value => {
  try {
    sessionStorage.setItem(draftKey, JSON.stringify(value))
  } catch {
    // 浏览器禁用暂存时仍可在页面内作答。
  }
}, { flush: 'sync' })

function clearDraft() {
  try {
    sessionStorage.removeItem(draftKey)
  } catch {
    // 当前浏览器没有可用的暂存。
  }
}

async function submit() {
  if (!questionPaper.value || submitting.value) return
  const values = questionPaper.value.buildAnswerList()
  const blankCount = values.filter(row => !row.studentAnswer.trim()).length
  const message = blankCount ? `还有 ${blankCount} 道题未作答，确认提交？` : '确认提交本次重做？'
  submitting.value = true
  try {
    await ElMessageBox.confirm(message, '提交确认', {
      type: 'warning',
      confirmButtonText: '确认提交',
      cancelButtonText: '继续作答',
    })
    await submitErrorBookRedo(id, values)
    submitted.value = true
    clearDraft()
    await router.replace({ name: 'StudentErrorBookHistory', params: { id } })
  } catch {
    // 取消时保留草稿，接口错误由请求拦截器提示。
  } finally {
    submitting.value = false
  }
}

onBeforeRouteLeave(async () => {
  if (submitted.value || (!paper.value && !loading.value)) return true
  if (submitting.value) return false
  try {
    await ElMessageBox.confirm('返回将清除本次重做记录，是否返回错题本？', '放弃本次重做', {
      type: 'warning',
      confirmButtonText: '确认返回',
      cancelButtonText: '继续作答',
    })
    await discardErrorBookRedo(id)
  } catch {
    return false
  }
  clearDraft()
  return true
})
</script>

<style scoped>
.redo-page {
  padding: 24px;
  background: white;
  border: 1px solid #edf1f6;
  border-radius: 16px;
}

h1 {
  margin: 14px 0 8px;
}

p {
  margin: 0 0 20px;
  color: #6b7280;
}

footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 20px;
}
</style>

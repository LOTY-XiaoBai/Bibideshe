<template>
  <section class="history-page" v-loading="loading">
    <div class="history-nav">
      <el-button text @click="router.push({ name: 'StudentErrorBook' })">返回错题本</el-button>
      <el-button v-if="id" text @click="router.push({ name: 'StudentErrorBookHistory', params: { id: '' } })">
        返回重做历史
      </el-button>
    </div>
    <h1>{{ id ? '重做结果' : '重做历史' }}</h1>
    <template v-if="result">
      <div v-if="objectiveCount" class="redo-summary">
        <div
          class="redo-chart"
          role="img"
          :aria-label="`正确率 ${correctRate}，错误 ${incorrectRate}，未答 ${unansweredRate}`"
          :style="{ '--correct-stop': correctStop, '--answered-stop': answeredStop }"
        >
          <div class="redo-chart__center" aria-hidden="true"><strong>{{ correctRate }}</strong><span>正确率</span></div>
        </div>
        <div>
          <h2>客观题得分 {{ result.correct }} / {{ objectiveCount }}</h2>
          <ul class="redo-legend">
            <li class="redo-legend__correct">正确 {{ result.correct }} 题 · {{ correctRate }}</li>
            <li class="redo-legend__incorrect">错误 {{ result.incorrect }} 题 · {{ incorrectRate }}</li>
            <li class="redo-legend__unanswered">未答 {{ result.unanswered }} 题 · {{ unansweredRate }}</li>
          </ul>
        </div>
      </div>
      <p v-else class="redo-summary">本次无客观题</p>
      <QuestionPaper :questions="result.answers" :model-value="answers" :review-items="result.answers" readonly practice />
    </template>
    <template v-else-if="!id && history.length">
      <ul class="history-list">
        <li v-for="item in history" :key="item.id">
          <div>
            <strong>{{ formatTime(item.submittedAt) }}</strong>
            <span>{{ item.questionCount }} 题 · {{ historySummary(item) }}</span>
          </div>
          <RouterLink :to="{ name: 'StudentErrorBookHistory', params: { id: item.id } }">查看结果</RouterLink>
        </li>
      </ul>
    </template>
    <el-empty v-else-if="!loading" :description="id ? '重做历史不存在' : '暂无重做历史'" />
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchErrorBookHistory, fetchErrorBookReview } from '@/api/errorBook'
import QuestionPaper from '@/components/QuestionPaper.vue'

const route = useRoute()
const router = useRouter()
const id = computed(() => route.params.id)
const history = ref([])
const result = ref(null)
const loading = ref(false)
const formatter = new Intl.NumberFormat('zh-CN', { style: 'percent', maximumFractionDigits: 1 })

const objectiveCount = computed(() => objectiveQuestions(result.value))
const correctRate = computed(() => formatter.format(result.value.correct / objectiveCount.value))
const incorrectRate = computed(() => formatter.format(result.value.incorrect / objectiveCount.value))
const unansweredRate = computed(() => formatter.format(result.value.unanswered / objectiveCount.value))
const correctStop = computed(() => `${result.value.correct / objectiveCount.value * 100}%`)
const answeredStop = computed(() => `${(result.value.correct + result.value.incorrect) / objectiveCount.value * 100}%`)
const answers = computed(() => Object.fromEntries((result.value?.answers || []).map(({ question, studentAnswer }) =>
  [`${question.questionType}-${question.questionId}`, studentAnswer])))

function objectiveQuestions(item) {
  return item ? item.correct + item.incorrect + item.unanswered : 0
}

function historySummary(item) {
  const total = objectiveQuestions(item)
  return total ? `客观题得分 ${item.correct} / ${total}` : '本次无客观题'
}

function formatTime(value) {
  return value?.replace('T', ' ').slice(0, 19)
}

watch(id, async (redoId, _, onCleanup) => {
  let current = true
  onCleanup(() => { current = false })
  result.value = null
  history.value = []
  loading.value = true
  try {
    if (redoId) {
      const { data } = await fetchErrorBookReview(redoId)
      if (current) result.value = data
    } else {
      const { data } = await fetchErrorBookHistory()
      if (current) history.value = data
    }
  } catch {
    // 接口错误由请求拦截器提示。
  } finally {
    if (current) loading.value = false
  }
}, { immediate: true })
</script>

<style scoped>
.history-page {
  --correct-color: #15803d;
  --incorrect-color: #b91c1c;
  --unanswered-color: #64748b;
  padding: 24px;
  background: white;
  border: 1px solid #edf1f6;
  border-radius: 16px;
}

h1 {
  margin: 14px 0 20px;
}

.redo-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 24px;
  margin: 0 0 20px;
  padding: 20px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
}

.redo-summary h2 {
  margin: 0 0 10px;
  font-size: 17px;
}

.redo-chart {
  display: grid;
  place-items: center;
  width: 116px;
  height: 116px;
  border-radius: 50%;
  background: conic-gradient(var(--correct-color) 0 var(--correct-stop),
    var(--incorrect-color) var(--correct-stop) var(--answered-stop),
    var(--unanswered-color) var(--answered-stop) 100%);
}

.redo-chart__center {
  display: grid;
  place-content: center;
  width: 84px;
  height: 84px;
  border-radius: 50%;
  background: white;
  text-align: center;
}

.redo-chart__center span {
  color: #475569;
  font-size: 12px;
}

.redo-legend,
.history-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.redo-legend li {
  margin: 5px 0;
}

.redo-legend__correct { color: var(--correct-color); }
.redo-legend__incorrect { color: var(--incorrect-color); }
.redo-legend__unanswered { color: var(--unanswered-color); }

.history-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px;
  border-bottom: 1px solid #e2e8f0;
}

.history-list span {
  display: block;
  margin-top: 4px;
  color: #475569;
  font-size: 13px;
}

.history-list a {
  color: var(--theme-primary);
}
</style>

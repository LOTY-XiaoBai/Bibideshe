<template>
  <div class="page">
    <header class="page__header">
      <div>
        <h2>我的错题本</h2>
        <p>可从考试、作业或小测复盘中勾选收录，集中整理需要巩固的题目</p>
      </div>
      <div class="page__actions">
        <template v-if="selecting">
          <span class="selection-count">已选 {{ selectedKeys.length }} 题</span>
          <el-button :disabled="starting" @click="cancelSelection">取消重做</el-button>
          <el-button type="primary" :disabled="!selectedKeys.length" :loading="starting" @click="confirmSelection">
            确认重做
          </el-button>
        </template>
        <template v-else>
          <el-button :disabled="!answerItems.length || loading" @click="toggleAllAnswers">
            {{ expandedKeys.length ? '全部隐藏' : '全部展开' }}
          </el-button>
          <el-button type="primary" :disabled="!redoItems.length || loading || !!removingKey" @click="startSelection">
            重做错题
          </el-button>
          <el-button @click="router.push({ name: 'StudentErrorBookHistory' })">重做历史</el-button>
          <el-button text :loading="loading" @click="loadList">刷新</el-button>
        </template>
      </div>
    </header>

    <el-skeleton v-if="loading && !items.length" :rows="5" animated />

    <div v-else-if="!items.length" class="empty">
      错题本暂无内容，可在考试、作业或小测复盘中点击“加入错题本”收录题目。
    </div>

    <div v-else class="list">
      <article v-for="item in items" :key="itemKey(item)" class="error-card">
        <div class="error-card__head">
          <div class="meta">
            <input
              v-if="selecting"
              v-model="selectedKeys"
              type="checkbox"
              class="question-picker"
              :value="itemKey(item)"
              :disabled="item.question.lost || starting"
              :aria-label="`选择题目：${item.question.content}`"
            />
            <el-tag size="small" effect="plain">
              {{ questionTypeLabel(item.question?.questionType) }}
            </el-tag>
            <span>收录时间：{{ formatTime(item.addTime) }}</span>
          </div>
          <el-button
            v-if="!selecting"
            type="danger"
            link
            :loading="removingKey === itemKey(item)"
            @click="handleRemove(item)"
          >
            <i class="ph ph-trash" />
            移除
          </el-button>
        </div>

        <h3>{{ item.question?.content || '题目内容缺失' }}</h3>

        <div
          v-if="item.question?.lost"
          class="lost"
        >
          {{ item.archived ? '原题已删除，无法重做' : '原题内容缺失，无法重做' }}
        </div>

        <template v-if="!item.question?.lost || item.archived">
          <div
            v-if="Number(item.question?.questionType) === 1 && item.question?.options?.length"
            class="options"
          >
            <p
              v-for="opt in item.question.options"
              :key="opt.optionCode"
              :class="{ 'is-answer': isCorrectOption(item, opt) }"
            >
              {{ opt.optionCode }}. {{ opt.optionContent }}
              <span v-if="isCorrectOption(item, opt)">（正确答案）</span>
            </p>
          </div>

          <button
            v-if="!selecting"
            type="button"
            class="answer"
            :aria-expanded="isExpanded(item)"
            :aria-label="isExpanded(item) ? '隐藏答案' : '显示答案'"
            @click="toggleAnswer(item)"
          >
            <span v-if="!isExpanded(item)">显示答案</span>
            <span v-else-if="!(itemKey(item) in revealedAnswers)">正在加载答案…</span>
            <span v-else-if="!answerFor(item)">暂时没有可查看的答案</span>
            <template v-else>
              <span v-if="Number(item.question.questionType) !== 1">参考答案</span>
              {{ Number(item.question.questionType) === 1 ? '隐藏答案' : answerFor(item) }}
            </template>
          </button>
        </template>
      </article>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { fetchErrorBook, removeErrorBookItem, revealErrorBookAnswers, startErrorBookRedo } from '@/api/errorBook'
import { questionTypeLabel } from '@/utils/question'

const userStore = useUserStore()
const router = useRouter()
const loading = ref(false)
const items = ref([])
const removingKey = ref('')
const expandedKeys = ref([])
const revealedAnswers = ref({})
const selecting = ref(false)
const selectedKeys = ref([])
const starting = ref(false)
const answerItems = computed(() => items.value.filter(item => !item.question?.lost || item.archived))
const redoItems = computed(() => items.value.filter(item => !item.question?.lost))
const visibilityKey = computed(() => `error-book-expanded:${userStore.userId}`)
let revealRequest = 0

function itemKey(item) {
  return `${item.question?.questionId}-${item.question?.questionType}`
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

function isExpanded(item) {
  return expandedKeys.value.includes(itemKey(item))
}

function answerFor(item) {
  return revealedAnswers.value[itemKey(item)]
}

function isCorrectOption(item, opt) {
  if (selecting.value || !answerFor(item)) return false
  const answer = String(answerFor(item)).trim().toUpperCase()
  return answer.includes(String(opt.optionCode).toUpperCase())
}

function savedVisibility() {
  try {
    const keys = JSON.parse(localStorage.getItem(visibilityKey.value))
    return Array.isArray(keys) ? keys : []
  } catch {
    return []
  }
}

function saveVisibility(keys) {
  expandedKeys.value = keys
  try {
    localStorage.setItem(visibilityKey.value, JSON.stringify(keys))
  } catch {
    // 显隐仍可在当前页面使用。
  }
}

async function showAnswers(keys) {
  const requestId = ++revealRequest
  const visible = answerItems.value.filter(item => keys.includes(itemKey(item)))
  saveVisibility(visible.map(itemKey))
  const retained = Object.fromEntries(visible
    .filter(item => answerFor(item) != null)
    .map(item => [itemKey(item), answerFor(item)]))
  revealedAnswers.value = retained
  const missing = visible.filter(item => !(itemKey(item) in retained))
  if (!missing.length) return
  try {
    const { data } = await revealErrorBookAnswers(missing.map(({ question }) => ({
      questionId: question.questionId,
      questionType: question.questionType,
    })))
    if (requestId !== revealRequest) return
    revealedAnswers.value = { ...retained, ...Object.fromEntries(data.map(row => [itemKey({ question: row }), row.answer])) }
  } catch {
    if (requestId === revealRequest) saveVisibility(Object.keys(retained))
  }
}

function toggleAnswer(item) {
  const key = itemKey(item)
  showAnswers(isExpanded(item) ? expandedKeys.value.filter(value => value !== key) : [...expandedKeys.value, key])
}

function toggleAllAnswers() {
  showAnswers(expandedKeys.value.length ? [] : answerItems.value.map(itemKey))
}

function startSelection() {
  selecting.value = true
  selectedKeys.value = []
  ++revealRequest
  revealedAnswers.value = {}
}

function cancelSelection() {
  selecting.value = false
  selectedKeys.value = []
  showAnswers(expandedKeys.value)
}

async function confirmSelection() {
  const questions = redoItems.value.filter(item => selectedKeys.value.includes(itemKey(item)))
    .map(({ question }) => ({ questionId: question.questionId, questionType: question.questionType }))
  if (!questions.length || starting.value) return
  starting.value = true
  try {
    const { data } = await startErrorBookRedo(questions)
    await router.push({ name: 'StudentErrorBookRedo', params: { id: data.id } })
  } catch {
    // 接口错误由请求拦截器提示。
  } finally {
    starting.value = false
  }
}

async function loadList() {
  const studentId = userStore.userId
  if (!studentId) return
  loading.value = true
  ++revealRequest
  revealedAnswers.value = {}
  try {
    const res = await fetchErrorBook(studentId)
    items.value = res.data || []
    await showAnswers(savedVisibility())
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

async function handleRemove(item) {
  try {
    await ElMessageBox.confirm('确认从错题本移除该题？', '移除错题', {
      type: 'warning',
    })
    removingKey.value = itemKey(item)
    await removeErrorBookItem({
      questionId: item.question.questionId,
      questionType: item.question.questionType,
    })
    ElMessage.success('已移除')
    items.value = items.value.filter((row) => itemKey(row) !== itemKey(item))
    await showAnswers(expandedKeys.value)
  } catch {
    // cancel
  } finally {
    removingKey.value = ''
  }
}

onMounted(async () => {
  if (!userStore.userId) {
    await userStore.refreshProfile().catch(() => {})
  }
  loadList()
})
</script>

<style scoped>
.page__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.page__header h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
}

.page__header p {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 14px;
}

.page__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.selection-count {
  color: #6b7280;
  font-size: 13px;
}

.question-picker {
  width: 19px;
  height: 19px;
  margin: 0;
  accent-color: var(--theme-primary);
  cursor: pointer;
}

.page__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.empty {
  margin-top: 24px;
  padding: 48px 24px;
  text-align: center;
  color: #9ca3af;
  background: #fff;
  border-radius: 16px;
  border: 1px dashed #e5e7eb;
}

.list {
  margin-top: 22px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.error-card {
  padding: 20px 22px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid #f3f4f6;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.04);
}

.error-card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.meta {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #9ca3af;
  font-size: 12px;
}

.error-card h3 {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.6;
  color: #1f2937;
}

.options {
  padding: 14px;
  border-radius: 12px;
  background: #f9fafb;
  border: 1px solid #f3f4f6;
}

.options p {
  margin: 0 0 8px;
  font-size: 13px;
  color: #4b5563;
}

.options p:last-child {
  margin-bottom: 0;
}

.options .is-answer {
  color: #059669;
  font-weight: 700;
}

.answer {
  display: block;
  width: 100%;
  margin-top: 10px;
  padding: 12px 14px;
  border: 0;
  border-radius: 12px;
  background: rgba(0, 255, 204, 0.08);
  color: #0f766e;
  cursor: pointer;
  font-size: 13px;
  text-align: left;
  white-space: pre-wrap;
}

.answer span {
  display: block;
  margin-bottom: 4px;
  color: #6b7280;
  font-size: 12px;
}

.lost {
  padding: 12px 14px;
  border-radius: 12px;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 13px;
}
</style>

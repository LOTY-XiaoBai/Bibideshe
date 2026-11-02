<template>
  <div class="page">
    <header class="page__header">
      <div>
        <h2>我的错题本</h2>
        <p>提交考试/作业答错时系统自动收录；此处仅展示题目内容，不含当时作答选项</p>
      </div>
      <el-button text :loading="loading" @click="loadList">刷新</el-button>
    </header>

    <el-skeleton v-if="loading && !items.length" :rows="5" animated />

    <div v-else-if="!items.length" class="empty">
      错题本暂无内容。完成作业或考试后，答错的题目会自动出现在这里。
    </div>

    <div v-else class="list">
      <article v-for="item in items" :key="itemKey(item)" class="error-card">
        <div class="error-card__head">
          <div class="meta">
            <el-tag size="small" effect="plain">
              {{ questionTypeLabel(item.question?.questionType) }}
            </el-tag>
            <span>收录时间：{{ formatTime(item.addTime) }}</span>
          </div>
          <el-button
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
          因为一些原因，该题目丢失了
        </div>

        <template v-else>
          <div
            v-if="Number(item.question?.questionType) === 1 && item.question?.options?.length"
            class="options"
          >
            <p
              v-for="opt in item.question.options"
              :key="opt.optionCode"
              :class="{ 'is-answer': isCorrectOption(item.question, opt) }"
            >
              {{ opt.optionCode }}. {{ opt.optionContent }}
              <span v-if="isCorrectOption(item.question, opt)">（正确答案）</span>
            </p>
          </div>

          <div v-else-if="item.question?.answer" class="answer">
            <span>参考答案</span>
            {{ item.question.answer }}
          </div>
        </template>
      </article>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { fetchErrorBook, removeErrorBookItem } from '@/api/errorBook'
import { questionTypeLabel } from '@/utils/question'

const userStore = useUserStore()
const loading = ref(false)
const items = ref([])
const removingKey = ref('')

function itemKey(item) {
  return `${item.question?.questionId}-${item.question?.questionType}`
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

function isCorrectOption(question, opt) {
  if (!question?.answer) return false
  const answer = String(question.answer).trim().toUpperCase()
  return answer.includes(String(opt.optionCode).toUpperCase())
}

async function loadList() {
  const studentId = userStore.userId
  if (!studentId) return
  loading.value = true
  try {
    const res = await fetchErrorBook(studentId)
    items.value = res.data || []
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
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(0, 255, 204, 0.08);
  color: #0f766e;
  font-size: 13px;
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

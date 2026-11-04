<template>
  <div class="question-paper">
    <article
      v-for="(item, idx) in questions"
      :key="`${item.question?.questionType}-${item.question?.questionId}`"
      class="q-block"
    >
      <header class="q-block__head">
        <span class="q-index">{{ idx + 1 }}</span>
        <el-tag size="small">{{ questionTypeLabel(item.question?.questionType) }}</el-tag>
        <span class="q-score">{{ formatScore(item.score) }} 分</span>
      </header>

      <p class="q-content">{{ item.question?.content || '题目内容缺失' }}</p>

      <template v-if="Number(item.question?.questionType) === 1 && item.question?.options?.length">
        <el-checkbox-group v-if="!readonly && item.question?.multiple" :model-value="String(answers[keyOf(item)] || '').split(',').filter(Boolean)" class="opt-group" @update:model-value="v => setAnswer(item, v.join(','))">
          <el-checkbox v-for="opt in item.question.options" :key="opt.optionCode" :value="opt.optionCode" class="opt-item">{{ opt.optionCode }}. {{ opt.optionContent }}</el-checkbox>
        </el-checkbox-group>
        <el-radio-group
          v-else-if="!readonly"
          :model-value="answers[keyOf(item)]"
          class="opt-group"
          @update:model-value="(v) => setAnswer(item, v)"
        >
          <el-radio
            v-for="opt in item.question.options"
            :key="opt.optionCode"
            :value="opt.optionCode"
            class="opt-item"
          >
            {{ opt.optionCode }}. {{ opt.optionContent }}
          </el-radio>
        </el-radio-group>
        <div v-else class="readonly-answer">
          你的答案：{{ answers[keyOf(item)] || '未作答' }}
        </div>
      </template>

      <template v-else-if="Number(item.question?.questionType) === 2">
        <el-radio-group
          v-if="!readonly"
          :model-value="answers[keyOf(item)]"
          @update:model-value="(v) => setAnswer(item, v)"
        >
          <el-radio value="正确">正确</el-radio>
          <el-radio value="错误">错误</el-radio>
        </el-radio-group>
        <div v-else class="readonly-answer">
          你的答案：{{ judgeLabel(answers[keyOf(item)]) }}
        </div>
      </template>

      <template v-else>
        <el-input
          v-if="!readonly"
          :model-value="answers[keyOf(item)]"
          type="textarea"
          :rows="4"
          placeholder="请输入解答"
          @update:model-value="(v) => setAnswer(item, v)"
        />
        <div v-else class="readonly-answer pre">{{ answers[keyOf(item)] || '未作答' }}</div>
      </template>

      <div v-if="showReview && reviewMap[keyOf(item)]" class="review-box">
        <p>得分：{{ formatScore(reviewMap[keyOf(item)].score) }} / {{ formatScore(item.score) }}</p>
        <p v-if="reviewStatusText(item, reviewMap[keyOf(item)])">
          批阅状态：{{ reviewStatusText(item, reviewMap[keyOf(item)]) }}
        </p>
        <p v-if="reviewMap[keyOf(item)].annotation && !isPendingAnnotation(reviewMap[keyOf(item)].annotation)">
          批注：{{ reviewMap[keyOf(item)].annotation }}
        </p>
        <p v-if="reviewMap[keyOf(item)].question?.answer">
          参考答案：{{ reviewMap[keyOf(item)].question.answer }}
        </p>
      </div>
    </article>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { questionTypeLabel } from '@/utils/question'

const props = defineProps({
  questions: { type: Array, default: () => [] },
  modelValue: { type: Object, default: () => ({}) },
  readonly: { type: Boolean, default: false },
  reviewItems: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:modelValue'])

const answers = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const showReview = computed(() => props.reviewItems.length > 0)

const reviewMap = computed(() => {
  const map = {}
  props.reviewItems.forEach((item) => {
    const q = item.question
    if (!q) return
    map[`${q.questionType}-${q.questionId}`] = item
  })
  return map
})

function keyOf(item) {
  const q = item.question || {}
  return `${q.questionType}-${q.questionId}`
}

function setAnswer(item, value) {
  const key = keyOf(item)
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}

function formatScore(v) {
  if (v === null || v === undefined || v === '') return '-'
  return Number(v).toFixed(1)
}

function judgeLabel(v) {
  if (v === 'true' || v === '正确') return '正确'
  if (v === 'false' || v === '错误') return '错误'
  return v || '未作答'
}

function isPendingAnnotation(annotation) {
  return annotation === '待教师批改' || annotation === '待批阅'
}

function reviewStatusText(item, reviewItem) {
  if (Number(item.question?.questionType) !== 3) return ''
  const ann = reviewItem.annotation
  if (!ann || ann === '已批阅') return '已批阅'
  if (isPendingAnnotation(ann)) return '待批阅'
  return ''
}

function buildAnswerList() {
  return props.questions.map((item) => {
    const q = item.question || {}
    return {
      questionId: q.questionId,
      questionType: q.questionType,
      studentAnswer: answers.value[keyOf(item)] ?? '',
    }
  })
}

defineExpose({ buildAnswerList })
</script>

<style scoped>
.question-paper {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.q-block {
  padding: 16px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid #f3f4f6;
}

.q-block__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.q-index {
  width: 24px;
  height: 24px;
  border-radius: 8px;
  background: var(--theme-hover-bg);
  color: var(--theme-primary);
  font-size: 12px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.q-score {
  margin-left: auto;
  font-size: 12px;
  color: #9ca3af;
}

.q-content {
  margin: 0 0 12px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.opt-group {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.opt-item {
  margin: 0;
}

.readonly-answer {
  padding: 10px 12px;
  border-radius: 10px;
  background: #f8fafc;
  color: #4b5563;
  font-size: 14px;
}

.readonly-answer.pre {
  white-space: pre-wrap;
}

.review-box {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(102, 204, 255, 0.08);
  font-size: 13px;
  color: #4b5563;
}

.review-box p {
  margin: 0 0 6px;
}
</style>

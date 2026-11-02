<template>
  <div class="page">
    <header class="page__header">
      <div>
        <h2>人工批阅</h2>
        <p>批改学生提交的解答题，支持考试与作业</p>
      </div>
    </header>

    <div class="toolbar">
      <el-radio-group v-model="taskType" @change="onTypeChange">
        <el-radio-button value="exam">考试</el-radio-button>
        <el-radio-button value="homework">作业</el-radio-button>
      </el-radio-group>
      <el-select v-model="selectedTaskId" placeholder="选择待批阅项" style="width: 280px" @change="loadPending">
        <el-option
          v-for="item in taskOptions"
          :key="item.id"
          :label="item.label"
          :value="item.id"
        />
      </el-select>
      <el-button text :loading="loading" @click="reloadTasks">刷新</el-button>
    </div>

    <el-skeleton v-if="loading && !pendingList.length" :rows="5" animated />

    <div v-else-if="!selectedTaskId" class="empty">请选择考试或作业</div>

    <div v-else-if="!pendingList.length" class="empty">暂无待批改的解答题</div>

    <div v-else class="grade-list">
      <article v-for="item in pendingList" :key="gradeKey(item)" class="grade-card">
        <header class="grade-card__head">
          <div>
            <h4>学生 {{ item.studentId }}</h4>
            <p>题目 ID {{ item.questionId }} · 满分 {{ formatScore(item.fullScore) }}</p>
          </div>
          <el-tag size="small">解答题</el-tag>
        </header>

        <div class="q-content">
          <h5>题干</h5>
          <p>{{ item.question?.content || '题目内容缺失' }}</p>
        </div>

        <div class="student-answer">
          <h5>学生答案</h5>
          <p>{{ item.studentAnswer || '（未作答）' }}</p>
        </div>

        <div class="grade-form">
          <el-form label-position="top" inline>
            <el-form-item label="得分">
              <el-input-number
                v-model="gradeForms[gradeKey(item)].score"
                :min="0"
                :max="Number(item.fullScore) || 100"
                :step="0.5"
              />
            </el-form-item>
            <el-form-item label="批注" class="annotation-item">
              <el-input
                v-model="gradeForms[gradeKey(item)].annotation"
                type="textarea"
                :rows="2"
                placeholder="可选批注"
              />
            </el-form-item>
          </el-form>
          <el-button
            type="primary"
            :loading="gradeForms[gradeKey(item)].loading"
            @click="submitGrade(item)"
          >
            提交批改
          </el-button>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { fetchExamsByTeacher, fetchExamPendingGrade, gradeExamAnswer } from '@/api/exam'
import { fetchHomeworksByTeacher, fetchHomeworkPendingGrade, gradeHomeworkAnswer } from '@/api/homework'

const userStore = useUserStore()

const taskType = ref('exam')
const loading = ref(false)
const taskOptions = ref([])
const selectedTaskId = ref(null)
const pendingList = ref([])
const gradeForms = reactive({})

function formatScore(v) {
  if (v === null || v === undefined || v === '') return '-'
  return Number(v).toFixed(1)
}

function gradeKey(item) {
  return `${item.studentId}-${item.questionId}-${item.questionType}`
}

function ensureForm(item) {
  const key = gradeKey(item)
  if (!gradeForms[key]) {
    gradeForms[key] = {
      score: 0,
      annotation: '',
      loading: false,
    }
  }
  return gradeForms[key]
}

async function reloadTasks() {
  loading.value = true
  try {
    const teacherId = userStore.userId
    if (taskType.value === 'exam') {
      const res = await fetchExamsByTeacher(teacherId)
      taskOptions.value = (res.data || []).map((e) => ({
        id: e.examId,
        label: `考试 #${e.examId}（课程 ${e.courseId}，${e.timeLimit} 分钟）`,
      }))
    } else {
      const res = await fetchHomeworksByTeacher(teacherId)
      taskOptions.value = (res.data || []).map((h) => ({
        id: h.homeworkId,
        label: `作业 #${h.homeworkId}（课程 ${h.courseId}）`,
      }))
    }
    if (!taskOptions.value.some((t) => t.id === selectedTaskId.value)) {
      selectedTaskId.value = taskOptions.value[0]?.id || null
    }
    await loadPending()
  } finally {
    loading.value = false
  }
}

async function loadPending() {
  if (!selectedTaskId.value) {
    pendingList.value = []
    return
  }
  loading.value = true
  try {
    const res = taskType.value === 'exam'
      ? await fetchExamPendingGrade(selectedTaskId.value)
      : await fetchHomeworkPendingGrade(selectedTaskId.value)
    pendingList.value = res.data || []
    pendingList.value.forEach((item) => ensureForm(item))
  } catch {
    pendingList.value = []
  } finally {
    loading.value = false
  }
}

function onTypeChange() {
  selectedTaskId.value = null
  pendingList.value = []
  reloadTasks()
}

async function submitGrade(item) {
  const form = ensureForm(item)
  const key = gradeKey(item)
  gradeForms[key].loading = true
  try {
    const payload = {
      questionId: item.questionId,
      questionType: item.questionType,
      studentId: item.studentId,
      score: form.score,
      annotation: form.annotation?.trim() || undefined,
    }
    if (taskType.value === 'exam') {
      payload.examId = selectedTaskId.value
      await gradeExamAnswer(payload)
    } else {
      payload.homeworkId = selectedTaskId.value
      await gradeHomeworkAnswer(payload)
    }
    ElMessage.success('批改成功')
    await loadPending()
  } catch {
    // handled
  } finally {
    gradeForms[key].loading = false
  }
}

onMounted(reloadTasks)
</script>

<style scoped>
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

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin: 20px 0 16px;
}

.empty {
  padding: 40px;
  text-align: center;
  color: #9ca3af;
  background: #fff;
  border-radius: 16px;
  border: 1px dashed #e5e7eb;
}

.grade-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.grade-card {
  padding: 18px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid #f3f4f6;
}

.grade-card__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.grade-card__head h4 {
  margin: 0 0 4px;
}

.grade-card__head p {
  margin: 0;
  font-size: 13px;
  color: #9ca3af;
}

.q-content,
.student-answer {
  margin-bottom: 12px;
}

.q-content h5,
.student-answer h5 {
  margin: 0 0 6px;
  font-size: 13px;
  color: #6b7280;
}

.q-content p,
.student-answer p {
  margin: 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f8fafc;
  white-space: pre-wrap;
  line-height: 1.5;
}

.grade-form {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 12px;
}

.annotation-item {
  min-width: 280px;
  flex: 1;
}
</style>

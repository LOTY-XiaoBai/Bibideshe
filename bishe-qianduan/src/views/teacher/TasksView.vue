<template>
  <div class="page">
    <header class="page__header">
      <div>
        <h2>发布作业 / 考试</h2>
        <p>从题库组卷，发布考试与作业</p>
      </div>
    </header>

    <div class="toolbar">
      <el-select v-model="selectedCourseId" placeholder="选择课程" style="width: 260px">
        <el-option
          v-for="c in courses"
          :key="c.courseId"
          :label="`${c.courseName} (${c.courseId})`"
          :value="c.courseId"
          :disabled="c.isFinished === 1"
        />
      </el-select>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="发布考试" name="exam" />
      <el-tab-pane label="发布作业" name="homework" />
    </el-tabs>

    <!-- 组卷区（考试/作业共用） -->
    <section v-if="activeTab === 'exam' || activeTab === 'homework'" class="publish-panel">
      <div class="panel-block">
        <h3>从题库选题</h3>
        <div class="filter-row">
          <el-radio-group v-model="bankTab" size="small" @change="loadQuestionBank">
            <el-radio-button value="mine">我的题库</el-radio-button>
            <el-radio-button value="public">公共题库</el-radio-button>
          </el-radio-group>
          <el-radio-group v-model="typeFilter" size="small">
            <el-radio-button :value="0">全部</el-radio-button>
            <el-radio-button :value="1">选择</el-radio-button>
            <el-radio-button :value="2">判断</el-radio-button>
            <el-radio-button :value="3">解答</el-radio-button>
          </el-radio-group>
        </div>
        <div v-loading="bankLoading" class="bank-list">
          <div
            v-for="q in filteredBank"
            :key="`${q.questionType}-${q.questionId}`"
            class="bank-item"
          >
            <div class="bank-item__info">
              <el-tag size="small">{{ questionTypeLabel(q.questionType) }}</el-tag>
              <span>ID {{ q.questionId }}</span>
              <p>{{ q.content }}</p>
            </div>
            <el-button size="small" @click="addQuestion(q)">加入试卷</el-button>
          </div>
          <p v-if="!bankLoading && !filteredBank.length" class="empty-tip">暂无题目</p>
        </div>
      </div>

      <div class="panel-block">
        <h3>试卷题目（{{ selectedQuestions.length }} 道）</h3>
        <div v-if="!selectedQuestions.length" class="empty-tip">请从左侧题库添加题目</div>
        <div v-else class="selected-list">
          <div
            v-for="(q, idx) in selectedQuestions"
            :key="`${q.questionType}-${q.questionId}`"
            class="selected-item"
          >
            <div>
              <strong>{{ idx + 1 }}. [{{ questionTypeLabel(q.questionType) }}] {{ q.content }}</strong>
            </div>
            <el-input-number v-model="q.score" :min="0.5" :max="100" :step="0.5" size="small" />
            <el-button type="danger" link @click="removeQuestion(idx)">移除</el-button>
          </div>
        </div>

        <el-form v-if="activeTab === 'exam'" label-position="top" class="publish-form">
          <el-form-item label="考试限时（分钟）">
            <el-input-number v-model="examTimeLimit" :min="5" :max="300" />
          </el-form-item>
          <el-form-item>
            <el-checkbox v-model="hideResultUntilPublish" :true-value="1" :false-value="0">
              成绩/答案需教师公布后学生才可查看
            </el-checkbox>
          </el-form-item>
        </el-form>

        <el-form v-else label-position="top" class="publish-form">
          <el-form-item label="作业截止时间">
            <el-date-picker
              v-model="homeworkDeadline"
              type="datetime"
              placeholder="选择截止时间"
              value-format="YYYY-MM-DDTHH:mm:ss"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item>
            <el-checkbox v-model="hideResultUntilPublish" :true-value="1" :false-value="0">
              成绩/答案需教师公布后学生才可查看
            </el-checkbox>
          </el-form-item>
        </el-form>

        <el-button
          type="primary"
          class="publish-btn"
          :loading="publishing"
          :disabled="!selectedCourseId || !selectedQuestions.length"
          @click="handlePublish"
        >
          {{ activeTab === 'exam' ? '发布考试' : '发布作业' }}
        </el-button>
      </div>
    </section>

    <section v-if="activeTab === 'exam' || activeTab === 'homework'" class="published-panel">
      <div class="history-head">
        <h3>{{ activeTab === 'exam' ? '已发布考试' : '已发布作业' }}</h3>
        <el-button text :loading="publishedLoading" @click="loadPublishedList">刷新</el-button>
      </div>
      <el-table
        v-loading="publishedLoading"
        :data="publishedList"
        size="small"
        empty-text="暂无记录"
      >
        <el-table-column :prop="activeTab === 'exam' ? 'examId' : 'homeworkId'" label="编号" width="80" />
        <el-table-column label="成绩公布" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.hideResultUntilPublish !== 1" type="info" size="small">提交后可看</el-tag>
            <el-tag v-else-if="row.isResultPublished === 1" type="success" size="small">已公布</el-tag>
            <el-tag v-else type="warning" size="small">待公布</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button
              v-if="row.hideResultUntilPublish === 1 && row.isResultPublished !== 1"
              size="small"
              type="primary"
              link
              @click="handlePublishResult(row)"
            >
              公布成绩
            </el-button>
            <el-button size="small" type="success" link @click="openScoreRank(row)">
              成绩排名
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog
      v-model="rankVisible"
      :title="rankTitle"
      width="640px"
      destroy-on-close
    >
      <el-table
        v-loading="rankLoading"
        :data="rankList"
        size="small"
        empty-text="暂无提交记录"
        highlight-current-row
        @row-click="openStudentReview"
      >
        <el-table-column prop="rank" label="排名" width="70" />
        <el-table-column prop="studentId" label="学生 ID" width="120" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="finalScore" label="得分" width="90" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="openStudentReview(row)">
              查看作答
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <p class="rank-tip">点击学生行可查看其作答详情</p>
    </el-dialog>

    <el-dialog
      v-model="studentReviewVisible"
      :title="studentReviewTitle"
      width="760px"
      top="4vh"
      destroy-on-close
    >
      <div v-if="studentReviewSummary" class="review-summary">
        <p>总得分：{{ studentReviewSummary.score ?? '-' }}</p>
      </div>
      <QuestionPaper
        :questions="studentReviewQuestions"
        :model-value="studentReviewAnswers"
        readonly
        :review-items="studentReviewItems"
      />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchTeacherCourses } from '@/api/course'
import { fetchMyQuestions, fetchPublicQuestions } from '@/api/question'
import { publishExam, fetchExamsByCourse, publishExamResult, fetchExamScores, reviewExamForTeacher } from '@/api/exam'
import {
  publishHomework,
  fetchHomeworksByCourse,
  publishHomeworkResult,
  fetchHomeworkScores,
  reviewHomeworkForTeacher,
} from '@/api/homework'
import { questionTypeLabel } from '@/utils/question'
import QuestionPaper from '@/components/QuestionPaper.vue'

const activeTab = ref('exam')
const courses = ref([])
const selectedCourseId = ref(null)

const bankTab = ref('mine')
const typeFilter = ref(0)
const bankLoading = ref(false)
const questionBank = ref([])
const selectedQuestions = ref([])

const examTimeLimit = ref(60)
const homeworkDeadline = ref('')
const hideResultUntilPublish = ref(0)
const publishing = ref(false)
const publishedLoading = ref(false)
const publishedList = ref([])

const rankVisible = ref(false)
const rankLoading = ref(false)
const rankList = ref([])
const rankTitle = ref('成绩排名')
const currentRankTarget = ref(null)

const studentReviewVisible = ref(false)
const studentReviewTitle = ref('')
const studentReviewQuestions = ref([])
const studentReviewAnswers = ref({})
const studentReviewItems = ref([])
const studentReviewSummary = ref(null)

const filteredBank = computed(() => {
  if (!typeFilter.value) return questionBank.value
  return questionBank.value.filter((q) => Number(q.questionType) === typeFilter.value)
})

function formatTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 16)
}

async function loadCourses() {
  const res = await fetchTeacherCourses()
  courses.value = res.data || []
  if (!selectedCourseId.value && courses.value.length) {
    const active = courses.value.find((c) => c.isFinished !== 1)
    selectedCourseId.value = active?.courseId || courses.value[0].courseId
  }
}

async function loadQuestionBank() {
  bankLoading.value = true
  try {
    const res = bankTab.value === 'mine'
      ? await fetchMyQuestions()
      : await fetchPublicQuestions()
    questionBank.value = res.data || []
  } catch {
    questionBank.value = []
  } finally {
    bankLoading.value = false
  }
}

function addQuestion(q) {
  const exists = selectedQuestions.value.some(
    (item) => item.questionId === q.questionId && item.questionType === q.questionType,
  )
  if (exists) {
    ElMessage.warning('该题已在试卷中')
    return
  }
  selectedQuestions.value.push({
    questionId: q.questionId,
    questionType: q.questionType,
    content: q.content,
    score: 5,
  })
}

function removeQuestion(idx) {
  selectedQuestions.value.splice(idx, 1)
}

async function handlePublish() {
  if (!selectedCourseId.value) {
    ElMessage.warning('请选择课程')
    return
  }
  if (!selectedQuestions.value.length) {
    ElMessage.warning('请至少选择一道题')
    return
  }
  publishing.value = true
  try {
    const questions = selectedQuestions.value.map((q) => ({
      questionId: q.questionId,
      questionType: q.questionType,
      score: q.score,
    }))
    if (activeTab.value === 'exam') {
      await publishExam({
        courseId: selectedCourseId.value,
        timeLimit: examTimeLimit.value,
        hideResultUntilPublish: hideResultUntilPublish.value,
        questions,
      })
      ElMessage.success('考试发布成功')
    } else {
      if (!homeworkDeadline.value) {
        ElMessage.warning('请设置作业截止时间')
        return
      }
      await publishHomework({
        courseId: selectedCourseId.value,
        deadline: homeworkDeadline.value,
        hideResultUntilPublish: hideResultUntilPublish.value,
        questions,
      })
      ElMessage.success('作业发布成功')
    }
    selectedQuestions.value = []
    await loadPublishedList()
  } catch {
    // handled
  } finally {
    publishing.value = false
  }
}

watch(selectedCourseId, () => {
  if (activeTab.value === 'exam' || activeTab.value === 'homework') loadPublishedList()
})

watch(activeTab, (tab) => {
  selectedQuestions.value = []
  if (tab === 'exam' || tab === 'homework') loadPublishedList()
})

async function loadPublishedList() {
  if (!selectedCourseId.value) {
    publishedList.value = []
    return
  }
  publishedLoading.value = true
  try {
    const res = activeTab.value === 'exam'
      ? await fetchExamsByCourse(selectedCourseId.value)
      : await fetchHomeworksByCourse(selectedCourseId.value)
    publishedList.value = res.data || []
  } catch {
    publishedList.value = []
  } finally {
    publishedLoading.value = false
  }
}

async function handlePublishResult(row) {
  try {
    if (activeTab.value === 'exam') {
      await publishExamResult(row.examId)
    } else {
      await publishHomeworkResult(row.homeworkId)
    }
    ElMessage.success('成绩已公布')
    await loadPublishedList()
  } catch {
    // handled
  }
}

async function openScoreRank(row) {
  currentRankTarget.value = row
  const isExam = activeTab.value === 'exam'
  const id = isExam ? row.examId : row.homeworkId
  rankTitle.value = `${isExam ? '考试' : '作业'} #${id} 成绩排名`
  rankVisible.value = true
  rankLoading.value = true
  try {
    const res = isExam ? await fetchExamScores(id) : await fetchHomeworkScores(id)
    rankList.value = res.data || []
  } catch {
    rankList.value = []
  } finally {
    rankLoading.value = false
  }
}

function answerKey(q) {
  return `${q.questionType}-${q.questionId}`
}

async function openStudentReview(row) {
  if (!row?.studentId || !currentRankTarget.value) return
  const isExam = activeTab.value === 'exam'
  const targetId = isExam ? currentRankTarget.value.examId : currentRankTarget.value.homeworkId
  try {
    const res = isExam
      ? await reviewExamForTeacher(targetId, row.studentId)
      : await reviewHomeworkForTeacher(targetId, row.studentId)
    const data = res.data || {}
    const answers = data.answers || []
    studentReviewQuestions.value = answers.map((item) => ({
      question: item.question,
      score: item.fullScore,
    }))
    studentReviewItems.value = answers
    const map = {}
    answers.forEach((item) => {
      if (item.question) map[answerKey(item.question)] = item.studentAnswer || ''
    })
    studentReviewAnswers.value = map
    studentReviewSummary.value = {
      score: data.teacherScore ?? data.examScore ?? data.homeworkScore ?? row.finalScore,
    }
    studentReviewTitle.value = `${row.nickname || row.studentId} 的作答 · ${isExam ? '考试' : '作业'} #${targetId}`
    studentReviewVisible.value = true
  } catch {
    // handled
  }
}

onMounted(async () => {
  await loadCourses()
  await loadQuestionBank()
  if (activeTab.value === 'exam' || activeTab.value === 'homework') await loadPublishedList()
})
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
  margin: 20px 0 12px;
}

.publish-panel {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.panel-block {
  padding: 16px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid #f3f4f6;
}

.panel-block h3 {
  margin: 0 0 12px;
  font-size: 15px;
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}

.bank-list,
.selected-list {
  max-height: 360px;
  overflow-y: auto;
}

.bank-item,
.selected-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f3f4f6;
}

.bank-item__info p,
.selected-item strong {
  margin: 6px 0 0;
  font-size: 13px;
  color: #4b5563;
  font-weight: 500;
}

.bank-item__info span {
  margin-left: 8px;
  font-size: 12px;
  color: #9ca3af;
}

.empty-tip {
  padding: 24px;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
}

.publish-form {
  margin-top: 16px;
}

.publish-btn {
  width: 100%;
  margin-top: 12px;
  background: var(--theme-gradient) !important;
  border: none !important;
}

.history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.history-head h3 {
  margin: 0;
  font-size: 15px;
}

.published-panel {
  margin-top: 16px;
  padding: 16px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid #f3f4f6;
}

.muted {
  color: #9ca3af;
  font-size: 12px;
}

.rank-tip {
  margin: 12px 0 0;
  color: #6b7280;
  font-size: 13px;
}

.review-summary {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f8fafc;
  color: #334155;
}

@media (max-width: 960px) {
  .publish-panel {
    grid-template-columns: 1fr;
  }
}
</style>

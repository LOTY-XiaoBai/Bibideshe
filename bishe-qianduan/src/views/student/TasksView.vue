<template>
  <div class="page">
    <header class="page__header">
      <div>
        <h2>考试与作业</h2>
        <p>参加考试、提交作业，并完成课程签到</p>
      </div>
    </header>

    <div class="toolbar">
      <el-select v-model="selectedCourseId" placeholder="选择课程" style="width: 260px" @change="onCourseChange">
        <el-option
          v-for="c in courses"
          :key="c.courseId"
          :label="`${c.courseName} (${c.courseId})`"
          :value="c.courseId"
          :disabled="c.isFinished === 1"
        />
      </el-select>
      <el-button text :loading="loading" @click="reloadAll">刷新</el-button>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="考试" name="exam" />
      <el-tab-pane label="作业" name="homework" />
      <el-tab-pane label="签到" name="checkin" />
    </el-tabs>

    <!-- 考试 -->
    <section v-show="activeTab === 'exam'" v-loading="loading">
      <div v-if="!selectedCourseId" class="empty">请先选择课程</div>
      <div v-else-if="!exams.length" class="empty">该课程暂无考试</div>
      <div v-else class="task-list">
        <article v-for="exam in exams" :key="exam.examId" class="task-card">
          <div>
            <h4>考试 #{{ exam.examId }}</h4>
            <p>限时 {{ exam.timeLimit }} 分钟</p>
            <p v-if="examRecordMap[exam.examId] && canViewResult(exam)">
              得分：{{ formatScore(examRecordMap[exam.examId].teacherScore ?? examRecordMap[exam.examId].examScore) }}
            </p>
            <p v-else-if="examRecordMap[exam.examId] && exam.hideResultUntilPublish === 1" class="hint-text">
              成绩待教师公布
            </p>
          </div>
          <div class="task-actions">
            <el-button
              v-if="!examRecordMap[exam.examId]"
              type="primary"
              @click="openExam(exam)"
            >
              开始考试
            </el-button>
            <template v-else>
              <el-tag type="success" size="small">已提交</el-tag>
              <el-button
                v-if="canViewResult(exam)"
                size="small"
                @click="viewExamReview(exam.examId)"
              >
                查看复盘
              </el-button>
              <el-tag v-else size="small" type="warning">待公布</el-tag>
            </template>
          </div>
        </article>
      </div>
    </section>

    <!-- 作业 -->
    <section v-show="activeTab === 'homework'" v-loading="loading">
      <div v-if="!selectedCourseId" class="empty">请先选择课程</div>
      <div v-else-if="!homeworks.length" class="empty">该课程暂无作业</div>
      <div v-else class="task-list">
        <article v-for="hw in homeworks" :key="hw.homeworkId" class="task-card">
          <div>
            <h4>作业 #{{ hw.homeworkId }}</h4>
            <p>截止：{{ formatTime(hw.deadline) }}</p>
            <p v-if="homeworkRecordMap[hw.homeworkId] && canViewResult(hw)">
              得分：{{ formatScore(homeworkRecordMap[hw.homeworkId].teacherScore ?? homeworkRecordMap[hw.homeworkId].homeworkScore) }}
            </p>
            <p v-else-if="homeworkRecordMap[hw.homeworkId] && hw.hideResultUntilPublish === 1" class="hint-text">
              成绩待教师公布
            </p>
          </div>
          <div class="task-actions">
            <el-button
              v-if="!homeworkRecordMap[hw.homeworkId]"
              type="primary"
              :disabled="isPast(hw.deadline)"
              @click="openHomework(hw)"
            >
              {{ isPast(hw.deadline) ? '已截止' : '开始作答' }}
            </el-button>
            <template v-else>
              <el-tag type="success" size="small">已提交</el-tag>
              <el-button
                v-if="canViewResult(hw)"
                size="small"
                @click="viewHomeworkReview(hw.homeworkId)"
              >
                查看复盘
              </el-button>
              <el-tag v-else size="small" type="warning">待公布</el-tag>
            </template>
          </div>
        </article>
      </div>
    </section>

    <!-- 签到 -->
    <section v-show="activeTab === 'checkin'">
      <el-tabs v-model="checkinSubTab" class="sub-tabs">
        <el-tab-pane label="可签到" name="active" />
        <el-tab-pane label="签到记录" name="records" />
        <el-tab-pane label="漏签记录" name="missed" />
      </el-tabs>

      <div v-if="checkinSubTab === 'active'" v-loading="checkinLoading">
        <div v-if="!activeCheckIns.length" class="empty">当前没有进行中的签到</div>
        <div v-else class="task-list">
          <article v-for="item in activeCheckIns" :key="item.checkInId" class="task-card">
            <div>
              <h4>{{ item.courseName || `课程 ${item.courseId}` }}</h4>
              <p>签到 #{{ item.checkInId }} · 截止 {{ formatTime(item.deadline) }}</p>
            </div>
            <el-button type="primary" :loading="signingId === item.checkInId" @click="handleSign(item.checkInId)">
              立即签到
            </el-button>
          </article>
        </div>
      </div>

      <div v-else-if="checkinSubTab === 'records'" v-loading="checkinLoading">
        <el-table :data="checkinRecords" size="small" empty-text="暂无签到记录">
          <el-table-column prop="courseName" label="课程" />
          <el-table-column prop="checkInId" label="签到号" width="90" />
          <el-table-column label="发布时间" min-width="140">
            <template #default="{ row }">{{ formatTime(row.publishTime) }}</template>
          </el-table-column>
          <el-table-column label="签到时间" min-width="140">
            <template #default="{ row }">{{ formatTime(row.checkInTime) || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="checkinStatusType(row.status)" size="small">
                {{ checkinStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-else v-loading="checkinLoading">
        <div class="toolbar" v-if="selectedCourseId">
          <span class="hint">当前课程漏签</span>
        </div>
        <el-table :data="missedRecords" size="small" empty-text="暂无漏签记录">
          <el-table-column prop="courseName" label="课程" />
          <el-table-column prop="checkInId" label="签到号" width="90" />
          <el-table-column label="发布时间" min-width="140">
            <template #default="{ row }">{{ formatTime(row.publishTime) }}</template>
          </el-table-column>
          <el-table-column label="截止时间" min-width="140">
            <template #default="{ row }">{{ formatTime(row.deadline) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <!-- 考试作答 -->
    <el-dialog
      v-model="examVisible"
      :title="`考试 #${currentExam?.examId || ''}`"
      width="760px"
      top="4vh"
      destroy-on-close
      :close-on-click-modal="false"
      @closed="clearExamTimer"
    >
      <div v-if="examSession" class="timer-bar">
        <i class="ph ph-timer" />
        剩余时间：{{ formatCountdown(remainSeconds) }}
      </div>
      <QuestionPaper
        ref="examPaperRef"
        v-model="examAnswers"
        :questions="examQuestions"
      />
      <template #footer>
        <el-button @click="examVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitExamPaper">提交考试</el-button>
      </template>
    </el-dialog>

    <!-- 作业作答 -->
    <el-dialog
      v-model="homeworkVisible"
      :title="`作业 #${currentHomework?.homeworkId || ''}`"
      width="760px"
      top="4vh"
      destroy-on-close
    >
      <p class="deadline-tip">截止时间：{{ formatTime(currentHomework?.deadline) }}</p>
      <QuestionPaper
        ref="homeworkPaperRef"
        v-model="homeworkAnswers"
        :questions="homeworkQuestions"
      />
      <template #footer>
        <el-button @click="homeworkVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitHomeworkPaper">提交作业</el-button>
      </template>
    </el-dialog>

    <!-- 复盘 -->
    <el-dialog v-model="reviewVisible" :title="reviewTitle" width="760px" top="4vh" destroy-on-close>
      <div v-if="reviewSummary" class="review-summary">
        <p>总得分：{{ formatScore(reviewSummary.score) }}</p>
      </div>
      <QuestionPaper
        :questions="reviewQuestions"
        :model-value="reviewAnswers"
        readonly
        :review-items="reviewItems"
      />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { fetchStudentCourses } from '@/api/course'
import {
  fetchExamsByCourse,
  fetchExamDetail,
  startExam,
  submitExam,
  reviewExam,
  fetchStudentExamRecords,
} from '@/api/exam'
import {
  fetchHomeworksByCourse,
  fetchHomeworkDetail,
  submitHomework,
  reviewHomework,
  fetchStudentHomeworkRecords,
} from '@/api/homework'
import {
  signCheckIn,
  fetchStudentCheckInRecords,
  fetchMissedByCourse,
  fetchAllMissed,
} from '@/api/checkIn'
import QuestionPaper from '@/components/QuestionPaper.vue'

const userStore = useUserStore()

const activeTab = ref('exam')
const checkinSubTab = ref('active')
const loading = ref(false)
const checkinLoading = ref(false)
const submitting = ref(false)
const signingId = ref(null)

const courses = ref([])
const selectedCourseId = ref(null)
const exams = ref([])
const homeworks = ref([])
const examRecordMap = reactive({})
const homeworkRecordMap = reactive({})
const checkinRecords = ref([])
const missedRecords = ref([])

const examVisible = ref(false)
const homeworkVisible = ref(false)
const reviewVisible = ref(false)
const currentExam = ref(null)
const currentHomework = ref(null)
const examQuestions = ref([])
const homeworkQuestions = ref([])
const examAnswers = ref({})
const homeworkAnswers = ref({})
const examPaperRef = ref(null)
const homeworkPaperRef = ref(null)
const examSession = ref(null)
const remainSeconds = ref(0)
let examTimer = null

const reviewTitle = ref('作答复盘')
const reviewQuestions = ref([])
const reviewAnswers = ref({})
const reviewItems = ref([])
const reviewSummary = ref(null)

const activeCheckIns = computed(() =>
  checkinRecords.value.filter(
    (r) => Number(r.checkInStatus) === 0 && Number(r.status) === 0,
  ),
)

function formatScore(v) {
  if (v === null || v === undefined || v === '') return '-'
  return Number(v).toFixed(1)
}

function formatTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 16)
}

function isPast(t) {
  if (!t) return false
  return new Date(t).getTime() < Date.now()
}

function formatCountdown(sec) {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function checkinStatusLabel(s) {
  if (s === 1) return '已签到'
  if (s === 2) return '迟到'
  return '缺勤'
}

function checkinStatusType(s) {
  if (s === 1) return 'success'
  if (s === 2) return 'warning'
  return 'danger'
}

function canViewResult(task) {
  if (!task || task.hideResultUntilPublish !== 1) return true
  return task.isResultPublished === 1
}

function answerKey(q) {
  return `${q.questionType}-${q.questionId}`
}

async function loadCourses() {
  const res = await fetchStudentCourses()
  courses.value = res.data || []
  if (!selectedCourseId.value && courses.value.length) {
    const active = courses.value.find((c) => c.isFinished !== 1)
    selectedCourseId.value = active?.courseId || courses.value[0].courseId
  }
}

async function loadExamData() {
  if (!selectedCourseId.value) return
  const [examRes, recordRes] = await Promise.all([
    fetchExamsByCourse(selectedCourseId.value),
    fetchStudentExamRecords(userStore.userId),
  ])
  exams.value = examRes.data || []
  Object.keys(examRecordMap).forEach((k) => delete examRecordMap[k])
  ;(recordRes.data || []).forEach((r) => {
    examRecordMap[r.examId] = r
  })
}

async function loadHomeworkData() {
  if (!selectedCourseId.value) return
  const [hwRes, recordRes] = await Promise.all([
    fetchHomeworksByCourse(selectedCourseId.value),
    fetchStudentHomeworkRecords(userStore.userId),
  ])
  homeworks.value = hwRes.data || []
  Object.keys(homeworkRecordMap).forEach((k) => delete homeworkRecordMap[k])
  ;(recordRes.data || []).forEach((r) => {
    homeworkRecordMap[r.homeworkId] = r
  })
}

async function loadCheckinData() {
  checkinLoading.value = true
  try {
    const sid = userStore.userId
    const [recordRes, missedRes] = await Promise.all([
      fetchStudentCheckInRecords(sid, 1, 100),
      selectedCourseId.value
        ? fetchMissedByCourse(sid, selectedCourseId.value)
        : fetchAllMissed(sid, 1, 100),
    ])
    checkinRecords.value = recordRes.data?.records || []
    missedRecords.value = selectedCourseId.value
      ? missedRes.data || []
      : missedRes.data?.records || []
  } catch {
    checkinRecords.value = []
    missedRecords.value = []
  } finally {
    checkinLoading.value = false
  }
}

async function reloadAll() {
  loading.value = true
  try {
    await loadCourses()
    await Promise.all([loadExamData(), loadHomeworkData(), loadCheckinData()])
  } finally {
    loading.value = false
  }
}

function onCourseChange() {
  loading.value = true
  Promise.all([loadExamData(), loadHomeworkData(), loadCheckinData()]).finally(() => {
    loading.value = false
  })
}

function clearExamTimer() {
  if (examTimer) {
    clearInterval(examTimer)
    examTimer = null
  }
  examSession.value = null
}

function startExamCountdown(deadline) {
  clearExamTimer()
  const end = new Date(deadline).getTime()
  const tick = () => {
    const left = Math.max(0, Math.floor((end - Date.now()) / 1000))
    remainSeconds.value = left
    if (left <= 0) {
      clearExamTimer()
      ElMessage.warning('考试时间到，请尽快提交')
    }
  }
  tick()
  examTimer = setInterval(tick, 1000)
}

async function openExam(exam) {
  try {
    const [startRes, detailRes] = await Promise.all([
      startExam(exam.examId),
      fetchExamDetail(exam.examId),
    ])
    currentExam.value = exam
    examSession.value = startRes.data
    examQuestions.value = detailRes.data?.questions || []
    examAnswers.value = {}
    examVisible.value = true
    if (startRes.data?.deadline) {
      startExamCountdown(startRes.data.deadline)
    }
  } catch {
    // handled
  }
}

async function submitExamPaper() {
  if (!currentExam.value || !examPaperRef.value) return
  try {
    await ElMessageBox.confirm('确认提交考试？提交后不可修改', '提交确认', { type: 'warning' })
    submitting.value = true
    const answers = examPaperRef.value.buildAnswerList()
    const res = await submitExam({
      examId: currentExam.value.examId,
      answers,
    })
    ElMessage.success('考试提交成功')
    examVisible.value = false
    await loadExamData()
    if (res.data && canViewResult(currentExam.value)) {
      showReview(res.data, '考试复盘', examQuestions.value)
    } else if (currentExam.value?.hideResultUntilPublish === 1) {
      ElMessage.info('已提交，成绩待教师公布后可见')
    }
  } catch {
    // cancel or error
  } finally {
    submitting.value = false
  }
}

async function openHomework(hw) {
  try {
    const res = await fetchHomeworkDetail(hw.homeworkId)
    currentHomework.value = hw
    homeworkQuestions.value = res.data?.questions || []
    homeworkAnswers.value = {}
    homeworkVisible.value = true
  } catch {
    // handled
  }
}

async function submitHomeworkPaper() {
  if (!currentHomework.value || !homeworkPaperRef.value) return
  try {
    await ElMessageBox.confirm('确认提交作业？', '提交确认', { type: 'warning' })
    submitting.value = true
    const answers = homeworkPaperRef.value.buildAnswerList()
    const res = await submitHomework({
      homeworkId: currentHomework.value.homeworkId,
      answers,
    })
    ElMessage.success('作业提交成功')
    homeworkVisible.value = false
    await loadHomeworkData()
    if (res.data && canViewResult(currentHomework.value)) {
      showReview(res.data, '作业复盘', homeworkQuestions.value)
    } else if (currentHomework.value?.hideResultUntilPublish === 1) {
      ElMessage.info('已提交，成绩待教师公布后可见')
    }
  } catch {
    // cancel or error
  } finally {
    submitting.value = false
  }
}

function showReview(data, title, questions) {
  reviewTitle.value = title
  reviewQuestions.value = questions
  reviewItems.value = data.answers || []
  const map = {}
  ;(data.answers || []).forEach((item) => {
    const q = item.question
    if (q) map[answerKey(q)] = item.studentAnswer || ''
  })
  reviewAnswers.value = map
  reviewSummary.value = {
    score: data.teacherScore ?? data.examScore ?? data.homeworkScore,
  }
  reviewVisible.value = true
}

async function viewExamReview(examId) {
  try {
    const reviewRes = await reviewExam(examId)
    const questions = (reviewRes.data?.answers || []).map((item) => ({
      score: item.fullScore,
      question: item.question,
    }))
    showReview(reviewRes.data, '考试复盘', questions)
  } catch {
    // handled
  }
}

async function viewHomeworkReview(homeworkId) {
  try {
    const reviewRes = await reviewHomework(homeworkId)
    const questions = (reviewRes.data?.answers || []).map((item) => ({
      score: item.fullScore,
      question: item.question,
    }))
    showReview(reviewRes.data, '作业复盘', questions)
  } catch {
    // handled
  }
}

async function handleSign(checkInId) {
  signingId.value = checkInId
  try {
    await signCheckIn(checkInId)
    ElMessage.success('签到成功')
    await loadCheckinData()
  } catch {
    // handled
  } finally {
    signingId.value = null
  }
}

onMounted(reloadAll)
onUnmounted(clearExamTimer)
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
  align-items: center;
  gap: 12px;
  margin: 20px 0 12px;
}

.hint {
  font-size: 13px;
  color: #9ca3af;
}

.sub-tabs {
  margin-bottom: 12px;
}

.empty {
  padding: 40px;
  text-align: center;
  color: #9ca3af;
  background: #fff;
  border-radius: 16px;
  border: 1px dashed #e5e7eb;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.task-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid #f3f4f6;
}

.task-card h4 {
  margin: 0 0 6px;
}

.task-card p {
  margin: 0 0 4px;
  font-size: 13px;
  color: #9ca3af;
}

.task-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.timer-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  padding: 10px 14px;
  border-radius: 10px;
  background: rgba(102, 204, 255, 0.12);
  color: #0891b2;
  font-weight: 600;
}

.deadline-tip {
  margin: 0 0 14px;
  color: #6b7280;
  font-size: 13px;
}

.review-summary {
  margin-bottom: 14px;
  padding: 10px 14px;
  border-radius: 10px;
  background: var(--theme-hover-bg);
  font-weight: 600;
}

.hint-text {
  color: #f59e0b !important;
}
</style>

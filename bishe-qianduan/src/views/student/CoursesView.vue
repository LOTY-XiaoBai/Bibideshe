<template>
  <div class="page">
    <header class="page__header">
      <div>
        <h2>我的课程</h2>
        <p>通过课程号搜索加入班级，查看得分并给教师评分</p>
      </div>
    </header>

    <section class="search-bar">
      <el-input
        v-model="searchId"
        placeholder="输入课程号搜索"
        clearable
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <i class="ph ph-magnifying-glass" />
        </template>
      </el-input>
      <el-button type="primary" :loading="searching" @click="handleSearch">搜索</el-button>
    </section>

    <section v-if="searchResult" class="search-result">
      <div>
        <h3>{{ searchResult.courseName }}</h3>
        <p>
          课程号 {{ searchResult.courseId }} · 教师 ID {{ searchResult.teacherId }} ·
          {{ searchResult.isFinished === 1 ? '已结课' : '进行中' }}
        </p>
      </div>
      <el-button
        type="primary"
        :disabled="searchResult.isFinished === 1 || isJoined(searchResult.courseId)"
        :loading="joining"
        @click="handleJoin(searchResult.courseId)"
      >
        {{ isJoined(searchResult.courseId) ? '已加入' : '加入班级' }}
      </el-button>
    </section>

    <section class="list-section">
      <div class="list-section__title">
        <h3>已选课程</h3>
        <el-button text :loading="loading" @click="loadCourses">刷新</el-button>
      </div>

      <el-skeleton v-if="loading && !courses.length" :rows="4" animated />

      <div v-else-if="!courses.length" class="empty">暂未选修任何课程，请先搜索课程号加入</div>

      <div v-else class="course-grid">
        <article v-for="course in courses" :key="course.courseId" class="course-card">
          <div class="course-card__top">
            <div>
              <h4>{{ course.courseName }}</h4>
              <p>课程号 {{ course.courseId }}</p>
            </div>
            <el-tag :type="course.isFinished === 1 ? 'info' : 'success'" size="small">
              {{ course.isFinished === 1 ? '已结课' : '进行中' }}
            </el-tag>
          </div>

          <div class="score-row">
            <span>本课得分</span>
            <strong>{{ formatScore(scoreMap[course.courseId]) }}</strong>
          </div>

          <div class="actions">
            <el-button size="small" @click="openDetail(course)">详情</el-button>
            <el-button
              size="small"
              type="danger"
              plain
              :disabled="course.isFinished === 1"
              @click="handleLeave(course)"
            >
              退课
            </el-button>
          </div>
        </article>
      </div>
    </section>

    <el-drawer v-model="detailVisible" :title="detailCourse?.courseName || '课程详情'" size="420px">
      <template v-if="detailCourse">
        <div class="detail-block">
          <p><span>课程号</span>{{ detailCourse.courseId }}</p>
          <p><span>主讲教师</span>{{ teacherInfo?.teacherName || detailCourse.teacherId }}</p>
          <p><span>本课得分</span>{{ formatScore(scoreMap[detailCourse.courseId]) }}</p>
          <p>
            <span>教师评分</span>
            {{
              teacherInfo?.hasRating
                ? formatScore(teacherInfo.comprehensiveScore)
                : teacherInfo?.scoreTip || '暂未有人打分'
            }}
          </p>
          <p>
            <span>我的打分</span>
            {{ teacherInfo?.myScore != null ? formatScore(teacherInfo.myScore) : '尚未打分' }}
          </p>
        </div>

        <div class="rate-box">
          <h4>给教师打分（1-10）</h4>
          <el-slider v-model="rateScore" :min="1" :max="10" :step="0.5" show-stops />
          <el-button
            type="primary"
            class="rate-btn"
            :loading="rating"
            :disabled="teacherInfo?.myScore != null"
            @click="handleRate"
          >
            {{ teacherInfo?.myScore != null ? '已评分' : '提交评分' }}
          </el-button>
        </div>

        <div class="members">
          <h4>班级成员（{{ members.length }}）</h4>
          <el-table :data="members" size="small" empty-text="暂无成员">
            <el-table-column prop="studentId" label="学生 ID" />
            <el-table-column prop="nickname" label="昵称" />
            <el-table-column label="得分" width="90">
              <template #default="{ row }">
                {{ formatScore(row.studentScore) }}
              </template>
            </el-table-column>
          </el-table>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  searchCourse,
  joinCourse,
  leaveCourse,
  fetchStudentCourses,
  fetchMyCourseScores,
  fetchCourseMembers,
} from '@/api/course'
import { fetchCourseTeacher, rateTeacher } from '@/api/teacherEvaluation'

const loading = ref(false)
const searching = ref(false)
const joining = ref(false)
const rating = ref(false)
const searchId = ref('')
const searchResult = ref(null)
const courses = ref([])
const scoreMap = reactive({})
const detailVisible = ref(false)
const detailCourse = ref(null)
const teacherInfo = ref(null)
const members = ref([])
const rateScore = ref(8)

function formatScore(score) {
  if (score === null || score === undefined || score === '') return '-'
  return Number(score).toFixed(1)
}

function isJoined(courseId) {
  return courses.value.some((c) => String(c.courseId) === String(courseId))
}

async function loadCourses() {
  loading.value = true
  try {
    const [courseRes, scoreRes] = await Promise.all([
      fetchStudentCourses(),
      fetchMyCourseScores(),
    ])
    courses.value = courseRes.data || []
    Object.keys(scoreMap).forEach((key) => delete scoreMap[key])
    ;(scoreRes.data || []).forEach((item) => {
      scoreMap[item.courseId] = item.studentScore
    })
  } catch {
    courses.value = []
  } finally {
    loading.value = false
  }
}

async function handleSearch() {
  const id = searchId.value.trim()
  if (!id) {
    ElMessage.warning('请输入课程号')
    return
  }
  searching.value = true
  searchResult.value = null
  try {
    const res = await searchCourse(id)
    searchResult.value = res.data
  } catch {
    searchResult.value = null
  } finally {
    searching.value = false
  }
}

async function handleJoin(courseId) {
  joining.value = true
  try {
    await joinCourse(courseId)
    ElMessage.success('选课成功')
    await loadCourses()
  } catch {
    // handled
  } finally {
    joining.value = false
  }
}

async function handleLeave(course) {
  try {
    await ElMessageBox.confirm(
      `确认退出课程「${course.courseName}」？`,
      '退课确认',
      { type: 'warning' },
    )
    await leaveCourse(course.courseId)
    ElMessage.success('退课成功')
    await loadCourses()
  } catch {
    // cancel or error
  }
}

async function openDetail(course) {
  detailCourse.value = course
  detailVisible.value = true
  teacherInfo.value = null
  members.value = []
  rateScore.value = 8
  try {
    const [teacherRes, memberRes] = await Promise.all([
      fetchCourseTeacher(course.courseId),
      fetchCourseMembers(course.courseId),
    ])
    teacherInfo.value = teacherRes.data
    members.value = memberRes.data || []
    if (teacherRes.data?.myScore != null) {
      rateScore.value = Number(teacherRes.data.myScore)
    }
  } catch {
    // handled
  }
}

async function handleRate() {
  if (!detailCourse.value || !teacherInfo.value) return
  rating.value = true
  try {
    await rateTeacher({
      courseId: detailCourse.value.courseId,
      teacherId: teacherInfo.value.teacherId,
      score: rateScore.value,
    })
    ElMessage.success('评分成功')
    const res = await fetchCourseTeacher(detailCourse.value.courseId)
    teacherInfo.value = res.data
  } catch {
    // handled
  } finally {
    rating.value = false
  }
}

onMounted(loadCourses)
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

.search-bar {
  display: flex;
  gap: 12px;
  margin-top: 22px;
  max-width: 560px;
}

.search-bar .el-button {
  background: var(--theme-gradient) !important;
  border: none;
}

.search-result {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 16px;
  padding: 18px 20px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid rgba(102, 204, 255, 0.35);
  box-shadow: 0 6px 18px rgba(102, 204, 255, 0.12);
}

.search-result h3 {
  margin: 0 0 6px;
  font-size: 16px;
}

.search-result p {
  margin: 0;
  color: #6b7280;
  font-size: 13px;
}

.list-section {
  margin-top: 28px;
}

.list-section__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.list-section__title h3 {
  margin: 0;
  font-size: 16px;
}

.empty {
  padding: 40px;
  text-align: center;
  color: #9ca3af;
  background: #fff;
  border-radius: 16px;
  border: 1px dashed #e5e7eb;
}

.course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

.course-card {
  padding: 18px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid #f3f4f6;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.04);
}

.course-card__top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.course-card h4 {
  margin: 0 0 4px;
  font-size: 16px;
}

.course-card p {
  margin: 0;
  color: #9ca3af;
  font-size: 12px;
}

.score-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 18px 0 14px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(102, 204, 255, 0.08);
  color: #4b5563;
  font-size: 13px;
}

.score-row strong {
  color: #0891b2;
  font-size: 18px;
}

.actions {
  display: flex;
  gap: 8px;
}

.detail-block p {
  display: flex;
  justify-content: space-between;
  margin: 0 0 12px;
  font-size: 14px;
  color: #1f2937;
}

.detail-block span {
  color: #9ca3af;
}

.rate-box,
.members {
  margin-top: 24px;
}

.rate-box h4,
.members h4 {
  margin: 0 0 12px;
  font-size: 15px;
}

.rate-btn {
  width: 100%;
  margin-top: 12px;
  background: var(--theme-gradient) !important;
  border: none;
}
</style>

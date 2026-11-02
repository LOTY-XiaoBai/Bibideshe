<template>
  <div class="page">
    <header class="page__header">
      <div>
        <h2>我的授课</h2>
        <p>每位教师最多同时进行 3 门课程，结课课程不计入上限</p>
      </div>
      <el-button
        type="primary"
        class="create-btn"
        :disabled="activeCount >= 3"
        @click="openCreate"
      >
        <i class="ph ph-plus-circle" />
        创建新课程
      </el-button>
    </header>

    <div class="stat-row">
      <div class="stat">
        <span>进行中 / 上限</span>
        <strong>{{ activeCount }} / 3</strong>
      </div>
      <div class="stat">
        <span>进行中</span>
        <strong>{{ activeCount }}</strong>
      </div>
      <div class="stat">
        <span>已结课</span>
        <strong>{{ finishedCount }}</strong>
      </div>
    </div>

    <el-skeleton v-if="loading && !courses.length" :rows="5" animated />

    <div v-else-if="!courses.length" class="empty">
      尚未创建课程，点击右上角「创建新课程」开始教学
    </div>

    <div v-else class="course-list">
      <article v-for="course in courses" :key="course.courseId" class="course-card">
        <div class="course-card__main">
          <div>
            <div class="title-row">
              <h3>{{ course.courseName }}</h3>
              <el-tag :type="course.isFinished === 1 ? 'info' : 'danger'" size="small">
                {{ course.isFinished === 1 ? '已结课' : '进行中' }}
              </el-tag>
            </div>
            <p>课程号：{{ course.courseId }}（可分享给学生选课）</p>
          </div>
          <div class="ops">
            <el-button size="small" @click="openMembers(course)">班级成员</el-button>
            <el-button
              v-if="course.isFinished !== 1"
              size="small"
              type="warning"
              plain
              @click="handleFinish(course)"
            >
              结束课程
            </el-button>
            <el-button
              v-else
              size="small"
              type="success"
              plain
              @click="handleUnfinish(course)"
            >
              取消结课
            </el-button>
            <el-button size="small" type="danger" plain @click="handleDelete(course)">
              删除
            </el-button>
          </div>
        </div>
      </article>
    </div>

    <el-dialog v-model="createVisible" title="创建新课程" width="420px" align-center>
      <el-form @submit.prevent>
        <el-form-item label="课程名称">
          <el-input
            v-model="createName"
            maxlength="100"
            show-word-limit
            placeholder="例如：高等数学 A"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="membersVisible"
      :title="`${currentCourse?.courseName || ''} · 班级成员`"
      size="480px"
    >
      <el-table v-loading="membersLoading" :data="members" empty-text="暂无学生">
        <el-table-column prop="studentId" label="学生 ID" min-width="120" />
        <el-table-column prop="nickname" label="昵称" min-width="100" />
        <el-table-column label="得分" width="100">
          <template #default="{ row }">
            {{ formatScore(row.studentScore) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button
              type="danger"
              link
              :disabled="currentCourse?.isFinished === 1"
              @click="handleRemove(row)"
            >
              移除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createCourse,
  finishCourse,
  unfinishCourse,
  deleteCourse,
  removeStudent,
  fetchTeacherCourses,
  fetchCourseMembers,
} from '@/api/course'

const loading = ref(false)
const creating = ref(false)
const membersLoading = ref(false)
const courses = ref([])
const createVisible = ref(false)
const createName = ref('')
const membersVisible = ref(false)
const currentCourse = ref(null)
const members = ref([])

const activeCount = computed(
  () => courses.value.filter((c) => c.isFinished !== 1).length,
)
const finishedCount = computed(
  () => courses.value.filter((c) => c.isFinished === 1).length,
)

function formatScore(score) {
  if (score === null || score === undefined || score === '') return '-'
  return Number(score).toFixed(1)
}

async function loadCourses() {
  loading.value = true
  try {
    const res = await fetchTeacherCourses()
    courses.value = res.data || []
  } catch {
    courses.value = []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  createName.value = ''
  createVisible.value = true
}

async function handleCreate() {
  const name = createName.value.trim()
  if (!name) {
    ElMessage.warning('请输入课程名称')
    return
  }
  creating.value = true
  try {
    await createCourse({ courseName: name })
    ElMessage.success('课程创建成功')
    createVisible.value = false
    await loadCourses()
  } catch {
    // handled
  } finally {
    creating.value = false
  }
}

async function handleFinish(course) {
  try {
    await ElMessageBox.confirm(`确认结束「${course.courseName}」？`, '结束课程', {
      type: 'warning',
    })
    await finishCourse(course.courseId)
    ElMessage.success('课程已结束')
    await loadCourses()
  } catch {
    // cancel
  }
}

async function handleUnfinish(course) {
  try {
    await unfinishCourse(course.courseId)
    ElMessage.success('课程已恢复为进行中')
    await loadCourses()
  } catch {
    // handled
  }
}

async function handleDelete(course) {
  try {
    await ElMessageBox.confirm(
      `删除「${course.courseName}」将级联清理相关记录，确认继续？`,
      '删除课程',
      { type: 'error', confirmButtonText: '确认删除' },
    )
    await deleteCourse(course.courseId)
    ElMessage.success('课程已删除')
    await loadCourses()
  } catch {
    // cancel
  }
}

async function openMembers(course) {
  currentCourse.value = course
  membersVisible.value = true
  membersLoading.value = true
  members.value = []
  try {
    const res = await fetchCourseMembers(course.courseId)
    members.value = res.data || []
  } catch {
    members.value = []
  } finally {
    membersLoading.value = false
  }
}

async function handleRemove(row) {
  try {
    await ElMessageBox.confirm(
      `确认将学生 ${row.studentId} 移出班级？`,
      '移除学生',
      { type: 'warning' },
    )
    await removeStudent(currentCourse.value.courseId, row.studentId)
    ElMessage.success('已移除学生')
    const res = await fetchCourseMembers(currentCourse.value.courseId)
    members.value = res.data || []
  } catch {
    // cancel
  }
}

onMounted(loadCourses)
</script>

<style scoped>
.page__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
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

.create-btn {
  background: linear-gradient(135deg, #66ccff, #ee0000) !important;
  border: none;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin: 22px 0;
}

.stat {
  padding: 16px 18px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid #f3f4f6;
}

.stat span {
  display: block;
  color: #9ca3af;
  font-size: 12px;
  margin-bottom: 6px;
}

.stat strong {
  font-size: 22px;
  color: #1f2937;
}

.empty {
  padding: 48px;
  text-align: center;
  color: #9ca3af;
  background: #fff;
  border-radius: 16px;
  border: 1px dashed #e5e7eb;
}

.course-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.course-card {
  padding: 20px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid #f3f4f6;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.04);
}

.course-card__main {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.title-row h3 {
  margin: 0;
  font-size: 17px;
}

.course-card p {
  margin: 0;
  color: #6b7280;
  font-size: 13px;
}

.ops {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

@media (max-width: 800px) {
  .page__header,
  .course-card__main {
    flex-direction: column;
    align-items: stretch;
  }

  .ops {
    justify-content: flex-start;
  }
}
</style>

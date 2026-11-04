<template>
  <div class="page">
    <header class="page__header">
      <div>
        <h2>课程管理</h2>
        <p>浏览全部课程，可按课程号筛选后删除（级联删除全部记录）</p>
      </div>
    </header>

    <section class="panel">
      <div class="search-row">
        <el-input
          v-model="keyword"
          placeholder="输入课程号查询（可留空列出全部）"
          clearable
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="courseList" size="small" empty-text="暂无课程">
        <el-table-column prop="courseId" label="课程号" width="100" />
        <el-table-column prop="courseName" label="课程名" min-width="160" show-overflow-tooltip />
        <el-table-column prop="teacherId" label="教师 ID" width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.isFinished === 1" type="info" size="small">已结课</el-tag>
            <el-tag v-else type="success" size="small">进行中</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" type="danger" :loading="deletingId === row.courseId" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        class="pager"
        @current-change="loadCourses"
        @size-change="page = 1; loadCourses()"
      />
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteCourse, fetchAdminCourses } from '@/api/admin'

const keyword = ref('')
const courseList = ref([])
const loading = ref(false)
const deletingId = ref(null)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

function pageList(data) {
  return data?.list || data?.records || []
}

async function loadCourses() {
  loading.value = true
  try {
    const res = await fetchAdminCourses(keyword.value.trim(), page.value, pageSize.value)
    courseList.value = pageList(res.data)
    total.value = res.data?.total || 0
  } catch {
    courseList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadCourses()
}

function handleReset() {
  keyword.value = ''
  page.value = 1
  loadCourses()
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除课程「${row.courseName || row.courseId}」？所有考试/作业/签到记录将一并删除`,
      '危险操作',
      { type: 'warning' },
    )
    deletingId.value = row.courseId
    await deleteCourse(row.courseId)
    ElMessage.success('课程已删除')
    await loadCourses()
  } catch {
    // cancel
  } finally {
    deletingId.value = null
  }
}

onMounted(loadCourses)
</script>

<style scoped>
.page__header h2 {
  margin: 0;
  font-size: 24px;
}

.page__header p {
  margin: 8px 0 0;
  color: #6b7280;
}

.panel {
  margin-top: 20px;
  padding: 20px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid #f3f4f6;
}

.search-row {
  display: flex;
  gap: 12px;
  max-width: 640px;
  margin-bottom: 16px;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>

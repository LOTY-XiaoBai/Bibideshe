<template>
  <div class="page">
    <header class="page__header">
      <div>
        <h2>题库审核</h2>
        <p>审核教师申请公开的题目，支持通过、驳回与删除</p>
      </div>
      <el-button text :loading="loading" @click="loadList">刷新</el-button>
    </header>

    <el-table v-loading="loading" :data="pageData" size="small" empty-text="暂无待审核题目">
      <el-table-column prop="questionId" label="题目ID" width="90" />
      <el-table-column label="题型" width="90">
        <template #default="{ row }">{{ questionTypeLabel(row.questionType) }}</template>
      </el-table-column>
      <el-table-column prop="content" label="内容" min-width="220" show-overflow-tooltip />
      <el-table-column prop="publisherId" label="教师ID" width="120" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link type="success" size="small" @click="handleAudit(row, true)">通过</el-button>
          <el-button link type="warning" size="small" @click="handleAudit(row, false)">驳回</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      :page-size="pageSize"
      :total="list.length"
      layout="total, prev, pager, next"
      class="pager"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchPendingQuestions, auditQuestion, deleteQuestion } from '@/api/admin'
import { questionTypeLabel } from '@/utils/question'

const loading = ref(false)
const list = ref([])
const page = ref(1)
const pageSize = 10

const pageData = computed(() => {
  const start = (page.value - 1) * pageSize
  return list.value.slice(start, start + pageSize)
})

async function loadList() {
  loading.value = true
  try {
    const res = await fetchPendingQuestions()
    list.value = res.data || []
    page.value = 1
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function handleAudit(row, approve) {
  try {
    await auditQuestion({
      questionId: row.questionId,
      questionType: row.questionType,
      approve,
    })
    ElMessage.success(approve ? '已通过' : '已驳回')
    await loadList()
  } catch {
    // handled
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除该题目？', '删除确认', { type: 'warning' })
    await deleteQuestion({
      questionId: row.questionId,
      questionType: row.questionType,
    })
    ElMessage.success('题目已删除')
    await loadList()
  } catch {
    // cancel
  }
}

onMounted(loadList)
</script>

<style scoped>
.page__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.page__header h2 {
  margin: 0;
  font-size: 24px;
}

.page__header p {
  margin: 8px 0 0;
  color: #6b7280;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>

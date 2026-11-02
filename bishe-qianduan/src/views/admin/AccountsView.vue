<template>
  <div class="page">
    <header class="page__header">
      <div>
        <h2>账号管理</h2>
        <p>按昵称/姓名或 ID 查询师生账号，支持分页浏览与注销</p>
      </div>
    </header>

    <section class="panel">
      <el-tabs v-model="accountType" @tab-change="onTypeChange">
        <el-tab-pane label="学生账号" name="student" />
        <el-tab-pane label="教师账号" name="teacher" />
      </el-tabs>

      <div class="search-row">
        <el-input
          v-model="keyword"
          :placeholder="accountType === 'teacher' ? '输入教师姓名或 ID' : '输入学生昵称或 ID'"
          clearable
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="accountList" size="small" empty-text="暂无账号">
        <el-table-column :prop="accountType === 'teacher' ? 'teacherId' : 'studentId'" label="账号 ID" width="140" />
        <el-table-column
          :prop="accountType === 'teacher' ? 'name' : 'nickname'"
          :label="accountType === 'teacher' ? '姓名' : '昵称'"
          min-width="120"
        />
        <el-table-column prop="signature" label="个性签名" min-width="160" show-overflow-tooltip />
        <el-table-column v-if="accountType === 'teacher'" prop="courseCount" label="课程数" width="80" />
        <el-table-column prop="comprehensiveScore" label="综合评分" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.isDeleted === 1" type="info" size="small">已注销</el-tag>
            <el-tag v-else-if="row.isBanned === 1" type="danger" size="small">已封禁</el-tag>
            <el-tag v-else type="success" size="small">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="封禁理由" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.isBanned === 1">{{ row.banReason || '-' }}</span>
            <span v-else style="color:#9ca3af">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.isBanned !== 1 && row.isDeleted !== 1"
              link
              size="small"
              type="warning"
              @click="handleBan(row)"
            >
              封禁
            </el-button>
            <el-button
              v-else-if="row.isBanned === 1 && row.isDeleted !== 1"
              link
              size="small"
              type="success"
              @click="handleUnban(row)"
            >
              解禁
            </el-button>
            <el-button
              v-if="row.isDeleted !== 1"
              link
              size="small"
              type="danger"
              @click="handleSoftDelete(row)"
            >
              软删
            </el-button>
            <el-button link size="small" type="danger" @click="handleDeregister(row)">注销</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        class="pager"
        @current-change="loadAccounts"
      />
    </section>

    <section class="panel">
      <h3>创建新管理员</h3>
      <el-form label-position="top" class="form-grid">
        <el-form-item label="密码">
          <el-input v-model="adminForm.password" type="password" show-password placeholder="新管理员登录密码" />
        </el-form-item>
      </el-form>
      <el-button type="primary" :loading="creating" @click="handleCreateAdmin">创建管理员</el-button>
      <p class="tip">管理员 ID 由系统自动生成</p>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createAdmin,
  deregisterAccount,
  fetchAdminStudents,
  fetchAdminTeachers,
  banAccount,
  unbanAccount,
  softDeleteAccount,
} from '@/api/admin'

const accountType = ref('student')
const keyword = ref('')
const loading = ref(false)
const creating = ref(false)
const page = ref(1)
const pageSize = 10
const total = ref(0)
const accountList = ref([])
const adminForm = reactive({ password: '' })

function pageList(data) {
  return data?.list || data?.records || []
}

async function loadAccounts() {
  loading.value = true
  try {
    const fetcher = accountType.value === 'teacher' ? fetchAdminTeachers : fetchAdminStudents
    const res = await fetcher(keyword.value.trim(), page.value, pageSize)
    accountList.value = pageList(res.data)
    total.value = res.data?.total || 0
  } catch {
    accountList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadAccounts()
}

function handleReset() {
  keyword.value = ''
  page.value = 1
  loadAccounts()
}

function onTypeChange() {
  keyword.value = ''
  page.value = 1
  loadAccounts()
}

async function handleBan(row) {
  const accountId = accountType.value === 'teacher' ? row.teacherId : row.studentId
  const label = accountType.value === 'teacher' ? row.name : row.nickname
  try {
    const { value } = await ElMessageBox.prompt(
      `封禁账号「${label || accountId}」的理由`,
      '封禁账号',
      { inputPlaceholder: '违规原因' },
    )
    await banAccount({
      accountType: accountType.value,
      accountId: Number(accountId),
      banReason: value || '违规封禁',
    })
    ElMessage.success('账号已封禁')
    await loadAccounts()
  } catch {
    // cancel
  }
}

async function handleUnban(row) {
  const accountId = accountType.value === 'teacher' ? row.teacherId : row.studentId
  try {
    await unbanAccount(accountType.value, Number(accountId))
    ElMessage.success('账号已解禁')
    await loadAccounts()
  } catch {
    // handled
  }
}

async function handleSoftDelete(row) {
  const accountId = accountType.value === 'teacher' ? row.teacherId : row.studentId
  const label = accountType.value === 'teacher' ? row.name : row.nickname
  try {
    await ElMessageBox.confirm(
      `确定软删除账号「${label || accountId}」？账号将无法登录，数据保留`,
      '软删除确认',
      { type: 'warning' },
    )
    await softDeleteAccount(accountType.value, Number(accountId), '管理员软删除')
    ElMessage.success('账号已软删除')
    await loadAccounts()
  } catch {
    // cancel
  }
}

async function handleDeregister(row) {
  const accountId = accountType.value === 'teacher' ? row.teacherId : row.studentId
  const label = accountType.value === 'teacher' ? row.name : row.nickname
  try {
    await ElMessageBox.confirm(
      `确定注销账号「${label || accountId}」？此操作不可恢复`,
      '危险操作',
      { type: 'warning' },
    )
    loading.value = true
    await deregisterAccount({
      accountType: accountType.value,
      accountId: Number(accountId),
    })
    ElMessage.success('账号已注销')
    await loadAccounts()
  } catch {
    // cancel or error
  } finally {
    loading.value = false
  }
}

async function handleCreateAdmin() {
  if (!adminForm.password) {
    ElMessage.warning('请填写密码')
    return
  }
  creating.value = true
  try {
    await createAdmin({ password: adminForm.password })
    ElMessage.success('管理员创建成功')
    adminForm.password = ''
  } catch {
    // handled
  } finally {
    creating.value = false
  }
}

onMounted(loadAccounts)
</script>

<style scoped>
.page__header h2 {
  margin: 0;
  font-size: 24px;
}

.page__header p {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 14px;
}

.panel {
  margin-top: 20px;
  padding: 20px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid #f3f4f6;
}

.panel h3 {
  margin: 0 0 12px;
  font-size: 16px;
}

.search-row {
  display: flex;
  gap: 12px;
  max-width: 640px;
  margin-bottom: 16px;
}

.form-grid {
  max-width: 420px;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.tip {
  margin: 10px 0 0;
  font-size: 12px;
  color: #9ca3af;
}
</style>

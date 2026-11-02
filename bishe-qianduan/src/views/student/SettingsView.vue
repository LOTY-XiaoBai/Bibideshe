<template>
  <div class="page">
    <header class="page__header">
      <div>
        <h2>个人设置</h2>
        <p>修改昵称、头像与个性签名，并查看各课程得分</p>
      </div>
    </header>

    <div class="layout">
      <section class="panel">
        <h3>基本资料</h3>
        <div class="avatar-row">
          <img :src="avatarPreview" alt="avatar" class="avatar" />
          <div>
            <el-upload
              :show-file-list="false"
              accept="image/*"
              :http-request="handleAvatarUpload"
            >
              <el-button :loading="uploading">更换头像</el-button>
            </el-upload>
            <p class="hint">支持 jpg/png，最大 10MB</p>
          </div>
        </div>

        <el-form label-position="top" class="form">
          <el-form-item label="账号 ID">
            <el-input :model-value="String(userStore.userId || '')" disabled />
          </el-form-item>
          <el-form-item label="昵称">
            <el-input v-model="form.displayName" maxlength="50" show-word-limit />
          </el-form-item>
          <el-form-item label="个性签名">
            <el-input
              v-model="form.signature"
              type="textarea"
              :rows="3"
              maxlength="255"
              show-word-limit
            />
          </el-form-item>
          <el-button type="primary" class="save-btn" :loading="saving" @click="saveProfile">
            保存修改
          </el-button>
        </el-form>
      </section>

      <section class="panel">
        <h3>课程得分总览</h3>
        <p class="panel-desc">成绩 = 考试 60% + 作业 30% + 签到 10%</p>
        <el-table v-loading="scoresLoading" :data="scores" empty-text="暂无课程得分">
          <el-table-column prop="courseName" label="课程" min-width="140" />
          <el-table-column prop="teacherName" label="教师" width="120" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.isFinished === 1 ? 'info' : 'success'" size="small">
                {{ row.isFinished === 1 ? '已结课' : '进行中' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="得分" width="100">
            <template #default="{ row }">
              {{ formatScore(row.studentScore) }}
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>
    <ExtraProfile />
  </div>
</template>

<script setup>
import ExtraProfile from '@/components/ExtraProfile.vue'
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { uploadImage } from '@/api/file'
import { fetchMyCourseScores } from '@/api/course'

const userStore = useUserStore()
const saving = ref(false)
const uploading = ref(false)
const scoresLoading = ref(false)
const scores = ref([])
const avatarUrl = ref('')

const form = reactive({
  displayName: '',
  signature: '',
})

const avatarPreview = computed(() => {
  if (avatarUrl.value) return avatarUrl.value
  if (userStore.avatar) return userStore.avatar
  return 'https://placehold.co/100x100/66ccff/ffffff?text=Stu'
})

function formatScore(score) {
  if (score === null || score === undefined || score === '') return '-'
  return Number(score).toFixed(1)
}

async function loadScores() {
  scoresLoading.value = true
  try {
    const res = await fetchMyCourseScores()
    scores.value = res.data || []
  } catch {
    scores.value = []
  } finally {
    scoresLoading.value = false
  }
}

async function handleAvatarUpload({ file }) {
  uploading.value = true
  try {
    const res = await uploadImage(file)
    avatarUrl.value = res.data?.url || ''
    ElMessage.success('头像上传成功，请点击保存')
  } catch {
    // handled
  } finally {
    uploading.value = false
  }
}

async function saveProfile() {
  if (!form.displayName.trim()) {
    ElMessage.warning('昵称不能为空')
    return
  }
  saving.value = true
  try {
    await userStore.updateProfile({
      displayName: form.displayName.trim(),
      signature: form.signature.trim(),
      avatar: avatarUrl.value || undefined,
    })
    ElMessage.success('资料已更新')
  } catch {
    // handled
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    const profile = await userStore.refreshProfile()
    form.displayName = profile?.displayName || profile?.nickname || ''
    form.signature = profile?.signature || ''
    avatarUrl.value = profile?.avatar || ''
  } catch {
    form.displayName = userStore.displayName
    form.signature = userStore.profile?.signature || ''
  }
  loadScores()
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

.layout {
  display: grid;
  grid-template-columns: minmax(280px, 420px) 1fr;
  gap: 20px;
  margin-top: 24px;
}

@media (max-width: 960px) {
  .layout {
    grid-template-columns: 1fr;
  }
}

.panel {
  background: #fff;
  border: 1px solid #f3f4f6;
  border-radius: 18px;
  padding: 22px;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.04);
}

.panel h3 {
  margin: 0 0 16px;
  font-size: 16px;
}

.panel-desc {
  margin: -8px 0 14px;
  color: #9ca3af;
  font-size: 12px;
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 18px;
}

.avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid #fff;
  box-shadow: 0 4px 12px rgba(102, 204, 255, 0.25);
}

.hint {
  margin: 8px 0 0;
  color: #9ca3af;
  font-size: 12px;
}

.save-btn {
  width: 100%;
  background: var(--theme-gradient) !important;
  border: none;
}
</style>

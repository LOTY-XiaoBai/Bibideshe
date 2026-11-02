<template>
  <div class="page">
    <header class="page__header">
      <div>
        <h2>个人设置</h2>
        <p>修改姓名、头像与个性签名，并查看综合评分</p>
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

        <el-form label-position="top">
          <el-form-item label="账号 ID">
            <el-input :model-value="String(userStore.userId || '')" disabled />
          </el-form-item>
          <el-form-item label="姓名">
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

      <section class="rating-panel">
        <div class="rating-panel__glow" />
        <p class="rating-panel__label">教师综合评分</p>
        <div v-if="hasRating" class="rating-panel__score">
          {{ formatScore(comprehensiveScore) }}
          <span>/ 10</span>
        </div>
        <div v-else class="rating-panel__empty">
          {{ scoreTip || '暂未有人打分' }}
        </div>
        <div class="rating-meta">
          <div>
            <span>已开课程</span>
            <strong>{{ courseCount }} / 3</strong>
          </div>
          <div>
            <span>评分状态</span>
            <strong>{{ hasRating ? '已有学生评价' : '等待首评' }}</strong>
          </div>
        </div>
      </section>
    </div>
    <ExtraProfile />
  </div>
</template>

<script setup>
import ExtraProfile from '@/components/ExtraProfile.vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { uploadImage } from '@/api/file'

const userStore = useUserStore()
const saving = ref(false)
const uploading = ref(false)
const avatarUrl = ref('')
const comprehensiveScore = ref(null)
const hasRating = ref(false)
const scoreTip = ref('')
const courseCount = ref(0)

const form = reactive({
  displayName: '',
  signature: '',
})

const avatarPreview = computed(() => {
  if (avatarUrl.value) return avatarUrl.value
  if (userStore.avatar) return userStore.avatar
  return 'https://placehold.co/100x100/EE0000/ffffff?text=Tea'
})

function formatScore(score) {
  if (score === null || score === undefined || score === '') return '-'
  return Number(score).toFixed(1)
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
    ElMessage.warning('姓名不能为空')
    return
  }
  saving.value = true
  try {
    const profile = await userStore.updateProfile({
      displayName: form.displayName.trim(),
      signature: form.signature.trim(),
      avatar: avatarUrl.value || undefined,
    })
    syncRating(profile)
    ElMessage.success('资料已更新')
  } catch {
    // handled
  } finally {
    saving.value = false
  }
}

function syncRating(profile) {
  comprehensiveScore.value = profile?.comprehensiveScore ?? null
  hasRating.value = Boolean(profile?.hasRating)
  scoreTip.value = profile?.scoreTip || ''
  courseCount.value = profile?.courseCount ?? 0
}

onMounted(async () => {
  try {
    const profile = await userStore.refreshProfile()
    form.displayName = profile?.displayName || profile?.name || ''
    form.signature = profile?.signature || ''
    avatarUrl.value = profile?.avatar || ''
    syncRating(profile)
  } catch {
    form.displayName = userStore.displayName
    form.signature = userStore.profile?.signature || ''
    syncRating(userStore.profile)
  }
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
  box-shadow: 0 4px 12px rgba(238, 0, 0, 0.18);
}

.hint {
  margin: 8px 0 0;
  color: #9ca3af;
  font-size: 12px;
}

.save-btn {
  width: 100%;
  background: linear-gradient(135deg, #66ccff, #ee0000) !important;
  border: none;
}

.rating-panel {
  position: relative;
  overflow: hidden;
  border-radius: 18px;
  padding: 28px;
  background: linear-gradient(135deg, #2b2d42, #ee0000);
  color: #fff;
  box-shadow: 0 10px 28px rgba(238, 0, 0, 0.2);
  min-height: 220px;
}

.rating-panel__glow {
  position: absolute;
  right: -40px;
  bottom: -40px;
  width: 160px;
  height: 160px;
  border-radius: 50%;
  background: #66ccff;
  filter: blur(48px);
  opacity: 0.25;
}

.rating-panel__label {
  position: relative;
  margin: 0;
  font-size: 14px;
  opacity: 0.85;
}

.rating-panel__score {
  position: relative;
  margin-top: 12px;
  font-size: 56px;
  font-weight: 800;
  line-height: 1;
}

.rating-panel__score span {
  font-size: 18px;
  font-weight: 500;
  opacity: 0.8;
  margin-left: 6px;
}

.rating-panel__empty {
  position: relative;
  margin-top: 24px;
  font-size: 22px;
  font-weight: 600;
}

.rating-meta {
  position: relative;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 36px;
}

.rating-meta span {
  display: block;
  font-size: 12px;
  opacity: 0.75;
  margin-bottom: 4px;
}

.rating-meta strong {
  font-size: 16px;
}
</style>

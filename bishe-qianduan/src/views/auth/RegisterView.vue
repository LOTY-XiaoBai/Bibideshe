<template>
  <div class="auth-page" :data-role="themeAttr">
    <div class="blob blob--one" />
    <div class="blob blob--two" />

    <div class="auth-card">
      <div class="auth-card__header">
        <div class="icon-box">
          <i class="ph ph-user-plus" />
        </div>
        <h1>系统注册</h1>
        <p>注册成功后将自动分配角色 ID</p>
      </div>

      <div class="role-switch">
        <button
          type="button"
          class="role-switch__btn"
          :class="{ active: form.roleType === 'student' }"
          @click="switchRole('student')"
        >
          学生注册
        </button>
        <button
          type="button"
          class="role-switch__btn"
          :class="{ active: form.roleType === 'teacher' }"
          @click="switchRole('teacher')"
        >
          教师注册
        </button>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <el-form-item
          v-if="form.roleType === 'student'"
          label="昵称"
          prop="nickname"
        >
          <el-input v-model="form.nickname" size="large" placeholder="请输入昵称" />
        </el-form-item>

        <el-form-item v-else label="姓名" prop="name">
          <el-input v-model="form.name" size="large" placeholder="请输入姓名" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            size="large"
            placeholder="请设置密码"
            show-password
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            size="large"
            placeholder="请再次输入密码"
            show-password
          />
        </el-form-item>

        <el-form-item label="个性签名（可选）" prop="signature">
          <el-input
            v-model="form.signature"
            size="large"
            placeholder="一句话介绍自己"
          />
        </el-form-item>

        <el-form-item label="验证码" prop="captchaCode">
          <div class="captcha-row">
            <el-input
              v-model="form.captchaCode"
              size="large"
              placeholder="请输入验证码"
              maxlength="6"
            />
            <button type="button" class="captcha-img" title="点击刷新" @click="loadCaptcha">
              <img v-if="captchaImage" :src="captchaImage" alt="验证码" />
              <span v-else>加载中</span>
            </button>
          </div>
        </el-form-item>

        <el-button
          class="submit-btn"
          type="primary"
          size="large"
          :loading="loading"
          @click="handleSubmit"
        >
          立即注册
        </el-button>
      </el-form>

      <p class="auth-card__footer">
        已有账号？
        <RouterLink class="link" to="/login">返回登录</RouterLink>
      </p>
    </div>

    <el-dialog
      v-model="idDialogVisible"
      title="请妥善保管您的账号 ID"
      width="420px"
      align-center
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <div class="id-dialog">
        <p class="id-dialog__warn">
          <i class="ph ph-warning-circle" />
          账号 ID 是您唯一的登录凭证，系统不会再次展示完整找回入口，请立即抄写或截图保存。
        </p>
        <div class="id-dialog__id">{{ createdId }}</div>
        <p class="id-dialog__hint">关闭本窗口后将返回登录页，请使用该 ID 与您设置的密码登录。</p>
      </div>
      <template #footer>
        <el-button type="primary" @click="confirmIdAndGoLogin">
          我已记录，前往登录
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { fetchCaptcha } from '@/api/captcha'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const createdId = ref('')
const idDialogVisible = ref(false)
const captchaImage = ref('')

const form = reactive({
  roleType: 'student',
  nickname: '',
  name: '',
  password: '',
  confirmPassword: '',
  signature: '',
  captchaKey: '',
  captchaCode: '',
})

const validateConfirm = (_rule, value, callback) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = computed(() => ({
  nickname: [
    {
      required: form.roleType === 'student',
      message: '请输入昵称',
      trigger: 'blur',
    },
  ],
  name: [
    {
      required: form.roleType === 'teacher',
      message: '请输入姓名',
      trigger: 'blur',
    },
  ],
  password: [
    { required: true, message: '请设置密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}))

const themeAttr = computed(() =>
  form.roleType === 'teacher' ? 'teacher' : 'student',
)

onMounted(() => {
  loadCaptcha()
})

async function loadCaptcha() {
  try {
    const res = await fetchCaptcha()
    form.captchaKey = res.data?.captchaKey || ''
    captchaImage.value = res.data?.captchaImage || ''
    form.captchaCode = ''
  } catch {
    form.captchaKey = ''
    captchaImage.value = ''
  }
}

function switchRole(type) {
  form.roleType = type
  createdId.value = ''
  idDialogVisible.value = false
  formRef.value?.clearValidate()
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await userStore.register({ ...form })
    const id =
      res.data?.studentId ||
      res.data?.teacherId ||
      ''
    createdId.value = id ? String(id) : ''
    ElMessage.success(res.message || '注册成功')
    if (createdId.value) {
      idDialogVisible.value = true
    } else {
      router.push('/login')
    }
  } catch {
    await loadCaptcha()
  } finally {
    loading.value = false
  }
}

function confirmIdAndGoLogin() {
  idDialogVisible.value = false
  router.push({
    path: '/login',
    query: { id: createdId.value },
  })
}
</script>

<style scoped>
.auth-page {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  overflow: hidden;
  background: linear-gradient(135deg, #eff6ff, #f3f4f6);
}

.blob {
  position: absolute;
  width: 24rem;
  height: 24rem;
  border-radius: 50%;
  filter: blur(64px);
  opacity: 0.3;
  mix-blend-mode: multiply;
  transition: background-color 0.7s ease;
  pointer-events: none;
}

.blob--one {
  top: -10%;
  left: -10%;
  background: var(--theme-primary);
}

.blob--two {
  right: -10%;
  bottom: -10%;
  background: var(--theme-secondary);
}

.auth-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 440px;
  margin: 24px;
  padding: 36px 32px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(12px);
  box-shadow: var(--theme-shadow);
  transition: box-shadow 0.5s ease;
}

.auth-card__header {
  text-align: center;
  margin-bottom: 20px;
}

.icon-box {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  margin-bottom: 12px;
  border-radius: 16px;
  background: var(--theme-gradient);
  color: #fff;
  font-size: 28px;
  box-shadow: var(--theme-shadow);
  transition: background 0.5s ease;
}

.auth-card__header h1 {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
}

.auth-card__header p {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 13px;
}

.role-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 18px;
  padding: 4px;
  border-radius: 12px;
  background: #f3f4f6;
}

.role-switch__btn {
  border: none;
  border-radius: 10px;
  padding: 10px 12px;
  background: transparent;
  color: #6b7280;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s ease;
}

.role-switch__btn.active {
  background: #fff;
  color: var(--theme-primary);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
}

.submit-btn {
  width: 100%;
  margin-top: 4px;
  height: 46px;
  border: none;
  border-radius: 12px;
  font-weight: 700;
  background: var(--theme-gradient) !important;
  box-shadow: var(--theme-shadow);
}

.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;
  align-items: center;
}

.captcha-img {
  flex-shrink: 0;
  width: 130px;
  height: 40px;
  padding: 0;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #f9fafb;
  cursor: pointer;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #6b7280;
}

.captcha-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.auth-card__footer {
  margin: 22px 0 0;
  text-align: center;
  color: #4b5563;
  font-size: 14px;
}

.link {
  margin-left: 4px;
  color: var(--theme-primary);
  font-weight: 700;
}

.link:hover {
  text-decoration: underline;
}

.id-dialog__warn {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  margin: 0 0 16px;
  padding: 12px;
  border-radius: 12px;
  background: #fff7ed;
  color: #c2410c;
  font-size: 13px;
  line-height: 1.5;
}

.id-dialog__warn i {
  font-size: 18px;
  flex-shrink: 0;
  margin-top: 1px;
}

.id-dialog__id {
  text-align: center;
  padding: 18px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(102, 204, 255, 0.15), rgba(0, 255, 204, 0.15));
  color: #0f172a;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: 0.04em;
  word-break: break-all;
}

.id-dialog__hint {
  margin: 14px 0 0;
  color: #6b7280;
  font-size: 13px;
  text-align: center;
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: #374151;
}

:deep(.el-input__wrapper) {
  border-radius: 12px;
  box-shadow: 0 0 0 1px #e5e7eb inset;
  background: #f9fafb;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--theme-primary) inset !important;
  background: #fff;
}
</style>

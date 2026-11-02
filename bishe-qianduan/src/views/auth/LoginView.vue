<template>
  <div class="auth-page" :class="{ 'is-admin-mode': adminMode }" :data-role="themeAttr">
    <div class="blob blob--one" />
    <div class="blob blob--two" />

    <div class="auth-card">
      <div class="auth-card__header">
        <div class="icon-box">
          <i :class="iconClass" />
        </div>
        <h1>{{ title }}</h1>
        <p>{{ subtitle }}</p>
      </div>

      <el-form
        v-if="adminRegisterMode"
        ref="registerFormRef"
        :model="registerForm"
        :rules="registerRules"
        label-position="top"
        @submit.prevent="handleAdminRegister"
      >
        <p class="bootstrap-tip">
          系统尚未创建管理员账号，请设置首个管理员密码完成初始化。
        </p>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            size="large"
            placeholder="请设置管理员密码"
            show-password
          >
            <template #prefix>
              <i class="ph ph-lock-key input-icon" />
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            size="large"
            placeholder="请再次输入密码"
            show-password
            @keyup.enter="handleAdminRegister"
          >
            <template #prefix>
              <i class="ph ph-lock-key input-icon" />
            </template>
          </el-input>
        </el-form-item>

        <el-button
          class="submit-btn"
          type="primary"
          size="large"
          :loading="registering"
          native-type="submit"
          @click="handleAdminRegister"
        >
          创建首个管理员
        </el-button>
      </el-form>

      <el-form
        v-else
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="账号 (ID)" prop="id">
          <el-input
            v-model="form.id"
            size="large"
            :placeholder="idPlaceholder"
            clearable
            @input="onIdInput"
          >
            <template #prefix>
              <i class="ph ph-user input-icon" />
            </template>
            <template #suffix>
              <span v-if="roleBadge" class="role-badge" :class="`role-badge--${themeAttr}`">
                {{ roleBadge }}
              </span>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            size="large"
            placeholder="请输入密码"
            show-password
            @keyup.enter="handleSubmit"
          >
            <template #prefix>
              <i class="ph ph-lock-key input-icon" />
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="验证码" prop="captchaCode">
          <div class="captcha-row">
            <el-input
              v-model="form.captchaCode"
              size="large"
              placeholder="请输入验证码"
              maxlength="6"
              @keyup.enter="handleSubmit"
            >
              <template #prefix>
                <i class="ph ph-shield-check input-icon" />
              </template>
            </el-input>
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
          native-type="submit"
          @click="handleSubmit"
        >
          {{ adminMode ? '管理员登录' : '登录系统' }}
        </el-button>
      </el-form>

      <p v-if="!adminMode" class="auth-card__footer">
        还没有账号？
        <RouterLink class="link" to="/register">立即注册</RouterLink>
      </p>

      <button
        v-if="adminMode"
        type="button"
        class="back-normal"
        @click="exitAdminMode"
      >
        <i class="ph ph-arrow-left" />
        返回普通登录
      </button>
    </div>

    <el-dialog
      v-model="idDialogVisible"
      title="请妥善保管您的管理员 ID"
      width="420px"
      align-center
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <div class="id-dialog">
        <p class="id-dialog__warn">
          <i class="ph ph-warning-circle" />
          管理员 ID 是您唯一的登录凭证，请立即抄写或截图保存。
        </p>
        <div class="id-dialog__id">{{ createdAdminId }}</div>
        <p class="id-dialog__hint">关闭后将自动填入登录框，请使用该 ID 与密码登录。</p>
      </div>
      <template #footer>
        <el-button type="primary" @click="confirmAdminId">
          我已记录，前往登录
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { fetchAdminRegisterAvailable, registerAdmin } from '@/api/admin'
import { fetchCaptcha } from '@/api/captcha'
import {
  detectRoleFromId,
  roleLabel,
  themeRoleAttr,
  ROLES,
} from '@/utils/role'

const ADMIN_SECRET = 'LOVE-LOTY'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref()
const registerFormRef = ref()
const loading = ref(false)
const registering = ref(false)
const detectedRole = ref(null)
const adminMode = ref(false)
const adminBootstrapAvailable = ref(false)
const secretBuffer = ref('')
const createdAdminId = ref('')
const idDialogVisible = ref(false)

const form = reactive({
  id: '',
  password: '',
  captchaKey: '',
  captchaCode: '',
})

const captchaImage = ref('')

const registerForm = reactive({
  password: '',
  confirmPassword: '',
})

const validateConfirm = (_rule, value, callback) => {
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const registerRules = {
  password: [
    { required: true, message: '请设置密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
}

onMounted(() => {
  if (route.query.id) {
    form.id = String(route.query.id)
    detectedRole.value = detectRoleFromId(form.id)
  }
  loadCaptcha()
  window.addEventListener('keydown', onSecretKeydown, true)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onSecretKeydown, true)
})

const rules = {
  id: [{ required: true, message: '请输入账号 ID', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}

const adminRegisterMode = computed(
  () => adminMode.value && adminBootstrapAvailable.value,
)

const themeAttr = computed(() => {
  if (adminMode.value) return 'admin'
  return detectedRole.value ? themeRoleAttr(detectedRole.value) : 'student'
})

const title = computed(() => {
  if (adminRegisterMode.value) return '初始化管理员'
  if (adminMode.value) return '管理员登录'
  if (!detectedRole.value) return '欢迎回来'
  return `${roleLabel(detectedRole.value)}登录`
})

const subtitle = computed(() => {
  if (adminRegisterMode.value) return '首次部署，创建系统首个管理员账号'
  return adminMode.value ? '系统管理控制台入口' : '关注洛天依谢谢喵教育系统'
})

const idPlaceholder = computed(() =>
  adminMode.value
    ? '请输入管理员 ID（如 admin01234）'
    : '学生以1开头，教师以2开头',
)

const roleBadge = computed(() => {
  if (adminMode.value) return '管理员模式'
  if (!detectedRole.value) return ''
  return `${roleLabel(detectedRole.value)}识别`
})

const iconClass = computed(() => {
  if (adminMode.value) return 'ph ph-shield-check'
  switch (detectedRole.value) {
    case ROLES.TEACHER:
      return 'ph ph-chalkboard-teacher'
    case ROLES.ADMIN:
      return 'ph ph-shield-check'
    case ROLES.STUDENT:
      return 'ph ph-student'
    default:
      return 'ph ph-graduation-cap'
  }
})

function onIdInput(value) {
  if (adminMode.value) return
  detectedRole.value = detectRoleFromId(value)
}

function onSecretKeydown(event) {
  if (adminMode.value) return

  if (event.ctrlKey && (event.key === '6' || event.code === 'Digit6')) {
    event.preventDefault()
    secretBuffer.value = ''
    return
  }

  if (event.key === 'Enter') {
    if (secretBuffer.value === ADMIN_SECRET) {
      event.preventDefault()
      event.stopPropagation()
      enterAdminMode()
    }
    secretBuffer.value = ''
    return
  }

  if (event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey) {
    const next = secretBuffer.value + event.key
    if (ADMIN_SECRET.startsWith(next)) {
      secretBuffer.value = next
    } else if (ADMIN_SECRET.startsWith(event.key)) {
      secretBuffer.value = event.key
    } else {
      secretBuffer.value = ''
    }
  }
}

async function enterAdminMode() {
  adminMode.value = true
  secretBuffer.value = ''
  form.id = ''
  form.password = ''
  registerForm.password = ''
  registerForm.confirmPassword = ''
  detectedRole.value = ROLES.ADMIN
  formRef.value?.clearValidate()
  registerFormRef.value?.clearValidate()

  try {
    const res = await fetchAdminRegisterAvailable()
    adminBootstrapAvailable.value = Boolean(res.data?.available)
    if (adminBootstrapAvailable.value) {
      ElMessage.info('系统尚未创建管理员，请先完成初始化注册')
    } else {
      ElMessage.success('已进入管理员登录')
    }
  } catch {
    adminBootstrapAvailable.value = false
    ElMessage.success('已进入管理员登录')
  }
}

function exitAdminMode() {
  adminMode.value = false
  adminBootstrapAvailable.value = false
  form.id = ''
  form.password = ''
  registerForm.password = ''
  registerForm.confirmPassword = ''
  detectedRole.value = null
  secretBuffer.value = ''
  formRef.value?.clearValidate()
  registerFormRef.value?.clearValidate()
}

async function handleAdminRegister() {
  if (!registerFormRef.value) return
  const valid = await registerFormRef.value.validate().catch(() => false)
  if (!valid) return

  registering.value = true
  try {
    const res = await registerAdmin({ password: registerForm.password })
    const adminId = res.data?.adminId ? String(res.data.adminId) : ''
    createdAdminId.value = adminId
    adminBootstrapAvailable.value = false
    registerForm.password = ''
    registerForm.confirmPassword = ''
    ElMessage.success(res.message || '管理员注册成功')
    if (adminId) {
      idDialogVisible.value = true
    }
  } catch {
    // 错误提示已由 request 拦截器处理
  } finally {
    registering.value = false
  }
}

function confirmAdminId() {
  idDialogVisible.value = false
  form.id = createdAdminId.value
  form.password = ''
  formRef.value?.clearValidate()
}

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

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (!adminMode.value && !detectRoleFromId(form.id)) {
    ElMessage.warning('无法识别账号类型，请检查 ID 格式')
    return
  }

  loading.value = true
  try {
    const homePath = await userStore.login({
      id: form.id.trim(),
      password: form.password,
      forceAdmin: adminMode.value,
      captchaKey: form.captchaKey,
      captchaCode: form.captchaCode,
    })
    ElMessage.success('登录成功')
    const redirect = route.query.redirect
    router.replace(typeof redirect === 'string' ? redirect : homePath)
  } catch {
    await loadCaptcha()
  } finally {
    loading.value = false
  }
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
  transition: background 0.7s ease;
}

.auth-page.is-admin-mode {
  background: #0a0a0a;
}

.blob {
  position: absolute;
  width: 24rem;
  height: 24rem;
  border-radius: 50%;
  filter: blur(64px);
  opacity: 0.3;
  mix-blend-mode: multiply;
  transition: background-color 0.7s ease, opacity 0.7s ease;
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

.is-admin-mode .blob {
  opacity: 0.18;
  mix-blend-mode: normal;
}

.is-admin-mode .blob--one {
  background: #475569;
}

.is-admin-mode .blob--two {
  background: #1e293b;
}

.auth-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 420px;
  margin: 24px;
  padding: 40px 36px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(12px);
  box-shadow: var(--theme-shadow);
  transition: box-shadow 0.5s ease, background 0.5s ease, border-color 0.5s ease;
}

.is-admin-mode .auth-card {
  background: rgba(15, 23, 42, 0.92);
  border-color: rgba(71, 85, 105, 0.6);
  color: #e2e8f0;
}

.auth-card__header {
  text-align: center;
  margin-bottom: 28px;
}

.icon-box {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  margin-bottom: 14px;
  border-radius: 16px;
  background: var(--theme-gradient);
  color: #fff;
  font-size: 32px;
  box-shadow: var(--theme-shadow);
  transition: background 0.5s ease, box-shadow 0.5s ease;
}

.auth-card__header h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: #1f2937;
  transition: color 0.4s ease;
}

.is-admin-mode .auth-card__header h1 {
  color: #f8fafc;
}

.auth-card__header p {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 14px;
}

.is-admin-mode .auth-card__header p {
  color: #94a3b8;
}

.bootstrap-tip {
  margin: 0 0 16px;
  padding: 12px;
  border-radius: 12px;
  background: rgba(148, 163, 184, 0.12);
  color: #cbd5e1;
  font-size: 13px;
  line-height: 1.5;
}

.input-icon {
  color: #9ca3af;
  font-size: 16px;
}

.role-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 700;
}

.role-badge--student {
  background: rgba(0, 255, 204, 0.12);
  color: #00b894;
}

.role-badge--teacher {
  background: rgba(238, 0, 0, 0.08);
  color: var(--brand-red);
}

.role-badge--admin {
  background: rgba(148, 163, 184, 0.2);
  color: #cbd5e1;
}

.submit-btn {
  width: 100%;
  margin-top: 8px;
  height: 46px;
  border: none;
  border-radius: 12px;
  font-weight: 700;
  background: var(--theme-gradient) !important;
  box-shadow: var(--theme-shadow);
  transition: transform 0.2s ease, box-shadow 0.3s ease;
}

.submit-btn:hover {
  transform: translateY(-1px);
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
  margin: 28px 0 0;
  text-align: center;
  color: #4b5563;
  font-size: 14px;
}

.link {
  margin-left: 4px;
  color: var(--theme-primary);
  font-weight: 700;
  transition: color 0.3s ease;
}

.link:hover {
  text-decoration: underline;
}

.back-normal {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  margin-top: 20px;
  padding: 10px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 12px;
  background: transparent;
  color: #94a3b8;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.back-normal:hover {
  background: rgba(148, 163, 184, 0.12);
  color: #e2e8f0;
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
  background: linear-gradient(135deg, rgba(71, 85, 105, 0.15), rgba(30, 41, 59, 0.2));
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

.is-admin-mode :deep(.el-form-item__label) {
  color: #cbd5e1;
}

:deep(.el-input__wrapper) {
  border-radius: 12px;
  box-shadow: 0 0 0 1px #e5e7eb inset;
  background: #f9fafb;
}

.is-admin-mode :deep(.el-input__wrapper) {
  background: rgba(30, 41, 59, 0.8);
  box-shadow: 0 0 0 1px #334155 inset;
}

.is-admin-mode :deep(.el-input__inner) {
  color: #f1f5f9;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--theme-primary) inset !important;
  background: #fff;
}

.is-admin-mode :deep(.el-input__wrapper.is-focus) {
  background: rgba(30, 41, 59, 1);
}
</style>

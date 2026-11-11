<template>
  <section ref="stage" class="checkin-display">
    <header>
      <div>
        <h1>签到展示</h1>
        <p>{{ details?.courseName }}</p>
      </div>
      <div class="display-actions">
        <el-button @click="emit('close')">返回课堂</el-button>
        <el-button v-if="fullscreenAvailable" @click="toggleFullscreen">{{ fullscreen ? '退出全屏' : '全屏' }}</el-button>
      </div>
    </header>
    <div class="display-content">
      <p v-if="error" role="alert">{{ error }}</p>
      <p v-if="!details && !error">正在加载签到…</p>
      <template v-if="details">
        <h2 v-if="isEnded">签到已结束</h2>
        <template v-else-if="details.mode === 'CODE'">
          <h2>请输入大屏签到码</h2>
          <div class="checkin-code" aria-label="当前签到码">{{ codeLeft > 0 ? details.code : '更新中' }}</div>
          <p>{{ Math.ceil(codeLeft / 1000) }} 秒后换码</p>
        </template>
        <template v-else-if="details.mode === 'GESTURE'">
          <h2>按数字顺序连接手势</h2>
          <CheckInGesture class="display-gesture" :model-value="details.gesture" readonly />
        </template>
        <template v-else-if="details.mode === 'LOCATION'">
          <h2>定位签到</h2>
          <p>请在签到范围内获取当前位置</p>
          <p>在学生端打开「课堂签到」，点击「立即签到」并允许定位。</p>
        </template>
        <p class="progress"><strong>{{ details.attendeeCount }} 人已签到</strong><span v-if="!isEnded">剩余 {{ timeLeft }}</span></p>
      </template>
    </div>
    <footer v-if="details && !isEnded">
      <template v-if="confirmEnd">
        <el-button @click="confirmEnd = false">继续签到</el-button>
        <el-button type="danger" :loading="ending" @click="end">确认结束签到</el-button>
      </template>
      <el-button v-else type="danger" plain @click="confirmEnd = true">结束签到</el-button>
    </footer>
  </section>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { endCheckIn, fetchCheckInDisplay } from '@/api/checkIn'
import CheckInGesture from '@/components/CheckInGesture.vue'

const props = defineProps({ checkInId: { type: String, required: true }, courseId: { type: String, required: true } })
const emit = defineEmits(['close'])
const stage = ref(null), fullscreen = ref(false), error = ref(''), details = ref(null)
const ending = ref(false), confirmEnd = ref(false)
const countdown = ref(0), codeLeft = ref(0)
const isEnded = computed(() => details.value?.ended || countdown.value <= 0)
const fullscreenAvailable = document.fullscreenEnabled
const timeLeft = computed(() => {
  const seconds = Math.ceil(countdown.value / 1000)
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
})

let fetchTimer, tickTimer, codeExpires = 0, endsAt = 0, disposed = false
let requestVersion = 0

function updateCountdown() {
  const now = performance.now()
  countdown.value = Math.max(0, endsAt - now)
  codeLeft.value = Math.max(0, codeExpires - now)
}

async function refresh() {
  clearTimeout(fetchTimer)
  const version = ++requestVersion
  const sent = performance.now()
  let delay = 2000
  try {
    const { data } = await fetchCheckInDisplay(props.checkInId)
    if (disposed || version !== requestVersion) return
    if (String(data.courseId) !== String(props.courseId)) {
      error.value = '这次签到不属于当前课程'
      return
    }
    details.value = data
    error.value = ''
    const now = performance.now()
    const responseTime = (now - sent) / 2
    endsAt = now + data.remainingMs - responseTime
    codeExpires = now + (data.refreshMs || 0) - responseTime
    updateCountdown()
    if (data.ended) return
    delay = Math.min(delay, Math.max(30, countdown.value + 25))
    if (data.refreshMs) delay = Math.min(delay, Math.max(30, codeLeft.value + 25))
  } catch (failure) {
    if (disposed || version !== requestVersion) return
    error.value = failure.response?.data?.message || '签到状态加载失败，正在重试'
    if ([400, 401, 403, 404].includes(failure.response?.status)) return
  }
  fetchTimer = setTimeout(refresh, delay)
}

async function end() {
  ending.value = true
  try {
    await endCheckIn(props.checkInId)
    confirmEnd.value = false
    await refresh()
  } catch {} finally { ending.value = false }
}

function fullscreenChanged() { fullscreen.value = document.fullscreenElement === stage.value }
async function toggleFullscreen() {
  try {
    if (fullscreen.value) await document.exitFullscreen()
    else await stage.value.requestFullscreen()
  } catch { ElMessage.warning('浏览器暂时无法进入全屏') }
}

onMounted(() => {
  document.addEventListener('fullscreenchange', fullscreenChanged)
  tickTimer = setInterval(updateCountdown, 100)
  refresh()
})
onBeforeUnmount(() => {
  disposed = true
  clearTimeout(fetchTimer)
  clearInterval(tickTimer)
  document.removeEventListener('fullscreenchange', fullscreenChanged)
  if (fullscreen.value) document.exitFullscreen().catch(() => {})
})
</script>

<style scoped>
.checkin-display { position: fixed; inset: 0; z-index: 1800; display: flex; flex-direction: column; overflow: auto; padding: clamp(18px, 4vw, 56px); color: #f1f5f9; background: #0f172a; }
header { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 16px; }
h1, h2, p { margin: 0; }
header p { margin-top: 6px; color: #a9bbd4; }
.display-actions, footer, .progress { display: flex; flex-wrap: wrap; align-items: center; gap: 14px; }
.display-actions :deep(.el-button), footer :deep(.el-button) { margin: 0; }
.display-content { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; gap: 16px; padding: 24px 0; }
.display-content h2 { font-size: clamp(22px, 3vw, 42px); }
.checkin-code { font-size: clamp(54px, 16vw, 220px); font-weight: 800; line-height: 1; font-variant-numeric: tabular-nums; letter-spacing: .08em; color: #93c5fd; }
.display-gesture { --gesture-size: min(68vw, 45vh, 400px); width: var(--gesture-size); }
.progress { justify-content: center; font-size: clamp(17px, 2.5vw, 30px); }
footer { justify-content: center; }
</style>

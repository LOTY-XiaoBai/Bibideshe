<template>
  <div v-loading="loading" class="classroom-page">
    <header class="class-header">
      <div><el-button text @click="router.push('/teacher/courses')"><i aria-hidden="true" class="ph ph-arrow-left" /> 返回我的授课</el-button><h2>{{ course?.courseName || '课堂' }}</h2><p>{{ mode === 'ONLINE' ? '网络授课' : '本地授课' }} · {{ teaching ? '正在上课' : session ? '等待恢复直播' : '准备上课' }}<span v-if="mode === 'ONLINE' && teaching"> · {{ viewers }} 位学生已连接</span></p></div>
      <div class="header-actions"><el-radio-group v-if="!session" v-model="mode" :disabled="starting" @change="releaseMedia"><el-radio-button value="LOCAL">本地授课</el-radio-button><el-radio-button value="ONLINE">网络授课</el-radio-button></el-radio-group><el-button v-if="!teaching" type="primary" :loading="starting" :disabled="loading || !course" @click="begin">{{ session ? '恢复直播' : '开始上课' }}</el-button><el-button v-if="session" type="danger" plain @click="finish">结束本次上课</el-button></div>
    </header>

    <section v-if="mode === 'ONLINE'" class="media-panel">
      <div class="media-settings"><h3>直播设备</h3><p>开启摄像头或屏幕共享后开始授课</p><div class="media-buttons">
        <el-button :type="hasCamera ? 'primary' : 'default'" :loading="deviceBusy === 'camera'" :disabled="!!deviceBusy" @click="toggleCamera"><i aria-hidden="true" class="ph ph-video-camera" /> {{ hasCamera ? '关闭摄像头' : '开启摄像头' }}</el-button>
        <el-button :type="hasMic ? 'primary' : 'default'" :loading="deviceBusy === 'mic'" :disabled="!!deviceBusy" @click="toggleMic"><i aria-hidden="true" class="ph ph-microphone" /> {{ hasMic ? '关闭麦克风' : '开启麦克风' }}</el-button>
        <el-button :type="screen ? 'primary' : 'default'" :loading="deviceBusy === 'screen'" :disabled="!!deviceBusy" @click="toggleScreen"><i aria-hidden="true" class="ph ph-monitor" /> {{ screen ? '停止共享' : '共享屏幕' }}</el-button>
      </div><el-alert v-if="mediaError" :title="mediaError" type="warning" :closable="false" /></div>
      <div class="self-preview"><div v-if="!hasCamera && !screen" class="preview-empty"><i aria-hidden="true" class="ph ph-video-camera-slash" /><span>教师画面预览</span></div><div v-if="screen" class="preview-tile"><StreamVideo :stream="screen" muted /><span>屏幕共享</span></div><div v-if="hasCamera" class="preview-tile camera-preview"><StreamVideo :stream="camera" muted /><span>摄像头</span></div></div>
    </section>

    <div class="module-tabs"><button :class="{ selected: module === 'roll' }" @click="module = 'roll'"><i aria-hidden="true" class="ph ph-users-three" /> 随机点名</button><button :class="{ selected: module === 'checkin' }" @click="module = 'checkin'"><i aria-hidden="true" class="ph ph-calendar-check" /> 发布签到</button><button :class="{ selected: module === 'quiz' }" @click="module = 'quiz'"><i aria-hidden="true" class="ph ph-exam" /> 发布小测</button></div>
    <section v-show="module === 'roll'" class="roll-panel">
      <div class="roll-toolbar"><div><h3>课堂随机点名</h3><p>共 {{ members.length }} 位学生，每轮抽取的学生不重复</p></div><div class="draw-controls"><span>一次抽取</span><el-input-number v-model="drawCount" :min="1" :max="Math.max(1, members.length)" :precision="0" /><el-button type="primary" :disabled="!teaching || !members.length" :loading="drawing" @click="draw">{{ selected.length ? '再抽一次' : '开始点名' }}</el-button></div></div>
      <div class="draw-stage" aria-live="polite"><div v-if="!selected.length" class="draw-empty"><i aria-hidden="true" class="ph ph-student" /><p>{{ teaching ? '点击开始点名，随机抽取课堂成员' : '开始上课后即可点名' }}</p></div><div v-for="person in selected" :key="person.studentId" class="student-spot"><el-avatar :size="100" :src="person.avatar">{{ (person.nickname || '学生').slice(0, 1) }}</el-avatar><strong>{{ person.nickname || person.studentId }}</strong><span>{{ person.studentId }}</span></div></div>
    </section>
    <ClassCheckInPanel v-if="course" v-show="module === 'checkin'" :course-id="course.courseId" :active="teaching" />
    <ClassQuizPanel v-if="course" v-show="module === 'quiz'" :course-id="course.courseId" :session-id="session?.sessionId" :active="teaching" />
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref, shallowRef } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchTeacherCourses, fetchCourseMembers } from '@/api/course'
import { activeClass, startClass, heartbeatClass, endClass } from '@/api/classroom'
import { createClassroomRtc } from '@/utils/classroomRtc'
import ClassCheckInPanel from '@/components/ClassCheckInPanel.vue'
import ClassQuizPanel from '@/components/ClassQuizPanel.vue'
import StreamVideo from '@/components/StreamVideo.vue'

const route = useRoute(), router = useRouter()
const course = ref(null), members = ref([]), session = ref(null), loading = ref(true), starting = ref(false), ready = ref(false)
const mode = ref(route.query.mode === 'ONLINE' ? 'ONLINE' : 'LOCAL'), module = ref('roll'), drawCount = ref(1), selected = ref([]), drawing = ref(false), viewers = ref(0)
const camera = shallowRef(null), screen = shallowRef(null), deviceBusy = ref(''), mediaError = ref('')
const hasCamera = computed(() => camera.value?.getVideoTracks().some(t => t.readyState === 'live'))
const hasMic = computed(() => camera.value?.getAudioTracks().some(t => t.readyState === 'live'))
const teaching = computed(() => !!session.value && ready.value)
let rtc, heartbeat, rollTimer, disposed = false, ending = false
function secureMedia() {
  if (!window.isSecureContext || !navigator.mediaDevices) throw new Error('直播需要使用 HTTPS 或 localhost，并允许浏览器访问设备')
}
function errorText(error) {
  if (error.name === 'NotAllowedError') return '设备权限未获允许，请在浏览器权限设置中开启后重试'
  if (error.name === 'NotFoundError') return '未找到对应设备，请连接摄像头或麦克风后重试'
  if (error.name === 'NotReadableError') return '设备正在被其他应用占用，请关闭占用后重试'
  return error.message || '设备连接失败，请重试'
}
async function updateRtc() { if (rtc) try { await rtc.update() } catch { ElMessage.warning('直播连接更新失败，学生将自动尝试重新连接') } }
async function toggleDevice(kind) {
  deviceBusy.value = kind; mediaError.value = ''
  try {
    secureMedia()
    const tracks = camera.value?.getTracks().filter(t => t.readyState === 'live') || []
    const target = kind === 'camera' ? 'video' : 'audio'
    const existing = tracks.filter(t => t.kind === target)
    let next = tracks.filter(t => t.kind !== target)
    if (existing.length) existing.forEach(t => t.stop())
    else {
      const captured = await navigator.mediaDevices.getUserMedia({ video: target === 'video', audio: target === 'audio' })
      if (disposed) { captured.getTracks().forEach(t => t.stop()); return }
      captured.getTracks().forEach(track => { track.onended = () => { camera.value = new MediaStream(camera.value?.getTracks().filter(t => t.readyState === 'live') || []); updateRtc() } })
      next = [...next, ...captured.getTracks()]
    }
    camera.value = new MediaStream(next); await updateRtc()
  } catch (error) { mediaError.value = errorText(error) } finally { deviceBusy.value = '' }
}
const toggleCamera = () => toggleDevice('camera'), toggleMic = () => toggleDevice('mic')
async function toggleScreen() {
  deviceBusy.value = 'screen'; mediaError.value = ''
  try {
    secureMedia()
    if (screen.value) { screen.value.getTracks().forEach(t => t.stop()); screen.value = null }
    else {
      const captured = await navigator.mediaDevices.getDisplayMedia({ video: true, audio: false })
      if (disposed) { captured.getTracks().forEach(t => t.stop()); return }
      screen.value = captured
      captured.getVideoTracks()[0].onended = () => { screen.value = null; updateRtc() }
    }
    await updateRtc()
  } catch (error) { mediaError.value = errorText(error) } finally { deviceBusy.value = '' }
}
async function initializeRtc() {
  if (mode.value !== 'ONLINE' || rtc || disposed) return
  const connection = await createClassroomRtc({ sessionId: session.value.sessionId, teacher: true, streams: () => ({ camera: camera.value, screen: screen.value }), onCount: count => { viewers.value = count } })
  if (disposed) connection.close(); else rtc = connection
}
function startHeartbeat() {
  clearInterval(heartbeat)
  heartbeat = setInterval(async () => {
    if (!session.value || ending) return
    try { await heartbeatClass(session.value.sessionId) } catch (error) {
      if ([400, 403, 404].includes(error.response?.status)) { session.value = null; ready.value = false; clearInterval(heartbeat); releaseMedia(); ElMessage.warning('本次上课已结束') }
    }
  }, 15000)
}
async function begin() {
  if (starting.value) return
  if (mode.value === 'ONLINE' && !hasCamera.value && !screen.value) { ElMessage.warning('请先开启摄像头或屏幕共享'); return }
  starting.value = true
  try {
    if (!session.value) session.value = (await startClass(course.value.courseId, mode.value)).data
    await heartbeatClass(session.value.sessionId)
    await initializeRtc(); ready.value = true; startHeartbeat()
    ElMessage.success('已开始上课，学生已收到开课通知')
  } catch { if (session.value && !ready.value) { try { await endClass(session.value.sessionId) } catch {} session.value = null } }
  finally { starting.value = false }
}
function releaseMedia() {
  rtc?.close(); rtc = null
  camera.value?.getTracks().forEach(t => t.stop()); screen.value?.getTracks().forEach(t => t.stop())
  camera.value = null; screen.value = null
}
async function closeClass() {
  if (!session.value) return
  ending = true
  try { await endClass(session.value.sessionId); session.value = null; ready.value = false; clearInterval(heartbeat); releaseMedia() } finally { ending = false }
}
async function finish() {
  try { await ElMessageBox.confirm('结束后将关闭直播并结束正在进行的签到。', '结束本次上课', { type: 'warning' }); await closeClass(); ElMessage.success('本次上课已结束'); router.push('/teacher/courses') } catch {}
}
function randomIndex(max) {
  const data = new Uint32Array(1), limit = Math.floor(4294967296 / max) * max
  do { crypto.getRandomValues(data) } while (data[0] >= limit)
  return data[0] % max
}
function sample() {
  const pool = [...members.value]
  for (let i = pool.length - 1; i > 0; i--) { const j = randomIndex(i + 1); [pool[i], pool[j]] = [pool[j], pool[i]] }
  return pool.slice(0, Math.min(drawCount.value, pool.length))
}
async function draw() {
  if (!teaching.value || drawing.value) return
  try { members.value = (await fetchCourseMembers(course.value.courseId)).data || [] } catch { return }
  if (!members.value.length || disposed) { ElMessage.info('当前课堂暂无学生'); return }
  drawing.value = true
  let frames = 0
  rollTimer = setInterval(() => { selected.value = sample(); if (++frames >= 12) { clearInterval(rollTimer); drawing.value = false } }, 80)
}
function beforeUnload(event) { if (session.value) { event.preventDefault(); event.returnValue = '' } }
onBeforeRouteLeave(async () => {
  if (starting.value) return false
  if (!session.value) return true
  try { await ElMessageBox.confirm('离开将结束本次上课并关闭直播，确认离开？', '离开课堂', { type: 'warning' }); await closeClass(); return true } catch { return false }
})
onMounted(async () => {
  window.addEventListener('beforeunload', beforeUnload)
  try {
    const [courses, memberResult, current] = await Promise.all([fetchTeacherCourses(), fetchCourseMembers(route.params.courseId), activeClass(route.params.courseId)])
    if (disposed) return
    course.value = courses.data?.find(c => String(c.courseId) === String(route.params.courseId))
    if (!course.value || course.value.isFinished === 1) { ElMessage.warning('课程不存在或已结课'); router.replace('/teacher/courses'); return }
    members.value = memberResult.data || []
    if (current.data) { session.value = current.data; mode.value = current.data.mode; ready.value = mode.value === 'LOCAL'; startHeartbeat() }
  } catch {} finally { loading.value = false }
})
onBeforeUnmount(() => { disposed = true; clearInterval(heartbeat); clearInterval(rollTimer); releaseMedia(); window.removeEventListener('beforeunload', beforeUnload) })
</script>

<style scoped>
.class-header, .header-actions, .roll-toolbar, .draw-controls { display: flex; align-items: center; justify-content: space-between; gap: 14px; }
.class-header { margin-bottom: 24px; }
h2 { margin: 8px 0; font-size: 26px; } h3 { margin: 0 0 8px; font-size: 17px; }
p { margin: 0; color: #87909e; font-size: 13px; }
.media-panel { display: flex; justify-content: space-between; gap: 24px; background: white; border: 1px solid #eef0f4; border-radius: 16px; padding: 22px; margin-bottom: 24px; }
.media-settings { flex: 1; min-width: 0; }.media-buttons { display: flex; gap: 10px; flex-wrap: wrap; margin: 20px 0; }.media-buttons .el-button { margin: 0; gap: 6px; }
.self-preview { width: 300px; flex-shrink: 0; display: grid; gap: 8px; align-content: start; }.preview-tile, .preview-empty { border-radius: 12px; overflow: hidden; background: #172033; position: relative; }
.preview-tile video { display: block; width: 100%; max-height: 170px; object-fit: contain; }.preview-tile span { position: absolute; bottom: 8px; left: 10px; color: white; font-size: 11px; background: #0008; padding: 3px 8px; border-radius: 6px; }
.preview-empty { height: 160px; display: grid; place-content: center; gap: 12px; text-align: center; color: #94a3b8; }.preview-empty i { font-size: 32px; }
.module-tabs { display: flex; gap: 12px; margin: 20px 0; }.module-tabs button { padding: 14px 22px; display: flex; align-items: center; gap: 8px; border: 1px solid #e5e7eb; background: white; border-radius: 12px; color: #64748b; cursor: pointer; font-size: 15px; }.module-tabs .selected { border-color: var(--brand-blue); color: #168cc6; background: #eef9ff; }
.roll-panel { border: 1px solid #eef0f4; border-radius: 16px; background: white; padding: 24px; }.draw-controls { flex-wrap: wrap; font-size: 14px; color: #64748b; }
.draw-stage { min-height: 360px; display: flex; flex-wrap: wrap; align-items: center; justify-content: center; gap: 36px; padding: 30px 0; background: radial-gradient(ellipse at center, #effaff, white 70%); }
.draw-empty { text-align: center; color: #a8c9dc; }.draw-empty i { font-size: 72px; }.draw-empty p { margin-top: 18px; }.student-spot { display: flex; flex-direction: column; align-items: center; gap: 12px; min-width: 120px; }.student-spot .el-avatar { box-shadow: 0 8px 25px #66ccff33; background: #e4f5ff; color: #409eff; font-size: 36px; }.student-spot strong { font-size: 22px; }.student-spot span { font-size: 12px; color: #9ca3af; }
@media(max-width: 1050px) { .media-panel,.class-header,.roll-toolbar { flex-direction: column; align-items: stretch; }.self-preview { width: 100%; grid-template-columns: repeat(2,minmax(0,1fr)); }.preview-tile video { max-height: 200px; } }
</style>

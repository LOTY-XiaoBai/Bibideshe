<template>
  <div v-loading="loading" class="live-page">
    <header><div><el-button text @click="router.push('/student/courses')">← 返回我的课程</el-button><h2>{{ session?.courseName || '课程直播' }}</h2><p>网络授课 · {{ statusText }}</p></div><el-tag v-if="session && !ended" type="success">直播中</el-tag></header>
    <section class="live-stage">
      <StreamVideo v-if="screen" :stream="screen" muted controls class="screen-video" />
      <StreamVideo v-if="camera" :stream="camera" controls :class="screen ? 'camera-video' : 'main-video'" />
      <div v-if="!screen && !camera" class="waiting"><i class="ph ph-broadcast" /><h3>{{ ended ? '本次上课已结束' : '正在连接教师直播' }}</h3><p>{{ ended ? '可返回课程列表查看后续安排' : '连接成功后会展示教师画面，学生无需开启摄像头或麦克风' }}</p></div>
    </section>
    <div v-if="!ended" class="live-toolbar"><p>如听不到声音，请点击视频的播放按钮开启声音。</p><el-button @click="reconnect" :loading="connecting">重新连接</el-button><el-button type="primary" @click="router.push('/student/check-in')">前往签到<span v-if="classroom.hasPending" class="red-dot" /></el-button></div>
    <el-alert v-if="connectionState === 'failed' && !ended" title="直播暂未连接成功，正在自动重试。也可点击重新连接。" type="warning" :closable="false" />
  </div>
</template>
<script setup>
import { computed, onMounted, onBeforeUnmount, ref, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { classroomDetail } from '@/api/classroom'
import { createClassroomRtc } from '@/utils/classroomRtc'
import { onChatEvent } from '@/utils/websocket'
import { useClassroomStore } from '@/stores/classroom'
import StreamVideo from '@/components/StreamVideo.vue'

const route = useRoute(), router = useRouter(), classroom = useClassroomStore()
const session = ref(null), loading = ref(true), camera = shallowRef(null), screen = shallowRef(null), ended = ref(false), connectionState = ref('connecting'), connecting = ref(false)
const statusText = computed(() => ended.value ? '已结束' : ({ connected: '已连接', connecting: '连接中', disconnected: '连接中断，正在重连', failed: '连接失败，正在重试', new: '等待教师画面' }[connectionState.value] || '连接中'))
let rtc, off, timer, disposed = false, generation = 0
function close() { generation++; rtc?.close(); rtc = null; camera.value = null; screen.value = null }
async function reconnect() {
  if (connecting.value || ended.value || !session.value) return
  connecting.value = true; close(); const stamp = generation
  try {
    const instance = await createClassroomRtc({ sessionId: session.value.sessionId, teacher: false, teacherId: session.value.teacherId,
      onRemote: (kind, stream) => { if (!disposed && generation === stamp) { if (kind === 'screen') screen.value = stream; else camera.value = stream } },
      onState: state => { if (!disposed && generation === stamp) connectionState.value = state },
    })
    if (disposed || generation !== stamp) instance.close(); else rtc = instance
  } catch { connectionState.value = 'failed' } finally { connecting.value = false }
}
function finish() { ended.value = true; close(); clearInterval(timer) }
onMounted(async () => {
  off = onChatEvent(event => { if (event.type === 'CLASS_ENDED' && event.data?.sessionId === route.params.sessionId) finish() })
  try {
    session.value = (await classroomDetail(route.params.sessionId)).data
    if (disposed) return
    if (session.value?.mode !== 'ONLINE') { router.replace('/student/courses'); return }
    await reconnect()
    if (disposed) return
    timer = setInterval(async () => { try { await classroomDetail(route.params.sessionId) } catch (error) { if ([400, 403, 404].includes(error.response?.status)) finish() } }, 15000)
  } catch { ended.value = true } finally { loading.value = false }
})
onBeforeUnmount(() => { disposed = true; off?.(); clearInterval(timer); close() })
</script>
<style scoped>
header,.live-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 22px; }h2 { margin: 8px 0; font-size: 25px; }p { color: #8a94a3; font-size: 13px; margin: 0; }
.live-stage { min-height: 500px; border-radius: 18px; background: #111b2d; position: relative; overflow: hidden; display: flex; align-items: center; justify-content: center; }
.screen-video,.main-video { width: 100%; max-height: 70vh; min-height: 400px; object-fit: contain; }.camera-video { position: absolute; right: 20px; top: 20px; width: 230px; max-height: 180px; border-radius: 12px; border: 2px solid #ffffff70; background: #1e293b; }
.waiting { padding: 40px; text-align: center; color: #cbd5e1; }.waiting i { font-size: 64px; color: #66ccff; }.waiting p { max-width: 380px; line-height: 1.8; }.live-toolbar { margin-top: 18px; }.live-toolbar p { flex: 1; }.red-dot { width: 7px; height: 7px; border-radius: 50%; background: #ef4444; margin-left: 8px; }
@media(max-width:800px){.live-stage{min-height:360px}.camera-video{width:140px;right:10px;top:10px}.live-toolbar{flex-wrap:wrap}.live-toolbar p{flex-basis:100%}}
</style>

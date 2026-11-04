import { defineStore } from 'pinia'
import { computed, h, onMounted, onUnmounted, ref } from 'vue'
import { ElMessageBox, ElNotification } from 'element-plus'
import { useRouter } from 'vue-router'
import { useUserStore } from './user'
import { getToken } from '@/utils/auth'
import { onChatEvent } from '@/utils/websocket'
import { fetchTeacherCourses } from '@/api/course'
import { pendingCheckIns, studentActiveClasses } from '@/api/classroom'
import { fetchPendingQuizzes } from '@/api/quiz'

export const useClassroomStore = defineStore('classroom', () => {
  const checkIns = ref([]), liveClasses = ref([]), quizzes = ref([]), dialogQuizId = ref(null)
  const quizQueue = []
  const hasPending = computed(() => checkIns.value.length > 0)
  let users = 0, timer, off, busy = false, generation = 0, owner = '', navigate
  const seen = new Set(), notices = new Map()
  function openQuizDialog(id) {
    if (String(dialogQuizId.value) === String(id)) return
    if (dialogQuizId.value) { if (!quizQueue.some(q => String(q) === String(id))) quizQueue.push(id); return }
    dialogQuizId.value = id
  }
  function closeQuizDialog() { dialogQuizId.value = quizQueue.shift() || null }
  function notifyQuiz(data) {
    const key = `${owner}:quiz:${data.id}`
    if (seen.has(key)) return
    seen.add(key)
    if (data.mode === 'ONLINE') { openQuizDialog(data.id); return }
    let notification
    notification = ElNotification({ title: '你有一份课堂小测', message: h('div', [h('p', `教师发布了「${data.name}」，请及时完成。`), h('button', { class: 'class-notice-button', onClick: () => { notification?.close(); navigate(`/student/quiz/${data.id}`) } }, '前往作答')]), duration: 0, type: 'info' })
    notices.set(`quiz:${data.id}`, notification)
  }
  function notifyClass(data) {
    const key = `${owner}:${data.sessionId}`
    if (seen.has(key)) return
    seen.add(key)
    let notification
    const children = [h('p', `你的${data.courseName}课程开始上课！本次授课为${data.mode === 'ONLINE' ? '网络' : '本地'}授课`)]
    if (data.mode === 'ONLINE') children.push(h('button', {
      class: 'class-notice-button', onClick: () => { notification?.close(); navigate(`/student/live/${data.sessionId}`) },
    }, '确认并进入直播间'))
    notification = ElNotification({ title: '课程开课通知', message: h('div', children), duration: data.mode === 'ONLINE' ? 0 : 10000, type: 'success' })
    notices.set(data.sessionId, notification)
  }
  async function remind(courses, stamp) {
    // 固定课表按中国时区计算，覆盖周日到周一及跨午夜前五分钟。
    const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date())
    const part = type => parts.find(p => p.type === type).value
    const date = `${part('year')}-${part('month')}-${part('day')}`
    for (const c of courses.filter(c => c.fixedSchedule && c.isFinished !== 1 && c.classTime)) {
      let next = new Date(`${date}T${c.classTime}:00+08:00`)
      const today = new Date(`${date}T00:00:00+08:00`)
      const weekday = new Date(today.getTime() + 8 * 3600000).getUTCDay() || 7
      next = new Date(next.getTime() + ((Number(c.classWeekday) - weekday + 7) % 7) * 86400000)
      if (next.getTime() < Date.now()) next = new Date(next.getTime() + 7 * 86400000)
      const delta = next.getTime() - Date.now()
      if (delta > 300000 || delta <= 0) continue
      const key = `class-reminder:${owner}:${c.courseId}:${next.toISOString()}`
      if (sessionStorage.getItem(key)) continue
      sessionStorage.setItem(key, '1')
      try {
        await ElMessageBox.confirm(`「${c.courseName}」将在 ${c.classTime} 开课，请准备上课。`, '上课提醒 · 前5分钟', { confirmButtonText: '前往上课', cancelButtonText: '稍后上课', distinguishCancelAndClose: true })
        if (generation === stamp) navigate(`/teacher/classroom/${c.courseId}?mode=LOCAL`)
      } catch {}
    }
  }
  async function refresh() {
    if (!users || !getToken() || busy) return
    busy = true
    const stamp = generation, user = useUserStore(), token = getToken()
    try {
      if (user.role === 'STUDENT') {
        const [checks, classes, pending] = await Promise.all([pendingCheckIns(), studentActiveClasses(), fetchPendingQuizzes()])
        if (generation !== stamp || token !== getToken()) return
        checkIns.value = checks.data || []; liveClasses.value = classes.data || []; quizzes.value = pending.data || []
        liveClasses.value.forEach(notifyClass)
        quizzes.value.forEach(notifyQuiz)
        for (const [id, notice] of notices) {
          const active = String(id).startsWith('quiz:') ? quizzes.value.some(q => `quiz:${q.id}` === id) : liveClasses.value.some(c => c.sessionId === id)
          if (!active) { notice.close(); notices.delete(id) }
        }
      } else if (user.role === 'TEACHER') {
        const { data } = await fetchTeacherCourses()
        if (generation === stamp && token === getToken()) await remind(data || [], stamp)
      }
    } catch {} finally { busy = false }
  }
  function start(router) {
    navigate = path => router.push(path)
    if (users++ > 0) return
    generation++
    const id = String(useUserStore().userId)
    if (id !== owner) { seen.clear(); owner = id }
    off = onChatEvent(event => {
      if (event.type === 'CLASS_STARTED' && useUserStore().role === 'STUDENT') notifyClass(event.data)
      if (event.type === 'QUIZ_PUBLISHED' && useUserStore().role === 'STUDENT') notifyQuiz(event.data)
      if (['CLASS_STARTED', 'CLASS_ENDED', 'CHECK_IN_PUBLISHED', 'QUIZ_PUBLISHED', 'QUIZ_ENDED'].includes(event.type)) { refresh(); window.dispatchEvent(new Event('classroom-changed')) }
    })
    refresh(); timer = setInterval(refresh, 15000)
    window.addEventListener('focus', refresh)
    window.addEventListener('classroom-refresh', refresh)
  }
  function stop() {
    if (--users > 0) return
    users = 0; generation++; clearInterval(timer); off?.(); checkIns.value = []; liveClasses.value = []; quizzes.value = []; dialogQuizId.value = null; quizQueue.length = 0
    notices.forEach(n => n.close()); notices.clear()
    window.removeEventListener('focus', refresh); window.removeEventListener('classroom-refresh', refresh)
  }
  return { checkIns, liveClasses, quizzes, dialogQuizId, openQuizDialog, closeQuizDialog, hasPending, refresh, start, stop }
})
export function useClassroomNotifications() {
  const store = useClassroomStore(), router = useRouter()
  onMounted(() => store.start(router)); onUnmounted(store.stop)
  return store
}

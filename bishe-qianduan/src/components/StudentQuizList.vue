<template>
  <section class="student-quiz-list"><div class="list-head"><p>查看小测、成绩、题目答案与开放的排名</p><el-button text @click="load">刷新</el-button></div><el-empty v-if="!filtered.length" description="暂时没有课堂小测" /><article v-for="q in filtered" :key="q.id" class="quiz-row"><div><div class="quiz-title"><h4>{{ q.name }}</h4><el-tag :type="q.endedAt ? 'info' : 'success'" size="small">{{ q.endedAt ? '已结束' : '进行中' }}</el-tag><el-tag v-if="Number(q.countScore)" size="small">计入总分</el-tag></div><p>{{ q.courseName }} · {{ q.mode === 'ONLINE' ? '网络课堂' : '本地课堂' }} · 满分 {{ q.maxScore }} 分</p><p v-if="['SUBMITTED','ABSENT'].includes(q.status)">{{ q.status === 'ABSENT' ? '未提交' : '已提交' }} · 得分 {{ Number(q.hideAnswers) && !q.endedAt ? '待公布' : q.score }}<span v-if="!Number(q.graded) && q.status === 'SUBMITTED'">（含待批阅题目）</span></p></div><el-button type="primary" plain @click="open(q)">{{ !q.endedAt && ['PENDING','DRAFT'].includes(q.status) ? '进入小测' : '成绩与复盘' }}</el-button></article></section>
</template>
<script setup>
import { computed,onMounted,onUnmounted,ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchStudentQuizzes } from '@/api/quiz'
import { useClassroomStore } from '@/stores/classroom'
const props=defineProps({courseId:[String,Number]})
const router=useRouter(),classroom=useClassroomStore(),quizzes=ref([])
const filtered=computed(()=>quizzes.value.filter(q=>!props.courseId||String(q.courseId)===String(props.courseId)))
let timer
async function load(){try{quizzes.value=(await fetchStudentQuizzes()).data||[]}catch{}}
function open(q){if(q.mode==='ONLINE'&&!q.endedAt)classroom.openQuizDialog(q.id);else router.push(`/student/quiz/${q.id}`)}
onMounted(()=>{load();timer=setInterval(load,10000);window.addEventListener('classroom-changed',load)})
onUnmounted(()=>{clearInterval(timer);window.removeEventListener('classroom-changed',load)})
</script>
<style scoped>
.list-head,.quiz-row,.quiz-title { display:flex;align-items:center;justify-content:space-between;gap:14px; }.list-head p,p { color:#87909e;font-size:13px; }.quiz-row { background:white;border:1px solid #edf0f4;border-radius:14px;padding:20px;margin-bottom:14px; }.quiz-title { justify-content:flex-start;flex-wrap:wrap; }h4 { margin:0;font-size:17px; }p { margin:8px 0 0; }
</style>

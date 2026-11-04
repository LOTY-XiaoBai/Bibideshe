<template>
  <div class="course-list-page">
    <header class="page-header"><div><h2>我的授课</h2><p>管理课程、课堂教学与学习资料，最多同时进行 5 门课程</p></div><el-button class="create-btn" type="primary" :disabled="activeCount >= 5" @click="createVisible = true"><i class="ph ph-plus-circle" aria-hidden="true" /> 创建新课程</el-button></header>
    <div class="course-stats"><div><span>进行中 / 上限</span><strong>{{ activeCount }} / 5</strong></div><div><span>已结课</span><strong>{{ finishedCount }}</strong></div><div><span>待处理入课申请</span><strong class="request-count">{{ pendingCount }}</strong></div></div>
    <el-skeleton v-if="loading && !courses.length" :rows="5" animated />
    <el-empty v-else-if="!courses.length" description="尚未创建课程，创建第一门课程开始教学" />
    <div v-else class="course-grid"><article v-for="course in courses" :key="course.courseId" class="course-card">
      <div class="course-cover" :class="{ 'has-image': course.coverUrl }"><img v-if="course.coverUrl" :src="course.coverUrl" alt="课程封面" /><template v-else><i class="ph ph-books" aria-hidden="true" /><span>COURSE</span></template><el-tag class="status" :type="course.isFinished === 1 ? 'info' : 'success'">{{ course.isFinished === 1 ? '已结课' : '进行中' }}</el-tag><span v-if="Number(course.pendingApplications)" class="application-badge" :aria-label="`${course.pendingApplications}条入课申请`"><i />{{ course.pendingApplications }}</span></div>
      <div class="course-body"><h3>{{ course.courseName }}</h3><p class="course-id">课程号 {{ course.courseId }}</p><div class="course-meta"><span><i class="ph ph-users" aria-hidden="true" /> {{ course.memberCount || 0 }} 位成员<span v-if="course.maxStudents"> / {{ course.maxStudents }} 人上限</span></span><span><i class="ph ph-clock" aria-hidden="true" /> {{ course.fixedSchedule ? `每周${weekdays[course.classWeekday-1]} ${course.classTime}` : '授课时间灵活安排' }}</span></div><div class="card-footer"><span>{{ course.approvalRequired ? '加入需教师审核' : '允许直接加入' }}</span><el-button type="primary" plain @click="router.push(`/teacher/courses/${course.courseId}`)">编辑 <i class="ph ph-arrow-right" aria-hidden="true" /></el-button></div></div>
    </article></div>
    <el-dialog v-model="createVisible" title="创建新课程" width="min(660px,94vw)" align-center destroy-on-close><CourseSettingsEditor cancelable @cancel="createVisible = false" @saved="created" /></el-dialog>
  </div>
</template>
<script setup>
import {computed,onMounted,onUnmounted,ref} from 'vue'
import {useRouter} from 'vue-router'
import {fetchTeacherCourses} from '@/api/course'
import {useUnreadStore} from '@/stores/unread'
import CourseSettingsEditor from '@/components/CourseSettingsEditor.vue'
const router=useRouter(),unread=useUnreadStore(),courses=ref([]),loading=ref(false),createVisible=ref(false)
const weekdays=['一','二','三','四','五','六','日']
const activeCount=computed(()=>courses.value.filter(c=>c.isFinished!==1).length),finishedCount=computed(()=>courses.value.filter(c=>c.isFinished===1).length),pendingCount=computed(()=>courses.value.reduce((n,c)=>n+Number(c.pendingApplications||0),0))
let timer
async function load(){loading.value=true;try{courses.value=(await fetchTeacherCourses()).data||[]}catch{}finally{loading.value=false}}
async function created(){createVisible.value=false;await load();unread.refresh()}
onMounted(()=>{load();timer=setInterval(load,10000)})
onUnmounted(()=>clearInterval(timer))
</script>
<style scoped>
.page-header { display:flex;justify-content:space-between;align-items:flex-start;gap:18px;margin-bottom:24px; }h2 { margin:0;font-size:26px; }p { color:#8792a2;font-size:13px;line-height:1.7; }.create-btn { gap:7px; }.course-stats { display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px;margin-bottom:26px; }.course-stats > div { background:white;border:1px solid #edf1f6;border-radius:16px;padding:20px 24px; }.course-stats span { display:block;font-size:12px;color:#9aa4b2;margin-bottom:9px; }.course-stats strong { font-size:28px;color:#273449; }.course-stats .request-count { color:#f06475; }.course-grid { display:grid;grid-template-columns:repeat(auto-fill,minmax(360px,1fr));gap:24px; }.course-card { background:white;border:1px solid #edf1f6;border-radius:20px;overflow:hidden;box-shadow:0 6px 24px #243b5310; }.course-cover { height:176px;position:relative;display:flex;align-items:center;justify-content:center;gap:18px;background:linear-gradient(125deg,#e3f5ff,#dff9f1);overflow:hidden; }.course-cover > i { font-size:70px;color:#8cc7d6; }.course-cover > span:not(.application-badge):not(.status) { font-size:32px;letter-spacing:.2em;color:#8ec4d044;font-weight:800; }.course-cover img { width:100%;height:100%;object-fit:cover; }.status { position:absolute;left:18px;top:18px; }.application-badge { position:absolute;right:18px;top:18px;display:flex;gap:7px;align-items:center;border-radius:20px;padding:5px 11px;background:#fff;color:#f04452;font-size:13px;font-weight:700;box-shadow:0 3px 12px #27344915; }.application-badge i { width:8px;height:8px;border-radius:50%;background:#f04452; }.course-body { padding:25px; }.course-body h3 { margin:0;font-size:22px;color:#263449; }.course-id { margin:8px 0 18px; }.course-meta { display:grid;gap:12px;color:#67768a;font-size:13px; }.course-meta i { font-size:17px;margin-right:5px; }.card-footer { border-top:1px solid #f0f3f7;margin-top:23px;padding-top:20px;display:flex;justify-content:space-between;align-items:center;gap:12px; }.card-footer > span { font-size:12px;color:#9ba4b1; }.card-footer .el-button { gap:8px; }
@media(max-width:800px){.page-header { flex-wrap:wrap; }.course-grid { grid-template-columns:1fr; }.course-stats { gap:10px; }.course-stats > div { padding:15px; }}
</style>

<template>
  <div class="page">
    <header class="page__header">
      <div>
        <h2>{{ isCheckInPage ? '课程签到' : '考试 / 作业 / 小测' }}</h2>
        <p>{{ isCheckInPage ? '完成课堂签到，查看签到和漏签记录' : '参加课程考核，查看小测成绩、排名和答题复盘' }}</p>
      </div>
    </header>

    <div class="toolbar">
      <el-select v-model="selectedCourseId" placeholder="选择课程" style="width: 260px" @change="onCourseChange">
        <el-option label="全部课程" :value="0" />
        <el-option
          v-for="c in courses"
          :key="c.courseId"
          :label="`${c.courseName} (${c.courseId})`"
          :value="c.courseId"
        />
      </el-select>
      <el-button text :loading="loading" @click="reloadAll">刷新</el-button>
    </div>

    <el-tabs v-if="!isCheckInPage" v-model="activeTab">
      <el-tab-pane label="考试" name="exam" />
      <el-tab-pane label="作业" name="homework" />
      <el-tab-pane label="小测" name="quiz" />
    </el-tabs>

    <!-- 考试 -->
    <StudentQuizList v-if="!isCheckInPage && activeTab === 'quiz'" :course-id="selectedCourseId" />
    <section v-show="activeTab === 'exam'" v-loading="loading">
      <div v-if="!courses.length" class="empty">请先加入课程</div>
      <div v-else-if="!exams.length" class="empty">暂无考试</div>
      <div v-else class="task-list">
        <article v-for="exam in exams" :key="exam.examId" class="task-card">
          <div>
            <h4>考试 #{{ exam.examId }}</h4>
            <p>{{ exam.courseName }}</p>
            <p>限时 {{ exam.timeLimit }} 分钟</p>
            <p v-if="isExamSubmitted(exam) && canViewResult(exam)">
              得分：{{ formatScore(examRecordMap[exam.examId].teacherScore ?? examRecordMap[exam.examId].examScore) }}
            </p>
            <p v-else-if="isExamSubmitted(exam) && exam.hideResultUntilPublish === 1" class="hint-text">
              成绩待教师公布
            </p>
          </div>
          <div class="task-actions">
            <el-button
              v-if="!isExamSubmitted(exam)"
              type="primary"
              @click="openExam(exam)"
            >
              开始考试
            </el-button>
            <template v-else>
              <el-tag type="success" size="small">已提交</el-tag>
              <el-button
                size="small"
                @click="viewExamReview(exam.examId)"
              >
                {{ canViewResult(exam) ? '查看复盘' : '查看作答' }}
              </el-button>
              <el-tag v-if="!canViewResult(exam)" size="small" type="warning">待公布</el-tag>
            </template>
          </div>
        </article>
      </div>
    </section>

    <!-- 作业 -->
    <section v-show="activeTab === 'homework'" v-loading="loading">
      <div v-if="!courses.length" class="empty">请先加入课程</div>
      <div v-else-if="!homeworks.length" class="empty">暂无作业</div>
      <div v-else class="task-list">
        <article v-for="hw in homeworks" :key="hw.homeworkId" class="task-card">
          <div>
            <h4>作业 #{{ hw.homeworkId }}</h4>
            <p>{{ hw.courseName }}</p>
            <p>截止：{{ formatTime(hw.deadline) }}</p>
            <p v-if="homeworkRecordMap[hw.homeworkId] && canViewResult(hw)">
              得分：{{ formatScore(homeworkRecordMap[hw.homeworkId].teacherScore ?? homeworkRecordMap[hw.homeworkId].homeworkScore) }}
            </p>
            <p v-else-if="homeworkRecordMap[hw.homeworkId] && hw.hideResultUntilPublish === 1" class="hint-text">
              成绩待教师公布
            </p>
          </div>
          <div class="task-actions">
            <el-button
              v-if="!homeworkRecordMap[hw.homeworkId]"
              type="primary"
              :disabled="isPast(hw.deadline)"
              @click="openHomework(hw)"
            >
              {{ isPast(hw.deadline) ? '已截止' : '开始作答' }}
            </el-button>
            <template v-else>
              <el-tag type="success" size="small">已提交</el-tag>
              <el-button
                size="small"
                @click="viewHomeworkReview(hw.homeworkId)"
              >
                {{ canViewResult(hw) ? '查看复盘' : '查看作答' }}
              </el-button>
              <el-tag v-if="!canViewResult(hw)" size="small" type="warning">待公布</el-tag>
            </template>
          </div>
        </article>
      </div>
    </section>

    <!-- 签到 -->
    <section v-show="activeTab === 'checkin'">
      <el-tabs v-model="checkinSubTab" class="sub-tabs">
        <el-tab-pane label="可签到" name="active" />
        <el-tab-pane label="签到记录" name="records" />
        <el-tab-pane label="漏签记录" name="missed" />
      </el-tabs>

      <div v-if="checkinSubTab === 'active'" v-loading="checkinLoading">
        <div v-if="!activeCheckIns.length" class="empty">当前没有进行中的签到</div>
        <div v-else class="task-list">
          <article v-for="item in activeCheckIns" :key="item.checkInId" class="task-card">
            <div>
              <h4>{{ item.courseName || `课程 ${item.courseId}` }}</h4>
              <p>签到 #{{ item.checkInId }} · 截止 {{ formatTime(item.deadline) }}</p>
            </div>
            <el-button type="primary" :loading="signingId === item.checkInId" @click="handleSign(item.checkInId)">
              立即签到
            </el-button>
          </article>
        </div>
      </div>

      <div v-else-if="checkinSubTab === 'records'" v-loading="checkinLoading">
        <el-table :data="checkinRecords" size="small" empty-text="暂无签到记录">
          <el-table-column prop="courseName" label="课程" />
          <el-table-column prop="checkInId" label="签到号" width="90" />
          <el-table-column label="发布时间" min-width="140">
            <template #default="{ row }">{{ formatTime(row.publishTime) }}</template>
          </el-table-column>
          <el-table-column label="签到时间" min-width="140">
            <template #default="{ row }">{{ formatTime(row.checkInTime) || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="checkinStatusType(row.status)" size="small">
                {{ checkinStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-else v-loading="checkinLoading">
        <div class="toolbar" v-if="selectedCourseId">
          <span class="hint">当前课程漏签</span>
        </div>
        <el-table :data="missedRecords" size="small" empty-text="暂无漏签记录">
          <el-table-column prop="courseName" label="课程" />
          <el-table-column prop="checkInId" label="签到号" width="90" />
          <el-table-column label="发布时间" min-width="140">
            <template #default="{ row }">{{ formatTime(row.publishTime) }}</template>
          </el-table-column>
          <el-table-column label="截止时间" min-width="140">
            <template #default="{ row }">{{ formatTime(row.deadline) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </section>


  </div>
</template>

<script setup>
import {computed,onMounted,onUnmounted,reactive,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import {useUserStore} from '@/stores/user'
import {useClassroomStore} from '@/stores/classroom'
import {fetchStudentCourses} from '@/api/course'
import {fetchExamsByCourse,fetchStudentExamRecords} from '@/api/exam'
import {fetchHomeworksByCourse,fetchStudentHomeworkRecords} from '@/api/homework'
import {signCheckIn,fetchStudentCheckInRecords,fetchMissedByCourse,fetchAllMissed} from '@/api/checkIn'
import StudentQuizList from '@/components/StudentQuizList.vue'
const route=useRoute(),router=useRouter(),userStore=useUserStore(),classroom=useClassroomStore(),isCheckInPage=computed(()=>route.path==='/student/check-in')
const activeTab=ref(isCheckInPage.value?'checkin':['exam','homework','quiz'].includes(route.query.tab)?route.query.tab:'exam'),checkinSubTab=ref('active'),loading=ref(false),checkinLoading=ref(false),signingId=ref(null),courses=ref([]),selectedCourseId=ref(0),exams=ref([]),homeworks=ref([]),examRecordMap=reactive({}),homeworkRecordMap=reactive({}),checkinRecords=ref([]),missedRecords=ref([])
const activeCheckIns=computed(()=>classroom.checkIns.filter(r=>!selectedCourseId.value||String(r.courseId)===String(selectedCourseId.value)))
const formatScore=v=>v==null?'-':Number(v).toFixed(1),formatTime=v=>v?String(v).replace('T',' ').slice(0,16):'-',isPast=v=>v&&new Date(v).getTime()<Date.now(),canViewResult=t=>Number(t?.hideResultUntilPublish)!==1||Number(t?.isResultPublished)===1,isExamSubmitted=e=>examRecordMap[e.examId]?.submitted===true
const checkinStatusLabel=s=>Number(s)===1?'已签到':Number(s)===2?'迟到':'缺勤',checkinStatusType=s=>Number(s)===1?'success':Number(s)===2?'warning':'danger'
const selectedCourses=()=>selectedCourseId.value?courses.value.filter(c=>String(c.courseId)===String(selectedCourseId.value)):courses.value
async function loadExamData(){const [lists,records]=await Promise.all([Promise.all(selectedCourses().map(async c=>(await fetchExamsByCourse(c.courseId)).data.map(e=>({...e,courseName:c.courseName})))),fetchStudentExamRecords(userStore.userId)]);exams.value=lists.flat();Object.keys(examRecordMap).forEach(k=>delete examRecordMap[k]);for(const r of records.data||[])examRecordMap[r.examId]=r}
async function loadHomeworkData(){const [lists,records]=await Promise.all([Promise.all(selectedCourses().map(async c=>(await fetchHomeworksByCourse(c.courseId)).data.map(e=>({...e,courseName:c.courseName})))),fetchStudentHomeworkRecords(userStore.userId)]);homeworks.value=lists.flat();Object.keys(homeworkRecordMap).forEach(k=>delete homeworkRecordMap[k]);for(const r of records.data||[])homeworkRecordMap[r.homeworkId]=r}
async function loadCheckinData(){checkinLoading.value=true;try{const [a,b]=await Promise.all([fetchStudentCheckInRecords(userStore.userId,1,100),selectedCourseId.value?fetchMissedByCourse(userStore.userId,selectedCourseId.value):fetchAllMissed(userStore.userId,1,100)]);checkinRecords.value=a.data?.records||[];missedRecords.value=selectedCourseId.value?b.data||[]:b.data?.records||[]}catch{}finally{checkinLoading.value=false}}
async function reloadAll(){loading.value=true;try{courses.value=(await fetchStudentCourses()).data||[];await(isCheckInPage.value?loadCheckinData():Promise.all([loadExamData(),loadHomeworkData()]))}catch{}finally{loading.value=false}}
async function onCourseChange(){loading.value=true;try{await(isCheckInPage.value?loadCheckinData():Promise.all([loadExamData(),loadHomeworkData()]))}catch{}finally{loading.value=false}}
const openExam=e=>router.push(`/student/assessment/exam/${e.examId}`),openHomework=h=>router.push(`/student/assessment/homework/${h.homeworkId}`),viewExamReview=id=>router.push(`/student/assessment/exam/${id}`),viewHomeworkReview=id=>router.push(`/student/assessment/homework/${id}`)
async function handleSign(id){signingId.value=id;try{await signCheckIn(id);await classroom.refresh();await loadCheckinData();ElMessage.success('签到成功')}catch{}finally{signingId.value=null}}
watch(()=>route.query.tab,v=>{if(['exam','homework','quiz'].includes(v)&&!isCheckInPage.value)activeTab.value=v});watch(isCheckInPage,v=>{activeTab.value=v?'checkin':['exam','homework','quiz'].includes(route.query.tab)?route.query.tab:'exam';reloadAll()})
let timer
function changed(){if(isCheckInPage.value)loadCheckinData()}
onMounted(()=>{reloadAll();classroom.refresh();timer=setInterval(changed,15000);window.addEventListener('classroom-changed',changed)})
onUnmounted(()=>{clearInterval(timer);window.removeEventListener('classroom-changed',changed)})
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

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 20px 0 12px;
}

.hint {
  font-size: 13px;
  color: #9ca3af;
}

.sub-tabs {
  margin-bottom: 12px;
}

.empty {
  padding: 40px;
  text-align: center;
  color: #9ca3af;
  background: #fff;
  border-radius: 16px;
  border: 1px dashed #e5e7eb;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.task-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid #f3f4f6;
}

.task-card h4 {
  margin: 0 0 6px;
}

.task-card p {
  margin: 0 0 4px;
  font-size: 13px;
  color: #9ca3af;
}

.task-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.timer-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  padding: 10px 14px;
  border-radius: 10px;
  background: rgba(102, 204, 255, 0.12);
  color: #0891b2;
  font-weight: 600;
}

.deadline-tip {
  margin: 0 0 14px;
  color: #6b7280;
  font-size: 13px;
}

.review-summary {
  margin-bottom: 14px;
  padding: 10px 14px;
  border-radius: 10px;
  background: var(--theme-hover-bg);
  font-weight: 600;
}

.hint-text {
  color: #f59e0b !important;
}
</style>

<template>
  <div v-loading="loading" class="assessment-page">
    <el-button text @click="router.push(`/student/tasks?tab=${kind}`)">← 返回考试/作业/小测</el-button>
    <template v-if="task"><header class="assessment-header"><div><span class="eyebrow">{{ isExam ? 'COURSE EXAM' : 'COURSE HOMEWORK' }}</span><h1>{{ label }} #{{ taskId }}</h1><p>{{ submitted ? '已提交 · 查看本次作答与学习复盘' : '完成作答后提交，内容会在当前浏览器自动暂存' }}</p></div><el-tag :type="submitted ? 'success' : expired ? 'info' : 'warning'">{{ submitted ? '已提交' : expired ? '已截止' : isExam ? `剩余 ${countdown}` : '作答中' }}</el-tag></header>
      <div class="assessment-facts"><div><span>题目数量</span><strong>{{ items.length }}<small> 题</small></strong></div><div><span>试卷满分</span><strong>{{ totalScore }}<small> 分</small></strong></div><div v-if="submitted"><span>我的得分</span><strong>{{ canView ? formatScore(resultScore) : '待公布' }}</strong></div><div v-else><span>{{ isExam ? '考试时长' : '作业截止' }}</span><strong class="small-value">{{ isExam ? task.timeLimit+' 分钟' : formatDate(task.deadline) }}</strong></div></div>
      <el-alert v-if="submitted && !canView" title="已提交，成绩与答案待教师公布后可见" type="info" :closable="false" />
      <el-alert v-else-if="expired && !submitted" title="已超过作答截止时间，本次暂存内容仍保留在当前浏览器" type="warning" :closable="false" />
      <StudyQuestionPaper v-model="answers" :questions="items" :readonly="submitted || expired" :review-items="submitted && canView ? reviewItems : []" :source="kind.toUpperCase()" :source-id="taskId" :answers-visible="submitted && canView" />
      <footer v-if="!submitted" class="assessment-footer"><span>{{ draftSaved ? '作答已在当前浏览器暂存' : '请认真阅读题目并完成作答' }}</span><el-button type="primary" :disabled="expired" :loading="submitting" @click="submit">提交{{ label }}</el-button></footer>
    </template>
    <el-empty v-else-if="!loading" description="暂时无法读取本次作答，请返回任务列表刷新" />
  </div>
</template>
<script setup>
import {computed,onBeforeUnmount,ref,watch} from 'vue'
import {useRoute,useRouter,onBeforeRouteLeave} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import {useUserStore} from '@/stores/user'
import {fetchExamDetail,startExam,submitExam,reviewExam,fetchStudentExamRecords} from '@/api/exam'
import {fetchHomeworkDetail,submitHomework,reviewHomework} from '@/api/homework'
import {savedStudyAnswers} from '@/api/courseWorkspace'
import StudyQuestionPaper from '@/components/StudyQuestionPaper.vue'
const route=useRoute(),router=useRouter(),user=useUserStore(),kind=computed(()=>route.params.kind),taskId=computed(()=>route.params.taskId),isExam=computed(()=>kind.value==='exam'),label=computed(()=>isExam.value?'考试':'作业')
const task=ref(null),items=ref([]),answers=ref({}),reviewItems=ref([]),resultScore=ref(null),submitted=ref(false),loading=ref(false),submitting=ref(false),deadline=ref(null),now=ref(Date.now()),draftSaved=ref(false)
const canView=computed(()=>task.value&&(Number(task.value.hideResultUntilPublish)!==1||Number(task.value.isResultPublished)===1)),expired=computed(()=>!submitted.value&&deadline.value&&new Date(deadline.value).getTime()<=now.value)
const totalScore=computed(()=>items.value.reduce((n,i)=>n+Number(i.score||0),0)),countdown=computed(()=>{const seconds=Math.max(0,Math.ceil((new Date(deadline.value).getTime()-now.value)/1000));return `${Math.floor(seconds/60)}:${String(seconds%60).padStart(2,'0')}`})
const draftKey=computed(()=>`study-draft:${user.userId}:${kind.value}:${taskId.value}`),formatScore=v=>v==null?'-':Number(v).toFixed(1),formatDate=v=>String(v||'').replace('T',' ').slice(0,16)
let timer,disposed=false,generation=0,hydrating=false
function setAnswers(rows){hydrating=true;answers.value=Object.fromEntries((rows||[]).map(a=>{const q=a.question||a;return [`${q.questionType}-${q.questionId}`,a.studentAnswer||'']}));hydrating=false}
function applyReview(data){reviewItems.value=data.answers||[];items.value=reviewItems.value.map(a=>({question:a.question,score:a.fullScore}));setAnswers(reviewItems.value);resultScore.value=data.teacherScore??data.examScore??data.homeworkScore}
async function load(){
  const token=++generation;loading.value=true
  if(!['exam','homework'].includes(kind.value)){router.replace('/student/tasks');return}
  try{
    const [detail,saved]=await Promise.all([isExam.value?fetchExamDetail(taskId.value):fetchHomeworkDetail(taskId.value),savedStudyAnswers(kind.value,taskId.value)])
    if(disposed||token!==generation)return
    task.value=detail.data[isExam.value?'exam':'homework'];items.value=detail.data.questions||[];submitted.value=!!saved.data?.length
    if(submitted.value){setAnswers(saved.data);if(canView.value)applyReview((await(isExam.value?reviewExam(taskId.value):reviewHomework(taskId.value))).data)}
    else{
      try{const local=JSON.parse(localStorage.getItem(draftKey.value)||'{}');hydrating=true;answers.value=local;hydrating=false;draftSaved.value=Object.keys(local).length>0}catch{}
      if(isExam.value){
        try{const {data}=await startExam(taskId.value);deadline.value=data.deadline}catch{
          const records=(await fetchStudentExamRecords(user.userId)).data||[];const record=records.find(r=>String(r.examId)===String(taskId.value));if(record?.startTime)deadline.value=new Date(new Date(record.startTime).getTime()+Number(task.value.timeLimit)*60000).toISOString()
        }
      }else deadline.value=task.value.deadline
    }
  }catch{}finally{loading.value=false}
}
watch(answers,value=>{if(hydrating||submitted.value)return;try{localStorage.setItem(draftKey.value,JSON.stringify(value));draftSaved.value=true}catch{}},{deep:true,flush:'sync'})
async function submit(){
  if(submitted.value||expired.value||submitting.value)return
  const values=items.value.map(i=>({questionId:i.question.questionId,questionType:i.question.questionType,studentAnswer:answers.value[`${i.question.questionType}-${i.question.questionId}`]||''})),blank=values.filter(v=>!v.studentAnswer.trim()).length
  try{await ElMessageBox.confirm(blank?`还有 ${blank} 道题未作答，确认提交？`:`确认提交${label.value}？提交后不可修改`, '提交确认',{type:'warning'})}catch{return}
  submitting.value=true
  try{const body={answers:values,[isExam.value?'examId':'homeworkId']:Number(taskId.value)},response=await(isExam.value?submitExam(body):submitHomework(body));submitted.value=true;setAnswers(response.data?.answers||[]);if(canView.value)applyReview(response.data);localStorage.removeItem(draftKey.value);ElMessage.success(`${label.value}已提交`)}catch{}finally{submitting.value=false}
}
onBeforeRouteLeave(()=>!submitting.value)
watch(()=>`${kind.value}:${taskId.value}`,()=>{task.value=null;items.value=[];hydrating=true;answers.value={};hydrating=false;reviewItems.value=[];submitted.value=false;deadline.value=null;draftSaved.value=false;load()},{immediate:true})
let poll=0;timer=setInterval(()=>{now.value=Date.now();if(++poll%10===0&&submitted.value&&!canView.value)load()},1000)
onBeforeUnmount(()=>{disposed=true;generation++;clearInterval(timer)})
</script>
<style scoped>
.assessment-page { padding:28px 32px;background:white;border:1px solid #edf1f6;border-radius:20px; }.assessment-page > .el-button { margin-bottom:24px; }.assessment-header { display:flex;justify-content:space-between;gap:20px;align-items:center;margin-bottom:26px; }.eyebrow { color:#74afc7;font-size:11px;letter-spacing:.15em; }h1 { font-size:28px;margin:12px 0; }.assessment-header p { color:#8a97a8;font-size:13px; }.assessment-facts { display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px;background:#f7fafc;border:1px solid #edf2f7;border-radius:15px;padding:22px;margin-bottom:24px; }.assessment-facts span { display:block;font-size:12px;color:#99a5b4;margin-bottom:12px; }.assessment-facts strong { font-size:27px;color:#34516e; }.assessment-facts small { font-size:13px;font-weight:400; }.assessment-facts .small-value { font-size:18px; }.assessment-footer { display:flex;justify-content:space-between;align-items:center;gap:20px;position:sticky;bottom:0;padding:20px 0;border-top:1px solid #edf1f6;background:white; }.assessment-footer span { color:#97a2b1;font-size:12px; }.el-alert { margin-bottom:20px; }@media(max-width:750px){.assessment-page { padding:20px; }.assessment-facts { gap:10px;padding:16px; }.assessment-facts .small-value { font-size:14px; }}
</style>

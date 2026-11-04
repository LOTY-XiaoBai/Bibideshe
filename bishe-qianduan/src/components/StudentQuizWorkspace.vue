<template>
  <section v-loading="loading" class="student-quiz">
    <template v-if="quiz">
      <div class="quiz-heading"><div><h3>{{ quiz.name }}</h3><p>{{ quiz.questions?.length }} 道题 · 满分 {{ quiz.maxScore }} 分 <span v-if="Number(quiz.countScore)">· 计入课程总分</span></p></div><el-tag :type="quiz.endedAt ? 'info' : 'success'">{{ quiz.endedAt ? '已结束' : quiz.deadline ? `剩余 ${remainingLabel}` : '教师手动结束' }}</el-tag></div>
      <el-alert v-if="!canEdit && !quiz.answersVisible" title="已提交，待小测时间结束或教师手动结束后公布答案" type="info" :closable="false" />
      <div v-if="!canEdit" class="score-summary"><span>我的得分</span><strong>{{ quiz.student?.score == null ? '待公布' : quiz.student.score }}</strong><span v-if="quiz.answersVisible && !Number(quiz.student?.graded)">含待教师批阅的解答题</span><span v-if="quiz.student?.status === 'ABSENT'">未提交</span></div>
      <el-tabs v-model="tab"><el-tab-pane :label="canEdit ? '小测作答' : '题目与复盘'" name="paper" /><el-tab-pane v-if="rankingAvailable" label="小测排名" name="ranking" /></el-tabs>
      <template v-if="tab === 'paper'">
        <StudyQuestionPaper :questions="quiz.questions || []" v-model="answers" :readonly="!canEdit" source="QUIZ" :source-id="quizId" :answers-visible="!!quiz.answersVisible" :review-items="!canEdit && quiz.answersVisible ? quiz.answers || [] : []" />
        <div v-if="canEdit" class="submit-row"><span :class="{ warning: saveState === '保存失败，将重试' }">{{ saveState }}</span><el-button type="primary" :loading="submitting" @click="submit">提交小测</el-button></div>
      </template>
      <el-table v-else :data="ranking" empty-text="暂无已完成批阅的成绩"><el-table-column prop="rank" label="排名" width="80" /><el-table-column prop="nickname" label="学生" /><el-table-column prop="score" label="得分" width="110" /></el-table>
    </template>
    <el-empty v-else-if="!loading" description="小测暂不可访问，请返回列表刷新" />
  </section>
</template>
<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchQuiz, saveQuizDraft, submitQuiz, fetchQuizScores } from '@/api/quiz'
import { onChatEvent } from '@/utils/websocket'
import StudyQuestionPaper from './StudyQuestionPaper.vue'
const props = defineProps({ quizId: { type: [Number,String], required:true } })
const emit = defineEmits(['submitted'])
const quiz=ref(null), loading=ref(false), answers=ref({}), submitting=ref(false), tab=ref('paper'), ranking=ref([]), saveState=ref('作答后自动保存'), now=ref(Date.now())
const canEdit=computed(()=>!!quiz.value && !quiz.value.endedAt && ['PENDING','DRAFT'].includes(quiz.value.student?.status))
const rankingAvailable=computed(()=>quiz.value && Number(quiz.value.showRanking) && quiz.value.answersVisible && !canEdit.value)
const remainingLabel=computed(()=>{const seconds=Math.max(0,Math.ceil((new Date(quiz.value?.deadline).getTime()-now.value)/1000));return `${Math.floor(seconds/60)}:${String(seconds%60).padStart(2,'0')}`})
let saveTimer, refreshTimer, clockTimer, disposed=false, hydrating=false, sequence=0, saving=Promise.resolve(), loadingRefresh=false
const answerList=()=> (quiz.value?.questions||[]).map(item=>({ questionId:item.question.questionId,questionType:item.question.questionType,studentAnswer:answers.value[`${item.question.questionType}-${item.question.questionId}`]||'' }))
function hydrate(rows){hydrating=true;answers.value=Object.fromEntries((rows||[]).map(a=>[`${a.questionType}-${a.questionId}`,a.studentAnswer]));hydrating=false}
async function load(initial=false){
  if(loadingRefresh)return
  loadingRefresh=true;if(initial)loading.value=true
  const id=String(props.quizId), stamp=sequence
  try{const {data}=await fetchQuiz(props.quizId);if(disposed||stamp!==sequence||String(props.quizId)!==id)return
    // 较早发出的轮询不能把已提交/已结束的界面恢复为作答状态。
    if (quiz.value?.endedAt && !data.endedAt) return
    if (['SUBMITTED','ABSENT'].includes(quiz.value?.student?.status) && ['PENDING','DRAFT'].includes(data.student?.status)) return
    const wasEditing=canEdit.value;quiz.value=data
    if(initial||!canEdit.value)hydrate(data.answers)
    if(wasEditing&&!canEdit.value){clearTimeout(saveTimer);saveState.value='作答已交卷';window.dispatchEvent(new Event('classroom-refresh'));emit('submitted')}
  }catch{}finally{loadingRefresh=false;if(initial)loading.value=false}
}
function persist(){
  if(!canEdit.value)return saving
  const id=props.quizId,payload=answerList();saveState.value='正在保存…'
  saving=saving.catch(()=>{}).then(()=>saveQuizDraft(id,payload)).then(()=>{if(!disposed&&String(id)===String(props.quizId))saveState.value='作答已自动保存'}).catch(error=>{if(!disposed){saveState.value='保存失败，将重试';if([400,403,409].includes(error.response?.status))load()}throw error})
  return saving
}
watch(answers,()=>{if(hydrating||!canEdit.value||submitting.value)return;clearTimeout(saveTimer);saveTimer=setTimeout(()=>persist().catch(()=>{}),500)},{deep:true,flush:'sync'})
async function submit(){
  if(!canEdit.value||submitting.value)return
  const missing=answerList().filter(a=>!a.studentAnswer.trim()).length
  if(missing)try{await ElMessageBox.confirm(`还有 ${missing} 道题未作答，确认提交？`,'提交小测')}catch{return}
  submitting.value=true;clearTimeout(saveTimer)
  try{await saving.catch(()=>{});const {data}=await submitQuiz(props.quizId,answerList());quiz.value=data;hydrate(data.answers);saveState.value='作答已提交';ElMessage.success('小测已提交');window.dispatchEvent(new Event('classroom-refresh'));emit('submitted')}catch{await load()}finally{submitting.value=false}
}
async function loadRanking(){if(!rankingAvailable.value)return;try{ranking.value=(await fetchQuizScores(props.quizId)).data||[]}catch{}}
watch(tab,value=>{if(value==='ranking')loadRanking()})
const off=onChatEvent(event=>{if(event.type==='QUIZ_ENDED'&&String(event.data?.id)===String(props.quizId)){load();loadRanking()}})
watch(()=>props.quizId,()=>{sequence++;quiz.value=null;hydrate([]);tab.value='paper';load(true)},{immediate:true})
refreshTimer=setInterval(()=>{if(saveState.value==='保存失败，将重试'&&canEdit.value)persist().catch(()=>{});load();if(tab.value==='ranking')loadRanking()},5000)
clockTimer=setInterval(()=>{now.value=Date.now();if(canEdit.value&&quiz.value.deadline&&new Date(quiz.value.deadline).getTime()<=now.value)load()},1000)
onBeforeUnmount(()=>{clearTimeout(saveTimer);if(canEdit.value)persist().catch(()=>{});disposed=true;sequence++;clearInterval(refreshTimer);clearInterval(clockTimer);off()})
</script>
<style scoped>
.quiz-heading,.submit-row,.mistake-section article { display:flex;align-items:center;justify-content:space-between;gap:16px; }.quiz-heading { margin-bottom:20px; }h3 { margin:0 0 8px;font-size:20px; }p,.submit-row span { font-size:13px;color:#87909e; }.score-summary { display:flex;align-items:center;gap:14px;padding:18px 0;color:#87909e;font-size:13px; }.score-summary strong { font-size:30px;color:#409eff; }.submit-row { position:sticky;bottom:0;background:#fff;padding:18px 0;border-top:1px solid #eef0f4; }.submit-row .warning { color:#e6a23c; }.mistake-section { margin-top:20px;padding:18px;border-radius:12px;background:#f8fafc; }.mistake-section h4 { margin:0; }.mistake-section article { padding:12px 0;border-top:1px solid #e8edf3;font-size:14px; }.mistake-section article span { flex:1; }.el-alert { margin-bottom:16px; }
</style>

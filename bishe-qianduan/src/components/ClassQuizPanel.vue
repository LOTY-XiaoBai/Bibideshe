<template>
  <section class="quiz-panel"><div class="panel-head"><div><h3>发布课堂小测</h3><p>从预备小测中选择，上课后发布给本班学生</p></div><a href="/teacher/quizzes" target="_blank" rel="noopener">管理预备小测 ↗</a></div>
    <div class="publish-row"><el-select v-model="selectedId" placeholder="选择预备小测" style="flex:1"><el-option v-for="t in templates" :key="t.id" :value="t.id" :label="`${t.name} · ${t.questionCount}题 · ${t.timeLimitMinutes ? t.timeLimitMinutes+'分钟' : '手动结束'}`" /></el-select><el-button text @click="loadTemplates">刷新小测</el-button><el-button type="primary" :disabled="!active || !sessionId || !selectedId || !!openQuiz" :loading="publishing" @click="publish">发布小测</el-button></div>
    <el-alert v-if="openQuiz" :title="`「${openQuiz.name}」正在进行，结束后才能发布下一份小测`" type="info" :closable="false" />
    <el-table :data="runs" empty-text="本课程尚未发布小测"><el-table-column prop="name" label="小测名称" min-width="170" /><el-table-column label="截止时间" min-width="150"><template #default="{ row }">{{ row.deadline ? String(row.deadline).replace('T',' ').slice(0,16) : '手动结束' }}</template></el-table-column><el-table-column label="提交人数" width="100"><template #default="{ row }">{{ row.submittedCount }} / {{ row.studentCount }}</template></el-table-column><el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.endedAt ? 'info' : 'success'">{{ row.endedAt ? '已结束' : '进行中' }}</el-tag></template></el-table-column><el-table-column label="操作" width="190"><template #default="{ row }"><el-button link type="primary" @click="showResults(row.id)">成绩 / 批阅</el-button><el-button v-if="!row.endedAt" link type="danger" @click="finish(row)">结束小测</el-button></template></el-table-column></el-table>
    <TeacherQuizResults v-model="resultsVisible" :quiz-id="resultsId" @changed="loadRuns" />
  </section>
</template>
<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchQuizTemplates, fetchTeacherQuizzes, publishQuiz, endQuiz } from '@/api/quiz'
import TeacherQuizResults from './TeacherQuizResults.vue'
const props = defineProps({ courseId: { type: [String,Number], required:true }, sessionId: String, active: Boolean })
const templates=ref([]), selectedId=ref(null), runs=ref([]), publishing=ref(false), resultsId=ref(null), resultsVisible=ref(false)
const openQuiz=computed(()=>runs.value.find(q=>!q.endedAt))
let timer
async function loadTemplates(){try{templates.value=(await fetchQuizTemplates()).data||[];if(!templates.value.some(t=>t.id===selectedId.value))selectedId.value=templates.value[0]?.id||null}catch{}}
async function loadRuns(){try{runs.value=(await fetchTeacherQuizzes(props.courseId)).data||[]}catch{}}
async function publish(){publishing.value=true;try{await publishQuiz(selectedId.value,props.sessionId);ElMessage.success('小测已发布');await loadRuns()}catch{}finally{publishing.value=false}}
async function finish(q){try{await ElMessageBox.confirm(`结束「${q.name}」后，已保存的作答会自动交卷并公布答案。`,'结束小测');await endQuiz(q.id);await loadRuns();ElMessage.success('小测已结束')}catch{}}
function showResults(id){resultsId.value=id;resultsVisible.value=true}
onMounted(()=>{loadTemplates();loadRuns();timer=setInterval(loadRuns,5000)})
onUnmounted(()=>clearInterval(timer))
</script>
<style scoped>
.quiz-panel { padding:22px; border:1px solid #edf0f4; border-radius:16px; background:white; margin-top:20px; }.panel-head,.publish-row { display:flex;align-items:center;justify-content:space-between;gap:14px; margin-bottom:18px; }h3 { margin:0 0 8px;font-size:17px; }p,a { font-size:13px;color:#87909e; }a { color:#409eff; }.publish-row { flex-wrap:wrap; }.el-alert { margin-bottom:16px; }
</style>

<template>
  <div class="quiz-preparation">
    <header class="page-header"><div><h2>预备小测</h2><p>提前命名、选题并设置规则，上课后即可发布给学生</p></div><el-button type="primary" @click="openEditor()"><i aria-hidden="true" class="ph ph-plus-circle" /> 新建小测</el-button></header>
    <el-skeleton v-if="loading" :rows="4" animated />
    <el-empty v-else-if="!templates.length" description="还没有预备小测，先新建一份吧" />
    <div v-else class="template-grid">
      <article v-for="item in templates" :key="item.id" class="template-card">
        <div class="card-title"><i aria-hidden="true" class="ph ph-exam" /><h3>{{ item.name }}</h3></div>
        <p>{{ item.questionCount }} 道题 · 满分 {{ item.maxScore }} 分 · {{ item.timeLimitMinutes ? `${item.timeLimitMinutes} 分钟` : '手动结束' }}</p>
        <div class="tags"><el-tag v-if="Number(item.hideAnswers)" type="warning" size="small">结束后公布答案</el-tag><el-tag v-if="Number(item.countScore)" size="small">计入总分</el-tag><el-tag v-if="Number(item.showRanking)" type="success" size="small">显示排名</el-tag></div>
        <div class="card-actions"><el-button size="small" @click="openEditor(item.id)">编辑小测</el-button><el-button size="small" type="danger" plain @click="remove(item)">删除</el-button></div>
      </article>
    </div>
    <section class="history-card"><div class="section-head"><h3>已发布小测</h3><el-button text @click="loadRuns">刷新</el-button></div><el-table :data="runs" empty-text="在课堂中发布小测后，可在这里查看成绩和批阅">
      <el-table-column prop="name" label="小测名称" min-width="160" /><el-table-column prop="courseName" label="课程" min-width="130" /><el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.endedAt ? 'info' : 'success'">{{ row.endedAt ? '已结束' : '进行中' }}</el-tag></template></el-table-column><el-table-column label="提交人数" width="100"><template #default="{ row }">{{ row.submittedCount }} / {{ row.studentCount }}</template></el-table-column><el-table-column label="操作" width="180"><template #default="{ row }"><el-button link type="primary" @click="showResults(row.id)">成绩 / 批阅</el-button><el-button v-if="!row.endedAt" link type="danger" @click="finish(row)">结束小测</el-button></template></el-table-column>
    </el-table></section>

    <el-dialog v-model="editorVisible" :title="editingId ? '编辑预备小测' : '新建预备小测'" width="min(1120px, 96vw)" destroy-on-close :close-on-click-modal="false">
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="小测名称"><el-input v-model="form.name" maxlength="100" show-word-limit placeholder="例如：第一章课堂巩固" /></el-form-item>
        <div class="settings-grid"><div><el-checkbox v-model="fixedTime">设置固定时间</el-checkbox><div v-if="fixedTime" class="duration"><el-input-number v-model="form.timeLimitMinutes" :min="1" :max="180" :precision="0" /> 分钟</div></div><div><el-checkbox v-model="form.hideAnswers">学生做完后不公布答案</el-checkbox><p>到时或手动结束小测后公布</p></div><div><el-checkbox v-model="form.countScore">本次成绩计入总分</el-checkbox><p>并入课程的考试考核部分</p></div><div><el-checkbox v-model="form.showRanking">向学生显示排名</el-checkbox><p>不勾选时学生仅能查看自己的成绩</p></div></div>
        <div class="question-columns">
          <section class="question-bank"><div class="section-head"><h3>从题库选题</h3><el-radio-group v-model="bankScope" size="small"><el-radio-button value="mine">我的题库</el-radio-button><el-radio-button value="public">公共题库</el-radio-button></el-radio-group></div><div class="bank-filters"><el-input v-model="keyword" placeholder="搜索题目内容" clearable /><el-select v-model="typeFilter"><el-option label="全部题型" :value="0" /><el-option v-for="type in [1, 2, 3]" :key="type" :value="type" :label="questionTypeLabel(type)" /></el-select></div>
            <div v-loading="bankLoading" class="bank-list"><article v-for="q in pageQuestions" :key="key(q)" class="bank-question"><div><el-tag size="small" type="info">{{ questionTypeLabel(q.questionType) }}</el-tag><p>{{ q.content }}</p></div><el-button size="small" :disabled="selected.some(s => key(s.question) === key(q)) || selected.length >= 20" @click="selected.push({ question: q, score: 5 })">{{ selected.some(s => key(s.question) === key(q)) ? '已添加' : '添加' }}</el-button></article><el-empty v-if="!filteredQuestions.length" :image-size="60" description="当前没有可选题目" /></div>
            <el-pagination v-model:current-page="bankPage" :page-size="10" :total="filteredQuestions.length" layout="total, prev, pager, next" small />
          </section>
          <section class="selection"><div class="section-head"><h3>已选 {{ selected.length }} / 20 题</h3><span>满分 {{ maxScore }} 分</span></div><p class="hint">最少 1 题，最多 20 题；可分别设置分值。</p><div class="selected-list"><article v-for="(item, i) in selected" :key="key(item.question)" class="selected-question"><p>{{ i + 1 }}. {{ item.question.content }}</p><div><el-input-number v-model="item.score" :min="0.01" :max="100" :precision="2" :step="1" size="small" /><span>分</span><el-button link type="danger" @click="selected.splice(i, 1)">移除</el-button></div></article><el-empty v-if="!selected.length" :image-size="60" description="从左侧添加题目" /></div></section>
        </div>
      </el-form>
      <template #footer><el-button @click="editorVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存小测</el-button></template>
    </el-dialog>
    <TeacherQuizResults v-model="resultsVisible" :quiz-id="resultsId" @changed="loadRuns" />
  </div>
</template>
<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchMyQuestions, fetchPublicQuestions } from '@/api/question'
import { fetchQuizTemplates, fetchQuizTemplate, saveQuizTemplate, deleteQuizTemplate, fetchTeacherQuizzes, endQuiz } from '@/api/quiz'
import { questionTypeLabel } from '@/utils/question'
import TeacherQuizResults from '@/components/TeacherQuizResults.vue'
const templates = ref([]), runs = ref([]), loading = ref(false), editorVisible = ref(false), editingId = ref(null), saving = ref(false), fixedTime = ref(false), selected = ref([])
const form = reactive({ name: '', timeLimitMinutes: 10, hideAnswers: false, countScore: false, showRanking: false })
const mine = ref([]), publicBank = ref([]), bankScope = ref('mine'), keyword = ref(''), typeFilter = ref(0), bankPage = ref(1), bankLoading = ref(false)
const resultsId = ref(null), resultsVisible = ref(false)
const key = q => `${q.questionType}-${q.questionId}`
const maxScore = computed(() => selected.value.reduce((n, item) => n + Number(item.score || 0), 0).toFixed(2))
const filteredQuestions = computed(() => (bankScope.value === 'mine' ? mine.value : publicBank.value).filter(q => (!typeFilter.value || Number(q.questionType) === typeFilter.value) && q.content.toLowerCase().includes(keyword.value.trim().toLowerCase())))
const pageQuestions = computed(() => filteredQuestions.value.slice((bankPage.value - 1) * 10, bankPage.value * 10))
watch([bankScope, keyword, typeFilter], () => { bankPage.value = 1 })
async function loadTemplates() { loading.value = true; try { templates.value = (await fetchQuizTemplates()).data || [] } catch {} finally { loading.value = false } }
async function loadRuns() { try { runs.value = (await fetchTeacherQuizzes()).data || [] } catch {} }
async function openEditor(id = null) {
  editingId.value = id; selected.value = []; fixedTime.value = false; keyword.value = ''; bankPage.value = 1
  Object.assign(form, { name: '', timeLimitMinutes: 10, hideAnswers: false, countScore: false, showRanking: false })
  if (id) {
    try { const { data } = await fetchQuizTemplate(id); Object.assign(form, { name: data.name, timeLimitMinutes: data.timeLimitMinutes || 10, hideAnswers: !!Number(data.hideAnswers), countScore: !!Number(data.countScore), showRanking: !!Number(data.showRanking) }); fixedTime.value = !!data.timeLimitMinutes; selected.value = data.questions || [] } catch { return }
  }
  editorVisible.value = true; bankLoading.value = true
  try { const [a, b] = await Promise.all([fetchMyQuestions(), fetchPublicQuestions()]); mine.value = a.data || []; publicBank.value = b.data || [] } catch {} finally { bankLoading.value = false }
}
async function save() {
  if (!form.name.trim() || selected.value.length < 1 || selected.value.length > 20) { ElMessage.warning('请填写名称，并选择1至20道题'); return }
  saving.value = true
  try { await saveQuizTemplate(editingId.value, { ...form, name: form.name.trim(), timeLimitMinutes: fixedTime.value ? form.timeLimitMinutes : null, questions: selected.value.map(item => ({ questionId: item.question.questionId, questionType: item.question.questionType, score: item.score })) }); ElMessage.success('预备小测已保存'); editorVisible.value = false; await loadTemplates() } catch {} finally { saving.value = false }
}
async function remove(item) { try { await ElMessageBox.confirm(`删除「${item.name}」？已发布的小测记录会保留。`, '删除预备小测'); await deleteQuizTemplate(item.id); await loadTemplates(); ElMessage.success('已删除预备小测') } catch {} }
function showResults(id) { resultsId.value = id; resultsVisible.value = true }
async function finish(item) { try { await ElMessageBox.confirm(`确认结束「${item.name}」并公布答案？`, '结束小测'); await endQuiz(item.id); await loadRuns(); ElMessage.success('小测已结束') } catch {} }
onMounted(() => { loadTemplates(); loadRuns() })
</script>
<style scoped>
.page-header,.section-head,.card-title,.card-actions,.selected-question > div,.bank-question { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.page-header { margin-bottom: 24px; align-items: flex-start; }h2 { margin: 0 0 8px; font-size: 24px; }h3 { margin: 0; font-size: 17px; }p { color: #7c8796; font-size: 13px; line-height: 1.7; }.template-grid { display: grid; grid-template-columns: repeat(auto-fill,minmax(290px,1fr)); gap: 18px; }.template-card,.history-card { border: 1px solid #edf0f4; border-radius: 16px; background: white; padding: 22px; }.card-title { justify-content: flex-start; }.card-title i { font-size: 26px; color: #409eff; }.tags { display: flex; gap: 8px; flex-wrap: wrap; min-height: 24px; }.card-actions { justify-content: flex-end; margin-top: 20px; }.history-card { margin-top: 24px; }.section-head { margin-bottom: 14px; }.settings-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 16px; padding: 18px; background: #f8fafc; border-radius: 12px; margin-bottom: 24px; }.settings-grid p { margin: 0; font-size: 12px; }.duration { display: flex; gap: 8px; align-items: center; margin-top: 8px; }.question-columns { display: grid; grid-template-columns: 1fr 1fr; gap: 22px; }.question-bank,.selection { min-width: 0; }.bank-filters { display: flex; gap: 10px; }.bank-filters .el-select { width: 130px; flex-shrink: 0; }.bank-list,.selected-list { height: 350px; overflow: auto; margin: 12px 0; }.bank-question,.selected-question { padding: 12px; border: 1px solid #edf0f4; border-radius: 10px; margin-bottom: 10px; }.bank-question p,.selected-question p { margin: 6px 0; color: #334155; white-space: pre-wrap; }.selected-question > div { justify-content: flex-end; }.hint,.section-head > span { font-size: 12px; color: #87909e; }
@media(max-width:800px){.question-columns,.settings-grid { grid-template-columns:1fr; }.page-header { flex-wrap: wrap; }}
</style>

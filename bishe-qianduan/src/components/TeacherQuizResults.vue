<template>
  <el-dialog :model-value="modelValue" title="小测成绩与批阅" width="min(900px, 96vw)" append-to-body @update:model-value="$emit('update:modelValue', $event)">
    <div class="result-head"><p>解答题批阅后更新总分与排名；未提交、待批阅的学生暂不排名。</p><el-button text @click="load">刷新</el-button></div>
    <el-table v-loading="loading" :data="rows" empty-text="暂无成绩记录"><el-table-column prop="rank" label="排名" width="70" /><el-table-column prop="nickname" label="学生" /><el-table-column prop="studentId" label="学生 ID" min-width="110" /><el-table-column label="状态" min-width="120"><template #default="{ row }">{{ row.status === 'SUBMITTED' ? Number(row.graded) ? '已批阅' : '待批阅' : row.status === 'ABSENT' ? '未提交' : '作答中' }}</template></el-table-column><el-table-column prop="score" label="得分" width="90" /><el-table-column label="操作" width="110"><template #default="{ row }"><el-button v-if="['SUBMITTED','ABSENT'].includes(row.status)" link type="primary" @click="openReview(row)">查看 / 批阅</el-button></template></el-table-column></el-table>
    <el-dialog v-model="reviewVisible" :title="`${current?.nickname || ''} · 小测答卷`" width="min(850px, 96vw)" append-to-body destroy-on-close>
      <QuestionPaper :questions="review.questions || []" :model-value="answerMap" readonly :review-items="review.answers || []" />
      <section v-for="a in solveAnswers" :key="a.questionId" class="grade-row"><h4>{{ a.question.content }}</h4><p>参考答案：{{ a.question.answer }}</p><el-form inline><el-form-item label="得分"><el-input-number v-model="grades[a.questionId].score" :min="0" :max="Number(a.fullScore)" :precision="2" /></el-form-item><el-form-item label="批注"><el-input v-model="grades[a.questionId].annotation" maxlength="500" /></el-form-item><el-form-item><el-button type="primary" :loading="grading" @click="saveGrade(a)">保存评分</el-button></el-form-item></el-form></section>
    </el-dialog>
  </el-dialog>
</template>
<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchQuizScores, fetchQuizStudent, gradeQuiz } from '@/api/quiz'
import QuestionPaper from './QuestionPaper.vue'
const props = defineProps({ modelValue: Boolean, quizId: [String, Number] })
const emit = defineEmits(['update:modelValue', 'changed'])
const rows = ref([]), loading = ref(false), reviewVisible = ref(false), review = ref({}), current = ref(null), grading = ref(false), grades = reactive({})
const answerMap = computed(() => Object.fromEntries((review.value.answers || []).map(a => [`${a.questionType}-${a.questionId}`, a.studentAnswer])))
const solveAnswers = computed(() => current.value?.status === 'SUBMITTED' ? (review.value.answers || []).filter(a => Number(a.questionType) === 3) : [])
async function load() { if (!props.quizId) return; loading.value = true; try { rows.value = (await fetchQuizScores(props.quizId)).data || [] } catch {} finally { loading.value = false } }
async function openReview(row) {
  try { review.value = (await fetchQuizStudent(props.quizId, row.studentId)).data; current.value = row; Object.keys(grades).forEach(k => delete grades[k]); for (const a of review.value.answers || []) grades[a.questionId] = { score: a.score === null ? 0 : Number(a.score), annotation: a.annotation === '待教师批改' ? '' : a.annotation }; reviewVisible.value = true } catch {}
}
async function saveGrade(a) { grading.value = true; try { await gradeQuiz(props.quizId, { studentId: current.value.studentId, questionId: a.questionId, ...grades[a.questionId] }); ElMessage.success('评分已保存'); await load(); await openReview(rows.value.find(r => r.studentId === current.value.studentId) || current.value); emit('changed') } catch {} finally { grading.value = false } }
watch([() => props.modelValue, () => props.quizId], ([visible]) => { if (visible) { rows.value = []; reviewVisible.value = false; load() } })
</script>
<style scoped>
.result-head { display:flex; align-items:center; justify-content:space-between; gap:12px; }.result-head p,.grade-row p { color:#7c8796;font-size:13px; }.grade-row { padding:18px; border-radius:12px; background:#f8fafc; margin-top:16px; }.grade-row h4 { margin:0 0 10px; }
</style>

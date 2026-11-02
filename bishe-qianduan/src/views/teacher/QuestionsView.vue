<template>
  <div class="page">
    <header class="page__header">
      <div>
        <h2>题库管理</h2>
        <p>上传选择题 / 判断题 / 解答题；公开题目需管理员审核后进入公共题库</p>
      </div>
      <el-button type="primary" class="create-btn" @click="openCreate">
        <i class="ph ph-plus-circle" />
        新建题目
      </el-button>
    </header>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="我的题库" name="mine" />
      <el-tab-pane label="公共题库" name="public" />
    </el-tabs>

    <div class="filter-row">
      <el-radio-group v-model="typeFilter" size="small">
        <el-radio-button :value="0">全部</el-radio-button>
        <el-radio-button :value="1">选择题</el-radio-button>
        <el-radio-button :value="2">判断题</el-radio-button>
        <el-radio-button :value="3">解答题</el-radio-button>
      </el-radio-group>
      <el-button text :loading="loading" @click="loadList">刷新</el-button>
    </div>

    <el-skeleton v-if="loading && !filteredList.length" :rows="5" animated />

    <div v-else-if="!filteredList.length" class="empty">
      {{ activeTab === 'mine' ? '还没有上传题目，点击「新建题目」开始出题' : '暂无公开题库题目' }}
    </div>

    <div v-else class="list">
      <article v-for="q in filteredList" :key="`${q.questionType}-${q.questionId}`" class="q-card">
        <div class="q-card__head">
          <div class="tags">
            <el-tag size="small">{{ questionTypeLabel(q.questionType) }}</el-tag>
            <el-tag
              v-if="activeTab === 'mine'"
              size="small"
              :type="q.isPublic === 1 ? (q.auditStatus === 1 ? 'success' : 'warning') : 'info'"
              effect="plain"
            >
              {{ auditStatusLabel(q.auditStatus, q.isPublic) }}
            </el-tag>
            <span class="qid">ID: {{ q.questionId }}</span>
          </div>
        </div>
        <h3>{{ q.content }}</h3>

        <div v-if="Number(q.questionType) === 1 && q.options?.length" class="options">
          <p
            v-for="opt in q.options"
            :key="opt.optionCode"
            :class="{ 'is-answer': isCorrectOption(q, opt) }"
          >
            {{ opt.optionCode }}. {{ opt.optionContent }}
          </p>
        </div>

        <div v-if="q.answer" class="answer">
          <span>答案</span>{{ q.answer }}
        </div>
      </article>
    </div>

    <el-dialog
      v-model="createVisible"
      title="新建题目"
      width="560px"
      align-center
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item label="题型">
          <el-radio-group v-model="form.questionType" @change="onTypeChange">
            <el-radio :value="1">选择题</el-radio>
            <el-radio :value="2">判断题</el-radio>
            <el-radio :value="3">解答题</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="题目内容">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="4"
            placeholder="请输入题干"
          />
        </el-form-item>

        <template v-if="form.questionType === 1">
          <el-form-item
            v-for="(opt, idx) in form.options"
            :key="idx"
            :label="`选项 ${optionCodes[idx]}`"
          >
            <div class="option-row">
              <el-input v-model="form.options[idx]" :placeholder="`选项 ${optionCodes[idx]} 内容`" />
              <el-button
                v-if="form.options.length > 2"
                type="danger"
                link
                @click="form.options.splice(idx, 1)"
              >
                删除
              </el-button>
            </div>
          </el-form-item>
          <el-button
            v-if="form.options.length < 6"
            text
            type="primary"
            @click="form.options.push('')"
          >
            + 添加选项
          </el-button>
          <el-form-item label="正确答案（填写选项字母，如 A 或 A,B）">
            <el-input v-model="form.answer" placeholder="A" />
          </el-form-item>
        </template>

        <el-form-item v-else-if="form.questionType === 2" label="正确答案">
          <el-radio-group v-model="form.answer">
            <el-radio value="正确">正确</el-radio>
            <el-radio value="错误">错误</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-else label="参考答案">
          <el-input
            v-model="form.answer"
            type="textarea"
            :rows="3"
            placeholder="填写参考答案，供批改对照"
          />
        </el-form-item>

        <el-form-item label="是否申请公开">
          <el-switch
            v-model="form.isPublic"
            :active-value="1"
            :inactive-value="0"
            active-text="申请公开"
            inactive-text="仅自己可见"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">上传题库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createQuestion, fetchMyQuestions, fetchPublicQuestions } from '@/api/question'
import { questionTypeLabel, auditStatusLabel } from '@/utils/question'

const optionCodes = ['A', 'B', 'C', 'D', 'E', 'F']

const activeTab = ref('mine')
const typeFilter = ref(0)
const loading = ref(false)
const creating = ref(false)
const createVisible = ref(false)
const list = ref([])

const form = reactive({
  questionType: 1,
  content: '',
  answer: 'A',
  isPublic: 0,
  options: ['', '', '', ''],
})

const filteredList = computed(() => {
  if (!typeFilter.value) return list.value
  return list.value.filter((q) => Number(q.questionType) === typeFilter.value)
})

function isCorrectOption(question, opt) {
  if (!question?.answer) return false
  const answer = String(question.answer).trim().toUpperCase()
  return answer.includes(String(opt.optionCode).toUpperCase())
}

function resetForm() {
  form.questionType = 1
  form.content = ''
  form.answer = 'A'
  form.isPublic = 0
  form.options = ['', '', '', '']
}

function onTypeChange(type) {
  if (type === 1) {
    form.answer = 'A'
    if (!form.options.length) form.options = ['', '', '', '']
  } else if (type === 2) {
    form.answer = '正确'
  } else {
    form.answer = ''
  }
}

function openCreate() {
  resetForm()
  createVisible.value = true
}

async function loadList() {
  loading.value = true
  try {
    const res =
      activeTab.value === 'mine'
        ? await fetchMyQuestions()
        : await fetchPublicQuestions()
    list.value = res.data || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  loadList()
}

async function handleCreate() {
  if (!form.content.trim()) {
    ElMessage.warning('请填写题目内容')
    return
  }
  if (!form.answer || !String(form.answer).trim()) {
    ElMessage.warning('请填写答案')
    return
  }

  const payload = {
    questionType: form.questionType,
    content: form.content.trim(),
    answer: String(form.answer).trim(),
    isPublic: form.isPublic,
  }

  if (form.questionType === 1) {
    const options = form.options.map((o) => o.trim()).filter(Boolean)
    if (options.length < 2) {
      ElMessage.warning('选择题至少需要 2 个选项')
      return
    }
    payload.options = options
  }

  creating.value = true
  try {
    await createQuestion(payload)
    ElMessage.success('题目已上传到题库')
    createVisible.value = false
    activeTab.value = 'mine'
    await loadList()
  } catch {
    // handled
  } finally {
    creating.value = false
  }
}

onMounted(loadList)
</script>

<style scoped>
.page__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 8px;
}

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

.create-btn {
  background: linear-gradient(135deg, #66ccff, #ee0000) !important;
  border: none;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.filter-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.empty {
  padding: 48px;
  text-align: center;
  color: #9ca3af;
  background: #fff;
  border-radius: 16px;
  border: 1px dashed #e5e7eb;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.q-card {
  padding: 18px 20px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid #f3f4f6;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.04);
}

.q-card__head {
  margin-bottom: 10px;
}

.tags {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.qid {
  color: #9ca3af;
  font-size: 12px;
}

.q-card h3 {
  margin: 0 0 12px;
  font-size: 15px;
  line-height: 1.6;
}

.options {
  padding: 12px 14px;
  border-radius: 12px;
  background: #f9fafb;
  margin-bottom: 10px;
}

.options p {
  margin: 0 0 6px;
  font-size: 13px;
  color: #4b5563;
}

.options p:last-child {
  margin-bottom: 0;
}

.options .is-answer {
  color: #b91c1c;
  font-weight: 700;
}

.answer {
  font-size: 13px;
  color: #1f2937;
}

.answer span {
  color: #9ca3af;
  margin-right: 8px;
}

.option-row {
  display: flex;
  gap: 8px;
  width: 100%;
  align-items: center;
}
</style>

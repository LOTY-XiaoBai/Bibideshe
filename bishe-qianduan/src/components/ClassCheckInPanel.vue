<template>
  <section class="checkin-panel">
    <div class="panel-head"><h3>课堂签到</h3><el-button text @click="load">刷新</el-button></div>
    <el-form inline @submit.prevent>
      <el-form-item label="签到方式">
        <el-radio-group v-model="mode">
          <el-radio-button value="CODE">签到码</el-radio-button>
          <el-radio-button value="GESTURE">手势连线</el-radio-button>
          <el-radio-button value="LOCATION">定位签到</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="签到时长"><el-input-number v-model="minutes" :min="1" :max="120" :precision="0" /> 分钟</el-form-item>
      <el-form-item label="签到分值"><el-input-number v-model="score" :min="0.5" :max="100" :step="0.5" /></el-form-item>
      <el-form-item><el-button type="primary" :loading="publishing" :disabled="!canPublish" @click="publish">发布签到</el-button></el-form-item>
    </el-form>
    <div v-if="mode === 'GESTURE'" class="gesture-setup">
      <p>按顺序连接 4～9 个点，学生需要绘制相同的手势。</p>
      <CheckInGesture v-model="gesture" />
    </div>
    <CheckInLocationMap v-if="mode === 'LOCATION' && !displaying" v-model="bounds" :course-id="courseId" />
    <el-table :data="records" empty-text="暂无签到记录">
      <el-table-column prop="checkInId" label="签到号" width="90" />
      <el-table-column label="截止时间" min-width="150"><template #default="{ row }">{{ formatTime(row.deadline) }}</template></el-table-column>
      <el-table-column prop="attendeeCount" label="已签人数" width="100" />
      <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="ended(row) ? 'info' : 'success'">{{ ended(row) ? '已结束' : '进行中' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" min-width="220"><template #default="{ row }"><el-button v-if="row.mode && row.mode !== 'LEGACY'" link type="primary" @click="emit('display', row.checkInId)">打开大屏</el-button><el-button link @click="details(row)">签到详情</el-button><el-button v-if="!ended(row)" link type="danger" @click="end(row)">结束签到</el-button></template></el-table-column>
    </el-table>
    <el-pagination v-if="total > 20" v-model:current-page="page" :page-size="20" :total="total" layout="prev, pager, next" @current-change="load" />
    <el-dialog v-model="visible" title="签到详情" width="min(680px, 94vw)" append-to-body>
      <el-switch v-model="absentOnly" active-text="仅看未签到" />
      <el-table :data="absentOnly ? rows.filter(r => Number(r.studentStatus) === 0) : rows" max-height="400">
        <el-table-column prop="studentNickname" label="昵称" />
        <el-table-column prop="studentId" label="学生 ID" />
        <el-table-column label="状态"><template #default="{ row }"><el-select v-if="selected && ended(selected)" :model-value="row.studentStatus" @change="v => update(row, v)"><el-option :value="0" label="缺勤" /><el-option :value="1" label="已签到" /></el-select><span v-else>{{ ['未签到', '已签到', '迟到'][row.studentStatus] }}</span></template></el-table-column>
      </el-table>
    </el-dialog>
  </section>
</template>
<script setup>
import { computed, ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { publishCheckIn, endCheckIn, fetchCourseCheckIns, fetchCheckInDetail, updateCheckInStatus } from '@/api/checkIn'
import CheckInGesture from '@/components/CheckInGesture.vue'
import CheckInLocationMap from '@/components/CheckInLocationMap.vue'
const props = defineProps({ courseId: { type: [String, Number], required: true }, active: Boolean, displaying: Boolean })
const emit = defineEmits(['display'])
const mode = ref('')
const gesture = ref([])
const bounds = ref(null)
const canPublish = computed(() => props.active && mode.value
  && (mode.value !== 'GESTURE' || gesture.value.length >= 4)
  && (mode.value !== 'LOCATION' || bounds.value))
const records = ref([]), minutes = ref(5), score = ref(1), publishing = ref(false), visible = ref(false), rows = ref([]), selected = ref(null), absentOnly = ref(false), page = ref(1), total = ref(0)
const formatTime = t => String(t || '').replace('T', ' ').slice(0, 16)
const ended = row => Number(row.status) === 1 || new Date(row.deadline).getTime() <= Date.now()
async function load() { try { const { data } = await fetchCourseCheckIns(props.courseId, page.value, 20); records.value = data?.records || []; total.value = data?.total || 0 } catch {} }
async function publish() {
  publishing.value = true
  try {
    const d = new Date(Date.now() + minutes.value * 60000)
    const local = new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 19)
    const { data } = await publishCheckIn({ courseId: props.courseId, deadline: local, scoreValue: score.value,
      mode: mode.value, gesture: mode.value === 'GESTURE' ? gesture.value : undefined,
      bounds: mode.value === 'LOCATION' ? bounds.value : undefined })
    gesture.value = []
    bounds.value = null
    ElMessage.success('签到已发布')
    emit('display', data.checkInId)
  } catch {} finally { publishing.value = false }
}
async function details(row) { try { rows.value = (await fetchCheckInDetail(row.checkInId)).data || []; selected.value = row; absentOnly.value = false; visible.value = true } catch {} }
async function end(row) { try { await ElMessageBox.confirm('确认结束本次签到？', '结束签到'); await endCheckIn(row.checkInId); await load() } catch {} }
async function update(row, status) { try { await updateCheckInStatus({ checkInId: selected.value.checkInId, studentId: row.studentId, status }); row.studentStatus = status; await load() } catch {} }
onMounted(load)
watch(() => props.displaying, showing => { if (!showing) load() })
</script>
<style scoped>
.checkin-panel { background: white; border: 1px solid #f0f2f5; border-radius: 16px; padding: 22px; margin-top: 20px; }
.panel-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
h3 { margin: 0; font-size: 17px; }
.el-pagination { margin-top: 16px; }
.gesture-setup { margin-bottom: 16px; }
.gesture-setup p { margin: 0 0 8px; color: #475569; }
</style>

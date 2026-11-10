<template>
  <section v-if="status?.editable" class="map-settings">
    <h3>高德地图配置</h3>
    <p>{{ status.configured ? '已配置城市识别' : '未配置城市识别，买家和商家可手动选择城市' }}</p>
    <el-form inline @submit.prevent="save">
      <el-form-item label="高德 Web 服务 Key">
        <el-input v-model="key" type="password" autocomplete="off" maxlength="256" />
      </el-form-item>
      <el-button type="primary" native-type="submit" :loading="saving" :disabled="!key.trim()">保存</el-button>
      <el-button :disabled="saving" @click="restore">恢复部署配置</el-button>
    </el-form>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

const status = ref(null)
const key = ref('')
const saving = ref(false)
const endpoint = '/api/admin/map-settings'

onMounted(async () => {
  try {
    status.value = (await request.get(endpoint)).data
  } catch {}
})

async function update(value) {
  if (saving.value) return
  saving.value = true
  try {
    status.value = (await request.put(endpoint, { key: value })).data
    key.value = ''
    ElMessage.success('地图配置已更新')
  } catch {} finally {
    saving.value = false
  }
}

function save() {
  if (key.value.trim()) return update(key.value.trim())
}

async function restore() {
  try {
    await ElMessageBox.confirm('清除页面保存的 Key，改用部署配置。', '恢复部署配置', { type: 'warning' })
    await update('')
  } catch {}
}
</script>

<style scoped>
.map-settings {
  margin-bottom: 16px;
  padding: 16px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
}
.map-settings h3 { margin: 0; }
.map-settings p { color: #64748b; }
</style>

<template>
  <section v-if="status?.editable" class="map-settings">
    <h3>高德地图配置</h3>
    <p>Web 服务 Key 用于地址解析和签到坐标转换。Web 端 Key 与对应安全密钥用于教师签到地图。</p>

    <div v-for="group in groups" :key="group.type" class="credential-group">
      <h4>{{ group.title }}</h4>
      <p v-if="!group.credentials.length" class="empty">暂无新增凭据</p>
      <ol v-else class="credentials">
        <li v-for="(credential, index) in group.credentials" :key="credential.id">
          <div class="credential-name">
            <el-tag size="small" :type="index === 0 ? 'success' : 'info'">{{ index === 0 ? '主用' : `备用 ${index}` }}</el-tag>
            <strong>{{ credential.label }}</strong>
            <code>{{ credential.maskedKey }}</code>
          </div>
          <div class="credential-actions">
            <el-button size="small" :disabled="busy || index === 0" @click="move(group, index, -1)">上移</el-button>
            <el-button size="small" :disabled="busy || index === group.credentials.length - 1" @click="move(group, index, 1)">下移</el-button>
            <el-button size="small" type="danger" plain :disabled="busy" @click="remove(credential)">删除</el-button>
          </div>
        </li>
      </ol>
    </div>

    <div v-if="status.legacyConfigured || status.deploymentConfigured" class="fallback">
      <span>{{ status.legacyConfigured ? '已保留旧版 Web 服务 Key' : '已配置部署 Web 服务 Key' }}，排在上方 Web 服务列表之后。</span>
      <el-button v-if="status.legacyConfigured" text type="danger" :disabled="busy" @click="restore">清除旧版配置</el-button>
    </div>

    <h4>添加凭据</h4>
    <el-form class="add-credential" label-position="top" @submit.prevent="add">
      <el-form-item label="凭据类型">
        <el-select v-model="form.type" @change="form.securityCode = ''">
          <el-option label="Web 服务 Key" value="WEB_SERVICE" />
          <el-option label="Web 端 Key（JS API）" value="JS_API" />
        </el-select>
      </el-form-item>
      <el-form-item label="凭据名称">
        <el-input v-model="form.label" maxlength="80" placeholder="例如：生产主用" />
      </el-form-item>
      <el-form-item label="高德 Key">
        <el-input v-model="form.key" type="password" autocomplete="off" maxlength="256" />
      </el-form-item>
      <el-form-item v-if="form.type === 'JS_API'" label="安全密钥">
        <el-input v-model="form.securityCode" type="password" autocomplete="off" maxlength="256" />
      </el-form-item>
      <el-button type="primary" native-type="submit" :loading="busy" :disabled="!complete">添加</el-button>
    </el-form>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

const status = ref(null), busy = ref(false)
const form = reactive({ type: 'WEB_SERVICE', label: '', key: '', securityCode: '' })
const endpoint = '/api/admin/map-settings'
const groupTypes = [
  { type: 'WEB_SERVICE', title: 'Web 服务 Key' },
  { type: 'JS_API', title: 'Web 端 Key（JS API）' },
]
const groups = computed(() => groupTypes.map(group => ({
  ...group,
  credentials: (status.value?.credentials || []).filter(credential => credential.type === group.type),
})))
const complete = computed(() => !!(form.label.trim() && form.key.trim()
  && (form.type !== 'JS_API' || form.securityCode.trim())))

onMounted(async () => {
  try { status.value = (await request.get(endpoint)).data } catch {}
})

async function update(operation) {
  if (busy.value) return false
  busy.value = true
  try {
    status.value = (await operation()).data
    ElMessage.success('地图配置已更新')
    return true
  } catch (error) {
    if (error.response?.status === 409) {
      try { status.value = (await request.get(endpoint)).data } catch {}
    }
    return false
  } finally { busy.value = false }
}

async function add() {
  if (!complete.value) return
  const { type, label, key, securityCode } = form
  if (await update(() => request.post(`${endpoint}/credentials`, {
    type, label: label.trim(), key: key.trim(), securityCode: type === 'JS_API' ? securityCode.trim() : undefined,
  }))) Object.assign(form, { label: '', key: '', securityCode: '' })
}

function move(group, index, delta) {
  const ids = group.credentials.map(credential => credential.id)
  const [moved] = ids.splice(index, 1)
  ids.splice(index + delta, 0, moved)
  return update(() => request.put(`${endpoint}/credentials/${group.type}/order`, { ids }))
}

async function remove(credential) {
  try {
    await ElMessageBox.confirm(`确认删除「${credential.label}」？`, '删除地图凭据', { type: 'warning' })
    await update(() => request.delete(`${endpoint}/credentials/${credential.id}`))
  } catch {}
}

async function restore() {
  try {
    await ElMessageBox.confirm('清除旧版 Web 服务 Key。已添加的凭据和部署配置仍可使用。', '清除旧版配置', { type: 'warning' })
    await update(() => request.put(endpoint, { key: '' }))
  } catch {}
}
</script>

<style scoped>
.map-settings { margin-bottom: 16px; padding: 16px; background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; }
h3, h4 { margin: 0; }
.map-settings p, .fallback { color: #64748b; }
.credential-group, .fallback { margin: 12px 0; }
.credentials { list-style: none; margin: 8px 0; padding: 0; }
.credentials li, .credential-name, .credential-actions, .fallback { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.credentials li { justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #e5e7eb; }
.credential-actions :deep(.el-button), .fallback :deep(.el-button) { margin: 0; }
.empty { margin: 6px 0; }
.add-credential { display: grid; grid-template-columns: repeat(auto-fit, minmax(156px, 1fr)); gap: 12px; align-items: end; margin-top: 8px; }
.add-credential :deep(.el-form-item) { margin: 0; }
</style>

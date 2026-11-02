<template>
  <div class="page">
    <header class="page__header">
      <div>
        <h2>广告与订单</h2>
        <p>发布/更新/下架广告，管理订单物流与商品评价</p>
      </div>
      <el-button type="primary" @click="openAdDialog()">发布广告</el-button>
    </header>

    <el-tabs v-model="activeTab" @tab-change="loadTab">
      <el-tab-pane label="广告管理" name="ads" />
      <el-tab-pane name="orders">
        <template #label>
          <span class="tab-label">
            订单物流
            <el-badge v-if="pendingCount > 0" :value="pendingCount" class="tab-badge" />
          </span>
        </template>
      </el-tab-pane>
      <el-tab-pane label="商品评价" name="evaluations" />
    </el-tabs>

    <section v-if="activeTab === 'ads'" class="panel">
      <el-table v-loading="loading" :data="adList" size="small" empty-text="暂无广告">
        <el-table-column prop="adId" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="120" />
        <el-table-column prop="content" label="内容" min-width="180" show-overflow-tooltip />
        <el-table-column prop="price" label="价格" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.isDeleted === 1" type="info" size="small">已下架</el-tag>
            <el-tag v-else type="success" size="small">在售</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button link size="small" :disabled="row.isDeleted === 1" @click="openAdDialog(row)">
              编辑
            </el-button>
            <el-button
              link
              size="small"
              type="danger"
              :disabled="row.isDeleted === 1"
              @click="handleDeleteAd(row.adId)"
            >
              下架
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="adPage"
        :page-size="pageSize"
        :total="adTotal"
        layout="prev, pager, next"
        class="pager"
        @current-change="loadAds"
      />
    </section>

    <section v-else-if="activeTab === 'orders'" class="panel">
      <el-alert
        v-if="pendingCount > 0"
        :title="`有 ${pendingCount} 笔待发货订单，请及时填写物流`"
        type="warning"
        show-icon
        :closable="false"
        class="order-alert"
      />
      <el-table v-loading="loading" :data="orderList" size="small" empty-text="暂无订单">
        <el-table-column prop="orderId" label="订单号" width="110" />
        <el-table-column prop="adId" label="广告ID" width="80" />
        <el-table-column prop="adTitle" label="商品" min-width="120" show-overflow-tooltip />
        <el-table-column prop="buyerId" label="买家ID" width="120" />
        <el-table-column prop="buyerName" label="买家" width="100" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 0" type="danger" size="small">待发货</el-tag>
            <el-tag v-else-if="row.status === 1" type="warning" size="small">已发货</el-tag>
            <el-tag v-else-if="row.status === 2" type="success" size="small">已收货</el-tag>
            <el-tag v-else type="info" size="small">未知</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="logisticsInfo" label="物流" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link size="small" :disabled="row.status === 2" @click="openLogistics(row)">
              物流
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="orderPage"
        :page-size="pageSize"
        :total="orderTotal"
        layout="prev, pager, next"
        class="pager"
        @current-change="loadOrders"
      />
    </section>

    <section v-else class="panel">
      <el-table v-loading="loading" :data="evaluationList" size="small" empty-text="暂无评价">
        <el-table-column prop="evaluationId" label="ID" width="70" />
        <el-table-column prop="orderId" label="订单号" width="90" />
        <el-table-column prop="buyerId" label="买家ID" width="120" />
        <el-table-column prop="rating" label="星级" width="70" />
        <el-table-column prop="feedbackContent" label="反馈" min-width="200" show-overflow-tooltip />
      </el-table>
      <el-pagination
        v-model:current-page="evalPage"
        :page-size="pageSize"
        :total="evalTotal"
        layout="prev, pager, next"
        class="pager"
        @current-change="loadEvaluations"
      />
    </section>

    <el-dialog v-model="adVisible" :title="adForm.adId ? '编辑广告' : '发布广告'" width="520px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="标题"><el-input v-model="adForm.title" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="adForm.content" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="价格"><el-input-number v-model="adForm.price" :min="0" :step="0.01" /></el-form-item>
        <el-form-item label="图片">
          <el-input v-model="adForm.imageUrl" placeholder="图片 URL 或上传后填入" />
          <input type="file" accept="image/*" class="file-input" @change="onImagePick" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAd">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="logisticsVisible" title="发布物流信息" width="420px" destroy-on-close>
      <el-input v-model="logisticsInfo" type="textarea" :rows="3" placeholder="物流公司 + 单号" />
      <template #footer>
        <el-button @click="logisticsVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveLogistics">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchAdminAdList,
  fetchAdminOrderList,
  fetchAdminEvaluationList,
  fetchPendingOrderCount,
  publishAd,
  updateAd,
  deleteAd,
  publishLogistics,
} from '@/api/admin'
import { uploadImage } from '@/api/file'

const activeTab = ref('ads')
const loading = ref(false)
const saving = ref(false)
const pageSize = 10
const pendingCount = ref(0)

const adPage = ref(1)
const adTotal = ref(0)
const adList = ref([])

const orderPage = ref(1)
const orderTotal = ref(0)
const orderList = ref([])

const evalPage = ref(1)
const evalTotal = ref(0)
const evaluationList = ref([])

const adVisible = ref(false)
const adForm = reactive({ adId: null, title: '', content: '', price: 0, imageUrl: '' })

const logisticsVisible = ref(false)
const logisticsInfo = ref('')
const currentOrderId = ref(null)

function pageList(data) {
  return data?.list || data?.records || []
}

function flattenOrder(item) {
  const order = item?.order || item || {}
  return {
    orderId: order.orderId,
    adId: order.adId,
    buyerId: order.buyerId,
    logisticsInfo: order.logisticsInfo,
    status: order.status,
    afterSaleStatus: order.afterSaleStatus,
    createTime: order.createTime,
    adTitle: item?.advertisement?.title || '',
    buyerName: item?.buyer?.displayName || '',
  }
}

function openAdDialog(row) {
  adForm.adId = row?.adId || null
  adForm.title = row?.title || ''
  adForm.content = row?.content || ''
  adForm.price = row?.price || 0
  adForm.imageUrl = row?.imageUrl || ''
  adVisible.value = true
}

async function onImagePick(e) {
  const file = e.target.files?.[0]
  if (!file) return
  try {
    const res = await uploadImage(file)
    adForm.imageUrl = res.data?.url || res.data
    ElMessage.success('图片上传成功')
  } catch {
    // handled
  }
}

async function saveAd() {
  saving.value = true
  try {
    if (adForm.adId) {
      await updateAd({ ...adForm })
      ElMessage.success('广告已更新')
    } else {
      await publishAd({ ...adForm })
      ElMessage.success('广告已发布')
    }
    adVisible.value = false
    adPage.value = 1
    await loadAds()
  } catch {
    // handled
  } finally {
    saving.value = false
  }
}

async function handleDeleteAd(adId) {
  try {
    await ElMessageBox.confirm('确定下架该广告？', '下架确认', { type: 'warning' })
    await deleteAd(adId)
    ElMessage.success('已下架')
    await loadAds()
  } catch {
    // cancel
  }
}

function openLogistics(row) {
  currentOrderId.value = row.orderId
  logisticsInfo.value = row.logisticsInfo || ''
  logisticsVisible.value = true
}

async function saveLogistics() {
  if (!logisticsInfo.value.trim()) {
    ElMessage.warning('请填写物流信息')
    return
  }
  saving.value = true
  try {
    await publishLogistics({ orderId: currentOrderId.value, logisticsInfo: logisticsInfo.value })
    ElMessage.success('物流信息已更新')
    logisticsVisible.value = false
    await loadOrders()
    await refreshPendingCount()
  } catch {
    // handled
  } finally {
    saving.value = false
  }
}

async function refreshPendingCount() {
  try {
    const res = await fetchPendingOrderCount()
    pendingCount.value = res.data?.count || 0
  } catch {
    pendingCount.value = 0
  }
}

async function loadAds() {
  loading.value = true
  try {
    const res = await fetchAdminAdList(adPage.value, pageSize)
    adList.value = pageList(res.data)
    adTotal.value = res.data?.total || 0
  } catch {
    adList.value = []
  } finally {
    loading.value = false
  }
}

async function loadOrders() {
  loading.value = true
  try {
    const res = await fetchAdminOrderList(orderPage.value, pageSize)
    orderList.value = pageList(res.data).map(flattenOrder)
    orderTotal.value = res.data?.total || 0
    await refreshPendingCount()
  } catch {
    orderList.value = []
  } finally {
    loading.value = false
  }
}

async function loadEvaluations() {
  loading.value = true
  try {
    const res = await fetchAdminEvaluationList(evalPage.value, pageSize)
    evaluationList.value = pageList(res.data)
    evalTotal.value = res.data?.total || 0
  } catch {
    evaluationList.value = []
  } finally {
    loading.value = false
  }
}

function loadTab() {
  if (activeTab.value === 'ads') loadAds()
  else if (activeTab.value === 'orders') loadOrders()
  else loadEvaluations()
}

onMounted(async () => {
  await refreshPendingCount()
  if (pendingCount.value > 0) {
    activeTab.value = 'orders'
    await loadOrders()
  } else {
    await loadAds()
  }
})
</script>

<style scoped>
.page__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.page__header h2 {
  margin: 0;
  font-size: 24px;
}

.page__header p {
  margin: 8px 0 0;
  color: #6b7280;
}

.panel {
  padding: 16px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid #f3f4f6;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.file-input {
  margin-top: 8px;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.tab-badge {
  margin-top: -2px;
}

.order-alert {
  margin-bottom: 14px;
}
</style>

<template>
  <div class="shop-page">
    <header class="page-header">
      <div>
        <h2>周边商城</h2>
        <p>浏览在售广告商品，下单后可查看物流、确认收货与评价反馈</p>
      </div>
      <div class="header-tabs">
        <button
          type="button"
          class="tab"
          :class="{ active: mainTab === 'goods' }"
          @click="mainTab = 'goods'"
        >
          商品浏览
        </button>
        <button
          type="button"
          class="tab"
          :class="{ active: mainTab === 'orders' }"
          @click="switchToOrders"
        >
          订单与物流
        </button>
      </div>
    </header>

    <!-- 商品列表 -->
    <section v-show="mainTab === 'goods'" v-loading="adsLoading" class="goods-grid">
      <article
        v-for="ad in ads"
        :key="ad.adId"
        class="goods-card"
        @click="openDetail(ad)"
      >
        <div class="goods-cover">
          <img
            v-if="ad.imageUrl"
            :src="resolveMediaUrl(ad.imageUrl)"
            :alt="ad.title"
          />
          <div v-else class="goods-cover__placeholder">
            <i class="ph ph-package" />
          </div>
          <span class="goods-tag">官方广告</span>
        </div>
        <div class="goods-body">
          <h3>{{ ad.title || `商品 #${ad.adId}` }}</h3>
          <p>{{ ad.content || '暂无描述' }}</p>
          <div class="goods-foot">
            <span class="price">¥ {{ formatPrice(ad.price) }}</span>
            <span class="score">评分 {{ formatScore(ad.score) }}</span>
          </div>
          <el-button type="primary" class="buy-btn" @click.stop="quickBuy(ad)">
            立即购买
          </el-button>
        </div>
      </article>
      <p v-if="!adsLoading && !ads.length" class="empty-tip">暂无在售商品</p>
    </section>

    <!-- 订单中心 -->
    <section v-show="mainTab === 'orders'" v-loading="ordersLoading" class="orders">
      <div v-for="item in orders" :key="item.order?.orderId" class="order-card">
        <div class="order-cover">
          <img
            v-if="item.advertisement?.imageUrl"
            :src="resolveMediaUrl(item.advertisement.imageUrl)"
            alt=""
          />
          <div v-else class="goods-cover__placeholder small">
            <i class="ph ph-package" />
          </div>
        </div>
        <div class="order-body">
          <div class="order-top">
            <h3>{{ item.advertisement?.title || `广告 #${item.order?.adId}` }}</h3>
            <el-tag :type="statusTag(item.order?.status)" size="small">
              {{ statusLabel(item.order?.status) }}
            </el-tag>
          </div>
          <p class="order-meta">
            订单号 {{ item.order?.orderId }} ·
            {{ formatTime(item.order?.createTime) }} ·
            ¥ {{ formatPrice(item.advertisement?.price) }}
          </p>
          <p class="logistics">
            <i class="ph ph-truck" />
            物流：{{ item.order?.logisticsInfo || '暂无物流信息' }}
          </p>
          <p v-if="item.order?.afterSaleStatus === 1" class="after-sale pending">
            售后申请中：{{ item.order.afterSaleContent }}
          </p>
          <p v-else-if="item.order?.afterSaleStatus === 2" class="after-sale done">
            售后已处理：{{ item.order.afterSaleReply || '管理员已处理' }}
          </p>
          <p v-if="item.evaluation" class="eval-line">
            我的评价：{{ '★'.repeat(item.evaluation.rating || 0) }}
            {{ item.evaluation.feedbackContent || '' }}
          </p>
          <div class="order-actions">
            <el-button size="small" @click="openOrderDetail(item)">详情</el-button>
            <el-button
              v-if="item.order?.status === 1"
              size="small"
              type="primary"
              @click="doConfirm(item.order.orderId)"
            >
              确认收货
            </el-button>
            <el-button
              v-if="item.order?.status === 2 && !item.evaluation"
              size="small"
              @click="openFeedback(item)"
            >
              提交评价
            </el-button>
            <el-button
              v-if="[0, 1, 2].includes(item.order?.status) && item.order?.afterSaleStatus !== 1"
              size="small"
              type="danger"
              plain
              @click="openAfterSale(item)"
            >
              申请售后
            </el-button>
          </div>
        </div>
      </div>
      <p v-if="!ordersLoading && !orders.length" class="empty-tip">暂无订单</p>
    </section>

    <!-- 商品详情 -->
    <el-dialog v-model="showDetail" title="商品详情" width="560px" destroy-on-close>
      <div v-if="currentAd" class="detail">
        <img
          v-if="currentAd.imageUrl"
          :src="resolveMediaUrl(currentAd.imageUrl)"
          alt=""
          class="detail-img"
        />
        <h3>{{ currentAd.title || `商品 #${currentAd.adId}` }}</h3>
        <p class="detail-price">¥ {{ formatPrice(currentAd.price) }}</p>
        <p class="detail-score">综合评分 {{ formatScore(currentAd.score) }}</p>
        <p class="detail-content">{{ currentAd.content || '暂无描述' }}</p>
      </div>
      <template #footer>
        <el-button @click="showDetail = false">关闭</el-button>
        <el-button type="primary" :loading="buying" @click="doBuy(currentAd?.adId)">
          下单购买
        </el-button>
      </template>
    </el-dialog>

    <!-- 订单详情 -->
    <el-dialog v-model="showOrderDetail" title="订单详情" width="520px" destroy-on-close>
      <div v-if="currentOrder" class="detail">
        <p><strong>订单号：</strong>{{ currentOrder.order?.orderId }}</p>
        <p><strong>商品：</strong>{{ currentOrder.advertisement?.title || currentOrder.order?.adId }}</p>
        <p><strong>状态：</strong>{{ statusLabel(currentOrder.order?.status) }}</p>
        <p><strong>物流：</strong>{{ currentOrder.order?.logisticsInfo || '暂无' }}</p>
        <p><strong>下单时间：</strong>{{ formatTime(currentOrder.order?.createTime) }}</p>
        <p v-if="currentOrder.order?.afterSaleContent">
          <strong>售后申请：</strong>{{ currentOrder.order.afterSaleContent }}
        </p>
        <p v-if="currentOrder.order?.afterSaleReply">
          <strong>售后回复：</strong>{{ currentOrder.order.afterSaleReply }}
        </p>
        <p v-if="currentOrder.evaluation">
          <strong>评价：</strong>
          {{ currentOrder.evaluation.rating }} 星 ·
          {{ currentOrder.evaluation.feedbackContent }}
        </p>
      </div>
    </el-dialog>

    <!-- 评价 -->
    <el-dialog v-model="showFeedback" title="提交评价反馈" width="440px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="星级（1-5）" required>
          <el-rate v-model="feedbackForm.rating" :max="5" />
        </el-form-item>
        <el-form-item label="文字反馈">
          <el-input
            v-model="feedbackForm.feedbackContent"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="说说使用体验"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showFeedback = false">取消</el-button>
        <el-button type="primary" :loading="feedbackLoading" @click="doFeedback">提交</el-button>
      </template>
    </el-dialog>

    <!-- 售后 -->
    <el-dialog v-model="showAfterSale" title="提交售后申请" width="440px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="售后说明" required>
          <el-input
            v-model="afterSaleForm.content"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="请描述问题"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAfterSale = false">取消</el-button>
        <el-button type="primary" :loading="afterSaleLoading" @click="doAfterSale">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { resolveMediaUrl } from '@/utils/media'
import {
  fetchAdList,
  fetchAdDetail,
  createOrder,
  fetchMyOrders,
  fetchOrderDetail,
  confirmOrder,
  submitFeedback,
  submitAfterSale,
} from '@/api/shop'

const mainTab = ref('goods')
const ads = ref([])
const adsLoading = ref(false)
const orders = ref([])
const ordersLoading = ref(false)

const showDetail = ref(false)
const currentAd = ref(null)
const buying = ref(false)

const showOrderDetail = ref(false)
const currentOrder = ref(null)

const showFeedback = ref(false)
const feedbackLoading = ref(false)
const feedbackForm = reactive({
  orderId: null,
  rating: 5,
  feedbackContent: '',
})

const showAfterSale = ref(false)
const afterSaleLoading = ref(false)
const afterSaleForm = reactive({
  orderId: null,
  content: '',
})

function formatPrice(v) {
  if (v === null || v === undefined || v === '') return '0.00'
  return Number(v).toFixed(2)
}

function formatScore(v) {
  if (v === null || v === undefined || v === '') return '暂无'
  return Number(v).toFixed(1)
}

function formatTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

function statusLabel(s) {
  const map = { 0: '待发货', 1: '已发货', 2: '已收货' }
  return map[s] ?? '未知'
}

function statusTag(s) {
  if (s === 0) return 'warning'
  if (s === 1) return 'primary'
  if (s === 2) return 'success'
  return 'info'
}

async function loadAds() {
  adsLoading.value = true
  try {
    const res = await fetchAdList()
    ads.value = res.data || []
  } catch {
    ads.value = []
  } finally {
    adsLoading.value = false
  }
}

async function loadOrders() {
  ordersLoading.value = true
  try {
    const res = await fetchMyOrders()
    orders.value = res.data || []
  } catch {
    orders.value = []
  } finally {
    ordersLoading.value = false
  }
}

function switchToOrders() {
  mainTab.value = 'orders'
  loadOrders()
}

async function openDetail(ad) {
  try {
    const res = await fetchAdDetail(ad.adId)
    currentAd.value = res.data || ad
  } catch {
    currentAd.value = ad
  }
  showDetail.value = true
}

async function doBuy(adId) {
  if (!adId) return
  buying.value = true
  try {
    await createOrder(adId)
    ElMessage.success('下单成功')
    showDetail.value = false
    switchToOrders()
  } catch {
    // handled
  } finally {
    buying.value = false
  }
}

async function quickBuy(ad) {
  try {
    await ElMessageBox.confirm(`确认购买「${ad.title || ad.adId}」？`, '确认下单', {
      type: 'info',
    })
    await doBuy(ad.adId)
  } catch {
    // cancel
  }
}

async function openOrderDetail(item) {
  try {
    const res = await fetchOrderDetail(item.order.orderId)
    currentOrder.value = res.data || item
  } catch {
    currentOrder.value = item
  }
  showOrderDetail.value = true
}

async function doConfirm(orderId) {
  try {
    await ElMessageBox.confirm('确认已收到商品？', '确认收货', { type: 'warning' })
    await confirmOrder(orderId)
    ElMessage.success('已确认收货')
    loadOrders()
  } catch {
    // cancel
  }
}

function openFeedback(item) {
  feedbackForm.orderId = item.order.orderId
  feedbackForm.rating = 5
  feedbackForm.feedbackContent = ''
  showFeedback.value = true
}

async function doFeedback() {
  if (!feedbackForm.rating) {
    ElMessage.warning('请选择星级')
    return
  }
  feedbackLoading.value = true
  try {
    await submitFeedback({
      orderId: feedbackForm.orderId,
      rating: feedbackForm.rating,
      feedbackContent: feedbackForm.feedbackContent.trim(),
    })
    ElMessage.success('评价成功')
    showFeedback.value = false
    loadOrders()
  } catch {
    // handled
  } finally {
    feedbackLoading.value = false
  }
}

function openAfterSale(item) {
  afterSaleForm.orderId = item.order.orderId
  afterSaleForm.content = ''
  showAfterSale.value = true
}

async function doAfterSale() {
  if (!afterSaleForm.content.trim()) {
    ElMessage.warning('请填写售后说明')
    return
  }
  afterSaleLoading.value = true
  try {
    await submitAfterSale({
      orderId: afterSaleForm.orderId,
      content: afterSaleForm.content.trim(),
    })
    ElMessage.success('售后申请已提交')
    showAfterSale.value = false
    loadOrders()
  } catch {
    // handled
  } finally {
    afterSaleLoading.value = false
  }
}

onMounted(() => {
  loadAds()
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;
}

.page-header h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
}

.page-header p {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 14px;
}

.header-tabs {
  display: flex;
  padding: 4px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #f3f4f6;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.04);
}

.tab {
  border: none;
  background: transparent;
  padding: 8px 16px;
  border-radius: 10px;
  color: #6b7280;
  font-weight: 600;
  cursor: pointer;
}

.tab.active {
  background: var(--theme-gradient);
  color: #fff;
}

.goods-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 18px;
  min-height: 160px;
}

.goods-card {
  background: #fff;
  border: 1px solid #f3f4f6;
  border-radius: 18px;
  overflow: hidden;
  cursor: pointer;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.04);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.goods-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
}

.goods-cover {
  position: relative;
  height: 148px;
  background: #f3f4f6;
  overflow: hidden;
}

.goods-cover img,
.order-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.goods-cover__placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #cbd5e1;
  font-size: 48px;
  background: linear-gradient(135deg, rgba(102, 204, 255, 0.12), rgba(0, 255, 204, 0.1));
}

.goods-cover__placeholder.small {
  font-size: 28px;
}

.goods-tag {
  position: absolute;
  top: 10px;
  right: 10px;
  padding: 3px 8px;
  border-radius: 6px;
  background: #ef4444;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
}

.goods-body {
  padding: 16px;
}

.goods-body h3 {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 700;
}

.goods-body > p {
  margin: 0;
  color: #9ca3af;
  font-size: 12px;
  line-height: 1.5;
  height: 36px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.goods-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 12px 0;
}

.price {
  color: var(--theme-primary);
  font-size: 18px;
  font-weight: 700;
}

.score {
  font-size: 12px;
  color: #9ca3af;
}

.buy-btn {
  width: 100%;
  background: var(--theme-gradient) !important;
  border: none !important;
}

.orders {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 160px;
}

.order-card {
  display: flex;
  gap: 16px;
  padding: 16px;
  background: #fff;
  border: 1px solid #f3f4f6;
  border-radius: 16px;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.04);
}

.order-cover {
  width: 96px;
  height: 96px;
  flex-shrink: 0;
  border-radius: 12px;
  overflow: hidden;
  background: #f3f4f6;
}

.order-body {
  flex: 1;
  min-width: 0;
}

.order-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.order-top h3 {
  margin: 0;
  font-size: 15px;
}

.order-meta,
.logistics,
.after-sale,
.eval-line {
  margin: 6px 0 0;
  font-size: 13px;
  color: #6b7280;
}

.after-sale.pending {
  color: #d97706;
}

.after-sale.done {
  color: #059669;
}

.order-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.empty-tip {
  grid-column: 1 / -1;
  text-align: center;
  color: #9ca3af;
  padding: 48px 0;
}

.detail-img {
  width: 100%;
  max-height: 240px;
  object-fit: cover;
  border-radius: 12px;
  margin-bottom: 14px;
}

.detail h3 {
  margin: 0 0 8px;
}

.detail-price {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--theme-primary);
}

.detail-score {
  margin: 6px 0 12px;
  color: #9ca3af;
  font-size: 13px;
}

.detail-content {
  margin: 0;
  color: #4b5563;
  line-height: 1.6;
  white-space: pre-wrap;
}

.detail p {
  margin: 8px 0;
  line-height: 1.6;
}

@media (max-width: 720px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .order-card {
    flex-direction: column;
  }

  .order-cover {
    width: 100%;
    height: 140px;
  }
}
</style>

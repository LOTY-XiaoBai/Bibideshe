<template>
  <div class="shop-page">
    <header class="page-header">
      <div>
        <h2>周边商城</h2>
        <p>浏览合作商家已审核上架的商品，下单后可查看物流、确认收货与评价反馈</p>
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

    <el-form v-if="mainTab === 'goods'" class="shop-search" @submit.prevent="loadAds">
      <div class="shop-search-head">
        <h3>筛选商品</h3>
        <el-button text @click="clearFilters">清空条件</el-button>
      </div>
      <div class="shop-keyword">
        <el-input v-model="keyword" size="large" clearable maxlength="100" placeholder="搜索商品、商家或发货城市"
          aria-label="搜索商品、商家或发货城市" @clear="loadAds">
          <template #prefix><i class="ph ph-magnifying-glass" /></template>
        </el-input>
        <el-button native-type="submit" size="large" type="primary" :loading="adsLoading">搜索</el-button>
      </div>
      <div class="shop-filter-fields">
        <fieldset class="shop-filter-field">
          <legend>价格区间（元）</legend>
          <div class="shop-price-range">
            <input v-model.trim="filters.minPrice" aria-label="最低价" inputmode="decimal" maxlength="11" placeholder="最低价" />
            <span aria-hidden="true">–</span>
            <input v-model.trim="filters.maxPrice" aria-label="最高价" inputmode="decimal" maxlength="11" placeholder="最高价" />
          </div>
        </fieldset>
        <fieldset class="shop-filter-field">
          <legend>发货城市</legend>
          <div class="shop-city-picker">
            <select v-model="filters.city" aria-label="发货城市">
              <option value="">不限城市</option>
              <option v-for="city in cityOptions" :key="city" :value="city">{{ city }}</option>
            </select>
            <el-button :loading="locating" @click="useLocation">
              <i class="ph ph-map-pin" aria-hidden="true" />定位我的城市
            </el-button>
          </div>
        </fieldset>
      </div>
      <fieldset class="shop-tag-filters">
        <legend>商品标签</legend>
        <div class="shop-tag-options">
          <label v-for="tag in filterTags" :key="tag.code">
            <input v-model="filters.tags" type="checkbox" :value="tag.code" @change="loadAds" />
            <span>{{ tag.label }}</span>
          </label>
        </div>
      </fieldset>
      <p v-if="locationNotice" class="shop-location-notice" role="status">{{ locationNotice }}</p>
    </el-form>
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
          <span class="goods-tag">合作商家</span>
        </div>
        <div class="goods-body">
          <h3>{{ ad.title || `商品 #${ad.adId}` }}</h3><RouterLink v-if="ad.merchantId" class="goods-merchant" :to="`/${getRole().toLowerCase()}/shop/merchant/${ad.merchantId}`" @click.stop><i class="ph ph-storefront" /> {{ ad.merchantName||'进入店铺' }} <i class="ph ph-caret-right" /></RouterLink>
          <div v-if="ad.shipCity || ad.tags?.length" class="goods-labels">
            <span v-if="ad.shipCity">{{ ad.shipCity }}发货</span>
            <el-tag v-for="tag in ad.tags" :key="tag" size="small" effect="plain">{{ tagLabels[tag] || tag }}</el-tag>
          </div>
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
      <p v-if="!adsLoading && !ads.length" class="empty-tip">{{ appliedFilters ? '没有找到匹配的商品，可调整或清空条件' : '暂无在售商品' }}</p>
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
            <el-button v-if="item.order?.status===2 && item.order?.afterSaleStatus!==1" size="small" type="danger" plain @click="removeOrder(item.order.orderId)">删除订单记录</el-button>
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
        <p><strong>收货地址：</strong>{{ currentOrder.order?.shippingSnapshot || '历史订单未记录地址' }}</p>
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
import { useRouter } from 'vue-router'
import { getRole } from '@/utils/auth'
const router = useRouter()
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { resolveMediaUrl } from '@/utils/media'
import {
  fetchAdList,
  fetchCatalogOptions,
  shopLocationAvailable,
  locateShopCity,
  deleteMyOrder,
  fetchAdDetail,
  createOrder,
  fetchMyOrders,
  fetchOrderDetail,
  confirmOrder,
  submitFeedback,
  submitAfterSale,
} from '@/api/shop'

const mainTab = ref('goods')
const keyword = ref('')
const filters = reactive({ minPrice: '', maxPrice: '', city: '', tags: [] })
const options = ref({ cities: [], tags: [] })
const cityOptions = computed(() => [...new Set([...options.value.cities, filters.city].filter(Boolean))])
const filterTags = computed(() => [...options.value.tags, { code: 'other', label: '其他' }])
const tagLabels = computed(() => Object.fromEntries(filterTags.value.map(tag => [tag.code, tag.label])))
const locating = ref(false)
const locationNotice = ref('')
const appliedFilters = ref(false)
let locationVersion = 0
let searchVersion=0
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
  const { minPrice, maxPrice, city, tags } = filters
  const validPrice = value => !value || /^\d{1,8}(\.\d{1,2})?$/.test(value)
  if (!validPrice(minPrice) || !validPrice(maxPrice)) {
    return ElMessage.warning('筛选价格须为非负金额，最多两位小数')
  }
  if (minPrice && maxPrice && Number(minPrice) > Number(maxPrice)) {
    return ElMessage.warning('最低价不能高于最高价')
  }
  const stamp=++searchVersion
  adsLoading.value = true
  try {
    const search = keyword.value.trim()
    const res = await fetchAdList(search, { minPrice, maxPrice, city, tags: tags.join(',') })
    if(stamp!==searchVersion)return
    appliedFilters.value = Boolean(search || minPrice || maxPrice || city || tags.length)
    ads.value = res.data || []
  } catch {
    if(stamp===searchVersion)ads.value = []
  } finally {
    if(stamp===searchVersion)adsLoading.value = false
  }
}

function clearFilters() {
  ++locationVersion
  locating.value = false
  keyword.value = ''
  Object.assign(filters, { minPrice: '', maxPrice: '', city: '', tags: [] })
  locationNotice.value = ''
  loadAds()
}

async function loadCatalogOptions() {
  try {
    options.value = (await fetchCatalogOptions()).data
  } catch {}
}

async function useLocation() {
  const stamp = ++locationVersion
  const originalCity = filters.city
  const manual = '请手动选择发货城市'
  locationNotice.value = ''
  if (!window.isSecureContext || !navigator.geolocation) {
    locationNotice.value = `当前页面无法定位，${manual}。`
    return
  }
  locating.value = true
  try {
    const { data } = await shopLocationAvailable()
    if (stamp !== locationVersion) return
    if (!data.available) {
      locationNotice.value = `城市识别尚未配置，${manual}。`
      return
    }
    const position = await new Promise((resolve, reject) => navigator.geolocation.getCurrentPosition(resolve, reject, {
      timeout: 10000,
      maximumAge: 300000,
    }))
    if (stamp !== locationVersion) return
    const { latitude, longitude } = position?.coords || {}
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
      locationNotice.value = `没有获得有效定位坐标，${manual}。`
      return
    }
    const result = await locateShopCity(latitude, longitude)
    if (stamp !== locationVersion || filters.city !== originalCity) return
    filters.city = result.data.city
    locationNotice.value = `已选择同城发货 · ${filters.city}`
    await loadAds()
  } catch (error) {
    if (stamp === locationVersion) {
      locationNotice.value = error.code === 1 ? `定位权限被拒绝，${manual}。` : `未能识别城市，${manual}。`
    }
  } finally {
    if (stamp === locationVersion) locating.value = false
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
  router.push(`/${getRole().toLowerCase()}/shop/product/${ad.adId}`)
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
  await openDetail(ad)
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

async function removeOrder(id){const ok=await ElMessageBox.confirm('删除后该订单将从你的列表中移除，商家的履约记录和商品评价会保留。','删除历史订单',{type:'warning',confirmButtonText:'删除记录',cancelButtonText:'取消'}).then(()=>true).catch(()=>false);if(!ok)return;try{await deleteMyOrder(id);ElMessage.success('订单记录已删除');await loadOrders()}catch{}}
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
  loadCatalogOptions()
  loadAds()
})
</script>

<style scoped>
.shop-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 15px;
  max-width: 980px;
  margin-bottom: 24px;
  padding: clamp(14px, 2vw, 22px);
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 16px;
}
.shop-search-head, .shop-keyword, .shop-city-picker, .shop-price-range {
  display: flex;
  align-items: center;
  gap: 8px;
}
.shop-search-head { justify-content: space-between; }
.shop-search-head h3 { margin: 0; font-size: 16px; }
.shop-keyword .el-input { min-width: 0; flex: 1; }
.shop-filter-fields { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.3fr); gap: 16px; }
.shop-filter-field, .shop-tag-filters { margin: 0; padding: 0; border: 0; min-width: 0; }
.shop-filter-field legend, .shop-tag-filters legend { margin-bottom: 6px; font-size: 13px; font-weight: 600; }
.shop-price-range input, .shop-city-picker select {
  box-sizing: border-box;
  min-width: 0;
  width: 100%;
  height: 36px;
  padding: 0 9px;
  color: var(--el-text-color-primary);
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
}
.shop-city-picker .el-button { flex-shrink: 0; height: 36px; }
.shop-city-picker i { margin-right: 4px; }
.shop-tag-options { display: flex; flex-wrap: wrap; gap: 8px; }
.shop-tag-options label { position: relative; cursor: pointer; }
.shop-tag-options input { position: absolute; opacity: 0; }
.shop-tag-options span {
  display: block;
  padding: 6px 13px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border: 1px solid transparent;
  border-radius: 999px;
  font-size: 13px;
}
.shop-tag-options label:hover span { border-color: var(--theme-primary); }
.shop-tag-options input:checked + span {
  color: var(--el-color-primary-dark-2);
  background: var(--el-color-primary-light-9);
  border-color: var(--theme-primary);
  font-weight: 600;
}
.shop-tag-options input:focus-visible + span { outline: 2px solid var(--theme-primary); outline-offset: 2px; }
@media (max-width: 820px) {
  .shop-filter-fields { grid-template-columns: 1fr; gap: 12px; }
}
.shop-location-notice { font-size: 13px; }
.shop-location-notice { margin: 0; color: #475569; }
.goods-labels { display: flex; flex-wrap: wrap; align-items: center; gap: 5px; font-size: 12px; color: #475569; }
.goods-merchant{display:inline-flex;gap:6px;align-items:center;font-size:11px;color:#8e9eaf;margin:3px 0 10px}.goods-merchant:hover{color:var(--theme-primary)}
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

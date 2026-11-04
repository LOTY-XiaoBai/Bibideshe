<template>
  <section class="surface carousel-manager">
    <div class="carousel-title">
      <div><p class="eyebrow">HOMEPAGE SPOTLIGHT</p><h2><i class="ph ph-slideshow" /> 首页轮播</h2><p>选择首页展示的广告，为每一份推荐安排合适的节奏。</p></div>
      <el-button type="primary" plain @click="openCampaigns">选择商家广告 <i class="ph ph-arrow-up-right" /></el-button>
    </div>
    <div v-loading="loading" class="carousel-options">
      <div class="official-option"><span class="official-icon"><i class="ph ph-storefront" /></span><div><strong>官方 · 周边商城</strong><p>内置广告永久保留，关闭后仅退出轮播。</p></div><el-switch v-model="settings.officialEnabled" aria-label="官方广告加入轮播" :disabled="loading||saving" /></div>
      <div class="interval-option"><label for="carousel-seconds">每张广告停留</label><div><el-input-number v-if="!loading" id="carousel-seconds" v-model="settings.intervalSeconds" :min="3" :max="60" :precision="0" :disabled="saving" controls-position="right" /><span>秒</span></div><small>支持 3～60 秒，师生仍可手动切换</small></div>
      <el-button type="primary" :loading="saving" :disabled="loading" @click="save">保存轮播设置</el-button>
    </div>
    <el-dialog v-model="visible" title="选择商家轮播广告" width="860px" class="commerce-dialog admin-commerce-dialog" align-center>
      <p class="campaign-help">只有审核通过且关联商品在售的广告可以加入轮播。移出轮播不影响审核结果。</p>
      <el-table :data="rows" v-loading="listLoading" empty-text="暂无审核通过的商家广告">
        <el-table-column label="广告" min-width="230"><template #default="{row}"><div class="carousel-candidate"><img :src="row.imageUrl" :alt="row.title" /><div><strong>{{ row.title }}</strong><small>{{ row.merchantName }}</small></div></div></template></el-table-column>
        <el-table-column label="关联内容" min-width="145"><template #default="{row}"><span>{{ row.targetType==='MERCHANT'?'商家主页':row.productTitle }}</span></template></el-table-column>
        <el-table-column label="加入轮播" width="120"><template #default="{row}"><el-switch :model-value="Boolean(row.carouselEnabled)" :loading="changing===row.id" :disabled="changing!==null" :aria-label="`${row.title}加入轮播`" @change="value=>select(row,value)" /></template></el-table-column>
      </el-table>
      <el-pagination v-if="total>10" v-model:current-page="page" class="commerce-pager" :page-size="10" :total="total" layout="total, prev, pager, next" @current-change="loadCampaigns" />
      <template #footer><el-button @click="visible=false">完成</el-button></template>
    </el-dialog>
  </section>
</template>
<script setup>
import { onMounted,ref } from 'vue'
import { ElMessage } from 'element-plus'
import { carouselSettings,saveCarouselSettings,selectCarouselCampaign,reviewList } from '@/api/merchant'
const settings=ref({officialEnabled:true,intervalSeconds:5,revision:0})
const loading=ref(true),saving=ref(false),visible=ref(false),rows=ref([]),total=ref(0),page=ref(1),listLoading=ref(false),changing=ref(null)
async function load(){loading.value=true;try{const r=await carouselSettings();settings.value={...r.data,officialEnabled:Boolean(r.data.officialEnabled)}}catch{}finally{loading.value=false}}
async function save(){if(saving.value||loading.value)return;saving.value=true;try{const r=await saveCarouselSettings(settings.value);settings.value={...r.data,officialEnabled:Boolean(r.data.officialEnabled)};ElMessage.success('轮播设置已保存')}catch{await load()}finally{saving.value=false}}
async function loadCampaigns(){listLoading.value=true;try{const r=await reviewList('campaigns',{status:'APPROVED',page:page.value,size:10});rows.value=r.data.list;total.value=r.data.total}catch{}finally{listLoading.value=false}}
function openCampaigns(){visible.value=true;page.value=1;loadCampaigns()}
async function select(row,enabled){if(changing.value!==null)return;changing.value=row.id;try{await selectCarouselCampaign(row.id,{enabled,revision:row.revision});ElMessage.success(enabled?'已加入首页轮播':'已移出首页轮播')}catch{}finally{await loadCampaigns();changing.value=null}}
onMounted(load)
</script>
<style scoped>
.carousel-manager{margin-bottom:26px}.carousel-title{display:flex;justify-content:space-between;align-items:center;gap:20px;margin-bottom:24px}.carousel-title h2{display:flex;align-items:center;gap:9px;margin:6px 0 10px;font-size:20px;color:#353451}.carousel-title p:not(.eyebrow){font-size:12px;line-height:1.8;color:#8b8aa5;margin:0}.carousel-options{display:flex;align-items:center;gap:28px;padding:20px;border-radius:14px;background:#f7f7fc}.official-option{display:flex;gap:14px;align-items:center;flex:1}.official-icon{display:grid;place-items:center;width:45px;height:45px;border-radius:13px;background:#eae8fc;color:#7771c8;font-size:23px;flex-shrink:0}.official-option strong{font-size:13px;color:#45425f}.official-option p{font-size:11px;color:#9290a7;margin:8px 0 0;line-height:1.6}.official-option .el-switch{margin-left:auto}.interval-option{border-left:1px solid #e4e2f0;padding-left:28px}.interval-option label{display:block;font-size:12px;color:#6b6785;margin-bottom:8px}.interval-option>div{display:flex;align-items:center;gap:10px;font-size:12px;color:#86829f}.interval-option small{display:block;margin-top:6px;font-size:10px;color:#9691ac}.campaign-help{margin:0 0 20px;font-size:12px;color:#8b87a5;line-height:1.8}.carousel-candidate{display:flex;gap:13px;align-items:center}.carousel-candidate img{width:70px;height:55px;object-fit:cover;border-radius:8px}.carousel-candidate strong{font-size:13px}.carousel-candidate small{display:block;margin-top:7px;color:#9490ac;font-size:11px}@media(max-width:1000px){.carousel-options{flex-wrap:wrap}.official-option{flex-basis:100%}.interval-option{padding-left:0;border:0}}@media(max-width:600px){.carousel-title{align-items:flex-start;flex-direction:column}.carousel-options{gap:18px;padding:15px}.official-icon{display:none}.official-option{gap:8px}}
</style>

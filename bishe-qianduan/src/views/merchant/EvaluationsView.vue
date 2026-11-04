<template>
  <div class="commerce-page"><header class="commerce-heading"><div><p class="eyebrow">EVERY VOICE MATTERS</p><h1>买家评价 <span>{{ total }}</span></h1><p class="intro">每一份真实的反馈，都是让店铺更好的方向。</p></div><el-button size="large" @click="load">刷新评价</el-button></header><section v-loading="loading" class="evaluation-grid"><article v-for="r in rows" :key="r.id" class="surface review-card"><div class="review-card-top"><span class="review-icon"><i class="ph ph-chat-circle-text" /></span><div><strong>{{ r.productTitle }}</strong><small>订单 {{ r.orderId }}</small></div><el-rate :model-value="r.rating" disabled /></div><p>{{ r.content || '买家未填写文字评价' }}</p><footer><span>来自已购买用户</span><time>{{ String(r.createdAt||'').replace('T',' ').slice(0,16) }}</time></footer></article><div v-if="!loading&&!rows.length" class="surface"><el-empty description="评价还在路上，先把每一份商品和服务做好" /></div></section><el-pagination v-if="total>12" v-model:current-page="page" class="commerce-pager" :page-size="12" :total="total" layout="total, prev, pager, next" @current-change="load" /></div>
</template>
<script setup>
import { ref,onMounted } from 'vue'
import { merchantList } from '@/api/merchant'
const rows=ref([]),total=ref(0),page=ref(1),loading=ref(false)
async function load(){loading.value=true;try{const r=await merchantList('evaluations',{page:page.value,size:12});rows.value=r.data.list;total.value=r.data.total}catch{}finally{loading.value=false}}
onMounted(load)
</script>
<style scoped>
h1>span{font-size:14px;font-weight:400;letter-spacing:0;margin-left:10px;color:#9fa58f}.evaluation-grid{display:grid;gap:18px}.review-card-top{display:flex;gap:14px;align-items:center}.review-icon{display:grid;place-items:center;width:42px;height:42px;border-radius:50%;background:#eff1e4;color:#94a276;font-size:22px}.review-card-top strong{font-size:14px}.review-card-top small{display:block;color:#a1aa90;font-size:11px;margin-top:6px}.review-card-top .el-rate{margin-left:auto;--el-rate-fill-color:#c6a365}.review-card>p{padding:12px 0 14px 56px;color:#68795d;line-height:1.9;font-size:13px;white-space:pre-wrap}.review-card footer{display:flex;justify-content:space-between;font-size:11px;color:#a1a98e;border-top:1px solid #edf0e5;padding-top:14px}@media(max-width:600px){.review-card-top{flex-wrap:wrap}.review-card-top .el-rate{margin-left:56px}.review-card>p{padding-left:0}}
</style>

<template>
  <section v-if="slides.length" class="campaign-carousel" aria-label="合作商家广告" @touchstart.passive="touchStart" @touchend.passive="touchEnd">
    <div class="carousel-heading"><span><i class="ph ph-sparkle" /> 校园好物 · 周边商城 <small>广告</small></span><button type="button" :aria-label="autoplay?'暂停自动轮播':'开启自动轮播'" @click="autoplay=!autoplay"><i :class="autoplay?'ph ph-pause':'ph ph-play'" /> {{ autoplay?'暂停轮播':'自动轮播' }}</button></div>
    <el-carousel ref="carousel" height="266px" :interval="intervalSeconds*1000" :autoplay="autoplay" trigger="click" arrow="hover" :pause-on-hover="true" indicator-position="outside">
      <el-carousel-item v-for="(ad,i) in slides" :key="ad.id"><AdBanner v-if="ad.system" class="system-carousel-banner" @click="router.push(shopPath)" @action="router.push(shopPath)" /><RouterLink v-else :to="ad.targetType==='MERCHANT'?`${shopPath}/merchant/${ad.merchantId}`:productPath(ad.productId)" class="campaign-slide" :class="`slide-tone-${i%3}`"><div class="campaign-copy"><span class="merchant-name"><i class="ph ph-storefront" /> {{ ad.merchantName }}</span><h3>{{ ad.title }}</h3><p>{{ ad.content }}</p><span class="campaign-cta">{{ ad.targetType==='MERCHANT'?'逛逛这家店':'发现这份好物' }} <i class="ph ph-arrow-right" /></span></div><div class="campaign-picture"><img :src="ad.imageUrl" :alt="ad.title" loading="lazy" /></div></RouterLink></el-carousel-item>
    </el-carousel>
  </section>
</template>
<script setup>
import { computed,ref,onMounted,onUnmounted } from 'vue'
import AdBanner from '@/components/AdBanner.vue'
import { useRouter } from 'vue-router'
import { publicCarousel } from '@/api/merchant'
import { getRole } from '@/utils/auth'
const ads=ref([]),carousel=ref(),autoplay=ref(true),officialEnabled=ref(false),intervalSeconds=ref(5)
const router=useRouter(),shopPath=`/${getRole().toLowerCase()}/shop`
const slides=computed(()=>[...(officialEnabled.value?[{id:'system',system:true}]:[]),...ads.value])
let timer,startX=0,alive=true,busy=false
const productPath=id=>`/${getRole().toLowerCase()}/shop/product/${id}`
function touchStart(e){startX=e.changedTouches[0].clientX}
function touchEnd(e){const delta=e.changedTouches[0].clientX-startX;if(Math.abs(delta)>50){if(delta<0)carousel.value?.next();else carousel.value?.prev()}}
async function load(){if(document.hidden||busy)return;busy=true;try{const r=await publicCarousel();if(alive){ads.value=r.data.campaigns||[];officialEnabled.value=Boolean(r.data.officialEnabled);intervalSeconds.value=r.data.intervalSeconds||5}}catch{}finally{busy=false}}
onMounted(()=>{load();timer=setInterval(load,60000);window.addEventListener('focus',load)})
onUnmounted(()=>{alive=false;clearInterval(timer);window.removeEventListener('focus',load)})
</script>
<style scoped>
.campaign-carousel{margin:0 0 26px}.system-carousel-banner{height:100%;border-radius:20px;box-shadow:none;display:flex;align-items:center;padding:28px 56px}.carousel-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:14px}.carousel-heading>span{display:flex;align-items:center;gap:8px;font-size:13px;font-weight:600;color:#677b6a}.carousel-heading small{font-weight:400;font-size:9px;padding:2px 5px;color:#a5ae9b;border:1px solid #dce1d5;border-radius:3px;margin-left:3px}.carousel-heading button{display:flex;align-items:center;gap:6px;border:0;background:transparent;font-size:11px;color:#9aa68e;cursor:pointer}.campaign-slide{height:100%;display:flex;overflow:hidden;border-radius:20px;background:#e7ecdf;isolation:isolate}.campaign-copy{display:flex;flex-direction:column;justify-content:center;padding:28px 56px;width:56%;position:relative;z-index:1}.merchant-name{display:flex;gap:7px;align-items:center;font-size:10px;letter-spacing:1px;color:#7d9271}.campaign-copy h3{font-size:27px;color:#3c5438;margin:14px 0 10px;line-height:1.3;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.campaign-copy p{font-size:12px;line-height:1.9;color:#85947a;margin:0;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.campaign-cta{display:flex;align-items:center;gap:18px;font-size:11px;font-weight:600;color:#5e7c51;margin-top:22px}.campaign-picture{width:44%;position:relative}.campaign-picture:after{content:'';position:absolute;inset:0;background:linear-gradient(90deg,#e7ecdf,transparent 24%);pointer-events:none}.campaign-picture img{height:100%;width:100%;object-fit:cover}.slide-tone-1{background:#eee6da}.slide-tone-1 .campaign-copy h3{color:#6b5139}.slide-tone-1 .campaign-picture:after{background:linear-gradient(90deg,#eee6da,transparent 24%)}.slide-tone-2{background:#e2e9ed}.slide-tone-2 .campaign-copy h3{color:#405d69}.slide-tone-2 .campaign-picture:after{background:linear-gradient(90deg,#e2e9ed,transparent 24%)}.campaign-carousel :deep(.el-carousel__arrow){background:#fff9;color:#738769}.campaign-carousel :deep(.el-carousel__button){background:#9aa98b;border-radius:3px;height:3px;width:24px}.campaign-carousel :deep(.el-carousel__indicators--outside){padding-top:8px}@media(max-width:850px){.campaign-copy{padding:25px 40px;width:60%}.campaign-picture{width:40%}.campaign-copy h3{font-size:23px}}@media(max-width:600px){.campaign-copy{width:70%;padding:25px 35px}.campaign-picture{width:30%}.campaign-copy h3{font-size:20px}.carousel-heading>span{font-size:11px}.carousel-heading button{font-size:10px}}
</style>

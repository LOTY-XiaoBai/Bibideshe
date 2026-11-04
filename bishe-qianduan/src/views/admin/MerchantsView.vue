<template>
  <div class="commerce-page admin-commerce"><header class="commerce-heading"><div><p class="eyebrow">CAMPUS PARTNERS</p><h1>合作方商家</h1><p class="intro">邀请值得信赖的合作伙伴，一起丰富校园生活。</p></div><el-button type="primary" size="large" :loading="creating" @click="invite"><i class="ph ph-plus" /> 新增商家</el-button></header><div class="partner-explainer"><i class="ph ph-ticket" /><div><strong>邀请入驻 · 首次登录即激活</strong><p>管理员生成16位邀请码，商家使用邀请码和初始密码 123456 登录。12小时未登录自动销毁；首次成功登录后账号永久保留。</p></div></div><section class="surface" v-loading="loading"><div class="table-heading"><h3>当前合作伙伴 <span>{{ total }}</span></h3><el-button text @click="load">刷新列表</el-button></div><el-table :data="rows" empty-text="还没有合作商家，点击“新增商家”发送第一份邀请"><el-table-column label="商家" min-width="175"><template #default="{row}"><div class="partner-name"><el-avatar :size="36" :src="row.avatar || undefined">{{ (row.name||'待').slice(0,1) }}</el-avatar><div><strong>{{ row.name||'等待商家完善名称' }}</strong><small>{{ row.city||'尚未填写发货城市' }}</small></div></div></template></el-table-column><el-table-column label="邀请码 / 登录账号" min-width="190"><template #default="{row}"><code>{{ row.accountCode }}</code><el-button text size="small" @click="copy(row.accountCode)"><i class="ph ph-copy" /></el-button></template></el-table-column><el-table-column label="账号状态" width="160"><template #default="{row}"><span class="partner-state" :class="{active:row.activatedAt&&!Number(row.isBanned),banned:Number(row.isBanned)}">{{ Number(row.isBanned)?'已封禁':row.activatedAt ? Number(row.mustChangePassword)?'已激活 · 待完善入驻':'已激活 · 永久有效' : '等待首次登录' }}</span><p v-if="Number(row.isBanned)" class="ban-reason">{{ row.banReason }}<br />{{ row.banUntil?'至 '+time(row.banUntil):'永久封禁' }}</p></template></el-table-column><el-table-column label="创建时间" min-width="160"><template #default="{row}">{{ time(row.createdAt) }}</template></el-table-column><el-table-column label="邀请截止 / 激活时间" min-width="165"><template #default="{row}"><span :class="{expiry:!row.activatedAt}">{{ time(row.activatedAt||row.expiresAt) }}</span></template></el-table-column><el-table-column label="操作" width="105" fixed="right"><template #default="{row}"><el-button text :type="Number(row.isBanned)?'primary':'danger'" :loading="changing===row.merchantId" @click="toggleBan(row)">{{ Number(row.isBanned)?'解除封禁':'封禁商家' }}</el-button></template></el-table-column></el-table><el-pagination v-if="total>12" v-model:current-page="page" class="commerce-pager" :page-size="12" :total="total" layout="total, prev, pager, next" @current-change="load" /></section>
    <el-dialog v-model="visible" title="一份新的合作邀请" width="510px" class="commerce-dialog admin-commerce-dialog" :close-on-click-modal="false"><div v-if="created" class="invitation-card"><div class="invite-icon"><i class="ph ph-handshake" /></div><p>请将以下信息发送给合作商家</p><span class="invite-label">专属邀请码 / 登录账号</span><strong class="invite-code">{{ created.accountCode }}</strong><div class="invite-details"><span>初始密码 <b>123456</b></span><span>有效期至 <b>{{ time(created.expiresAt) }}</b></span></div><p class="invite-note">首次成功登录后永久激活，进入后需修改密码并设置发货城市。</p><el-button type="primary" size="large" @click="copyInvitation">复制完整邀请信息</el-button></div><template #footer><el-button @click="visible=false">完成</el-button></template></el-dialog>
    <el-dialog v-model="banVisible" title="封禁合作商家" width="480px" class="merchant-ban-dialog" align-center :close-on-click-modal="false" :before-close="closeBan">
      <p class="ban-dialog-description">封禁「{{ banTarget?.name || banTarget?.accountCode }}」后，其商品全部下架、广告撤销，订单信息保留。</p>
      <el-form ref="banFormRef" :model="banForm" :rules="banRules" label-position="top" size="large">
        <el-form-item label="封禁时间" prop="duration"><el-select v-model="banForm.duration" style="width:100%"><el-option v-for="option in durations" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item>
        <el-form-item label="封禁原因" prop="reason"><el-input v-model="banForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请填写具体原因，商家登录时会收到封禁通知" /></el-form-item>
      </el-form><p class="ban-dialog-hint">限时封禁到期后可重新登录；商品和广告仍需重新提交审核。</p>
      <template #footer><el-button :disabled="changing!==null" @click="closeBan()">取消</el-button><el-button type="primary" :loading="changing!==null" @click="confirmBan">确认封禁</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup>
import { onMounted,onUnmounted,reactive,ref } from 'vue'
import { ElMessage,ElMessageBox } from 'element-plus'
import { listMerchants,inviteMerchant,setMerchantBanned } from '@/api/merchant'
const rows=ref([]),total=ref(0),page=ref(1),loading=ref(false),creating=ref(false),visible=ref(false),created=ref(null)
const changing=ref(null)
let timer
const time=v=>v?String(v).replace('T',' ').slice(0,16):'—'
async function load(){if(loading.value)return;loading.value=true;try{const r=await listMerchants({page:page.value,size:12});rows.value=r.data.list;total.value=r.data.total;if(!rows.value.length&&page.value>1)page.value=1}catch{}finally{loading.value=false}}
async function invite(){if(creating.value)return;creating.value=true;try{created.value=(await inviteMerchant()).data;visible.value=true;await load()}catch{}finally{creating.value=false}}
async function copy(text){try{await navigator.clipboard.writeText(text);ElMessage.success('已复制')}catch{ElMessage.info('浏览器未开放复制权限，请选中邀请码手动复制')}}
function copyInvitation(){copy(`校园合作商家邀请\n登录地址：${window.location.origin}/login\n邀请码 / 登录账号：${created.value.accountCode}\n初始密码：123456\n首次登录截止：${time(created.value.expiresAt)}（北京时间）\n首次登录后账号永久有效，请立即修改初始密码并设置发货城市。`)}
const banVisible=ref(false),banTarget=ref(null),banFormRef=ref()
const banForm=reactive({duration:'DAY_1',reason:''})
const durations=[{value:'DAY_1',label:'1天'},{value:'DAY_3',label:'3天'},{value:'WEEK_2',label:'2周'},{value:'MONTH_1',label:'1个月'},{value:'MONTH_3',label:'3个月'},{value:'YEAR_10',label:'10年'},{value:'PERMANENT',label:'永久'}]
const banRules={duration:[{required:true,message:'请选择封禁时间',trigger:'change'}],reason:[{validator:(_r,v,done)=>v?.trim()?done():done(new Error('请填写封禁原因')),trigger:'blur'}]}
function closeBan(done){if(changing.value!==null)return;if(typeof done==='function')done();else banVisible.value=false}
async function toggleBan(row){
 if(changing.value!==null)return
 if(!Number(row.isBanned)){banTarget.value=row;banForm.duration='DAY_1';banForm.reason='';banVisible.value=true;return}
 const ok=await ElMessageBox.confirm('解禁后商家可重新登录，已下架商品和已撤销广告需要重新提交审核。','解除封禁',{confirmButtonText:'确认解禁',cancelButtonText:'取消',customClass:'merchant-unban-box'}).then(()=>true).catch(()=>false);if(!ok)return
 changing.value=row.merchantId;try{await setMerchantBanned(row.merchantId,false,'');ElMessage.success('已解除封禁');await load()}catch{}finally{changing.value=null}
}
async function confirmBan(){
 if(changing.value!==null)return
 if(!await banFormRef.value.validate().catch(()=>false))return
 changing.value=banTarget.value.merchantId
 try{await setMerchantBanned(banTarget.value.merchantId,true,banForm.reason.trim(),banForm.duration);ElMessage.success('商家已封禁，商品已下架、广告已撤销');banVisible.value=false;await load()}catch{}finally{changing.value=null}
}
onMounted(()=>{load();timer=setInterval(load,60000)})
onUnmounted(()=>clearInterval(timer))
</script>
<style scoped>
:global(.merchant-ban-dialog){width:min(480px,calc(100vw - 32px))!important;max-width:480px;border-radius:18px;padding:24px;--el-color-primary:#9999ff;--el-color-primary-light-9:#f5f5ff}
:global(.merchant-unban-box){width:420px!important;max-width:calc(100vw - 32px);border-radius:16px;--el-color-primary:#9999ff}
.ban-dialog-description{font-size:13px;line-height:1.9;color:#85829b;margin:0 0 20px}.ban-dialog-hint{font-size:11px;line-height:1.8;color:#aaa6bc;margin:0}

.partner-state.banned{background:#fff0f0;color:#ca6e72}.ban-reason{font-size:11px;color:#b98b90;line-height:1.7;overflow-wrap:anywhere;margin:6px 0 0}.partner-name :deep(.el-avatar){background:#efefff;color:#9e9bcb;flex-shrink:0}
.commerce-heading .el-button i{margin-right:8px}.partner-explainer{display:flex;align-items:center;gap:18px;padding:22px 26px;background:#e5e4f0;border:1px solid #d5d4e6;border-radius:16px;margin-bottom:25px}.partner-explainer>i{font-size:30px;color:#6e6c9b}.partner-explainer strong{font-size:14px;color:#4a4873}.partner-explainer p{font-size:12px;color:#7f7e9b;margin:8px 0 0;line-height:1.9}.table-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:18px}.table-heading h3{font-size:15px;margin:0}.table-heading h3>span{font-size:12px;color:#9190ab;font-weight:400;margin-left:8px}.partner-name{display:flex;gap:12px;align-items:center}.partner-name>span{width:36px;height:36px;display:grid;place-items:center;border-radius:11px;background:#e4e3f0;color:#716f9b}.partner-name strong{font-size:13px}.partner-name small{display:block;font-size:11px;color:#8f8ea8;margin-top:5px}code{font-size:12px;letter-spacing:.5px;color:#6c6b87}.partner-state{font-size:11px;padding:6px 9px;border-radius:6px;background:#dfdef9;color:#5d59b0}.partner-state.active{background:#eae9f4;color:#6a6891}.expiry{color:#6e6ab3;font-size:12px}.invitation-card{text-align:center;padding:20px 10px}.invite-icon{display:grid;place-items:center;width:64px;height:64px;border-radius:50%;background:#e5e4f1;color:#6e6c97;font-size:30px;margin:auto}.invitation-card>p{color:#8e8da4;font-size:12px;margin:20px 0 25px}.invite-label{display:block;font-size:11px;letter-spacing:1px;color:#8e8da6}.invite-code{display:block;letter-spacing:2px;font-size:24px;font-family:Consolas,monospace;margin:15px 0 24px;color:#42406c;overflow-wrap:anywhere;user-select:all}.invite-details{display:grid;gap:12px;background:#eeeef6;padding:18px;border-radius:12px}.invite-details span{display:flex;justify-content:space-between;font-size:12px;color:#9897ac}.invite-details b{color:#636189;font-weight:500}.invitation-card .invite-note{font-size:11px;line-height:1.9}.invitation-card .el-button{width:100%}
</style>

<template>
<section v-loading="busy" class="appeals">
  <h3>{{ admin?'申诉审核':'我的申诉' }} <CountBadge v-if="admin" :count="unread.data.appeals"/></h3>
  <template v-if="!admin"><p>如对举报处置有异议，请说明事实、理由和相关证据，至少100字。</p><article v-for="r in eligible" :key="r.operation_id?'operation:'+r.operation_id:'report:'+r.id"><strong>{{ r.post_title }}</strong><p>处置说明：{{ r.feedback }}</p><el-button @click="start(r)">提交申诉</el-button></article></template>
  <article v-for="a in rows" :key="a.id"><div class="heading"><strong>{{ a.post_title }}</strong><el-tag>{{ names[a.status] }}</el-tag></div><p>{{ a.owner }} · {{ displayTime(a.created_at) }}</p><p class="content">{{ a.content }}</p><p v-if="a.feedback">处理结果：{{ a.feedback }}</p><el-button text @click="router.push({path:'/community/post/'+a.post_id,query:{returnTo:route.fullPath}})">查看帖子</el-button><el-button v-if="admin&&a.status==='PENDING'" type="primary" @click="start(a)">审核申诉</el-button></article>
  <el-empty v-if="!rows.length&&!eligible.length&&!busy" description="暂无申诉记录"/>
  <ListPager v-model:page="page" v-model:size="size" :total="total" @change="load"/>
  <el-dialog v-model="visible" :title="admin?'审核申诉':'提交申诉'" width="min(600px,94vw)"><p>{{ current?.post_title }}</p><template v-if="admin"><el-select v-model="decision"><el-option label="申诉通过" value="APPROVED"/><el-option label="驳回申诉" value="REJECTED"/></el-select><p>通过后撤销本次举报处置；无其他有效举报处置时恢复隐藏帖子，并解除本次附加禁言。删除的帖子保留删除状态。</p></template><el-input v-model="content" type="textarea" :rows="7" :maxlength="admin?500:3000" show-word-limit :placeholder="admin?'填写审核依据':'请填写100至3000字申诉说明'"/><template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="admin?!content.trim():Array.from(content.trim()).length<100" @click="submit">确认提交</el-button></template></el-dialog>
</section>
</template>
<script setup>
import {ref,onMounted,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {getRole} from '@/utils/auth'
import request from '@/api/request'
import {displayTime} from '@/utils/community'
import {useUnread} from '@/stores/unread'
import {ElMessage} from 'element-plus'
import CountBadge from './CountBadge.vue'
import ListPager from './ListPager.vue'
const route=useRoute(),router=useRouter(),unread=useUnread(),admin=getRole()==='ADMIN'
const rows=ref([]),eligible=ref([]),page=ref(Math.max(1,Number(route.query.appealPage)||1)),size=ref([5,10,20,50].includes(Number(route.query.appealSize))?Number(route.query.appealSize):20),total=ref(0),busy=ref(false),visible=ref(false),current=ref(),content=ref(''),decision=ref('APPROVED'),saving=ref(false)
const names={PENDING:'待审核',APPROVED:'已通过',REJECTED:'已驳回'}
async function load(){busy.value=true;try{const r=(await request.get('/api/community/appeals',{params:{page:page.value,size:size.value}})).data;rows.value=r.list;total.value=r.total;if(!admin)eligible.value=(await request.get('/api/community/appealable')).data}catch{}finally{busy.value=false}}
function start(r){current.value=r;content.value='';decision.value='APPROVED';visible.value=true}
async function submit(){saving.value=true;try{await request.post(admin?'/api/community/appeals/'+current.value.id+'/resolve':current.value.operation_id?'/api/community/operations/'+current.value.operation_id+'/appeal':'/api/community/reports/'+current.value.id+'/appeal',admin?{decision:decision.value,feedback:content.value}:{content:content.value});visible.value=false;ElMessage.success('已提交');await load()}catch{}finally{saving.value=false}}
watch([page,size],()=>router.replace({query:{...route.query,appealPage:page.value,appealSize:size.value}}))
onMounted(load)
</script>
<style scoped>.appeals{padding:20px 0;color:#53627b}.appeals>p,article>p{font-size:13px;line-height:1.9}.heading{display:flex;justify-content:space-between;gap:16px}article{padding:20px 0;border-bottom:1px solid #edf0f7}.content{white-space:pre-wrap;overflow-wrap:anywhere;background:#f7f9fc;padding:14px}.el-select{margin-bottom:16px}</style>

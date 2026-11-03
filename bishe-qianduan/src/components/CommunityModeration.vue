<template>
  <section class="moderation" v-loading="busy">
    <header><div><h3>{{ fullPage ? '社区用户管理' : '禁言与警告' }}</h3><p>警告每满24小时减1；超过3次自动禁言24小时。解禁与警告清零分别处理。</p></div><el-button text @click="load">刷新</el-button></header>
    <div v-if="!fullPage" class="moderation-box"><SharedWordLibrary /></div>
    <div class="moderation-box">
      <h4>社区用户管理</h4>
      <div class="user-form">
        <el-select v-model="role" style="width:110px" @change="filter"><el-option label="全部" value="ALL"/><el-option label="学生" value="STUDENT"/><el-option label="教师" value="TEACHER"/></el-select>
        <el-input v-model="userId" placeholder="用户ID" class="user-id"/><el-select v-model="action" style="width:160px"><el-option label="发送警告" value="WARN"/><el-option label="禁言24小时" value="MUTE"/></el-select>
      </div>
      <div class="word-form"><el-input v-model="reason" maxlength="500" placeholder="填写处理原因，会通知用户并留下操作记录"/><el-button type="primary" :loading="saving" @click="handle">确认处理</el-button></div>
      <div v-for="u in users" :key="u.owner" class="user-row"><div><strong>{{ u.owner }}</strong><p>{{ u.warnings }} 次警告 · {{ muted(u) ? '禁言至 '+displayTime(u.muted_until) : '未禁言' }}</p></div><div><el-button plain size="small" :disabled="!muted(u)" @click="process(u,'unmute')">解禁</el-button><el-button plain size="small" :disabled="!u.warnings" @click="process(u,'clear-warnings')">警告清零</el-button></div></div>
      <el-empty v-if="!users.length&&!busy" description="暂无社区警告或禁言记录" :image-size="60"/>
      <ListPager v-if="fullPage" v-model:page="page" v-model:size="size" :total="total" @change="load"/>
      <el-button v-else-if="total>5" text @click="router.push({path:'/admin/community-users',query:{role}})">查看更多（共 {{ total }} 条）</el-button>
    </div>
  </section>
</template>
<script setup>
import {ref,onMounted,onUnmounted,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import request from '@/api/request'
import {displayTime} from '@/utils/community'
import SharedWordLibrary from './SharedWordLibrary.vue'
import ListPager from './ListPager.vue'
const props=defineProps({fullPage:Boolean}),route=useRoute(),router=useRouter()
const users=ref([]),busy=ref(false),saving=ref(false),role=ref(['ALL','STUDENT','TEACHER'].includes(route.query.role)?route.query.role:'ALL'),page=ref(Math.max(1,Number(route.query.page)||1)),size=ref([5,10,20,50].includes(Number(route.query.size))?Number(route.query.size):20),total=ref(0),userId=ref(''),action=ref('WARN'),reason=ref('')
let generation=0
async function load(){const current=++generation;busy.value=true;try{const r=(await request.get('/api/community/mutes',{params:{role:role.value,page:props.fullPage?page.value:1,size:props.fullPage?size.value:5}})).data;if(current===generation){users.value=r.list;total.value=r.total}}catch{}finally{if(current===generation)busy.value=false}}
function filter(){page.value=1;load()}
watch([role,page,size],()=>{if(props.fullPage)router.replace({query:{role:role.value,page:page.value,size:size.value}})})
async function handle(){if(!/^\d+$/.test(userId.value)||!reason.value.trim()){ElMessage.warning('请填写用户ID和处理原因');return}saving.value=true;try{await request.post('/api/community/moderate-user',{owner:role.value+':'+userId.value,action:action.value,reason:reason.value});reason.value='';ElMessage.success('已处理并记录操作');await load()}catch{}finally{saving.value=false}}
async function process(u,action){try{const clear=action==='clear-warnings';await ElMessageBox.confirm(clear?'清零该用户的社区警告次数？禁言状态将保留。':'解除该用户的社区禁言？警告次数将保留。',clear?'警告清零':'解除禁言',{confirmButtonText:'确认',cancelButtonText:'取消'});await request.post('/api/community/'+action,{owner:u.owner});ElMessage.success('已处理并记录操作');await load()}catch{}}
function muted(u){return u.muted_until&&new Date(u.muted_until).getTime()>Date.now()}
onMounted(()=>{load();window.addEventListener('focus',load)})
onUnmounted(()=>{generation++;window.removeEventListener('focus',load)})
</script>
<style scoped>
.moderation {
  padding-top: 20px;
  color: #53627b;
}
header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
}
h3 {
  margin: 0;
  font-size: 18px;
}
p {
  font-size: 12px;
  color: #96a1b4;
  line-height: 1.8;
}
h4 {
  font-size: 14px;
  margin: 0 0 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}
h4 i {
  color: #8198ce;
  font-size: 20px;
}
.moderation-box {
  background: #fafbfe;
  border: 1px solid #edf0f7;
  border-radius: 14px;
  margin-top: 20px;
  padding: 22px;
}
.word-form,
.user-form {
  display: flex;
  gap: 10px;
  margin: 15px 0;
}
.user-form {
  flex-wrap: wrap;
}
.user-id {
  width: 200px;
}
.word-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 20px;
}
.word-chip {
  display: flex;
  align-items: center;
  gap: 9px;
  background: #edf3ff;
  border: 1px solid #e4eafa;
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 12px;
}
.word-chip.off {
  background: #f1f2f5;
  opacity: 0.7;
}
.word-chip b {
  font-weight: 500;
}
.word-chip button {
  border: 0;
  background: none;
  color: #8193b7;
  cursor: pointer;
  font-size: 11px;
}
.user-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid #eaf0f8;
  padding: 18px 0;
  font-size: 13px;
}
.user-row p {
  margin: 5px 0 0;
}
</style>

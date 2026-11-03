<template>
<section v-loading="busy" class="word-library">
  <h4>共用屏蔽词 <small>共 {{ words.length }} 个</small></h4>
  <p>社区和聊天使用同一份词库，修改后同步生效。</p>
  <div class="word-form"><el-input v-model="word" maxlength="100" placeholder="添加共用屏蔽词" @keyup.enter="add"/><el-button type="primary" :loading="saving" @click="add">添加</el-button></div>
  <div class="word-list"><span v-for="w in words.slice(0,40)" :key="w.wordId" class="word-chip"><b>{{ w.word }}</b><el-button link size="small" @click="toggle(w)">{{ w.enabled ? '停用' : '启用' }}</el-button><el-button link type="danger" size="small" @click="remove(w)">删除</el-button></span></div>
  <el-button v-if="words.length>40" class="all-words" @click="showAll=true">查看所有屏蔽词（{{ words.length }}）</el-button>
  <el-empty v-if="!words.length" description="暂无屏蔽词" :image-size="55"/>
  <el-dialog v-model="showAll" title="所有屏蔽词" width="min(800px,94vw)">
    <el-input v-model="search" clearable placeholder="搜索已有屏蔽词" />
    <div class="word-form"><el-input v-model="word" maxlength="100" placeholder="添加共用屏蔽词" @keyup.enter="add"/><el-button type="primary" :loading="saving" @click="add">添加</el-button></div>
    <el-table :data="filtered" max-height="420"><el-table-column prop="word" label="屏蔽词"/><el-table-column label="状态"><template #default="{row}">{{ row.enabled ? '已启用' : '已停用' }}</template></el-table-column><el-table-column label="操作"><template #default="{row}"><el-button link @click="toggle(row)">{{ row.enabled ? '停用' : '启用' }}</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column></el-table>
    <p>匹配 {{ filtered.length }} 个 / 共 {{ words.length }} 个</p>
  </el-dialog>
</section>
</template>
<script setup>
import {ref,computed,onMounted,onUnmounted} from 'vue'
import {ElMessageBox} from 'element-plus'
import {fetchSpecialWords,addSpecialWord,enableSpecialWord,deleteSpecialWord} from '@/api/admin'
const words=ref([]),word=ref(''),search=ref(''),busy=ref(false),saving=ref(false),showAll=ref(false)
const filtered=computed(()=>words.value.filter(w=>w.word.toLowerCase().includes(search.value.trim().toLowerCase())))
async function load(){busy.value=true;try{words.value=(await fetchSpecialWords()).data||[]}catch{}finally{busy.value=false}}
async function add(){if(!word.value.trim()||saving.value)return;saving.value=true;try{await addSpecialWord({word:word.value.trim(),enabled:1});word.value='';await load()}catch{}finally{saving.value=false}}
async function toggle(w){try{await enableSpecialWord(w.wordId,w.enabled?0:1);await load()}catch{}}
async function remove(w){try{await ElMessageBox.confirm('删除后社区和聊天均不再检测此词，是否继续？','删除共用屏蔽词',{type:'warning',confirmButtonText:'删除',cancelButtonText:'取消'});await deleteSpecialWord(w.wordId);await load()}catch{}}
onMounted(()=>{load();window.addEventListener('focus',load)})
onUnmounted(()=>window.removeEventListener('focus',load))
</script>
<style scoped>
p,small{color:#96a1b4;font-size:12px}h4{margin:0}.word-form{display:flex;gap:10px;margin:15px 0}.word-list{display:flex;flex-wrap:wrap;gap:8px}.word-chip{display:inline-flex;align-items:center;gap:8px;padding:5px 10px;background:#edf3ff;border-radius:8px;font-size:12px}.word-chip b{font-weight:500}.all-words{margin-top:16px}
</style>

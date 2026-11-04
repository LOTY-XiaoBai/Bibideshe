<template>
  <div class="study-question-paper">
    <div v-if="canCollect" class="collection-tools"><el-button :disabled="!answersVisible" :type="selecting ? 'default' : 'primary'" plain @click="toggle">{{ selecting ? '取消加入' : '加入错题本' }}</el-button><template v-if="selecting"><el-checkbox :model-value="allSelected" :indeterminate="selected.length > 0 && !allSelected" @change="selectAll">全选</el-checkbox><span>已选 {{ selected.length }} 题</span><el-button type="primary" :disabled="!selected.length" :loading="saving" @click="collect">确认加入</el-button></template><span v-else-if="!answersVisible" class="collection-hint">答案公布后可加入错题本</span></div>
    <QuestionPaper v-model="answers" :questions="questions" :readonly="readonly" :review-items="reviewItems" :selection-mode="selecting" v-model:selected-keys="selected" />
  </div>
</template>
<script setup>
import {computed,ref,watch} from 'vue'
import {ElMessage} from 'element-plus'
import QuestionPaper from './QuestionPaper.vue'
import {addStudyMistakes} from '@/api/courseWorkspace'
const props=defineProps({questions:{type:Array,default:()=>[]},modelValue:{type:Object,default:()=>({})},reviewItems:{type:Array,default:()=>[]},readonly:Boolean,source:String,sourceId:[String,Number],answersVisible:{type:Boolean,default:true}})
const emit=defineEmits(['update:modelValue'])
const answers=computed({get:()=>props.modelValue,set:value=>emit('update:modelValue',value)})
const selecting=ref(false),selected=ref([]),saving=ref(false)
const selectable=computed(()=>props.questions.filter(i=>i.question&&!i.question.lost).map(i=>`${i.question.questionType}-${i.question.questionId}`))
const canCollect=computed(()=>props.readonly&&Object.values(props.modelValue).some(v=>String(v??'').trim()))
const allSelected=computed(()=>selectable.value.length>0&&selectable.value.every(k=>selected.value.includes(k)))
function toggle(){selecting.value=!selecting.value;if(!selecting.value)selected.value=[]}
function selectAll(value){selected.value=value?[...selectable.value]:[]}
async function collect(){saving.value=true;try{const refs=props.questions.filter(i=>selected.value.includes(`${i.question.questionType}-${i.question.questionId}`)).map(i=>({questionId:i.question.questionId,questionType:i.question.questionType}));const {data}=await addStudyMistakes(props.source,props.sourceId,refs);ElMessage.success(data.addedCount?`已加入 ${data.addedCount} 道题`:'所选题目已在错题本中')}catch{}finally{saving.value=false}}
watch(()=>props.sourceId,()=>{selecting.value=false;selected.value=[]})
watch(()=>props.readonly,value=>{if(!value){selecting.value=false;selected.value=[]}})
</script>
<style scoped>
.collection-tools { display:flex;gap:16px;align-items:center;flex-wrap:wrap;border:1px solid #e1edf5;border-radius:12px;padding:13px 16px;background:#f7fbff;margin:18px 0; }.collection-tools > span { font-size:13px;color:#8a98aa; }.collection-tools .collection-hint { font-size:12px; }
</style>

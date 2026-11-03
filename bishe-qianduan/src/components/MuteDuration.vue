<template><div class="mute-duration"><el-select v-model="preset" @change="update"><el-option v-for="o in options" :key="o[0]" :label="o[1]" :value="o[0]"/></el-select><template v-if="preset==='CUSTOM'"><el-input-number v-model="amount" :min="1" :max="5256000" @change="update"/><el-select v-model="unit" @change="update"><el-option label="分钟" :value="1"/><el-option label="小时" :value="60"/><el-option label="天" :value="1440"/></el-select></template></div></template>
<script setup>
import {ref} from 'vue'
defineProps({modelValue:{type:String,default:'60'}})
const emit=defineEmits(['update:modelValue']),preset=ref('60'),amount=ref(1),unit=ref(60)
const options=[['60','1小时'],['120','2小时'],['300','5小时'],['720','12小时'],['1440','1天'],['4320','3天'],['20160','2周'],['PERMANENT','永久'],['CUSTOM','自定义时间']]
function update(){emit('update:modelValue',preset.value==='CUSTOM'?String((amount.value||1)*unit.value):preset.value)}
</script>
<style scoped>.mute-duration{display:flex;gap:8px;flex-wrap:wrap;margin:10px 0}.mute-duration .el-select{width:145px}</style>

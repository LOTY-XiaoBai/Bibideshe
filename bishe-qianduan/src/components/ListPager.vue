<template><el-pagination class="list-pager" :current-page="page" :page-size="size" :page-sizes="[5,10,20,50]" :total="Number(total)" layout="total, prev, pager, next, sizes" @current-change="changePage" @size-change="changeSize" /></template>
<script setup>
const props=defineProps({page:{type:Number,default:1},size:{type:Number,default:20},total:{type:[Number,String],default:0}})
const emit=defineEmits(['update:page','update:size','change'])
function changePage(value){if(value===props.page)return;emit('update:page',value);emit('change')}
function changeSize(value){emit('update:page',1);emit('update:size',value);emit('change')}
</script>
<style scoped>.list-pager{justify-content:flex-end;flex-wrap:wrap;gap:8px;margin-top:20px}</style>

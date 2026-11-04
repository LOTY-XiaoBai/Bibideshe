<template>
  <el-upload class="commerce-upload" drag :show-file-list="false" accept="image/jpeg,image/png,image/webp,image/gif,image/bmp" :http-request="upload" :disabled="busy">
    <div v-if="modelValue" class="upload-preview"><img :src="modelValue" alt="封面预览" /><span>{{ busy ? '上传中…' : '点击或拖入图片更换封面' }}</span></div>
    <div v-else class="upload-empty"><i class="ph ph-image" /><strong>{{ busy ? '正在上传图片…' : '为你的内容添加一张封面' }}</strong><span>点击选择或拖入图片 · JPG / PNG / WebP / GIF / BMP · 最大 10 MB</span></div>
  </el-upload>
</template>
<script setup>
import { ref } from 'vue'
import { uploadImage } from '@/api/file'
const props = defineProps({ modelValue: String }), emit = defineEmits(['update:modelValue', 'busy'])
const busy = ref(false)
async function upload({ file, onSuccess, onError }) {
  busy.value = true; emit('busy', true)
  try { const r = await uploadImage(file); emit('update:modelValue', r.data.url); onSuccess(r) } catch (e) { onError(e) } finally { busy.value = false; emit('busy', false) }
}
</script>
<style scoped>
.commerce-upload{width:100%}.commerce-upload :deep(.el-upload){width:100%}.commerce-upload :deep(.el-upload-dragger){padding:0;border:1px dashed #c4d0c7;border-radius:16px;background:#f7f9f5;overflow:hidden}.upload-empty{min-height:190px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;padding:24px}.upload-empty i{font-size:32px;color:#6b896f}.upload-empty strong{font-size:14px;color:#385545}.upload-empty span{font-size:12px;line-height:1.8;color:#859085}.upload-preview{position:relative;height:230px}.upload-preview img{width:100%;height:100%;object-fit:contain;background:#f4f5ef}.upload-preview span{position:absolute;bottom:0;left:0;right:0;background:#203d32d9;color:white;padding:10px;font-size:12px}
</style>

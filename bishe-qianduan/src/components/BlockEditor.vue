<template>
  <div>
    <div v-for="(b, i) in blocks" :key="i" class="block">
      <el-input
        v-if="b.type === 'text'"
        v-model="b.value"
        type="textarea"
        :rows="4"
        maxlength="10000"
        placeholder="填写文字，可在段落间插入图片"
        @input="emitValue"
      /><img v-else :src="b.value" alt="已上传图片" /><el-button
        text
        type="danger"
        @click="
          blocks.splice(i, 1);
          emitValue();
        "
        >移除此段</el-button
      >
    </div>
    <el-button @click="blocks.push({ type: 'text', value: '' })"
      >添加文字段落</el-button
    ><el-upload
      :show-file-list="false"
      accept="image/jpeg,image/png,image/gif,image/webp"
      :http-request="upload"
      ><el-button :loading="uploading">插入图片</el-button></el-upload
    >
  </div>
</template>
<script setup>
import { ref, watch } from "vue";
import { uploadImage } from "@/api/file";
const props = defineProps({ modelValue: { type: String, default: "[]" } }),
  emit = defineEmits(["update:modelValue"]);
const blocks = ref([]),
  uploading = ref(false);
watch(
  () => props.modelValue,
  (v) => {
    try {
      if (v !== JSON.stringify(blocks.value)) blocks.value = JSON.parse(v);
    } catch {
      blocks.value = [];
    }
  },
  { immediate: true },
);
function emitValue() {
  emit("update:modelValue", JSON.stringify(blocks.value));
}
async function upload({ file, onSuccess, onError }) {
  uploading.value = true;
  try {
    const r = await uploadImage(file);
    blocks.value.push({ type: "image", value: r.data.url });
    emitValue();
    onSuccess(r);
  } catch (e) {
    onError(e);
  } finally {
    uploading.value = false;
  }
}
</script>
<style scoped>
.block {
  margin: 12px 0;
}
.block img {
  max-width: 100%;
  max-height: 220px;
}
.el-upload {
  display: inline-flex;
  margin: 12px;
}
</style>

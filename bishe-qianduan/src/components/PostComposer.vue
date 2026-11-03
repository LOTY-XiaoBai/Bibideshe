<template>
  <el-dialog
    v-model="visible"
    :title="postId ? '编辑帖子' : '发布新动态'"
    width="min(680px,94vw)"
    class="post-composer"
    :close-on-click-modal="false"
    ><p class="intro">分享你的想法，让校园生活多一点共鸣。</p>
    <el-input
      v-model="title"
      class="title-input"
      placeholder="给帖子起个标题（必填）"
      maxlength="120"
      show-word-limit
    /><el-input
      v-model="content"
      type="textarea"
      :autosize="{ minRows: 6, maxRows: 14 }"
      maxlength="10000"
      show-word-limit
      placeholder="想说点什么？正文可以留空，图片会展示在正文下方。"
    />
    <div class="photo-grid">
      <div v-for="(url, i) in images" :key="url" class="photo">
        <img :src="url" alt="待发布图片" /><button
          type="button"
          :aria-label="'移除第' + (i + 1) + '张图片'"
          @click="images.splice(i, 1)"
        >
          <i aria-hidden="true" class="ph ph-x" /></button
        ><span>{{ i + 1 }}</span>
      </div>
      <el-upload
        v-if="images.length < 9"
        :show-file-list="false"
        accept="image/jpeg,image/png,image/webp,image/gif"
        :http-request="upload"
        :disabled="uploading"
        ><button class="add-photo" type="button" :disabled="uploading">
          <i aria-hidden="true"
            :class="uploading ? 'ph ph-spinner' : 'ph ph-image-square'"
          /><span>{{ uploading ? "上传中…" : "添加照片" }}</span>
        </button></el-upload
      >
    </div>
    <div class="composer-hint">
      <i aria-hidden="true" class="ph ph-images" /> {{ images.length }} / 9 张图片 · 列表最多展示前
      3 张
    </div>
    <template #footer
      ><el-button @click="visible = false">取消</el-button
      ><el-button
        type="primary"
        round
        :loading="saving"
        :disabled="uploading"
        @click="save"
        >{{ postId ? "保存并发布" : "发布帖子" }}</el-button
      ></template
    ></el-dialog
  >
</template>
<script setup>
import { ref } from "vue";
import { ElMessage } from "element-plus";
import request from "@/api/request";
import { uploadImage } from "@/api/file";
import { postText, postImages } from "@/utils/community";
const emit = defineEmits(["saved"]),
  visible = ref(false),
  postId = ref(null),
  title = ref(""),
  content = ref(""),
  images = ref([]),
  saving = ref(false),
  uploading = ref(false);
function open(post) {
  postId.value = post?.id || null;
  title.value = post?.title || "";
  content.value = post ? postText(post) : "";
  images.value = post ? postImages(post) : [];
  visible.value = true;
}
async function upload({ file, onSuccess, onError }) {
  if (images.value.length >= 9) {
    ElMessage.warning("最多上传9张图片");
    return;
  }
  uploading.value = true;
  try {
    const r = await uploadImage(file);
    images.value.push(r.data.url);
    onSuccess?.(r);
  } catch (e) {
    onError?.(e);
  } finally {
    uploading.value = false;
  }
}
async function save() {
  if (!title.value.trim()) {
    ElMessage.warning("请填写帖子标题");
    return;
  }
  saving.value = true;
  try {
    const blocks = [];
    if (content.value.trim())
      blocks.push({ type: "text", value: content.value.trim() });
    blocks.push(...images.value.map((value) => ({ type: "image", value })));
    const body = { title: title.value.trim(), blocks: JSON.stringify(blocks) };
    if (postId.value)
      await request.put("/api/community/posts/" + postId.value, body);
    else await request.post("/api/community/posts", body);
    visible.value = false;
    ElMessage.success("帖子已发布");
    emit("saved");
  } catch {
    /* request interceptor displays errors */
  } finally {
    saving.value = false;
  }
}
defineExpose({ open });
</script>
<style scoped>
.intro {
  color: #9299a8;
  margin: 0 0 20px;
}
.title-input {
  margin-bottom: 16px;
}
.title-input :deep(.el-input__wrapper) {
  box-shadow: none;
  border-bottom: 1px solid #e9edf4;
  border-radius: 0;
  padding: 8px 0;
  font-size: 20px;
  font-weight: 600;
}
.post-composer :deep(.el-textarea__inner) {
  box-shadow: none;
  background: #f8f9fc;
  padding: 16px;
  border-radius: 12px;
}
.photo-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 20px 0 12px;
}
.photo,
.add-photo {
  width: 104px;
  height: 104px;
  border-radius: 12px;
  position: relative;
  overflow: hidden;
}
.photo img {
  height: 100%;
  width: 100%;
  object-fit: cover;
}
.photo button {
  position: absolute;
  right: 5px;
  top: 5px;
  border: 0;
  background: #263248ad;
  color: white;
  border-radius: 50%;
  width: 24px;
  height: 24px;
  cursor: pointer;
}
.photo > span {
  position: absolute;
  bottom: 4px;
  left: 7px;
  color: white;
  text-shadow: 0 1px 3px black;
  font-size: 12px;
}
.add-photo {
  border: 1px dashed #cbd5e9;
  color: #7b89aa;
  background: #f7f9ff;
  display: flex;
  flex-direction: column;
  gap: 8px;
  justify-content: center;
  align-items: center;
  cursor: pointer;
}
.add-photo i {
  font-size: 28px;
}
.add-photo:hover {
  border-color: #5e7cfa;
  background: #eef2ff;
}
.composer-hint {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  color: #9299a8;
}
</style>

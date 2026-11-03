<template>
  <el-dialog v-model="visible" title="转发到聊天" width="min(460px,94vw)"
    ><p class="summary">{{ post?.title }}</p>
    <el-select
      v-model="target"
      placeholder="选择好友或已加入的群聊"
      filterable
      style="width: 100%"
      ><el-option-group label="好友"
        ><el-option
          v-for="f in friends"
          :key="f.userId"
          :label="f.displayName || String(f.userId)"
          :value="'private:' + f.userId" /></el-option-group
      ><el-option-group label="群聊"
        ><el-option
          v-for="g in groups"
          :key="g.groupId"
          :label="g.groupName"
          :value="'group:' + g.groupId" /></el-option-group></el-select
    ><el-empty
      v-if="!friends.length && !groups.length"
      description="先添加好友或加入群聊，就可以分享啦"
      :image-size="70"
    /><template #footer
      ><el-button
        type="primary"
        :disabled="!target"
        :loading="busy"
        @click="send"
        >确认转发</el-button
      ></template
    ></el-dialog
  >
</template>
<script setup>
import { ref } from "vue";
import request from "@/api/request";
import { ElMessage } from "element-plus";
const emit = defineEmits(["shared"]),
  visible = ref(false),
  post = ref(null),
  friends = ref([]),
  groups = ref([]),
  target = ref(""),
  busy = ref(false);
async function open(p) {
  post.value = p;
  target.value = "";
  try {
    const [f, g] = await Promise.all([
      request.get("/api/friend/list"),
      request.get("/api/group/mine"),
    ]);
    friends.value = f.data || [];
    groups.value = g.data || [];
    visible.value = true;
  } catch {}
}
async function send() {
  busy.value = true;
  try {
    const [type, id] = target.value.split(":");
    await request.post(
      "/api/community/posts/" + post.value.id + "/share",
      type === "group" ? { groupId: id } : { receiverId: id },
    );
    ElMessage.success("已转发");
    visible.value = false;
    emit("shared", post.value.id);
  } catch {
  } finally {
    busy.value = false;
  }
}
defineExpose({ open });
</script>
<style scoped>
.summary {
  border-radius: 10px;
  padding: 14px;
  background: #f5f7fc;
  color: #55617c;
  margin-top: 0;
}
</style>

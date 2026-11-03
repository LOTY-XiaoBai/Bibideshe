<template>
  <section class="block-manager" v-loading="busy">
    <h3>屏蔽管理</h3>
    <p>屏蔽只对你的账号生效，取消后可重新看到对应内容。</p>
    <article v-for="b in blocks" :key="b.id">
      <span class="block-icon"
        ><i
          aria-hidden="true"
          :class="b.kind === 'POST' ? 'ph ph-file-x' : 'ph ph-user-minus'"
      /></span>
      <div>
        <strong>{{ b.label }}</strong
        ><small
          >{{ b.kind === "POST" ? "单条帖子" : "作者的所有帖子" }} ·
          {{ displayTime(b.created_at) }}</small
        >
      </div>
      <el-button
        round
        size="small"
        :loading="removing === b.id"
        @click="unblock(b.id)"
        >取消屏蔽</el-button
      >
    </article>
    <el-empty
      v-if="!blocks.length && !busy"
      description="暂时没有屏蔽内容"
      :image-size="80"
    />
  </section>
</template>
<script setup>
import { ref, onMounted } from "vue";
import { ElMessage } from "element-plus";
import request from "@/api/request";
import { displayTime } from "@/utils/community";
const blocks = ref([]),
  busy = ref(false),
  removing = ref(null);
async function load() {
  busy.value = true;
  try {
    blocks.value = (await request.get("/api/community/blocks")).data;
  } catch {
  } finally {
    busy.value = false;
  }
}
async function unblock(id) {
  removing.value = id;
  try {
    await request.delete("/api/community/blocks/" + id);
    blocks.value = blocks.value.filter((b) => b.id !== id);
    ElMessage.success("已取消屏蔽");
  } catch {
  } finally {
    removing.value = null;
  }
}
onMounted(load);
</script>
<style scoped>
.block-manager {
  padding: 12px 0;
}
.block-manager h3 {
  font-size: 16px;
  color: #46536b;
}
.block-manager > p {
  color: #929bad;
  font-size: 13px;
}
article {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 22px 0;
  border-bottom: 1px solid #f0f2f6;
}
.block-icon {
  background: #f3f5fa;
  color: #8895af;
  border-radius: 12px;
  padding: 12px;
  font-size: 22px;
}
article div {
  flex: 1;
  min-width: 0;
}
strong {
  font-size: 14px;
  display: block;
  color: #536079;
  overflow-wrap: anywhere;
}
small {
  display: block;
  margin-top: 8px;
  color: #a0a8b7;
  font-size: 12px;
}
</style>

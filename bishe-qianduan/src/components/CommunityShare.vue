<template>
  <el-dialog
    v-model="visible"
    title="转发到聊天"
    width="min(760px,95vw)"
    :close-on-click-modal="!busy"
  >
    <div class="share-layout" v-loading="loading">
      <section class="target-panel">
        <el-input v-model="q" placeholder="搜索好友或群聊" clearable
          ><template #prefix
            ><i aria-hidden="true" class="ph ph-magnifying-glass" /></template
        ></el-input>
        <div class="target-scroll">
          <template v-for="section in sections" :key="section.label"
            ><h4 v-if="section.items.length">{{ section.label }}</h4>
            <button
              v-for="t in section.items"
              :key="t.key"
              class="target-row"
              :class="{ selected: selected.includes(t.key) }"
              :aria-pressed="selected.includes(t.key)"
              @click="toggle(t.key)"
            >
              <span class="select-mark"
                ><i
                  v-if="selected.includes(t.key)"
                  aria-hidden="true"
                  class="ph ph-check" /></span
              ><el-avatar :size="34" :src="resolveMediaUrl(t.avatar)">{{
                t.name?.slice(0, 1)
              }}</el-avatar
              ><span class="target-name"
                >{{ t.name
                }}<small>{{
                  t.kind === "group" ? "群聊" : "好友"
                }}</small></span
              >
            </button></template
          >
          <el-empty
            v-if="!filtered.length && !loading"
            description="暂无可转发的好友或群聊"
            :image-size="60"
          />
        </div>
      </section>
      <section class="preview-panel">
        <h4>
          发送给 <span>{{ selected.length }}/20</span>
        </h4>
        <div class="selected-targets">
          <el-tag
            v-for="t in chosen"
            :key="t.key"
            closable
            @close="toggle(t.key)"
            >{{ t.name }}</el-tag
          >
          <p v-if="!selected.length">从左侧选择一个或多个好友、群聊</p>
        </div>
        <div class="post-preview">
          <span><i aria-hidden="true" class="ph ph-article" /> 社区帖子</span
          ><strong>{{ post?.title }}</strong>
        </div>
        <p class="share-note">将分别转发到已选会话</p>
      </section>
    </div>
    <template #footer
      ><el-button :disabled="busy" @click="visible = false">取消</el-button
      ><el-button
        type="primary"
        :disabled="!selected.length"
        :loading="busy"
        @click="send"
        >确认转发<span v-if="selected.length"
          >（{{ selected.length }}）</span
        ></el-button
      ></template
    >
  </el-dialog>
</template>
<script setup>
import { ref, computed } from "vue";
import request from "@/api/request";
import { ElMessage } from "element-plus";
import { resolveMediaUrl } from "@/utils/media";
const emit = defineEmits(["shared"]),
  visible = ref(false),
  post = ref(null),
  targets = ref([]),
  selected = ref([]),
  q = ref(""),
  busy = ref(false),
  loading = ref(false);
const filtered = computed(() =>
  targets.value.filter((t) =>
    (t.name + " " + t.id).toLowerCase().includes(q.value.toLowerCase()),
  ),
);
const sections = computed(() => [
  { label: "最近聊天", items: filtered.value.filter((t) => t.last_chat) },
  {
    label: "最近添加的好友",
    items: filtered.value.filter((t) => !t.last_chat && t.kind === "private"),
  },
  {
    label: "已加入的群聊",
    items: filtered.value.filter((t) => !t.last_chat && t.kind === "group"),
  },
]);
const chosen = computed(() =>
  targets.value.filter((t) => selected.value.includes(t.key)),
);
function toggle(key) {
  if (busy.value) return;
  const index = selected.value.indexOf(key);
  if (index >= 0) selected.value.splice(index, 1);
  else if (selected.value.length < 20) selected.value.push(key);
  else ElMessage.warning("每次最多选择20个会话");
}
async function open(p) {
  post.value = p;
  selected.value = [];
  q.value = "";
  targets.value = [];
  visible.value = true;
  loading.value = true;
  try {
    targets.value = (await request.get("/api/community/share-targets")).data;
  } catch {
  } finally {
    loading.value = false;
  }
}
async function send() {
  if (!selected.value.length) return;
  busy.value = true;
  try {
    const r = await request.post(
      "/api/community/posts/" + post.value.id + "/share-batch",
      { targets: [...selected.value] },
    );
    ElMessage.success("已转发到 " + r.data + " 个会话");
    visible.value = false;
    emit("shared", post.value.id, Number(r.data));
  } catch {
  } finally {
    busy.value = false;
  }
}
defineExpose({ open });
</script>
<style scoped>
.share-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  min-height: 350px;
  color: #596781;
}
.target-panel {
  padding-right: 20px;
  border-right: 1px solid #edf0f6;
  min-width: 0;
}
.target-scroll {
  height: 330px;
  overflow-y: auto;
  margin-top: 12px;
}
.target-scroll h4 {
  font-size: 12px;
  font-weight: 500;
  color: #95a0b5;
  margin: 18px 8px 10px;
}
.target-row {
  width: 100%;
  padding: 9px 7px;
  display: flex;
  align-items: center;
  gap: 10px;
  border: 0;
  background: white;
  border-radius: 9px;
  text-align: left;
  cursor: pointer;
}
.target-row:hover,
.target-row.selected {
  background: #f0f5ff;
}
.select-mark {
  width: 17px;
  height: 17px;
  border: 1px solid #d4dce9;
  border-radius: 50%;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  color: white;
}
.selected .select-mark {
  border-color: #7b98ec;
  background: #7b98ec;
}
.target-name {
  font-size: 13px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: #4b5e7b;
}
.target-name small {
  display: block;
  font-size: 10px;
  color: #a0aabb;
  margin-top: 3px;
}
.preview-panel {
  padding: 0 0 0 23px;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.preview-panel h4 {
  font-size: 13px;
  margin: 5px 0 16px;
}
.preview-panel h4 span {
  font-size: 11px;
  font-weight: 400;
  color: #99a5ba;
  margin-left: 8px;
}
.selected-targets {
  display: flex;
  gap: 7px;
  flex-wrap: wrap;
  align-content: flex-start;
  min-height: 150px;
  max-height: 200px;
  overflow: auto;
}
.selected-targets p {
  font-size: 12px;
  color: #a2adbd;
  line-height: 1.8;
}
.post-preview {
  margin-top: auto;
  border: 1px solid #e8edf6;
  border-radius: 12px;
  background: #f7f9fe;
  padding: 16px;
}
.post-preview span {
  display: flex;
  gap: 7px;
  font-size: 11px;
  color: #97a6c3;
  margin-bottom: 10px;
}
.post-preview strong {
  font-size: 14px;
  color: #526788;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.share-note {
  font-size: 11px;
  color: #a6afbe;
}
@media (max-width: 560px) {
  .share-layout {
    grid-template-columns: 1fr;
  }
  .target-panel {
    padding: 0;
    border: 0;
  }
  .target-scroll {
    height: 220px;
  }
  .preview-panel {
    padding: 15px 0 0;
  }
  .selected-targets {
    min-height: 40px;
    max-height: 90px;
  }
  .post-preview {
    margin-top: 15px;
  }
}
</style>

<template>
  <section class="community">
    <header>
      <h2>{{ compact ? "校园动态" : "动态" }}</h2>
      <el-button type="primary" @click="edit()">发布帖子</el-button>
    </header>
    <el-tabs v-if="!compact" v-model="scope" @tab-change="reset"
      ><el-tab-pane label="社区" name="community" /><el-tab-pane
        label="我的发帖"
        name="mine" /><el-tab-pane label="通知" name="notices" /><el-tab-pane
        v-if="admin"
        label="后台监控"
        name="admin" /></el-tabs
    ><template v-if="scope !== 'notices'"
      ><div class="filters">
        <el-input
          v-model="q"
          clearable
          placeholder="搜索帖子标题或正文"
          @keyup.enter="reset"
        /><el-button @click="reset">搜索</el-button
        ><el-select v-model="sort" @change="reset"
          ><el-option label="最新" value="latest" /><el-option
            label="最热门"
            value="hot" /><el-option label="精华" value="featured"
        /></el-select>
      </div>
      <p v-if="moderation.warnings">
        社区警告 {{ moderation.warnings }} 次
        <span v-if="moderation.muted_until"
          >· 禁言截止 {{ moderation.muted_until }}</span
        >
      </p>
      <div :class="{ scroll: compact }" v-loading="busy">
        <article v-for="p in posts" :key="p.id">
          <h3 @click="open(p.id)">
            <el-tag v-if="p.pinned" size="small">置顶</el-tag>
            <el-tag v-if="p.featured" type="warning" size="small">精华</el-tag>
            {{ p.title }}
          </h3>
          <p class="meta">
            {{ p.author }} · {{ p.created_at }} · {{ p.views }} 浏览 ·
            {{ p.likes }} 赞
            <span v-if="p.status !== 'PUBLIC'"
              >· {{ statusLabel(p.status) }}</span
            >
          </p>
          <p class="preview" @click="open(p.id)">{{ p.search_text }}</p>
          <el-button text @click="open(p.id)">查看全文 / 回复</el-button
          ><template
            v-if="
              p.owner === viewer && !['FORCED', 'DELETED'].includes(p.status)
            "
            ><el-button text @click="edit(p)">编辑</el-button
            ><el-button
              text
              @click="action(p, p.status === 'HIDDEN' ? 'restore' : 'hide')"
              >{{ p.status === "HIDDEN" ? "重新发布" : "隐藏" }}</el-button
            ><el-button text type="danger" @click="action(p, 'delete')"
              >删除</el-button
            ></template
          ><template v-if="admin && p.status === 'PUBLIC'"
            ><el-button
              text
              @click="action(p, p.featured ? 'unfeature' : 'feature')"
              >{{ p.featured ? "取消精华" : "设为精华" }}</el-button
            ><el-button text @click="action(p, p.pinned ? 'unpin' : 'pin')">{{
              p.pinned ? "取消置顶" : "置顶"
            }}</el-button
            ><el-button text type="danger" @click="action(p, 'forceHide')"
              >强制隐藏</el-button
            ><el-button text type="danger" @click="action(p, 'delete')"
              >删帖</el-button
            ></template
          >
        </article>
        <el-empty
          v-if="!posts.length"
          description="暂无帖子，来发布第一条动态"
        />
      </div>
      <el-pagination
        :current-page="page"
        :page-size="20"
        :total="total"
        layout="prev,pager,next"
        @current-change="
          (v) => {
            page = v;
            load();
          }
        " /></template
    ><template v-else
      ><el-button @click="read">全部标为已读</el-button>
      <article v-for="n in notices" :key="n.id">
        <el-tag v-if="!n.is_read" size="small">未读</el-tag> {{ n.message }}
        <small>{{ n.created_at }}</small
        ><el-button v-if="n.post_id" text @click="open(n.post_id)"
          >查看帖子</el-button
        >
      </article>
      <el-button
        @click="
          noticePage++;
          loadNotices(true);
        "
        >加载更多通知</el-button
      ></template
    >
    <section v-if="admin && scope === 'admin'">
      <h3>帖子监控词</h3>
      <el-input v-model="word" placeholder="新增社区专用监控词" /><el-button
        @click="addWord"
        >添加</el-button
      ><el-tag
        v-for="w in words"
        :key="w.id"
        closable
        @close="removeWord(w.id)"
        >{{ w.word }}</el-tag
      >
      <h3>警告与禁言管理</h3>
      <article v-for="u in mutes" :key="u.owner">
        {{ u.owner }} · 警告 {{ u.warnings }} 次 · 禁言至
        {{ u.muted_until || "无" }}
        <el-button @click="unmute(u.owner)">解禁并清零警告</el-button>
      </article>
    </section>
    <el-dialog
      v-model="editing"
      :title="editId ? '编辑帖子（保留原发布时间）' : '发布帖子'"
      width="min(760px,95vw)"
      ><el-input
        v-model="title"
        maxlength="120"
        show-word-limit
        placeholder="帖子标题"
      /><BlockEditor v-model="blocks" /><template #footer
        ><el-button @click="editing = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >发布</el-button
        ></template
      ></el-dialog
    ><el-dialog v-model="show" title="帖子详情" width="min(850px,95vw)"
      ><template v-if="detail"
        ><h2>{{ detail.title }}</h2>
        <p>
          {{ detail.author }} · {{ detail.created_at }} ·
          {{ detail.views }} 浏览
        </p>
        <el-alert
          v-if="detail.status !== 'PUBLIC'"
          :title="statusLabel(detail.status)"
          :closable="false"
        /><ContentBlocks :value="detail.blocks" /><el-button
          v-if="detail.status === 'PUBLIC'"
          @click="like"
          >{{ detail.liked ? "取消点赞" : "点赞" }}
          {{ detail.likes }}</el-button
        ><el-button v-if="!admin && detail.status === 'PUBLIC'" @click="share"
          >转发到聊天</el-button
        >
        <h3>回复</h3>
        <article v-for="r in detail.replies" :key="r.id">
          <b>{{ r.author }}</b
          ><small v-if="r.parent_id"> 回复 #{{ r.parent_id }}</small>
          <p>{{ r.content }}</p>
          <small>{{ r.created_at }}</small
          ><el-button
            v-if="detail.status === 'PUBLIC'"
            text
            @click="replyParent = r.id"
            >回复此人</el-button
          ><el-button
            v-if="admin || r.owner === viewer || detail.owner === viewer"
            text
            type="danger"
            @click="deleteReply(r.id)"
            >删除回复</el-button
          >
        </article>
        <template v-if="detail.status === 'PUBLIC'"
          ><p v-if="replyParent">
            正在回复 #{{ replyParent }}
            <el-button text @click="replyParent = null">取消</el-button>
          </p>
          <el-input
            v-model="replyText"
            type="textarea"
            maxlength="2000"
          /><el-button :loading="replying" @click="reply"
            >发送回复</el-button
          ></template
        ></template
      ></el-dialog
    >
    <el-dialog v-model="sharing" title="转发帖子到会话" width="min(480px,95vw)">
      <el-select
        v-model="shareTarget"
        placeholder="选择好友或群聊"
        filterable
        style="width: 100%"
      >
        <el-option-group label="好友"
          ><el-option
            v-for="f in friends"
            :key="f.userId"
            :label="f.displayName || String(f.userId)"
            :value="'private:' + f.userId"
        /></el-option-group>
        <el-option-group label="已加入群聊"
          ><el-option
            v-for="g in groups"
            :key="g.groupId"
            :label="g.groupName"
            :value="'group:' + g.groupId"
        /></el-option-group>
      </el-select>
      <p v-if="!friends.length && !groups.length">
        暂无会话，请先添加好友或加入群聊。
      </p>
      <template #footer
        ><el-button
          type="primary"
          :disabled="!shareTarget"
          :loading="shareBusy"
          @click="sendShare"
          >确认转发</el-button
        ></template
      >
    </el-dialog>
  </section>
</template>
<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { useRoute } from "vue-router";
import { getRole } from "@/utils/auth";
import request from "@/api/request";
import { ElMessage, ElMessageBox } from "element-plus";
import BlockEditor from "./BlockEditor.vue";
import ContentBlocks from "./ContentBlocks.vue";
defineProps({ compact: Boolean });
const route = useRoute(),
  admin = computed(() => getRole() === "ADMIN"),
  scope = ref("community"),
  q = ref(""),
  sort = ref("latest"),
  page = ref(1),
  total = ref(0),
  posts = ref([]),
  viewer = ref(""),
  moderation = ref({}),
  busy = ref(false),
  notices = ref([]),
  noticePage = ref(1),
  words = ref([]),
  mutes = ref([]),
  word = ref(""),
  editing = ref(false),
  editId = ref(null),
  title = ref(""),
  blocks = ref("[]"),
  saving = ref(false),
  show = ref(false),
  detail = ref(null),
  replyText = ref(""),
  replyParent = ref(null),
  replying = ref(false);
const api = "/api/community";
const sharing = ref(false),
  shareTarget = ref(""),
  friends = ref([]),
  groups = ref([]),
  shareBusy = ref(false);
function statusLabel(s) {
  return (
    {
      HIDDEN: "作者已隐藏",
      FORCED: "管理员强制隐藏（不可恢复）",
      DELETED: "帖子已删除（不可恢复）",
    }[s] || s
  );
}
async function load() {
  busy.value = true;
  try {
    const r = (
      await request.get(api + "/posts", {
        params: {
          q: q.value,
          sort: sort.value,
          scope: scope.value,
          page: page.value,
        },
      })
    ).data;
    posts.value = r.list;
    total.value = r.total;
    viewer.value = r.owner;
    moderation.value = r.moderation;
    if (scope.value === "admin") await controls();
  } finally {
    busy.value = false;
  }
}
async function reset() {
  page.value = 1;
  if (scope.value === "notices") {
    noticePage.value = 1;
    await loadNotices();
  } else await load();
}
async function loadNotices(append = false) {
  const rows = (
    await request.get(api + "/notices", { params: { page: noticePage.value } })
  ).data;
  notices.value = append ? [...notices.value, ...rows] : rows;
}
async function read() {
  await request.post(api + "/notices/read");
  await loadNotices();
}
async function open(id) {
  detail.value = (await request.get(api + `/posts/${id}`)).data;
  viewer.value = detail.value.viewer;
  replyParent.value = null;
  show.value = true;
}
function edit(p) {
  editId.value = p?.id || null;
  title.value = p?.title || "";
  blocks.value = p?.blocks || JSON.stringify([{ type: "text", value: "" }]);
  editing.value = true;
}
async function save() {
  saving.value = true;
  try {
    const b = {
      title: title.value,
      blocks: JSON.stringify(
        JSON.parse(blocks.value).filter((b) => b.value.trim()),
      ),
    };
    if (editId.value) await request.put(api + `/posts/${editId.value}`, b);
    else await request.post(api + "/posts", b);
    editing.value = false;
    ElMessage.success("发布成功");
    await reset();
  } finally {
    saving.value = false;
  }
}
async function action(p, act) {
  if (["delete", "hide", "forceHide"].includes(act)) {
    const confirmed = await ElMessageBox.confirm(
      "确认执行此操作？管理员下架或删除不可由作者恢复",
      "确认",
    ).then(() => true).catch(() => false);
    if (!confirmed) return;
  }
  await request.post(api + `/posts/${p.id}/action`, { action: act });
  await load();
}
async function like() {
  await request.post(api + `/posts/${detail.value.id}/action`, {
    action: "like",
  });
  await open(detail.value.id);
  await load();
}
async function reply() {
  if (!replyText.value.trim()) return;
  replying.value = true;
  try {
    await request.post(api + `/posts/${detail.value.id}/replies`, {
      content: replyText.value,
      parentId: replyParent.value,
    });
    replyText.value = "";
    await open(detail.value.id);
    await load();
  } finally {
    replying.value = false;
  }
}
async function deleteReply(id) {
  const confirmed = await ElMessageBox.confirm("确认删除回复？", "确认").then(() => true).catch(() => false);
  if (!confirmed) return;
  await request.delete(api + `/replies/${id}`);
  await open(detail.value.id);
}
async function share() {
  const [f, g] = await Promise.all([
    request.get("/api/friend/list"),
    request.get("/api/group/mine"),
  ]);
  friends.value = f.data || [];
  groups.value = g.data || [];
  shareTarget.value = "";
  sharing.value = true;
}
async function sendShare() {
  const [type, id] = shareTarget.value.split(":");
  shareBusy.value = true;
  try {
    await request.post(
      api + `/posts/${detail.value.id}/share`,
      type === "group" ? { groupId: id } : { receiverId: id },
    );
    ElMessage.success("已转发到聊天");
    sharing.value = false;
  } finally {
    shareBusy.value = false;
  }
}
async function controls() {
  words.value = (await request.get(api + "/words")).data;
  mutes.value = (await request.get(api + "/mutes")).data;
}
async function addWord() {
  await request.post(api + "/words", { word: word.value });
  word.value = "";
  await controls();
}
async function removeWord(id) {
  await request.delete(api + `/words/${id}`);
  await controls();
}
async function unmute(owner) {
  await request.post(api + "/unmute", { owner });
  await controls();
}
onMounted(async () => {
  await load();
  if (route.params.id) await open(route.params.id);
});
watch(
  () => route.params.id,
  (id) => {
    if (id) open(id);
  },
);
</script>
<style scoped>
.community {
  background: #fff;
  padding: 24px;
  border-radius: 18px;
  margin-top: 24px;
  min-width: 0;
}
header,
.filters {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: space-between;
}
.filters .el-input {
  flex: 1;
}
.filters .el-select {
  width: 140px;
}
article {
  padding: 20px 0;
  border-bottom: 1px solid #edf0f5;
}
h3 {
  cursor: pointer;
  margin: 0 0 10px;
}
.meta,
small {
  color: #8993a4;
  font-size: 12px;
}
.preview {
  white-space: pre-wrap;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  cursor: pointer;
}
.scroll {
  max-height: 590px;
  overflow-y: auto;
}
.el-pagination {
  margin-top: 20px;
}
.el-tag {
  margin: 4px;
}
</style>

<template>
  <main class="post-page" v-loading="loading">
    <header class="page-top">
      <button class="back" aria-label="返回动态" @click="back">
        <i aria-hidden="true" class="ph ph-arrow-left" /></button
      ><span>帖子详情</span><span class="top-brand">校园社区</span>
    </header>
    <div v-if="post" class="detail-inner">
      <div class="author-toolbar">
        <CommunityAuthor :person="post" :views="post.views"
          ><span v-if="post.status === 'HIDDEN'" class="visibility"
            ><i aria-hidden="true" class="ph ph-lock-simple" />仅自己可见</span
          ></CommunityAuthor
        ><PostSafety
          v-if="active && !admin"
          :post="post"
          :viewer="post.viewer"
          show-follow
          @blocked="back"
          @friend-requested="post.friend_status = 'REQUEST_SENT'"
        />
      </div>
      <h1>
        <span v-if="flag(post.pinned)" class="tag">置顶</span
        ><span v-if="flag(post.featured)" class="tag essence">精华</span
        >{{ post.title }}
      </h1>
      <el-alert
        v-if="post.status === 'FORCED' || post.status === 'DELETED'"
        :title="
          post.status === 'FORCED'
            ? '该帖子已被管理员隐藏，可在动态的“我的申诉”中提交说明'
            : '该帖子已删除'
        "
        type="warning"
        :closable="false"
      />
      <p v-if="postText(post)" class="post-body">{{ postText(post) }}</p>
      <div class="full-images">
        <el-image
          v-for="(url, i) in postImages(post)"
          :key="url"
          :src="url"
          :preview-src-list="postImages(post)"
          :initial-index="i"
          fit="contain"
          hide-on-click-modal
          preview-teleported
        />
      </div>
      <div class="detail-actions">
        <button v-if="!admin" :disabled="!active" @click="share.open(post)">
          <i aria-hidden="true" class="ph ph-share-fat" />{{
            post.share_count || "转发"
          }}</button
        ><button @click="admin ? scrollReplies() : focusReply()">
          <i aria-hidden="true" class="ph ph-chat-circle" />{{
            post.reply_count || "回复"
          }}</button
        ><button
          v-if="!admin"
          :class="{ liked: flag(post.liked) }"
          :disabled="!active || postLiking"
          @click="likePost"
        >
          <i
            aria-hidden="true"
            :class="
              flag(post.liked) ? 'ph-fill ph-thumbs-up' : 'ph ph-thumbs-up'
            "
          />{{ post.likes || "点赞" }}
        </button>
      </div>
      <section id="replies" class="discussion">
        <div class="discussion-heading">
          <div class="reply-tabs">
            <button
              :class="{ selected: !onlyAuthor }"
              @click="onlyAuthor = false"
            >
              全部回复 <span>{{ post.reply_count }}</span></button
            ><button
              :class="{ selected: onlyAuthor }"
              @click="onlyAuthor = true"
            >
              只看楼主
            </button>
          </div>
          <div class="order-tabs">
            <button :class="{ selected: !reverse }" @click="reverse = false">
              正序</button
            ><button :class="{ selected: reverse }" @click="reverse = true">
              倒序
            </button>
          </div>
        </div>
        <div v-if="active && !admin" class="reply-editor">
          <div v-if="replyTarget" class="reply-target">
            回复 {{ replyTarget.display_name || replyTarget.author }}
            <button @click="replyTarget = null">
              <i aria-hidden="true" class="ph ph-x" />
            </button>
          </div>
          <el-input
            ref="replyInput"
            v-model="replyText"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 6 }"
            maxlength="2000"
            show-word-limit
            :placeholder="
              replyTarget ? '写下你的回复…' : '说点什么，一起参与讨论吧…'
            "
          /><el-button
            round
            type="primary"
            :loading="replying"
            :disabled="!replyText.trim()"
            @click="sendReply"
            >发送回复</el-button
          >
        </div>
        <div v-for="root in visibleRoots" :key="root.id" class="reply-thread">
          <CommunityReply
            :reply="root"
            :floor="root.floor"
            :post-owner="post.owner"
            :viewer="post.viewer"
            :admin="admin"
            :active="active"
            :busy="replyLikes.has(root.id)"
            @reply="focusReply"
            @like="likeReply"
            @remove="removeReply"
            ><div v-if="childrenOf(root.id).length" class="nested-replies">
              <CommunityReply
                v-for="child in shownChildren(root.id)"
                :key="child.id"
                :reply="child"
                nested
                :parent-name="parentName(child)"
                :post-owner="post.owner"
                :viewer="post.viewer"
                :admin="admin"
                :active="active"
                :busy="replyLikes.has(child.id)"
                @reply="focusReply"
                @like="likeReply"
                @remove="removeReply"
              /><button
                v-if="childrenOf(root.id).length > 3"
                class="expand-replies"
                @click="toggle(root.id)"
              >
                {{
                  expanded.has(root.id)
                    ? "收起回复"
                    : "展开 " + (childrenOf(root.id).length - 3) + " 条回复"
                }}
                <i
                  aria-hidden="true"
                  :class="
                    expanded.has(root.id)
                      ? 'ph ph-caret-up'
                      : 'ph ph-caret-down'
                  "
                />
              </button></div
          ></CommunityReply>
        </div>
        <el-empty
          v-if="!roots.length"
          description="暂无回复，来聊聊你的想法吧"
          :image-size="80"
        /><button
          v-if="roots.length > rootLimit"
          class="load-discussion"
          @click="rootLimit += 20"
        >
          展开更多讨论
        </button>
      </section>
    </div>
    <el-empty v-else-if="!loading" description="帖子不存在或已被下架"
      ><el-button @click="back">返回社区</el-button></el-empty
    ><CommunityShare
      v-if="!admin"
      ref="share"
      @shared="
        (id, count) =>
          (post.share_count = Number(post.share_count || 0) + count)
      "
    />
  </main>
</template>
<script setup>
import { ref, computed, reactive, onMounted, watch, nextTick } from "vue";
import { useRoute, useRouter } from "vue-router";
import { getRole } from "@/utils/auth";
import { ElMessage, ElMessageBox } from "element-plus";
import request from "@/api/request";
import { postText, postImages, flag } from "@/utils/community";
import CommunityAuthor from "@/components/CommunityAuthor.vue";
import PostSafety from "@/components/PostSafety.vue";
import CommunityReply from "@/components/CommunityReply.vue";
import CommunityShare from "@/components/CommunityShare.vue";
const route = useRoute(),
  router = useRouter(),
  post = ref(null),
  loading = ref(false),
  share = ref(),
  replyInput = ref(),
  replyText = ref(""),
  replyTarget = ref(null),
  replying = ref(false),
  postLiking = ref(false),
  replyLikes = reactive(new Set()),
  expanded = reactive(new Set()),
  onlyAuthor = ref(false),
  reverse = ref(false),
  rootLimit = ref(20),
  admin = computed(() => getRole() === "ADMIN"),
  active = computed(() => post.value?.status === "PUBLIC");
const allReplies = computed(() => post.value?.replies || []),
  byId = computed(
    () => new Map(allReplies.value.map((r) => [Number(r.id), r])),
  );
function rootId(reply) {
  let current = reply;
  const seen = new Set([Number(current.id)]);
  while (
    current.parent_id &&
    byId.value.has(Number(current.parent_id)) &&
    !seen.has(Number(current.parent_id))
  ) {
    current = byId.value.get(Number(current.parent_id));
    seen.add(Number(current.id));
  }
  return Number(current.id);
}
const roots = computed(() => {
  let rows = allReplies.value
    .filter((r) => rootId(r) === Number(r.id))
    .map((r, i) => ({ ...r, floor: i + 1 }))
    .filter((r) => !flag(r.deleted) || childrenOf(r.id).length);
  if (onlyAuthor.value)
    rows = rows.filter(
      (r) =>
        r.owner === post.value.owner ||
        childrenOf(r.id).some((c) => c.owner === post.value.owner),
    );
  return reverse.value ? [...rows].reverse() : rows;
});
const visibleRoots = computed(() => roots.value.slice(0, rootLimit.value));
function childrenOf(id) {
  return allReplies.value.filter(
    (r) =>
      Number(r.id) !== Number(id) &&
      rootId(r) === Number(id) &&
      !flag(r.deleted) &&
      (!onlyAuthor.value || r.owner === post.value.owner),
  );
}
function shownChildren(id) {
  const rows = childrenOf(id);
  return expanded.has(id) ? rows : rows.slice(0, 3);
}
function parentName(reply) {
  const parent = byId.value.get(Number(reply.parent_id));
  return parent?.display_name || parent?.author || "";
}
function toggle(id) {
  expanded.has(id) ? expanded.delete(id) : expanded.add(id);
}
async function load() {
  loading.value = true;
  try {
    post.value = (
      await request.get("/api/community/posts/" + route.params.id)
    ).data;
    if (route.hash === "#replies") {
      await nextTick();
      document
        .getElementById("replies")
        ?.scrollIntoView({ behavior: "smooth" });
    }
  } catch {
    post.value = null;
  } finally {
    loading.value = false;
  }
}
function back() {
  const returnTo=route.query.returnTo;
  if(typeof returnTo==='string' && /^\/(student|teacher|admin)\//.test(returnTo)){
    if(router.options.history.state.back===returnTo)router.back();else router.replace(returnTo);
    return;
  }
  const previous = router.options.history.state.back;
  if (typeof previous === "string" && previous.startsWith("/")) router.back();
  else router.push("/" + getRole().toLowerCase() + "/activity");
}
function scrollReplies() {
  document.getElementById("replies")?.scrollIntoView({ behavior: "smooth" });
}
async function focusReply(reply = null) {
  replyTarget.value = reply;
  await nextTick();
  document.getElementById("replies")?.scrollIntoView({ behavior: "smooth" });
  replyInput.value?.focus();
}
async function sendReply() {
  if (!replyText.value.trim() || replying.value) return;
  replying.value = true;
  try {
    await request.post("/api/community/posts/" + post.value.id + "/replies", {
      content: replyText.value,
      parentId: replyTarget.value?.id || null,
    });
    replyText.value = "";
    replyTarget.value = null;
    post.value = (
      await request.get("/api/community/posts/" + post.value.id)
    ).data;
    ElMessage.success("回复已发送");
  } catch {
  } finally {
    replying.value = false;
  }
}
async function likePost() {
  postLiking.value = true;
  try {
    await request.post("/api/community/posts/" + post.value.id + "/action", {
      action: "like",
    });
    post.value.likes =
      Number(post.value.likes || 0) + (flag(post.value.liked) ? -1 : 1);
    post.value.liked = !flag(post.value.liked);
  } catch {
  } finally {
    postLiking.value = false;
  }
}
async function likeReply(reply) {
  if (replyLikes.has(reply.id)) return;
  replyLikes.add(reply.id);
  try {
    await request.post("/api/community/replies/" + reply.id + "/like");
    reply.likes = Number(reply.likes || 0) + (flag(reply.liked) ? -1 : 1);
    reply.liked = !flag(reply.liked);
    const original = byId.value.get(Number(reply.id));
    if (original)
      Object.assign(original, { likes: reply.likes, liked: reply.liked });
  } catch {
  } finally {
    replyLikes.delete(reply.id);
  }
}
async function removeReply(reply) {
  const ok = await ElMessageBox.confirm(
    "删除这条回复后将无法恢复，是否继续？",
    "删除回复",
    { confirmButtonText: "删除", cancelButtonText: "取消", type: "warning" },
  )
    .then(() => true)
    .catch(() => false);
  if (!ok) return;
  try {
    await request.delete("/api/community/replies/" + reply.id);
    post.value = (
      await request.get("/api/community/posts/" + post.value.id)
    ).data;
  } catch {}
}
onMounted(load);
watch(
  () => route.params.id,
  () => {
    rootLimit.value = 20;
    expanded.clear();
    load();
  },
);
</script>
<style scoped>
.author-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.post-page {
  min-height: 100vh;
  background: #fff;
  color: #30394b;
}
.page-top {
  position: sticky;
  top: 0;
  background: #ffffffed;
  backdrop-filter: blur(12px);
  display: flex;
  align-items: center;
  gap: 18px;
  height: 74px;
  max-width: 930px;
  margin: auto;
  padding: 0 28px;
  border-bottom: 1px solid #eff1f6;
  z-index: 10;
  font-size: 14px;
  color: #6e7890;
}
.back {
  border: 0;
  background: #f4f6fb;
  border-radius: 50%;
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  font-size: 22px;
  color: #596780;
  cursor: pointer;
}
.back:hover {
  background: #eaf0ff;
}
.top-brand {
  margin-left: auto;
  color: #a9b0bd;
  font-size: 12px;
  letter-spacing: 2px;
}
.detail-inner {
  max-width: 800px;
  margin: auto;
  padding: 32px 28px 80px;
}
h1 {
  font-size: 24px;
  line-height: 1.6;
  color: #253149;
  letter-spacing: -0.5px;
  margin: 26px 0 22px;
  overflow-wrap: anywhere;
}
.tag {
  display: inline-block;
  padding: 2px 7px;
  font-size: 11px;
  color: #7188d9;
  background: #f0f4ff;
  border: 1px solid #e2e9ff;
  border-radius: 5px;
  vertical-align: 4px;
  margin-right: 9px;
  letter-spacing: 0;
}
.essence {
  color: #a281b9;
  background: #faf5fe;
  border-color: #ece0f4;
}
.post-body {
  font-size: 15px;
  line-height: 2;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  color: #4c566b;
  margin: 20px 0;
}
.full-images {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
  margin-top: 20px;
}
.full-images :deep(.el-image) {
  max-width: 100%;
  border-radius: 10px;
  cursor: zoom-in;
}
.full-images :deep(img) {
  max-width: 100%;
  max-height: 900px;
  object-fit: contain;
  display: block;
}
.detail-actions {
  display: flex;
  gap: clamp(45px, 12vw, 120px);
  padding: 25px 0 30px;
  border-bottom: 1px solid #eff1f5;
}
.detail-actions button {
  border: 0;
  background: none;
  color: #8993a8;
  display: flex;
  gap: 9px;
  align-items: center;
  font-size: 13px;
  cursor: pointer;
}
.detail-actions i {
  font-size: 22px;
}
.detail-actions .liked,
.detail-actions button:hover {
  color: #5e7cfa;
}
.detail-actions button:disabled {
  opacity: 0.4;
  cursor: default;
}
.discussion-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin: 22px 0;
}
.reply-tabs {
  display: flex;
  gap: 24px;
}
.reply-tabs button {
  background: none;
  border: 0;
  color: #9199aa;
  padding: 14px 0;
  position: relative;
  cursor: pointer;
  font-size: 14px;
}
.reply-tabs .selected {
  color: #35415e;
  font-weight: 650;
}
.reply-tabs .selected:after {
  content: "";
  position: absolute;
  bottom: 0;
  height: 3px;
  left: 0;
  right: 0;
  border-radius: 4px;
  background: #5e7cfa;
}
.reply-tabs span {
  font-weight: 400;
  margin-left: 5px;
  font-size: 12px;
}
.order-tabs {
  display: flex;
  padding: 3px;
  background: #f5f7fb;
  border-radius: 20px;
}
.order-tabs button {
  border: 0;
  background: none;
  border-radius: 20px;
  padding: 5px 10px;
  font-size: 11px;
  color: #9aa2b3;
  cursor: pointer;
}
.order-tabs .selected {
  background: white;
  color: #657bc5;
}
.reply-editor {
  background: #f7f9fc;
  border-radius: 14px;
  padding: 14px;
  margin-bottom: 10px;
  text-align: right;
}
.reply-editor :deep(.el-textarea__inner) {
  background: transparent;
  box-shadow: none;
  resize: none;
  font-size: 14px;
}
.reply-editor :deep(.el-input__count) {
  background: transparent;
  color: #acb3c1;
}
.reply-editor .el-button {
  margin-top: 9px;
}
.reply-target {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #7c8ab2;
  text-align: left;
  padding: 0 10px 8px;
}
.reply-target button {
  background: none;
  border: 0;
  cursor: pointer;
  color: #909bad;
}
.nested-replies {
  margin: 14px 0 0 56px;
  padding: 0 18px 10px;
  border-radius: 12px;
  background: #fafbfe;
}
.expand-replies {
  display: flex;
  align-items: center;
  gap: 7px;
  background: none;
  border: 0;
  font-size: 12px;
  font-weight: 600;
  color: #7187d1;
  cursor: pointer;
  padding: 10px 0 5px 46px;
}
.load-discussion {
  display: block;
  margin: 24px auto;
  border: 0;
  border-radius: 9px;
  background: #f2f5fc;
  color: #7b8dc2;
  padding: 10px 30px;
  cursor: pointer;
}
.visibility {
  font-size: 10px;
  color: #939dad;
  font-weight: 400;
  background: #f1f3f7;
  border-radius: 4px;
  padding: 3px 6px;
}
#replies {
  scroll-margin-top: 84px;
}
@media (max-width: 600px) {
  .detail-inner {
    padding: 24px 18px 50px;
  }
  .page-top {
    padding: 0 18px;
    height: 62px;
  }
  h1 {
    font-size: 20px;
  }
  .nested-replies {
    margin-left: 16px;
    padding: 0 12px 10px;
  }
  .reply-tabs {
    gap: 16px;
  }
  .order-tabs button {
    padding: 4px 8px;
  }
  .expand-replies {
    padding-left: 0;
  }
  .detail-actions {
    justify-content: space-between;
    gap: 15px;
  }
}
</style>

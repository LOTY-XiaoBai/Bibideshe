<template>
  <section class="community">
    <header class="feed-header">
      <div>
        <span class="eyebrow">CAMPUS COMMUNITY</span>
        <h2>社区动态</h2>
        <p v-if="admin && compact" class="monitor-caption">后台监控</p>
      </div>
      <el-button v-if="!admin" type="primary" round @click="composer.open()"
        ><i aria-hidden="true" class="ph ph-pencil-line" /> 发布帖子</el-button
      >
    </header>
    <div v-if="!compact" class="scope-tabs">
      <button
        v-for="tab in scopes"
        :key="tab.value"
        :class="{ active: scope === tab.value }"
        @click="
          scope = tab.value;
          reset();
        "
      >
        {{ tab.label }} <CountBadge :count="tab.value === 'notices' ? unread.data.notices : tab.value === 'reports' ? unread.data.reports : tab.value === 'appeals' && admin ? unread.data.appeals : 0" />
      </button>
    </div>
    <template
      v-if="
        !['notices', 'blocks', 'reports', 'mutes', 'appeals'].includes(scope)
      "
    >
      <div class="feed-toolbar">
        <nav
          v-if="scope === 'community'"
          class="sort-tabs"
          aria-label="帖子排序"
        >
          <button
            v-for="s in sorts"
            :key="s.value"
            :class="{ active: sort === s.value }"
            @click="
              sort = s.value;
              reset();
            "
          >
            {{ s.label }}
          </button>
        </nav>
        <div v-if="scope === 'mine'" class="mine-intro">
          <i aria-hidden="true" class="ph ph-notebook" /> 我的创作
          <span>{{ total }} 篇 · 按发布时间排列</span>
        </div>
        <el-input
          v-if="scope !== 'mine'"
          v-model="q"
          placeholder="搜索校园里的新鲜事"
          clearable
          class="search"
          @keyup.enter="reset"
          @clear="reset"
          ><template #prefix
            ><i aria-hidden="true" class="ph ph-magnifying-glass" /></template
          ><template #append
            ><button class="search-button" @click="reset">
              搜索
            </button></template
          ></el-input
        >
      </div>
      <p v-if="showWarning" class="warning-note">
        社区警告 {{ moderation.warnings }} 次<span
          v-if="moderation.muted_until"
        >
          · 禁言截止 {{ displayTime(moderation.muted_until) }}</span
        >
        <button
          class="dismiss-warning"
          aria-label="关闭社区警告"
          @click="dismissWarning"
        >
          <i aria-hidden="true" class="ph ph-x" />
        </button>
      </p>
      <div
        class="feed-list"

        v-loading="busy"
      >
        <article
          v-for="p in posts"
          :key="p.id"
          class="post-card"
          :class="{ 'pinned-card': scope === 'community' && flag(p.pinned) }"
          tabindex="0"
          role="link"
          :aria-label="'打开帖子：' + p.title"
          @click="open(p.id)"
          @keydown.enter.self="open(p.id)"
        >
          <CommunityAuthor :person="p" :views="p.views"
            ><span
              v-if="scope === 'mine' && p.status === 'HIDDEN'"
              class="private-badge"
              ><i
                aria-hidden="true"
                class="ph ph-lock-simple"
              />仅自己可见</span
            ><span v-if="admin" class="private-badge">{{
              statusName(p.status)
            }}</span></CommunityAuthor
          >
          <div class="post-content">
            <h3>
              <span
                v-if="scope !== 'mine' && scope !== 'friends' && flag(p.pinned)"
                class="post-badge pinned"
                >置顶</span
              ><span
                v-if="
                  scope !== 'mine' && scope !== 'friends' && flag(p.featured)
                "
                class="post-badge featured"
                >精华</span
              >{{ p.title }}
            </h3>
            <p v-if="postText(p)" class="excerpt">{{ postText(p) }}</p>
            <div
              v-if="postImages(p).length"
              class="image-grid"
              :class="'images-' + Math.min(3, postImages(p).length)"
            >
              <div
                v-for="(url, i) in postImages(p).slice(0, 3)"
                :key="url"
                class="thumb"
              >
                <img :src="url" alt="帖子图片" loading="lazy" /><span
                  v-if="i === 2 && postImages(p).length > 3"
                  class="more-images"
                  >+{{ postImages(p).length - 3 }}</span
                >
              </div>
            </div>
            <p
              v-if="p.status === 'FORCED' || p.status === 'DELETED'"
              class="status-note"
            >
              {{
                p.status === "FORCED" ? "管理员已隐藏，可提交申诉" : "帖子已删除"
              }}
            </p>
          </div>
          <footer @click.stop>
            <div v-if="scope !== 'mine' && !admin" class="social-actions">
              <button
                :disabled="admin || p.status !== 'PUBLIC'"
                :title="admin ? '当前管理员身份没有好友和群聊' : '转发到聊天'"
                @click="share.open(p)"
              >
                <i aria-hidden="true" class="ph ph-share-fat" /><span>{{
                  p.share_count || "转发"
                }}</span>
              </button>
              <button @click="open(p.id, true)">
                <i aria-hidden="true" class="ph ph-chat-circle" /><span>{{
                  p.reply_count || "回复"
                }}</span>
              </button>
              <button
                :class="{ liked: flag(p.liked) }"
                :disabled="p.status !== 'PUBLIC' || liking.has(p.id)"
                @click="like(p)"
              >
                <i
                  aria-hidden="true"
                  :class="
                    flag(p.liked) ? 'ph-fill ph-thumbs-up' : 'ph ph-thumbs-up'
                  "
                /><span>{{ p.likes || "点赞" }}</span>
              </button>
            </div>
            <div v-if="admin" class="monitor-actions">
              <button @click="open(p.id, true)">
                <i aria-hidden="true" class="ph ph-chat-circle" /> 查看回复
                {{ p.reply_count || 0 }}</button
              ><span>{{ p.views || 0 }} 浏览</span>
            </div>
            <div
              v-if="
                scope === 'mine' &&
                p.owner === viewer &&
                !['FORCED', 'DELETED'].includes(p.status)
              "
              class="mine-actions"
            >
              <button @click="manage(p, 'edit')">
                <i aria-hidden="true" class="ph ph-pencil-simple" />编辑</button
              ><button
                @click="manage(p, p.status === 'HIDDEN' ? 'restore' : 'hide')"
              >
                <i
                  aria-hidden="true"
                  :class="
                    p.status === 'HIDDEN' ? 'ph ph-eye' : 'ph ph-eye-slash'
                  "
                />{{ p.status === "HIDDEN" ? "恢复公开" : "隐藏" }}</button
              ><button class="delete-action" @click="manage(p, 'delete')">
                <i aria-hidden="true" class="ph ph-trash" />删除
              </button>
            </div>
            <PostSafety
              v-if="!admin && scope !== 'mine' && p.status === 'PUBLIC'"
              :post="p"
              :viewer="viewer"
              @blocked="load"
            />
            <el-dropdown
              v-if="scope === 'admin' && p.status !== 'DELETED'"
              trigger="click"
              @command="(cmd) => manage(p, cmd)"
            >
              <button class="more-button" aria-label="管理员操作">
                <i aria-hidden="true" class="ph ph-shield-check" />
              </button>
              <template #dropdown
                ><el-dropdown-menu
                  ><el-dropdown-item
                    v-if="p.status === 'PUBLIC'"
                    :command="flag(p.featured) ? 'unfeature' : 'feature'"
                    >{{
                      flag(p.featured) ? "取消精华" : "设为精华"
                    }}</el-dropdown-item
                  ><el-dropdown-item
                    v-if="p.status === 'PUBLIC'"
                    :command="flag(p.pinned) ? 'unpin' : 'pin'"
                    >{{
                      flag(p.pinned) ? "取消置顶" : "设为置顶"
                    }}</el-dropdown-item
                  ><el-dropdown-item
                    v-if="p.status !== 'FORCED'"
                    command="forceHide"
                    >隐藏帖子</el-dropdown-item
                  ><el-dropdown-item v-if="['FORCED','HIDDEN'].includes(p.status)" command="restore">恢复公开</el-dropdown-item
                  ><el-dropdown-item command="delete"
                    >管理员删帖</el-dropdown-item
                  ><el-dropdown-item
                    v-if="!p.owner.startsWith('ADMIN:')"
                    command="warn"
                    >警告作者</el-dropdown-item
                  ><el-dropdown-item
                    v-if="!p.owner.startsWith('ADMIN:')"
                    command="mute"
                    >作者禁言24小时</el-dropdown-item
                  ></el-dropdown-menu
                ></template
              >
            </el-dropdown>
          </footer>
        </article>
        <el-empty
          v-if="!busy && !posts.length"
          description="这里还很安静，分享你的第一条动态吧"
          :image-size="100"
        />
      </div>
      <div ref="sentinel" class="feed-sentinel">
        <el-button v-if="hasMorePosts" :loading="busy" @click="startMore">{{ autoLoad ? '继续加载' : '加载更多' }}</el-button>
        <span v-else-if="posts.length">已显示全部帖子</span>
      </div>
    </template>
    <section v-else-if="scope === 'notices'" class="notifications">
      <div class="notice-header">
        <span>与你有关的校园消息</span
        ><el-button text @click="read">全部标为已读</el-button>
      </div>
      <article v-for="n in notices" :key="n.id" class="notice-card">
        <span class="notice-icon" :class="{ unread: !flag(n.is_read) }"
          ><i aria-hidden="true" :class="noticeIcon(n.kind)"
        /></span>
        <div>
          <p>{{ n.message }}</p>
          <time>{{ displayTime(n.created_at) }}</time>
        </div>
        <button v-if="n.post_id" @click="openNotice(n)">
          查看帖子 <i aria-hidden="true" class="ph ph-arrow-up-right" />
        </button>
      </article>
      <el-empty
        v-if="!notices.length"
        description="暂时没有新通知"
        :image-size="90"
      />
      <el-button
        v-if="noticeHasMore"
        class="load-more"
        :loading="noticeLoading"
        @click="loadMoreNotices"
        >加载更多通知</el-button
      >
    </section>
    <CommunityBlocks v-if="scope === 'blocks'" />
    <CommunityReports v-if="scope === 'reports'" />
    <CommunityModeration v-if="scope === 'mutes'" />
    <CommunityAppeals v-if="scope === 'appeals'" />
    <PostComposer v-if="!admin" ref="composer" @saved="reset" /><CommunityShare
      v-if="!admin"
      ref="share"
      @shared="
        (id, count) => {
          const p = posts.find((p) => p.id === id);
          if (p) p.share_count = Number(p.share_count || 0) + count;
        }
      "
    />
  </section>
</template>
<script setup>
import { ref, computed, onMounted, onUnmounted, reactive, watch, nextTick } from "vue";
import { useRoute, useRouter } from "vue-router";
import { getRole } from "@/utils/auth";
import { ElMessage, ElMessageBox } from "element-plus";
import request from "@/api/request";
import { postImages, postText, displayTime, flag } from "@/utils/community";
import CommunityAuthor from "./CommunityAuthor.vue";
import PostSafety from "./PostSafety.vue";
import CommunityBlocks from "./CommunityBlocks.vue";
import CommunityReports from "./CommunityReports.vue";
import PostComposer from "./PostComposer.vue";
import CommunityShare from "./CommunityShare.vue";
import CommunityModeration from "./CommunityModeration.vue";
import CommunityAppeals from "./CommunityAppeals.vue";
import CountBadge from "./CountBadge.vue";
import { useUnread } from "@/stores/unread";
const unread=useUnread(), route=useRoute();
const props=defineProps({ compact: Boolean });
const router = useRouter(),
  admin = computed(() => getRole() === "ADMIN"),
  composer = ref(),
  share = ref(),
  scope = ref(!props.compact && ["community","admin","mine","friends","notices","blocks","reports","mutes","appeals"].includes(route.query.scope) ? route.query.scope : getRole() === "ADMIN" ? "admin" : "community"),
  sort = ref("latest"),
  q = ref(""),
  page = ref(1),
  sentinel = ref(), autoLoad = ref(false), failed = ref(false), fetchedOffset=ref(0), hasMorePosts=ref(false),
  total = ref(0),
  posts = ref([]),
  viewer = ref(""),
  moderation = ref({}),
  busy = ref(false),
  notices = ref([]),
  noticePage = ref(1),
  noticeHasMore = ref(false),
  noticeLoading = ref(false),
  dismissedWarning = ref(""),
  liking = reactive(new Set());
const scopes = computed(() =>
  admin.value
    ? [
        { value: "admin", label: "后台监控" },
        { value: "mutes", label: "禁言与警告" },
        { value: "appeals", label: "申诉审核" },
        { value: "reports", label: "举报审核" },
      ]
    : [
        { value: "community", label: "社区" },
        { value: "mine", label: "我的发帖" },
        { value: "friends", label: "好友动态" },
        { value: "notices", label: "通知" },
        { value: "blocks", label: "屏蔽管理" },
        { value: "appeals", label: "我的申诉" },
      ],
);
const sorts = [
  { value: "featured", label: "精华" },
  { value: "hot", label: "热门" },
  { value: "latest", label: "最新" },
];
let generation = 0;
async function load(append = false) {
  if (append && (busy.value || !autoLoad.value || !hasMorePosts.value)) return;
  failed.value=false;
  const current = ++generation;
  busy.value = true;
  try {
    const r = (
      await request.get("/api/community/posts", {
        params: {
          q: scope.value === "mine" ? "" : q.value,
          sort: scope.value === "community" ? sort.value : "latest",
          scope: scope.value,
          offset: append ? fetchedOffset.value : 0,
          size: append ? 10 : 5,
        },
      })
    ).data;
    if (current !== generation) return;
    posts.value = append ? [...posts.value,...r.list.filter(p=>!posts.value.some(old=>old.id===p.id))] : r.list;
    fetchedOffset.value=(append?fetchedOffset.value:0)+r.list.length;
    hasMorePosts.value=r.list.length>0 && fetchedOffset.value<r.total;
    total.value = r.total;
    viewer.value = r.owner;
    moderation.value = r.moderation;
  } catch {if(current===generation)failed.value=true;
  } finally {
    if (current === generation) {busy.value = false; await nextTick(); checkMore();}
  }
}
async function reset() {
  page.value = 1;
  autoLoad.value=false;
  clearTimeout(retryTimer);
  if (["blocks", "reports", "mutes", "appeals"].includes(scope.value)) {
    generation++;
    busy.value = false;
    return;
  }
  if (scope.value === "notices") {
    generation++;
    noticePage.value = 1;
    await loadNotices();
  } else await load();
}
let observer, retryTimer;
function checkMore(){
  clearTimeout(retryTimer);
  if(!autoLoad.value || busy.value || failed.value || !hasMorePosts.value || !sentinel.value)return;
  const rect=sentinel.value.getBoundingClientRect();
  let parent=sentinel.value.parentElement;
  while(parent && !/(auto|scroll)/.test(getComputedStyle(parent).overflowY))parent=parent.parentElement;
  const bottom=parent?parent.getBoundingClientRect().bottom:window.innerHeight;
  if(rect.top<=bottom+80 && rect.bottom>=0)retryTimer=setTimeout(()=>load(true),100);
}
function startMore(){autoLoad.value=true;load(true);}
watch(scope,()=>{if(!props.compact)router.replace({query:{...route.query,scope:scope.value}})});
watch(sentinel,el=>{observer?.disconnect();if(el){observer=new IntersectionObserver(entries=>{if(entries[0].isIntersecting)checkMore()},{rootMargin:'80px'});observer.observe(el)}});
onUnmounted(()=>{observer?.disconnect();clearTimeout(retryTimer);generation++});
function open(id, reply = false) {
  router.push({ path: "/community/post/" + id, query:{returnTo:route.fullPath}, hash: reply ? "#replies" : "" });
}
async function like(p) {
  if (liking.has(p.id)) return;
  liking.add(p.id);
  try {
    await request.post("/api/community/posts/" + p.id + "/action", {
      action: "like",
    });
    p.likes = Number(p.likes || 0) + (flag(p.liked) ? -1 : 1);
    p.liked = !flag(p.liked);
  } catch {
  } finally {
    liking.delete(p.id);
  }
}
async function manage(p, act) {
  if (act === "warn" || act === "mute") {
    try {
      const { value } = await ElMessageBox.prompt(
        "填写处理原因，将通知作者并记入操作记录",
        act === "warn" ? "警告作者" : "作者禁言24小时",
        {
          inputValidator: (v) => Boolean(v?.trim()) || "请填写处理原因",
          inputType: "textarea",
          confirmButtonText: "确认处理",
          cancelButtonText: "取消",
        },
      );
      await request.post("/api/community/moderate-user", {
        owner: p.owner,
        postId: p.id,
        action: act === "warn" ? "WARN" : "MUTE",
        reason: value,
      });
      ElMessage.success("已处理并记录操作");
    } catch {}
    return;
  }
  if (act === "edit") {
    composer.value.open(p);
    return;
  }
  if (act === "delete" || act === "forceHide") {
    const message =
      act === "forceHide"
        ? "隐藏后其他用户不可见，作者可提交申诉，是否继续？"
        : "删除后无法恢复，是否删除这条帖子？";
    const ok = await ElMessageBox.confirm(
      message,
      act === "forceHide" ? "隐藏帖子" : "删除帖子",
      {
        type: "warning",
        confirmButtonText: act === "forceHide" ? "确认隐藏" : "确认删除",
        cancelButtonText: "取消",
      },
    )
      .then(() => true)
      .catch(() => false);
    if (!ok) return;
  }
  try {
    await request.post("/api/community/posts/" + p.id + "/action", {
      action: act,
    });
    ElMessage.success(
      act === "hide"
        ? "已设为仅自己可见，可在我的发帖中恢复公开"
        : act === "restore"
          ? "帖子已恢复公开"
          : "操作成功",
    );
    await load();
  } catch {}
}
async function loadNotices(append = false) {
  noticeLoading.value = true;
  try {
    const r = (
      await request.get("/api/community/notices", {
        params: { page: noticePage.value },
      })
    ).data;
    notices.value = append ? [...notices.value, ...r.list] : r.list;
    noticeHasMore.value = Boolean(r.hasMore);
  } catch {
  } finally {
    noticeLoading.value = false;
  }
}
async function loadMoreNotices() {
  noticePage.value++;
  await loadNotices(true);
}
async function openNotice(n){try{await request.post("/api/community/notices/"+n.id+"/read");n.is_read=true;open(n.post_id)}catch{}}
async function read() {
  try {
    await request.post("/api/community/notices/read");
    notices.value.forEach((n) => (n.is_read = true));
  } catch {}
}
function noticeIcon(kind) {
  return (
    {
      LIKE: "ph ph-thumbs-up",
      REPLY_LIKE: "ph ph-thumbs-up",
      REPLY: "ph ph-chat-circle",
      FEATURED: "ph ph-star",
      WARNING: "ph ph-warning",
      FORCED: "ph ph-eye-slash",
      DELETED: "ph ph-trash",
      REPLY_DELETED: "ph ph-trash",
    }[kind] || "ph ph-bell"
  );
}
const warningKey = computed(
  () => `${moderation.value.warnings}:${moderation.value.muted_until || ""}`,
);
const showWarning = computed(
  () =>
    !admin.value &&
    moderation.value.warnings &&
    dismissedWarning.value !== warningKey.value &&
    sessionStorage.getItem("community-warning:" + viewer.value) !==
      warningKey.value,
);
function dismissWarning() {
  dismissedWarning.value = warningKey.value;
  sessionStorage.setItem("community-warning:" + viewer.value, warningKey.value);
}
function statusName(status) {
  return (
    {
      PUBLIC: "公开中",
      HIDDEN: "作者已隐藏",
      FORCED: "已隐藏帖子",
      DELETED: "已删除",
    }[status] || status
  );
}
onMounted(reset);
</script>
<style scoped>
.monitor-caption {
  color: #91a0ba;
  font-size: 12px;
  margin: 8px 0 0;
}
.monitor-actions {
  display: flex;
  gap: 20px;
  align-items: center;
  font-size: 12px;
  color: #9aa7bb;
}
.monitor-actions button {
  background: none;
  border: 0;
  color: #7387b4;
  cursor: pointer;
  display: flex;
  gap: 6px;
  align-items: center;
}
.dismiss-warning {
  margin-left: auto;
  float: right;
  border: 0;
  background: none;
  color: inherit;
  cursor: pointer;
  font-size: 16px;
}
.mine-intro {
  display: flex;
  gap: 9px;
  align-items: center;
  color: #536581;
  font-size: 14px;
  padding: 18px 0;
}
.mine-intro > i {
  font-size: 20px;
  color: #8194c3;
}
.mine-intro span {
  font-size: 12px;
  color: #a1aabb;
  margin-left: 8px;
}
.mine-actions {
  display: flex;
  gap: 8px;
  width: 100%;
  padding: 9px 0 1px;
}
.mine-actions button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid #e6ebf5;
  background: #fff;
  border-radius: 8px;
  padding: 8px 16px;
  font-size: 12px;
  color: #7182a1;
  cursor: pointer;
  transition: all 0.15s;
}
.mine-actions button:hover {
  border-color: #b9c9f6;
  background: #f0f4ff;
  color: #5572c6;
}
.mine-actions i {
  font-size: 16px;
}
.mine-actions .delete-action {
  margin-left: auto;
  color: #c17f86;
  border-color: #f1e7e9;
}
.mine-actions .delete-action:hover {
  background: #fff3f4;
  border-color: #ebc9ce;
}
.scope-tabs {
  flex-wrap: wrap;
}
.mine-actions + .el-dropdown {
  margin-left: 8px;
}

.community {
  background: white;
  border-radius: 18px;
  padding: 26px 24px;
  margin-top: 24px;
  color: #273246;
  min-width: 0;
}
.feed-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 22px;
}
.feed-header h2 {
  margin: 5px 0 0;
  font-size: 23px;
  letter-spacing: -0.5px;
}
.eyebrow {
  font-size: 10px;
  letter-spacing: 2px;
  color: #9aa5bb;
}
.feed-header .el-button i {
  font-size: 17px;
  margin-right: 7px;
}
.scope-tabs {
  display: flex;
  gap: 25px;
  border-bottom: 1px solid #eef1f6;
  margin-bottom: 10px;
}
.scope-tabs button {
  border: 0;
  background: none;
  cursor: pointer;
  color: #949aab;
  padding: 10px 1px 15px;
  font-size: 14px;
}
.scope-tabs button.active {
  color: #35425d;
  font-weight: 650;
  border-bottom: 2px solid #344563;
}
.feed-toolbar {
  display: flex;
  gap: 20px;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #edf0f6;
}
.sort-tabs {
  display: flex;
  gap: 26px;
  flex-shrink: 0;
}
.sort-tabs button {
  background: none;
  border: 0;
  color: #767e91;
  font-size: 14px;
  padding: 17px 0;
  position: relative;
  cursor: pointer;
}
.sort-tabs button.active {
  color: #35405b;
  font-weight: 650;
}
.sort-tabs button.active:after {
  content: "";
  position: absolute;
  height: 3px;
  background: #5e7cfa;
  border-radius: 3px;
  bottom: 0;
  left: 0;
  right: 0;
}
.search {
  max-width: 290px;
}
.search :deep(.el-input__wrapper) {
  box-shadow: none;
  background: #f6f8fc;
  border-radius: 8px 0 0 8px;
}
.search :deep(.el-input-group__append) {
  box-shadow: none;
  padding: 0 12px;
  background: #f1f4f9;
}
.search-button {
  background: none;
  border: 0;
  color: #74809c;
  font-size: 12px;
  cursor: pointer;
}
.post-card {
  padding: 23px 14px 15px;
  border-bottom: 1px solid #edf0f6;
  border-radius: 12px;
  cursor: pointer;
  transition:
    background 0.18s,
    box-shadow 0.18s;
}
.post-card:hover {
  background: #f5f7fc;
  box-shadow: inset 3px 0 #9aaeff;
}
.post-card:focus-visible {
  outline: 2px solid #829af6;
  outline-offset: -2px;
}
.pinned-card {
  background: #f8f9fd;
  margin-top: 8px;
}
.post-content {
  padding: 0 0 0 56px;
}
.post-content h3 {
  font-size: 16px;
  line-height: 1.6;
  margin: 15px 0 7px;
  color: #25314b;
  overflow-wrap: anywhere;
}
.post-badge {
  font-size: 11px;
  vertical-align: 2px;
  line-height: 18px;
  padding: 1px 4px;
  margin-right: 7px;
  border-radius: 4px;
  border: 1px solid;
}
.pinned {
  color: #6c83e4;
  border-color: #dbe3ff;
  background: #f2f5ff;
}
.featured {
  color: #a882c6;
  border-color: #eadff4;
  background: #faf5ff;
}
.excerpt {
  font-size: 14px;
  line-height: 1.9;
  color: #5f687b;
  white-space: pre-wrap;
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  overflow-wrap: anywhere;
}
.image-grid {
  display: grid;
  gap: 8px;
  margin-top: 14px;
  max-width: 660px;
}
.images-1 {
  grid-template-columns: minmax(0, 320px);
}
.images-2 {
  grid-template-columns: repeat(2, minmax(0, 220px));
}
.images-3 {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.thumb {
  aspect-ratio: 1.25;
  position: relative;
  border-radius: 9px;
  overflow: hidden;
  background: #f0f2f8;
}
.images-1 .thumb {
  aspect-ratio: 1.55;
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.2s;
}
.post-card:hover .thumb img {
  transform: scale(1.025);
}
.more-images {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: #15213a5c;
  color: white;
  font-size: 26px;
}
footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 16px 0 0 56px;
}
.social-actions {
  display: flex;
  gap: clamp(24px, 6vw, 80px);
}
.social-actions button,
.more-button {
  border: 0;
  background: none;
  display: flex;
  align-items: center;
  gap: 7px;
  color: #9098aa;
  cursor: pointer;
  padding: 6px 0;
  font-size: 12px;
}
.social-actions i {
  font-size: 20px;
}
.social-actions button:hover,
.social-actions button.liked {
  color: #5e7cfa;
}
.social-actions button:disabled {
  opacity: 0.4;
  cursor: default;
}
.more-button {
  font-size: 23px;
  padding: 5px 9px;
}
.private-badge {
  background: #eef0f6;
  color: #8b92a6;
  font-size: 11px;
  font-weight: 400;
  padding: 3px 7px;
  border-radius: 5px;
  display: inline-flex;
  gap: 4px;
  align-items: center;
}
.danger {
  color: #dc6d73;
}
.status-note {
  color: #b28c6b;
  font-size: 12px;
}
.warning-note {
  font-size: 12px;
  color: #b39065;
  background: #fffbf1;
  padding: 10px;
  border-radius: 8px;
}
.feed-sentinel { display:flex;justify-content:center; padding:24px; color:#94a3b8;font-size:12px; }
.el-pagination {
  justify-content: center;
  margin-top: 20px;
}
.notice-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 15px 0;
  color: #9299a9;
  font-size: 12px;
}
.notice-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 22px 4px;
  border-bottom: 1px solid #f0f2f7;
}
.notice-icon {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  background: #f6f7fb;
  border-radius: 13px;
  color: #a0a7b5;
  font-size: 20px;
  flex-shrink: 0;
}
.notice-icon.unread {
  background: #edf2ff;
  color: #7188e7;
}
.notice-card > div {
  flex: 1;
  min-width: 0;
}
.notice-card p {
  margin: 0 0 8px;
  font-size: 14px;
  line-height: 1.6;
}
.notice-card time {
  font-size: 12px;
  color: #a2a9b7;
}
.notice-card > button {
  background: none;
  border: 0;
  color: #7287cd;
  cursor: pointer;
  flex-shrink: 0;
  font-size: 12px;
}
.load-more {
  display: block;
  margin: 20px auto;
}
.moderation-panel {
  margin-top: 24px;
  padding: 20px;
  background: #f7f9fc;
  border-radius: 12px;
}
.moderation-panel h3 {
  font-size: 15px;
}
.moderation-panel p {
  font-size: 12px;
  color: #8b94a7;
}
.word-input {
  display: flex;
  gap: 10px;
  margin: 12px 0;
}
.moderation-panel .el-tag {
  margin: 4px;
}
.mute-row {
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.el-dropdown-menu i {
  margin-right: 8px;
}
@media (max-width: 640px) {
  .community {
    padding: 20px 12px;
  }
  .feed-toolbar {
    flex-wrap: wrap;
    gap: 0;
    padding-bottom: 12px;
  }
  .search {
    max-width: none;
    order: -1;
  }
  .scope-tabs {
    gap: 20px;
  }
  .sort-tabs {
    gap: 35px;
  }
  .post-card {
    padding: 18px 8px;
  }
  .post-content {
    padding-left: 0;
  }
  footer {
    margin-left: 0;
  }
  .post-content h3 {
    font-size: 15px;
  }
  .social-actions {
    gap: 35px;
  }
  .image-grid {
    gap: 5px;
  }
  .notice-card {
    gap: 9px;
  }
  .notice-card > button {
    font-size: 11px;
  }
}
</style>

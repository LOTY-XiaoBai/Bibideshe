<template>
  <div v-if="!self" class="post-safety" @click.stop>
    <el-button
      v-if="showFollow && canAddFriend"
      :type="friendStatus === 'NONE' ? 'primary' : 'info'"
      :plain="friendStatus !== 'NONE'"
      :disabled="['FRIEND', 'REQUEST_SENT'].includes(friendStatus)"
      :title="
        friendStatus === 'NONE'
          ? '发送好友申请，对方同意后成为好友'
          : friendLabel
      "
      round
      size="small"
      :loading="busy"
      @click="follow"
      ><i
        aria-hidden="true"
        :class="friendStatus === 'NONE' ? 'ph ph-plus' : 'ph ph-check'"
      />{{ friendLabel }}</el-button
    ><el-dropdown trigger="click" @command="command"
      ><button class="safety-more" aria-label="举报与屏蔽">
        <i aria-hidden="true" class="ph ph-dots-three" /></button
      ><template #dropdown
        ><el-dropdown-menu
          ><el-dropdown-item command="report"
            ><i
              aria-hidden="true"
              class="ph ph-flag"
            />举报帖子</el-dropdown-item
          ><el-dropdown-item command="block"
            ><i
              aria-hidden="true"
              class="ph ph-eye-slash"
            />不想看到</el-dropdown-item
          ></el-dropdown-menu
        ></template
      ></el-dropdown
    >
  </div>
  <el-dialog
    v-model="reporting"
    title="举报帖子"
    width="min(460px,94vw)"
    append-to-body
    ><p class="summary">{{ post.title }}</p>
    <p class="hint">请选择举报原因，管理员会审核处理。</p>
    <el-select v-model="reason" placeholder="选择举报原因" style="width: 100%"
      ><el-option v-for="r in reasons" :key="r" :value="r" /></el-select
    ><el-input
      v-model="details"
      class="details"
      type="textarea"
      :rows="4"
      maxlength="1000"
      show-word-limit
      placeholder="补充说明，帮助管理员了解情况（可选）"
    /><template #footer
      ><el-button @click="reporting = false">取消</el-button
      ><el-button
        type="primary"
        :loading="busy"
        :disabled="!reason"
        @click="report"
        >提交举报</el-button
      ></template
    ></el-dialog
  ><el-dialog
    v-model="blocking"
    title="选择屏蔽范围"
    width="min(460px,94vw)"
    append-to-body
    ><button
      class="block-choice"
      :class="{ selected: kind === 'POST' }"
      @click="kind = 'POST'"
    >
      <i aria-hidden="true" class="ph ph-file-x" /><span
        ><strong>只屏蔽这条帖子</strong
        ><small>不再出现在你的社区推荐中</small></span
      ><i
        v-if="kind === 'POST'"
        aria-hidden="true"
        class="ph ph-check-circle"
      /></button
    ><button
      class="block-choice"
      :class="{ selected: kind === 'AUTHOR' }"
      @click="kind = 'AUTHOR'"
    >
      <i aria-hidden="true" class="ph ph-user-minus" /><span
        ><strong>屏蔽这位作者的帖子</strong
        ><small>隐藏对方现在和以后发布的所有帖子</small></span
      ><i
        v-if="kind === 'AUTHOR'"
        aria-hidden="true"
        class="ph ph-check-circle"
      />
    </button>
    <p class="hint">屏蔽只影响你自己，可在“动态 → 屏蔽管理”中取消。</p>
    <template #footer
      ><el-button @click="blocking = false">取消</el-button
      ><el-button type="primary" :loading="busy" @click="block"
        >确认屏蔽</el-button
      ></template
    ></el-dialog
  >
</template>
<script setup>
import { computed, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import request from "@/api/request";
import { sendFriendRequest } from "@/api/friend";
import { useRouter } from "vue-router";
import { getRole } from "@/utils/auth";
const props = defineProps({
    post: { type: Object, required: true },
    viewer: String,
    showFollow: Boolean,
  }),
  emit = defineEmits(["blocked", "friend-requested"]);
const router = useRouter();
const self = computed(() => props.post.owner === props.viewer),
  friendStatus = ref("UNAVAILABLE"),
  canAddFriend = computed(
    () => !["UNAVAILABLE", "SELF"].includes(friendStatus.value),
  ),
  friendLabel = computed(
    () =>
      ({
        NONE: "关注",
        FRIEND: "已是好友",
        REQUEST_SENT: "已申请",
        REQUEST_RECEIVED: "处理好友申请",
      })[friendStatus.value],
  ),
  busy = ref(false),
  reporting = ref(false),
  blocking = ref(false),
  reason = ref(""),
  details = ref(""),
  kind = ref("POST"),
  reasons = [
    "违法违规",
    "辱骂攻击",
    "广告骚扰",
    "虚假信息",
    "侵犯隐私",
    "其他",
  ];
watch(
  () => props.post.friend_status,
  (v) => (friendStatus.value = v || "UNAVAILABLE"),
  { immediate: true },
);
function command(value) {
  if (self.value) return;
  if (value === "report") {
    reason.value = "";
    details.value = "";
    reporting.value = true;
  } else {
    kind.value = "POST";
    blocking.value = true;
  }
}
async function follow() {
  if (self.value || !canAddFriend.value) return;
  if (friendStatus.value === "REQUEST_RECEIVED") {
    router.push("/" + getRole().toLowerCase() + "/chat?tab=requests");
    return;
  }
  if (friendStatus.value !== "NONE") return;
  busy.value = true;
  try {
    await sendFriendRequest(props.post.author_id);
    friendStatus.value = "REQUEST_SENT";
    emit("friend-requested");
    ElMessage.success("好友申请已发送，等待对方同意");
  } catch {
  } finally {
    busy.value = false;
  }
}
async function report() {
  if (!reason.value || self.value) return;
  busy.value = true;
  try {
    await request.post("/api/community/posts/" + props.post.id + "/report", {
      reason: reason.value,
      details: details.value,
    });
    reporting.value = false;
    ElMessage.success("举报已提交，管理员审核后会通知你");
  } catch {
  } finally {
    busy.value = false;
  }
}
async function block() {
  if (self.value) return;
  busy.value = true;
  try {
    await request.post("/api/community/posts/" + props.post.id + "/block", {
      kind: kind.value,
    });
    blocking.value = false;
    emit("blocked");
    ElMessage.success("已屏蔽，可在屏蔽管理中取消");
  } catch {
  } finally {
    busy.value = false;
  }
}
</script>
<style scoped>
.post-safety {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.post-safety .el-button i {
  margin-right: 4px;
}
.safety-more {
  background: none;
  border: 0;
  color: #929bad;
  padding: 5px 8px;
  font-size: 23px;
  cursor: pointer;
}
.summary {
  background: #f5f7fc;
  padding: 14px;
  border-radius: 10px;
  color: #54617b;
  overflow-wrap: anywhere;
}
.hint {
  color: #939dae;
  font-size: 12px;
  line-height: 1.7;
}
.details {
  margin-top: 16px;
}
.block-choice {
  width: 100%;
  display: flex;
  gap: 13px;
  align-items: center;
  text-align: left;
  background: #fafbfe;
  border: 1px solid #edf0f6;
  border-radius: 12px;
  padding: 18px;
  margin: 12px 0;
  cursor: pointer;
  color: #6f7b94;
}
.block-choice.selected {
  border-color: #a9b9fa;
  background: #f3f6ff;
}
.block-choice > i {
  font-size: 24px;
}
.block-choice span {
  flex: 1;
}
.block-choice strong {
  display: block;
  font-size: 14px;
  color: #46536c;
}
.block-choice small {
  display: block;
  margin-top: 7px;
  font-size: 12px;
  color: #96a0b2;
}
</style>

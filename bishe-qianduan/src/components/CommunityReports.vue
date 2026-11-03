<template>
  <section v-loading="busy" class="reports">
    <div class="report-heading">
      <h3>
        举报审核
        <span>待处理 <CountBadge :count="unread.data.reports ?? pending" /></span>
      </h3>
      <el-select
        v-model="status"
        style="width: 130px"
        @change="
          page = 1;
          load();
        "
        ><el-option label="待处理" value="PENDING" /><el-option
          label="已处置"
          value="RESOLVED" /><el-option
          label="未成立"
          value="DISMISSED" /><el-option label="申诉已撤销" value="OVERTURNED"/><el-option label="全部举报" value="ALL"
      /></el-select>
    </div>
    <article v-for="r in reports" :key="r.id">
      <div class="report-title">
        <strong>{{ r.post_title }}</strong
        ><el-tag
          size="small"
          :type="r.status === 'PENDING' ? 'warning' : 'info'"
          >{{ statusName(r.status) }}</el-tag
        >
      </div>
      <p class="report-meta">
        举报人 {{ r.reporter }} · 作者 {{ r.post_owner }} ·
        {{ displayTime(r.created_at) }}
      </p>
      <p>
        <b>{{ r.reason }}</b> {{ r.details || "未填写补充说明" }}
      </p>
      <blockquote>{{ r.content_snapshot || "该帖只有标题或图片" }}</blockquote>
      <p v-if="r.feedback" class="feedback">
        处理说明：{{ r.feedback }} · {{ r.reviewer }}
      </p>
      <div class="report-actions">
        <el-button text @click="router.push({path:'/community/post/'+r.post_id,query:{returnTo:route.fullPath}})"
          >查看帖子</el-button
        ><el-button
          v-if="r.status === 'PENDING'"
          type="primary"
          plain
          size="small"
          @click="open(r)"
          >处理举报</el-button
        >
      </div>
    </article>
    <el-empty
      v-if="!reports.length && !busy"
      description="当前没有举报记录"
      :image-size="80"
    /><ListPager v-model:page="page" v-model:size="size" :total="total" @change="load" /><el-dialog v-model="reviewing" title="处理举报" width="min(500px,94vw)"
      ><p>{{ current?.post_title }}</p>
      <el-select v-model="decision" style="width: 100%"
        ><el-option label="举报成立：隐藏帖子" value="hide" /><el-option
          label="举报成立：删除帖子"
          value="delete" /><el-option
          label="举报未成立"
          value="dismiss" /></el-select
      >
      <template v-if="decision!=='dismiss'"><el-checkbox v-model="mute">同时禁言作者</el-checkbox><MuteDuration v-if="mute" :key="reviewing" v-model="muteMinutes" /></template>
      <el-input
        v-model="feedback"
        type="textarea"
        :rows="4"
        maxlength="500"
        show-word-limit
        placeholder="填写处理依据，会通知举报人；作者可以对处置结果提交申诉"
        style="margin-top: 16px"
      /><template #footer
        ><el-button @click="reviewing = false">取消</el-button
        ><el-button
          type="primary"
          :disabled="!feedback.trim()"
          :loading="saving"
          @click="resolve"
          >确认处理</el-button
        ></template
      ></el-dialog
    >
  </section>
</template>
<script setup>
import { ref, onMounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import request from "@/api/request";
import { displayTime } from "@/utils/community";
import CountBadge from './CountBadge.vue';
import ListPager from './ListPager.vue';
import MuteDuration from './MuteDuration.vue';
import {useUnread} from '@/stores/unread';
const unread=useUnread(),route=useRoute();
const mute=ref(false),muteMinutes=ref('60');
const router = useRouter(),
  reports = ref([]),
  status = ref(["PENDING","RESOLVED","DISMISSED","OVERTURNED","ALL"].includes(route.query.reportStatus)?route.query.reportStatus:"PENDING"),
  page = ref(Math.max(1,Number(route.query.reportPage)||1)),
  size = ref([5,10,20,50].includes(Number(route.query.reportSize))?Number(route.query.reportSize):20),
  total = ref(0),
  pending = ref(0),
  busy = ref(false),
  reviewing = ref(false),
  current = ref(null),
  decision = ref("hide"),
  feedback = ref(""),
  saving = ref(false);
function statusName(s) {
  return { PENDING: "待处理", RESOLVED: "已处置", DISMISSED: "未成立", OVERTURNED:"申诉已撤销" }[s] || s;
}
async function load() {
  busy.value = true;
  try {
    const r = (
      await request.get("/api/community/reports", {
        params: { status: status.value, page: page.value, size:size.value },
      })
    ).data;
    reports.value = r.list;
    total.value = r.total;
    pending.value = r.pending;
  } catch {
  } finally {
    busy.value = false;
  }
}
function open(r) {
  current.value = r;
  mute.value=false;muteMinutes.value="60";
  decision.value = "hide";
  feedback.value = "";
  reviewing.value = true;
}
async function resolve() {
  saving.value = true;
  try {
    await request.post(
      "/api/community/reports/" + current.value.id + "/resolve",
      { decision: decision.value, feedback: feedback.value, mute: String(mute.value && decision.value!=="dismiss"), muteMinutes:muteMinutes.value },
    );
    reviewing.value = false;
    ElMessage.success("举报已处理并通知相关用户");
    await load();
  } catch {
  } finally {
    saving.value = false;
  }
}
watch([status,page,size],()=>router.replace({query:{...route.query,reportStatus:status.value,reportPage:page.value,reportSize:size.value}}));
onMounted(load);
</script>
<style scoped>
.reports {
  padding-top: 15px;
}
.report-heading,
.report-title,
.report-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: space-between;
}
.report-heading h3 {
  font-size: 16px;
  color: #42516f;
}
article {
  padding: 22px 0;
  border-bottom: 1px solid #edf0f6;
  font-size: 13px;
  color: #636f85;
}
.report-title strong {
  font-size: 15px;
  color: #354562;
}
.report-meta {
  font-size: 11px;
  color: #a0a9b9;
}
blockquote {
  white-space: pre-wrap;
  background: #f7f9fc;
  border-left: 3px solid #d8e0f5;
  margin: 15px 0;
  padding: 14px;
  color: #9099ab;
  max-height: 120px;
  overflow: auto;
}
.feedback {
  color: #7f90ad;
}
.el-pagination {
  justify-content: center;
  margin-top: 20px;
}
</style>

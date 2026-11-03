<template>
  <section class="operations" v-loading="busy">
    <header>
      <div>
        <h3>操作记录</h3>
        <p>记录所有管理员的处理操作，保留操作人、对象、帖子、说明和时间。</p>
      </div>
      <el-button text @click="load">刷新</el-button>
    </header>
    <el-input
      v-model="q"
      clearable
      placeholder="搜索管理员、用户ID、帖子或操作"
      @keyup.enter="search"
      @clear="search"
      ><template #append
        ><el-button @click="search">搜索</el-button></template
      ></el-input
    >
    <article v-for="r in rows" :key="r.id">
      <div class="operation-icon">
        <i aria-hidden="true" class="ph ph-clipboard-text" />
      </div>
      <div class="operation-body">
        <div class="operation-title">
          <strong>{{ r.action }}</strong
          ><time>{{ displayTime(r.created_at) }}</time>
        </div>
        <p>
          <b>{{ r.operator }}</b> · {{ r.target_type }} #{{ r.target_id }}
          <span v-if="r.target_owner">· 对象 {{ r.target_owner }}</span>
        </p>
        <p v-if="r.post_id" class="post-title">
          关联帖子 #{{ r.post_id }}：{{ r.post_title }}
        </p>
        <pre v-if="r.details">{{ r.details }}</pre>
        <el-button
          v-if="r.post_id"
          text
          size="small"
          @click="router.push({path:'/community/post/'+r.post_id,query:{returnTo:route.fullPath}})"
          >查看帖子</el-button
        >
      </div>
    </article>
    <el-empty
      v-if="!rows.length && !busy"
      description="暂无操作记录"
      :image-size="80"
    />
    <ListPager v-model:page="page" v-model:size="size" :total="total" @change="load" />
  </section>
</template>
<script setup>
import { ref, onMounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import request from "@/api/request";
import { displayTime } from "@/utils/community";
import ListPager from "./ListPager.vue";
const route=useRoute();
const router = useRouter(),
  rows = ref([]),
  busy = ref(false),
  page = ref(Math.max(1,Number(route.query.page)||1)),
  size = ref([5,10,20,50].includes(Number(route.query.size))?Number(route.query.size):20),
  total = ref(0),
  q = ref(String(route.query.q||""));
watch([page,size,q],()=>router.replace({query:{...route.query,page:page.value,size:size.value,q:q.value}}));
async function load() {
  busy.value = true;
  try {
    const r = (
      await request.get("/api/community/operations", {
        params: { q: q.value, page: page.value, size:size.value },
      })
    ).data;
    rows.value = r.list;
    total.value = r.total;
  } catch {
  } finally {
    busy.value = false;
  }
}
function search() {
  page.value = 1;
  load();
}
function changePage(v) {
  page.value = v;
  load();
}
onMounted(load);
</script>
<style scoped>
.operations {
  padding-top: 20px;
  color: #53627b;
}
header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  margin-bottom: 18px;
}
h3 {
  font-size: 18px;
  margin: 0;
}
header p {
  font-size: 12px;
  color: #96a1b4;
}
article {
  display: flex;
  gap: 15px;
  padding: 23px 0;
  border-bottom: 1px solid #edf0f7;
}
.operation-icon {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: #f0f5ff;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  color: #7d96ca;
  font-size: 20px;
}
.operation-body {
  flex: 1;
  min-width: 0;
}
.operation-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 15px;
  font-size: 14px;
}
time {
  font-size: 11px;
  color: #a4afbe;
  font-weight: 400;
}
article p {
  font-size: 12px;
  color: #96a1b4;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
article b {
  font-weight: 500;
  color: #7185ab;
}
pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-family: inherit;
  font-size: 12px;
  line-height: 1.9;
  background: #f7f9fd;
  padding: 12px;
  border-radius: 8px;
  max-height: 160px;
  overflow: auto;
}
.post-title {
  color: #607cad;
}
.el-pagination {
  justify-content: center;
  margin-top: 20px;
}
</style>

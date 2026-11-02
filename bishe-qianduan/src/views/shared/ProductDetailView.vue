<template>
  <div class="product" v-loading="loading">
    <el-button @click="router.back()">返回</el-button
    ><template v-if="product"
      ><div class="hero">
        <img
          v-if="product.image_url"
          :src="product.image_url"
          :alt="product.title"
        />
        <div>
          <h1>{{ product.title }}</h1>
          <h2>¥ {{ product.price }}</h2>
          <p>发货地：{{ product.detail.origin || "待商家补充" }}</p>
          <p>总销量 {{ product.sales }} · 综合评分 {{ product.score }}</p>
          <el-button
            v-if="!isAdmin"
            type="primary"
            :loading="buying"
            @click="buy"
            >立即购买</el-button
          >
        </div>
      </div>
      <section>
        <h2>商品图文介绍</h2>
        <p class="description">{{ product.content }}</p>
        <ContentBlocks :value="product.detail.blocks" />
      </section>
      <section>
        <h2>购买评价（{{ product.reviews.length }}）</h2>
        <article v-for="(r, i) in product.reviews" :key="i">
          <el-rate :model-value="r.rating" disabled />
          <p>{{ r.feedback_content || "买家未填写文字评价" }}</p>
          <small>{{ r.publish_time }}</small>
        </article>
        <el-empty v-if="!product.reviews.length" description="暂无购买评价" />
      </section>
      <section v-if="isAdmin">
        <h2>编辑图文详情</h2>
        <el-input
          v-model="origin"
          maxlength="120"
          placeholder="发货地，例如浙江杭州"
        /><BlockEditor v-model="blocks" /><el-button
          type="primary"
          @click="save"
          >保存图文与发货地</el-button
        >
      </section></template
    >
  </div>
</template>
<script setup>
import { ref, computed, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import request from "@/api/request";
import { createOrder } from "@/api/shop";
import { getRole } from "@/utils/auth";
import { ElMessage, ElMessageBox } from "element-plus";
import ContentBlocks from "@/components/ContentBlocks.vue";
import BlockEditor from "@/components/BlockEditor.vue";
const route = useRoute(),
  router = useRouter(),
  product = ref(null),
  loading = ref(false),
  buying = ref(false),
  origin = ref(""),
  blocks = ref("[]"),
  isAdmin = computed(() => getRole() === "ADMIN");
async function load() {
  loading.value = true;
  try {
    product.value = (
      await request.get(`/api/extra/products/${route.params.id}`)
    ).data;
    origin.value = product.value.detail.origin;
    blocks.value = product.value.detail.blocks;
  } finally {
    loading.value = false;
  }
}
async function buy() {
  const p = (await request.get("/api/extra/profile")).data;
  if (!p.recipient || !p.phone || !p.address) {
    await ElMessageBox.alert(
      "请先在个人设置填写收货人、电话和详细地址",
      "完善收货信息",
    );
    router.push(`/${getRole().toLowerCase()}/settings`);
    return;
  }
  const confirmed = await ElMessageBox.confirm(
    `收货信息：${p.recipient} ${p.phone} ${p.address}，确认下单？`,
    "确认购买",
  ).then(() => true).catch(() => false);
  if (!confirmed) return;
  buying.value = true;
  try {
    await createOrder(product.value.ad_id);
    ElMessage.success("下单成功");
    await load();
  } finally {
    buying.value = false;
  }
}
async function save() {
  await request.put(`/api/extra/products/${route.params.id}`, {
    origin: origin.value,
    blocks: blocks.value,
  });
  ElMessage.success("已保存");
  await load();
}
onMounted(load);
</script>
<style scoped>
.product {
  max-width: 1050px;
  margin: auto;
}
.hero {
  display: flex;
  flex-wrap: wrap;
  gap: 32px;
  background: white;
  padding: 28px;
  margin: 20px 0;
  border-radius: 18px;
}
.hero img {
  width: 360px;
  max-width: 100%;
  height: 320px;
  object-fit: contain;
}
.hero h2 {
  color: #ef5c3c;
}
section {
  background: white;
  padding: 28px;
  margin: 20px 0;
  border-radius: 16px;
}
article {
  border-bottom: 1px solid #eee;
  padding: 18px 0;
}
.description {
  white-space: pre-wrap;
}
</style>

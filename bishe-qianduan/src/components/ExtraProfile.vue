<template>
  <section class="extra">
    <h3>详细资料与收货地址</h3>
    <el-form label-position="top"
      ><el-form-item label="性别"
        ><el-select v-model="form.gender"
          ><el-option
            v-for="v in ['保密', '男', '女']"
            :key="v"
            :value="v" /></el-select></el-form-item
      ><el-form-item label="出生日期"
        ><el-date-picker
          v-model="form.birth_date"
          value-format="YYYY-MM-DD"
          :disabled-date="(d) => d > new Date()" /></el-form-item
      ><el-form-item label="学校"
        ><el-input v-model="form.school" maxlength="120" /></el-form-item
      ><el-form-item label="学历"
        ><el-select v-model="form.education" clearable
          ><el-option
            v-for="v in [
              '小学',
              '初中',
              '高中',
              '中专',
              '大专',
              '本科',
              '硕士',
              '博士',
              '其他',
            ]"
            :key="v"
            :value="v" /></el-select></el-form-item
      ><el-form-item label="收货人"
        ><el-input v-model="form.recipient" maxlength="60" /></el-form-item
      ><el-form-item label="联系电话"
        ><el-input v-model="form.phone" maxlength="30" /></el-form-item
      ><el-form-item label="详细地址（省市区、街道、门牌号）"
        ><el-input
          v-model="form.address"
          type="textarea"
          maxlength="500" />
        <el-button v-if="allowLocation" class="locate-address" :loading="locating" :disabled="busy || !loaded" @click="locateAddress">
          获取当前位置
        </el-button></el-form-item
      ><el-button type="primary" :loading="busy" :disabled="locating" @click="save"
        >保存详细资料</el-button
      ></el-form
    >
  </section>
</template>
<script setup>
import { reactive, ref, onMounted } from "vue";
import request from "@/api/request";
import { ElMessage } from "element-plus";
import { getBrowserPosition } from "@/utils/browserPosition";
defineProps({ allowLocation: Boolean });
const form = reactive({
    gender: "保密",
    birth_date: null,
    school: "",
    education: "",
    recipient: "",
    phone: "",
    address: "",
  }),
  busy = ref(false), locating = ref(false), loaded = ref(false);
onMounted(async () => {
  const r = await request.get("/api/extra/profile");
  Object.assign(form, r.data);
  if (form.birth_date) form.birth_date = String(form.birth_date).slice(0, 10);
  loaded.value = true;
});
async function locateAddress() {
  locating.value = true;
  const original = form.address;
  try {
    const position = await getBrowserPosition().catch(error => { ElMessage.warning(error.message); return null; });
    if (!position) return;
    const { data } = await request.post("/api/location/address", {
      latitude: position.latitude, longitude: position.longitude,
    });
    if (form.address === original) form.address = data.address;
  } catch {} finally { locating.value = false; }
}
async function save() {
  busy.value = true;
  try {
    await request.put("/api/extra/profile", form);
    ElMessage.success("详细资料已保存");
  } finally {
    busy.value = false;
  }
}
</script>
<style scoped>
.extra {
  background: white;
  border-radius: 16px;
  padding: 24px;
  margin-top: 24px;
  max-width: 720px;
}
.locate-address { margin-top: 8px; }
</style>

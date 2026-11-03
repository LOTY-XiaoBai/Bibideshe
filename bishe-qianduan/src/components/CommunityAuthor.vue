<template>
  <div class="author">
    <img
      v-if="person.avatar && !failed"
      class="avatar"
      :src="person.avatar"
      alt="作者头像"
      @error="failed = true"
    /><span v-else class="avatar fallback">{{ name.slice(0, 1) }}</span>
    <div>
      <div class="name">
        {{ name }}
        <span v-if="String(person.owner).startsWith('ADMIN:')" class="role"
          >管理员</span
        ><slot />
      </div>
      <div class="meta">
        ID {{ authorId(person) }}
        <span>· {{ displayTime(person.created_at) }}</span
        ><span v-if="views !== undefined"> · {{ views }} 浏览</span>
      </div>
    </div>
  </div>
</template>
<script setup>
import { computed, ref, watch } from "vue";
import { authorId, displayTime } from "@/utils/community";
const props = defineProps({
  person: { type: Object, required: true },
  views: { default: undefined },
});
const failed = ref(false),
  name = computed(
    () => props.person.display_name || props.person.author || "校园用户",
  );
watch(
  () => props.person.avatar,
  () => (failed.value = false),
);
</script>
<style scoped>
.author {
  display: flex;
  gap: 12px;
  align-items: center;
}
.avatar {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: 14px;
  object-fit: cover;
}
.fallback {
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #dce8ff, #dbf6ed);
  color: #4d68b1;
  font-weight: 700;
  font-size: 20px;
}
.name {
  font-weight: 600;
  font-size: 14px;
  color: #30394b;
  display: flex;
  align-items: center;
  gap: 7px;
}
.meta {
  margin-top: 6px;
  color: #9299a8;
  font-size: 12px;
}
.role {
  font-size: 10px;
  background: #edf1ff;
  color: #6078d8;
  padding: 2px 5px;
  border-radius: 4px;
}
@media (max-width: 540px) {
  .meta {
    font-size: 11px;
  }
  .avatar {
    width: 38px;
    height: 38px;
  }
}
</style>

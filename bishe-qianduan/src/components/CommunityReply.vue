<template>
  <article class="reply" :class="{ nested }">
    <CommunityAuthor :person="reply"
      ><span v-if="reply.owner === postOwner" class="op-label"
        >楼主</span
      ></CommunityAuthor
    >
    <div class="reply-body">
      <p :class="{ deleted: flag(reply.deleted) }">
        <span v-if="parentName" class="parent-name"
          >回复 {{ parentName }}：</span
        >{{ reply.content }}
      </p>
      <footer>
        <span>{{ nested ? "" : "第" + floor + "楼" }}</span>
        <div>
          <button
            v-if="!admin"
            :class="{ liked: flag(reply.liked) }"
            :disabled="flag(reply.deleted) || !active || busy"
            @click="$emit('like', reply)"
          >
            <i
              aria-hidden="true"
              :class="
                flag(reply.liked) ? 'ph-fill ph-thumbs-up' : 'ph ph-thumbs-up'
              "
            />{{ reply.likes || "赞" }}</button
          ><button
            v-if="!admin && active && !flag(reply.deleted)"
            @click="$emit('reply', reply)"
          >
            <i aria-hidden="true" class="ph ph-chat-circle" />回复</button
          ><el-dropdown
            v-if="
              !flag(reply.deleted) &&
              (admin || reply.owner === viewer || postOwner === viewer)
            "
            trigger="click"
            @command="$emit('remove', reply)"
            ><button aria-label="回复管理">
              <i aria-hidden="true" class="ph ph-dots-three" /></button
            ><template #dropdown
              ><el-dropdown-menu
                ><el-dropdown-item command="remove"
                  >删除回复</el-dropdown-item
                ></el-dropdown-menu
              ></template
            ></el-dropdown
          >
        </div>
      </footer>
    </div>
    <slot />
  </article>
</template>
<script setup>
import CommunityAuthor from "./CommunityAuthor.vue";
import { flag } from "@/utils/community";
defineProps({
  reply: Object,
  postOwner: String,
  viewer: String,
  admin: Boolean,
  nested: Boolean,
  parentName: String,
  floor: Number,
  active: Boolean,
  busy: Boolean,
});
defineEmits(["like", "reply", "remove"]);
</script>
<style scoped>
.reply {
  padding: 24px 0;
  border-bottom: 1px solid #eef0f5;
}
.reply-body {
  margin-left: 56px;
}
.reply-body > p {
  white-space: pre-wrap;
  line-height: 1.9;
  font-size: 14px;
  color: #465167;
  margin: 12px 0;
  overflow-wrap: anywhere;
}
.reply-body > p.deleted {
  color: #a8aebb;
  font-style: italic;
}
.parent-name {
  color: #7186cf;
}
footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #a0a7b5;
  font-size: 12px;
}
footer > div {
  display: flex;
  align-items: center;
  gap: 22px;
}
button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 0;
  background: none;
  color: #929bad;
  cursor: pointer;
  font-size: 12px;
  padding: 5px 0;
}
button i {
  font-size: 17px;
}
button:hover,
button.liked {
  color: #5e7cfa;
}
button:disabled {
  opacity: 0.4;
  cursor: default;
}
.op-label {
  background: #edf3ff;
  font-size: 10px;
  color: #7490dd;
  border-radius: 3px;
  padding: 2px 5px;
}
.nested {
  padding: 18px 0;
  border: 0;
}
.nested :deep(.avatar) {
  width: 34px;
  height: 34px;
  border-radius: 50%;
}
.nested :deep(.meta) {
  font-size: 11px;
}
.nested .reply-body {
  margin-left: 46px;
}
.nested .reply-body > p {
  margin: 8px 0;
}
@media (max-width: 540px) {
  .reply-body {
    margin-left: 0;
  }
  .nested .reply-body {
    margin-left: 0;
  }
  footer > div {
    gap: 15px;
  }
}
</style>

<template>
  <div class="page">
    <header class="page__header">
      <div>
        <h2>聊天监管</h2>
        <p>查看群聊/私聊记录，管理群聊封禁，监视特殊词</p>
      </div>
    </header>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="聊天记录" name="chat" />
      <el-tab-pane label="群聊管理" name="group" />
      <el-tab-pane label="特殊词配置" name="words" />
    </el-tabs>

    <section v-if="activeTab === 'chat'" class="panel">
      <div class="filter-row">
        <el-radio-group v-model="chatFilter" size="small" @change="onChatFilterChange">
          <el-radio-button :value="''">全部</el-radio-button>
          <el-radio-button :value="0">群聊</el-radio-button>
          <el-radio-button :value="1">私聊</el-radio-button>
        </el-radio-group>
      </div>
      <el-table v-loading="loading" :data="chatList" size="small" empty-text="暂无聊天记录">
        <el-table-column prop="messageId" label="ID" width="70" />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isPrivate === 1 ? 'warning' : 'success'" size="small">
              {{ row.isPrivate === 1 ? '私聊' : '群聊' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="senderId" label="发送人" width="110" />
        <el-table-column label="会话信息" min-width="160">
          <template #default="{ row }">
            <span v-if="row.isPrivate === 1">
              接收人 {{ row.receiverId || '-' }}
            </span>
            <span v-else>群号 {{ row.groupId || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button
              v-if="row.isPrivate === 1"
              link
              size="small"
              @click="viewPrivateChat(row.senderId, row.receiverId)"
            >
              查看私聊
            </el-button>
            <el-button
              v-else
              link
              size="small"
              @click="viewGroupChat(row.groupId)"
            >
              查看群聊
            </el-button>
            <el-button link size="small" @click="openEdit(row)">修改</el-button>
            <el-button link size="small" type="danger" @click="handleDeleteChat(row.messageId)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <ListPager v-model:page="chatPage" v-model:size="chatSize" :total="chatTotal" @change="loadChats" />
    </section>

    <section v-else-if="activeTab === 'group'" class="panel">
      <el-table v-loading="loading" :data="groupList" size="small" empty-text="暂无群聊">
        <el-table-column prop="groupId" label="群号" width="90" />
        <el-table-column prop="groupName" label="群名" min-width="140" />
        <el-table-column prop="totalPeople" label="人数" width="70" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.isBanned === 1" type="danger" size="small">已封禁</el-tag>
            <el-tag v-else-if="row.isDeleted === 1" type="info" size="small">已删除</el-tag>
            <el-tag v-else type="success" size="small">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button link size="small" @click="viewGroupChat(row.groupId)">查看聊天</el-button>
            <el-button
              v-if="row.isBanned !== 1"
              link
              size="small"
              type="warning"
              @click="handleBan(row)"
            >
              封禁
            </el-button>
            <el-button
              v-else
              link
              size="small"
              type="success"
              @click="handleUnban(row.groupId)"
            >
              解禁
            </el-button>
            <el-button link size="small" type="danger" @click="handleSoftDelete(row.groupId)">软删</el-button>
          </template>
        </el-table-column>
      </el-table>
      <ListPager v-model:page="groupPage" v-model:size="groupSize" :total="groupTotal" @change="loadGroups" />
    </section>

    <section v-else class="panel"><SharedWordLibrary /></section>

    <!-- 特殊词监视 FAB -->
    <button type="button" class="monitor-fab" @click="openMonitor">
      <span class="monitor-fab__label">特殊词监视</span>
      <span v-if="pendingCount > 0" class="monitor-fab__badge">{{ pendingCount > 99 ? '99+' : pendingCount }}</span>
    </button>

    <el-dialog v-model="editVisible" title="修改聊天内容" width="480px" destroy-on-close>
      <el-input v-model="editContent" type="textarea" :rows="4" />
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="groupChatVisible" :title="`群聊 #${currentGroupId}`" width="640px" destroy-on-close>
      <el-table :data="groupChatList" size="small" max-height="360">
        <el-table-column prop="messageId" label="ID" width="70" />
        <el-table-column prop="senderId" label="发送人" width="110" />
        <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
      </el-table>
    </el-dialog>

    <el-dialog
      v-model="privateChatVisible"
      :title="`私聊 ${privateUserA} ↔ ${privateUserB}`"
      width="640px"
      destroy-on-close
    >
      <el-table :data="privateChatList" size="small" max-height="360">
        <el-table-column prop="messageId" label="ID" width="70" />
        <el-table-column prop="senderId" label="发送人" width="110" />
        <el-table-column prop="receiverId" label="接收人" width="110" />
        <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
      </el-table>
    </el-dialog>

    <el-drawer v-model="monitorVisible" title="特殊词监视" size="520px" destroy-on-close>
      <div class="filter-row">
        <el-radio-group v-model="violationStatus" size="small" @change="loadViolations">
          <el-radio-button :value="0">待处理</el-radio-button>
          <el-radio-button value="all">全部</el-radio-button>
          <el-radio-button :value="1">已忽视</el-radio-button>
          <el-radio-button :value="2">已警告</el-radio-button>
          <el-radio-button :value="3">已封禁</el-radio-button>
        </el-radio-group>
      </div>
      <div v-loading="monitorLoading" class="violation-list">
        <article v-for="item in violationList" :key="item.violationId" class="violation-card">
          <header>
            <el-tag size="small" type="danger">{{ item.matchedWord }}</el-tag>
            <span class="muted">{{ formatTime(item.hitTime) }}</span>
          </header>
          <p class="violation-meta">
            <template v-if="item.isPrivate === 1">
              私聊 · {{ item.senderName || item.senderId }} → {{ item.receiverName || item.receiverId }}
            </template>
            <template v-else>
              群聊 · {{ item.groupName || ('群号 ' + item.groupId) }} · 发送人 {{ item.senderName || item.senderId }}
            </template>
          </p>
          <p class="violation-content">{{ item.contentSnapshot }}</p>
          <div v-if="item.status === 0" class="violation-actions">
            <el-button size="small" @click="jumpToChat(item)">查看记录</el-button>
            <el-button size="small" @click="handleViolation(item, 'IGNORE')">忽视</el-button>
            <el-button size="small" type="warning" @click="handleViolation(item, 'WARN')">警告</el-button>
            <el-button size="small" type="danger" @click="handleViolation(item, 'BAN')">封禁</el-button>
          </div>
          <p v-else class="muted">状态：{{ statusLabel(item.status) }} · 处理人 {{ item.handledBy || '-' }}</p>
        </article>
        <p v-if="!monitorLoading && !violationList.length" class="empty-tip">暂无命中记录</p>
      </div>
      <ListPager v-model:page="violationPage" v-model:size="violationSize" :total="violationTotal" @change="loadViolations" />
    </el-drawer>
  </div>
</template>

<script setup>
import ListPager from "@/components/ListPager.vue"
import SharedWordLibrary from "@/components/SharedWordLibrary.vue"
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchChatRecords,
  fetchGroupChatRecords,
  fetchPrivateChatRecords,
  updateChatMessage,
  deleteChatMessage,
  fetchGroupList,
  banGroup,
  unbanGroup,
  softDeleteGroup,
  fetchSpecialWordViolations,
  handleSpecialWordViolation,
} from '@/api/admin'

const activeTab = ref('chat')
const loading = ref(false)
const saving = ref(false)
const chatSize=ref(20),groupSize=ref(20),violationSize=ref(20)

const chatFilter = ref('')
const chatPage = ref(1)
const chatTotal = ref(0)
const chatList = ref([])

const groupPage = ref(1)
const groupTotal = ref(0)
const groupList = ref([])


const editVisible = ref(false)
const editContent = ref('')
const currentMessageId = ref(null)

const groupChatVisible = ref(false)
const currentGroupId = ref(null)
const groupChatList = ref([])

const privateChatVisible = ref(false)
const privateUserA = ref(null)
const privateUserB = ref(null)
const privateChatList = ref([])

const monitorVisible = ref(false)
const monitorLoading = ref(false)
const violationStatus = ref(0)
const violationPage = ref(1)
const violationTotal = ref(0)
const violationList = ref([])
const pendingCount = ref(0)

let pollTimer = null

function pageList(data) {
  return data?.list || data?.records || []
}

function formatTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

function statusLabel(s) {
  if (s === 1) return '已忽视'
  if (s === 2) return '已警告'
  if (s === 3) return '已封禁'
  return '待处理'
}

async function loadChats() {
  loading.value = true
  try {
    const filter = chatFilter.value === '' ? null : Number(chatFilter.value)
    const res = await fetchChatRecords(chatPage.value, chatSize.value, filter)
    chatList.value = pageList(res.data)
    chatTotal.value = res.data?.total || 0
  } catch {
    chatList.value = []
  } finally {
    loading.value = false
  }
}

async function loadGroups() {
  loading.value = true
  try {
    const res = await fetchGroupList(groupPage.value, groupSize.value)
    groupList.value = pageList(res.data)
    groupTotal.value = res.data?.total || 0
  } catch {
    groupList.value = []
  } finally {
    loading.value = false
  }
}

async function refreshPendingCount() {
  try {
    const res = await fetchSpecialWordViolations(0, 1, 1)
    pendingCount.value = res.data?.pendingCount || res.data?.total || 0
  } catch {
    pendingCount.value = 0
  }
}

async function loadViolations() {
  monitorLoading.value = true
  try {
    const status = violationStatus.value === 'all' || violationStatus.value === '' || violationStatus.value === null
      ? null
      : Number(violationStatus.value)
    const res = await fetchSpecialWordViolations(status, violationPage.value, violationSize.value)
    violationList.value = pageList(res.data)
    violationTotal.value = res.data?.total || 0
    pendingCount.value = res.data?.pendingCount ?? pendingCount.value
  } catch {
    violationList.value = []
  } finally {
    monitorLoading.value = false
  }
}

function onTabChange(tab) {
  if (tab === 'chat') loadChats()
  else if (tab === 'group') loadGroups()
}

function onChatFilterChange() {
  chatPage.value = 1
  loadChats()
}

function openEdit(row) {
  currentMessageId.value = row.messageId
  editContent.value = row.content || ''
  editVisible.value = true
}

async function saveEdit() {
  saving.value = true
  try {
    await updateChatMessage({ messageId: currentMessageId.value, content: editContent.value })
    ElMessage.success('消息已修改')
    editVisible.value = false
    await loadChats()
  } catch {
    // handled
  } finally {
    saving.value = false
  }
}

async function handleDeleteChat(messageId) {
  try {
    await ElMessageBox.confirm('确定软删除该消息？', '删除确认', { type: 'warning' })
    await deleteChatMessage(messageId)
    ElMessage.success('消息已删除')
    await loadChats()
  } catch {
    // cancel
  }
}

async function viewGroupChat(groupId) {
  currentGroupId.value = groupId
  try {
    const res = await fetchGroupChatRecords(groupId, 1, 50)
    groupChatList.value = res.data || []
    groupChatVisible.value = true
  } catch {
    // handled
  }
}

async function viewPrivateChat(userA, userB) {
  if (!userA || !userB) {
    ElMessage.warning('私聊双方信息不完整')
    return
  }
  privateUserA.value = userA
  privateUserB.value = userB
  try {
    const res = await fetchPrivateChatRecords(userA, userB, 1, 100)
    privateChatList.value = res.data || []
    privateChatVisible.value = true
  } catch {
    // handled
  }
}

async function handleBan(row) {
  try {
    const { value } = await ElMessageBox.prompt('请输入封禁理由', '封禁群聊', {
      inputPlaceholder: '违规原因',
    })
    await banGroup({ groupId: row.groupId, banReason: value || '违规' })
    ElMessage.success('群聊已封禁')
    await loadGroups()
  } catch {
    // cancel
  }
}

async function handleUnban(groupId) {
  try {
    await unbanGroup(groupId)
    ElMessage.success('已解除封禁')
    await loadGroups()
  } catch {
    // handled
  }
}

async function handleSoftDelete(groupId) {
  try {
    await ElMessageBox.confirm('确定软删除该群聊？', '删除确认', { type: 'warning' })
    await softDeleteGroup(groupId)
    ElMessage.success('群聊已软删除')
    await loadGroups()
  } catch {
    // cancel
  }
}

function openMonitor() {
  monitorVisible.value = true
  violationPage.value = 1
  loadViolations()
}

function jumpToChat(item) {
  if (item.isPrivate === 1) {
    viewPrivateChat(item.senderId, item.receiverId)
  } else {
    viewGroupChat(item.groupId)
  }
}

async function handleViolation(item, action) {
  try {
    if (action === 'WARN') {
      const { value } = await ElMessageBox.prompt(
        '警告内容（可修改）',
        '发出警告',
        {
          inputValue:
            '文明用语：请注意网络言行，使用礼貌、友善的表达。辱骂、人身攻击、歧视性言论等不当内容一经查实，平台将予以警告或封禁处理。请共同维护清朗校园交流环境。',
          inputType: 'textarea',
        },
      )
      await handleSpecialWordViolation({
        violationId: item.violationId,
        action: 'WARN',
        warnContent: value,
      })
      ElMessage.success('已警告用户')
    } else if (action === 'BAN') {
      const { value } = await ElMessageBox.prompt('封禁理由', '封禁发送人', {
        inputValue: `聊天特殊词违规: ${item.matchedWord}`,
      })
      await handleSpecialWordViolation({
        violationId: item.violationId,
        action: 'BAN',
        banReason: value,
      })
      ElMessage.success('已封禁发送人')
    } else {
      await handleSpecialWordViolation({
        violationId: item.violationId,
        action: 'IGNORE',
      })
      ElMessage.success('已忽视')
    }
    await loadViolations()
    await refreshPendingCount()
  } catch {
    // cancel
  }
}

function refreshSharedWords() {
}

onMounted(() => {
  window.addEventListener('focus', refreshSharedWords)
  loadChats()
  refreshPendingCount()
  pollTimer = setInterval(refreshPendingCount, 30000)
})

onUnmounted(() => {
  window.removeEventListener('focus', refreshSharedWords)
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.page__header h2 {
  margin: 0;
  font-size: 24px;
}

.page__header p {
  margin: 8px 0 0;
  color: #6b7280;
}

.panel {
  padding: 16px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid #f3f4f6;
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
  align-items: center;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.monitor-fab {
  position: fixed;
  right: 28px;
  bottom: 28px;
  z-index: 40;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 18px;
  border: none;
  border-radius: 999px;
  background: #5b21b6;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  box-shadow: 0 10px 24px rgba(91, 33, 182, 0.35);
  cursor: pointer;
}

.monitor-fab:hover {
  filter: brightness(1.05);
}

.monitor-fab__badge {
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: #ef4444;
  color: #fff;
  font-size: 12px;
  line-height: 20px;
  text-align: center;
}

.violation-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 120px;
}

.violation-card {
  padding: 12px;
  border-radius: 12px;
  border: 1px solid #eee;
  background: #fafafa;
}

.violation-card header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.violation-meta {
  margin: 0 0 6px;
  font-size: 13px;
  color: #4b5563;
}

.violation-content {
  margin: 0 0 10px;
  font-size: 14px;
  color: #111827;
  word-break: break-word;
}

.violation-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.muted {
  color: #9ca3af;
  font-size: 12px;
}

.empty-tip {
  text-align: center;
  color: #9ca3af;
  padding: 24px 0;
}
</style>

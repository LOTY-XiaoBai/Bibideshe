<template>
  <div class="chat-page">
    <aside class="chat-side">
      <div class="side-tabs">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          class="side-tab"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
          <CountBadge :count="tab.key==='requests'?unread.data.requests:tab.key==='groups'?unread.groupCount:unread.privateCount"/>
        </button>
      </div>

      <!-- 好友 -->
      <div v-show="activeTab === 'friends'" class="side-panel">
        <div class="side-actions">
          <el-input
            v-model="friendSearchKeyword"
            placeholder="输入用户 ID 或昵称搜索"
            clearable
            @keyup.enter="doSearchUser"
          >
            <template #append>
              <el-button :loading="searchingUser" @click="doSearchUser">搜索</el-button>
            </template>
          </el-input>
        </div>
        <div v-if="searchResults.length" class="search-results">
          <div v-for="user in searchResults" :key="user.userId" class="search-card">
            <img :src="avatarOf(user)" alt="" class="mini-avatar" />
            <div class="search-card__info">
              <strong>{{ user.displayName }}</strong>
              <span>ID {{ user.userId }} · {{ roleLabel(user.role) }}</span>
            </div>
            <el-button size="small" type="primary" @click="doAddFriend(user.userId)">
              加好友
            </el-button>
          </div>
        </div>
        <p v-else-if="searchDone && !searchingUser" class="empty-tip">未找到匹配用户</p>
        <div v-loading="friendsLoading" class="list">
          <button
            type="button"
            class="list-item system-item"
            :class="{ active: session?.type === 'private' && session.id === 0 }"
            @click="openSystemNotify"
          >
            <div class="system-avatar">通</div>
            <div class="list-item__body">
              <div class="list-item__title">系统通知 <CountBadge :count="unread.count('private',0)"/></div>
              <div class="list-item__sub">平台警告与文明用语提醒</div>
            </div>
          </button>
          <button
            v-for="f in friends"
            :key="f.userId"
            type="button"
            class="list-item"
            :class="{ active: session?.type === 'private' && session.id === f.userId }"
            @click="openPrivate(f)"
          >
            <img :src="avatarOf(f)" alt="" class="mini-avatar" />
            <div class="list-item__body">
              <div class="list-item__title">{{ f.displayName }} <CountBadge :count="unread.count('private',f.userId)"/></div>
              <div class="list-item__sub">{{ f.signature || '暂无签名' }}</div>
            </div>
          </button>
          <p v-if="!friendsLoading && !friends.length" class="empty-tip">暂无好友，先搜索添加吧</p>
        </div>
      </div>

      <!-- 群聊 -->
      <div v-show="activeTab === 'groups'" class="side-panel">
        <div class="side-actions row">
          <el-button type="primary" @click="openCreateGroup">创建群聊</el-button>
          <el-button @click="showJoinDialog = true">搜索加入</el-button>
        </div>
        <div v-loading="groupsLoading" class="list">
          <button
            v-for="g in groups"
            :key="g.groupId"
            type="button"
            class="list-item"
            :class="{ active: session?.type === 'group' && session.id === g.groupId }"
            @click="openGroup(g)"
          >
            <img :src="groupAvatarOf(g)" alt="" class="mini-avatar" />
            <div class="list-item__body">
              <div class="list-item__title">
                {{ g.groupName }} <CountBadge :count="unread.count('group',g.groupId)"/>
                <el-tag v-if="g.myAdminLevel === 1" size="small" type="warning">群主</el-tag>
                <el-tag v-else-if="g.myAdminLevel === 2" size="small">管理</el-tag>
              </div>
              <div class="list-item__sub">{{ g.totalPeople || 0 }} 人 · {{ g.groupDescription || '暂无简介' }}</div>
            </div>
          </button>
          <p v-if="!groupsLoading && !groups.length" class="empty-tip">暂未加入群聊</p>
        </div>
      </div>

      <!-- 申请 -->
      <div v-show="activeTab === 'requests'" class="side-panel">
        <div class="req-section">
          <h4>收到的申请</h4>
          <div v-loading="requestsLoading" class="list">
            <div v-for="r in receivedRequests" :key="r.requestId" class="req-item">
              <img :src="avatarOf(r.requester)" alt="" class="mini-avatar" />
              <div class="list-item__body">
                <div class="list-item__title">{{ r.requester?.displayName || r.requesterId }}</div>
                <div class="list-item__sub">ID {{ r.requesterId }} · {{ formatTime(r.applyTime) }}</div>
              </div>
              <div class="req-actions">
                <el-button size="small" type="primary" @click="handleAccept(r.requestId)">同意</el-button>
                <el-button size="small" @click="handleReject(r.requestId)">拒绝</el-button>
              </div>
            </div>
            <p v-if="!requestsLoading && !receivedRequests.length" class="empty-tip">暂无待处理申请</p>
          </div>
        </div>
        <div class="req-section">
          <h4>已发出</h4>
          <div class="list">
            <div v-for="r in sentRequests" :key="r.requestId" class="req-item">
              <div class="list-item__body">
                <div class="list-item__title">{{ r.receiver?.displayName || r.receiverId }}</div>
                <div class="list-item__sub">{{ statusLabel(r.status) }} · {{ formatTime(r.applyTime) }}</div>
              </div>
            </div>
            <p v-if="!sentRequests.length" class="empty-tip">暂无发出记录</p>
          </div>
        </div>
      </div>
    </aside>

    <section class="chat-main">
      <template v-if="session">
        <header class="chat-header">
          <div>
            <h3>{{ sessionTitle }}</h3>
            <p>{{ sessionSub }}</p>
          </div>
          <div class="chat-header__actions">
            <template v-if="session.type === 'private' && !isSystemSession">
              <el-button v-if="canPkWithFriend" @click="openPk">成绩 PK</el-button>
              <el-button type="danger" plain @click="handleDeleteFriend">删除好友</el-button>
            </template>
            <template v-else-if="session.type === 'group'">
              <el-button @click="openMembers">成员与管理</el-button>
              <el-button v-if="canManageGroup" @click="openGroupEdit">群资料</el-button>
              <el-button v-if="isOwner" type="danger" plain @click="handleDissolve">解散群聊</el-button>
              <el-button v-else type="danger" plain @click="handleLeave">退出群聊</el-button>
            </template>
          </div>
        </header>

        <div ref="msgBoxRef" v-loading="messagesLoading" class="msg-box">
          <div
            v-for="m in messages"
            :key="m.messageId"
            class="msg-row"
            :class="{ mine: isMine(m), recalled: m.isDeleted === 1 }"
          >
            <img
              v-if="!isMine(m)"
              :src="m.messageType === 2 ? systemAvatar : avatarOf(m.sender)"
              alt=""
              class="bubble-avatar"
            />
            <div class="bubble-wrap">
              <div v-if="!isMine(m) && session.type === 'group'" class="sender-name">
                {{ m.sender?.displayName || m.senderId }}
              </div>
              <div
                class="bubble"
                :class="{ 'bubble--warn': m.messageType === 2 }"
                @contextmenu.prevent="m.messageType === 2 ? null : openMsgMenu($event, m)"
              >
                <template v-if="m.isDeleted === 1">
                  <span class="recalled">消息已撤回</span>
                </template>
                <template v-else-if="m.messageType === 2">
                  <div class="warn-title">平台警告 · 文明用语</div>
                  <p class="bubble-text">{{ m.content }}</p>
                  <button
                    v-if="isSystemSession"
                    type="button"
                    class="link-btn"
                    @click="dismissSystemMessage(m)"
                  >
                    不再显示
                  </button>
                </template>
                <template v-else>
                  <div v-if="m.referencedMessage" class="quote">
                    <span class="quote-author">
                      {{ m.referencedMessage.sender?.displayName || m.referencedMessage.senderId }}
                    </span>
                    <span class="quote-text">{{ quotePreview(m.referencedMessage) }}</span>
                  </div>
                  <img
                    v-if="m.messageType === 1"
                    :src="resolveMediaUrl(m.content)"
                    alt="图片"
                    class="bubble-img"
                    @click="previewImage(m.content)"
                  />
                  <div v-else class="bubble-text">{{ m.content }}<RouterLink v-if="postLink(m.content)" :to="postLink(m.content)">打开社区帖子 →</RouterLink></div>
                </template>
              </div>
              <div class="msg-meta">
                <span>{{ formatTime(m.sendTime) }}</span>
                <button
                  v-if="m.isDeleted !== 1 && m.messageType !== 2"
                  type="button"
                  class="link-btn"
                  @click="setReply(m)"
                >
                  引用
                </button>
                <button
                  v-if="canRecall(m) && m.messageType !== 2"
                  type="button"
                  class="link-btn danger"
                  @click="doRecall(m)"
                >
                  撤回
                </button>
                <button
                  v-if="canEditGroupMessage(m)"
                  type="button"
                  class="link-btn"
                  @click="doEditGroupMessage(m)"
                >
                  管理修改
                </button>
              </div>
            </div>
            <img
              v-if="isMine(m)"
              :src="myAvatar"
              alt=""
              class="bubble-avatar"
            />
          </div>
          <p v-if="!messagesLoading && !messages.length" class="empty-tip center">暂无消息，打个招呼吧</p>
        </div>

        <footer v-if="!isSystemSession" class="composer">
          <div v-if="replyTarget" class="reply-bar">
            <div>
              引用 {{ replyTarget.sender?.displayName || replyTarget.senderId }}：
              {{ quotePreview(replyTarget) }}
            </div>
            <button type="button" class="link-btn" @click="replyTarget = null">取消</button>
          </div>
          <div class="composer__tools">
            <el-upload
              :show-file-list="false"
              accept="image/*"
              :http-request="handleImageSend"
            >
              <el-button :loading="uploading" circle>
                <i class="ph ph-image" />
              </el-button>
            </el-upload>
          </div>
          <el-input
            v-model="draft"
            type="textarea"
            :rows="2"
            resize="none"
            placeholder="输入消息，Enter 发送，Shift+Enter 换行"
            @keydown="onDraftKeydown"
          />
          <el-button type="primary" class="send-btn" :loading="sending" @click="doSendText">
            发送
          </el-button>
        </footer>
        <footer v-else class="composer system-footer">
          <p>系统通知仅供查看，不可回复。可对单条警告选择「不再显示」。</p>
        </footer>
      </template>

      <div v-else class="chat-empty">
        <i class="ph ph-chats-circle" />
        <h3>选择好友或群聊开始沟通</h3>
        <p>支持文本、图片、引用回复与消息撤回；实时消息经 WebSocket 推送</p>
      </div>
    </section>

    <!-- 创建群聊 -->
    <el-dialog v-model="showCreateDialog" title="创建群聊" width="440px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="群名称" required>
          <el-input v-model="createForm.groupName" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="群简介">
          <el-input v-model="createForm.groupDescription" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
        <el-form-item label="群头像">
          <div class="avatar-edit">
            <img :src="createForm.groupAvatar || defaultGroupAvatar" alt="" class="edit-avatar" />
            <el-upload :show-file-list="false" accept="image/*" :http-request="uploadCreateAvatar">
              <el-button size="small">上传头像</el-button>
            </el-upload>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="doCreateGroup">创建</el-button>
      </template>
    </el-dialog>

    <!-- 搜索加入群 -->
    <el-dialog v-model="showJoinDialog" title="搜索群聊" width="520px" destroy-on-close>
      <el-input
        v-model="groupKeyword"
        placeholder="输入群号或群名"
        clearable
        @keyup.enter="doSearchGroup"
      >
        <template #append>
          <el-button :loading="searchingGroup" @click="doSearchGroup">搜索</el-button>
        </template>
      </el-input>
      <div class="join-list">
        <div v-for="g in groupSearchResults" :key="g.groupId" class="join-item">
          <img :src="groupAvatarOf(g)" alt="" class="mini-avatar" />
          <div class="list-item__body">
            <div class="list-item__title">{{ g.groupName }}</div>
            <div class="list-item__sub">群号 {{ g.groupId }} · {{ g.totalPeople || 0 }} 人</div>
          </div>
          <el-button size="small" type="primary" @click="doJoinGroup(g.groupId)">加入</el-button>
        </div>
        <p v-if="groupSearched && !groupSearchResults.length" class="empty-tip">未找到相关群聊</p>
      </div>
    </el-dialog>

    <!-- 群资料 -->
    <el-dialog v-model="showGroupEdit" title="修改群资料" width="440px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="群名称">
          <el-input v-model="editForm.groupName" maxlength="100" />
        </el-form-item>
        <el-form-item label="群简介">
          <el-input v-model="editForm.groupDescription" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
        <el-form-item label="群头像">
          <div class="avatar-edit">
            <img :src="editForm.groupAvatar || defaultGroupAvatar" alt="" class="edit-avatar" />
            <el-upload :show-file-list="false" accept="image/*" :http-request="uploadEditAvatar">
              <el-button size="small">更换头像</el-button>
            </el-upload>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showGroupEdit = false">取消</el-button>
        <el-button type="primary" :loading="savingGroup" @click="doUpdateGroup">保存</el-button>
      </template>
    </el-dialog>

    <!-- 成员 -->
    <el-dialog v-model="showMembers" title="群成员与等级" width="560px" destroy-on-close>
      <div v-loading="membersLoading" class="member-list">
        <div v-for="m in members" :key="m.userId" class="member-item">
          <img :src="avatarOf(m.profile)" alt="" class="mini-avatar" />
          <div class="list-item__body">
            <div class="list-item__title">
              {{ m.profile?.displayName || m.userId }}
              <el-tag v-if="m.adminLevel === 1" size="small" type="warning">群主</el-tag>
              <el-tag v-else-if="m.adminLevel === 2" size="small">管理员</el-tag>
              <el-tag v-else size="small" type="info">成员</el-tag>
            </div>
            <div class="list-item__sub">ID {{ m.userId }}</div>
          </div>
          <template v-if="isOwner && m.adminLevel !== 1 && m.userId !== Number(myId)">
            <el-button
              v-if="m.adminLevel !== 2"
              size="small"
              @click="doAppoint(m.userId)"
            >
              任命管理
            </el-button>
            <el-button
              v-else
              size="small"
              type="danger"
              plain
              @click="doRemoveAdmin(m.userId)"
            >
              撤销管理
            </el-button>
          </template>
          <el-button
            v-if="canRemoveMember(m)"
            size="small"
            type="danger"
            plain
            @click="doRemoveMember(m)"
          >
            移出群聊
          </el-button>
        </div>
      </div>
    </el-dialog>

    <!-- PK -->
    <el-dialog v-model="showPk" title="好友成绩 PK" width="480px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="对比方式">
          <el-radio-group v-model="pkMode">
            <el-radio value="comprehensive">综合评分</el-radio>
            <el-radio value="course">指定课程成绩</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="pkMode === 'course'" label="课程 ID">
          <el-input v-model="pkCourseId" placeholder="双方须均为该班学生" />
        </el-form-item>
      </el-form>
      <div v-if="pkResult" class="pk-result">
        <div class="pk-side">
          <strong>{{ pkResult.me?.displayName || '我' }}</strong>
          <p class="pk-score">{{ formatScore(pkResult.myScore) }}</p>
        </div>
        <div class="pk-vs">VS</div>
        <div class="pk-side">
          <strong>{{ pkResult.friend?.displayName || '好友' }}</strong>
          <p class="pk-score">{{ formatScore(pkResult.friendScore) }}</p>
        </div>
        <div class="pk-tip">
          <el-tag :type="pkTagType(pkResult.result)">{{ pkResultLabel(pkResult.result) }}</el-tag>
          <p>{{ pkResult.tip || (pkResult.courseName ? `课程：${pkResult.courseName}` : '综合评分对比') }}</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="showPk = false">关闭</el-button>
        <el-button type="primary" :loading="pkLoading" @click="doPk">开始 PK</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="viewerVisible" width="auto" align-center destroy-on-close>
      <img v-if="viewerUrls[0]" :src="viewerUrls[0]" alt="预览" class="preview-full" />
    </el-dialog>
  </div>
</template>

<script setup>
import CountBadge from '@/components/CountBadge.vue'
import {useUnread} from '@/stores/unread'
const unread=useUnread()

function postLink(content) {
  const last = String(content || '').split('\n').pop()
  return /^\/community\/post\/\d+$/.test(last) ? last : ''
}
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { resolveMediaUrl, defaultAvatar } from '@/utils/media'
import { ROLES } from '@/utils/role'
import { uploadImage } from '@/api/file'
import {
  searchUser,
  searchUserByNickname,
  sendFriendRequest,
  fetchReceivedRequests,
  fetchSentRequests,
  acceptFriendRequest,
  rejectFriendRequest,
  fetchFriendList,
  deleteFriend,
  pkWithFriend,
} from '@/api/friend'
import {
  createGroup,
  searchGroup,
  fetchMyGroups,
  fetchGroupMembers,
  joinGroup,
  leaveGroup,
  dissolveGroup,
  updateGroup,
  appointAdmin,
  removeAdmin,
  removeGroupMember,
} from '@/api/group'
import {
  sendPrivateMessage,
  fetchPrivateHistory,
  sendGroupMessage,
  fetchGroupHistory,
  recallMessage,
  updateGroupMessage,
} from '@/api/chat'
import { dismissWarning, fetchVisibleWarnings } from '@/api/warning'
import {
  connectChatWs,
  onChatEvent,
  onGroupEvent,
  watchGroups,
} from '@/utils/websocket'

const userStore = useUserStore()
const tabs = [
  { key: 'friends', label: '好友' },
  { key: 'groups', label: '群聊' },
  { key: 'requests', label: '申请' },
]

const route = useRoute()
const activeTab = ref(route.query.tab === 'requests' ? 'requests' : 'friends')
const friends = ref([])
const groups = ref([])
const receivedRequests = ref([])
const sentRequests = ref([])
const friendsLoading = ref(false)
const groupsLoading = ref(false)
const requestsLoading = ref(false)

const friendSearchKeyword = ref('')
const searchingUser = ref(false)
const searchResults = ref([])
const searchDone = ref(false)

const session = ref(null)
const currentGroup = ref(null)
const messages = ref([])
const messagesLoading = ref(false)
const draft = ref('')
const sending = ref(false)
const uploading = ref(false)
const replyTarget = ref(null)
const msgBoxRef = ref(null)

const showCreateDialog = ref(false)
const creating = ref(false)
const createForm = reactive({
  groupName: '',
  groupDescription: '',
  groupAvatar: '',
})

const showJoinDialog = ref(false)
const groupKeyword = ref('')
const searchingGroup = ref(false)
const groupSearchResults = ref([])
const groupSearched = ref(false)

const showGroupEdit = ref(false)
const savingGroup = ref(false)
const editForm = reactive({
  groupId: null,
  groupName: '',
  groupDescription: '',
  groupAvatar: '',
})

const showMembers = ref(false)
const membersLoading = ref(false)
const members = ref([])

const showPk = ref(false)
const pkMode = ref('comprehensive')
const pkCourseId = ref('')
const pkLoading = ref(false)
const pkResult = ref(null)

const viewerVisible = ref(false)
const viewerUrls = ref([])

const defaultGroupAvatar = 'https://placehold.co/80x80/94a3b8/ffffff?text=G'
const systemAvatar = 'https://placehold.co/80x80/b45309/ffffff?text=通'
const warningIdByMessage = ref({})

let unsubChat = null
let unsubGroup = null

const myId = computed(() => Number(userStore.userId))
const myAvatar = computed(() => {
  if (userStore.avatar) return resolveMediaUrl(userStore.avatar)
  return defaultAvatar(userStore.displayName || 'Me', userStore.role === ROLES.TEACHER ? 'teacher' : 'student')
})
const pendingCount = computed(() => receivedRequests.value.length)
const isSystemSession = computed(() => session.value?.type === 'private' && Number(session.value.id) === 0)

const sessionTitle = computed(() => {
  if (!session.value) return ''
  if (isSystemSession.value) return '系统通知'
  if (session.value.type === 'private') return session.value.profile?.displayName || `用户 ${session.value.id}`
  return currentGroup.value?.groupName || `群 ${session.value.id}`
})

const sessionSub = computed(() => {
  if (!session.value) return ''
  if (isSystemSession.value) return '平台警告将保留在此，可选择不再显示'
  if (session.value.type === 'private') {
    return `ID ${session.value.id} · ${roleLabel(session.value.profile?.role)}`
  }
  const g = currentGroup.value
  if (!g) return `群号 ${session.value.id}`
  const level =
    g.myAdminLevel === 1 ? '群主' : g.myAdminLevel === 2 ? '管理员' : '成员'
  return `群号 ${g.groupId} · ${g.totalPeople || 0} 人 · 我是${level}`
})

const isOwner = computed(() => currentGroup.value?.myAdminLevel === 1)
const canManageGroup = computed(() => [1, 2].includes(currentGroup.value?.myAdminLevel))

const canPkWithFriend = computed(() => {
  if (!session.value || session.value.type !== 'private' || isSystemSession.value) return false
  const myRole = userStore.role
  const friendRole = session.value.profile?.role
  if (!myRole || !friendRole) return false
  return myRole === friendRole
})

function roleLabel(role) {
  if (role === 'TEACHER' || role === ROLES.TEACHER) return '教师'
  if (role === 'STUDENT' || role === ROLES.STUDENT) return '学生'
  return role || '用户'
}

function avatarOf(profile) {
  if (profile?.avatar) return resolveMediaUrl(profile.avatar)
  const name = profile?.displayName || profile?.userId || 'U'
  const role = profile?.role === 'TEACHER' ? 'teacher' : 'student'
  return defaultAvatar(name, role)
}

function groupAvatarOf(g) {
  if (g?.groupAvatar) return resolveMediaUrl(g.groupAvatar)
  return defaultGroupAvatar
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return String(t).replace('T', ' ').slice(0, 16)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function formatScore(v) {
  if (v === null || v === undefined || v === '') return '-'
  return Number(v).toFixed(1)
}

function statusLabel(s) {
  if (s === 0) return '待处理'
  if (s === 1) return '已同意'
  if (s === 2) return '已拒绝'
  return '未知'
}

function isMine(m) {
  return Number(m.senderId) === myId.value
}

function canRecall(m) {
  if (m.isDeleted === 1) return false
  if (isMine(m)) return true
  return session.value?.type === 'group' && canManageGroup.value
}

function canEditGroupMessage(m) {
  return session.value?.type === 'group'
    && canManageGroup.value
    && m.isDeleted !== 1
    && m.messageType === 0
}

function canRemoveMember(member) {
  if (!canManageGroup.value || Number(member.userId) === myId.value || member.adminLevel === 1) return false
  if (isOwner.value) return true
  return member.adminLevel == null
}

function quotePreview(m) {
  if (!m) return ''
  if (m.isDeleted === 1) return '[已撤回]'
  if (m.messageType === 1) return '[图片]'
  const text = m.content || ''
  return text.length > 40 ? `${text.slice(0, 40)}…` : text
}

function scrollToBottom() {
  nextTick(() => {
    const el = msgBoxRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

async function loadFriends() {
  friendsLoading.value = true
  try {
    const res = await fetchFriendList()
    friends.value = res.data || []
  } catch {
    friends.value = []
  } finally {
    friendsLoading.value = false
  }
}

async function loadGroups() {
  groupsLoading.value = true
  try {
    const res = await fetchMyGroups()
    groups.value = res.data || []
    watchGroups(groups.value.map((g) => g.groupId))
  } catch {
    groups.value = []
  } finally {
    groupsLoading.value = false
  }
}

async function loadRequests() {
  requestsLoading.value = true
  try {
    const [recv, sent] = await Promise.all([fetchReceivedRequests(), fetchSentRequests()])
    receivedRequests.value = recv.data || []
    sentRequests.value = sent.data || []
  } catch {
    receivedRequests.value = []
    sentRequests.value = []
  } finally {
    requestsLoading.value = false
  }
}

async function doSearchUser() {
  const keyword = friendSearchKeyword.value.trim()
  if (!keyword) {
    ElMessage.warning('请输入用户 ID 或昵称')
    return
  }
  searchingUser.value = true
  searchResults.value = []
  searchDone.value = false
  try {
    if (/^\d+$/.test(keyword)) {
      const res = await searchUser(keyword)
      searchResults.value = res.data ? [res.data] : []
    } else {
      const res = await searchUserByNickname(keyword)
      searchResults.value = res.data || []
    }
  } catch {
    searchResults.value = []
  } finally {
    searchingUser.value = false
    searchDone.value = true
  }
}

async function doAddFriend(receiverId) {
  try {
    await sendFriendRequest(receiverId)
    ElMessage.success('好友申请已发送')
    loadRequests()
  } catch {
    // handled
  }
}

async function handleAccept(requestId) {
  try {
    await acceptFriendRequest(requestId)
    ElMessage.success('已同意')
    await Promise.all([loadRequests(), loadFriends()])
  } catch {
    // handled
  }
}

async function handleReject(requestId) {
  try {
    await rejectFriendRequest(requestId)
    ElMessage.success('已拒绝')
    loadRequests()
  } catch {
    // handled
  }
}

async function openPrivate(friend) {
  unsubGroup?.()
  unsubGroup = null
  session.value = { type: 'private', id: friend.userId, profile: friend }
  currentGroup.value = null
  replyTarget.value = null
  await loadMessages()
}

async function openSystemNotify() {
  unsubGroup?.()
  unsubGroup = null
  session.value = {
    type: 'private',
    id: 0,
    profile: { userId: 0, displayName: '系统通知', role: 'SYSTEM' },
  }
  currentGroup.value = null
  replyTarget.value = null
  try {
    const res = await fetchVisibleWarnings()
    const map = {}
    ;(res.data || []).forEach((w) => {
      if (w.chatMessageId) map[w.chatMessageId] = w.warningId
    })
    warningIdByMessage.value = map
  } catch {
    warningIdByMessage.value = {}
  }
  await loadMessages()
}

async function dismissSystemMessage(msg) {
  const warningId = warningIdByMessage.value[msg.messageId]
  if (!warningId) {
    ElMessage.warning('未找到对应警告记录')
    return
  }
  try {
    await dismissWarning(warningId)
    messages.value = messages.value.filter((m) => m.messageId !== msg.messageId)
    ElMessage.success('已不再显示该警告')
  } catch {
    // handled
  }
}

async function openGroup(group) {
  unsubGroup?.()
  session.value = { type: 'group', id: group.groupId }
  currentGroup.value = group
  replyTarget.value = null
  unsubGroup = onGroupEvent(group.groupId, handleWsPayload)
  await loadMessages()
}

async function loadMessages() {
  if (!session.value) return
  messagesLoading.value = true
  const current={...session.value}
  try {
    const res =
      current.type === 'private'
        ? await fetchPrivateHistory(current.id)
        : await fetchGroupHistory(current.id)
    if(session.value?.type!==current.type||session.value?.id!==current.id)return
    messages.value = res.data || []
    scrollToBottom()
    await markCurrentRead()
  } catch {
    messages.value = []
  } finally {
    messagesLoading.value = false
  }
}

function upsertMessage(msg) {
  if (!msg?.messageId) return
  const idx = messages.value.findIndex((m) => m.messageId === msg.messageId)
  if (idx >= 0) {
    messages.value[idx] = { ...messages.value[idx], ...msg }
  } else {
    messages.value.push(msg)
    scrollToBottom()
  }
}

function messageBelongsToSession(msg) {
  if (!session.value || !msg) return false
  if (session.value.type === 'private') {
    if (Number(msg.isPrivate) !== 1) return false
    const fid = Number(session.value.id)
    if (fid === 0) {
      return (
        (Number(msg.senderId) === 0 && Number(msg.receiverId) === myId.value) ||
        (Number(msg.receiverId) === 0 && Number(msg.senderId) === myId.value)
      )
    }
    return (
      (Number(msg.senderId) === myId.value && Number(msg.receiverId) === fid) ||
      (Number(msg.receiverId) === myId.value && Number(msg.senderId) === fid)
    )
  }
  return Number(msg.groupId) === Number(session.value.id) && Number(msg.isPrivate) !== 1
}

function handleWsPayload(payload) {
  if (!payload?.type) return
  if (payload.type === 'NEW_MESSAGE' || payload.type === 'UPDATE') {
    const msg = payload.data
    if (messageBelongsToSession(msg)) {upsertMessage(msg);markCurrentRead()}
    unread.refresh()
  } else if (payload.type === 'RECALL') {
    const mid = payload.data?.messageId
    if (!mid) return
    const idx = messages.value.findIndex((m) => m.messageId === mid)
    if (idx >= 0) {
      messages.value[idx] = { ...messages.value[idx], isDeleted: 1 }
    }
  }
}

function setReply(m) {
  if (m.isDeleted === 1) return
  replyTarget.value = m
}

async function doRecall(m) {
  try {
    await ElMessageBox.confirm('确定撤回这条消息？', '撤回确认', { type: 'warning' })
    await recallMessage(m.messageId)
    m.isDeleted = 1
    ElMessage.success('已撤回')
  } catch {
    // cancel or error
  }
}

async function doEditGroupMessage(m) {
  try {
    const { value } = await ElMessageBox.prompt('修改后的文本将实时同步给所有群成员。', '管理员修改消息', {
      inputValue: m.content || '',
      inputType: 'textarea',
      inputValidator: (v) => {
        const text = String(v || '').trim()
        if (!text) return '消息内容不能为空'
        if (text.length > 2000) return '消息内容不能超过2000个字符'
        return true
      },
    })
    const content = String(value || '').trim()
    await updateGroupMessage({ messageId: m.messageId, content })
    m.content = content
    ElMessage.success('消息已修改')
  } catch {
    // cancel or error
  }
}

function openMsgMenu(_e, m) {
  if (m.isDeleted === 1) return
  setReply(m)
}

async function doSendText() {
  const content = draft.value.trim()
  if (!content) return
  if (!session.value) return
  sending.value = true
  try {
    const body = {
      content,
      messageType: 0,
      referenceId: replyTarget.value?.messageId || 0,
    }
    let res
    if (session.value.type === 'private') {
      res = await sendPrivateMessage({ ...body, receiverId: session.value.id })
    } else {
      res = await sendGroupMessage({ ...body, groupId: session.value.id })
    }
    if (res.data) upsertMessage(res.data)
    draft.value = ''
    replyTarget.value = null
  } catch {
    // handled
  } finally {
    sending.value = false
  }
}

function onDraftKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    doSendText()
  }
}

async function handleImageSend({ file }) {
  if (!session.value) return
  uploading.value = true
  try {
    const up = await uploadImage(file)
    const url = up.data?.url
    if (!url) throw new Error('上传失败')
    const body = {
      content: url,
      messageType: 1,
      referenceId: replyTarget.value?.messageId || 0,
    }
    let res
    if (session.value.type === 'private') {
      res = await sendPrivateMessage({ ...body, receiverId: session.value.id })
    } else {
      res = await sendGroupMessage({ ...body, groupId: session.value.id })
    }
    if (res.data) upsertMessage(res.data)
    replyTarget.value = null
  } catch {
    // handled
  } finally {
    uploading.value = false
  }
}

function previewImage(url) {
  viewerUrls.value = [resolveMediaUrl(url)]
  viewerVisible.value = true
}

function openCreateGroup() {
  createForm.groupName = ''
  createForm.groupDescription = ''
  createForm.groupAvatar = ''
  showCreateDialog.value = true
}

async function uploadCreateAvatar({ file }) {
  try {
    const res = await uploadImage(file)
    createForm.groupAvatar = res.data?.url || ''
  } catch {
    // handled
  }
}

async function doCreateGroup() {
  if (!createForm.groupName.trim()) {
    ElMessage.warning('请填写群名称')
    return
  }
  creating.value = true
  try {
    const res = await createGroup({
      groupName: createForm.groupName.trim(),
      groupDescription: createForm.groupDescription.trim() || undefined,
      groupAvatar: createForm.groupAvatar || undefined,
    })
    ElMessage.success('群聊创建成功')
    showCreateDialog.value = false
    await loadGroups()
    const created = res.data
    if (created?.groupId) {
      const g = groups.value.find((x) => x.groupId === created.groupId) || {
        ...created,
        myAdminLevel: 1,
      }
      openGroup(g)
      activeTab.value = 'groups'
    }
  } catch {
    // handled
  } finally {
    creating.value = false
  }
}

async function doSearchGroup() {
  const kw = groupKeyword.value.trim()
  if (!kw) {
    ElMessage.warning('请输入关键词')
    return
  }
  searchingGroup.value = true
  groupSearched.value = true
  try {
    const res = await searchGroup(kw)
    groupSearchResults.value = res.data || []
  } catch {
    groupSearchResults.value = []
  } finally {
    searchingGroup.value = false
  }
}

async function doJoinGroup(groupId) {
  try {
    await joinGroup(groupId)
    ElMessage.success('已加入群聊')
    showJoinDialog.value = false
    await loadGroups()
    activeTab.value = 'groups'
  } catch {
    // handled
  }
}

function openGroupEdit() {
  const g = currentGroup.value
  if (!g) return
  editForm.groupId = g.groupId
  editForm.groupName = g.groupName || ''
  editForm.groupDescription = g.groupDescription || ''
  editForm.groupAvatar = g.groupAvatar || ''
  showGroupEdit.value = true
}

async function uploadEditAvatar({ file }) {
  try {
    const res = await uploadImage(file)
    editForm.groupAvatar = res.data?.url || ''
  } catch {
    // handled
  }
}

async function doUpdateGroup() {
  savingGroup.value = true
  try {
    await updateGroup({
      groupId: editForm.groupId,
      groupName: editForm.groupName.trim(),
      groupDescription: editForm.groupDescription.trim(),
      groupAvatar: editForm.groupAvatar,
    })
    ElMessage.success('群资料已更新')
    showGroupEdit.value = false
    await loadGroups()
    const g = groups.value.find((x) => x.groupId === editForm.groupId)
    if (g) currentGroup.value = g
  } catch {
    // handled
  } finally {
    savingGroup.value = false
  }
}

async function openMembers() {
  if (!session.value || session.value.type !== 'group') return
  showMembers.value = true
  membersLoading.value = true
  try {
    const res = await fetchGroupMembers(session.value.id)
    members.value = res.data || []
  } catch {
    members.value = []
  } finally {
    membersLoading.value = false
  }
}

async function doAppoint(targetUserId) {
  try {
    await appointAdmin(session.value.id, targetUserId)
    ElMessage.success('已任命管理员')
    openMembers()
  } catch {
    // handled
  }
}

async function doRemoveAdmin(targetUserId) {
  try {
    await removeAdmin(session.value.id, targetUserId)
    ElMessage.success('已撤销管理员')
    openMembers()
  } catch {
    // handled
  }
}

async function doRemoveMember(member) {
  const name = member.profile?.displayName || member.userId
  try {
    await ElMessageBox.confirm(`确定将 ${name} 移出群聊？`, '移出成员', { type: 'warning' })
    await removeGroupMember(session.value.id, member.userId)
    ElMessage.success('已将成员移出群聊')
    await openMembers()
    await loadGroups()
  } catch {
    // cancel or error
  }
}

async function handleLeave() {
  try {
    await ElMessageBox.confirm('确定退出该群聊？', '退出确认', { type: 'warning' })
    await leaveGroup(session.value.id)
    ElMessage.success('已退出群聊')
    session.value = null
    currentGroup.value = null
    messages.value = []
    loadGroups()
  } catch {
    // cancel
  }
}

async function handleDissolve() {
  try {
    await ElMessageBox.confirm('解散后不可恢复，确定解散？', '解散群聊', { type: 'warning' })
    await dissolveGroup(session.value.id)
    ElMessage.success('群聊已解散')
    session.value = null
    currentGroup.value = null
    messages.value = []
    loadGroups()
  } catch {
    // cancel
  }
}

async function handleDeleteFriend() {
  try {
    await ElMessageBox.confirm('确定删除该好友？', '删除好友', { type: 'warning' })
    await deleteFriend(session.value.id)
    ElMessage.success('已删除好友')
    session.value = null
    messages.value = []
    loadFriends()
  } catch {
    // cancel
  }
}

function openPk() {
  pkResult.value = null
  pkMode.value = 'comprehensive'
  pkCourseId.value = ''
  showPk.value = true
}

async function doPk() {
  if (!session.value) return
  if (pkMode.value === 'course' && !pkCourseId.value.trim()) {
    ElMessage.warning('请输入课程 ID')
    return
  }
  pkLoading.value = true
  try {
    const res = await pkWithFriend(
      session.value.id,
      pkMode.value === 'course' ? pkCourseId.value.trim() : undefined,
    )
    pkResult.value = res.data
  } catch {
    pkResult.value = null
  } finally {
    pkLoading.value = false
  }
}

function pkResultLabel(r) {
  const map = { WIN: '你赢了', LOSE: '你输了', DRAW: '平局', INCOMPARABLE: '无法对比' }
  return map[r] || r || '-'
}

function pkTagType(r) {
  if (r === 'WIN') return 'success'
  if (r === 'LOSE') return 'danger'
  if (r === 'DRAW') return 'info'
  return 'warning'
}

async function markCurrentRead(){
  if(!session.value||document.hidden||!document.hasFocus())return
  const last=messages.value.reduce((n,m)=>Math.max(n,Number(m.messageId)||0),0)
  try{await unread.readSession(session.value.type,session.value.id,last)}catch{}
}
function refreshVisible(){if(!document.hidden){markCurrentRead();unread.refresh()}}
watch(()=>unread.data.requests,()=>loadRequests())
watch(activeTab, (tab) => {
  if (tab === 'friends') loadFriends()
  if (tab === 'groups') loadGroups()
  if (tab === 'requests') loadRequests()
})

onMounted(() => {
  window.addEventListener('focus',refreshVisible)
  document.addEventListener('visibilitychange',refreshVisible)
  connectChatWs()
  unsubChat = onChatEvent(handleWsPayload)
  loadFriends()
  loadGroups()
  loadRequests()
})

onUnmounted(() => {
  window.removeEventListener('focus',refreshVisible)
  document.removeEventListener('visibilitychange',refreshVisible)
  unsubChat?.()
  unsubGroup?.()
})
</script>

<style scoped>
.chat-page {
  display: flex;
  height: calc(100vh - 120px);
  min-height: 520px;
  background: #fff;
  border: 1px solid var(--border-color);
  border-radius: 18px;
  overflow: hidden;
  box-shadow: 0 4px 18px rgba(15, 23, 42, 0.04);
}

.chat-side {
  width: 300px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #f3f4f6;
  background: #fafbfc;
}

.side-tabs {
  display: flex;
  padding: 12px 12px 0;
  gap: 4px;
}

.side-tab {
  flex: 1;
  position: relative;
  border: none;
  background: transparent;
  padding: 10px 8px;
  border-radius: 10px 10px 0 0;
  color: #6b7280;
  font-weight: 600;
  cursor: pointer;
}

.side-tab.active {
  background: #fff;
  color: var(--theme-primary);
  box-shadow: 0 -1px 0 #fff;
}

.badge {
  position: absolute;
  top: 4px;
  right: 8px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: #ef4444;
  color: #fff;
  font-size: 10px;
  line-height: 16px;
}

.side-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
  padding: 12px;
}

.side-actions {
  margin-bottom: 12px;
}

.side-actions.row {
  display: flex;
  gap: 8px;
}

.side-actions.row .el-button {
  flex: 1;
}

.list {
  flex: 1;
  overflow-y: auto;
}

.list-item,
.req-item,
.join-item,
.member-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px;
  border: none;
  border-radius: 12px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s ease;
}

.list-item:hover,
.req-item:hover {
  background: var(--theme-hover-bg);
}

.list-item.active {
  background: var(--theme-active-bg);
}

.mini-avatar,
.bubble-avatar,
.edit-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  background: #e5e7eb;
}

.edit-avatar {
  width: 64px;
  height: 64px;
}

.list-item__body {
  flex: 1;
  min-width: 0;
}

.list-item__title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.list-item__sub {
  margin-top: 2px;
  font-size: 12px;
  color: #9ca3af;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.search-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  margin-bottom: 10px;
  border-radius: 12px;
  background: var(--theme-hover-bg);
}

.search-results {
  max-height: 220px;
  overflow-y: auto;
  margin-bottom: 10px;
}

.search-card__info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
  color: #6b7280;
}

.empty-tip {
  margin: 24px 8px;
  color: #9ca3af;
  font-size: 13px;
  text-align: center;
}

.empty-tip.center {
  margin-top: 80px;
}

.req-section {
  margin-bottom: 16px;
}

.req-section h4 {
  margin: 0 0 8px;
  font-size: 13px;
  color: #6b7280;
}

.req-actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.chat-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #f8fafc 0%, #f3f6f8 100%);
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  background: #fff;
  border-bottom: 1px solid #f3f4f6;
}

.chat-header h3 {
  margin: 0;
  font-size: 16px;
}

.chat-header p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #9ca3af;
}

.chat-header__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.msg-box {
  flex: 1;
  overflow-y: auto;
  padding: 18px;
}

.msg-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  margin-bottom: 14px;
}

.msg-row.mine {
  justify-content: flex-end;
}

.bubble-wrap {
  max-width: min(68%, 480px);
}

.sender-name {
  margin-bottom: 4px;
  font-size: 12px;
  color: #9ca3af;
}

.bubble {
  padding: 10px 14px;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.06);
  word-break: break-word;
}

.msg-row.mine .bubble {
  background: var(--theme-gradient);
  color: #fff;
}

.msg-row.mine .quote {
  background: rgba(255, 255, 255, 0.18);
  border-left-color: rgba(255, 255, 255, 0.7);
}

.quote {
  margin-bottom: 8px;
  padding: 6px 8px;
  border-left: 3px solid var(--theme-primary);
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.04);
  font-size: 12px;
}

.quote-author {
  display: block;
  font-weight: 600;
  margin-bottom: 2px;
}

.bubble-text {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.5;
  font-size: 14px;
}

.bubble-img {
  max-width: 220px;
  max-height: 220px;
  border-radius: 10px;
  cursor: zoom-in;
  display: block;
}

.recalled {
  font-size: 13px;
  opacity: 0.75;
  font-style: italic;
}

.msg-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  font-size: 11px;
  color: #9ca3af;
}

.msg-row.mine .msg-meta {
  justify-content: flex-end;
}

.link-btn {
  border: none;
  background: none;
  padding: 0;
  color: var(--theme-primary);
  cursor: pointer;
  font-size: 11px;
}

.link-btn.danger {
  color: #ef4444;
}

.composer {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 10px;
  align-items: end;
  padding: 12px 16px;
  background: #fff;
  border-top: 1px solid #f3f4f6;
}

.reply-bar {
  grid-column: 1 / -1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 10px;
  border-radius: 10px;
  background: var(--theme-hover-bg);
  font-size: 12px;
  color: #4b5563;
}

.send-btn {
  background: var(--theme-gradient) !important;
  border: none !important;
}

.chat-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  gap: 8px;
}

.chat-empty i {
  font-size: 48px;
  color: var(--theme-primary);
  opacity: 0.5;
}

.chat-empty h3 {
  margin: 8px 0 0;
  color: #4b5563;
}

.chat-empty p {
  margin: 0;
  font-size: 13px;
}

.avatar-edit {
  display: flex;
  align-items: center;
  gap: 12px;
}

.join-list,
.member-list {
  margin-top: 14px;
  max-height: 360px;
  overflow-y: auto;
}

.join-item,
.member-item {
  cursor: default;
}

.pk-result {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 12px;
  align-items: center;
  margin-top: 8px;
  padding: 16px;
  border-radius: 14px;
  background: #f8fafc;
}

.pk-side {
  text-align: center;
}

.pk-score {
  margin: 8px 0 0;
  font-size: 28px;
  font-weight: 700;
  color: var(--theme-primary);
}

.pk-vs {
  font-weight: 800;
  color: #cbd5e1;
}

.pk-tip {
  grid-column: 1 / -1;
  text-align: center;
}

.pk-tip p {
  margin: 8px 0 0;
  font-size: 13px;
  color: #6b7280;
}

.preview-full {
  max-width: min(80vw, 720px);
  max-height: 70vh;
  display: block;
  margin: 0 auto;
  border-radius: 8px;
}

.system-item {
  border: 1px dashed #f59e0b;
  background: #fffbeb;
}

.system-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #b45309;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
}

.bubble--warn {
  background: #fff7ed !important;
  border: 1px solid #fdba74;
  color: #9a3412;
}

.warn-title {
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 6px;
  color: #c2410c;
}

.system-footer {
  justify-content: center;
  color: #6b7280;
  font-size: 13px;
}

.system-footer p {
  margin: 0;
}

@media (max-width: 900px) {
  .chat-page {
    flex-direction: column;
    height: auto;
  }

  .chat-side {
    width: 100%;
    max-height: 320px;
  }

  .chat-main {
    min-height: 480px;
  }
}
</style>

import { defineStore } from 'pinia'
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { getToken } from '@/utils/auth'
import request from '@/api/request'
export const useUnreadStore=defineStore('unread',()=>{
  const data=ref({}), privateRows=computed(()=>data.value.private||[]), groupRows=computed(()=>data.value.groups||[])
  const privateCount=computed(()=>privateRows.value.reduce((n,r)=>n+Number(r.unread),0))
  const groupCount=computed(()=>groupRows.value.reduce((n,r)=>n+Number(r.unread),0))
  const chatCount=computed(()=>privateCount.value+groupCount.value+Number(data.value.requests||0))
  const activityCount=computed(()=>Number(data.value.notices||0)+Number(data.value.reports||0)+Number(data.value.appeals||0))
  let timer,users=0,loading=false,pending=false
  async function refresh(){
    if(!getToken())return
    if(loading){pending=true;return}
    loading=true;const token=getToken()
    try {const r=await request.get('/api/unread',{silent:true});if(token===getToken())data.value=r.data||{}}catch{}finally{loading=false;if(pending){pending=false;refresh()}}
  }
  function start(){if(users++===0){data.value={};refresh();timer=setInterval(refresh,10000);window.addEventListener('focus',refresh);window.addEventListener('unread-changed',refresh)}}
  function stop(){users=Math.max(0,users-1);if(!users){clearInterval(timer);window.removeEventListener('focus',refresh);window.removeEventListener('unread-changed',refresh);data.value={}}}
  function count(kind,id){return Number((kind==='group'?groupRows.value:privateRows.value).find(r=>String(r.target_id)===String(id))?.unread||0)}
  async function readSession(kind,targetId,lastMessageId){if(!lastMessageId||document.hidden)return;await request.post('/api/unread/read',{kind,targetId,lastMessageId},{silent:true});await refresh()}
  return {data,privateCount,groupCount,chatCount,activityCount,refresh,start,stop,count,readSession}
})
export function useUnread(){const store=useUnreadStore();onMounted(store.start);onUnmounted(store.stop);return store}

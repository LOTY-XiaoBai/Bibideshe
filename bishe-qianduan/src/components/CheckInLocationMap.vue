<template>
  <section class="location-setup" :class="{ 'location-preview': studentPreview }">
    <div class="map-actions">
      <el-button v-if="!studentPreview" :disabled="!ready" @click="draw">绘制矩形</el-button>
      <el-button :disabled="!ready || submitting" :loading="locating" @click="locate">
        {{ studentPreview ? '重新定位' : '定位到我' }}
      </el-button>
      <el-button v-if="error" text @click="reload">重新加载地图</el-button>
      <span v-if="error" role="alert">{{ error }}</span>
      <span v-else aria-live="polite">{{ status }}</span>
      <span v-if="precisionMeters !== null" aria-live="polite">定位精度约 {{ precisionMeters }} 米</span>
    </div>
    <p v-if="studentPreview && ready" class="map-legend">蓝色矩形为教师设置的签到范围，箭头为你的估算位置。</p>
    <iframe :key="frameVersion" ref="frame" title="签到范围地图" :srcdoc="mapDocument" @load="initialize" />
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { getBrowserPosition } from '@/utils/browserPosition'

const props = defineProps({
  courseId: [String, Number],
  checkInId: [String, Number],
  submitting: Boolean,
})
const emit = defineEmits(['position'])
const bounds = defineModel({ type: Object, default: null })
const studentPreview = computed(() => props.checkInId != null)
const frame = ref(null), ready = ref(false), locating = ref(false), error = ref('')
const status = ref('正在加载地图…'), frameVersion = ref(0)
const precisionMeters = ref(null)
const mapDocument = '<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><style>html,body,#map{height:100%;margin:0}</style></head><body><div id="map"></div></body></html>'
const style = { strokeColor: '#1d4ed8', strokeWeight: 3, fillColor: '#60a5fa', fillOpacity: 0.25 }
let sdk, map, mouseTool, rectangle, positionMarker, generation = 0

function loadSdk(target, config) {
  const view = target.contentWindow
  const doc = target.contentDocument
  view._AMapSecurityConfig = { serviceHost: new URL(config.serviceHost, window.location.origin).href }
  const url = new URL('https://webapi.amap.com/maps')
  url.search = new URLSearchParams({ v: '2.0', key: config.key, plugin: 'AMap.MouseTool' })
  return new Promise((resolve, reject) => {
    const script = doc.createElement('script')
    const fail = () => {
      clearTimeout(timer)
      script.remove()
      reject(new Error('高德地图加载失败，请检查网络或联系管理员'))
    }
    const timer = setTimeout(fail, 12000)
    script.src = url.href
    script.onerror = fail
    script.onload = () => {
      clearTimeout(timer)
      if (!view.AMap?.Map || !view.AMap?.MouseTool) { fail(); return }
      resolve(view.AMap)
    }
    doc.head.appendChild(script)
  })
}

async function initialize() {
  const id = ++generation
  const target = frame.value
  bounds.value = null
  try {
    const [{ data: config }, { data: previous }] = await Promise.all([
      request.get('/api/location/map-config', {
        silent: true, params: studentPreview.value ? { checkInId: props.checkInId } : undefined,
      }),
      studentPreview.value ? { data: null }
        : request.get(`/api/check-in/location-default/${props.courseId}`, { silent: true }),
    ])
    const saved = studentPreview.value ? config.bounds : previous
    if (id !== generation) return
    // Each map has its own SDK window, so switching the active Key takes effect when reopened.
    const loadedSdk = await loadSdk(target, config)
    if (id !== generation) return
    sdk = loadedSdk
    map = new sdk.Map(target.contentDocument.getElementById('map'), {
      viewMode: '2D', zoom: saved ? 16 : 12, resizeEnable: true,
      ...(saved ? { center: new sdk.LngLat((saved.west + saved.east) / 2, (saved.south + saved.north) / 2) } : {}),
    })
    if (!studentPreview.value) {
      mouseTool = new sdk.MouseTool(map)
      mouseTool.on('draw', finishDrawing)
    }
    if (saved) {
      const area = new sdk.Bounds(new sdk.LngLat(saved.west, saved.south), new sdk.LngLat(saved.east, saved.north))
      rectangle = new sdk.Rectangle({ ...style, bounds: area })
      map.add(rectangle)
      map.setBounds(area)
      bounds.value = saved
    }
    ready.value = true
    status.value = saved ? '已载入上次签到范围' : '请在地图上绘制签到范围'
    if (studentPreview.value) locate()
  } catch (failure) {
    if (id === generation) error.value = failure.response?.data?.message || failure.message || '地图加载失败'
  }
}

function draw() {
  mouseTool.close(false)
  if (rectangle) map.remove(rectangle)
  rectangle = null
  bounds.value = null
  status.value = '在地图上按住鼠标并拖动绘制矩形'
  mouseTool.rectangle(style)
}

function finishDrawing(event) {
  mouseTool.close(false)
  rectangle = event.obj
  const area = rectangle.getBounds()
  const sw = area.getSouthWest(), ne = area.getNorthEast()
  const next = { west: sw.getLng(), south: sw.getLat(), east: ne.getLng(), north: ne.getLat() }
  if (next.west >= next.east || next.south >= next.north) {
    map.remove(rectangle)
    rectangle = null
    status.value = '范围太小，请重新绘制矩形'
    return
  }
  bounds.value = next
  status.value = '已选定签到范围'
}

async function locate() {
  if (locating.value || props.submitting) return
  const id = generation
  locating.value = true
  if (studentPreview.value) {
    emit('position', null)
    status.value = '正在获取当前位置…'
  }
  if (positionMarker) map.remove(positionMarker)
  positionMarker = null
  precisionMeters.value = null
  try {
    const position = await getBrowserPosition()
    if (id !== generation) return
    const { latitude, longitude } = position
    const endpoint = studentPreview.value ? `/api/check-in/location-preview/${props.checkInId}` : '/api/location/map-position'
    const { data } = await request.post(endpoint, { latitude, longitude }, { silent: true })
    if (id !== generation) return
    const point = studentPreview.value ? data.point : data
    const markerPoint = new sdk.LngLat(point.longitude, point.latitude)
    const content = frame.value.contentDocument.createElement('div')
    content.setAttribute('role', 'img')
    content.setAttribute('aria-label', '我的位置')
    content.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" width="28" height="36" viewBox="0 0 28 36" aria-hidden="true"><path d="M14 34 2 4l12 7 12-7Z" fill="#2563eb" stroke="white" stroke-width="2" stroke-linejoin="round"/></svg>'
    positionMarker = new sdk.Marker({ position: markerPoint, content, anchor: 'bottom-center', zIndex: 120 })
    map.add(positionMarker)
    precisionMeters.value = Number.isFinite(position.accuracy) ? Math.ceil(position.accuracy) : null
    if (studentPreview.value) {
      const { west, south, east, north } = bounds.value
      const view = new sdk.Bounds(
        new sdk.LngLat(Math.min(west, point.longitude), Math.min(south, point.latitude)),
        new sdk.LngLat(Math.max(east, point.longitude), Math.max(north, point.latitude)),
      )
      const padding = new frame.value.contentWindow.Array(42, 24, 24, 24)
      map.setBounds(view, true, padding)
      status.value = data.inside ? '当前位置在签到范围内' : '当前位置在签到范围外'
      emit('position', data.inside ? position : null)
    } else {
      map.setZoomAndCenter(17, markerPoint)
    }
  } catch (failure) {
    if (id !== generation) return
    const message = failure.response?.data?.message || failure.message || '定位失败，请重试'
    if (studentPreview.value) status.value = message
    else ElMessage.warning(message)
  } finally {
    if (id === generation) locating.value = false
  }
}

function dispose() {
  generation++
  mouseTool?.close(false)
  map?.destroy()
  sdk = map = mouseTool = rectangle = positionMarker = null
  locating.value = false
  precisionMeters.value = null
}

function reload() {
  dispose()
  error.value = ''
  ready.value = false
  status.value = '正在加载地图…'
  frameVersion.value++
}

onBeforeUnmount(dispose)
</script>

<style scoped>
.location-setup { margin-bottom: 16px; }
.map-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; color: #475569; }
.map-actions :deep(.el-button) { margin: 0; }
.map-actions [role='alert'] { color: #b91c1c; }
iframe { display: block; width: 100%; height: clamp(280px, 42vh, 460px); border: 1px solid #cbd5e1; border-radius: 12px; background: #f8fafc; }
.location-preview { margin-bottom: 0; }
.location-preview iframe { height: clamp(210px, 35dvh, 330px); }
.map-legend { margin: 0 0 8px; color: #475569; }
</style>

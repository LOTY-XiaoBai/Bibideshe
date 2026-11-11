<template>
  <div>
    <div ref="pad" class="gesture-pad" :role="readonly ? undefined : 'group'"
      :aria-label="readonly ? undefined : '手势九宫格'"
      @pointerdown="start" @pointermove="move" @pointerup="finish" @pointercancel="finish" @lostpointercapture="finish">
      <svg viewBox="0 0 100 100" :role="readonly ? 'img' : undefined"
        :aria-label="readonly ? '大屏手势' : undefined" :aria-hidden="readonly ? undefined : true">
        <polyline v-if="path" :points="path" />
        <g v-for="point in points" :key="point.id">
          <circle :cx="point.x" :cy="point.y" :r="sequence.includes(point.id) ? 6.5 : 4.5"
            :class="{ selected: sequence.includes(point.id) }" />
          <text v-if="sequence.includes(point.id)" :x="point.x" :y="point.y">{{ sequence.indexOf(point.id) + 1 }}</text>
        </g>
      </svg>
      <template v-if="!readonly">
        <button v-for="point in points" :key="point.id" type="button" :aria-label="`点 ${point.id}`"
          :aria-pressed="sequence.includes(point.id)" :style="{ left: `${point.x}%`, top: `${point.y}%` }"
          @click="keyboardSelect($event, point.id)" />
      </template>
    </div>
    <div v-if="!readonly" class="gesture-controls">
      <span aria-live="polite">已连接 {{ sequence.length }} 个点</span>
      <el-button text :disabled="!sequence.length" @click="sequence = []">清空重画</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({ readonly: Boolean })
const sequence = defineModel({ type: Array, default: () => [] })
const points = Array.from({ length: 9 }, (_, i) => ({
  id: i + 1, x: (i % 3 + 0.5) * 100 / 3, y: (Math.floor(i / 3) + 0.5) * 100 / 3,
}))
const pad = ref(null)
const path = computed(() => sequence.value.map(id => {
  const { x, y } = points[id - 1]
  return `${x},${y}`
}).join(' '))
let pointerId = null

function add(id) {
  if (!id || sequence.value.includes(id)) return
  const next = [...sequence.value]
  if (next.length) {
    const from = next.at(-1) - 1, to = id - 1
    const rowSum = Math.floor(from / 3) + Math.floor(to / 3)
    const columnSum = from % 3 + to % 3
    if (rowSum % 2 === 0 && columnSum % 2 === 0) {
      const middle = rowSum / 2 * 3 + columnSum / 2 + 1
      if (!next.includes(middle)) next.push(middle)
    }
  }
  sequence.value = [...next, id]
}

function pointAt(event) {
  const box = pad.value.getBoundingClientRect()
  const x = (event.clientX - box.left) * 100 / box.width
  const y = (event.clientY - box.top) * 100 / box.height
  return points.find(point => Math.hypot(x - point.x, y - point.y) <= 11)?.id
}

function start(event) {
  if (props.readonly || !event.isPrimary || event.button !== 0) return
  const point = pointAt(event)
  if (!point) return
  pointerId = event.pointerId
  pad.value.setPointerCapture(pointerId)
  add(point)
}
function move(event) { if (event.pointerId === pointerId) add(pointAt(event)) }
function finish(event) {
  if (event.pointerId !== pointerId) return
  if (event.type === 'pointerup') add(pointAt(event))
  pointerId = null
}
function keyboardSelect(event, id) { if (event.detail === 0) add(id) }
</script>

<style scoped>
.gesture-pad { position: relative; width: min(100%, var(--gesture-size, 238px)); aspect-ratio: 1; border: 1px solid #cbd5e1; border-radius: 16px; background: #f8fafc; touch-action: none; user-select: none; }
.gesture-pad svg { display: block; width: 100%; height: 100%; pointer-events: none; }
circle { fill: #94a3b8; }
circle.selected { fill: #1d4ed8; }
polyline { fill: none; stroke: #2563eb; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
text { fill: #fff; text-anchor: middle; dominant-baseline: central; font-size: 5.8px; font-weight: 700; }
.gesture-pad button { position: absolute; transform: translate(-50%, -50%); width: 24%; aspect-ratio: 1; border: 0; border-radius: 50%; background: transparent; cursor: pointer; }
.gesture-pad button:focus-visible { outline: 2px solid #1d4ed8; }
.gesture-controls { display: flex; gap: 12px; align-items: center; color: #64748b; }
</style>

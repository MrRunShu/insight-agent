<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as pdfjsLib from 'pdfjs-dist'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import 'pdfjs-dist/web/pdf_viewer.css'

pdfjsLib.GlobalWorkerOptions.workerSrc = workerUrl

const props = defineProps({
  src: { type: String, required: true },
  annotations: { type: Array, default: () => [] },
})
const emit = defineEmits(['outline', 'create', 'delete'])

const readerEl = ref(null)
const scrollEl = ref(null)
const pagesEl = ref(null)
const numPages = ref(0)
const curPage = ref(1)
const zoom = ref(1)
const popup = ref({ show: false, x: 0, y: 0, mode: 'buttons', note: '' })

let pdf = null
let wrappers = []
let info = []          // per page: { hl, w, h, text }
let rendered = []
let baseScale = 1
let observer = null
let pending = null     // pending selection payload

async function load() {
  if (observer) observer.disconnect()
  wrappers = []; info = []; rendered = []
  if (pagesEl.value) pagesEl.value.innerHTML = ''
  popup.value.show = false
  try {
    pdf = await pdfjsLib.getDocument({ url: props.src }).promise
    numPages.value = pdf.numPages
    const first = await pdf.getPage(1)
    const vp1 = first.getViewport({ scale: 1 })
    baseScale = Math.max(0.4, ((scrollEl.value?.clientWidth || 700) - 28) / vp1.width)
    for (let i = 1; i <= numPages.value; i++) {
      const w = document.createElement('div')
      w.className = 'pg'
      pagesEl.value.appendChild(w)
      wrappers.push(w); rendered.push(false); info.push(null)
    }
    renderVisible()
    setTimeout(renderVisible, 120)
    loadOutline()
  } catch (e) {
    if (pagesEl.value) pagesEl.value.innerHTML = '<div style="color:#fff;padding:20px">PDF 加载失败：' + e.message + '</div>'
  }
}

async function renderPage(n) {
  rendered[n - 1] = true
  const page = await pdf.getPage(n)
  const outp = window.devicePixelRatio || 1
  const scale = baseScale * zoom.value
  const vp = page.getViewport({ scale })
  const w = wrappers[n - 1]
  w.style.width = vp.width + 'px'
  w.style.height = vp.height + 'px'
  w.innerHTML = ''

  // Build all three layers up front so they exist regardless of render timing.
  const canvas = document.createElement('canvas')
  canvas.className = 'pg-canvas'
  canvas.width = Math.floor(vp.width * outp)
  canvas.height = Math.floor(vp.height * outp)
  canvas.style.width = vp.width + 'px'
  canvas.style.height = vp.height + 'px'
  const tlDiv = document.createElement('div')
  tlDiv.className = 'textLayer'
  tlDiv.style.setProperty('--scale-factor', String(scale))
  tlDiv.style.width = vp.width + 'px'
  tlDiv.style.height = vp.height + 'px'
  const hl = document.createElement('div')
  hl.className = 'hl-layer'
  w.appendChild(canvas)
  w.appendChild(tlDiv)
  w.appendChild(hl)
  info[n - 1] = { hl, w: vp.width, h: vp.height, text: '' }
  drawHighlights(n)

  // Render canvas (fire-and-forget — don't block the text layer behind it).
  page.render({
    canvasContext: canvas.getContext('2d'),
    viewport: vp,
    transform: outp !== 1 ? [outp, 0, 0, outp, 0, 0] : null,
  }).promise.catch(() => {})

  // Render the selectable text layer in parallel.
  try {
    const tc = await page.getTextContent()
    info[n - 1].text = tc.items.map((it) => it.str).join(' ')
    if (pdfjsLib.TextLayer) {
      await new pdfjsLib.TextLayer({ textContentSource: tc, container: tlDiv, viewport: vp }).render()
    }
  } catch (e) { /* text layer unavailable on this page */ }
}

function drawHighlights(n) {
  const pi = info[n - 1]
  if (!pi) return
  pi.hl.innerHTML = ''
  for (const a of props.annotations) {
    if (a.page !== n || !Array.isArray(a.rects)) continue
    for (const r of a.rects) {
      const d = document.createElement('div')
      d.className = 'hl'
      d.style.left = r[0] * pi.w + 'px'
      d.style.top = r[1] * pi.h + 'px'
      d.style.width = r[2] * pi.w + 'px'
      d.style.height = r[3] * pi.h + 'px'
      if (a.note) d.title = a.note
      d.addEventListener('mousedown', (ev) => ev.stopPropagation())
      d.addEventListener('click', (ev) => { ev.stopPropagation(); showView(a, ev) })
      pi.hl.appendChild(d)
    }
  }
}

function onMouseUp() {
  const sel = window.getSelection()
  if (!sel || sel.isCollapsed || !sel.toString().trim()) return
  const range = sel.getRangeAt(0)
  let node = sel.anchorNode
  let el = node && (node.nodeType === 1 ? node : node.parentElement)
  const pg = el ? el.closest('.pg') : null
  if (!pg) return
  const n = wrappers.indexOf(pg) + 1
  const pgRect = pg.getBoundingClientRect()
  const cr = [...range.getClientRects()].filter((r) => r.width > 1 && r.height > 1)
  if (!cr.length) return
  const rects = cr.map((r) => [
    (r.left - pgRect.left) / pgRect.width,
    (r.top - pgRect.top) / pgRect.height,
    r.width / pgRect.width,
    r.height / pgRect.height,
  ])
  pending = { page: n, rects, quote: sel.toString().trim().slice(0, 4000), prefix: '', suffix: '' }
  const cont = readerEl.value.getBoundingClientRect()
  popup.value = {
    show: true,
    x: Math.max(6, cr[0].left - cont.left),
    y: Math.max(34, cr[0].top - cont.top - 40),
    mode: 'buttons',
    note: '',
  }
}

function doCreate(note) {
  if (!pending) return
  emit('create', { ...pending, color: 'yellow', note: note || null })
  window.getSelection()?.removeAllRanges()
  popup.value.show = false
  pending = null
}

function showView(a, ev) {
  const cont = readerEl.value.getBoundingClientRect()
  popup.value = {
    show: true, mode: 'view', viewId: a.id, viewNote: a.note || '（无笔记）',
    x: Math.max(6, ev.clientX - cont.left), y: Math.max(34, ev.clientY - cont.top - 10), note: '',
  }
}

function doDelete() {
  if (popup.value.viewId) emit('delete', popup.value.viewId)
  popup.value.show = false
}

async function loadOutline() {
  const ol = await pdf.getOutline()
  if (!ol || !ol.length) { emit('outline', []); return }
  const flat = []
  for (const it of ol) {
    flat.push({ title: it.title, page: await destToPage(it.dest), depth: 0 })
    if (it.items) for (const s of it.items) flat.push({ title: s.title, page: await destToPage(s.dest), depth: 1 })
  }
  emit('outline', flat)
}
async function destToPage(dest) {
  try {
    let d = dest
    if (typeof d === 'string') d = await pdf.getDestination(d)
    if (!Array.isArray(d)) return null
    return (await pdf.getPageIndex(d[0])) + 1
  } catch { return null }
}

// Render any page whose box is within ~600px of the viewport (lazy, no observer).
function renderVisible() {
  if (!scrollEl.value) return
  const r = scrollEl.value.getBoundingClientRect()
  for (let i = 0; i < wrappers.length; i++) {
    if (rendered[i]) continue
    const wr = wrappers[i].getBoundingClientRect()
    if (wr.bottom >= r.top - 600 && wr.top <= r.bottom + 600) renderPage(i + 1)
  }
}

function onScroll() {
  if (!scrollEl.value || !wrappers.length) return
  const rootTop = scrollEl.value.getBoundingClientRect().top
  let p = 1
  for (let i = 0; i < wrappers.length; i++) {
    if (wrappers[i].getBoundingClientRect().top - rootTop <= 80) p = i + 1
    else break
  }
  curPage.value = p
  renderVisible()
}
function scrollToPage(n) {
  const w = wrappers[n - 1]
  if (w && scrollEl.value) scrollEl.value.scrollTo({ top: w.offsetTop - 10, behavior: 'smooth' })
}
defineExpose({ scrollToPage })

function setZoom(z) {
  zoom.value = Math.min(2.5, Math.max(0.5, Math.round(z * 100) / 100))
  rendered = rendered.map(() => false)
  wrappers.forEach((w) => { w.innerHTML = '' })
  for (let i = Math.max(1, curPage.value - 1); i <= Math.min(numPages.value, curPage.value + 1); i++) renderPage(i)
}

watch(() => props.src, load)
watch(() => props.annotations, () => {
  for (let i = 0; i < wrappers.length; i++) if (rendered[i]) drawHighlights(i + 1)
}, { deep: true })

onMounted(load)
onBeforeUnmount(() => { if (observer) observer.disconnect() })
</script>

<template>
  <div ref="readerEl" class="reader">
    <div class="toolbar">
      <span class="pageno">{{ curPage }} / {{ numPages || '…' }}</span>
      <span class="sp"></span>
      <button @click="setZoom(zoom - 0.15)" aria-label="缩小">−</button>
      <span class="zoom">{{ Math.round(zoom * 100) }}%</span>
      <button @click="setZoom(zoom + 0.15)" aria-label="放大">＋</button>
    </div>
    <div ref="scrollEl" class="scroll" @scroll="onScroll" @mouseup="onMouseUp">
      <div ref="pagesEl" class="pages"></div>
    </div>

    <div v-if="popup.show" class="popup" :style="{ left: popup.x + 'px', top: popup.y + 'px' }" @mousedown.stop>
      <template v-if="popup.mode === 'buttons'">
        <button @click="doCreate(null)"><i></i>高亮</button>
        <button @click="popup.mode = 'note'">笔记</button>
      </template>
      <template v-else-if="popup.mode === 'note'">
        <textarea v-model="popup.note" placeholder="写点笔记…" rows="2"></textarea>
        <button @click="doCreate(popup.note)">保存</button>
      </template>
      <template v-else>
        <span class="note-text">{{ popup.viewNote }}</span>
        <button class="del" @click="doDelete">删除</button>
      </template>
    </div>
  </div>
</template>

<style scoped>
.reader { position: relative; display: flex; flex-direction: column; height: 100%; min-height: 0; }
.toolbar { flex: 0 0 auto; display: flex; align-items: center; gap: 8px; padding: 6px 14px; background: var(--raised); border-bottom: 1px solid var(--border); font-size: 12px; color: var(--text-soft); }
.toolbar .sp { flex: 1; }
.toolbar button { border: 1px solid var(--border); background: var(--panel); border-radius: 6px; width: 26px; height: 23px; cursor: pointer; font-size: 13px; color: var(--text); }
.zoom { min-width: 38px; text-align: center; }
.scroll { flex: 1; overflow: auto; background: #6e6e72; padding: 12px 0; }
.pages { display: flex; flex-direction: column; align-items: center; gap: 12px; }
:deep(.pg) { position: relative; background: #fff; box-shadow: 0 1px 5px rgba(0,0,0,.35); min-height: 200px; }
:deep(.pg-canvas) { display: block; }
:deep(.hl-layer) { position: absolute; inset: 0; pointer-events: none; }
:deep(.hl) { position: absolute; background: rgba(245,205,70,0.4); pointer-events: auto; cursor: pointer; border-radius: 2px; mix-blend-mode: multiply; }
:deep(.textLayer span::selection) { background: rgba(37, 99, 235, 0.25); }
:deep(.textLayer br::selection) { background: rgba(37, 99, 235, 0.25); }
.popup { position: absolute; z-index: 30; background: var(--panel); border: 1px solid var(--border); border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,.18); padding: 5px; display: flex; gap: 5px; align-items: flex-start; max-width: 240px; }
.popup button { border: 1px solid var(--border); background: var(--panel); border-radius: 6px; padding: 4px 9px; font-size: 12px; cursor: pointer; color: var(--text); white-space: nowrap; }
.popup button:hover { background: var(--accent-soft); }
.popup .del { color: #a33; }
.popup textarea { border: 1px solid var(--border); border-radius: 6px; padding: 4px 7px; font: inherit; font-size: 12px; resize: none; width: 150px; outline: none; }
.popup .note-text { font-size: 12px; color: var(--text); max-width: 160px; line-height: 1.4; padding: 2px 4px; }
</style>

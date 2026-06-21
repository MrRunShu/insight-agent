<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as pdfjsLib from 'pdfjs-dist'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'

pdfjsLib.GlobalWorkerOptions.workerSrc = workerUrl

const props = defineProps({ src: { type: String, required: true } })
const emit = defineEmits(['outline'])

const scrollEl = ref(null)
const pagesEl = ref(null)
const numPages = ref(0)
const curPage = ref(1)
const zoom = ref(1)
const loading = ref(true)

let pdf = null
let wrappers = []
let rendered = []
let baseScale = 1
let observer = null

async function load() {
  loading.value = true
  if (observer) observer.disconnect()
  wrappers = []; rendered = []
  if (pagesEl.value) pagesEl.value.innerHTML = ''
  try {
    pdf = await pdfjsLib.getDocument({ url: props.src }).promise
    numPages.value = pdf.numPages
    const first = await pdf.getPage(1)
    const vp1 = first.getViewport({ scale: 1 })
    const avail = (scrollEl.value?.clientWidth || 700) - 28
    baseScale = Math.max(0.4, avail / vp1.width)
    for (let i = 1; i <= numPages.value; i++) {
      const w = document.createElement('div')
      w.className = 'pg'
      pagesEl.value.appendChild(w)
      wrappers.push(w); rendered.push(false)
    }
    observer = new IntersectionObserver((es) => {
      for (const e of es) {
        const idx = wrappers.indexOf(e.target)
        if (idx < 0) continue
        if (e.isIntersecting && !rendered[idx]) renderPage(idx + 1)
      }
    }, { root: scrollEl.value, rootMargin: '400px 0px' })
    wrappers.forEach((w) => observer.observe(w))
    loadOutline()
  } catch (e) {
    if (pagesEl.value) pagesEl.value.innerHTML = '<div style="color:#fff;padding:20px">PDF 加载失败：' + e.message + '</div>'
  } finally {
    loading.value = false
  }
}

async function renderPage(n) {
  rendered[n - 1] = true
  const page = await pdf.getPage(n)
  const out = window.devicePixelRatio || 1
  const vp = page.getViewport({ scale: baseScale * zoom.value })
  const canvas = document.createElement('canvas')
  canvas.width = Math.floor(vp.width * out)
  canvas.height = Math.floor(vp.height * out)
  canvas.style.width = vp.width + 'px'
  canvas.style.height = vp.height + 'px'
  const w = wrappers[n - 1]
  w.style.height = vp.height + 'px'
  w.innerHTML = ''
  w.appendChild(canvas)
  await page.render({
    canvasContext: canvas.getContext('2d'),
    viewport: vp,
    transform: out !== 1 ? [out, 0, 0, out, 0, 0] : null,
  }).promise
}

async function destToPage(dest) {
  try {
    let d = dest
    if (typeof d === 'string') d = await pdf.getDestination(d)
    if (!Array.isArray(d)) return null
    return (await pdf.getPageIndex(d[0])) + 1
  } catch { return null }
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

function onScroll() {
  if (!scrollEl.value || !wrappers.length) return
  const rootTop = scrollEl.value.getBoundingClientRect().top
  let p = 1
  for (let i = 0; i < wrappers.length; i++) {
    if (wrappers[i].getBoundingClientRect().top - rootTop <= 80) p = i + 1
    else break
  }
  curPage.value = p
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

onMounted(load)
watch(() => props.src, load)
onBeforeUnmount(() => { if (observer) observer.disconnect() })
</script>

<template>
  <div class="reader">
    <div class="toolbar">
      <span class="pageno">{{ curPage }} / {{ numPages || '…' }}</span>
      <span class="sp"></span>
      <button @click="setZoom(zoom - 0.15)" aria-label="缩小">−</button>
      <span class="zoom">{{ Math.round(zoom * 100) }}%</span>
      <button @click="setZoom(zoom + 0.15)" aria-label="放大">＋</button>
    </div>
    <div ref="scrollEl" class="scroll" @scroll="onScroll">
      <div ref="pagesEl" class="pages"></div>
    </div>
  </div>
</template>

<style scoped>
.reader { display: flex; flex-direction: column; height: 100%; min-height: 0; }
.toolbar { flex: 0 0 auto; display: flex; align-items: center; gap: 8px; padding: 6px 14px; background: var(--raised); border-bottom: 1px solid var(--border); font-size: 12px; color: var(--text-soft); }
.toolbar .sp { flex: 1; }
.toolbar button { border: 1px solid var(--border); background: var(--panel); border-radius: 6px; width: 26px; height: 23px; cursor: pointer; font-size: 13px; color: var(--text); }
.zoom { min-width: 38px; text-align: center; }
.scroll { flex: 1; overflow: auto; background: #6e6e72; padding: 12px 0; }
.pages { display: flex; flex-direction: column; align-items: center; gap: 12px; }
:deep(.pg) { background: #fff; box-shadow: 0 1px 5px rgba(0,0,0,.35); min-height: 200px; }
</style>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { listPapers, groupByCategory, paperFileUrl, uploadPaper, deletePaper } from '../api/papers.js'
import AssistantChat from './AssistantChat.vue'

const emit = defineEmits(['home'])

const papers = ref([])
const openPaper = ref(null)
const uploadMsg = ref('')
const rightWidth = ref(330)

const groups = computed(() => groupByCategory(papers.value))

async function refresh() {
  try { papers.value = await listPapers() } catch (e) { uploadMsg.value = '加载失败：' + e.message }
}
onMounted(refresh)

function open(p) { openPaper.value = p }
function closePaper() { openPaper.value = null }

async function onUpload(e) {
  const file = e.target.files?.[0]
  if (!file) return
  uploadMsg.value = `上传中：${file.name}…`
  const data = await uploadPaper(file, '未分类')
  uploadMsg.value = data.error ? `❌ ${data.error}` : `✅ 已入库：${data.filename}`
  e.target.value = ''
  await refresh()
  setTimeout(() => { uploadMsg.value = '' }, 5000)
}

async function removePaper(p) {
  if (!confirm(`删除《${p.title}》？（可恢复的软删除）`)) return
  await deletePaper(p.id)
  if (openPaper.value && openPaper.value.id === p.id) closePaper()
  await refresh()
}

// Drag the middle|right divider to resize.
function startResize(e) {
  e.preventDefault()
  const startX = e.clientX
  const startW = rightWidth.value
  const move = (ev) => {
    const w = startW + (startX - ev.clientX)
    rightWidth.value = Math.min(620, Math.max(260, w))
  }
  const up = () => { window.removeEventListener('mousemove', move); window.removeEventListener('mouseup', up) }
  window.addEventListener('mousemove', move)
  window.addEventListener('mouseup', up)
}

const chatHint = computed(() =>
  openPaper.value ? `（针对论文《${openPaper.value.title}》，回答时优先依据本文内容并标注页码）` : '')
</script>

<template>
  <div class="view">
    <header class="topbar">
      <button class="icon-btn" title="返回主页" @click="emit('home')">←</button>
      <span class="logo">🎓</span>
      <span class="name">学术成长</span>
      <label class="upload-btn" title="上传论文 PDF">
        <input type="file" accept="application/pdf,.pdf" hidden @change="onUpload" />
        ＋ 添加论文
      </label>
      <span v-if="uploadMsg" class="upload-msg">{{ uploadMsg }}</span>
    </header>

    <div class="body">
      <!-- left rail -->
      <nav class="rail">
        <div class="nav-item active"><span>📚</span> 论文库</div>
        <div class="nav-item"><span>💬</span> 个人对话</div>
        <div v-if="openPaper" class="toc">
          <div class="toc-head">本篇</div>
          <div class="toc-title">{{ openPaper.title }}</div>
          <div class="toc-meta">{{ openPaper.pageCount }} 页 · {{ openPaper.category }}</div>
          <div class="toc-hint">章节目录见阅读器侧栏（Phase 2 接入本栏）</div>
        </div>
      </nav>

      <!-- middle -->
      <main class="middle">
        <template v-if="!openPaper">
          <div class="lib-head">论文库 <span class="muted">· {{ papers.length }} 篇</span></div>
          <div class="lib-scroll">
            <div v-for="(items, cat) in groups" :key="cat" class="cat">
              <div class="cat-name">{{ cat }}</div>
              <div class="cat-grid">
                <div v-for="p in items" :key="p.id" class="paper" @click="open(p)">
                  <span class="paper-title">{{ p.title }}</span>
                  <span class="paper-meta">{{ p.pageCount }} 页</span>
                  <button class="del" title="删除" @click.stop="removePaper(p)">🗑</button>
                </div>
              </div>
            </div>
            <div v-if="!papers.length" class="empty">还没有论文，点右上角「添加论文」上传 PDF。</div>
          </div>
        </template>
        <template v-else>
          <div class="reader-head">
            <button class="icon-btn" title="返回论文库" @click="closePaper">←</button>
            <span class="reader-title">{{ openPaper.filename }}</span>
            <button class="del" title="删除" @click="removePaper(openPaper)">🗑</button>
          </div>
          <iframe class="pdf" :src="paperFileUrl(openPaper.id)" :title="openPaper.title"></iframe>
        </template>
      </main>

      <!-- resize handle -->
      <div class="divider" title="拖动调整宽度" @mousedown="startResize">⋮</div>

      <!-- right chat -->
      <aside class="right" :style="{ width: rightWidth + 'px', minWidth: rightWidth + 'px' }">
        <div class="right-head">💬 {{ openPaper ? '问这篇论文' : '学术问答' }}</div>
        <AssistantChat
          :context-hint="chatHint"
          :intro="openPaper ? '就这篇论文问我任何问题，我会依据原文回答并标注页码。' : '选一篇论文开始，或直接问我学术上的问题。'"
          :placeholder="openPaper ? '就这篇提问…' : '问问助理…'"
        />
      </aside>
    </div>
  </div>
</template>

<style scoped>
.view { display: flex; flex-direction: column; height: 100vh; }
.topbar { flex: 0 0 52px; display: flex; align-items: center; gap: 10px; padding: 0 18px; background: var(--raised); border-bottom: 1px solid var(--border); }
.logo { font-size: 16px; }
.name { font-weight: 600; font-size: 14px; }
.upload-btn { margin-left: auto; display: inline-flex; align-items: center; gap: 4px; font-size: 12.5px; padding: 5px 11px; border: 1px solid var(--border); border-radius: 8px; background: var(--panel); cursor: pointer; }
.upload-btn:hover { border-color: var(--accent); background: var(--accent-soft); }
.upload-msg { font-size: 12px; color: var(--text-soft); }
.icon-btn { border: 1px solid var(--border); background: var(--panel); border-radius: 7px; width: 28px; height: 26px; cursor: pointer; font-size: 14px; color: var(--text-soft); }
.body { flex: 1; display: flex; min-height: 0; }
.rail { width: 150px; min-width: 150px; border-right: 1px solid var(--border); background: var(--raised); padding: 10px 8px; }
.nav-item { display: flex; align-items: center; gap: 8px; padding: 8px 10px; border-radius: 8px; font-size: 13px; color: var(--text-soft); cursor: pointer; }
.nav-item.active { background: var(--accent-soft); color: var(--accent-strong); font-weight: 600; }
.toc { margin-top: 14px; border-top: 1px solid var(--border); padding-top: 10px; }
.toc-head { font-size: 10.5px; letter-spacing: .05em; color: #b7a98a; margin-bottom: 6px; }
.toc-title { font-size: 12px; font-weight: 600; line-height: 1.4; }
.toc-meta { font-size: 11px; color: var(--text-soft); margin-top: 3px; }
.toc-hint { font-size: 10px; color: #c2b79c; margin-top: 8px; line-height: 1.4; }
.middle { flex: 1; display: flex; flex-direction: column; min-width: 0; background: var(--bg); }
.lib-head { padding: 14px 18px 8px; font-size: 14px; font-weight: 600; }
.muted { color: var(--text-soft); font-weight: 400; }
.lib-scroll { flex: 1; overflow-y: auto; padding: 6px 18px 18px; }
.cat { margin-bottom: 16px; }
.cat-name { font-size: 11px; letter-spacing: .04em; color: #b7a98a; margin-bottom: 8px; }
.cat-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 10px; }
.paper { position: relative; background: var(--panel); border: 1px solid var(--border); border-radius: 10px; padding: 12px 13px; cursor: pointer; display: flex; flex-direction: column; gap: 4px; transition: border-color .15s, transform .1s; }
.paper:hover { border-color: var(--accent); transform: translateY(-1px); }
.paper-title { font-size: 12.5px; font-weight: 500; line-height: 1.4; }
.paper-meta { font-size: 11px; color: var(--text-soft); }
.del { position: absolute; top: 8px; right: 8px; border: none; background: transparent; cursor: pointer; font-size: 12px; opacity: 0; }
.paper:hover .del { opacity: 0.6; }
.del:hover { opacity: 1; }
.empty { color: var(--text-soft); font-size: 13px; padding: 20px 4px; }
.reader-head { display: flex; align-items: center; gap: 10px; padding: 8px 12px; background: var(--raised); border-bottom: 1px solid var(--border); }
.reader-title { font-size: 12.5px; font-weight: 500; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.reader-head .del { position: static; opacity: 0.6; }
.pdf { flex: 1; width: 100%; border: none; background: #525659; }
.divider { width: 10px; cursor: col-resize; background: var(--raised); border-left: 1px solid var(--border); border-right: 1px solid var(--border); display: flex; align-items: center; justify-content: center; color: #b7a98a; font-size: 12px; user-select: none; }
.right { display: flex; flex-direction: column; min-height: 0; }
.right-head { flex: 0 0 auto; padding: 11px 14px; border-bottom: 1px solid var(--border); font-size: 13px; font-weight: 600; background: var(--raised); }
</style>

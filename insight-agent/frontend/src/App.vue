<script setup>
import { ref } from 'vue'
import { NConfigProvider } from 'naive-ui'
import ChatPanel from './components/ChatPanel.vue'
import StepsPanel from './components/StepsPanel.vue'
import { streamAgent } from './api/agentStream.js'

const messages = ref([])
const steps = ref([])
const running = ref(false)
const ragEnabled = ref(false)

let controller = null
let stepCounter = 0

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8123/api'
const uploadStatus = ref('')

// Upload a paper PDF to the knowledge base — parsed, embedded and indexed on the fly.
async function onUploadPaper(e) {
  const file = e.target.files?.[0]
  if (!file) return
  uploadStatus.value = `上传中：${file.name}…`
  try {
    const fd = new FormData()
    fd.append('file', file)
    const res = await fetch(`${API_BASE}/papers/upload`, { method: 'POST', body: fd })
    const data = await res.json()
    uploadStatus.value = data.error
      ? `❌ ${file.name}：${data.error}`
      : `✅ 已入库：${data.fileName}（${data.chunks} 块）`
  } catch (err) {
    uploadStatus.value = `❌ 上传失败：${err.message}`
  } finally {
    e.target.value = ''
    setTimeout(() => { uploadStatus.value = '' }, 6000)
  }
}

// Warm parchment theme — mirrors the CSS variables defined in style.css
const themeOverrides = {
  common: {
    primaryColor: '#7c4d1e',
    primaryColorHover: '#9b6028',
    primaryColorPressed: '#5c3510',
    primaryColorSuppl: '#9b6028',
    borderRadius: '8px',
    bodyColor: '#f4edd8',
    cardColor: '#fdf8ed',
    modalColor: '#fdf8ed',
    popoverColor: '#fdf8ed',
    borderColor: '#d8c9a3',
    dividerColor: '#d8c9a3',
    textColorBase: '#2a1a08',
    textColor1: '#2a1a08',
    textColor2: '#4a3018',
    textColor3: '#7a6040',
    placeholderColor: '#a08060',
    inputColor: '#fdf8ed',
    inputColorDisabled: '#f0e8d4',
    fontFamily:
      '"Ma Shan Zheng", "Noto Serif SC", "KaiTi", "楷体", "STKaiti", Georgia, serif',
  },
  Button: {
    fontWeightStrong: '500',
  },
}

function labelFor(content) {
  if (content.startsWith('📄')) return '📄 检索内容'
  if (content.startsWith('Executed:')) return '🔧 工具执行'
  if (content.includes('searchKnowledgeBase') || content.includes('fetchWebPage') || content.includes('writeFile')) return '🔧 工具执行'
  return '💭 推理'
}

function onSubmit(text) {
  messages.value.push({ role: 'user', content: text })
  steps.value = []
  stepCounter = 0
  running.value = true

  controller = streamAgent(
    { message: text, ragEnabled: ragEnabled.value },
    {
      onStep(data) {
        steps.value.push({
          id: `step-${++stepCounter}`,
          step: data.step,
          label: labelFor(data.content || ''),
          content: data.content || '',
        })
      },
      onDone(data) {
        running.value = false
        steps.value.push({
          id: `step-${++stepCounter}`,
          step: data.step,
          label: '✅ 任务完成',
          content: `共 ${stepCounter} 步`,
        })
        messages.value.push({ role: 'assistant', content: data.content || '（无内容）' })
      },
      onError(data) {
        running.value = false
        messages.value.push({
          role: 'assistant',
          content: data.content || '发生未知错误。',
          isError: true,
        })
      },
    },
  )
}

function onStop() {
  controller?.close()
  running.value = false
  messages.value.push({
    role: 'assistant',
    content: '已手动停止本次分析。',
    isError: true,
  })
}
</script>

<template>
  <n-config-provider :theme-overrides="themeOverrides">
    <div class="layout">
      <header class="topbar">
        <div class="brand">
          <span class="logo">🔍</span>
          <span class="name">InsightAgent</span>
          <span class="tagline">个人 AI 助理 · 工作学习</span>
        </div>
        <div class="topbar-right">
          <span v-if="uploadStatus" class="upload-status">{{ uploadStatus }}</span>
          <label class="upload-btn" title="上传论文 PDF 到知识库">
            <input type="file" accept="application/pdf,.pdf" @change="onUploadPaper" hidden />
            📎 添加论文
          </label>
          <label class="rag-toggle" :class="{ active: ragEnabled }">
            <n-switch v-model:value="ragEnabled" size="small" />
            <span class="rag-label">
              <span class="rag-icon">📚</span>
              知识库 RAG
              <span class="rag-badge" v-if="ragEnabled">ON</span>
              <span class="rag-badge off" v-else>OFF</span>
            </span>
          </label>
          <a
            class="repo"
            href="http://localhost:8123/api/doc.html"
            target="_blank"
            rel="noreferrer"
          >API 文档</a>
        </div>
      </header>

      <main class="body">
        <section class="left">
          <ChatPanel :messages="messages" :running="running" @submit="onSubmit" @stop="onStop" />
        </section>
        <aside class="right">
          <StepsPanel :steps="steps" :running="running" />
        </aside>
      </main>
    </div>
  </n-config-provider>
</template>

<style scoped>
.layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
}
.topbar {
  flex: 0 0 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: #ecdcc0;           /* one shade deeper than --bg for visual hierarchy */
  border-bottom: 2px solid #c9ab7c;  /* warm gold separator */
}
.brand {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.brand .logo {
  font-size: 20px;
}
.brand .name {
  font-weight: 700;
  font-size: 17px;
}
.brand .tagline {
  font-size: 13px;
  color: var(--text-soft);
}
.topbar-right {
  display: flex;
  align-items: center;
  gap: 20px;
}
.rag-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 8px;
  border: 1px solid var(--border);
  background: var(--panel);
  transition: border-color 0.2s, background 0.2s;
}
.rag-toggle.active {
  border-color: #7c4d1e;
  background: #f4e8d0;
}
.rag-label {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text);
  user-select: none;
}
.rag-icon {
  font-size: 14px;
}
.rag-badge {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 4px;
  background: #7c4d1e;
  color: #fff;
  letter-spacing: 0.5px;
}
.rag-badge.off {
  background: #b0a090;
}
.repo {
  font-size: 13px;
  color: var(--accent);
  text-decoration: none;
}
.repo:hover {
  text-decoration: underline;
}
.upload-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 500;
  padding: 4px 10px;
  border-radius: 8px;
  border: 1px solid var(--border);
  background: var(--panel);
  color: var(--text);
  cursor: pointer;
  user-select: none;
  transition: border-color 0.2s, background 0.2s;
}
.upload-btn:hover {
  border-color: #7c4d1e;
  background: #f4e8d0;
}
.upload-status {
  font-size: 12px;
  color: var(--text-soft);
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.body {
  flex: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
  min-height: 0;
}
.left {
  min-height: 0;
  border-right: 1px solid var(--border);
}
.right {
  min-height: 0;
}
@media (max-width: 860px) {
  .body {
    grid-template-columns: 1fr;
    grid-template-rows: 1fr 1fr;
  }
  .left {
    border-right: none;
    border-bottom: 1px solid var(--border);
  }
}
</style>

<script setup>
import { ref } from 'vue'
import { NConfigProvider } from 'naive-ui'
import ChatPanel from './components/ChatPanel.vue'
import StepsPanel from './components/StepsPanel.vue'
import { streamAgent } from './api/agentStream.js'

const messages = ref([])
const steps = ref([])
const running = ref(false)

let controller = null
let stepCounter = 0

// Light theme tuned to match our CSS variables.
const themeOverrides = {
  common: {
    primaryColor: '#2563eb',
    primaryColorHover: '#1d4ed8',
    primaryColorPressed: '#1e40af',
    borderRadius: '8px',
  },
}

function labelFor(content) {
  return content.startsWith('Executed:') ? '🔧 工具执行' : '💭 推理'
}

function onSubmit(text) {
  messages.value.push({ role: 'user', content: text })
  steps.value = []
  stepCounter = 0
  running.value = true

  controller = streamAgent(
    { message: text },
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
        // Also add the final answer as the last step so the right panel is
        // never empty (the model may call terminate on step 1 without any
        // intermediate tool calls).
        steps.value.push({
          id: `step-${++stepCounter}`,
          step: data.step,
          label: '✅ 最终答案',
          content: data.content || '',
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
          <span class="tagline">新闻深度分析助手</span>
        </div>
        <a
          class="repo"
          href="http://localhost:8123/api/doc.html"
          target="_blank"
          rel="noreferrer"
        >API 文档</a>
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
  background: var(--panel);
  border-bottom: 1px solid var(--border);
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
.repo {
  font-size: 13px;
  color: var(--accent);
  text-decoration: none;
}
.repo:hover {
  text-decoration: underline;
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

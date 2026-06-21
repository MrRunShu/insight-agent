<script setup>
import AssistantChat from './AssistantChat.vue'
const emit = defineEmits(['open'])

const cards = [
  { key: 'academic', icon: '🎓', title: '学术成长', desc: '论文库 · 阅读 · 问答', active: true },
  { key: 'todo',     icon: '🗂️', title: 'Todo List',  desc: '任务与计划',           active: false },
  { key: 'news',     icon: '📰', title: '产业新闻',  desc: '行业动态追踪',           active: false },
]
</script>

<template>
  <div class="welcome">
    <header class="topbar">
      <span class="logo">✦</span>
      <span class="name">InsightAgent</span>
      <div class="tabs">
        <button
          v-for="c in cards"
          :key="c.key"
          class="tab"
          :class="{ active: c.active, disabled: !c.active }"
          @click="c.active && emit('open', c.key)"
        >
          <span class="tab-icon">{{ c.icon }}</span>
          <span class="tab-title">{{ c.title }}</span>
          <span v-if="!c.active" class="soon">即将推出</span>
        </button>
      </div>
    </header>

    <div class="chat-wrap">
      <AssistantChat
        intro="你好！我帮你统筹工作与学习。点上方功能进入对应模块，或直接告诉我你想做什么。"
        placeholder="问问助理…"
      />
    </div>
  </div>
</template>

<style scoped>
.welcome { display: flex; flex-direction: column; height: 100vh; }

.topbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 20px;
  height: 54px;
  background: var(--raised);
  border-bottom: 1px solid var(--border);
}
.logo { font-size: 18px; color: var(--accent); }
.name { font-weight: 600; font-size: 15px; margin-right: 4px; }

.tabs { display: flex; gap: 6px; }
.tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 20px;
  border: 1px solid var(--border);
  background: var(--panel);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  color: var(--text);
  transition: border-color .15s, background .15s;
  position: relative;
}
.tab.active { border-color: var(--accent); background: var(--accent-soft); color: var(--accent-strong); font-weight: 600; }
.tab.active:hover { background: var(--accent-soft); }
.tab.disabled { cursor: default; opacity: 0.6; }
.tab-icon { font-size: 14px; }
.soon {
  position: absolute;
  top: -6px; right: -4px;
  font-size: 9px;
  background: var(--raised);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 1px 5px;
  color: #b7a98a;
  white-space: nowrap;
}

.chat-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
</style>

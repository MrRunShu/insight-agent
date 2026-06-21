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
      <span class="tag">个人 AI 助理</span>
    </header>

    <div class="body">
      <!-- left: feature cards 30% -->
      <nav class="features">
        <div class="features-label">功能模块</div>
        <button
          v-for="c in cards"
          :key="c.key"
          class="feat-card"
          :class="{ active: c.active, disabled: !c.active }"
          @click="c.active && emit('open', c.key)"
        >
          <span class="feat-icon">{{ c.icon }}</span>
          <div class="feat-text">
            <span class="feat-title">{{ c.title }}</span>
            <span class="feat-desc">{{ c.desc }}</span>
          </div>
          <span v-if="!c.active" class="soon">即将推出</span>
        </button>
      </nav>

      <!-- divider -->
      <div class="vline"></div>

      <!-- right: chat 70% -->
      <div class="chat-wrap">
        <AssistantChat
          intro="你好！我帮你统筹工作与学习。点左侧功能进入对应模块，或直接告诉我你想做什么。"
          placeholder="问问助理…"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.welcome { display: flex; flex-direction: column; height: 100vh; }

.topbar {
  flex: 0 0 52px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 22px;
  background: var(--raised);
  border-bottom: 1px solid var(--border);
}
.logo  { font-size: 20px; color: var(--accent); }
.name  { font-weight: 700; font-size: 16px; }
.tag   { font-size: 12.5px; color: var(--text-soft); }

/* ── body ── */
.body {
  flex: 1;
  display: flex;
  min-height: 0;
}

/* ── left 30% ── */
.features {
  flex: 0 0 30%;
  padding: 28px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow-y: auto;
}
.features-label {
  font-size: 11px;
  letter-spacing: .08em;
  color: var(--text-soft);
  text-transform: uppercase;
  margin-bottom: 4px;
}
.feat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  border-radius: 14px;
  border: 1.5px solid var(--border);
  background: var(--panel);
  cursor: pointer;
  font: inherit;
  color: var(--text);
  position: relative;
  transition: border-color .15s, transform .1s, background .15s;
  text-align: left;
}
.feat-card.active {
  border-color: var(--accent);
  background: var(--accent-soft);
}
.feat-card.active:hover { transform: translateY(-2px); }
.feat-card.disabled     { cursor: default; opacity: 0.65; }
.feat-icon  { font-size: 32px; line-height: 1; flex-shrink: 0; }
.feat-text  { display: flex; flex-direction: column; gap: 3px; }
.feat-title { font-size: 15px; font-weight: 600; color: var(--text); }
.feat-desc  { font-size: 12px; color: var(--text-soft); }
.soon {
  position: absolute;
  top: 8px; right: 10px;
  font-size: 10px;
  background: var(--raised);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 2px 7px;
  color: #b7a98a;
}

.vline { width: 1px; background: var(--border); flex-shrink: 0; }

/* ── right 70% ── */
.chat-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
</style>

<script setup>
import AssistantChat from './AssistantChat.vue'
const emit = defineEmits(['open'])

const cards = [
  { key: 'academic', icon: '🎓', title: '学术成长', desc: '论文库 · 阅读 · 问答', active: true },
  { key: 'todo', icon: '🗂️', title: 'Todo List', desc: '任务与计划', active: false },
  { key: 'news', icon: '📰', title: '产业新闻', desc: '行业动态追踪', active: false },
]
</script>

<template>
  <div class="welcome">
    <header class="topbar">
      <span class="logo">✦</span>
      <span class="name">InsightAgent</span>
      <span class="tag">个人 AI 助理 · 工作学习</span>
    </header>
    <div class="body">
      <div class="main">
        <div class="hello">想从哪儿开始？</div>
        <div class="cards">
          <button
            v-for="c in cards"
            :key="c.key"
            class="card"
            :class="{ active: c.active, disabled: !c.active }"
            @click="c.active && emit('open', c.key)"
          >
            <span class="card-icon">{{ c.icon }}</span>
            <span class="card-title">{{ c.title }}</span>
            <span class="card-desc">{{ c.desc }}</span>
            <span v-if="!c.active" class="soon">即将推出</span>
          </button>
        </div>
      </div>
      <aside class="side">
        <div class="side-head"><span class="dot"></span>助理</div>
        <AssistantChat
          intro="你好！我帮你统筹工作与学习。点左边的卡片进入功能，或直接告诉我你想做什么。"
          placeholder="问问助理…"
        />
      </aside>
    </div>
  </div>
</template>

<style scoped>
.welcome { display: flex; flex-direction: column; height: 100vh; }
.topbar { flex: 0 0 56px; display: flex; align-items: baseline; gap: 10px; padding: 0 24px; background: var(--raised); border-bottom: 1px solid var(--border); }
.topbar .logo { font-size: 18px; color: var(--accent); align-self: center; }
.topbar .name { font-weight: 600; font-size: 17px; }
.topbar .tag { font-size: 13px; color: var(--text-soft); }
.body { flex: 1; display: flex; min-height: 0; }
.main { flex: 1; padding: 32px 28px; }
.hello { font-size: 15px; color: var(--text-soft); margin-bottom: 20px; }
.cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; max-width: 620px; }
.card { text-align: left; display: flex; flex-direction: column; gap: 4px; padding: 20px 18px; border-radius: 14px; border: 1px solid var(--border); background: var(--panel); cursor: pointer; font: inherit; color: var(--text); position: relative; transition: border-color .15s, transform .1s; }
.card.active { border: 2px solid var(--accent); }
.card.active:hover { transform: translateY(-2px); }
.card.disabled { cursor: default; opacity: 0.7; }
.card-icon { font-size: 26px; }
.card-title { font-size: 15px; font-weight: 600; margin-top: 8px; }
.card-desc { font-size: 12.5px; color: var(--text-soft); }
.soon { font-size: 10.5px; color: #b7a98a; margin-top: 6px; }
.side { width: 300px; min-width: 300px; border-left: 1px solid var(--border); display: flex; flex-direction: column; min-height: 0; }
.side-head { flex: 0 0 auto; padding: 12px 16px; border-bottom: 1px solid var(--border); font-weight: 600; font-size: 13px; display: flex; align-items: center; gap: 7px; background: var(--raised); }
.side-head .dot { width: 7px; height: 7px; border-radius: 50%; background: #5fa86a; }
</style>

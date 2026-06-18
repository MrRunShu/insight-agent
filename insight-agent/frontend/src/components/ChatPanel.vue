<script setup>
import { ref, nextTick, watch } from 'vue'
import MarkdownView from './MarkdownView.vue'

const props = defineProps({
  messages: { type: Array, default: () => [] },
  running: { type: Boolean, default: false },
})
const emit = defineEmits(['submit', 'stop'])

const input = ref('')
const scrollRef = ref(null)

function submit() {
  const text = input.value.trim()
  if (!text || props.running) return
  emit('submit', text)
  input.value = ''
}

// Ctrl/Cmd + Enter to send; plain Enter inserts a newline.
function onKeydown(e) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
    e.preventDefault()
    submit()
  }
}

async function scrollToBottom() {
  await nextTick()
  const el = scrollRef.value
  if (el) el.scrollTop = el.scrollHeight
}
watch(() => props.messages.length, scrollToBottom)
watch(() => props.running, scrollToBottom)
</script>

<template>
  <div class="chat-panel">
    <div ref="scrollRef" class="messages">
      <div v-if="!messages.length" class="welcome">
        <div class="welcome-icon">🔍</div>
        <h2>InsightAgent</h2>
        <p>
          向你的个人论文知识库提问——解释概念、总结某篇论文、或对比多篇论文之间的关联。
          Agent 会检索相关论文片段、引用来源作答。右侧实时展示它的思考过程。
        </p>
      </div>

      <div
        v-for="(m, i) in messages"
        :key="i"
        class="msg"
        :class="m.role"
      >
        <div class="avatar">{{ m.role === 'user' ? '我' : 'AI' }}</div>
        <div class="bubble" :class="{ error: m.isError }">
          <MarkdownView v-if="m.role === 'assistant'" :source="m.content" />
          <span v-else class="user-text">{{ m.content }}</span>
        </div>
      </div>
    </div>

    <div class="composer">
      <n-input
        v-model:value="input"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 6 }"
        placeholder="向你的论文库提问，例如「总结一下 OctoTools 的核心架构」…（Ctrl/⌘ + Enter 发送）"
        :disabled="running"
        @keydown="onKeydown"
      />
      <div class="composer-actions">
        <span class="hint">Ctrl / ⌘ + Enter 发送</span>
        <n-button
          v-if="running"
          tertiary
          type="error"
          size="small"
          @click="emit('stop')"
        >
          停止
        </n-button>
        <n-button
          type="primary"
          :loading="running"
          :disabled="!input.trim()"
          @click="submit"
        >
          提问
        </n-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg);
}
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}
.welcome {
  max-width: 460px;
  margin: 72px auto 0;
  text-align: center;
  color: var(--text-soft);
}
.welcome-icon {
  font-size: 40px;
}
.welcome h2 {
  margin: 12px 0 8px;
  color: var(--text);
}
.welcome p {
  line-height: 1.7;
  font-size: 14px;
}
.msg {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  align-items: flex-start;
}
.msg.user {
  flex-direction: row-reverse;
}
.avatar {
  flex: 0 0 32px;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: var(--accent);
}
.msg.user .avatar {
  background: #5a3e28;
}
.bubble {
  max-width: 78%;
  padding: 10px 14px;
  border-radius: 10px;
  background: var(--panel);
  border: 1px solid var(--border);
}
.msg.user .bubble {
  background: var(--accent-soft);
  border-color: #c9ab82;
}
.bubble.error {
  background: #fdf0e8;
  border-color: #e8b494;
  color: #8b3a1a;
}
.user-text {
  white-space: pre-wrap;
  font-size: 14px;
  line-height: 1.6;
}
.composer {
  border-top: 1px solid var(--border);
  padding: 14px 20px 18px;
  background: var(--panel);
}
.composer-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 10px;
}
.composer-actions .hint {
  margin-right: auto;
  font-size: 12px;
  color: var(--text-soft);
}
</style>

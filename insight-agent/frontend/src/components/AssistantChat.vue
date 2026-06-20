<script setup>
import { ref, watch, nextTick } from 'vue'
import { streamAgent } from '../api/agentStream.js'
import MarkdownView from './MarkdownView.vue'

const props = defineProps({
  intro: { type: String, default: '' },
  placeholder: { type: String, default: '问我任何问题…' },
  // Prepended to each user message so the agent focuses (e.g. a specific paper).
  contextHint: { type: String, default: '' },
})

const messages = ref([])
const input = ref('')
const running = ref(false)
const scrollRef = ref(null)
let controller = null

// New context (e.g. switched paper) → fresh conversation.
watch(() => props.contextHint, () => { messages.value = []; })

function scrollDown() {
  nextTick(() => { if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight })
}

function send() {
  const text = input.value.trim()
  if (!text || running.value) return
  input.value = ''
  messages.value.push({ role: 'user', content: text })
  messages.value.push({ role: 'assistant', content: '', thinking: true })
  running.value = true
  scrollDown()

  const message = props.contextHint ? `${props.contextHint}\n${text}` : text
  controller = streamAgent(
    { message, ragEnabled: true },
    {
      onStep() { scrollDown() },
      onDone(data) {
        running.value = false
        const last = messages.value[messages.value.length - 1]
        last.content = data.content || '（无内容）'
        last.thinking = false
        scrollDown()
      },
      onError(data) {
        running.value = false
        const last = messages.value[messages.value.length - 1]
        last.content = data.content || '发生错误。'
        last.thinking = false
        last.isError = true
        scrollDown()
      },
    },
  )
}

function onKeydown(e) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') { e.preventDefault(); send() }
}
</script>

<template>
  <div class="chat">
    <div ref="scrollRef" class="messages">
      <div v-if="intro && !messages.length" class="bubble assistant intro">{{ intro }}</div>
      <template v-for="(m, i) in messages" :key="i">
        <div class="bubble" :class="[m.role, { error: m.isError }]">
          <span v-if="m.thinking" class="thinking">思考中…</span>
          <MarkdownView v-else-if="m.role === 'assistant'" :source="m.content" />
          <span v-else>{{ m.content }}</span>
        </div>
      </template>
    </div>
    <div class="composer">
      <textarea
        v-model="input"
        :placeholder="placeholder"
        :disabled="running"
        rows="2"
        @keydown="onKeydown"
      ></textarea>
      <button :disabled="running || !input.trim()" @click="send">
        <span v-if="running">…</span><span v-else>↑</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.chat { display: flex; flex-direction: column; height: 100%; min-height: 0; background: var(--raised); }
.messages { flex: 1; overflow-y: auto; padding: 12px; display: flex; flex-direction: column; gap: 10px; min-height: 0; }
.bubble { max-width: 92%; padding: 8px 11px; border-radius: 10px; font-size: 13px; line-height: 1.6; }
.bubble.assistant { align-self: flex-start; background: var(--panel); border: 1px solid var(--border); border-radius: 3px 10px 10px 10px; }
.bubble.user { align-self: flex-end; background: var(--accent-soft); border: 1px solid #E5D4B8; border-radius: 10px 3px 10px 10px; }
.bubble.intro { color: var(--text-soft); }
.bubble.error { color: #a33; }
.thinking { color: var(--text-soft); font-style: italic; }
.composer { border-top: 1px solid var(--border); padding: 9px; display: flex; gap: 7px; align-items: flex-end; }
.composer textarea { flex: 1; resize: none; border: 1px solid var(--border); border-radius: 8px; padding: 7px 10px; font: inherit; font-size: 13px; background: var(--panel); color: var(--text); outline: none; }
.composer textarea:focus { border-color: var(--accent); }
.composer button { width: 32px; height: 32px; flex-shrink: 0; border: none; border-radius: 8px; background: var(--accent); color: #fff; font-size: 15px; cursor: pointer; }
.composer button:disabled { opacity: 0.5; cursor: default; }
</style>

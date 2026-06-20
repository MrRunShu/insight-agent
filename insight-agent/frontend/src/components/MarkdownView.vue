<script setup>
import { ref, watch, nextTick, onMounted } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import mermaid from 'mermaid'

const props = defineProps({
  source: { type: String, default: '' },
})

marked.setOptions({
  breaks: true,
  gfm: true,
  highlight(code, lang) {
    if (lang && lang !== 'mermaid' && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value
    }
    return hljs.highlightAuto(code).value
  },
})

mermaid.initialize({ startOnLoad: false, theme: 'neutral' })

const container = ref(null)
const html = ref('')
let seq = 0

// After marked renders ```mermaid blocks to <pre><code class="language-mermaid">,
// find them in the DOM and replace each with a rendered SVG.
async function renderMermaid() {
  await nextTick()
  const root = container.value
  if (!root) return
  const blocks = root.querySelectorAll('code.language-mermaid')
  for (const code of blocks) {
    const pre = code.closest('pre') || code
    if (pre.dataset.mmd) continue
    pre.dataset.mmd = '1'
    try {
      const { svg } = await mermaid.render(`mmd-${Date.now()}-${seq++}`, code.textContent || '')
      const wrap = document.createElement('div')
      wrap.className = 'mermaid-rendered'
      wrap.innerHTML = svg
      pre.replaceWith(wrap)
    } catch {
      // Leave the raw code block visible if the diagram fails to parse.
    }
  }
}

watch(
  () => props.source,
  (val) => {
    html.value = marked.parse(val || '')
    renderMermaid()
  },
  { immediate: true },
)
onMounted(renderMermaid)
</script>

<template>
  <div ref="container" class="md-body" v-html="html" />
</template>

<style>
.mermaid-rendered {
  display: flex;
  justify-content: center;
  margin: 12px 0;
  overflow-x: auto;
}
.mermaid-rendered svg {
  max-width: 100%;
  height: auto;
}
</style>

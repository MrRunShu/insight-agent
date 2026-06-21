<script setup>
import { ref, watch, nextTick, onMounted } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import mermaid from 'mermaid'
import katex from 'katex'
import 'katex/dist/katex.min.css'

const props = defineProps({
  source: { type: String, default: '' },
})

// ── KaTeX extensions for marked ────────────────────────────────────────────
const blockMath = {
  name: 'blockMath',
  level: 'block',
  start(src) { return src.indexOf('$$') },
  tokenizer(src) {
    const m = /^\$\$([\s\S]+?)\$\$/.exec(src)
    if (m) return { type: 'blockMath', raw: m[0], text: m[1].trim() }
  },
  renderer(token) {
    try {
      return `<div class="math-block">${katex.renderToString(token.text, { displayMode: true, throwOnError: false })}</div>`
    } catch {
      return `<pre class="math-raw">$$${token.text}$$</pre>`
    }
  },
}

const inlineMath = {
  name: 'inlineMath',
  level: 'inline',
  start(src) { return src.indexOf('$') },
  tokenizer(src) {
    const m = /^\$([^$\n]+?)\$/.exec(src)
    if (m) return { type: 'inlineMath', raw: m[0], text: m[1].trim() }
  },
  renderer(token) {
    try {
      return katex.renderToString(token.text, { displayMode: false, throwOnError: false })
    } catch {
      return `<code>$${token.text}$</code>`
    }
  },
}

marked.use({
  extensions: [blockMath, inlineMath],
  breaks: true,
  gfm: true,
})

marked.setOptions({
  highlight(code, lang) {
    if (lang && lang !== 'mermaid' && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value
    }
    return code
  },
})

mermaid.initialize({ startOnLoad: false, theme: 'neutral', suppressErrorRendering: true })

const container = ref(null)
const html = ref('')
let seq = 0

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
      // Unescape \" → " and \n → newline left by JSON serialization of LLM output.
      const mmdSrc = (code.textContent || '')
        .replace(/\\"/g, '"')
        .replace(/\\n/g, '\n')
      const id = `mmd-${Date.now()}-${seq++}`
      const { svg } = await mermaid.render(id, mmdSrc)
      const wrap = document.createElement('div')
      wrap.className = 'mermaid-rendered'
      wrap.innerHTML = svg
      pre.replaceWith(wrap)
    } catch {
      // Remove any error element mermaid appended to the body.
      document.body.querySelectorAll('[id^="mmd-"]').forEach(el => el.remove())
      // Leave the raw code block visible.
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
.mermaid-rendered svg { max-width: 100%; height: auto; }
.math-block { overflow-x: auto; margin: 10px 0; text-align: center; }
.math-raw { color: var(--text-soft); font-size: 12px; }
.md-body table { border-collapse: collapse; width: 100%; margin: 8px 0; font-size: 12.5px; }
.md-body th, .md-body td { border: 1px solid var(--border); padding: 5px 10px; text-align: left; }
.md-body th { background: var(--raised); font-weight: 600; }
.md-body code:not([class]) { background: var(--raised); padding: 1px 5px; border-radius: 4px; font-size: 12px; }
.md-body pre { background: var(--raised); border: 1px solid var(--border); border-radius: 8px; padding: 10px 14px; overflow-x: auto; margin: 8px 0; }
.md-body blockquote { border-left: 3px solid var(--accent); margin: 8px 0; padding-left: 12px; color: var(--text-soft); }
</style>

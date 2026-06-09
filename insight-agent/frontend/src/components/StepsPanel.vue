<script setup>
import { ref, watch } from 'vue'
import MarkdownView from './MarkdownView.vue'

const props = defineProps({
  steps: { type: Array, default: () => [] },
  running: { type: Boolean, default: false },
})

// Controlled expanded names — auto-expand every new step as it arrives.
// Using v-model:expanded-names (reactive) instead of default-expanded-names
// (initial-only) so dynamically added steps are expanded immediately.
const expandedNames = ref([])
watch(
  () => props.steps,
  (steps) => { expandedNames.value = steps.map((s) => s.id) },
  { deep: true },
)
</script>

<template>
  <div class="steps-panel">
    <div class="steps-header">
      <span class="title">执行过程</span>
      <n-tag v-if="steps.length" size="small" :bordered="false" type="info">
        {{ steps.length }} 步
      </n-tag>
    </div>

    <div class="steps-scroll">
      <n-empty
        v-if="!steps.length && !running"
        description="提交任务后，Agent 的思考与工具调用会实时显示在这里"
        class="empty"
      />

      <n-collapse v-else v-model:expanded-names="expandedNames" arrow-placement="right">
        <n-collapse-item
          v-for="s in steps"
          :key="s.id"
          :name="s.id"
        >
          <template #header>
            <div class="step-head">
              <span class="step-badge">Step {{ s.step }}</span>
              <span class="step-label">{{ s.label }}</span>
            </div>
          </template>
          <MarkdownView :source="s.content" />
        </n-collapse-item>
      </n-collapse>

      <div v-if="running" class="thinking">
        <n-spin size="small" />
        <span>思考中…</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.steps-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--panel);
}
.steps-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
}
.steps-header .title {
  font-weight: 600;
  font-size: 15px;
}
.steps-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px 24px;
}
.empty {
  margin-top: 64px;
}
.step-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.step-badge {
  font-size: 12px;
  font-weight: 600;
  color: var(--accent);
  background: var(--accent-soft);
  padding: 2px 8px;
  border-radius: 4px;
}
.step-label {
  font-size: 13px;
  color: var(--text-soft);
}
.thinking {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 8px;
  color: var(--text-soft);
  font-size: 13px;
}
</style>

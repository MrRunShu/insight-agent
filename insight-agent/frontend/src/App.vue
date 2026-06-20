<script setup>
import { ref } from 'vue'
import WelcomeView from './components/WelcomeView.vue'
import AcademicGrowthView from './components/AcademicGrowthView.vue'

const view = ref('welcome')
</script>

<template>
  <WelcomeView v-if="view === 'welcome'" @open="view = 'academic'" />
  <AcademicGrowthView v-else @home="view = 'welcome'" />
</template>

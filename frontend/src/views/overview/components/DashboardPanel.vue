<script setup lang="ts">
defineProps<{ title: string }>()
</script>

<template>
  <section class="dashboard-panel">
    <header class="panel-header">
      <h2>{{ title }}</h2>
      <slot name="action" />
    </header>
    <slot />
  </section>
</template>

<style scoped>
.dashboard-panel { min-width: 0; padding: 20px; border: 1px solid var(--fs-color-border, #dbe3ee); border-radius: 8px; background: var(--fs-color-surface, #fff); }
.panel-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.panel-header h2 { margin: 0; color: var(--fs-color-text, #1f2937); font-size: 17px; }
</style>

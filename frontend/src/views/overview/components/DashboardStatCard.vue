<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router'

import MaterialIcon from '@/components/MaterialIcon.vue'

defineProps<{
  label: string
  value: string | number
  detail: string
  icon: string
  tone: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  to?: RouteLocationRaw
}>()
</script>

<template>
  <component
    :is="to ? 'RouterLink' : 'article'"
    class="dashboard-stat-card"
    :class="`tone-${tone}`"
    v-bind="to ? { to } : {}"
  >
    <div class="stat-heading">
      <span>{{ label }}</span>
      <MaterialIcon :name="icon" :size="22" />
    </div>
    <strong>{{ value }}</strong>
    <small>{{ detail }}</small>
  </component>
</template>

<style scoped>
.dashboard-stat-card { display: grid; min-width: 0; gap: 10px; padding: 16px; border: 1px solid var(--fs-color-border, #dbe3ee); border-radius: 8px; background: var(--fs-color-surface, #fff); color: inherit; text-decoration: none; transition: border-color .2s, box-shadow .2s; }
a.dashboard-stat-card:hover { border-color: #2563eb; box-shadow: 0 4px 14px rgb(37 99 235 / 12%); }
.stat-heading { display: flex; align-items: center; justify-content: space-between; gap: 8px; color: var(--fs-color-text-secondary, #64748b); }
.dashboard-stat-card strong { color: var(--fs-color-text, #1f2937); font-size: 28px; line-height: 1; }
.dashboard-stat-card small { color: var(--fs-color-text-secondary, #64748b); }
.tone-primary :deep(.material-icon) { color: #2563eb; }.tone-success :deep(.material-icon) { color: #16a34a; }
.tone-warning :deep(.material-icon) { color: #d97706; }.tone-danger :deep(.material-icon) { color: #dc2626; }
.tone-info :deep(.material-icon) { color: #0891b2; }
</style>

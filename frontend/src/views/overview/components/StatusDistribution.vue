<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router'

export type StatusSegment = {
  readonly key: string
  readonly label: string
  readonly count: number
  readonly color: string
  readonly to?: RouteLocationRaw
}

defineProps<{
  segments: readonly StatusSegment[]
  total: number
}>()
</script>

<template>
  <div class="status-distribution">
    <div class="status-bar" aria-label="状态分布">
      <component
        :is="segment.to ? 'RouterLink' : 'span'"
        v-for="segment in segments.filter((item) => item.count > 0)"
        :key="segment.key"
        :style="{
          width: `${total === 0 ? 0 : segment.count / total * 100}%`,
          background: segment.color,
        }"
        :title="`${segment.label} ${segment.count}`"
        v-bind="segment.to ? { to: segment.to } : {}"
      />
    </div>
    <div class="status-legend">
      <component
        :is="segment.to ? 'RouterLink' : 'span'"
        v-for="segment in segments"
        :key="segment.key"
        v-bind="segment.to ? { to: segment.to } : {}"
      >
        <i :style="{ background: segment.color }" />
        {{ segment.label }} {{ segment.count }}
      </component>
    </div>
  </div>
</template>

<style scoped>
.status-bar { display: flex; height: 16px; overflow: hidden; border-radius: 999px; background: #e2e8f0; }
.status-bar > * { min-width: 0; }
.status-legend { display: flex; flex-wrap: wrap; gap: 10px 18px; margin-top: 14px; color: var(--fs-color-text-secondary, #64748b); font-size: 13px; }
.status-legend > * { display: inline-flex; align-items: center; gap: 6px; color: inherit; text-decoration: none; }
.status-legend a:hover { color: #2563eb; }.status-legend i { width: 9px; height: 9px; border-radius: 50%; }
</style>

<script setup lang="ts">
defineProps<{ taskId: string; projectId?: string }>()
</script>

<template>
  <RouterLink
    class="entity-link"
    :to="{
      name: 'task-detail',
      params: { taskId },
      ...(projectId ? { query: { projectId } } : {}),
    }"
    @click.stop
  >
    <slot />
  </RouterLink>
</template>

<style scoped>
.entity-link { color: var(--fs-color-primary, #2563eb); text-decoration: none; }
.entity-link:hover { text-decoration: underline; }
</style>

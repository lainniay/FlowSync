<script setup lang="ts">
defineProps<{ userId: string }>()
</script>

<template>
  <RouterLink
    class="entity-link"
    :to="{ name: 'user-profile', params: { userId } }"
    @click.stop
  >
    <slot />
  </RouterLink>
</template>

<style scoped>
.entity-link { color: var(--fs-color-primary, #2563eb); text-decoration: none; }
.entity-link:hover { text-decoration: underline; }
</style>

<script setup lang="ts">
import { ElCard, ElSpace, ElTag } from 'element-plus'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/space/style/css'
import 'element-plus/es/components/tag/style/css'

const stack = ['Vue 3', 'TypeScript', 'Element Plus', 'Spring Boot']
</script>

<template>
  <main class="home">
    <el-card class="intro" shadow="never">
      <p class="eyebrow">小组任务协同管理系统</p>
      <h1>FlowSync</h1>
      <p class="description">前端工程已就绪，下一步实现认证与项目协作流程。</p>
      <el-space wrap>
        <el-tag v-for="item in stack" :key="item" effect="plain">{{ item }}</el-tag>
      </el-space>
    </el-card>
  </main>
</template>

<style scoped>
.home {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 24px;
}

.intro {
  width: min(640px, 100%);
  border: 0;
}

.eyebrow {
  margin: 0 0 8px;
  color: #409eff;
  font-weight: 600;
}

h1 {
  margin: 0;
  font-size: clamp(40px, 8vw, 64px);
}

.description {
  margin: 16px 0 24px;
  color: #606266;
  line-height: 1.6;
}
</style>

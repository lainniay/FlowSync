<script setup lang="ts">
import {
  ElButton,
  ElResult,
} from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/result/style/css'
</script>

<template>
  <el-result
    icon="info"
    sub-title="请检查地址，或返回工作台继续操作"
    title="404 页面不存在"
  >
    <template #extra>
      <RouterLink to="/overview">
        <el-button type="primary">
          返回工作台
        </el-button>
      </RouterLink>
    </template>
  </el-result>
</template>

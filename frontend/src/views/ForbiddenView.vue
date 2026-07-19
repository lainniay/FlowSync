//403界面：页面存在、用户已经登录、当前帐号没有权限


<script setup lang="ts">
import { useRouter } from 'vue-router'
import {
  ElButton,
  ElResult,
} from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/result/style/css'

const router = useRouter()
</script>

<template>
  <el-result
    icon="warning"
    sub-title="当前账号没有访问该页面的权限。"
    title="403 无权限"
  >
    <template #extra>
      <el-button
        type="primary"
        @click="router.push('/overview')"
      >
        返回工作台
      </el-button>

      <el-button @click="router.back()">
        返回上一页
      </el-button>
    </template>
  </el-result>
</template>
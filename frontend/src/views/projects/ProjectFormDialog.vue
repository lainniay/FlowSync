<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  ElButton,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
} from 'element-plus'
import type {
  FormInstance,
  FormRules,
} from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/date-picker/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/select/style/css'

import type {
  Priority,
  ProjectStatus,
  User,
} from '@/shared/api/types'

import type {
  CreateProjectRequest,
  Project,
  UpdateProjectRequest,
} from './types'

type DialogMode = 'create' | 'edit'

type ProjectFormModel = {
  name: string
  description: string
  status: ProjectStatus
  priority: Priority
  startDate: string
  endDate: string
  ownerId: string
}

const props = defineProps<{
  visible: boolean
  mode: DialogMode
  project?: Project | null
  requireOwnerId?: boolean
  ownerOptions?: readonly User[]
  submitting?: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  submitCreate: [payload: CreateProjectRequest]
  submitEdit: [payload: UpdateProjectRequest]
}>()

const formRef = ref<FormInstance>()

const form = reactive<ProjectFormModel>({
  name: '',
  description: '',
  status: 'NOT_STARTED',
  priority: 'MEDIUM',
  startDate: '',
  endDate: '',
  ownerId: '',
})

const dialogTitle = computed(() => (
  props.mode === 'create' ? '创建项目' : '编辑项目'
))

const projectRules: FormRules<ProjectFormModel> = {
  name: [
    { required: true, message: '请输入项目名称', trigger: 'blur' },
    { min: 1, max: 100, message: '项目名称长度为 1 到 100 个字符', trigger: 'blur' },
  ],
  description: [
    { max: 2000, message: '项目描述不能超过 2000 个字符', trigger: 'blur' },
  ],
  status: [
    { required: true, message: '请选择项目状态', trigger: 'change' },
  ],
  priority: [
    { required: true, message: '请选择优先级', trigger: 'change' },
  ],
  ownerId: [
    {
      validator: (_rule, value, callback) => {
        if (!props.requireOwnerId) {
          callback()
          return
        }

        if (!value.trim()) {
          callback(new Error('请选择项目负责人'))
          return
        }

        callback()
      },
      trigger: 'change',
    },
  ],
}

function normalizeNullable(value: string): string | null {
  const trimmed = value.trim()
  return trimmed === '' ? null : trimmed
}

function resetForm(): void {
  Object.assign(form, {
    name: '',
    description: '',
    status: 'NOT_STARTED',
    priority: 'MEDIUM',
    startDate: '',
    endDate: '',
    ownerId: '',
  })
  formRef.value?.clearValidate()
}

function syncEditForm(): void {
  if (!props.project) return

  form.name = props.project.name
  form.description = props.project.description ?? ''
  form.status = props.project.status
  form.priority = props.project.priority
  form.startDate = props.project.startDate ?? ''
  form.endDate = props.project.endDate ?? ''
}

function closeDialog(): void {
  emit('update:visible', false)
}

function buildPayload(): UpdateProjectRequest {
  return {
    name: form.name.trim(),
    description: normalizeNullable(form.description),
    status: form.status,
    priority: form.priority,
    startDate: normalizeNullable(form.startDate),
    endDate: normalizeNullable(form.endDate),
  }
}

async function handleSubmit(): Promise<void> {
  const formInstance = formRef.value
  if (!formInstance) return

  const valid = await formInstance.validate().catch(() => false)
  if (!valid) return

  if (props.mode === 'create') {
    emit('submitCreate', {
      ...buildPayload(),
      ownerId: props.requireOwnerId
        ? form.ownerId.trim()
        : null,
    })
    return
  }

  emit('submitEdit', buildPayload())
}

watch(
  () => [props.visible, props.mode, props.project] as const,
  ([visible, mode]) => {
    if (!visible) {
      resetForm()
      return
    }

    if (mode === 'edit') {
      syncEditForm()
    }
  },
)
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="dialogTitle"
    width="560px"
    @close="closeDialog"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form
      ref="formRef"
      label-position="top"
      :model="form"
      :rules="projectRules"
      @submit.prevent="handleSubmit"
    >
      <el-form-item label="项目名称" prop="name">
        <el-input
          v-model="form.name"
          maxlength="100"
          placeholder="请输入项目名称"
        />
      </el-form-item>

      <el-form-item label="项目描述" prop="description">
        <el-input
          v-model="form.description"
          maxlength="2000"
          placeholder="可选"
          :rows="3"
          type="textarea"
        />
      </el-form-item>

      <el-form-item label="项目状态" prop="status">
        <el-select v-model="form.status">
          <el-option label="未开始" value="NOT_STARTED" />
          <el-option label="进行中" value="IN_PROGRESS" />
          <el-option label="已完成" value="COMPLETED" />
        </el-select>
      </el-form-item>

      <el-form-item label="优先级" prop="priority">
        <el-select v-model="form.priority">
          <el-option label="低" value="LOW" />
          <el-option label="中" value="MEDIUM" />
          <el-option label="高" value="HIGH" />
        </el-select>
      </el-form-item>

      <el-form-item label="开始日期" prop="startDate">
        <el-date-picker
          v-model="form.startDate"
          class="date-picker"
          placeholder="可选"
          type="date"
          value-format="YYYY-MM-DD"
        />
      </el-form-item>

      <el-form-item label="结束日期" prop="endDate">
        <el-date-picker
          v-model="form.endDate"
          class="date-picker"
          placeholder="可选"
          type="date"
          value-format="YYYY-MM-DD"
        />
      </el-form-item>

      <el-form-item
        v-if="mode === 'create' && requireOwnerId"
        label="项目负责人"
        prop="ownerId"
      >
        <el-select
          v-model="form.ownerId"
          filterable
          placeholder="选择 USER 作为 owner"
        >
          <el-option
            v-for="user in ownerOptions ?? []"
            :key="user.id"
            :label="`${user.displayName} (@${user.username})`"
            :value="user.id"
          />
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="closeDialog">
        取消
      </el-button>
      <el-button
        :loading="submitting"
        type="primary"
        @click="handleSubmit"
      >
        保存
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.el-select,
.date-picker {
  width: 100%;
}
</style>

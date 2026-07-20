<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElSwitch,
} from 'element-plus'
import type {
  FormInstance,
  FormRules,
} from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/switch/style/css'

import type { SystemRole, User } from '@/shared/api/types'

import type {
  CreateUserRequest,
  UpdateUserRequest,
} from './types'

type DialogMode = 'create' | 'edit'

type CreateFormModel = {
  username: string
  initialPassword: string
  displayName: string
  systemRole: SystemRole
  phone: string
  email: string
}

type EditFormModel = {
  displayName: string
  phone: string
  email: string
  systemRole: SystemRole
  active: boolean
}

const props = defineProps<{
  visible: boolean
  mode: DialogMode
  user?: User | null
  submitting?: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  submitCreate: [payload: CreateUserRequest]
  submitEdit: [payload: UpdateUserRequest]
}>()

const formRef = ref<FormInstance>()

const createForm = reactive<CreateFormModel>({
  username: '',
  initialPassword: '',
  displayName: '',
  systemRole: 'USER',
  phone: '',
  email: '',
})

const editForm = reactive<EditFormModel>({
  displayName: '',
  phone: '',
  email: '',
  systemRole: 'USER',
  active: true,
})

const dialogTitle = computed(() => (
  props.mode === 'create' ? '创建用户' : '编辑用户'
))

function validatePassword(
  _rule: unknown,
  value: string,
  callback: (error?: Error) => void,
): void {
  if (value.length < 12 || value.length > 64) {
    callback(new Error('密码长度为 12 到 64 个字符'))
    return
  }

  if (new TextEncoder().encode(value).length > 72) {
    callback(new Error('密码 UTF-8 编码不能超过 72 字节'))
    return
  }

  callback()
}

function validateUsername(
  _rule: unknown,
  value: string,
  callback: (error?: Error) => void,
): void {
  if (!/^[A-Za-z0-9._-]{3,50}$/.test(value)) {
    callback(new Error('用户名只能包含字母、数字、点、下划线和连字符，长度 3 到 50'))
    return
  }

  callback()
}

const createRules: FormRules<CreateFormModel> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { validator: validateUsername, trigger: 'blur' },
  ],
  initialPassword: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { validator: validatePassword, trigger: 'blur' },
  ],
  displayName: [
    { required: true, message: '请输入显示名称', trigger: 'blur' },
    { min: 1, max: 50, message: '显示名称长度为 1 到 50 个字符', trigger: 'blur' },
  ],
  systemRole: [
    { required: true, message: '请选择系统角色', trigger: 'change' },
  ],
  phone: [
    { max: 20, message: '手机号不能超过 20 个字符', trigger: 'blur' },
  ],
  email: [
    { max: 100, message: '邮箱不能超过 100 个字符', trigger: 'blur' },
    { type: 'email', message: '请输入合法邮箱地址', trigger: 'blur' },
  ],
}

const editRules: FormRules<EditFormModel> = {
  displayName: createRules.displayName,
  systemRole: createRules.systemRole,
  phone: createRules.phone,
  email: createRules.email,
}

function normalizeNullable(value: string): string | null {
  const trimmed = value.trim()
  return trimmed === '' ? null : trimmed
}

function resetForms(): void {
  Object.assign(createForm, {
    username: '',
    initialPassword: '',
    displayName: '',
    systemRole: 'USER',
    phone: '',
    email: '',
  })

  Object.assign(editForm, {
    displayName: '',
    phone: '',
    email: '',
    systemRole: 'USER',
    active: true,
  })

  formRef.value?.clearValidate()
}

function syncEditForm(): void {
  if (!props.user) return

  editForm.displayName = props.user.displayName
  editForm.phone = props.user.phone ?? ''
  editForm.email = props.user.email ?? ''
  editForm.systemRole = props.user.systemRole
  editForm.active = props.user.active
}

function closeDialog(): void {
  emit('update:visible', false)
}

async function handleSubmit(): Promise<void> {
  const form = formRef.value
  if (!form) return

  const valid = await form.validate().catch(() => false)
  if (!valid) return

  if (props.mode === 'create') {
    emit('submitCreate', {
      username: createForm.username.trim(),
      initialPassword: createForm.initialPassword,
      displayName: createForm.displayName.trim(),
      systemRole: createForm.systemRole,
      phone: normalizeNullable(createForm.phone),
      email: normalizeNullable(createForm.email),
    })
    return
  }

  emit('submitEdit', {
    displayName: editForm.displayName.trim(),
    phone: normalizeNullable(editForm.phone),
    email: normalizeNullable(editForm.email),
    systemRole: editForm.systemRole,
    active: editForm.active,
  })
}

watch(
  () => [props.visible, props.mode, props.user] as const,
  ([visible, mode]) => {
    if (!visible) {
      resetForms()
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
    width="520px"
    @close="closeDialog"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form
      v-if="mode === 'create'"
      ref="formRef"
      label-position="top"
      :model="createForm"
      :rules="createRules"
      @submit.prevent="handleSubmit"
    >
      <el-form-item label="用户名" prop="username">
        <el-input
          v-model="createForm.username"
          maxlength="50"
          placeholder="3 到 50 个字符"
        />
      </el-form-item>

      <el-form-item label="初始密码" prop="initialPassword">
        <el-input
          v-model="createForm.initialPassword"
          class="password-input"
          placeholder="12 到 64 个字符"
          show-password
          type="password"
        />
      </el-form-item>

      <el-form-item label="显示名称" prop="displayName">
        <el-input
          v-model="createForm.displayName"
          maxlength="50"
          placeholder="请输入显示名称"
        />
      </el-form-item>

      <el-form-item label="系统角色" prop="systemRole">
        <el-select v-model="createForm.systemRole">
          <el-option label="ADMIN" value="ADMIN" />
          <el-option label="USER" value="USER" />
        </el-select>
      </el-form-item>

      <el-form-item label="手机号" prop="phone">
        <el-input
          v-model="createForm.phone"
          maxlength="20"
          placeholder="可选"
        />
      </el-form-item>

      <el-form-item label="邮箱" prop="email">
        <el-input
          v-model="createForm.email"
          maxlength="100"
          placeholder="可选"
        />
      </el-form-item>
    </el-form>

    <el-form
      v-else
      ref="formRef"
      label-position="top"
      :model="editForm"
      :rules="editRules"
      @submit.prevent="handleSubmit"
    >
      <el-form-item label="用户名">
        <el-input
          disabled
          :model-value="user?.username ?? ''"
        />
      </el-form-item>

      <el-form-item label="显示名称" prop="displayName">
        <el-input
          v-model="editForm.displayName"
          maxlength="50"
          placeholder="请输入显示名称"
        />
      </el-form-item>

      <el-form-item label="系统角色" prop="systemRole">
        <el-select v-model="editForm.systemRole">
          <el-option label="ADMIN" value="ADMIN" />
          <el-option label="USER" value="USER" />
        </el-select>
      </el-form-item>

      <el-form-item label="启用状态" prop="active">
        <el-switch v-model="editForm.active" />
      </el-form-item>

      <el-form-item label="手机号" prop="phone">
        <el-input
          v-model="editForm.phone"
          maxlength="20"
          placeholder="可选，留空表示清空"
        />
      </el-form-item>

      <el-form-item label="邮箱" prop="email">
        <el-input
          v-model="editForm.email"
          maxlength="100"
          placeholder="可选，留空表示清空"
        />
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
.el-select {
  width: 100%;
}

.password-input {
  width: 50%;
}
</style>

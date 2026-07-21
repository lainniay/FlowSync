<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElDatePicker,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElResult,
  ElSelect,
  ElSkeleton,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/date-picker/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/input-number/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/result/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/skeleton/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'

import { getApiErrorMessage } from '@/shared/api/errors'
import { useAuthStore } from '@/stores/auth'
import { getProject } from '@/views/projects/api'

import {
  generateTaskPlan,
  importTaskPlan,
} from './api'
import type { AiTaskPlanItem } from './types'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const projectId = computed(() => route.params.projectId as string)

// --- Permission ---
const isAdmin = computed(() => authStore.currentUser?.systemRole === 'ADMIN')
const isProjectOwner = ref(false)
const projectLoading = ref(false)

// --- Step state ---
type Step =
  | 'loading'
  | 'input'
  | 'generating'
  | 'editing'
  | 'importing'
  | 'imported'
  | 'forbidden'
  | 'error'

const step = ref<Step>('loading')
const errorMessage = ref('')

// --- Load project & verify owner ---
async function verifyProjectOwner(): Promise<void> {
  projectLoading.value = true
  errorMessage.value = ''

  if (isAdmin.value) {
    step.value = 'forbidden'
    errorMessage.value = '管理员不参与项目内容操作，不能使用 AI 任务计划。'
    projectLoading.value = false
    return
  }

  try {
    const project = await getProject(projectId.value)
    isProjectOwner.value =
      project.owner.id === authStore.currentUser?.id

    if (!isProjectOwner.value) {
      step.value = 'forbidden'
      errorMessage.value = '只有项目 owner 才能使用 AI 任务计划。'
    } else {
      step.value = 'input'
    }
  } catch {
    step.value = 'error'
    errorMessage.value = '加载项目信息失败，请稍后重试。'
  } finally {
    projectLoading.value = false
  }
}

onMounted(() => {
  void verifyProjectOwner()
})

// --- Input form ---
const goal = ref('')
const description = ref('')
const maxItems = ref(10)
const targetEndDate = ref('')

// --- Generated plan ---
const planOverview = ref('')
const generatedAt = ref('')

// --- Editable items ---
type EditableItem = AiTaskPlanItem & { _localId: string }

const draftItems = ref<EditableItem[]>([])
let localIdCounter = 0
const importing = ref(false)

// --- Generate ---
async function handleGenerate(): Promise<void> {
  if (!goal.value.trim()) {
    ElMessage.warning('请输入项目目标')
    return
  }

  step.value = 'generating'
  errorMessage.value = ''

  try {
    const result = await generateTaskPlan(projectId.value, {
      goal: goal.value.trim(),
      description: description.value.trim() || undefined,
      constraints: {
        maxItems: maxItems.value,
        targetEndDate: targetEndDate.value || null,
      },
    })

    planOverview.value = result.overview
    generatedAt.value = result.generatedAt

    draftItems.value = result.items.map((item, index) => ({
      ...item,
      _localId: `local-${index}`,
    }))

    localIdCounter = result.items.length + 1
    step.value = 'editing'
  } catch (error) {
    errorMessage.value = getApiErrorMessage(
      error,
      '生成 AI 计划失败，请稍后重试',
    )
    step.value = 'error'
  }
}

// --- Edit items ---
function handleAddItem(): void {
  draftItems.value.push({
    draftId: `draft-new-${localIdCounter++}`,
    parentDraftId: null,
    title: '',
    description: null,
    priority: 'MEDIUM',
    dueDate: null,
    assigneeId: null,
    _localId: `local-${localIdCounter}`,
  })
}

function handleRemoveItem(draftId: string): void {
  if (draftItems.value.length <= 1) {
    ElMessage.warning('至少保留一个条目')
    return
  }
  // Clear parentDraftId for children referencing the removed item
  draftItems.value = draftItems.value
    .map((item) =>
      item.parentDraftId === draftId
        ? { ...item, parentDraftId: null }
        : item,
    )
    .filter((item) => item.draftId !== draftId)
}

function handleMoveUp(index: number): void {
  if (index <= 0) return
  const items = draftItems.value
  ;[items[index - 1], items[index]] = [items[index]!, items[index - 1]!]
}

function handleMoveDown(index: number): void {
  if (index >= draftItems.value.length - 1) return
  const items = draftItems.value
  ;[items[index], items[index + 1]] = [items[index + 1]!, items[index]!]
}

function handleRegenerate(): void {
  draftItems.value = []
  planOverview.value = ''
  generatedAt.value = ''
  step.value = 'input'
}

// --- Validation ---
function validateItems(): string | null {
  for (let i = 0; i < draftItems.value.length; i++) {
    const item = draftItems.value[i]!

    if (!item.title.trim()) {
      return `第 ${i + 1} 个条目标题不能为空`
    }
    if (item.title.length > 100) {
      return `第 ${i + 1} 个条目标题超过 100 个字符`
    }
    if (item.description && item.description.length > 5000) {
      return `第 ${i + 1} 个条目描述超过 5000 个字符`
    }
    if (item.dueDate && !/^\d{4}-\d{2}-\d{2}$/.test(item.dueDate)) {
      return `第 ${i + 1} 个条目截止日期格式无效`
    }
  }

  // Check draft cycles
  for (const item of draftItems.value) {
    if (!item.parentDraftId) continue
    const exists = draftItems.value.some((i) => i.draftId === item.parentDraftId)
    if (!exists) {
      return `条目 "${item.title || item.draftId}" 引用了不存在的父任务 ${item.parentDraftId}`
    }
    // Simple cycle check
    let current = item.parentDraftId
    const visited = new Set<string>()
    while (current) {
      if (visited.has(current)) {
        return `导入条目中存在循环引用`
      }
      visited.add(current)
      const parent = draftItems.value.find((i) => i.draftId === current)
      current = parent?.parentDraftId ?? ''
    }
  }

  return null
}

// --- Import ---
async function handleImport(): Promise<void> {
  const validationError = validateItems()
  if (validationError) {
    ElMessage.warning(validationError)
    return
  }

  importing.value = true

  try {
    const result = await importTaskPlan(projectId.value, {
      items: draftItems.value.map((item) => ({
        draftId: item.draftId,
        parentDraftId: item.parentDraftId ?? null,
        title: item.title.trim(),
        description: item.description || null,
        priority: item.priority,
        dueDate: item.dueDate || null,
        assigneeId: item.assigneeId || null,
      })),
    })

    draftItems.value = []
    step.value = 'imported'

    ElMessage.success(`成功导入 ${result.importedCount} 个任务`)
  } catch (error) {
    errorMessage.value = getApiErrorMessage(
      error,
      '导入 AI 任务计划失败，请稍后重试',
    )
    step.value = 'editing'
    ElMessage.error(
      getApiErrorMessage(error, '导入失败，请修改后重试'),
    )
  } finally {
    importing.value = false
  }
}

function handleBackToTasks(): void {
  router.push({ name: 'tasks', query: { projectId: projectId.value } })
}
</script>

<template>
  <section class="ai-plan-page">
    <header class="page-header">
      <div>
        <h1>AI 任务计划</h1>
        <p>
          为项目 {{ projectId }} 生成临时任务计划，人工审阅编辑后导入为正式任务。
        </p>
      </div>
    </header>

    <!-- Step: Loading -->
    <section
      v-if="step === 'loading'"
      class="content-panel"
    >
      <el-skeleton animated :rows="6" />
    </section>

    <!-- Step: Forbidden -->
    <section
      v-if="step === 'forbidden'"
      class="content-panel"
    >
      <div class="feedback-state">
        <el-alert
          :closable="false"
          :title="errorMessage"
          type="warning"
          show-icon
        />
        <router-link
          :to="{ name: 'tasks' }"
        >
          返回任务列表
        </router-link>
      </div>
    </section>

    <!-- Step: Input -->
    <section
      v-if="step === 'input'"
      class="content-panel"
    >
      <div class="plan-form">
        <label class="plan-label">
          项目目标
          <span class="required">*</span>
        </label>
        <el-input
          v-model="goal"
          maxlength="500"
          placeholder="描述项目目标，例如：完成 FlowSync 第一阶段开发"
          type="textarea"
          :rows="3"
        />

        <label class="plan-label">补充说明</label>
        <el-input
          v-model="description"
          maxlength="2000"
          placeholder="可选，补充项目背景和特殊要求"
          type="textarea"
          :rows="2"
        />

        <label class="plan-label">最大条目数</label>
        <el-input-number
          v-model="maxItems"
          :max="20"
          :min="1"
        />

        <label class="plan-label">目标结束日期</label>
        <el-date-picker
          v-model="targetEndDate"
          placeholder="可选"
          type="date"
          value-format="YYYY-MM-DD"
        />

        <div class="plan-actions">
          <el-button
            type="primary"
            @click="handleGenerate"
          >
            生成初步计划
          </el-button>
        </div>
      </div>
    </section>

    <!-- Step: Generating -->
    <section
      v-if="step === 'generating'"
      class="content-panel"
    >
      <el-skeleton animated :rows="6" />
    </section>

    <!-- Step: Editing -->
    <section
      v-if="step === 'editing'"
      class="content-panel"
    >
      <el-alert
        v-if="planOverview"
        :closable="false"
        :title="planOverview"
        type="info"
        show-icon
      />

      <el-table
        :data="draftItems"
        row-key="draftId"
        class="plan-table"
      >
        <el-table-column label="标题" min-width="180">
          <template #default="{ row }">
            <el-input
              v-model="row.title"
              maxlength="100"
              placeholder="任务标题"
            />
          </template>
        </el-table-column>

        <el-table-column label="父任务" width="160">
          <template #default="{ row }">
            <el-select
              v-model="row.parentDraftId"
              class="enum-select"
              clearable
              placeholder="无"
            >
              <el-option
                v-for="item in draftItems"
                :key="item.draftId"
                :label="item.title || item.draftId"
                :value="item.draftId"
              />
            </el-select>
          </template>
        </el-table-column>

        <el-table-column label="负责人 ID" width="110">
          <template #default="{ row }">
            <el-input
              v-model="row.assigneeId"
              placeholder="可选"
            />
          </template>
        </el-table-column>

        <el-table-column label="优先级" width="100">
          <template #default="{ row }">
            <el-select
              v-model="row.priority"
              class="enum-select"
            >
              <el-option label="低" value="LOW" />
              <el-option label="中" value="MEDIUM" />
              <el-option label="高" value="HIGH" />
            </el-select>
          </template>
        </el-table-column>

        <el-table-column label="截止日期" width="160">
          <template #default="{ row }">
            <el-date-picker
              v-model="row.dueDate"
              placeholder="可选"
              type="date"
              value-format="YYYY-MM-DD"
            />
          </template>
        </el-table-column>

        <el-table-column label="描述" min-width="160">
          <template #default="{ row }">
            <el-input
              v-model="row.description"
              maxlength="5000"
              placeholder="可选"
            />
          </template>
        </el-table-column>

        <el-table-column label="排序" width="110">
          <template #default="{ $index }">
            <el-button
              size="small"
              text
              :disabled="$index === 0"
              @click="handleMoveUp($index)"
            >
              上移
            </el-button>
            <el-button
              size="small"
              text
              :disabled="$index === draftItems.length - 1"
              @click="handleMoveDown($index)"
            >
              下移
            </el-button>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button
              type="danger"
              text
              @click="handleRemoveItem(row.draftId)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="plan-footer">
        <el-button @click="handleAddItem">
          新增临时条目
        </el-button>

        <el-button @click="handleRegenerate">
          重新生成
        </el-button>

        <el-button
          type="primary"
          :loading="importing"
          @click="handleImport"
        >
          确认并导入
        </el-button>
      </div>
    </section>

    <!-- Step: Imported -->
    <section
      v-if="step === 'imported'"
      class="content-panel"
    >
      <el-result
        title="导入成功"
        type="success"
      >
        <template #extra>
          <el-button
            type="primary"
            @click="handleBackToTasks"
          >
            返回任务列表
          </el-button>
        </template>
      </el-result>
    </section>

    <!-- Step: Error -->
    <section
      v-if="step === 'error'"
      class="content-panel"
    >
      <div class="feedback-state">
        <el-alert
          :closable="false"
          :title="errorMessage"
          type="error"
          show-icon
        />
        <div class="error-actions">
          <el-button
            type="primary"
            @click="verifyProjectOwner"
          >
            重新开始
          </el-button>
          <router-link
            :to="{ name: 'tasks' }"
          >
            返回任务列表
          </router-link>
        </div>
      </div>
    </section>
  </section>
</template>

<style scoped>
.ai-plan-page {
  display: grid;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-header h1 {
  margin: 0;
  color: var(--fs-color-text, #1f2937);
  font-size: 24px;
}

.page-header p {
  margin: 4px 0 0;
  color: var(--fs-color-text-secondary, #64748b);
}

.content-panel {
  border: 1px solid var(--fs-color-border, #dbe3ee);
  border-radius: 8px;
  background: var(--fs-color-surface, #fff);
  padding: 20px;
}

/* Input form */
.plan-form {
  display: grid;
  gap: 12px;
  max-width: 600px;
}

.plan-label {
  font-weight: 500;
  color: var(--fs-color-text, #1f2937);
}

.required {
  color: var(--fs-color-danger, #dc2626);
}

.plan-actions {
  margin-top: 8px;
}

/* Editing table */
.plan-table {
  margin: 16px 0;
}

.plan-footer {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.enum-select {
  width: 140px;
}

/* Feedback */
.feedback-state {
  display: grid;
  min-height: 240px;
  align-content: center;
  gap: 16px;
  justify-items: center;
}

.error-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

@media (max-width: 720px) {
  .content-panel {
    padding: 16px;
    overflow-x: auto;
  }

  .plan-form {
    max-width: 100%;
  }
}
</style>

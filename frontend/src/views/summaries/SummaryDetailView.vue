<script setup lang="ts">
import {
  computed,
  onMounted,
  reactive,
  ref,
} from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/tag/style/css'

import { getApiErrorMessage, hasApiStatus } from '@/shared/api/errors'
import MaterialIcon from '@/components/MaterialIcon.vue'
import ProjectLink from '@/components/ProjectLink.vue'
import TaskLink from '@/components/TaskLink.vue'
import UserLink from '@/components/UserLink.vue'
import { formatDateTime } from '@/shared/format'
import type { SummaryType } from '@/shared/api/types'
import { useAuthStore } from '@/stores/auth'
import { getProject } from '@/views/projects/api'
import type { Project } from '@/views/projects/types'
import { getTask } from '@/views/tasks/api'
import type { Task } from '@/views/tasks/types'

import {
  deleteSummary,
  getSummary,
  updateSummary,
} from './api'
import type { Summary, UpdateSummaryBody } from './types'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const summaryId = computed(() => route.params.summaryId as string)

function getSummaryListLocation() {
  const projectId = summary.value?.projectId ?? (typeof route.query?.projectId === 'string'
    ? route.query.projectId
    : '')

  if (!projectId) return { name: 'projects' }

  return {
    name: 'project-detail',
    params: { projectId },
    query: { tab: 'summaries' },
  }
}

type TagType =
  | 'primary'
  | 'success'
  | 'warning'
  | 'danger'
  | 'info'

const typeLabels: Record<SummaryType, string> = {
  STAGE: '阶段总结',
  FINAL: '最终总结',
}

const typeTagTypes: Record<SummaryType, TagType> = {
  STAGE: 'warning',
  FINAL: 'success',
}

// --- Summary data ---
const summary = ref<Summary | null>(null)
const project = ref<Project | null>(null)
const relatedTask = ref<Task | null>(null)
const loading = ref(false)
const loaded = ref(false)
const errorMessage = ref('')
const notFound = ref(false)

// --- Project owner ---
const projectOwner = ref<{ id: string; displayName: string } | null>(null)
const projectArchived = ref<boolean | null>(null)

// --- Permissions ---
const isAdmin = computed(() => authStore.currentUser?.systemRole === 'ADMIN')
const isCreator = computed(() =>
  summary.value?.createdBy.id === authStore.currentUser?.id,
)
const isProjectOwner = computed(() =>
  projectOwner.value?.id === authStore.currentUser?.id,
)
const canModify = computed(
  () => !isAdmin.value
    && projectArchived.value === false
    && (isCreator.value || isProjectOwner.value),
)

async function fetchSummary(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  notFound.value = false
  projectOwner.value = null
  projectArchived.value = null

  try {
    summary.value = await getSummary(summaryId.value)

    // Fetch project owner for permission checks
    try {
      project.value = await getProject(summary.value.projectId)
      projectOwner.value = project.value.owner
      projectArchived.value = Boolean(project.value.archivedAt)
    } catch {
      project.value = null
      projectOwner.value = null
      projectArchived.value = null
    }
    if (summary.value.taskId) {
      try {
        relatedTask.value = await getTask(summary.value.taskId)
      } catch {
        relatedTask.value = null
      }
    } else {
      relatedTask.value = null
    }
  } catch (error) {
    if (hasApiStatus(error, 404)) {
      notFound.value = true
    }
    errorMessage.value = getApiErrorMessage(
      error,
      '总结加载失败，请稍后重试',
    )
  } finally {
    loading.value = false
    loaded.value = true
  }
}

// --- Edit dialog ---
const editDialogVisible = ref(false)
const editSubmitting = ref(false)

const editForm = reactive({
  type: 'STAGE' as SummaryType,
  content: '',
})

function openEditDialog(): void {
  if (!summary.value) return

  editForm.type = summary.value.type
  editForm.content = summary.value.content

  editDialogVisible.value = true
}

async function handleEdit(): Promise<void> {
  const content = editForm.content.trim()
  if (!content) {
    ElMessage.warning('请输入总结内容')
    return
  }
  editSubmitting.value = true

  try {
    const body: UpdateSummaryBody = {
      type: editForm.type,
      content,
    }

    summary.value = await updateSummary(summaryId.value, body)
    ElMessage.success('总结更新成功')
    editDialogVisible.value = false
  } catch (error) {
    ElMessage.error(
      getApiErrorMessage(error, '更新总结失败，请稍后重试'),
    )
  } finally {
    editSubmitting.value = false
  }
}

// --- Delete ---
const deleteSubmitting = ref(false)

async function handleDelete(): Promise<void> {
  if (deleteSubmitting.value) return

  try {
    await ElMessageBox.confirm(
      '确定要删除这条总结吗？该操作不可撤销',
      '删除总结',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'error',
      },
    )
  } catch {
    return
  }

  deleteSubmitting.value = true
  try {
    await deleteSummary(summaryId.value)
    ElMessage.success('总结已删除')
    await router.push(getSummaryListLocation())
  } catch (error) {
    ElMessage.error(
      getApiErrorMessage(error, '删除总结失败，请稍后重试'),
    )
  } finally {
    deleteSubmitting.value = false
  }
}

onMounted(() => {
  void fetchSummary()
})
</script>

<template>
  <section class="summary-detail-page">
    <template v-if="loading && !loaded">
      <div class="content-panel">
        <div aria-label="加载中" class="initial-loading-space" />
      </div>
    </template>

    <template v-else-if="notFound">
      <div class="content-panel">
        <div class="feedback-state">
          <el-alert
            :closable="false"
            title="总结不存在或当前用户不可见"
            type="error"
            show-icon
          />
          <router-link :to="getSummaryListLocation()">
            返回总结列表
          </router-link>
        </div>
      </div>
    </template>

    <template v-else-if="errorMessage">
      <div class="content-panel">
        <div class="feedback-state">
          <el-alert
            :closable="false"
            :title="errorMessage"
            type="error"
            show-icon
          />
          <el-button
            type="primary"
            @click="fetchSummary"
          >
            重新加载
          </el-button>
        </div>
      </div>
    </template>

    <template v-else-if="summary">
      <header class="page-header">
        <div>
          <p class="breadcrumb">
            <RouterLink :to="getSummaryListLocation()">
              总结
            </RouterLink>
            <span>/</span>
            <span>{{ typeLabels[summary.type] }}</span>
          </p>
          <h1>
            {{ typeLabels[summary.type] }}
          </h1>
          <div class="summary-meta">
            <span>
              项目 <ProjectLink v-if="project" :project-id="project.id">{{ project.name }}</ProjectLink>
              <template v-else>不可用</template>
            </span>
            <span v-if="summary.taskId">
              关联任务 <TaskLink
                v-if="relatedTask"
                :task-id="relatedTask.id"
                :project-id="summary.projectId"
              >{{ relatedTask.title }}</TaskLink><template v-else>不可用</template>
            </span>
            <span>
              创建者 <UserLink :user-id="summary.createdBy.id">{{ summary.createdBy.displayName }}</UserLink>
            </span>
            <span class="muted">更新于 {{ formatDateTime(summary.updatedAt) }}</span>
          </div>
        </div>

        <div
          v-if="canModify"
          class="header-actions"
        >
          <el-button @click="openEditDialog">
            <MaterialIcon name="edit" />
            编辑
          </el-button>

          <el-button
            :loading="deleteSubmitting"
            type="danger"
            @click="handleDelete"
          >
            <MaterialIcon name="delete" />
            删除
          </el-button>
        </div>
      </header>

      <!-- Content -->
      <section class="content-panel">
        <div class="tag-row">
          <el-tag
            :type="typeTagTypes[summary.type]"
            effect="plain"
          >
            {{ typeLabels[summary.type] }}
          </el-tag>
        </div>

        <div class="info-section">
          <h3>总结内容</h3>
          <div class="summary-content">
            {{ summary.content }}
          </div>
        </div>

        <div class="info-section">
          <h3>详细信息</h3>
          <dl class="meta-list">
            <dt>项目</dt>
            <dd>
              <ProjectLink v-if="project" :project-id="project.id">{{ project.name }}</ProjectLink>
              <span v-else>不可用</span>
            </dd>

            <dt>关联任务</dt>
            <dd>
              <template v-if="summary.taskId">
                <TaskLink
                  v-if="relatedTask"
                  :task-id="summary.taskId"
                  :project-id="summary.projectId"
                >
                  {{ relatedTask.title }}
                </TaskLink>
                <span v-else>关联任务不可用</span>
              </template>
              <span v-else>项目级总结</span>
            </dd>

            <dt>创建者</dt>
            <dd><UserLink :user-id="summary.createdBy.id">{{ summary.createdBy.displayName }}</UserLink></dd>

            <dt>创建时间</dt>
            <dd>{{ formatDateTime(summary.createdAt) }}</dd>

            <dt>更新时间</dt>
            <dd>{{ formatDateTime(summary.updatedAt) }}</dd>
          </dl>
        </div>
      </section>

      <!-- Edit dialog -->
      <el-dialog
        v-model="editDialogVisible"
        title="编辑总结"
        width="560px"
      >
        <el-form
          label-position="top"
          :model="editForm"
        >
          <el-form-item label="类型">
            <el-select
              v-model="editForm.type"
              class="enum-select"
            >
              <el-option label="阶段总结" value="STAGE" />
              <el-option label="最终总结" value="FINAL" />
            </el-select>
          </el-form-item>

          <el-form-item label="内容" required>
            <el-input
              v-model="editForm.content"
              placeholder="请输入总结内容"
              type="textarea"
              :rows="6"
            />
          </el-form-item>
        </el-form>

        <template #footer>
          <el-button @click="editDialogVisible = false">
            取消
          </el-button>

          <el-button
            type="primary"
            :loading="editSubmitting"
            @click="handleEdit"
          >
            保存
          </el-button>
        </template>
      </el-dialog>

    </template>

  </section>
</template>

<style scoped>
.summary-detail-page {
  display: grid;
  gap: 16px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 8px;
  color: var(--fs-color-text-secondary, #64748b);
  font-size: 13px;
}

.breadcrumb a {
  color: var(--fs-color-primary, #2563eb);
  text-decoration: none;
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

.summary-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-top: 8px;
  color: var(--fs-color-text-secondary, #64748b);
}

.header-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.content-panel {
  border: 1px solid var(--fs-color-border, #dbe3ee);
  border-radius: 8px;
  background: var(--fs-color-surface, #fff);
  padding: 20px;
}

.tag-row {
  margin-bottom: 12px;
}

.info-section {
  margin-top: 16px;
}

.info-section h3 {
  margin: 0 0 8px;
  font-size: 16px;
  color: var(--fs-color-text, #1f2937);
}

.summary-content {
  max-width: 70ch;
  color: var(--fs-color-text, #1f2937);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  line-height: 1.7;
}

.meta-list {
  display: grid;
  grid-template-columns: 100px 1fr;
  gap: 8px 16px;
  margin: 0;
}

.meta-list dt {
  color: var(--fs-color-text-secondary, #64748b);
}

.meta-list dd {
  margin: 0;
  color: var(--fs-color-text, #1f2937);
}

.feedback-state {
  display: grid;
  min-height: 240px;
  align-content: center;
  gap: 16px;
  justify-items: center;
}

.summary-link {
  color: var(--fs-color-primary, #2563eb);
  text-decoration: none;
}

.summary-link:hover {
  text-decoration: underline;
}

.enum-select {
  width: 160px;
}

.muted {
  color: var(--fs-color-text-secondary, #64748b);
}

@media (max-width: 480px) {
  .meta-list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .page-header {
    flex-direction: column;
  }

  .content-panel {
    padding: 16px;
  }

  .summary-meta {
    flex-direction: column;
    gap: 4px;
  }
}
</style>

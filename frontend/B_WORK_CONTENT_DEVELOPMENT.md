# 队友 B：任务与内容协作模块 — 开发文档

开发分支 `feat/frontend-work-content`，基于 `main` 创建（2026-07-21）。

## 完成情况

### 主要任务

- [x] 完成任务列表的搜索、筛选、分页和进入详情
- [x] 完成任务详情以及任务创建、完整编辑和删除
- [x] 完成 owner 或 assignee 修改任务状态，并正确展示父子任务关系
- [x] 完成任务日志分页、新增进度记录和授权用户删除日志
- [x] 完成总结列表的筛选、分页和进入详情
- [x] 完成总结详情以及总结创建、完整编辑和删除
- [x] 为上述页面补充模块路由导出和最小组件测试

### Optimal

- [x] 完成任务 AI 建议、人工审阅编辑以及提交为普通任务日志的入口
- [x] 完成项目 AI 任务计划生成、页面临时编辑和批量导入

---

## 新增文件及职责

### 任务模块 (`src/views/tasks/`)

| 文件 | 职责 |
|---|---|
| `types.ts` | Task、TaskLog、分页、查询、筛选项、创建/更新/状态请求体的 TypeScript 类型定义 |
| `api.ts` | 9 个 API 函数：任务 CRUD（getTasks/createTask/updateTask/updateTaskStatus/deleteTask）、任务日志 CRUD（getTaskLogs/createTaskLog/deleteTaskLog）、getTask |
| `TaskListView.vue` | 任务列表页。筛选（标题/项目/负责人/状态/优先级/父任务/截止日期范围）、分页、5 种状态、创建对话框 |
| `TaskDetailView.vue` | 任务详情页。详情展示、编辑（完整 PUT）、状态修改、删除（含 409 处理）、日志列表/新增/删除、AI 建议区（获取→编辑→提交） |

### 总结模块 (`src/views/summaries/`)

| 文件 | 职责 |
|---|---|
| `types.ts` | Summary、分页、查询、筛选项、创建/更新请求体类型 |
| `api.ts` | 5 个 API 函数：getSummaries/getSummary/createSummary/updateSummary/deleteSummary |
| `SummaryListView.vue` | 总结列表页。筛选（项目/任务/类型/创建者）、分页、5 种状态、创建对话框 |
| `SummaryDetailView.vue` | 总结详情页。详情展示、编辑（完整 PUT）、删除确认 |

### AI 模块 (`src/views/ai/`)

| 文件 | 职责 |
|---|---|
| `types.ts` | AiTaskPlanItem、AiSuggestionResponse/Body、AiPlanResponse、ImportPlanResponse 类型 |
| `api.ts` | 3 个 API 函数：getTaskSuggestion/generateTaskPlan/importTaskPlan |
| `AiTaskPlanView.vue` | AI 任务计划页。三步流程：输入目标→生成→编辑临时条目→批量导入。所有中间状态纯前端临时存储。ADMIN 自动拒绝 |

### 路由文件

| 文件 | 导出 | 路由 |
|---|---|---|
| `src/router/routes/tasks.ts` | `taskRoutes` | `/tasks`, `/tasks/:taskId` |
| `src/router/routes/summaries.ts` | `summaryRoutes` | `/summaries`, `/summaries/:summaryId` |
| `src/router/routes/ai.ts` | `aiRoutes` | `/projects/:projectId/ai-plan` |

### 测试文件

| 文件 | 测试数 | 覆盖场景 |
|---|---|---|
| `src/__tests__/TaskListView.spec.ts` | 3 | success / empty / error |
| `src/__tests__/SummaryListView.spec.ts` | 3 | success / empty / error |

---

## 调用链

```
Router (/tasks, /tasks/:taskId, /summaries, ...)
  → View (管理 loading/empty/error 状态和表单)
    → 模块 api.ts (拼 URL、HTTP 方法、传参)
      → shared/api/http.ts (Axios 实例，/api、withCredentials、10s 超时)
      → shared/api/csrf.ts (POST/PUT/DELETE 前获取 CSRF Header)
    → shared/api/errors.ts (getApiErrorMessage、hasApiStatus 解析 Problem Details)
  → stores/auth.ts (useAuthStore → currentUser 判断 ADMIN/USER 权限)
```

---

## 使用的接口

### 任务
- `GET /tasks` — 任务列表（支持 9 个查询参数 + 6 个排序字段）
- `POST /tasks` — 创建任务
- `GET /tasks/{taskId}` — 任务详情
- `PUT /tasks/{taskId}` — 完整编辑任务（7 个字段全部必传）
- `PUT /tasks/{taskId}/status` — 修改任务状态
- `DELETE /tasks/{taskId}` — 删除任务（有子任务/日志/总结时 409）

### 任务日志
- `GET /tasks/{taskId}/logs` — 日志列表
- `POST /tasks/{taskId}/logs` — 新增进度记录（progressPercent 0-100, content 1-1000）
- `DELETE /tasks/{taskId}/logs/{logId}` — 删除日志

### 总结
- `GET /summaries` — 总结列表
- `POST /summaries` — 创建总结
- `GET /summaries/{summaryId}` — 总结详情
- `PUT /summaries/{summaryId}` — 编辑总结
- `DELETE /summaries/{summaryId}` — 删除总结

### AI
- `POST /ai/task-suggestions` — 获取单任务建议
- `POST /projects/{projectId}/ai/task-plans` — 生成任务计划
- `POST /projects/{projectId}/ai/task-plans/imports` — 导入任务计划

---

## 权限判断

| 场景 | 判断方式 |
|---|---|
| ADMIN 隐藏写按钮 | `authStore.currentUser?.systemRole === 'ADMIN'` → 不渲染 |
| 任务编辑/删除 | 非 ADMIN 即可显示（server 端校验 owner） |
| 任务状态修改 | 非 ADMIN 即可显示（server 端校验 owner/assignee） |
| 任务日志创建 | 非 ADMIN 即可显示 |
| 日志删除 | 非 ADMIN 且当前用户为日志 operator |
| AI 建议入口 | 非 ADMIN 且当前用户为任务 assignee |
| AI 计划入口 | 非 ADMIN（在 AiTaskPlanView mounted 时二次校验） |
| 总结编辑/删除 | 非 ADMIN 且当前用户为总结创建者 |
| 总结创建 | 非 ADMIN |

---

## 重要业务边界

- 所有写请求先调用 `getCsrfHeaders()` 获取 CSRF Token
- 资源更新使用完整 PUT（所有可编辑字段必传，nullable 字段传 `null`）
- `progressPercent` 来自最新 TaskLog，创建/删除日志后需要重新获取 Task
- AI 建议为临时文本，用户必须人工编辑并通过 `POST /tasks/{taskId}/logs` 提交
- AI 任务计划全部状态为前端临时，不持久化。刷新/离开即丢失
- 删除任务遇到 409（有子任务/日志/总结）时保留页面，展示错误
- 分页：API 0-based，Element Plus 1-based
- 所有枚举值使用中文 Record 映射，不直接展示 API 枚举名

---

## 验证结果

| 命令 | 结果 |
|---|---|
| `pnpm lint` | 0 warning, 0 error |
| `pnpm test` | 13 files, 119 tests 全部通过 |
| `pnpm test:mock` | 5 files, 89 tests 全部通过 |
| `pnpm build` | TypeScript 类型检查 + Vite production build 通过 |

---

## 待接入（需基座维护者操作）

1. **路由接入** — 在 `src/router/index.ts` 中 import 并展开 `taskRoutes`、`summaryRoutes`、`aiRoutes`
2. **菜单接入** — 在 `src/layouts/AppLayout.vue` 的 `adminMenu` 和 `userMenu` 中为「任务」「总结」启用菜单项（将 `disabled: true` 改为 `false`）

模块开发者不应自行修改上述公共文件。

---

## 手动功能验证清单

启动 Mock 模式：`mise run frontend`，浏览器访问 `http://localhost:8081`。

### 任务列表 `/tasks`
- [ ] 页面加载后显示 2 条种子任务（501、502）
- [ ] 按状态筛选「进行中」→ 只显示任务 501
- [ ] 按优先级筛选「高」→ 只显示任务 501
- [ ] 按标题搜索不存在的文字 → 空状态 + 「重置筛选」按钮
- [ ] 点击任务标题 → 跳转到任务详情
- [ ] 以 `admin` 登录 → 无「创建任务」按钮

### 任务详情 `/tasks/501`
- [ ] 显示标题、状态 tag、优先级 tag、进度 40%、描述、创建者、负责人
- [ ] 显示 2 条进度记录
- [ ] 点击「编辑」→ 对话框预填所有字段 → 修改标题 → 保存 → 刷新
- [ ] 点击「修改状态」→ 选「已完成」→ 保存 → 状态更新
- [ ] 点击「新增日志」→ 填进度和内容 → 提交 → 日志列表更新、任务进度更新
- [ ] 删除一条日志（当前用户为 operator）→ 确认 → 删除成功
- [ ] 点击「删除任务」（任务 501 有日志）→ 409 错误提示，不跳转
- [ ] 访问 `/tasks/999` → 「任务不存在或当前用户不可见」
- [ ] 以 `admin` 登录 → 无编辑/删除/状态修改/新增日志按钮

### AI 建议（在 `/tasks/501` 内，以 `lisi` 登录）
- [ ] 可见「AI 任务建议」区域（lisi 是 assignee）
- [ ] 点击「获取 AI 建议」→ loading → 显示可编辑文本 + 进度输入框
- [ ] 编辑文本 → 点击「提交为进度记录」→ 日志新增成功，AI 区清空

### 任务删除 `/tasks/502`
- [ ] 任务 502 无日志/子任务 → 点击「删除」→ 确认 → 删除成功 → 跳回任务列表

### AI 任务计划 `/projects/101/ai-plan`
- [ ] 以 `zhangsan` 登录 → 页面显示输入表单
- [ ] 填写目标 → 点击「生成初步计划」→ skeleton → 显示 3 条可编辑条目
- [ ] 编辑标题 → 新增临时条目 → 删除条目
- [ ] 点击「确认并导入」→ 成功 → 显示导入数量 → 返回任务列表
- [ ] 以 `admin` 登录 → 直接显示「管理员不参与项目内容操作」

### 总结列表 `/summaries`
- [ ] 显示 2 条种子总结（901、902）
- [ ] 按类型筛选「阶段总结」→ 全部显示
- [ ] 按类型筛选「最终总结」→ 空状态
- [ ] 点击「创建总结」→ 填写项目/类型/内容 → 提交 → 列表刷新
- [ ] 点击「查看」→ 进入详情

### 总结详情 `/summaries/901`
- [ ] 显示内容、类型 tag、创建者、时间
- [ ] 点击「编辑」→ 修改内容 → 保存 → 刷新
- [ ] 点击「删除」→ 确认 → 删除成功 → 跳回列表
- [ ] 以 `lisi`（非创建者）登录 → 无编辑/删除按钮
- [ ] 以 `admin` 登录 → 只读，无任何操作按钮

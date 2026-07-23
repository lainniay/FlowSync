# 队友 B：任务与内容协作模块 — 开发文档

原始开发分支为 `feat/frontend-work-content`。当前修复从最新 `main` 创建独立集成分支，
吸收 PR #25 的修复，不直接修改或覆盖 PR #20 原分支。

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

| 文件 | 覆盖场景 |
|---|---|
| `src/__tests__/TaskListView.spec.ts` | 任务列表 success / empty / error |
| `src/__tests__/SummaryListView.spec.ts` | 总结列表 success / empty / error |
| `src/__tests__/TaskListView.archived.spec.ts` | 归档项目隐藏任务创建入口 |
| `src/__tests__/TaskDetailView.archived.spec.ts` | 归档与项目上下文失败时关闭写入口、日志失败重试、超长 AI 建议拦截 |
| `src/__tests__/SummaryListView.create.spec.ts` | 创建总结默认项目为空，仅显式路由上下文预填 |
| `src/__tests__/SummaryDetailView.archived.spec.ts` | 归档与项目上下文失败时隐藏总结编辑、删除入口 |
| `src/__tests__/AiTaskPlanView.archived.spec.ts` | 归档项目拒绝进入 AI 计划写流程 |
| `src/__tests__/task-b-verification.spec.ts` | 既有 MSW 权限与状态验证；保留但不继续扩充重复 API 测试 |

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

| 场景 | owner | assignee | creator / operator | member | ADMIN | archived |
|---|---|---|---|---|---|---|
| 创建任务 | 允许 | 仅同时为 owner 时允许 | 仅同时为 owner 时允许 | 禁止 | 禁止 | 禁止 |
| 编辑、删除任务 | 允许 | 禁止 | 禁止 | 禁止 | 禁止 | 禁止 |
| 修改任务状态 | 允许 | 允许 | 仅同时为 owner 或 assignee 时允许 | 禁止 | 禁止 | 禁止 |
| 创建任务日志、使用 AI 建议 | 允许 | 允许 | 仅同时为 owner 或 assignee 时允许 | 禁止 | 禁止 | 禁止 |
| 删除任务日志 | 允许 | 仅同时为日志 operator 时允许 | 日志 operator 允许 | 禁止 | 禁止 | 禁止 |
| 创建总结 | 允许 | 项目成员允许 | 项目成员允许 | 允许 | 禁止 | 禁止 |
| 编辑、删除总结 | 允许 | 仅同时为总结 creator 时允许 | 总结 creator 允许 | 禁止 | 禁止 | 禁止 |
| 生成、编辑、导入 AI 计划 | 允许 | 禁止 | 禁止 | 禁止 | 禁止 | 禁止 |

前端必须先确认项目明确处于未归档状态才显示项目内容写入口。项目上下文请求失败或尚未完成
时按只读处理；后端仍是最终权限校验方。

---

## 重要业务边界

- 所有写请求先调用 `getCsrfHeaders()` 获取 CSRF Token
- 资源更新使用完整 PUT（所有可编辑字段必传，nullable 字段传 `null`）
- `progressPercent` 来自最新 TaskLog，创建/删除日志后需要重新获取 Task
- AI 建议为临时文本，用户必须人工编辑并通过 `POST /tasks/{taskId}/logs` 提交
- AI 建议可能超过 TaskLog `content` 的 1000 字限制；提交前必须提示用户人工精简，不能静默截断
- AI 任务计划全部状态为前端临时，不持久化。刷新/离开即丢失
- 不得为总结内容或 AI 计划补充说明增加契约之外的前端长度限制
- 删除任务遇到 409（有子任务/日志/总结）时保留页面，展示错误
- 分页：API 0-based，Element Plus 1-based
- 所有枚举值使用中文 Record 映射，不直接展示 API 枚举名

---

## 验证结果

| 命令 | 结果 |
|---|---|
| 任务与总结详情定向回归 | 2 files, 6 tests 全部通过 |
| `pnpm exec oxlint .` | 108 files，0 warning，0 error |
| `pnpm exec eslint . --no-cache` | 通过 |
| `pnpm test` | 28 files，187 tests 全部通过 |
| `pnpm test:mock` | 5 files，89 tests 全部通过 |
| `pnpm build` | TypeScript 类型检查与 Vite production build 通过 |

### 真实后端联调

- 登录、刷新后 Session 保持、`GET /users/me` 与 Overview 请求正常。
- ADMIN 创建 USER、项目和项目成员成功；ADMIN 的任务、总结创建入口隐藏。
- owner 创建、编辑任务，修改状态，创建日志并删除 assignee 日志后数据立即同步。
- assignee 只能修改状态、创建日志和使用 AI 入口，不能编辑或删除任务。
- member 创建、编辑总结，项目 owner 可编辑 member 创建的总结。
- 归档后任务、日志、总结和 AI 写入口全部关闭；任务与总结仍可只读访问。
- 项目详情已接入任务、总结和 AI 计划入口，列表、详情和项目之间保留返回路径与项目筛选。
- 本地 AI provider 未启用，已验证 `503` 错误反馈；真实 AI 成功生成不在本轮可执行范围内。

---

## 集成状态

- `taskRoutes`、`summaryRoutes`、`aiRoutes` 已由前端集成负责人接入根路由。
- ADMIN 与 USER 的任务、总结菜单已启用；用户管理、邀请和个人中心菜单保持最新 `main` 状态。
- 公共根路由和 `AppLayout` 继续由前端负责人统一维护，业务模块不得自行覆盖公共菜单。

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
- [ ] 以 owner 访问归档项目上下文 → 无「创建任务」按钮

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
- [ ] 访问归档项目任务 → 无编辑、删除、状态、日志和 AI 写入口
- [ ] 日志请求失败 → 显示独立错误与重试入口，不显示空列表或旧数据

### AI 建议（在 `/tasks/501` 内，以 `lisi` 登录）
- [ ] 可见「AI 任务建议」区域（lisi 是 assignee）
- [ ] 点击「获取 AI 建议」→ loading → 显示可编辑文本 + 进度输入框
- [ ] 编辑文本 → 点击「提交为进度记录」→ 日志新增成功，AI 区清空
- [ ] AI 返回超过 1000 字建议 → 阻止提交并提示人工精简，不静默截断

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
- [ ] 无项目路由上下文时打开创建表单 → 项目 ID 默认为空
- [ ] 从明确的项目路由上下文进入 → 创建表单可预填对应项目 ID
- [ ] 点击「查看」→ 进入详情

### 总结详情 `/summaries/901`
- [ ] 显示内容、类型 tag、创建者、时间
- [ ] 点击「编辑」→ 修改内容 → 保存 → 刷新
- [ ] 点击「删除」→ 确认 → 删除成功 → 跳回列表
- [ ] 以 `lisi`（非创建者）登录 → 无编辑/删除按钮
- [ ] 以 `admin` 登录 → 只读，无任何操作按钮
- [ ] 访问归档项目总结 → 创建者和 owner 均无编辑、删除入口

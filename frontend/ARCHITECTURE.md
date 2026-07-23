# FlowSync 前端运行结构与页面职责

本文档解释前端从启动到显示页面的完整关系，供模块开发者、Review 人员和 AI 编码工具共同
使用。页面地图与权限范围见 `PROTOTYPE.md`，视觉规则见 `DESIGN.md`，HTTP 契约见
`../docs/api.md`。

## 1. 谁是入口，谁是页面

前端不存在一个同时负责所有事情的“main 页面”。运行入口、根组件、公共布局和业务页面分层
负责不同工作。

```text
浏览器加载 index.html
→ src/main.ts                    应用启动入口
→ src/App.vue                    Vue 根组件
→ src/router/index.ts            根据 URL 和认证状态选择路由
   ├─ /login
   │  └─ src/views/LoginView.vue 独立登录页
   │
   └─ 登录后父路由 /
      └─ src/layouts/AppLayout.vue
         ├─ 侧栏
         ├─ 顶栏、用户信息和退出
         └─ RouterView           当前业务子页面插槽
            ├─ /overview → OverviewView.vue
            ├─ /projects → ProjectListView.vue
            ├─ /tasks → TaskListView.vue
            ├─ /summaries → SummaryListView.vue
            ├─ /403 → ForbiddenView.vue
            └─ 未匹配 → NotFoundView.vue
```

### `src/main.ts`

这是 JavaScript 应用启动入口，不是一个页面。

当前职责：

- 在开发环境按配置启动 MSW；
- 创建 Vue 应用；
- 安装 Pinia；
- 安装 Router；
- 把整个应用挂载到 `index.html` 中的 `#app`。

边界：不写登录表单、菜单、业务 API 或具体页面逻辑。

### `src/App.vue`

这是 Vue 根组件，也是应用最外层的路由出口。

当前只包含一个 `RouterView`。Router 根据 URL 在这里选择显示独立 LoginView，或显示登录后的
AppLayout。

边界：不复制 AppLayout，不保存登录身份，不直接调用业务 API。

### `src/router/index.ts`

这是根路由表和全局导航守卫。

当前职责：

- 把 URL 映射到页面组件；
- 组织 AppLayout 父路由和业务子路由；
- 导航前初始化当前 Session；
- 根据 `requiresAuth`、`guestOnly` 和 `roles` 放行或重定向；
- 提供 403 和登录后 404 路由。

边界：只判断登录状态与系统角色。项目 owner、member、assignee、creator 等资源权限必须在
取得具体资源后由业务页面判断，并由后端最终校验。

### `src/layouts/AppLayout.vue`

这是所有登录后页面共用的外框。

当前职责：

- 显示 FlowSync 品牌入口；
- 根据 ADMIN/USER 显示不同菜单结构；
- 显示当前用户信息与系统角色；
- 提供退出入口；
- 用内部 `RouterView` 承载当前业务子页面。

边界：不请求项目、任务、用户列表或其他业务数据。业务页面不能复制侧栏、顶栏或退出逻辑。

### `src/views/LoginView.vue`

这是未登录用户的独立页面，不使用 AppLayout。

调用链：

```text
表单校验
→ authStore.login()
→ GET /api/auth/csrf
→ POST /api/auth/login
→ Store 保存当前用户
→ 检查安全 redirect
→ Router 进入原业务页或 /overview
```

边界：不负责首次 Session 初始化，不请求业务数据。

### `src/views/overview/OverviewView.vue`

这是当前 `/overview` 工作台子页面。

当前通过 `GET /api/overview` 加载工作台统计，并展示 loading、error 和业务数据状态。

边界：不再主动调用 `/users/me`，不显示登录表单，不实现根布局。

### `ForbiddenView.vue` 与 `NotFoundView.vue`

- ForbiddenView：页面存在，但当前已登录用户没有权限，显示 403。
- NotFoundView：前端 URL 没有匹配页面，显示登录后 AppLayout 内的 404。

业务接口返回的 404 还需要业务页面处理“资源不存在或当前用户不可见”，不能全部交给前端
路由 404。

## 2. Router 父子关系

访问 `/overview` 时会同时匹配：

```text
父路由：/
  component = AppLayout
  meta.requiresAuth = true

子路由：overview
  component = OverviewView
```

页面结果是 AppLayout 和 OverviewView 同时存在：AppLayout 负责外框，OverviewView 出现在
AppLayout 的 `RouterView` 位置。

Vue Router 会把匹配到的父子 route record 的 `meta` 从父到子做非递归合并。因此子路由可以
读取父路由的 `requiresAuth=true`，不用逐页重复声明。

## 3. 当前认证初始化调用链

```text
1. 浏览器导航到目标 URL

2. src/router/index.ts
   beforeEach() 在页面确认前调用 authStore.initialize()

3. src/stores/auth.ts
   initialize() 复用同一个 initializationPromise，避免并发重复初始化
   loadCurrentUser() 管理 loading、currentUser、initialized 和 errorMessage

4. src/shared/api/auth.ts
   getCurrentUser() 描述 GET /users/me

5. src/shared/api/http.ts
   Axios 补上 /api、10 秒超时和 Cookie 凭据

6. src/mocks/handlers/auth.ts（仅启用 Mock 时）或真实后端
   返回 User 或 401 Problem Details

7. 响应返回 Store
   成功：currentUser=User
   401：currentUser=null

8. Router 守卫继续判断
   guestOnly + 已登录 → /overview 或安全 redirect
   requiresAuth + 未登录 → /login?redirect=原地址
   roles 不匹配 → /403
   其余 → 放行并渲染页面
```

`/users/me` 的职责分配：Router 决定何时需要确认身份，Store 管理共享认证状态，auth API
描述请求，Axios 发送请求，View 只消费结果。

## 4. 当前正式路由

| URL | 组件组合 | 访问条件 | 状态 |
| --- | --- | --- | --- |
| `/login` | App → LoginView | 未登录；已登录会离开 | 已实现 |
| `/` | 重定向 `/overview` | 已登录 | 已实现 |
| `/overview` | App → AppLayout → OverviewView | 已登录 | 已实现 |
| `/admin/users` | App → AppLayout → UserListView | ADMIN | 已实现 |
| `/projects` | App → AppLayout → ProjectListView | 已登录 | 已实现 |
| `/projects/:projectId` | App → AppLayout → ProjectDetailView | 已登录；资源权限由页面与后端校验 | 已实现 |
| `/tasks` | App → AppLayout → TaskListView | 已登录；ADMIN 只读 | 已实现 |
| `/tasks/:taskId` | App → AppLayout → TaskDetailView | 已登录；写权限由项目关系与归档状态决定 | 已实现 |
| `/summaries` | App → AppLayout → SummaryListView | 已登录；ADMIN 只读 | 已实现 |
| `/summaries/:summaryId` | App → AppLayout → SummaryDetailView | 已登录；写权限由项目关系与归档状态决定 | 已实现 |
| `/projects/:projectId/ai-plan` | App → AppLayout → AiTaskPlanView | 已登录；仅未归档项目 owner 可操作 | 已实现 |
| `/invitations` | App → AppLayout → InvitationListView | USER | 已实现 |
| `/profile` | App → AppLayout → ProfileView | 已登录 | 已实现 |
| `/403` | App → AppLayout → ForbiddenView | 已登录 | 已实现 |
| 未匹配地址 | App → AppLayout → NotFoundView | 已登录 | 已实现 |

用户管理、项目、任务、总结、邀请和个人中心菜单均已按系统角色启用。根路由只校验 Session
和 ADMIN/USER 系统角色；owner、member、assignee、creator、operator 与 archived 等资源级
边界由业务页面控制入口，并由后端最终校验。

## 5. Store、API 与 View 的关系

```text
View
→ 展示数据、收集输入、触发操作

Store
→ 只保存跨页面共享或需要长期保持的前端状态

API module
→ 描述 URL、HTTP 方法、请求参数和响应类型

Axios instance
→ 负责 /api、Cookie、超时等共有配置；业务请求 401 触发 Session 失效回调

Public API types
→ `types.ts` 定义公共枚举、分页、UserBrief、认证和 Problem Details 数据形状

Error helpers
→ `errors.ts` 解析 Problem Details、字段错误并分类 HTTP/网络错误，不直接弹页面消息

MSW / backend
→ 根据 API 契约返回数据和 Problem Details
```

不是所有列表都需要 Pinia。普通项目列表由页面直接调用项目 API 即可；认证身份需要跨所有
页面共享，因此使用 Pinia。

## 6. 模块开发者边界

模块开发者可以：

- 在自己的模块目录创建类型、API、页面、组件和测试；
- 使用认证 Store 读取当前用户；
- 使用公共 Axios、CSRF 和 Problem Details 工具；
- 提交模块路由数组，由基座维护者接入根路由；
- 为真实页面添加 scoped 样式。

模块开发者不能直接修改：

- `src/main.ts`；
- `src/App.vue`；
- `src/router/index.ts`；
- `src/layouts/AppLayout.vue`；
- `src/assets/main.css`；
- `src/stores/auth.ts`；
- `src/shared/api/http.ts`、`csrf.ts`、`errors.ts` 和公共 `types.ts`。

确需修改公共文件时，在 PR 描述中说明原因，由基座维护者统一接入。

## 7. 当前验证

公共基座已验证：

- Router 认证守卫：未登录重定向、USER 拒绝 ADMIN 路由、ADMIN 放行；
- 安全 redirect 和系统角色规则；
- OverviewView 在安装 Pinia 后可独立挂载；
- 3 个相关测试文件、9 个测试通过；
- 公共 API 错误工具 1 个测试文件、5 个测试通过；
- HTTP Session 失效检测与回调 1 个测试文件、7 个测试通过；
- production build 通过。

2026-07-23 任务与内容集成修复验证：Oxlint 0 warning、0 error，ESLint 通过，完整 Vitest
28 个文件、187 个测试通过，`test:mock` 5 个文件、89 个测试通过，production build 通过；
关闭 MSW 后已完成真实 Session、任务、日志、总结、归档权限与项目入口联调。

Mock Session 是内存模拟，整页刷新后的 Session 行为不能替代真实 HttpOnly Cookie 联调。

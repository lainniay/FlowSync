# FlowSync Frontend

本文档优先记录前端当前完成情况、现成可复用能力、协作边界和下一步任务。模块负责人必须
在开始编码前登记，未登记的模块不得并行开发。开发人员开始编码前，按以下顺序阅读：

1. 根目录 `AGENTS.md`
2. 根目录 `README.md` 和本文件
3. `ARCHITECTURE.md`
4. `PROTOTYPE.md` 和 `DESIGN.md`
5. `../docs/api.md` 和 `../docs/detail.md`
6. 涉及数据模型、外键或删除规则时阅读 `../docs/relationship.md`

不要根据页面、Mock 或后端实现自行推测接口。跨前后端的请求、响应、权限、Session 和
CSRF 约定只以 `../docs/api.md` 为准；联调排障说明也应写入 `../docs/`。

## 仓库文档导航


| 文档                                    | 面向对象与用途                                               | 前端开发者何时阅读                      |
| ------------------------------------- | ----------------------------------------------------- | ------------------------------ |
| `../AGENTS.md`                        | 全仓库开发规则、契约优先级、安全、权限、测试和 Git 规则                        | 开工前必读                          |
| `../README.md`                        | Windows/macOS 环境复现、mise、Docker、前后端启动方式                | 首次配置环境或启动全栈时                   |
| `README.md`                           | 前端当前状态、现成能力、目录边界、分工、交付和验证                             | 开工前必读，PR 前复查                   |
| `ARCHITECTURE.md`                     | `main.ts`、App、Router、AppLayout、View、Store、API 的职责和调用链 | 不清楚文件应该放哪或谁调用谁时                |
| `PROTOTYPE.md`                        | 页面地图、ADMIN/USER 页面差异、低保真结构、状态和关键流程                    | 创建页面、路由或判断操作入口时                |
| `DESIGN.md`                           | 颜色、字号、间距、Element Plus 和页面布局约定                         | 编写 template/style 或调整界面时       |
| `../docs/api.md`                      | 前后端和 MSW 唯一 HTTP 契约：路径、方法、类型、权限和错误                    | 写 `types.ts`、`api.ts` 和请求测试时必读 |
| `../docs/detail.md`                   | 角色、项目、任务、总结和 AI 的业务流程解释                               | 不理解业务动作和角色关系时                  |
| `../docs/relationship.md`             | 数据模型、字段、约束、外键和删除关系                                    | 涉及完整 PUT、nullable、删除或关联对象时     |
| `../backend/AUXILIARY_DEVELOPMENT.md` | 后端 Task、TaskLog、Summary 并行开发边界与公共能力                   | 联调这些模块或确认后端负责人时参考              |
| `../backend/TODO.md`                  | 后端尚未完成、部署前或外部集成事项                                     | 联调发现接口缺失时确认，不转写成前端缺陷           |


文档发生冲突时按以下优先级处理：

```text
AGENTS.md + docs/api.md + docs/relationship.md
> docs/detail.md
> frontend/PROTOTYPE.md + frontend/DESIGN.md + frontend/ARCHITECTURE.md
> 当前实现或口头约定
```

`backend/AUXILIARY_DEVELOPMENT.md` 和 `backend/TODO.md` 用于了解后端分工与状态，不是前端
接口契约；后端当前代码与 `docs/api.md` 不一致时，双方先确认并更新共享契约，不能让前端
静默适配一个未登记的返回结构。

## 当前目标

基座完成for后续模块能够复用同一套布局、路由、请求、错误处理、类型、设计规范和页面示例。

当前“分模块前公共基座”范围已完成 100%。包括：
页面地图、设计基线、认证与 Router、AppLayout、公共请求与类型、项目列表纵向示例和完整验证。

本文档当前首先服务于两名前端模块开发者：帮助其独立完成目录创建、页面开发、测试和 PR
交付。真实后端联调步骤与故障记录在开始联调时写入 `../docs/`，不阻塞前端模块开工。

2026-07-19 仓库复核：认证基础 PR #16 已合并到 `main`，本轮从与
`origin/main` 同步且无未提交文件的 `main` 创建 `codex/frontend-foundation-final`。
页面地图与低保真原型已按 `../docs/api.md` 和 `../docs/relationship.md` 写入
`PROTOTYPE.md`，后续布局、路由和业务页面必须遵守其中的页面边界与权限矩阵。

当前已确认的页面边界：

- 未登录用户只进入 `/login`；登录后由根路由按身份进入 `/overview`。
- ADMIN 使用工作台、用户管理、项目、任务、总结和个人中心；项目内容页面只读，但可执行
契约允许的系统管理动作。
- USER 使用工作台、项目、任务、总结、收到的邀请和个人中心；项目 owner、成员、负责人等
操作权限在具体资源页面判断，不能由 `systemRole` 代替。
- 登录后的页面共用 AppLayout；`/403` 和兜底 404 是公共反馈页，业务页面不得自行复制根布局。
- 项目详情承载任务、成员、邀请、总结和 AI 入口；AI 输出只保留在页面临时状态，人工确认后
才能调用普通写接口或计划导入接口。



## 现成能力


| 能力         | 位置                                                | 状态与用法                                                 |
| ---------- | ------------------------------------------------- | ----------------------------------------------------- |
| Vue 应用入口   | `src/main.ts`                                     | 已安装 Pinia、Router；开发环境可启动 MSW                          |
| 根组件        | `src/App.vue`                                     | 当前只渲染 `RouterView`                                    |
| 前端运行结构     | `ARCHITECTURE.md`                                 | 已明确入口、根组件、布局、页面、Router、Store 和 API 关系                 |
| 页面地图与低保真原型 | `PROTOTYPE.md`                                    | 已明确路由、布局、角色菜单、页面流程、反馈状态和资源权限                          |
| 全局设计基线     | `DESIGN.md`                                       | 已明确视觉气质、设计 token、Element Plus、页面模式、状态与禁用项             |
| 路由入口       | `src/router/index.ts`                             | 已有认证初始化、登录/访客/角色守卫、AppLayout 子路由、403 和 404            |
| 导航规则       | `src/router/navigation.ts`                        | 已有安全站内 redirect 和系统角色匹配工具                             |
| 应用布局       | `src/layouts/AppLayout.vue`                       | 已有侧栏、顶栏、用户信息、角色菜单和退出入口                                |
| Axios 实例   | `src/shared/api/http.ts`                          | 已配置 `/api`、超时、Cookie、业务请求 401 检测和 Session 失效回调        |
| CSRF 请求头   | `src/shared/api/csrf.ts`                          | 写请求调用 `getCsrfHeaders()`，禁止各模块重复实现                    |
| API 错误解析   | `src/shared/api/errors.ts`                        | 已有 Problem Details、字段错误和 401/403/404/409/422/网络/服务端分类 |
| 公共 API 类型  | `src/shared/api/types.ts`                         | 已有公共枚举、分页、UserBrief、认证和 Problem Details 类型            |
| 认证 API     | `src/shared/api/auth.ts`                          | 已有 CSRF、当前用户、登录和退出请求                                  |
| 认证状态       | `src/stores/auth.ts`                              | 已有幂等初始化、当前用户、loading、登录、退出和清理 Session action          |
| Mock API   | `src/mocks/`                                      | 已有 MSW handlers、种子状态和契约测试，禁止重复造 Mock 层                |
| 登录页        | `src/views/LoginView.vue`                         | 已有表单校验、登录、错误显示和安全返回原页面                                |
| 工作台基座页     | `src/views/HomeView.vue`                          | 当前用于验证登录后布局与子路由，真实 Overview 数据待接入                     |
| 公共反馈页      | `src/views/ForbiddenView.vue`, `NotFoundView.vue` | 已有登录后 403 和兜底 404                                     |
| 模块路由示例     | `src/router/routes/projects.ts`                   | 模块只导出子路由，由基座维护者接入根路由                                  |
| 项目列表纵向示例   | `src/views/projects/`                             | 已有类型、API、查询、筛选、分页和完整反馈状态，可复制为新模块模板                    |
| 项目列表最小测试   | `src/__tests__/ProjectListView.spec.ts`           | 已覆盖首次加载、empty 和 error 三条关键分支                          |




## 当前前端文件架构

以下是当前真实结构；业务模块在对应目录继续扩展，不要另建平行的 `pages/`、`services/` 或
第二套公共请求层：

```text
frontend/
  README.md                     当前状态、协作、分工与验证入口
  ARCHITECTURE.md               运行结构和文件职责
  PROTOTYPE.md                  页面地图、流程和权限原型
  DESIGN.md                     设计基线
  package.json                  pnpm 脚本和依赖
  vite.config.ts                Vite、路径别名和 /api 开发代理
  vitest.config.ts              前端测试配置
  src/
    main.ts                     启动 MSW，安装 Pinia/Router，挂载 Vue
    App.vue                     根 RouterView，不承载业务逻辑
    assets/
      main.css                  全局 token 和公共页面基线
    layouts/
      AppLayout.vue             登录后侧栏、顶栏、用户信息和内容出口
    router/
      index.ts                  根路由和全局认证/角色守卫；基座维护者负责
      navigation.ts             安全 redirect 和系统角色工具
      routes/
        projects.ts             项目模块子路由示例
    shared/api/
      http.ts                   公共 Axios 与 Session 失效处理
      csrf.ts                   写请求 CSRF Header
      errors.ts                 Problem Details 和错误分类
      auth.ts                   登录、退出和当前用户接口
      types.ts                  跨模块公共类型
    stores/
      auth.ts                   全局 Session 用户状态
    views/
      LoginView.vue             登录页
      HomeView.vue              当前工作台占位页
      ForbiddenView.vue         403 页面
      NotFoundView.vue          404 页面
      projects/
        types.ts                项目列表类型示例
        api.ts                  项目列表 API 示例
        ProjectListView.vue     标准纵向列表示例
    mocks/
      browser.ts                浏览器 MSW 启动
      store.ts                  可重置内存种子状态
      utils.ts                  运行时校验与 Problem Details 工具
      handlers/                 按 auth/users/projects/tasks 等模块分 handler
    __tests__/
      ProjectListView.spec.ts   业务页面最小测试示例
      router.spec.ts            Router 守卫测试
      navigation.spec.ts        导航工具测试
      http.spec.ts              公共 HTTP 行为测试
      api-errors.spec.ts        API 错误工具测试
      *.spec.ts                 其余认证与 MSW 契约测试
```

目录职责仍保持：`views` 放路由页面，`router/routes` 放模块路由，`shared` 只放跨模块能力，
`stores` 只放跨页面共享状态，`mocks` 模拟共享契约，`__tests__` 放 Vitest 与组件测试。

## 当前认证调用链

浏览器导航到页面后，当前用户请求按以下顺序执行：

```text
1. src/router/index.ts
   beforeEach() 在确认导航前调用 authStore.initialize()

2. src/stores/auth.ts
   initialize() 合并并发初始化；loadCurrentUser() 管理登录状态并调用 getCurrentUser()

3. src/shared/api/auth.ts
   getCurrentUser() 描述具体接口，调用 http.get('/users/me')

4. src/shared/api/http.ts
   Axios 实例补上 baseURL='/api'、超时和 Cookie 配置，发出 GET /api/users/me

5. src/mocks/handlers/auth.ts（仅 Mock 开发模式）
   MSW 拦截请求，从内存状态读取当前用户并返回 JSON

6. 响应沿原路返回
   Store 写入 currentUser，Router 根据 guestOnly、requiresAuth 和 roles 放行或重定向
```

各层边界：

- View 负责展示、输入和触发操作，不直接保存全局登录身份。
- Store 负责跨页面共享的响应式状态；普通页面局部数据不一定需要 Store。
- API module 负责具体 URL、HTTP 方法、请求体和响应类型，不负责页面样式。
- Axios 实例负责所有请求共有的基础配置，不写具体业务页面逻辑。
- MSW 只在开发环境模拟后端；生产环境和真实联调不能依赖 Mock 内存状态。

禁止 API 层导入 View，禁止在多个页面中重复创建 Axios 实例。不要为了所有服务端数据都创建
Pinia Store；只在跨页面共享、登录身份或需要跨组件长期保存时使用 Store。

## 任务



### P0 认证闭环

- [x] 建立 Axios 公共实例
- [x] 定义 User、LoginRequest、CSRF 和 Problem Details 类型
- [x] 实现 `GET /users/me`
- [x] 实现 CSRF 获取、登录和退出 API
- [x] 建立认证 Pinia Store
- [x] 首页连接当前用户读取和退出按钮
- [x] 使用 Element Plus 完成登录表单和前端校验
- [x] 验证 ADMIN、USER、错误密码和退出流程
- [x] 区分正常未登录与网络/服务端错误



### P0 请求基础设施

- [x] 将 CSRF 写请求处理提取为可供所有 API 模块复用的能力
- [x] 统一解析 RFC 9457 Problem Details
- [x] 分类处理 401、403、422 和未知网络错误，并统一处理业务请求 Session 失效
- [x] 明确响应拦截器只处理 Session 失效，页面负责业务消息，避免重复弹错
- [x] 为 Problem Details、字段错误和错误分类添加最小回归测试



### P0 路由与布局

- [x] 建立独立 LoginView
- [x] 建立应用根布局、侧栏、顶栏和内容区
- [x] 配置登录守卫和未登录重定向
- [x] 根据 `systemRole` 区分 ADMIN 与 USER 菜单
- [x] 采用模块路由文件，降低多人修改同一文件的冲突
- [x] 完成 404 和无权限页面



### P0 设计与标准示例

- [x] 完成 ADMIN、USER 低保真页面原型并记录页面与权限边界
- [x] 记录颜色、字号、间距、表格、表单、交互和反馈状态规范
- [x] 完成一个项目列表纵向示例
- [x] 示例包含类型、API、查询参数、分页、表格、loading、empty 和 error
- [x] 示例完成后补组件测试并通过 production build



### P0 交接准备

- [x] 建立页面、路由、接口和负责人登记表；实际分工必须在队友开工前填入
- [x] 写清新模块应复制的目录、路由交付方式、公共文件边界和 PR 流程
- [x] 确认 `pnpm lint`、`pnpm build`、`pnpm test`、`pnpm test:mock` 通过
- [x] 检查待提交文件，不提交 `.env`、`dist` 或本地测试产物



### 后续非阻断 TODO

- [ ] 开始连接真实后端时，将联调步骤、环境要求和常见故障记录到 `../docs/`



## 多人协作边界



### 开工前置条件

当前基座开发分支是 `codex/frontend-foundation-final`。认证基础 PR #16 已合并，但本分支的
路由、布局、项目列表示例和协作文档仍未提交、未合并。在基座 PR 合并前，两名前端队友只做
契约阅读、页面清单和接口整理，不从旧认证分支或本地未提交文件开始业务开发。

基座 PR 合并后，每名开发者先同步 `main`，再为自己的模块创建独立分支：

```powershell
git switch main
git pull --ff-only origin main
git switch -c feat/frontend-<module>
```

一个分支只负责一个已登记模块。不要把两个队友的业务代码放入同一分支，也不要在业务分支
顺手重构公共层。

### 模块目录模板

`src/views/projects/` 和 `src/__tests__/ProjectListView.spec.ts` 是标准纵向示例。新模块按实际
需要创建文件，不创建没有调用者的空 Store、空组件或万能工具：

```text
src/
  views/<module>/
    types.ts                 本模块请求、响应和页面模型类型
    api.ts                   本模块 URL、HTTP 方法、参数和响应类型
    XxxListView.vue          列表路由页面
    XxxDetailView.vue        仅在确有详情路由时创建
    components/              仅放本模块两个以上页面复用的组件
  router/routes/
    <module>.ts              只导出本模块的 AppLayout 子路由
  __tests__/
    XxxListView.spec.ts      页面最小组件测试
    <module>-api.spec.ts     仅在 API 映射存在特殊逻辑时创建
  mocks/handlers/
    <module>.ts              已有对应 handler 时优先复用，契约变化才修改
```

模块调用链固定为：

```text
Router -> View -> 本模块 api.ts -> shared/api/http.ts -> 后端或开发环境 MSW
                   |                    |
                   |                    +-> 公共 Cookie、超时和 Session 失效处理
                   +-> shared/api/csrf.ts（写请求）
```

- View 管理表单、筛选、分页、loading、empty、error 和操作后的重新查询。
- `types.ts` 只定义本模块真实使用的类型；跨模块稳定类型才申请加入 `shared/api/types.ts`。
- `api.ts` 不弹消息、不操作 Router、不保存页面状态，只描述 HTTP 调用。
- 页面局部列表和表单直接调用 API，不为每个服务端列表创建 Pinia Store。
- 只有跨页面共享且需要持续响应的数据才申请 Store；认证身份继续只由 `stores/auth.ts` 管理。



### 模块路由交付

业务开发者可以新增 `src/router/routes/<module>.ts`，但不要修改 `src/router/index.ts`。模块
路由必须使用相对路径，因为它会作为 AppLayout 的子路由接入：

```ts
import type { RouteRecordRaw } from 'vue-router'

export const exampleRoutes: readonly RouteRecordRaw[] = [
  {
    path: 'examples',
    name: 'examples',
    component: () => import('@/views/examples/ExampleListView.vue'),
  },
]
```

PR 描述必须写明导出变量、目标 URL、允许角色和需要显示的菜单项。基座维护者 Review 后统一
在 `src/router/index.ts` 导入并展开路由，并在 `AppLayout.vue` 接入菜单。开发者需要本地预览
时可以临时接入，但不得把对根路由和公共菜单的修改提交到模块 PR。

### 公共文件所有权

以下文件由基座维护者统一修改，模块开发者不得在业务 PR 中直接改动：


| 公共文件                                              | 原因                  |
| ------------------------------------------------- | ------------------- |
| `src/main.ts`, `src/App.vue`                      | 应用启动顺序和根渲染边界        |
| `src/router/index.ts`, `src/router/navigation.ts` | 根路由、认证和权限守卫         |
| `src/layouts/AppLayout.vue`                       | 全局菜单、顶栏和退出入口        |
| `src/shared/api/http.ts`, `csrf.ts`, `errors.ts`  | 所有模块共用的请求和错误语义      |
| `src/shared/api/types.ts`                         | 跨模块公共契约，错误修改会同时影响多人 |
| `src/stores/auth.ts`                              | 全局 Session 身份唯一来源   |
| `src/assets/main.css`, `DESIGN.md`                | 全局设计 token 和页面基线    |
| `README.md`, `ARCHITECTURE.md`, `PROTOTYPE.md`    | 基座状态、边界和页面地图        |
| `../docs/api.md`, `../docs/relationship.md`       | 前后端共享契约，必须双方确认后修改   |


若业务模块确实需要公共变更，在 PR 描述中给出需求、调用方和建议改法，由基座维护者单独
提交或在集成时处理。模块开发者可以修改自己登记的 `views/<module>/`、对应模块路由、测试
和明确分配的 Mock handler，不能修改其他人的模块目录。

### 分支、提交、自动 PR 和 Review

1. 基座 PR 合并后先同步最新 `main`，再创建本文档指定的个人模块分支。
2. 每名队友只在自己的一个模块分支中开发，不修改另一人的模块目录。
3. 开发过程中维护自己的模块开发文档，不并行修改本 README。
4. 提交使用 Conventional Commits，例如 `feat(projects): add project detail page`。
5. 提交前运行相关组件测试；推送前运行 `pnpm lint`、`pnpm test` 和 `pnpm build`。改动 Mock
  handler 时额外运行 `pnpm test:mock`。
6. `git push -u origin <branch>` 后，`.github/workflows/auto-pr.yml` 会为所有非 `main` 分支
  自动创建面向默认分支的 PR，CODEOWNERS 会自动指定 Reviewer。
7. 自动 PR 的初始标题是 `Auto PR: <branch> -> main`；作者需要改成 Conventional Commit
  风格，并补充范围、接口、权限、验证命令和可见页面截图。
8. Review 必须检查 API 契约、ADMIN/USER 与项目关系权限、写请求 CSRF、四种反馈状态、操作后
  数据同步、Console、Network 和测试。公共文件变更没有提前说明时不合并。
9. Review 通过并合并后，其他开发者才同步新的 `main`；不得继续在已经合并的旧分支开发。

如果发生冲突，模块作者只解决自己模块目录内的冲突；根路由、AppLayout、公共请求层和共享
类型的冲突交给基座维护者处理。API 或模型变化先更新共享契约，再改 Mock、后端和前端类型。

## 业务模块分工

主要任务优先完成；完成主要任务后继续实现 Optimal。每项只有在页面、API、状态、权限和相关测试都完成后才能勾选。

### 队友 A：管理与项目协作模块

- 开发分支：`feat/frontend-management`
- 主要目录：`views/admin/`、`views/projects/`、`views/profile/`
- 模块开发文档：`frontend/MANAGEMENT_DEVELOPMENT.md`
- 已有基础：`ProjectListView` 及其类型、API、路由和测试已经完成，可直接继续项目详情。



#### 主要任务

- [x] 完成 ADMIN 用户列表、搜索、筛选和分页。
- [x] 完成用户创建、完整编辑、启停和管理员重置密码。
- [x] 完成项目详情以及项目创建、完整编辑和 owner 转移。
- [x] 完成项目归档、恢复和归档后的永久删除。
- [x] 完成项目成员列表、ADMIN 添加成员和授权用户移除成员。
- [x] 完成项目邀请列表、owner 发起邀请和授权用户取消邀请。
- [x] 完成个人资料修改和当前用户修改密码。
- [x] 为上述页面补充模块路由导出和最小组件测试。



#### Optimal

- [x] 将工作台占位页接入真实 Overview 类型、API 和反馈状态。
- [x] 完成 USER 收到的邀请列表以及接受、拒绝操作。



### 队友 B：任务与内容协作模块

- 开发分支：`feat/frontend-work-content`
- 主要目录：`views/tasks/`、`views/summaries/`、`views/ai/`
- 模块开发文档：`frontend/WORK_CONTENT_DEVELOPMENT.md`
- 已有基础：可复制 `views/projects/` 的类型、API、列表状态和测试模式。



#### 主要任务

- [ ] 完成任务列表的搜索、筛选、分页和进入详情。
- [ ] 完成任务详情以及任务创建、完整编辑和删除。
- [ ] 完成 owner 或 assignee 修改任务状态，并正确展示父子任务关系。
- [ ] 完成任务日志分页、新增进度记录和授权用户删除日志。
- [ ] 完成总结列表的筛选、分页和进入详情。
- [ ] 完成总结详情以及总结创建、完整编辑和删除。
- [ ] 为上述页面补充模块路由导出和最小组件测试。



#### Optimal

- [ ] 完成任务 AI 建议、人工审阅编辑以及提交为普通任务日志的入口。
- [ ] 完成项目 AI 任务计划生成、页面临时编辑和批量导入。



### 模块开发文档

**不需要在并行开发过程中修改本 README**。本 README 由基座维护者在 Review 和合并后
统一更新，避免两个业务 PR 同时产生文档冲突。

在自己的分支维护上面指定的模块开发文档。开发文档至少包含：

- 当前完成的主要任务和 Optimal 勾选项；
- 新增或修改的文件及各自职责；
- 页面、Store、API 和 Axios 的实际调用链；
- 使用的接口、权限判断和重要业务边界；
- 页面、Console、Network、lint、test、test:mock 和 build 验证结果；
- 尚未完成的内容、已知问题以及需要接入的路由和菜单。


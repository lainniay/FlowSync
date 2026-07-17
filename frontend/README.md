# FlowSync Frontend

本文档优先记录前端当前完成情况、现成可复用能力、边界和下一步任务。模块分配暂时只做
占位，等前端基座稳定后再登记负责人。前端队友或 AI 开始编码前，按以下顺序阅读：

1. 根目录 `AGENTS.md`
2. 本文档
3. `../docs/api.md`
4. 涉及数据模型时阅读 `../docs/relationship.md`

不要根据页面、Mock 或后端实现自行推测接口。跨前后端的请求、响应、权限、Session 和
CSRF 约定只以 `../docs/api.md` 为准；联调排障说明也应写入 `../docs/`。

## 当前目标

在 2026-07-20 前完成可供多人并行开发的前端基座。基座完成不等于完成全部前端，而是要让
后续模块能够复用同一套布局、路由、请求、错误处理、类型、设计规范和页面示例。

当前阶段约完成 42%。每完成一项，立即更新本文档的勾选状态和“现成能力”章节。

## 现成能力

| 能力 | 位置 | 状态与用法 |
| --- | --- | --- |
| Vue 应用入口 | `src/main.ts` | 已安装 Pinia、Router；开发环境可启动 MSW |
| 根组件 | `src/App.vue` | 当前只渲染 `RouterView` |
| 路由入口 | `src/router/index.ts` | 当前只有首页，守卫和模块路由待完成 |
| Axios 实例 | `src/shared/api/http.ts` | 已配置 `/api`、10 秒超时、Cookie 凭据 |
| CSRF 请求头 | `src/shared/api/csrf.ts` | 写请求调用 `getCsrfHeaders()`，禁止各模块重复实现 |
| API 错误解析 | `src/shared/api/errors.ts` | 已有 Problem Details 消息和 HTTP 状态判断工具 |
| 公共 API 类型 | `src/shared/api/types.ts` | 已有认证相关类型，后续按 `api.md` 扩充 |
| 认证 API | `src/shared/api/auth.ts` | 已有 CSRF、当前用户、登录和退出请求 |
| 认证状态 | `src/stores/auth.ts` | 已有当前用户、loading、登录和退出 action |
| Mock API | `src/mocks/` | 已有 MSW handlers、种子状态和契约测试，禁止重复造 Mock 层 |
| 认证实验页 | `src/views/HomeView.vue` | 已连接当前用户、登录表单、前端校验和退出操作 |

## 目录职责

```text
src/
  assets/       全局样式、字体和静态资源
  mocks/        MSW handlers、Mock 状态和运行时校验
  router/       根路由、模块路由和导航守卫
  shared/       跨业务模块复用的 API、类型、工具和基础组件
  stores/       Pinia 全局状态；不要存放纯页面局部状态
  views/        路由页面，按业务模块继续分目录
  __tests__/    Vitest、组件测试和 Mock 契约测试
```

## 当前认证调用链

浏览器打开首页后，当前用户请求按以下顺序执行：

```text
1. src/views/HomeView.vue
   onMounted() 调用 authStore.loadCurrentUser()

2. src/stores/auth.ts
   loadCurrentUser() 管理 loading、currentUser、errorMessage，随后调用 getCurrentUser()

3. src/shared/api/auth.ts
   getCurrentUser() 描述具体接口，调用 http.get('/users/me')

4. src/shared/api/http.ts
   Axios 实例补上 baseURL='/api'、超时和 Cookie 配置，发出 GET /api/users/me

5. src/mocks/handlers/auth.ts（仅 Mock 开发模式）
   MSW 拦截请求，从内存状态读取当前用户并返回 JSON

6. 响应沿原路返回
   auth.ts 返回 response.data -> Store 写入 currentUser -> HomeView 自动重新渲染
```

各层边界：

- View 负责展示、输入和触发操作，不直接保存全局登录身份。
- Store 负责跨页面共享的响应式状态；普通页面局部数据不一定需要 Store。
- API module 负责具体 URL、HTTP 方法、请求体和响应类型，不负责页面样式。
- Axios 实例负责所有请求共有的基础配置，不写具体业务页面逻辑。
- MSW 只在开发环境模拟后端；生产环境和真实联调不能依赖 Mock 内存状态。

禁止 API 层导入 View，禁止在多个页面中重复创建 Axios 实例。不要为了所有服务端数据都创建
Pinia Store；只在跨页面共享、登录身份或需要跨组件长期保存时使用 Store。

## 分模块前任务

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
- [ ] 统一处理 401、403、422 和未知网络错误
- [ ] 明确响应拦截器与页面消息的职责，避免重复弹错
- [ ] 为请求基础设施添加最小回归测试

### P0 路由与布局

- [ ] 建立独立 LoginView
- [ ] 建立应用根布局、侧栏、顶栏和内容区
- [ ] 配置登录守卫和未登录重定向
- [ ] 根据 `systemRole` 区分 ADMIN 与 USER 菜单
- [ ] 采用模块路由文件，降低多人修改同一文件的冲突
- [ ] 完成 404 和无权限页面

### P0 设计与标准示例

- [ ] 先完成 ADMIN、USER 低保真页面原型
- [ ] 记录颜色、字号、间距、表格、表单和反馈状态规范
- [ ] 完成一个项目列表纵向示例
- [ ] 示例包含类型、API、查询参数、分页、表格、loading、empty 和 error
- [ ] 示例完成后补组件测试、lint 和 production build

### P0 交接准备

- [ ] 登记所有页面、路由、接口和负责人，避免重复开发
- [ ] 写清新模块应复制的目录和代码模式
- [ ] 将真实联调步骤与常见故障记录到 `../docs/`
- [ ] 确认 `pnpm lint`、`pnpm build`、`pnpm test`、`pnpm test:mock` 通过
- [ ] 检查未提交文件，不提交 `.env`、`dist` 或本地测试产物

## 后续模块清单

本表目前只用于记录未来前端范围，不代表已经分配。基座稳定后再登记负责人和并行开发边界。

| 模块 | 主要页面/能力 | 负责人 | 状态 |
| --- | --- | --- | --- |
| 系统管理 | 用户列表、创建、编辑、停用、密码重置 | 待定 | 未开始 |
| 项目管理 | 项目列表、详情、创建、编辑、归档、删除 | 待定 | 未开始 |
| 成员与邀请 | 成员、邀请发起、处理邀请 | 待定 | 未开始 |
| 任务管理 | 任务列表、详情、编辑、状态和子任务 | 待定 | 未开始 |
| 任务日志 | 进度记录、日志列表和删除 | 待定 | 未开始 |
| 总结 | 阶段总结、最终总结和筛选 | 待定 | 未开始 |
| 工作台 | 统计、任务状态、最近活动 | 待定 | 未开始 |
| AI 辅助 | 建议、临时计划、人工编辑和导入 | 待定 | 未开始 |
| 个人中心 | 资料修改和密码修改 | 待定 | 未开始 |

## 前端实现规则

- API 的 JSON ID 使用 `string`，枚举值必须与 `../docs/api.md` 完全一致。
- 后端直接返回资源或分页对象，不要假设存在额外的 `data/success/message` 业务包装。
- 身份来自 HttpOnly Session Cookie；不要在 localStorage 或 Pinia 中保存 Token。
- 不向后端提交 `currentUserId`、`creatorId` 或 `operatorId` 证明身份。
- 写请求遵守 CSRF 约定；资源更新遵守完整 `PUT` 规则。
- ADMIN 与 USER 是系统角色；项目 owner/member 是项目关系，不能混为一谈。
- 每个数据页面必须处理 loading、success、empty 和 error 状态。
- 新增、修改、删除成功后必须重新查询或可靠地同步页面数据。
- AI 结果是临时数据，必须由有权限的 USER 人工确认后通过普通写接口提交。
- 优先复用现有 API、类型、Store、Mock handler 和基础组件，禁止平行实现第二套。

## 开发与验证

从仓库根目录启动前端 Mock 开发：

```powershell
mise run frontend
```

前端目录中的常用验证命令：

```powershell
pnpm lint
pnpm build
pnpm test
pnpm test:mock
```

每完成一个功能，至少检查浏览器页面、Console、Network、相关测试和 production build。

最近一次基础验证（2026-07-18）：`pnpm build` 通过；Mock 5 个测试文件、89 个测试通过；
Oxlint 和 ESLint 只读检查通过。

## 更新本文档

提交功能代码时同步完成以下动作：

1. 勾选或新增对应任务。
2. 在“现成能力”中登记新增的公共文件及用法。
3. 在任务池登记页面、路由和负责人。
4. 跨前后端契约发生变化时先更新 `../docs/api.md`，再更新实现和类型。
5. 尚未具备前置条件的任务写明依赖，不用占位文件伪装成已完成。

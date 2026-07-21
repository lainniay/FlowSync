# 任务 A 模块开发文档（管理与项目协作）

开发分支：`feat/frontend-management`

## 进度

### 主要任务

- [x] ADMIN 用户列表、搜索、筛选和分页
- [x] 用户创建、完整编辑、启停和管理员重置密码
- [x] 项目详情以及项目创建、完整编辑和 owner 转移
- [x] 项目归档、恢复和归档后的永久删除
- [x] 项目成员列表、ADMIN 添加成员和授权用户移除成员
- [x] 项目邀请列表、owner 发起邀请和授权用户取消邀请
- [x] 个人资料修改和当前用户修改密码
- [x] 为上述页面补充模块路由导出和最小组件测试

### Optimal

- [x] 工作台占位页接入真实 Overview 类型、API 和反馈状态
- [x] USER 收到的邀请列表以及接受、拒绝操作

## 已完成模块

### 1. 个人中心 (`/profile`)

- `src/views/profile/*`
- `src/router/routes/profile.ts`

### 2. ADMIN 用户管理 (`/admin/users`)

- `src/views/admin/*`
- `src/router/routes/admin.ts`

### 3. 项目协作 (`/projects`, `/projects/:projectId`)

- `src/views/projects/*`（含详情、成员、邀请）
- `src/router/routes/projects.ts`

### 4. 工作台 (`/overview`)

| 文件 | 职责 |
|------|------|
| `src/views/overview/types.ts` | Overview 响应类型 |
| `src/views/overview/api.ts` | `GET /overview` |
| `src/views/overview/OverviewView.vue` | 统计卡片、任务分布、最近活动、项目筛选 |
| `src/router/routes/overview.ts` | 导出 `overviewRoutes` |

- 替换原 `HomeView.vue` 占位页
- 接口：`GET /overview?projectId=`

### 5. 收到的邀请 (`/invitations`)

| 文件 | 职责 |
|------|------|
| `src/views/invitations/types.ts` | 邀请列表与响应类型 |
| `src/views/invitations/api.ts` | `GET /project-invitations`、`PUT /project-invitations/{id}` |
| `src/views/invitations/InvitationListView.vue` | 列表、筛选、接受/拒绝 |
| `src/router/routes/invitations.ts` | 导出 `invitationRoutes`，`meta.roles: ['USER']` |

- USER 侧栏「收到的邀请」已启用
- ADMIN 访问 `/invitations` 会跳转 `/403`

## 验证（2026-07-20）

- `pnpm lint` 通过
- `pnpm test`：15 文件、124 测试通过
- `pnpm build` 通过

## 浏览器验证建议

### 工作台

1. 登录 `zhangsan` / `user1234` → **工作台**
2. 查看统计卡片、任务状态分布、最近活动
3. 切换项目筛选，确认数据刷新

### 收到的邀请

1. 登录 `wangwu` / `user1234`（或被邀请账号）
2. 侧栏 **收到的邀请**
3. 对待处理邀请执行接受或拒绝

## 任务 A 状态

**主要任务与 Optimal 均已完成后端 Mock 可用的前端实现。**

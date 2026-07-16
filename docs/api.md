# FlowSync 前后端 API 契约

本文档定义 FlowSync 前端与后端之间的 HTTP 接口约定。前端、后端和 Mock 均以本文档为准；数据库实体、字段约束和外键关系以 `docs/relationship.md` 为准。若本文档与 `docs/FlowSync.pdf` 不一致，以本文档和 `docs/relationship.md` 中已经确认的设计为准。

开始实现接口前，请先阅读“通用约定”和“公共数据结构”，再查阅具体接口。

## 1. 通用约定

### 1.1 基础地址与数据格式

- 所有接口路径均以 `/api` 开头。例如本文写作 `POST /auth/login`，实际请求地址是 `POST /api/auth/login`。
- 请求和响应使用 UTF-8 编码。
- JSON 字段使用 `camelCase`。
- JSON 中的 ID 一律使用字符串，例如 `"id": "101"`，避免 JavaScript 大整数精度丢失。
- 日期使用 `YYYY-MM-DD`，例如 `2026-07-13`。
- 日期时间使用 ISO 8601 UTC 格式，例如 `2026-07-13T08:30:00Z`。
- 后端直接返回资源或分页对象，不额外包裹 `data`、`success` 或 `message`。

### 1.2 请求头、Session 与 CSRF

除登录和获取 CSRF Token 外，接口都要求用户已经登录。后端使用 Spring Security Session，并通过 HttpOnly Cookie 保存会话。

发送 JSON 时使用以下请求头：

```http
Content-Type: application/json
Accept: application/json
```

执行 `POST`、`PUT` 或 `DELETE` 前，客户端先调用 `GET /auth/csrf`，然后把响应中的 Token 放入指定请求头：

```http
X-CSRF-TOKEN: 4cfe2f83-...
```

CSRF Token 获取示例：

```http
GET /api/auth/csrf HTTP/1.1
Accept: application/json
```

```json
{
  "token": "4cfe2f83-0a4b-4a24-8736-f0f735c1a642",
  "headerName": "X-CSRF-TOKEN"
}
```

响应字段：

| 响应字段 | 类型 | 可为 null | 说明 |
| --- | --- | --- | --- |
| `token` | string | 否 | CSRF Token |
| `headerName` | string | 否 | 后续写请求携带 Token 的请求头名称 |

客户端不得提交 `currentUserId`、`creatorId` 或 `operatorId` 来证明身份。服务端必须从当前 Session 中取得用户身份。

### 1.3 PUT 更新规则

资源更新统一使用 `PUT`。请求体必须包含该接口列出的全部可编辑字段，即使只修改其中一个字段，也要发送修改后的完整对象。

- 可空字段没有值时必须显式发送 `null`。
- 缺少必填字段，或对不可空字段发送 `null`，返回 `422 VALIDATION_ERROR`。
- 响应返回更新后的完整资源 JSON。
- `createdAt`、`updatedAt`、`owner` 等只读字段不放入更新请求体。

例如只修改项目名称时，仍需发送全部可编辑字段：

```json
{
  "name": "新的项目名称",
  "description": null,
  "status": "IN_PROGRESS",
  "priority": "HIGH",
  "startDate": "2026-07-01",
  "endDate": "2026-08-31"
}
```

### 1.4 分页与排序

支持分页的接口使用以下查询参数：

| 参数 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | `0` | 页码，从 0 开始 |
| `size` | integer | `20` | 每页数量，范围 1 到 100 |
| `sort` | string | `createdAt,desc` | `字段,asc` 或 `字段,desc` |

分页响应统一为：

| 响应字段 | 类型 | 可为 null | 说明 |
| --- | --- | --- | --- |
| `items` | array | 否 | 当前页资源数组，元素类型由具体接口决定 |
| `page` | integer | 否 | 当前页码，从 0 开始 |
| `size` | integer | 否 | 当前页容量 |
| `totalElements` | integer | 否 | 符合条件的资源总数 |
| `totalPages` | integer | 否 | 总页数 |

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

未知的查询参数值、排序字段或排序方向返回 `422 VALIDATION_ERROR`。

### 1.5 错误响应

错误使用 RFC 9457 Problem Details，响应头为：

```http
Content-Type: application/problem+json
```

响应结构：

| 响应字段 | 类型 | 可为 null | 说明 |
| --- | --- | --- | --- |
| `type` | string | 否 | 问题类型 URI；无专用 URI 时为 `about:blank` |
| `title` | string | 否 | 错误标题 |
| `status` | integer | 否 | HTTP 状态码 |
| `detail` | string | 否 | 错误说明 |
| `instance` | string | 否 | 发生错误的请求路径 |
| `code` | string | 否 | 稳定业务错误码 |
| `errors` | array | 否 | 字段错误数组；没有字段错误时为空数组 |
| `errors[].field` | string | 否 | 请求字段路径 |
| `errors[].code` | string | 否 | 字段错误码 |
| `errors[].message` | string | 否 | 字段错误说明 |

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 422,
  "detail": "One or more fields are invalid.",
  "instance": "/api/projects",
  "code": "VALIDATION_ERROR",
  "errors": [
    {
      "field": "name",
      "code": "NotBlank",
      "message": "name must not be blank"
    }
  ]
}
```

`errors` 用于字段级错误。批量请求必须指出准确下标，例如 `userIds[1]`、`items[2].title`。批量写入在任意一项失败时整体回滚。

无法解析的 JSON、数组 JSON 或其他非对象 JSON 返回 `400 BAD_REQUEST`，不得返回普通文本或 `500`。

Spring MVC 在路由和内容协商阶段产生的错误也使用相同结构。`405`、`406`、`413` 和
`415` 分别使用 `METHOD_NOT_ALLOWED`、`NOT_ACCEPTABLE`、`PAYLOAD_TOO_LARGE` 和
`UNSUPPORTED_MEDIA_TYPE`。Spring MVC 尚未识别的状态不得把默认错误体直接暴露给客户端，
统一降级为 `500 INTERNAL_SERVER_ERROR`。Spring Security 的 `401`、`403` 和 CSRF 错误
使用同一个 Problem Details 结构。

### 1.6 HTTP 状态码

| 状态码 | 含义 | 常见场景 |
| --- | --- | --- |
| `200 OK` | 请求成功 | 查询、更新、归档、恢复 |
| `201 Created` | 创建成功 | 创建用户、项目、任务、邀请等 |
| `204 No Content` | 成功且无响应体 | 登出、改密码、删除 |
| `400 Bad Request` | 请求无法解析 | 非法 JSON、JSON 不是对象 |
| `401 Unauthorized` | 未登录或凭据错误 | Session 失效、登录失败 |
| `403 Forbidden` | 已登录但无权限 | 普通成员管理项目 |
| `404 Not Found` | 资源不存在或当前用户不可见 | ID 不存在 |
| `405 Method Not Allowed` | 请求方法不受支持 | 对只读接口发送 PUT |
| `406 Not Acceptable` | 无法生成客户端要求的响应格式 | 请求不支持的 Accept 类型 |
| `409 Conflict` | 与当前业务状态冲突 | 重复成员、资源仍被引用 |
| `413 Content Too Large` | 请求体超过大小限制 | 上传内容过大 |
| `415 Unsupported Media Type` | 请求体媒体类型不受支持 | 使用 text/plain 提交 JSON 接口 |
| `422 Unprocessable Content` | 参数或字段校验失败 | 枚举、日期、必填字段错误 |
| `429 Too Many Requests` | 请求过于频繁 | AI 接口限流 |
| `500 Internal Server Error` | 未预期的服务端错误 | 服务端缺陷 |
| `502 Bad Gateway` | 上游服务失败 | AI Provider 调用失败 |
| `503 Service Unavailable` | 服务暂时不可用 | 数据库或依赖服务不可用 |

常用业务错误码包括：

| `code` | HTTP 状态 | 含义 |
| --- | --- | --- |
| `BAD_REQUEST` | 400 | 请求体无法解析 |
| `UNAUTHORIZED` | 401 | 尚未登录或 Session 已失效 |
| `INVALID_CREDENTIALS` | 401 | 用户名或密码错误 |
| `FORBIDDEN` | 403 | 当前用户没有操作权限 |
| `CSRF_INVALID` | 403 | CSRF Token 缺失或无效 |
| `NOT_FOUND` | 404 | 资源不存在 |
| `METHOD_NOT_ALLOWED` | 405 | 请求方法不受支持 |
| `NOT_ACCEPTABLE` | 406 | 无法生成客户端要求的响应格式 |
| `PAYLOAD_TOO_LARGE` | 413 | 请求体超过大小限制 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | 请求体媒体类型不受支持 |
| `VALIDATION_ERROR` | 422 | 字段或查询参数校验失败 |
| `CURRENT_PASSWORD_INCORRECT` | 422 | 当前密码不正确 |
| `USERNAME_ALREADY_EXISTS` | 409 | 用户名已存在 |
| `LAST_ADMIN_REQUIRED` | 409 | 操作会导致系统没有可用管理员 |
| `USER_OWNS_PROJECT` | 409 | 用户仍是项目 owner，不能停用 |
| `USER_HAS_ACTIVE_TASKS` | 409 | 用户仍负责未完成任务，不能停用 |
| `USER_HAS_PROJECT_MEMBERSHIP` | 409 | 用户仍有项目成员关系或待处理邀请，不能改为 ADMIN |
| `PROJECT_ARCHIVED` | 409 | 已归档项目不允许该操作 |
| `PROJECT_NOT_ARCHIVED` | 409 | 项目尚未归档，不能永久删除 |
| `MEMBER_ALREADY_EXISTS` | 409 | 用户已经是项目成员 |
| `MEMBER_HAS_ACTIVE_TASKS` | 409 | 成员仍负责未完成任务，不能移除 |
| `INVITATION_ALREADY_PENDING` | 409 | 已有待处理邀请 |
| `INVALID_INVITATION_STATE` | 409 | 邀请状态不能进行该转换 |
| `RESOURCE_IN_USE` | 409 | 资源仍被其他记录引用 |
| `RATE_LIMITED` | 429 | AI 请求超过限制 |
| `INTERNAL_SERVER_ERROR` | 500 | 未预期的服务端错误 |
| `AI_PROVIDER_ERROR` | 502 | AI Provider 调用失败 |
| `SERVICE_UNAVAILABLE` | 503 | 数据库或依赖服务暂时不可用 |

### 1.7 枚举

API 只接受以下大写枚举值：

| 类型 | 可选值 |
| --- | --- |
| `SystemRole` | `ADMIN`, `USER` |
| `ProjectStatus` | `NOT_STARTED`, `IN_PROGRESS`, `COMPLETED` |
| `TaskStatus` | `NOT_STARTED`, `IN_PROGRESS`, `BLOCKED`, `COMPLETED`, `CANCELLED` |
| `Priority` | `LOW`, `MEDIUM`, `HIGH` |
| `InvitationStatus` | `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED` |
| `SummaryType` | `STAGE`, `FINAL` |

`ADMIN` 是独立的系统管理账号，不具有项目成员身份。管理员需要参与项目工作时，必须使用另一个 `systemRole=USER` 的账号。

## 2. 公共响应结构

本节中的结构是 API DTO，不代表需要额外创建数据库表。例如 `UserBrief`、`Project.taskStats` 和 `AiTaskPlan` 都是响应结构，不是持久化实体。

### 2.1 UserBrief

用于任务负责人、日志操作人等嵌套位置：

| 响应字段 | 类型 | 可为 null | 说明 |
| --- | --- | --- | --- |
| `id` | string | 否 | 用户 ID |
| `displayName` | string | 否 | 用户显示名称 |

```json
{
  "id": "2",
  "displayName": "李明"
}
```

### 2.2 User

| 响应字段 | 类型 | 可为 null | 说明 |
| --- | --- | --- | --- |
| `id` | string | 否 | 用户 ID |
| `username` | string | 否 | 登录用户名 |
| `displayName` | string | 否 | 显示名称 |
| `phone` | string | 是 | 手机号 |
| `email` | string | 是 | 邮箱 |
| `systemRole` | SystemRole | 否 | 系统角色 |
| `active` | boolean | 否 | 是否启用 |
| `createdAt` | datetime | 否 | 创建时间 |
| `updatedAt` | datetime | 否 | 最后更新时间 |

```json
{
  "id": "2",
  "username": "liming",
  "displayName": "李明",
  "phone": "13800138000",
  "email": "liming@example.com",
  "systemRole": "USER",
  "active": true,
  "createdAt": "2026-07-01T08:00:00Z",
  "updatedAt": "2026-07-01T08:00:00Z"
}
```

密码和 `passwordHash` 永远不能出现在响应中。普通用户读取自己的资料时可以看到上述结构；用户管理接口仅限管理员。

### 2.3 Project

| 响应字段 | 类型 | 可为 null | 说明 |
| --- | --- | --- | --- |
| `id` | string | 否 | 项目 ID |
| `owner` | UserBrief | 否 | 当前项目 owner |
| `name` | string | 否 | 项目名称 |
| `description` | string | 是 | 项目说明 |
| `status` | ProjectStatus | 否 | 项目状态 |
| `priority` | Priority | 否 | 优先级 |
| `startDate` | date | 是 | 开始日期 |
| `endDate` | date | 是 | 结束日期 |
| `archivedAt` | datetime | 是 | 归档时间；未归档时为 null |
| `memberCount` | integer | 否 | 当前成员数量 |
| `taskStats.total` | integer | 否 | 任务总数 |
| `taskStats.completed` | integer | 否 | 已完成任务数 |
| `createdAt` | datetime | 否 | 创建时间 |
| `updatedAt` | datetime | 否 | 最后更新时间 |

```json
{
  "id": "101",
  "owner": {
    "id": "1",
    "displayName": "王强"
  },
  "name": "FlowSync 开发",
  "description": "课程小组项目",
  "status": "IN_PROGRESS",
  "priority": "HIGH",
  "startDate": "2026-07-01",
  "endDate": "2026-08-31",
  "archivedAt": null,
  "memberCount": 4,
  "taskStats": {
    "total": 12,
    "completed": 5
  },
  "createdAt": "2026-07-01T08:00:00Z",
  "updatedAt": "2026-07-13T08:30:00Z"
}
```

`archivedAt=null` 表示未归档。归档不是新的 `status` 枚举值。

### 2.4 ProjectMember

| 响应字段 | 类型 | 可为 null | 说明 |
| --- | --- | --- | --- |
| `user` | UserBrief | 否 | 成员信息 |
| `joinedAt` | datetime | 否 | 加入时间 |

```json
{
  "user": {
    "id": "2",
    "displayName": "李明"
  },
  "joinedAt": "2026-07-02T09:00:00Z"
}
```

### 2.5 ProjectInvitation

| 响应字段 | 类型 | 可为 null | 说明 |
| --- | --- | --- | --- |
| `id` | string | 否 | 邀请 ID |
| `project.id` | string | 否 | 项目 ID |
| `project.name` | string | 否 | 项目名称 |
| `invitee` | UserBrief | 否 | 被邀请人 |
| `invitedBy` | UserBrief | 否 | 邀请人 |
| `status` | InvitationStatus | 否 | 邀请状态 |
| `createdAt` | datetime | 否 | 创建时间 |
| `respondedAt` | datetime | 是 | 接受、拒绝或取消时间 |

```json
{
  "id": "301",
  "project": {
    "id": "101",
    "name": "FlowSync 开发"
  },
  "invitee": {
    "id": "2",
    "displayName": "李明"
  },
  "invitedBy": {
    "id": "1",
    "displayName": "王强"
  },
  "status": "PENDING",
  "createdAt": "2026-07-02T08:30:00Z",
  "respondedAt": null
}
```

### 2.6 Task

| 响应字段 | 类型 | 可为 null | 说明 |
| --- | --- | --- | --- |
| `id` | string | 否 | 任务 ID |
| `projectId` | string | 否 | 所属项目 ID |
| `parentId` | string | 是 | 父任务 ID |
| `assignee` | UserBrief | 是 | 当前负责人 |
| `creator` | UserBrief | 否 | 创建者 |
| `title` | string | 否 | 标题 |
| `description` | string | 是 | 描述 |
| `status` | TaskStatus | 否 | 状态 |
| `priority` | Priority | 否 | 优先级 |
| `progressPercent` | integer | 否 | 最新进度，范围 0 到 100 |
| `dueDate` | date | 是 | 截止日期 |
| `createdAt` | datetime | 否 | 创建时间 |
| `updatedAt` | datetime | 否 | 最后更新时间 |

```json
{
  "id": "501",
  "projectId": "101",
  "parentId": null,
  "assignee": {
    "id": "2",
    "displayName": "李明"
  },
  "creator": {
    "id": "1",
    "displayName": "王强"
  },
  "title": "完成登录页面",
  "description": "实现 Session 登录流程",
  "status": "IN_PROGRESS",
  "priority": "HIGH",
  "progressPercent": 40,
  "dueDate": "2026-07-20",
  "createdAt": "2026-07-03T08:00:00Z",
  "updatedAt": "2026-07-13T08:30:00Z"
}
```

`progressPercent` 是根据最新 TaskLog 得到的展示字段，没有日志时为 `0`。

### 2.7 TaskLog

| 响应字段 | 类型 | 可为 null | 说明 |
| --- | --- | --- | --- |
| `id` | string | 否 | 日志 ID |
| `taskId` | string | 否 | 任务 ID |
| `operator` | UserBrief | 否 | 操作人 |
| `progressPercent` | integer | 否 | 本次进度，范围 0 到 100 |
| `content` | string | 否 | 日志内容 |
| `createdAt` | datetime | 否 | 创建时间 |

```json
{
  "id": "601",
  "taskId": "501",
  "operator": {
    "id": "2",
    "displayName": "李明"
  },
  "progressPercent": 40,
  "content": "登录表单和校验已完成",
  "createdAt": "2026-07-13T08:30:00Z"
}
```

### 2.8 Summary

| 响应字段 | 类型 | 可为 null | 说明 |
| --- | --- | --- | --- |
| `id` | string | 否 | 总结 ID |
| `projectId` | string | 否 | 项目 ID |
| `taskId` | string | 是 | 关联任务 ID；项目级总结为 null |
| `createdBy` | UserBrief | 否 | 创建者 |
| `type` | SummaryType | 否 | 总结类型 |
| `content` | string | 否 | 总结内容 |
| `createdAt` | datetime | 否 | 创建时间 |
| `updatedAt` | datetime | 否 | 最后更新时间 |

```json
{
  "id": "701",
  "projectId": "101",
  "taskId": null,
  "createdBy": {
    "id": "2",
    "displayName": "李明"
  },
  "type": "STAGE",
  "content": "本周已完成登录和项目列表功能。",
  "createdAt": "2026-07-13T09:00:00Z",
  "updatedAt": "2026-07-13T09:00:00Z"
}
```

## 3. 接口总览

权限缩写：`登录用户` 表示任意有效用户；`成员` 表示项目成员；`owner` 表示项目 owner；`负责人` 表示当前 task assignee；`ADMIN` 表示系统管理员。

“请求字段”包含路径参数、查询参数和 JSON 请求体字段；具体请求体的类型与校验规则在后续章节展开。“响应字段”只描述成功响应；User、Project、Task 等命名响应的完整字段见第 2 节，错误响应字段见 1.5 节。

| 模块 | 方法与路径 | 请求字段 | 权限 | 响应字段 |
| --- | --- | --- | --- | --- |
| Auth | `GET /auth/csrf` | 无 | 公开 | 200：`token`, `headerName` |
| Auth | `POST /auth/login` | `username`, `password` | 公开 | 200：User |
| Auth | `POST /auth/logout` | 无 | 登录用户 | 204：无响应体 |
| Profile | `GET /users/me` | 无 | 登录用户 | 200：User |
| Profile | `PUT /users/me` | `displayName`, `phone`, `email` | 登录用户 | 200：User |
| Profile | `PUT /users/me/password` | `currentPassword`, `newPassword` | 登录用户 | 204：无响应体 |
| Users | `GET /users` | 查询参数见 5.1 | ADMIN | 200：User 分页 |
| Users | `POST /users` | 见 5.2 | ADMIN | 201：User |
| Users | `GET /users/{userId}` | 路径参数 `userId` | ADMIN | 200：User |
| Users | `PUT /users/{userId}` | 路径参数 `userId`；请求体见 5.3 | ADMIN | 200：User |
| Users | `PUT /users/{userId}/password` | 路径参数 `userId`；`newPassword` | ADMIN | 204：无响应体 |
| Projects | `GET /projects` | 查询参数见 6.1 | 登录用户 | 200：Project 分页 |
| Projects | `POST /projects` | 见 6.2 | 登录用户 | 201：Project |
| Projects | `GET /projects/{projectId}` | 路径参数 `projectId` | 成员或 ADMIN | 200：Project |
| Projects | `PUT /projects/{projectId}` | 路径参数 `projectId`；请求体见 6.3 | owner 或 ADMIN | 200：Project |
| Projects | `PUT /projects/{projectId}/owner` | 路径参数 `projectId`；`ownerId` | owner 或 ADMIN | 200：Project |
| Projects | `PUT /projects/{projectId}/archive` | 路径参数 `projectId` | owner 或 ADMIN | 200：Project |
| Projects | `DELETE /projects/{projectId}/archive` | 路径参数 `projectId` | owner 或 ADMIN | 200：Project |
| Projects | `DELETE /projects/{projectId}` | 路径参数 `projectId`，项目必须已归档 | owner 或 ADMIN | 204：无响应体 |
| Members | `GET /projects/{projectId}/members` | 路径参数 `projectId` | 成员或 ADMIN | 200：ProjectMember[] |
| Members | `POST /projects/{projectId}/members` | 路径参数 `projectId`；`userIds` | ADMIN | 201：ProjectMember[] |
| Members | `DELETE /projects/{projectId}/members/{userId}` | 路径参数 `projectId`, `userId` | owner 或 ADMIN | 204：无响应体 |
| Invitations | `GET /projects/{projectId}/invitations` | 路径参数 `projectId` | owner 或 ADMIN | 200：ProjectInvitation[] |
| Invitations | `POST /projects/{projectId}/invitations` | 路径参数 `projectId`；`userIds` | owner | 201：ProjectInvitation[] |
| Invitations | `DELETE /projects/{projectId}/invitations/{invitationId}` | 路径参数 `projectId`, `invitationId` | owner 或 ADMIN | 204：无响应体 |
| Invitations | `GET /project-invitations` | 查询参数 `status` | 登录用户 | 200：ProjectInvitation[] |
| Invitations | `PUT /project-invitations/{invitationId}` | 路径参数 `invitationId`；`status` | 被邀请人 | 200：ProjectInvitation |
| Tasks | `GET /tasks` | 查询参数见 7.1 | 登录用户 | 200：Task 分页 |
| Tasks | `POST /tasks` | 见 7.2 | owner | 201：Task |
| Tasks | `GET /tasks/{taskId}` | 路径参数 `taskId` | 成员或 ADMIN | 200：Task |
| Tasks | `PUT /tasks/{taskId}` | 路径参数 `taskId`；请求体见 7.3 | owner | 200：Task |
| Tasks | `PUT /tasks/{taskId}/status` | 路径参数 `taskId`；`status` | owner 或负责人 | 200：Task |
| Tasks | `DELETE /tasks/{taskId}` | 路径参数 `taskId` | owner | 204：无响应体 |
| Logs | `GET /tasks/{taskId}/logs` | 路径参数 `taskId`；分页参数 | 成员或 ADMIN | 200：TaskLog 分页 |
| Logs | `POST /tasks/{taskId}/logs` | 路径参数 `taskId`；`progressPercent`, `content` | owner 或负责人 | 201：TaskLog |
| Logs | `DELETE /tasks/{taskId}/logs/{logId}` | 路径参数 `taskId`, `logId` | owner 或日志创建者 | 204：无响应体 |
| Summaries | `GET /summaries` | 查询参数见 8.1 | 登录用户 | 200：Summary 分页 |
| Summaries | `POST /summaries` | `projectId`, `taskId`, `type`, `content` | 成员 | 201：Summary |
| Summaries | `GET /summaries/{summaryId}` | 路径参数 `summaryId` | 成员或 ADMIN | 200：Summary |
| Summaries | `PUT /summaries/{summaryId}` | 路径参数 `summaryId`；`type`, `content` | 创建者或 owner | 200：Summary |
| Summaries | `DELETE /summaries/{summaryId}` | 路径参数 `summaryId` | 创建者或 owner | 204：无响应体 |
| Overview | `GET /overview` | 查询参数 `projectId` | 登录用户 | 200：Overview |
| AI | `POST /ai/task-suggestions` | `taskId`, `focus` | owner 或负责人 | 200：`suggestion`, `generatedAt` |
| AI | `POST /projects/{projectId}/ai/task-plans` | 路径参数 `projectId`；见 9.2 | owner | 200：AiTaskPlan |
| AI | `POST /projects/{projectId}/ai/task-plans/imports` | 路径参数 `projectId`；`items` | owner | 201：`importedCount`, `tasks` |

## 4. 认证与个人资料

### 4.1 登录

`POST /auth/login`

请求体：

```json
{
  "username": "admin",
  "password": "change-me"
}
```

| 请求字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `username` | string | 是 | 1 到 50 个字符 |
| `password` | string | 是 | 不允许为空；UTF-8 编码不得超过 72 字节，超出时按凭据错误处理 |

成功返回 `200` 和当前 User，同时由后端写入 HttpOnly Session Cookie。用户名或密码错误统一返回 `401 INVALID_CREDENTIALS`，不得暴露用户是否存在。

### 4.2 登出

`POST /auth/logout`

无请求体，成功返回 `204`，后端使当前 Session 失效。

### 4.3 查询当前用户

`GET /users/me`

成功返回 `200 User`。

### 4.4 修改个人资料

`PUT /users/me`

| 请求字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `displayName` | string | 是 | 1 到 50 |
| `phone` | string/null | 是 | 最长 20 |
| `email` | string/null | 是 | 最长 100，合法邮箱 |

```json
{
  "displayName": "李明",
  "phone": "13800138000",
  "email": "liming@example.com"
}
```

三个字段都必须出现；`phone` 和 `email` 可以为 `null`。`displayName` 最长 50，`phone` 最长 20，`email` 最长 100 且必须是合法邮箱格式。成功返回 `200 User`。

### 4.5 修改自己的密码

`PUT /users/me/password`

| 请求字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `currentPassword` | string | 是 | 当前密码 |
| `newPassword` | string | 是 | 12 到 64 个 Unicode 字符，且 UTF-8 编码不超过 72 字节 |

```json
{
  "currentPassword": "old-password",
  "newPassword": "new-password"
}
```

新密码不做首尾空白裁剪，也不强制大小写、数字或特殊字符组合。成功返回 `204`，并使该用户
已有 Session 全部失效，用户需要重新登录。当前密码不正确返回
`422 CURRENT_PASSWORD_INCORRECT`。

## 5. 用户管理

本节接口仅限 `ADMIN`。系统首次启动且不存在有效管理员时，后端使用环境变量
`DEFAULT_ADMIN_USERNAME` 和 `DEFAULT_ADMIN_PASSWORD` 创建首个管理员；初始密码必须符合
4.5 节的密码安全策略。

### 5.1 查询用户列表

`GET /users`

| 查询参数 | 类型 | 说明 |
| --- | --- | --- |
| `q` | string | 按 username 或 displayName 模糊查询 |
| `systemRole` | SystemRole | 按系统角色过滤 |
| `active` | boolean | 默认 `true` |
| `page`, `size`, `sort` | - | 支持排序字段：`createdAt`, `username`, `displayName` |

示例：`GET /api/users?q=li&systemRole=USER&active=true&page=0&size=20&sort=createdAt,desc`

成功返回 `200` 和 User 分页对象。

### 5.2 创建用户

`POST /users`

```json
{
  "username": "liming",
  "initialPassword": "temporary-password",
  "displayName": "李明",
  "systemRole": "USER",
  "phone": null,
  "email": "liming@example.com"
}
```

| 请求字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `username` | string | 是 | 1 到 50，唯一 |
| `initialPassword` | string | 是 | 符合 4.5 节的密码安全策略，并由后端加密存储 |
| `displayName` | string | 是 | 1 到 50 |
| `systemRole` | SystemRole | 是 | `ADMIN` 或 `USER` |
| `phone` | string/null | 是 | 最长 20 |
| `email` | string/null | 是 | 最长 100，合法邮箱 |

成功返回 `201 User`。

### 5.3 查询、修改用户

- `GET /users/{userId}`：返回 `200 User`。
- `PUT /users/{userId}`：返回修改后的 `200 User`。

修改请求体必须包含全部可编辑字段：

| 请求字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `displayName` | string | 是 | 1 到 50 |
| `phone` | string/null | 是 | 最长 20 |
| `email` | string/null | 是 | 最长 100，合法邮箱 |
| `systemRole` | SystemRole | 是 | - |
| `active` | boolean | 是 | - |

```json
{
  "displayName": "李明",
  "phone": null,
  "email": "liming@example.com",
  "systemRole": "USER",
  "active": true
}
```

系统必须始终保留至少一个 `active=true` 的 `ADMIN`。用户仍是项目 owner 或仍负责未完成任务时不能停用。把 USER 修改为 ADMIN 前，必须先转移其项目、移除其成员关系并处理其待处理邀请。
角色变化或停用成功后，后端必须使该用户已有 Session 全部失效。

### 5.4 管理员重置密码

`PUT /users/{userId}/password`

| 请求字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `newPassword` | string | 是 | 符合 4.5 节的密码安全策略 |

```json
{
  "newPassword": "new-temporary-password"
}
```

成功返回 `204`，并使目标用户已有 Session 全部失效。

## 6. 项目、成员与邀请

### 6.1 查询项目列表

`GET /projects`

普通用户只能看到自己参与的项目；`ADMIN` 可以看到全部项目。

| 查询参数 | 类型 | 说明 |
| --- | --- | --- |
| `q` | string | 按项目名称模糊查询 |
| `status` | ProjectStatus | 项目状态 |
| `ownerId` | string | owner ID |
| `archived` | boolean | 默认 `false`；`true` 只查归档项目 |
| `page`, `size`, `sort` | - | 支持 `createdAt`, `updatedAt`, `name`, `startDate`, `endDate`, `priority` |

成功返回 `200` 和 Project 分页对象。

### 6.2 创建项目

`POST /projects`

```json
{
  "name": "FlowSync 开发",
  "description": "课程小组项目",
  "status": "NOT_STARTED",
  "priority": "HIGH",
  "startDate": "2026-07-01",
  "endDate": "2026-08-31",
  "ownerId": null
}
```

| 请求字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `name` | string | 是 | 1 到 100 |
| `description` | string/null | 是 | 最长 2000 |
| `status` | ProjectStatus | 是 | - |
| `priority` | Priority | 是 | - |
| `startDate` | date/null | 是 | 合法日历日期 |
| `endDate` | date/null | 是 | 不早于 startDate |
| `ownerId` | string/null | 否 | ADMIN 必须指定有效 USER；普通 USER 创建时发送 null |

普通 USER 创建项目时自动成为 owner。ADMIN 创建项目时必须通过 `ownerId` 指定一个 `active=true`、`systemRole=USER` 的账号，ADMIN 自身不能成为 owner 或成员。后端必须在同一事务内创建项目并把 owner 加入 `ProjectMember`。成功返回 `201 Project`。

### 6.3 查询和修改项目

- `GET /projects/{projectId}`：项目成员或 ADMIN 可访问，返回 `200 Project`。
- `PUT /projects/{projectId}`：owner 或 ADMIN 可修改，返回 `200 Project`。

修改请求体：

| 请求字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `name` | string | 是 | 1 到 100 |
| `description` | string/null | 是 | 最长 2000 |
| `status` | ProjectStatus | 是 | - |
| `priority` | Priority | 是 | - |
| `startDate` | date/null | 是 | 合法日历日期 |
| `endDate` | date/null | 是 | 不早于 startDate |

```json
{
  "name": "FlowSync 第二阶段",
  "description": null,
  "status": "IN_PROGRESS",
  "priority": "HIGH",
  "startDate": "2026-07-01",
  "endDate": "2026-09-15"
}
```

### 6.4 转移 owner

`PUT /projects/{projectId}/owner`

| 请求字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `ownerId` | string | 是 | `active=true` 且 `systemRole=USER` 的用户 ID |

```json
{
  "ownerId": "2"
}
```

目标用户必须是有效的 USER。目标用户尚不是成员时，后端在同一事务内把他加入项目。原 owner 仍保留普通成员身份。成功返回 `200 Project`。

### 6.5 归档、恢复和删除项目

- `PUT /projects/{projectId}/archive`：设置 `archivedAt`，返回 `200 Project`。
- `DELETE /projects/{projectId}/archive`：清空 `archivedAt`，返回恢复后的 `200 Project`。
- `DELETE /projects/{projectId}`：永久删除已归档项目及其全部关联数据，成功返回 `204`。

归档、恢复和删除均无请求体。未归档项目不能直接删除，调用删除接口时返回 `409 PROJECT_NOT_ARCHIVED`；必须先归档，再单独确认永久删除。已归档项目保留历史数据，不能继续新增成员、邀请、任务或修改业务数据。删除项目是不可撤销的显式聚合删除：后端必须在同一事务内删除项目的 Summary、TaskLog、Task、ProjectInvitation、ProjectMember 和 Project，任一步失败时整体回滚。该接口不依赖数据库隐式级联删除。

### 6.6 查询项目成员

`GET /projects/{projectId}/members`

项目成员或 ADMIN 可访问，返回 `200 ProjectMember[]`。

### 6.7 ADMIN 直接添加成员

`POST /projects/{projectId}/members`

| 请求字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `userIds` | string[] | 是 | 非空、无重复；全部用户必须是有效且启用的 USER |

```json
{
  "userIds": ["2", "3"]
}
```

仅系统 `ADMIN` 可以直接添加，但 ADMIN 账号自身以及其他 ADMIN 都不能成为项目成员。后端先校验全部用户，再一次性添加；任意一项失败时不能添加任何成员。若用户有待处理邀请，直接添加成功后将该邀请取消。成功返回 `201 ProjectMember[]`，只包含本次新增成员。

项目 owner 即使不是系统管理员，也不能使用此接口直接添加成员，必须使用邀请流程。

### 6.8 移除成员

`DELETE /projects/{projectId}/members/{userId}`

owner 或 ADMIN 可操作，成功返回 `204`。不能移除当前 owner；成员仍负责该项目未完成任务时不能移除。

### 6.9 owner 创建邀请

`POST /projects/{projectId}/invitations`

| 请求字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `userIds` | string[] | 是 | 非空、无重复；全部用户必须是有效 USER 且尚未加入项目 |

```json
{
  "userIds": ["2", "3"]
}
```

仅当前项目 owner 可以创建邀请。被邀请用户必须是有效 USER、尚未加入项目，且不能存在 `PENDING` 邀请。ADMIN 账号不能接收项目邀请。批量校验和写入必须具有事务性。成功返回 `201 ProjectInvitation[]`。

### 6.10 管理项目邀请

- `GET /projects/{projectId}/invitations`：owner 或 ADMIN 查询项目邀请，返回数组。
- `DELETE /projects/{projectId}/invitations/{invitationId}`：owner 或 ADMIN 取消 `PENDING` 邀请，返回 `204`。

取消邀请是状态变更为 `CANCELLED`，不是删除历史记录。

### 6.11 被邀请人处理邀请

查询当前用户收到的邀请：

`GET /project-invitations?status=PENDING`

`status` 可省略；成功返回当前用户自己的 `ProjectInvitation[]`。

接受或拒绝邀请：

`PUT /project-invitations/{invitationId}`

| 请求字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `status` | InvitationStatus | 是 | 只允许 `ACCEPTED` 或 `REJECTED` |

```json
{
  "status": "ACCEPTED"
}
```

这里只允许 `ACCEPTED` 或 `REJECTED`，且仅被邀请人可操作。接受时，后端必须在同一事务中创建 `ProjectMember` 并更新邀请状态。成功返回 `200 ProjectInvitation`。

## 7. 任务与任务日志

### 7.1 查询任务列表

`GET /tasks`

普通用户只能看到自己参与项目的任务；ADMIN 可看到全部任务。

| 查询参数 | 类型 | 说明 |
| --- | --- | --- |
| `projectId` | string | 项目 ID |
| `assigneeId` | string | 负责人 ID |
| `status` | TaskStatus | 任务状态 |
| `priority` | Priority | 优先级 |
| `parentId` | string | 父任务 ID |
| `dueBefore` | date | 截止日期不晚于该日期 |
| `dueAfter` | date | 截止日期不早于该日期 |
| `q` | string | 按标题模糊搜索 |
| `page`, `size`, `sort` | - | 支持 `createdAt`, `updatedAt`, `title`, `dueDate`, `priority`, `status` |

成功返回 Task 分页对象。

### 7.2 创建任务

`POST /tasks`

```json
{
  "projectId": "101",
  "parentId": null,
  "title": "完成登录页面",
  "description": "实现 Session 登录流程",
  "assigneeId": "2",
  "status": "NOT_STARTED",
  "priority": "HIGH",
  "dueDate": "2026-07-20"
}
```

| 请求字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `projectId` | string | 是 | 有效且未归档的项目 |
| `parentId` | string/null | 是 | 必须属于同一项目，不能形成循环 |
| `title` | string | 是 | 1 到 100 |
| `description` | string/null | 是 | 最长 5000 |
| `assigneeId` | string/null | 是 | 必须是有效项目成员 |
| `status` | TaskStatus | 是 | - |
| `priority` | Priority | 是 | - |
| `dueDate` | date/null | 是 | 必须位于项目日期范围内 |

仅 owner 可创建。`creator` 从当前 Session 取得。成功返回 `201 Task`。

### 7.3 查询、完整修改和删除任务

- `GET /tasks/{taskId}`：成员或 ADMIN 可访问，返回 `200 Task`。
- `PUT /tasks/{taskId}`：owner 完整修改，返回 `200 Task`。
- `DELETE /tasks/{taskId}`：owner 删除，成功返回 `204`。

完整修改请求体不包含 `projectId`：

| 请求字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `parentId` | string/null | 是 | 同一项目内的任务，不能形成循环 |
| `title` | string | 是 | 1 到 100 |
| `description` | string/null | 是 | 最长 5000 |
| `assigneeId` | string/null | 是 | 非空时必须是有效项目成员 |
| `status` | TaskStatus | 是 | - |
| `priority` | Priority | 是 | - |
| `dueDate` | date/null | 是 | 符合项目日期范围 |

```json
{
  "parentId": null,
  "title": "完成登录和退出页面",
  "description": "实现完整 Session 流程",
  "assigneeId": "2",
  "status": "IN_PROGRESS",
  "priority": "HIGH",
  "dueDate": "2026-07-22"
}
```

任务存在子任务、TaskLog 或 Summary 时不能删除。

### 7.4 修改任务状态

`PUT /tasks/{taskId}/status`

| 请求字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `status` | TaskStatus | 是 | - |

```json
{
  "status": "COMPLETED"
}
```

owner 或当前 assignee 可以修改，成功返回更新后的 `200 Task`。该接口只修改状态，因此不要求发送其他任务字段。

### 7.5 查询任务日志

`GET /tasks/{taskId}/logs`

支持 `page`、`size` 和 `sort`，可排序字段为 `createdAt`、`progressPercent`。成员或 ADMIN 可访问，返回 TaskLog 分页对象。

### 7.6 创建任务日志

`POST /tasks/{taskId}/logs`

| 请求字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `progressPercent` | integer | 是 | 0 到 100 |
| `content` | string | 是 | 1 到 1000 |

```json
{
  "progressPercent": 40,
  "content": "登录表单和校验已完成"
}
```

`progressPercent` 是 0 到 100 的整数，`content` 为 1 到 1000 个字符。owner 或当前 assignee 可以创建。`operator` 从 Session 取得，成功返回 `201 TaskLog`。

### 7.7 删除任务日志

`DELETE /tasks/{taskId}/logs/{logId}`

owner 或该日志创建者可以删除，成功返回 `204`。

## 8. 总结与总览

### 8.1 查询总结列表

`GET /summaries`

| 查询参数 | 类型 | 说明 |
| --- | --- | --- |
| `projectId` | string | 项目 ID |
| `taskId` | string | 任务 ID；使用 `none` 只查询项目级总结 |
| `type` | SummaryType | `STAGE` 或 `FINAL` |
| `createdBy` | string | 创建者 ID |
| `page`, `size`, `sort` | - | 支持 `createdAt`, `updatedAt`, `type` |

返回当前用户可见项目内的 Summary 分页对象。

### 8.2 创建总结

`POST /summaries`

| 请求字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `projectId` | string | 是 | 有效项目 ID |
| `taskId` | string/null | 是 | 非空时必须属于同一项目 |
| `type` | SummaryType | 是 | - |
| `content` | string | 是 | 不允许为空 |

```json
{
  "projectId": "101",
  "taskId": null,
  "type": "STAGE",
  "content": "本周已完成登录和项目列表功能。"
}
```

`taskId` 可以为 `null`；非空时任务必须属于同一项目。`content` 不能为空。项目成员可以创建，ADMIN 只能读取。`createdBy` 从 Session 取得。成功返回 `201 Summary`。

### 8.3 查询、修改和删除总结

- `GET /summaries/{summaryId}`：成员或 ADMIN 可访问。
- `PUT /summaries/{summaryId}`：创建者或 owner 可修改。
- `DELETE /summaries/{summaryId}`：创建者或 owner 可删除。

修改请求体：

| 请求字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `type` | SummaryType | 是 | - |
| `content` | string | 是 | 不允许为空 |

```json
{
  "type": "FINAL",
  "content": "项目第一阶段已完成。"
}
```

`PUT` 成功返回 `200 Summary`，删除成功返回 `204`。

### 8.4 工作台总览

`GET /overview?projectId=101`

`projectId` 可省略。普通用户只统计可见项目，ADMIN 可统计全部项目。

响应字段：

| 响应字段 | 类型 | 可为 null | 说明 |
| --- | --- | --- | --- |
| `counts.projects` | integer | 否 | 可见项目数 |
| `counts.tasks` | integer | 否 | 可见任务数 |
| `counts.completedTasks` | integer | 否 | 已完成任务数 |
| `counts.overdueTasks` | integer | 否 | 已逾期且未完成任务数 |
| `counts.summaries` | integer | 否 | 可见总结数 |
| `counts.members` | integer | 否 | 去重后的可见项目成员数 |
| `tasksByStatus` | array | 否 | 各 TaskStatus 对应的任务数量 |
| `tasksByStatus[].status` | TaskStatus | 否 | 任务状态 |
| `tasksByStatus[].count` | integer | 否 | 对应状态的任务数 |
| `recentActivities` | array | 否 | 最近活动，最多 10 条 |
| `recentActivities[].type` | string | 否 | 活动类型 |
| `recentActivities[].resourceId` | string | 否 | 相关资源 ID |
| `recentActivities[].summary` | string | 否 | 活动摘要 |
| `recentActivities[].occurredAt` | datetime | 否 | 活动时间 |

响应体示例：

```json
{
  "counts": {
    "projects": 3,
    "tasks": 12,
    "completedTasks": 5,
    "overdueTasks": 1,
    "summaries": 4,
    "members": 6
  },
  "tasksByStatus": [
    { "status": "NOT_STARTED", "count": 3 },
    { "status": "IN_PROGRESS", "count": 4 },
    { "status": "BLOCKED", "count": 0 },
    { "status": "COMPLETED", "count": 5 },
    { "status": "CANCELLED", "count": 0 }
  ],
  "recentActivities": [
    {
      "type": "TASK_LOG_CREATED",
      "resourceId": "601",
      "summary": "李明更新了任务进度",
      "occurredAt": "2026-07-13T08:30:00Z"
    }
  ]
}
```

`recentActivities` 最多返回最近 10 条可见活动。

## 9. AI 接口

AI 只生成临时建议或临时任务计划，不能直接写入 Task、TaskLog、Summary 或其他业务记录。
所有 AI 输出必须由具有对应权限的 `systemRole=USER` 用户人工审阅；需要进入业务记录的
内容必须由该用户通过正常业务接口提交，AI Provider 和 ADMIN 不能代替业务用户提交。

AI 计划采用以下流程：

1. 项目 owner 以 USER 身份提交项目目标。
2. 后端调用 AI，返回初步 `AiTaskPlan`。
3. owner 人工审阅并编辑计划内容。
4. owner 以当前 USER Session 把最终确认的 `items` 提交导入接口。
5. 后端校验全部条目，并在同一事务中创建 Task。

`AiTaskPlan` 是临时响应 DTO，不写入数据库，不创建 `planId`，也不建立 AI 计划表。只有最终导入的 Task 会持久化。

### 9.1 获取单个任务建议

`POST /ai/task-suggestions`

请求字段：

| 请求字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `taskId` | string | 是 | 有效任务 ID |
| `focus` | string/null | 否 | 希望 AI 重点回答的内容，可包含实际进展并要求生成 TaskLog 草稿 |

请求体示例：

```json
{
  "taskId": "501",
  "focus": "根据以下实际进展生成 TaskLog 草稿：登录表单已完成，正在联调接口"
}
```

仅项目 owner 或当前 task assignee 可调用。未负责该任务的普通 member 和 ADMIN 不能调用。

响应字段：

| 响应字段 | 类型 | 可为 null | 说明 |
| --- | --- | --- | --- |
| `suggestion` | string | 否 | AI 生成的任务建议 |
| `generatedAt` | datetime | 否 | 建议生成时间 |

响应体示例：

```json
{
  "suggestion": "登录表单及校验功能已完成，当前正在进行接口联调。",
  "generatedAt": "2026-07-13T09:30:00Z"
}
```

`suggestion` 是临时内容，不写入 Task、TaskLog 或 Summary。用于 TaskLog 草稿时，
`progressPercent` 和实际进展必须由 USER 提供；AI 不能自行认定真实完成度。owner 或当前
assignee 必须人工审阅并可编辑建议，再通过 `POST /tasks/{taskId}/logs` 提交最终内容，
最终 `operator` 从当前 Session 取得。

### 9.2 生成初步任务计划

`POST /projects/{projectId}/ai/task-plans`

请求体示例：

```json
{
  "goal": "完成 FlowSync 第一阶段开发",
  "description": "需要包含登录、项目管理和任务管理",
  "constraints": {
    "maxItems": 6,
    "targetEndDate": "2026-08-31"
  }
}
```

| 请求字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `goal` | string | 是 | 不允许为空 |
| `description` | string/null | 否 | 补充背景 |
| `constraints.maxItems` | integer | 否 | 1 到 20，默认 10 |
| `constraints.targetEndDate` | date/null | 否 | 合法日期且符合项目日期范围 |

响应字段：

| 响应字段 | 类型 | 可为 null | 说明 |
| --- | --- | --- | --- |
| `overview` | string | 否 | 计划整体说明 |
| `items` | AiTaskPlanItem[] | 否 | 初步任务条目 |
| `items[].draftId` | string | 否 | 本次计划内唯一的临时 ID |
| `items[].parentDraftId` | string | 是 | 父条目的临时 ID |
| `items[].title` | string | 否 | 任务标题 |
| `items[].description` | string | 是 | 任务描述 |
| `items[].priority` | Priority | 否 | 任务优先级 |
| `items[].dueDate` | date | 是 | 截止日期 |
| `items[].assigneeId` | string | 是 | 负责人 ID |
| `generatedAt` | datetime | 否 | 计划生成时间 |

响应体示例：

```json
{
  "overview": "先完成基础认证，再并行开发项目与任务模块。",
  "items": [
    {
      "draftId": "draft-1",
      "parentDraftId": null,
      "title": "实现 Session 登录",
      "description": "完成登录、登出和当前用户接口",
      "priority": "HIGH",
      "dueDate": "2026-07-20",
      "assigneeId": "2"
    },
    {
      "draftId": "draft-2",
      "parentDraftId": "draft-1",
      "title": "连接登录页面",
      "description": null,
      "priority": "MEDIUM",
      "dueDate": "2026-07-23",
      "assigneeId": null
    }
  ],
  "generatedAt": "2026-07-13T09:30:00Z"
}
```

owner 必须以 USER 身份人工审阅计划，并可以修改、增加、删除或重新排序 `items`。此时
数据不由服务端持久化。

### 9.3 导入编辑后的任务计划

`POST /projects/{projectId}/ai/task-plans/imports`

请求体不是上一步完整响应，只提交经 owner 人工审阅和编辑后最终确认的 `items`。导入请求
必须来自当前 owner 的 USER Session；AI Provider 和 ADMIN 不能调用导入接口。

请求字段：

| 请求字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `items` | AiTaskPlanItem[] | 是 | 1 到 20 项 |
| `items[].draftId` | string | 是 | 本次请求内唯一 |
| `items[].parentDraftId` | string/null | 是 | 只能指向本次请求中的条目且不能形成循环 |
| `items[].title` | string | 是 | 1 到 100 |
| `items[].description` | string/null | 是 | 最长 5000 |
| `items[].priority` | Priority | 是 | - |
| `items[].dueDate` | date/null | 是 | 符合项目日期范围 |
| `items[].assigneeId` | string/null | 是 | 非空时必须是有效项目成员 |

请求体示例：

```json
{
  "items": [
    {
      "draftId": "draft-1",
      "parentDraftId": null,
      "title": "实现 Session 登录",
      "description": "完成登录、登出和当前用户接口",
      "priority": "HIGH",
      "dueDate": "2026-07-20",
      "assigneeId": "2"
    },
    {
      "draftId": "draft-2",
      "parentDraftId": "draft-1",
      "title": "连接登录页面",
      "description": null,
      "priority": "MEDIUM",
      "dueDate": "2026-07-23",
      "assigneeId": null
    }
  ]
}
```

导入规则：

- `items` 数量为 1 到 20。
- 每个条目必须包含 `draftId`、`parentDraftId`、`title`、`description`、`priority`、`dueDate` 和 `assigneeId`。
- `draftId` 在本次请求中唯一。
- `parentDraftId` 只能指向同一请求中的 `draftId`，且不能形成循环。
- `assigneeId` 非空时必须是有效项目成员。
- `dueDate` 非空时必须在项目日期范围内。
- 导入后的 Task 初始状态为 `NOT_STARTED`，creator 为当前用户。
- 后端先校验全部条目，再在一个事务中创建全部任务；任意条目失败时一个任务也不能创建。

响应字段：

| 响应字段 | 类型 | 可为 null | 说明 |
| --- | --- | --- | --- |
| `importedCount` | integer | 否 | 本次成功创建的任务数 |
| `tasks` | Task[] | 否 | 本次创建的全部任务 |

响应体示例（HTTP 201）：

```json
{
  "importedCount": 2,
  "tasks": [
    {
      "id": "801",
      "projectId": "101",
      "parentId": null,
      "assignee": { "id": "2", "displayName": "李明" },
      "creator": { "id": "1", "displayName": "王强" },
      "title": "实现 Session 登录",
      "description": "完成登录、登出和当前用户接口",
      "status": "NOT_STARTED",
      "priority": "HIGH",
      "progressPercent": 0,
      "dueDate": "2026-07-20",
      "createdAt": "2026-07-13T09:35:00Z",
      "updatedAt": "2026-07-13T09:35:00Z"
    }
  ]
}
```

示例中 `tasks` 为节省篇幅只展示一项，真实响应必须返回本次创建的全部 Task。

## 10. 关键业务与事务规则

- `User.systemRole` 只表示系统级 `ADMIN` 或 `USER`，不表示项目角色。
- ADMIN 是独立的系统管理账号，可以查看全部项目并管理项目、owner 和成员，但不能成为 owner、成员、被邀请人或任务负责人，不能写入任务、日志或总结，也不能调用 AI 接口。
- 项目 owner 存储在 `Project.ownerId`；普通参与关系存储在 `ProjectMember`。
- owner 必须同时是项目成员。
- ADMIN 可把 USER 直接添加为成员；owner 添加成员必须走邀请并由用户接受。
- 已停用用户不能登录、接收邀请、加入项目或被分配新任务。
- 创建项目、转移 owner、接受邀请和批量写入涉及的多条记录必须在数据库事务中完成。
- 批量添加成员、批量邀请和 AI 任务导入必须先完整校验，再统一写入，禁止部分成功。
- AI 输出必须由具备对应权限的 USER 人工审阅；只有该 USER 通过正常业务接口提交后，内容才会持久化。
- User 使用 `active=false` 停用，不物理删除。
- Project 使用 `archivedAt` 归档；归档不是删除。
- 只有已归档项目可通过 `DELETE /projects/{projectId}` 显式、事务性地删除整个项目聚合；其他删除行为不隐式级联历史数据，并遵守 `docs/relationship.md` 中的外键和引用限制。
- 当前版本不使用 `version` 字段，也不实现乐观锁。

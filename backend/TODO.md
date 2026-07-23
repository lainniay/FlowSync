# Backend TODO

本文件只记录尚未完成的后端任务。完成一项后，将 `- [ ]` 改为 `- [x]`。

## 开始业务 Service 开发前

- [x] 限制 `BusinessException` 只能返回安全的公开文案。
  - 为什么：当前调用方可以把任意字符串作为 `detail` 返回客户端，误传
    `exception.getMessage()` 可能泄漏 SQL、连接信息或其他内部细节。
  - 怎么做：把固定的公开 `detail` 放进 `ErrorCode`，让 `BusinessException` 只接收错误码。
    确需动态信息时使用受控模板，不得直接使用底层异常消息。
  - 完成标准：业务代码无法通过 `BusinessException` 直接返回底层异常消息，并有测试确认
    内部信息不会出现在响应中。

- [x] `UserService` 在创建用户时转换用户名唯一约束冲突。
  - 写入前检查用户名；并发请求仍同时通过检查时，捕获 Mapper 写入产生的
    `DuplicateKeyException`，转换为 `USERNAME_ALREADY_EXISTS`。
  - 完成标准：重复用户名返回约定的 `409` Problem Details，数据库只保留一条记录。

- [x] 在其余业务 Service 中转换数据库约束冲突。
  - 为什么：即使写入前检查过数据，并发请求仍可能同时通过检查，最终由数据库的 `UNIQUE`
    约束发现重复。MyBatis-Plus 会把这种失败转换为 Spring 的 `DuplicateKeyException`。
  - 重复数据：写入前先检查；Mapper 写入时捕获 `DuplicateKeyException`，再抛出对应的
    `BusinessException`：
    - `ProjectMemberService`：`MEMBER_ALREADY_EXISTS`
    - `ProjectInvitationService`：`INVITATION_ALREADY_PENDING`
  - 删除仍被引用的资源：由具体删除 Service 捕获 `DataIntegrityViolationException`，转换为
    `RESOURCE_IN_USE`。它表示外键阻止删除，不是重复键错误。
  - 注意：不要在 `GlobalExceptionHandler` 中把所有数据库异常统一转换为 `409`，因为它无法
    判断当前执行的是哪项业务，其他数据库故障也不一定是数据冲突。
  - 批量操作：必须放在同一个事务中；任意一条失败时，整批回滚，不能部分成功。
  - 已完成：`ProjectMemberService` 转换 `MEMBER_ALREADY_EXISTS`，
    `ProjectInvitationService` 转换 `INVITATION_ALREADY_PENDING`，成员和邀请批量写入已使用事务。
  - 已完成：任务删除和项目永久删除转换 `DataIntegrityViolationException`，并覆盖引用阻止删除与
    事务回滚测试。
  - 完成标准：上述重复写入均返回约定的 `409` Problem Details；未识别的数据库故障仍返回
    `500 INTERNAL_SERVER_ERROR`；批量失败后数据库没有残留的部分写入。

## 实现对应业务模块时

- [x] 认证模块产生 `INVALID_CREDENTIALS` 和 `CURRENT_PASSWORD_INCORRECT`，并补充登录失败、
  当前密码错误的契约测试。
- [x] `UserService` 产生 `USERNAME_ALREADY_EXISTS`。
- [x] 项目、成员和邀请 Service 产生 `docs/api.md` 定义的对应 `409` 业务错误码。
  - 已完成成员直接添加、邀请创建、邀请状态转换、成员移除和项目永久删除。
- [x] 实现 `POST /api/projects/{projectId}/ai/task-plans/imports`，只持久化 owner 人工审阅后
  确认的 Task，并保证批量校验和写入事务性。
- [x] AI Provider 集成实现任务建议和任务计划生成，产生 `RATE_LIMITED` 和
  `AI_PROVIDER_ERROR`，且不把 AI 提供商的原始异常返回客户端。

- [x] 实现 `PUT /api/users/{userId}`。
  - 前置条件：先完成 Project、ProjectMember、ProjectInvitation 和 Task 的基础查询，才能检查项目
    owner、成员和待处理邀请关系，以及用户是否仍负责未完成任务。
  - 完成标准：支持资料、角色和启用状态的全量更新；保留至少一个有效 ADMIN；角色变化或停用后
    使该用户全部 Session 失效；并覆盖并发修改最后一个 ADMIN 的测试。
  - 复查时机：上述四个模块的 Entity、Mapper 和基础查询完成后。

## 部署与框架边界

- [ ] 生产环境启用 TLS，并配置 Session Cookie 的 `Secure` 和明确的 `SameSite` 策略。
  - 同时确认反向代理转发头配置正确；本地 HTTP 开发环境保持当前配置。

- [ ] 在反向代理或认证边界为 `POST /api/auth/login` 增加按账号和来源的限流。
  - 完成标准：连续失败请求受到限制，同时继续统一返回 `INVALID_CREDENTIALS`，不暴露用户名是否存在。

- [x] 为 JSON 请求体配置 1 MiB 大小上限，并按 `docs/api.md` 返回 `413 PAYLOAD_TOO_LARGE`。
  - 已覆盖已声明 `Content-Length` 和未知长度的流式请求；超过上限时均返回 Problem Details。

- [ ] 部署前决定是否公开 OpenAPI 与 Swagger；不需要公开时在生产配置中关闭或限制访问。

- [ ] 部署前决定是否统一 Servlet 容器提前拒绝的错误响应。
  - 背景：非法请求行、非法 HTTP 方法或 Tomcat 直接拒绝的 `TRACE`，可能在请求进入 Spring MVC
    前就被拒绝，因此不会经过 `GlobalExceptionHandler`。
  - 怎么做：只有部署环境要求这些响应也符合 FlowSync Problem Details 时，才在反向代理或
    Tomcat 错误处理层统一；否则保持当前行为。

- [ ] 部署前确认异步响应失败只记录服务端日志。
  - 背景：客户端断开连接，或响应已经 committed 后发生序列化、流式传输错误时，HTTP 响应头和
    部分响应体已经发出，Spring 无法安全地重新写成 Problem Details。
  - 完成标准：这些错误有服务端日志，并且不会尝试再次写响应体。

- [ ] 接入会抛出新 HTTP 状态的第三方库时，先扩展错误契约。
  - 怎么做：先在 `docs/api.md` 和 `ErrorCode` 增加稳定错误码，再增加异常映射和契约测试。
  - 当前行为：未定义的 `ErrorResponseException` 统一降级为
    `500 INTERNAL_SERVER_ERROR`。

## 框架概念速查

- **Service**：承载业务规则的代码层，知道当前正在“创建用户”还是“添加成员”。
- **Mapper**：MyBatis-Plus 用来执行数据库读写的接口。
- **数据库唯一约束（`UNIQUE`）**：由数据库保证某个值或值组合不能重复，是并发情况下的最后防线。
- **事务与回滚**：一组数据库操作要么全部成功，要么失败后全部撤销。
- **`GlobalExceptionHandler` / Advice**：集中把异常包装成 HTTP 错误响应，但通常不知道具体业务场景。
- **Servlet 容器**：Tomcat 等接收 HTTP 请求并把请求交给 Spring 的底层服务器。
- **committed 响应**：响应头或响应体已经开始发送，之后不能安全地更换状态码和完整响应体。

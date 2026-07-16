# 后端辅助开发说明

本文档用于指导辅助开发者并行完成 FlowSync 的 Task、TaskLog 和 Summary 模块。辅助开发者应在独立分支工作，主开发负责 Project 聚合、共享基座、最终集成和文档契约维护。

## 1. 开发范围与文件所有权

辅助开发者负责：

- `src/main/java/hgc/flowsync/task/**`
- `src/main/java/hgc/flowsync/summary/**`
- `src/test/java/hgc/flowsync/task/**`
- `src/test/java/hgc/flowsync/summary/**`

辅助开发者不负责：

- Project、ProjectMember、ProjectInvitation 的业务接口。
- Overview 和 AI 接口。
- 修改数据库模型或新增迁移。
- 修改前端和 Mock。
- 修改 `docs/api.md`、`docs/relationship.md` 或 `TODO.md`。
- 修改共享错误、安全、认证、分页和时间基座。

以下文件由主开发维护，默认视为冻结：

```text
src/main/java/hgc/flowsync/common/**
src/main/java/hgc/flowsync/project/**
src/main/java/hgc/flowsync/user/**
src/main/resources/db/migration/**
../docs/api.md
../docs/relationship.md
TODO.md
```

如果辅助模块确实需要修改冻结文件，不要直接修改，先按照“与主开发交流”一节提交共享变更请求。

## 2. 核心开发文档

开始编码前必须完整阅读：

1. `../AGENTS.md`：仓库结构、权限、安全、事务和测试总规则。
2. `../docs/api.md`：HTTP 契约。重点阅读第 1、2、7、8、10 节。
3. `../docs/relationship.md`：Task、TaskLog、Summary 字段、外键和删除约束。
4. `TODO.md`：数据库约束异常和后续业务错误处理要求。
5. `../.env.example`：本地 MySQL 和初始管理员配置。

文档优先级为：

```text
docs/api.md + docs/relationship.md > docs/FlowSync.pdf > 现有未完成代码
```

发现文档之间冲突时停止相关实现，把冲突位置和影响报告给主开发，不得自行选择新契约。

## 3. 核心公用代码位置

下表中的代码路径相对于 `src/main/java/hgc/flowsync/`：

| 能力 | 代码位置 | 用途 |
| --- | --- | --- |
| 当前有效用户 | `user/CurrentUserService.java` | 从 Spring Security Session 加载数据库 User，并检查账号是否有效 |
| 项目访问控制 | `project/ProjectAccessService.java` | 加载项目、判断 ADMIN/owner/member、检查归档状态 |
| 通用分页响应 | `common/api/PageResponse.java` | 返回统一的 `items/page/size/totalElements/totalPages` |
| API 时间转换 | `common/time/ApiDateTime.java` | 把数据库 `LocalDateTime` 转为 API 使用的 UTC `Instant` |
| 业务错误 | `common/error/BusinessException.java`、`ErrorCode.java` | 返回稳定、安全的 Problem Details 错误码 |
| 用户摘要 | `user/UserBrief.java` | Task、TaskLog、Summary 响应中的用户嵌套结构 |
| 用户查询 | `user/UserMapper.java` | 加载 assignee、creator、operator、createdBy 对应用户 |
| Task 持久化 | `task/Task.java`、`TaskMapper.java` | 已存在的 Task Entity 和 Mapper |
| TaskLog 持久化 | `task/TaskLog.java`、`TaskLogMapper.java` | 已存在的 TaskLog Entity 和 Mapper |
| Summary 持久化 | `summary/Summary.java`、`SummaryMapper.java` | 已存在的 Summary Entity 和 Mapper |

## 4. 必须复用的代码

### 4.1 当前用户

Controller 把 `Authentication` 传入 Service，Service 使用：

```java
User currentUser = currentUserService.require(authentication);
```

需要对当前用户记录加数据库行锁时才使用：

```java
User currentUser = currentUserService.requireForUpdate(authentication);
```

禁止：

- 接受客户端提交的 `currentUserId`、`creatorId` 或 `operatorId` 作为身份依据。
- 从请求体推断当前用户。
- 重新实现一套 Authentication 到 User 的查询。

### 4.2 项目权限和归档状态

必须复用 `ProjectAccessService`：

- `requireProject(projectId)`：项目不存在时返回 `NOT_FOUND`。
- `requireOwner(project, user)`：仅 owner 可继续。
- `requireMemberOrAdmin(project, user)`：项目成员或 ADMIN 可读取。
- `requireOwnerOrAdmin(project, user)`：owner 或 ADMIN 可管理。
- `requireUnarchived(project)`：阻止向归档项目写入业务内容。
- `isAdmin`、`isOwner`、`isMember`：组合 Task、TaskLog、Summary 的专用权限。

不要在 Task、TaskLog、Summary Service 中复制 Project owner/member 查询。

### 4.3 分页、时间和响应用户

所有分页接口返回：

```java
PageResponse.of(items, page, size, totalElements)
```

所有 API datetime 字段使用：

```java
ApiDateTime.toInstant(entity.getCreatedAt())
```

响应中的 assignee、creator、operator、createdBy 使用 `UserBrief`，JSON ID 必须是字符串。不得返回 Entity，也不得暴露 `passwordHash`。

### 4.4 错误处理

业务失败使用：

```java
throw new BusinessException(ErrorCode.NOT_FOUND);
```

禁止：

- 自定义另一种错误响应结构。
- 把 SQL、异常消息或数据库细节传给客户端。
- 在 Controller 中捕获所有异常。
- 把数据库异常全部转换成同一个 `409`。

Task 删除被子任务、TaskLog 或 Summary 引用时，在具体 Service 中把 `DataIntegrityViolationException` 转为 `RESOURCE_IN_USE`。无法识别的数据库异常保持为 `500 INTERNAL_SERVER_ERROR`。

## 5. 需要实现的需求

### 5.1 第一里程碑：Task 基础和访问控制

新增 feature-local `TaskAccessService`，至少统一：

- 加载 Task，不存在时返回 `NOT_FOUND`。
- 加载 Task 所属 Project。
- 成员或 ADMIN 的读取权限。
- owner 的创建、完整修改和删除权限。
- owner 或当前 assignee 的状态修改权限。
- owner 或当前 assignee 的 TaskLog 创建权限。
- 项目归档后的写入阻止。

在 `TaskMapper` 中补充当前实现实际需要的查询。主开发明确需要以下公共查询：

```java
boolean existsIncompleteByProjectIdAndAssigneeId(Long projectId, Long assigneeId)
long countByProjectId(Long projectId)
long countCompletedByProjectId(Long projectId)
```

这些查询供 Project 成员移除和 Project 响应统计使用，命名或返回类型需要调整时先通知主开发。

### 5.2 第二里程碑：Task HTTP 接口

实现 `docs/api.md` 第 7.1 至 7.4 节：

| 方法 | 路径 | 核心权限 |
| --- | --- | --- |
| GET | `/api/tasks` | USER 只能看参与项目，ADMIN 可看全部 |
| POST | `/api/tasks` | owner |
| GET | `/api/tasks/{taskId}` | 成员或 ADMIN |
| PUT | `/api/tasks/{taskId}` | owner |
| PUT | `/api/tasks/{taskId}/status` | owner 或当前 assignee |
| DELETE | `/api/tasks/{taskId}` | owner |

必须实现的业务规则：

- `creatorId` 始终取自当前 Session。
- ADMIN 只能读取，不能创建、修改状态或提交项目内容。
- assignee 必须是该项目中有效、启用的 USER 成员；允许 `null`。
- parent 必须属于同一个项目，且不能形成父子循环。
- dueDate 必须位于 Project 的日期范围内。
- 已归档项目不能创建或修改 Task。
- PUT 必须接收全部可编辑字段；nullable 字段需要显式允许 `null`。
- Task 有子任务、TaskLog 或 Summary 时不能删除，返回 `RESOURCE_IN_USE`。
- 排序字段、分页范围和查询参数严格匹配 `docs/api.md`，非法值返回 `VALIDATION_ERROR`。

### 5.3 第三里程碑：TaskLog HTTP 接口

实现 `docs/api.md` 第 7.5 至 7.7 节：

| 方法 | 路径 | 核心权限 |
| --- | --- | --- |
| GET | `/api/tasks/{taskId}/logs` | 成员或 ADMIN |
| POST | `/api/tasks/{taskId}/logs` | owner 或当前 assignee |
| DELETE | `/api/tasks/{taskId}/logs/{logId}` | owner 或日志创建者 |

必须实现的业务规则：

- `operatorId` 始终取自当前 Session。
- ADMIN 不能创建或删除项目内容。
- `progressPercent` 范围为 0 到 100。
- `content` 长度为 1 到 1000，不能为空白字符串。
- log 必须属于路径中的 task；资源关系不匹配时不得越权访问。
- 已归档项目不能新增或删除 TaskLog。
- 列表使用 `PageResponse<TaskLogResponse>`，排序字段仅允许契约列出的字段。

### 5.4 第四里程碑：Summary HTTP 接口

实现 `docs/api.md` 第 8.1 至 8.3 节：

| 方法 | 路径 | 核心权限 |
| --- | --- | --- |
| GET | `/api/summaries` | 当前用户可见项目；ADMIN 可读取全部 |
| POST | `/api/summaries` | 项目成员 USER |
| GET | `/api/summaries/{summaryId}` | 成员或 ADMIN |
| PUT | `/api/summaries/{summaryId}` | 创建者或 owner |
| DELETE | `/api/summaries/{summaryId}` | 创建者或 owner |

必须实现的业务规则：

- `createdBy` 始终取自当前 Session。
- ADMIN 只能读取，不能创建、修改或删除项目内容。
- taskId 为非 null 时，Task 必须属于同一个 Project。
- `taskId=none` 查询项目级 Summary 的语义严格遵循 `docs/api.md`。
- `content` 不能为空白字符串。
- 已归档项目不能创建、修改或删除 Summary。
- PUT 必须同时包含 `type` 和 `content`。
- 列表使用 `PageResponse<SummaryResponse>`。

### 5.5 不在本次辅助范围

以下内容不要顺手实现：

- Project、成员和邀请接口。
- Project 永久删除聚合编排。
- Overview。
- AI suggestion、task plan 和 import。
- 新数据库表、乐观锁、软删除或通用 Repository 抽象。
- 前端类型、页面或 MSW Mock。

## 6. 可以参考的现有代码

| 参考目标 | 文件 |
| --- | --- |
| Controller 请求 DTO、Bean Validation、状态码 | `project/ProjectController.java`、`user/UserController.java` |
| Service 事务、Session 身份、写入后响应 | `project/ProjectService.java` |
| 分页查询、排序白名单、PageResponse | `user/UserService.java` |
| 业务错误和 Session 失效处理 | `user/UserService.java`、`auth/AuthService.java` |
| 项目权限矩阵 | `project/ProjectAccessService.java` |
| 真实 HTTP、Session、CSRF、事务回滚测试 | `src/test/java/hgc/flowsync/project/ProjectControllerTests.java` |
| ADMIN/owner/member/外部用户权限矩阵 | `src/test/java/hgc/flowsync/project/ProjectAccessServiceTests.java` |
| Entity/Mapper 与真实 MySQL 测试 | `src/test/java/hgc/flowsync/task/TaskMapperTests.java`、`summary/TaskLogSummaryMapperTests.java` |
| Problem Details 契约测试 | `src/test/java/hgc/flowsync/common/error/GlobalExceptionHandlerTests.java` |

参考现有代码的行为和风格，不要机械复制不适用于 Task 域的业务判断。

## 7. 测试要求

测试与实现必须在同一个里程碑提交中完成。不能只写 Service 单元测试，也不能只验证成功状态码。

### 7.1 测试技术

- Controller 契约测试使用 `@SpringBootTest`、`@AutoConfigureMockMvc` 和真实 MySQL。
- Mapper 和事务行为使用 `@SpringBootTest`，可按测试需要使用 `@Transactional` 回滚数据。
- 写请求必须携带真实 Session 和 CSRF Token。
- 测试方法按可观察行为命名，类名使用 `*Tests`。

### 7.2 最低覆盖矩阵

每个变化至少覆盖：

- 正常成功响应、完整响应字段和 `Content-Type`。
- 未登录返回 `401 UNAUTHORIZED`。
- owner、member、assignee/creator、ADMIN、外部 USER 的权限差异。
- 资源不存在或不可见时的 `404 NOT_FOUND`。
- 归档项目写入返回 `409 PROJECT_ARCHIVED`。
- 非法枚举、缺失字段、非法 nullable 和分页/排序参数返回 `422 VALIDATION_ERROR`。
- 资源引用阻止删除时返回 `409 RESOURCE_IN_USE`。
- 失败后数据库状态未发生部分修改。
- JSON ID 为字符串、datetime 为 UTC、响应不包含 Entity 内部字段。

权限测试不能只测试 Service helper，必须至少有一条真实 HTTP 回归覆盖每种权限类别。

### 7.3 验证命令

从仓库根目录启动数据库：

```bash
mise run db
```

辅助模块定向测试示例：

```bash
cd backend
./mvnw -Dtest=TaskControllerTests,TaskLogControllerTests,SummaryControllerTests test
```

交付前必须运行：

```bash
cd backend
./mvnw verify
```

任何失败都必须修复或明确证明是与本分支无关的既有失败，不得删除或弱化测试获得绿色构建。

## 8. 并行审查要求

每个里程碑准备交付时，必须针对同一个 commit SHA 并行启动至少四条独立、只读的审查：

1. **契约审查**：逐项核对 `docs/api.md` 的请求、响应、状态码和枚举。
2. **权限与安全审查**：核对 Session 身份、ADMIN 限制、owner/member/assignee/creator 权限和越权路径。
3. **数据库与事务审查**：核对外键、引用删除、事务回滚、归档状态和数据库异常转换。
4. **实际 QA 审查**：运行定向测试和 `./mvnw verify`，必要时通过真实 HTTP 请求验证成功与失败路径。

审查规则：

- 审查任务必须相互独立，不能共享结论后直接互相认可。
- 审查者只读代码和运行验证，不得同时修改工作树。
- 每条审查返回 `PASS` 或具体 finding，包含文件位置、复现方式和影响。
- 任一审查失败都不能交付。
- 修复 finding 后 commit SHA 会变化，四条审查必须针对新 SHA 重新执行。
- 提交给主开发的报告必须列出四条审查的结果和实际执行的命令。

并行审查不等于并行编辑同一批文件。实现阶段仍由辅助开发者单独维护自己的模块文件。

## 9. Git 与提交要求

建议分支：

```text
feat/task-content
```

提交按可独立回滚的里程碑组织：

```text
feat(task): add task access and endpoints
feat(task): add task log endpoints
feat(summary): add summary endpoints
```

不要把三个模块压成一个大提交，也不要把格式化、文档猜测或共享基座修改混入功能提交。

主开发通过 commit 或合并请求接收成果。辅助开发者不得直接改写或强推主开发分支。

## 10. 如何与主开发交流

### 10.1 开始开发时

发送一次启动消息：

```text
基线 commit：<sha>
工作分支：feat/task-content
当前里程碑：Task / TaskLog / Summary
预计修改文件：<paths>
共享接口依赖：<none 或列表>
```

### 10.2 请求共享代码变更时

不要直接修改冻结文件，发送：

```text
共享文件：<path>
现有接口为什么不足：<具体调用场景>
建议签名或行为：<最小变更>
受影响调用方：<paths>
验证方式：<tests/commands>
是否阻塞当前里程碑：是/否
```

主开发确认或提供替代方案后再继续依赖该接口。可以先完成不受影响的部分，不要在分支中复制临时公共实现。

### 10.3 发现契约问题时

发送：

```text
冲突文档和位置：<path + section>
现有代码行为：<path + behavior>
影响的接口：<endpoints>
可选方案：<最多两个>
推荐方案及原因：<one paragraph>
```

在主开发更新契约前，不实现依赖该决定的行为。

### 10.4 里程碑交付时

交付报告必须包含：

```text
commit：<sha + subject>
完成接口：<endpoint list>
关键业务规则：<short list>
测试：<commands + pass count>
并行审查：契约 / 权限安全 / 数据库事务 / QA 的结果
共享文件改动：无，或列出主开发已批准的改动
剩余事项或风险：无，或具体说明
```

只在以下情况立即联系主开发：

- 文档冲突或业务决策不明确。
- 必须修改冻结共享接口。
- 数据库模型无法表达契约要求。
- 连续三种实现方案仍无法通过同一验证。
- 发现会影响主开发 Project 模块的高风险缺陷。

普通实现细节由辅助开发者按照现有模式自行决定，不需要逐行等待主开发确认。

## 11. 完成标准

辅助开发任务只有在以下条件全部满足时才算完成：

- Task、TaskLog、Summary 约定接口全部实现。
- DTO、权限、事务和错误响应符合 `docs/api.md`。
- 没有接受客户端身份字段，没有允许 ADMIN 写入项目内容。
- 已归档项目的业务写入被统一阻止。
- 定向测试和完整 `./mvnw verify` 通过。
- 四条并行审查在最终 commit SHA 上全部 PASS。
- 提交历史按里程碑拆分，未包含主开发文件或无关改动。
- 交付报告包含 commit、测试、审查和剩余风险。

# Fundamental Relationship

本文档定义 FlowSync 的基础数据模型、字段约束和对象之间的关联关系，作为数据库表结构与后端实体设计的依据。

标记说明：

- `->` 表示外键指向关系，例如 `ownerId -> User.id`。
- `{...}` 表示字段允许的枚举值。
- `[...]` 表示包含边界的数值范围，例如 `[0, 100]`。
- `nullable` 表示字段允许为 `null`；`NOT NULL` 表示字段不能为空。
- `UNIQUE` 表示字段或字段组合的值不能重复。
- `DEFAULT` 表示数据库默认值；`ON UPDATE` 表示记录更新时自动刷新字段值。

---

## User

- id: BIGINT, PRIMARY KEY, AUTO_INCREMENT
- username: VARCHAR(50), NOT NULL, UNIQUE
- passwordHash: VARCHAR(100), NOT NULL
- displayName: VARCHAR(50), NOT NULL
- phone: VARCHAR(20), nullable
- email: VARCHAR(100), nullable
- systemRole: VARCHAR(20), NOT NULL, {ADMIN, USER}
- active: BOOLEAN, NOT NULL, DEFAULT TRUE
- createdAt: DATETIME, NOT NULL, DEFAULT CURRENT_TIMESTAMP
- updatedAt: DATETIME, NOT NULL, DEFAULT CURRENT_TIMESTAMP, ON UPDATE CURRENT_TIMESTAMP

---

## Project

- id: BIGINT, PRIMARY KEY, AUTO_INCREMENT
- ownerId: BIGINT, NOT NULL -> User.id
- name: VARCHAR(100), NOT NULL
- description: VARCHAR(2000), nullable
- status: VARCHAR(20), NOT NULL, {NOT_STARTED, IN_PROGRESS, COMPLETED}
- priority: VARCHAR(20), NOT NULL, {LOW, MEDIUM, HIGH}
- startDate: DATE, nullable
- endDate: DATE, nullable
- archivedAt: DATETIME, nullable
- createdAt: DATETIME, NOT NULL, DEFAULT CURRENT_TIMESTAMP
- updatedAt: DATETIME, NOT NULL, DEFAULT CURRENT_TIMESTAMP, ON UPDATE CURRENT_TIMESTAMP

---

## ProjectMember

- id: BIGINT, PRIMARY KEY, AUTO_INCREMENT
- projectId: BIGINT, NOT NULL -> Project.id
- userId: BIGINT, NOT NULL -> User.id
- joinedAt: DATETIME, NOT NULL, DEFAULT CURRENT_TIMESTAMP
- UNIQUE (projectId, userId)

---

## ProjectInvitation

- id: BIGINT, PRIMARY KEY, AUTO_INCREMENT
- projectId: BIGINT, NOT NULL -> Project.id
- inviteeId: BIGINT, NOT NULL -> User.id
- invitedBy: BIGINT, NOT NULL -> User.id
- status: VARCHAR(20), NOT NULL, {PENDING, ACCEPTED, REJECTED, CANCELLED}
- createdAt: DATETIME, NOT NULL, DEFAULT CURRENT_TIMESTAMP
- respondedAt: DATETIME, nullable
- UNIQUE (projectId, inviteeId)

---

## Task

- id: BIGINT, PRIMARY KEY, AUTO_INCREMENT
- projectId: BIGINT, NOT NULL -> Project.id
- parentId: BIGINT, nullable -> Task.id
- assigneeId: BIGINT, nullable -> User.id
- creatorId: BIGINT, NOT NULL -> User.id
- title: VARCHAR(100), NOT NULL
- description: VARCHAR(5000), nullable
- status: VARCHAR(20), NOT NULL, {NOT_STARTED, IN_PROGRESS, BLOCKED, COMPLETED, CANCELLED}
- priority: VARCHAR(20), NOT NULL, {LOW, MEDIUM, HIGH}
- dueDate: DATE, nullable
- createdAt: DATETIME, NOT NULL, DEFAULT CURRENT_TIMESTAMP
- updatedAt: DATETIME, NOT NULL, DEFAULT CURRENT_TIMESTAMP, ON UPDATE CURRENT_TIMESTAMP

---

## TaskLog

- id: BIGINT, PRIMARY KEY, AUTO_INCREMENT
- taskId: BIGINT, NOT NULL -> Task.id
- operatorId: BIGINT, NOT NULL -> User.id
- progressPercent: INT, NOT NULL, [0, 100]
- content: VARCHAR(1000), NOT NULL
- createdAt: DATETIME, NOT NULL, DEFAULT CURRENT_TIMESTAMP

---

## Summary

- id: BIGINT, PRIMARY KEY, AUTO_INCREMENT
- projectId: BIGINT, NOT NULL -> Project.id
- taskId: BIGINT, nullable -> Task.id
- createdBy: BIGINT, NOT NULL -> User.id
- type: VARCHAR(20), NOT NULL, {STAGE, FINAL}
- content: TEXT, NOT NULL
- createdAt: DATETIME, NOT NULL, DEFAULT CURRENT_TIMESTAMP
- updatedAt: DATETIME, NOT NULL, DEFAULT CURRENT_TIMESTAMP, ON UPDATE CURRENT_TIMESTAMP

---

## 约束说明

- 系统必须始终保留至少一个 `active=true` 且 `systemRole=ADMIN` 的 User。
- `systemRole=ADMIN` 的账号只用于系统管理，不参与项目。管理员需要参与项目时，必须另建 `systemRole=USER` 的业务账号。
- ADMIN 不能成为 Project.ownerId、ProjectMember.userId、PENDING ProjectInvitation.inviteeId 或 Task.assigneeId。
- User 从 USER 修改为 ADMIN 前，必须先转移其负责的项目、移除其项目成员关系，并处理其 PENDING 邀请。
- User 仍是 Project 的 owner 或仍负责未完成 Task 时，不能被停用。
- 已停用的 User 不能登录、接收邀请、加入项目或被分配新任务。
- Project.startDate 和 Project.endDate 同时存在时，endDate 必须大于或等于 startDate。
- Project.ownerId 必须指向 `active=true` 且 `systemRole=USER` 的 User，并且该 User 必须同时存在于该项目的 ProjectMember 中。
- 创建 ProjectMember 时，userId 必须指向 `active=true` 且 `systemRole=USER` 的 User。User 后续停用时保留既有 ProjectMember 作为历史参与关系，但该用户不能登录、接受新邀请、加入新项目或被分配新任务。
- owner 创建邀请时，ProjectInvitation.invitedBy 必须是当前 Project.ownerId。
- ProjectInvitation.inviteeId 必须指向 `active=true`、`systemRole=USER` 且尚未加入该项目的 User。
- PENDING 邀请只能转换为 ACCEPTED、REJECTED 或 CANCELLED。用户当前不是项目成员时，重新邀请可以将非 PENDING 记录重置为 PENDING。
- 接受 ProjectInvitation 时，必须在同一个事务中创建对应的 ProjectMember 并更新邀请状态。
- Task.assigneeId 必须指向 Task.projectId 对应项目中 `systemRole=USER` 的有效成员。
- Task.parentId 必须指向同一项目中的 Task。
- 项目日期范围存在时，Task.dueDate 必须位于该范围内。
- Summary.taskId 存在时，必须指向 Summary.projectId 对应项目中的 Task。
- 外键默认使用 `ON DELETE RESTRICT`；User 使用 `active=false` 停用，不进行物理删除。
- 只有 `archivedAt` 非空的 Project 才能永久删除。删除是显式聚合操作，必须在同一个事务中删除其 Summary、TaskLog、Task、ProjectInvitation、ProjectMember 和 Project；任一步失败时整体回滚，不依赖数据库 `ON DELETE CASCADE`。
- Task 存在子任务、TaskLog 或 Summary 时不能删除。

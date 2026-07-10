# FlowSync 技术与工程方案

> 依据 `FlowSync.pdf` 的业务需求重新选型。保留其核心业务，不照搬其中的教学简化认证、Vue CLI、明文密码和未版本化数据库脚本。
>
> 方案日期：2026-07-10

## 1. 最终结论

FlowSync 采用“单仓库、前后端分离、模块化单体”结构：

- 后端：JDK 21 + Spring Boot 3.5.16 + Spring Security + MyBatis-Plus 3.5.16。
- 前端：Node.js 24 LTS + Vue 3 + TypeScript + Vite + Vue Router + Pinia + Element Plus + Axios。
- 数据库：MySQL 8.4 LTS，Flyway 管理表结构和种子数据。
- AI：阿里云百炼/DashScope Java SDK，模型和 API Key 均由环境变量配置。
- 构建：后端只使用 Maven Wrapper，前端只使用 pnpm 和 `pnpm-lock.yaml`；mise 作为推荐但非强制的统一工具链入口。
- 本地依赖：Docker Compose 只启动 MySQL；前后端在宿主机运行，便于断点、热更新和查看日志。
- 认证：Spring Security 服务端 Session + HttpOnly Cookie + CSRF，不自制 token，不从前端传 `currentUserId`。
- 密码：BCrypt 哈希；数据库只保存 `password_hash`。
- 权限：后端强制校验，前端菜单隐藏只负责用户体验，不能作为安全边界。
- 数据库迁移：每次结构修改新增 Flyway 脚本，不手工修改已共享数据库，也不修改已经执行过的迁移。

这是一个单体系统，不拆微服务。当前规模下，微服务只会增加部署、认证、事务和调试成本。

## 2. 相比指导书的主要调整

| 指导书方案 | 本方案 | 原因 |
| --- | --- | --- |
| Vue CLI | Vite | Vue CLI 已进入维护模式，新项目应使用 Vite。 |
| JavaScript | TypeScript | API DTO、权限和 AI 返回结构会持续变化，类型检查能减少联调返工。 |
| 所有功能集中在 `HomeView.vue` | 按业务功能拆分前端目录 | 避免单文件膨胀，功能可独立调试和迁移。 |
| 简单 token + `sessionStorage` | Spring Security Session + HttpOnly Cookie | 浏览器脚本不能读取会话 Cookie，避免自行处理 token 生命周期。 |
| `currentUserId` 查询参数 | 从认证上下文获取当前用户 | 前端传入的用户 ID 不可信。 |
| 仅前端控制权限 | 后端授权 + 前端显示控制 | 隐藏按钮不能阻止直接调用 API。 |
| 明文密码 | BCrypt | 从项目第一天避免密码迁移和泄露风险。 |
| API Key 写入配置默认值 | `DASHSCOPE_API_KEY` 环境变量 | 防止密钥进入 Git 历史。 |
| 普通 SQL 初始化 | Flyway 版本迁移 | 新机器、测试库和后续升级使用相同结构。 |
| SpringDoc 2.1.0 | SpringDoc 2.8.x | 官方兼容矩阵中，Spring Boot 3.5.x 对应 2.8.x。 |
| 全局“负责人”角色 | 系统角色 + 项目成员关系 | 同一个用户可以负责项目 A、仅参与项目 B，无需后续重做权限模型。 |

## 3. 技术栈

### 3.1 后端

| 技术 | 建议版本 | 用途与约束 |
| --- | --- | --- |
| JDK | 21 LTS | 统一开发、CI 和生产版本；`pom.xml` 设置 `release=21`。 |
| Spring Boot | 3.5.16 | 使用 3.x 成熟生态；暂不升级到 4.x，避免课程期内的框架迁移。 |
| Spring MVC | 由 Boot 管理 | REST API；当前业务不需要 WebFlux。 |
| Spring Security | 由 Boot 管理 | 登录、Session、CSRF、URL/方法授权、BCrypt。 |
| Jakarta Validation | 由 Boot 管理 | 在请求 DTO 上做必填、长度、范围和邮箱校验。 |
| MyBatis-Plus | 3.5.16 | 单表 CRUD、条件查询和分页；使用 Boot 3 starter。 |
| MySQL Connector/J | 由 Boot 管理 | 不单独覆盖版本，减少依赖冲突。 |
| Flyway | 由 Boot 管理 | 表结构、索引、外键和种子数据迁移。 |
| Spring Boot Actuator | 由 Boot 管理 | 只开放 health/info，便于本地和部署环境检查。 |
| SpringDoc OpenAPI | 2.8.x | Swagger UI 和 OpenAPI 描述。 |
| DashScope Java SDK | 在 `pom.xml` 固定兼容版本 | 调用千问；模型名与 Key 外部配置。 |
| Jackson | 由 Boot 自带 | JSON 序列化和 AI 结构化结果解析，不再引入另一套 JSON 库。 |
| JUnit 5 / Spring Boot Test | 由 Boot 管理 | 后端测试。 |
| Spring Security Test | 由 Boot 管理 | 验证匿名、成员、负责人和管理员权限。 |

不使用 Lombok、MapStruct、通用 Repository 封装或 Service 接口加唯一实现。Java record 可承担简单请求/响应 DTO，MyBatis-Plus 的 `BaseMapper` 已经覆盖基础数据访问。

### 3.2 前端

| 技术 | 建议版本策略 | 用途与约束 |
| --- | --- | --- |
| Node.js | 24 LTS | 不使用本机的 Current 版本作为团队基线。 |
| pnpm | 11.x | 安装依赖、执行脚本、提交 lockfile。 |
| Vue | 当前稳定 3.x | Composition API + `<script setup lang="ts">`。 |
| TypeScript | 当前稳定 6.x | API、表单、权限和 AI 返回数据类型。 |
| Vite | `create-vue` 当前稳定模板 | 开发服务器、代理、构建和热更新。 |
| Vue Router | 当前稳定 5.x | 登录页和各业务页面路由。 |
| Pinia | 当前稳定版 | 只存认证用户和少量跨页面状态；服务端数据不长期缓存。 |
| Element Plus | 当前稳定 2.x | 表格、表单、弹窗、菜单和反馈组件。 |
| Axios | 当前稳定 1.x | 统一 `baseURL`、Cookie、CSRF 和错误处理。 |
| Vitest | 与 Vite 模板匹配 | 少量前端逻辑测试。 |

`package.json` 声明 `packageManager` 和 `engines.node`，并提交 `pnpm-lock.yaml`。前端不得保存数据库密码或 DashScope Key；`VITE_*` 变量会被打进浏览器产物，不适合存放秘密。

### 3.3 数据与运行环境

| 技术 | 选择 | 说明 |
| --- | --- | --- |
| MySQL | 8.4 LTS | 与指导书 MySQL 8.x 一致，使用 `utf8mb4`。 |
| Docker Compose | 当前 Compose v2 | 本地只运行 MySQL，统一端口、初始化和数据卷。 |
| Maven Wrapper | 项目内固定 Maven 3.9.x | 使用 `./mvnw`，开发者无需全局安装 Maven。 |
| Maven Daemon (`mvnd`) | 可选、本地使用 | Maven 构建明显变慢时再装；CI 和 README 仍以 `./mvnw` 为准。 |
| mise | 当前稳定版 | 推荐安装；由根目录 `mise.toml` 统一 Java 21、Node 24、pnpm 11、环境变量和常用任务。 |

Java 没有一个工具完全等同于 Python `uv`。本项目用以下组合覆盖同样的职责：

- Maven Wrapper：固定构建器版本并解析 Java 依赖。
- pnpm + lockfile：固定前端包和快速安装。
- mise：安装并选择 Java、Node 和 pnpm，统一加载 `.env` 和常用命令。
- Docker Compose：固定 MySQL 运行环境。

mise 不替代 `mvnw`、`pnpm-lock.yaml` 或 Compose。没有安装 mise 的开发者仍可直接使用标准命令；README 必须同时保留这些命令。Gradle Wrapper 也是合格选择，但指导书和团队认知都偏 Maven。本项目不要同时保留 `pom.xml` 和 `build.gradle`，两套构建定义会产生漂移。

## 4. 总体架构

```text
Browser
  │  同源 /api；开发时由 Vite 代理
  ▼
Spring Boot 模块化单体
  ├─ Spring Security：Session、CSRF、认证、授权
  ├─ 业务模块：用户、项目、任务、进度、总结、总览
  ├─ AI 模块：Prompt、DashScope、结果校验、兜底
  └─ MyBatis-Plus + Flyway
       │
       ▼
    MySQL 8.4
```

开发时：

- `localhost:8081`：Vite。
- `localhost:8080`：Spring Boot。
- `localhost:3306`：Compose 中的 MySQL。
- Vite 将 `/api` 代理到 `http://localhost:8080`，浏览器始终只访问 8081，Cookie 和 CSRF 更容易调试。

生产时优先采用同源部署：Nginx/网关提供前端静态文件并把 `/api` 转发到后端。只有明确需要移动客户端、第三方 API 或多个独立服务时，才改成 OAuth2/OIDC 和访问令牌。

## 5. 后端模块职责

后端按业务功能组织，而不是把所有 Controller、Service、Mapper 分别堆在全局目录中。每个模块只包含它真正需要的层。

| 模块 | 主要职责 |
| --- | --- |
| `auth` | 登录、退出、当前用户、修改密码、认证失败响应。 |
| `user` | 用户资料、成员列表、管理员创建/禁用用户。永不返回密码哈希。 |
| `project` | 项目 CRUD、项目负责人、项目成员关系、项目级权限。 |
| `task` | 父子任务、负责人、状态、优先级、截止日期和项目内查询。 |
| `progress` | 任务进度记录、百分比校验和时间线。 |
| `summary` | 阶段总结、最终总结，可选关联任务。 |
| `overview` | 用户可见范围内的项目、任务、进度和总结统计。 |
| `ai` | 单任务建议、任务计划、结果校验、兜底计划和导入。 |
| `common` | API 错误、全局异常处理、安全配置、审计字段等真正共享的代码。 |

### 5.1 权限模型

不要把“负责人”永久写成用户的全局角色。采用两级权限：

- 系统角色：`ADMIN`、`USER`。
- 项目关系：`OWNER`、`MEMBER`，存储在 `project_member`。

核心规则：

- `ADMIN`：管理用户，可查看全部数据。
- 项目 `OWNER`：编辑项目、管理成员、创建/编辑/删除项目内任务、使用 AI 拆解并导入。
- 项目 `MEMBER`：查看参与项目；更新分配给自己的任务状态；新增进度和总结。
- 未加入项目的用户：不能读取该项目及其任务。
- Controller 可做粗粒度角色限制，Service 必须做项目归属、成员关系和任务负责人校验。

### 5.2 认证流程

1. `POST /api/auth/login` 接收用户名和密码。
2. Spring Security 使用 `PasswordEncoder` 校验 BCrypt 哈希。
3. 成功后创建服务端 Session，浏览器只收到 HttpOnly Session Cookie。
4. `GET /api/auth/me` 返回当前用户的安全视图和必要权限。
5. Pinia 只保存当前用户展示数据，不保存可重放的自制 token。
6. 修改密码后使其他 Session 失效；至少使当前 Session 重新认证。
7. `POST /api/auth/logout` 注销 Session，并刷新/清理 CSRF 状态。

Cookie 的生产配置至少包括 `HttpOnly`、`Secure`、合适的 `SameSite` 和合理过期时间。使用 Cookie 认证时保持 Spring Security CSRF 防护，Axios 统一发送 CSRF Header。

### 5.3 AI 模块边界

- AI 只生成草稿，不直接写任务表。
- System Prompt、模型名、超时和最大输出长度集中在 `ai` 模块配置中。
- 要求结构化 JSON；使用 Jackson 解析为 DTO。
- 校验标题、优先级、建议天数和 `assigneeId`。
- AI 返回不存在的成员 ID 时，不静默分配给第一个人；将其置空并要求负责人确认，避免错误归责。
- API 调用失败或 JSON 无效时返回固定兜底计划，并明确标记 `fallback=true`。
- 导入接口重新验证项目权限、成员归属和所有字段，不能信任前端回传的 AI 草稿。
- `DASHSCOPE_API_KEY` 只存在于后端环境变量。

## 6. 数据库设计调整

保留指导书的核心表，并新增项目成员表：

| 表 | 作用 | 关键调整 |
| --- | --- | --- |
| `sys_user` | 用户 | `password_hash`；系统角色；启用状态；创建/更新时间。 |
| `project_info` | 项目 | `owner_id`；状态、优先级、日期；创建/更新时间。 |
| `project_member` | 项目成员 | `(project_id, user_id)` 唯一；项目角色；加入时间。 |
| `task_info` | 任务 | 项目、父任务、负责人、创建人、状态、优先级、截止日期。 |
| `task_log` | 进度 | 数据库约束或服务校验确保进度为 0-100。 |
| `task_summary` | 总结 | 项目必填，任务可空，保存创建人和类型。 |

数据库规则：

- 所有表使用 `BIGINT` 主键、`utf8mb4` 和明确外键。
- 常用过滤列建立索引：`project_id`、`assignee_id`、`task_id`、`owner_id`、`create_time`。
- 状态和优先级在 Java 中用 enum，在数据库中保存稳定英文代码，如 `TODO/IN_PROGRESS/DONE`，中文只由前端展示。
- 外键默认 `RESTRICT`，避免误删项目后静默清空历史；需要删除时由事务显式处理。
- 不使用数据库自动建表；Hibernate/MyBatis 不负责 schema 演进。
- `V1__init_schema.sql` 创建结构，`V2__seed_demo_users.sql` 写入 BCrypt 后的演示用户。
- 已经应用的 `V*.sql` 不修改；任何变化新增下一条迁移。

暂不加入软删除、审计日志表和乐观锁。出现恢复误删、合规审计或多人同时编辑冲突的真实需求时再加。

## 7. API 约定

使用标准 HTTP 状态码，不再用 `200 OK + success=false` 表示失败：

- 成功：直接返回资源或列表。
- 创建成功：`201 Created`。
- 参数错误：`400 Bad Request`。
- 未登录：`401 Unauthorized`。
- 已登录但无权限：`403 Forbidden`。
- 资源不存在：`404 Not Found`。
- 冲突：`409 Conflict`。
- 错误体：Spring `ProblemDetail`，包含稳定的 `code`、可读的 `detail` 和必要字段错误。

初始接口：

```text
POST   /api/auth/login
POST   /api/auth/logout
GET    /api/auth/me
PUT    /api/auth/password

GET    /api/users
POST   /api/users                         # ADMIN
PATCH  /api/users/{id}/status             # ADMIN

GET    /api/projects
POST   /api/projects
GET    /api/projects/{id}
PUT    /api/projects/{id}
DELETE /api/projects/{id}
GET    /api/projects/{id}/members
POST   /api/projects/{id}/members
DELETE /api/projects/{id}/members/{userId}

GET    /api/tasks?projectId=&assigneeId=
POST   /api/tasks
GET    /api/tasks/{id}
PUT    /api/tasks/{id}                    # OWNER
PATCH  /api/tasks/{id}/status             # OWNER 或任务负责人
DELETE /api/tasks/{id}

GET    /api/task-logs?taskId=
POST   /api/task-logs
GET    /api/summaries?projectId=&taskId=
POST   /api/summaries
GET    /api/overview

POST   /api/ai/task-suggestion
POST   /api/ai/task-plan
POST   /api/ai/task-plan/import
```

列表数据增长后再加入分页，但查询参数和响应结构应预留稳定命名。OpenAPI 文档用于联调，不在初期引入前端客户端代码生成器；当前接口数量下，手写的类型化 `api.ts` 更容易阅读和排错。

## 8. 目标文件夹结构

```text
FlowSync/
├── docs/
│   ├── FlowSync.pdf
│   └── summary.md
├── README.md
├── .gitignore
├── .env.example
├── mise.toml                     # 推荐工具链与任务入口
├── compose.yaml                  # 仅 MySQL
│
├── backend/
│   ├── pom.xml
│   ├── mvnw
│   ├── mvnw.cmd
│   ├── .mvn/
│   │   └── wrapper/
│   │       └── maven-wrapper.properties
│   └── src/
│       ├── main/
│       │   ├── java/hgc/flowsync/
│       │   │   ├── FlowSyncApplication.java
│       │   │   ├── common/
│       │   │   │   ├── api/
│       │   │   │   ├── config/
│       │   │   │   ├── exception/
│       │   │   │   └── security/
│       │   │   ├── auth/
│       │   │   ├── user/
│       │   │   ├── project/
│       │   │   ├── task/
│       │   │   ├── progress/
│       │   │   ├── summary/
│       │   │   ├── overview/
│       │   │   └── ai/
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-dev.yml
│       │       └── db/migration/
│       │           ├── V1__init_schema.sql
│       │           └── V2__seed_demo_users.sql
│       └── test/
│           ├── java/hgc/flowsync/       # 镜像业务包结构
│           └── resources/application-test.yml
│
└── frontend/
    ├── package.json
    ├── pnpm-lock.yaml
    ├── vite.config.ts
    ├── tsconfig.json
    ├── index.html
    └── src/
        ├── main.ts
        ├── App.vue
        ├── router/index.ts
        ├── stores/auth.ts
        ├── layouts/AppLayout.vue
        ├── shared/
        │   ├── api/http.ts
        │   ├── components/
        │   └── types/
        └── features/
            ├── auth/
            ├── overview/
            ├── users/
            ├── projects/
            ├── tasks/
            ├── progress/
            ├── summaries/
            └── ai/
```

每个后端业务目录可直接包含 `Controller`、`Service`、`Mapper`、实体和 `dto/`；只有文件数量确实增加后再拆子目录。前端 feature 也遵循同样规则，不为只有一个文件的概念创建多层空目录。

## 9. 配置与秘密

仓库只提交 `.env.example`，不提交 `.env`。建议变量：

```dotenv
MYSQL_DATABASE=flowsync
MYSQL_USER=flowsync
MYSQL_PASSWORD=change-me
MYSQL_ROOT_PASSWORD=change-root-password

DB_URL=jdbc:mysql://localhost:3306/flowsync?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
DB_USERNAME=flowsync
DB_PASSWORD=change-me

DASHSCOPE_API_KEY=
QWEN_MODEL=qwen-plus
```

Compose 会自动读取根目录 `.env`，Spring Boot 不会。通过 `mise run` 启动时，`mise.toml` 的 `env._.file` 将 `.env` 注入任务；不使用 mise 时，需先在 shell 导出 `.env`，或在 IDE Run Configuration 中配置这些变量。可以给非秘密项提供合理默认值，但数据库密码和 DashScope Key 不提供可工作的默认秘密。前端只保留 `/api` 基础路径，不接触后端秘密。

开发日志配置放在 `application-dev.yml`：

- 应用包 DEBUG。
- MyBatis SQL 日志仅在需要时开启，避免长期输出敏感字段。
- Spring Security DEBUG 仅在排查认证问题时临时开启。
- 生产配置不输出 SQL、密码、Cookie、Session ID 或 API Key。

## 10. 构建、调试与迁移流程

### 10.1 新机器一次性准备

推荐路径需要：

- mise。
- Docker Desktop 或兼容的 Docker Engine + Compose v2。

执行 `mise trust && mise install` 后，mise 自动安装项目指定的 JDK 21、Node.js 24 LTS 和 pnpm 11。不需要全局 Maven；仓库中的 Maven Wrapper 会取得项目指定版本。

不使用 mise 时，手工安装 JDK 21、Node.js 24 LTS 和 pnpm 11，然后使用后面的原生命令，功能完全相同。

### 10.2 mise 最小配置

根目录 `mise.toml` 只管理工具、`.env` 和常用入口，不启用 monorepo 模式、实验性 hooks 或 mise lockfile：

```toml
[tools]
java = "temurin-21"
node = "24"
pnpm = "11"

[env]
_.file = { path = ".env", redact = true }

[tasks.db]
run = "docker compose up -d mysql"

[tasks."frontend:install"]
dir = "frontend"
run = "pnpm install --frozen-lockfile"

[tasks.backend]
dir = "backend"
env = { SPRING_PROFILES_ACTIVE = "dev" }
run = "./mvnw spring-boot:run"
run_windows = "mvnw.cmd spring-boot:run"

[tasks.frontend]
dir = "frontend"
depends = ["frontend:install"]
run = "pnpm dev"

[tasks."test:backend"]
dir = "backend"
run = "./mvnw test"
run_windows = "mvnw.cmd test"

[tasks."test:frontend"]
dir = "frontend"
run = "pnpm test"
```

mise 不负责安装 Docker，也不负责解析 Java/前端项目依赖；这些职责仍分别属于 Compose、Maven Wrapper 和 pnpm。

### 10.3 本地启动

推荐使用三个终端：

```bash
mise run db
mise run backend
mise run frontend
```

不使用 mise 时执行等价的原生命令：

```bash
# 1. 数据库
docker compose up -d mysql

# 2. 后端（终端一）
cd backend
set -a
source ../.env
set +a
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run

# 3. 前端（终端二）
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

后端启动时 Flyway 自动把数据库升级到最新版本。调试后端时可在 IntelliJ IDEA 中导入 `backend/pom.xml`，直接以 Debug 运行 `FlowSyncApplication`；不要为了断点调试把后端装进容器。

### 10.4 检查与构建

使用 mise：

```bash
mise run test:backend
mise run test:frontend
```

或使用原生命令：

```bash
# 后端
cd backend
./mvnw test
./mvnw clean verify
./mvnw package

# 前端
cd frontend
pnpm typecheck
pnpm test
pnpm build
```

常用调试入口：

- 前端：`http://localhost:8081`。
- 后端健康状态：`http://localhost:8080/actuator/health`，仅在加入 Actuator 后可用。
- Swagger UI：`http://localhost:8080/swagger-ui.html`。
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`。
- MySQL 日志：`docker compose logs -f mysql`。

Actuator 只需加入 health/info，并在生产中限制暴露；不开放 env、heapdump 等敏感端点。

### 10.5 迁移到另一台机器

1. 克隆仓库。
2. 安装 mise 和 Docker。
3. 从 `.env.example` 创建本地 `.env` 并填写秘密。
4. 执行 `mise trust && mise install`。
5. 分别执行 `mise run db`、`mise run backend` 和 `mise run frontend`。

不用 mise 时，手工安装 JDK 21、Node 24 和 pnpm，并执行上一节列出的原生命令。

代码、构建器版本、前端依赖和数据库结构都来自仓库；新机器无需复制 IDE 配置、全局 Maven 缓存或手工建表记录。

如果要迁移已有数据，使用 `mysqldump`/`mysql` 或受控备份恢复，不把真实数据 dump 和任何密钥提交到 Git。

## 11. 最小测试策略

测试只覆盖高风险边界，不追求每个 getter 或 CRUD 方法都有测试：

- 认证：正确密码、错误密码、禁用用户、退出。
- 授权：未登录 401；非项目成员 403；成员不能编辑他人任务；负责人可以管理项目。
- 密码：数据库中不出现明文，修改密码后旧密码失效。
- 项目成员：同一成员不能重复加入；负责人不能被误移除。
- 任务：进度范围、父任务同项目、负责人必须是项目成员。
- AI：正常 JSON、非法 JSON、无效成员 ID、调用失败兜底、导入时二次校验。
- Flyway：空 MySQL 能从 V1 迁移到最新版本。
- 前端：认证 store、路由守卫、Axios 对 401/403 的统一处理。

首轮不加入完整 E2E 测试集。登录到创建任务这一条主流程稳定后，再加一条 Playwright 冒烟测试即可。

## 12. 实施顺序

当前只执行环境和项目骨架阶段；业务开发在明确开始后再继续。

1. **已完成**：创建根目录 `mise.toml`、Compose、Maven Wrapper 和 Vite/pnpm 工程，并验证前后端基础构建。
2. 写 Flyway V1/V2，建立用户、项目成员和核心业务表。
3. 完成 Spring Security、BCrypt、Session、CSRF、登录/退出/当前用户。
4. 完成用户、项目和项目成员模块，先把授权边界跑通。
5. 完成任务、进度、总结和总览。
6. 完成前端页面和权限显示。
7. 最后接入 DashScope；先用固定假数据完成任务拆解页面和导入流程。
8. 加入最小测试、OpenAPI 文档和构建检查。

AI 放在最后，因为它依赖项目、成员和任务导入流程；提前接入只会增加联调变量。

骨架阶段没有数据库迁移，`spring.flyway.enabled=false`。创建首个 `V1` 脚本时再启用 Flyway；当前不实现用户、认证或其他业务模块。

## 13. 当前明确不做

- 不拆微服务，不加 API Gateway。
- 不加 Redis、Kafka、RabbitMQ。
- 不使用 JWT；出现移动端、第三方客户端、多服务或统一身份平台时改用标准 OAuth2/OIDC。
- 不同时维护 Maven 和 Gradle。
- 不让 mise 替代 Maven Wrapper、pnpm lockfile 或 Compose，也不启用其 monorepo/实验性功能。
- 不把前后端都容器化后再做日常断点调试。
- 不加 Kubernetes。
- 不加前端 OpenAPI 客户端生成器。
- 不加通用 DAO、BaseService、复杂领域事件或插件体系。
- 不做软删除和无限审计日志；有恢复/合规需求时再加。
- 不把整页塞入一个 `HomeView.vue`。

## 14. 当前机器检查结果

检查日期：2026-07-10。

- 当前默认 Java 是 26.0.1；Spring Boot 3.5.16 官方支持到 Java 25，因此项目应安装并固定 JDK 21。
- 当前 Node.js 是 26.5.0 Current；项目应固定 Node.js 24 LTS。
- mise 2026.7.5 已安装；项目目录固定 Temurin 21.0.11、Node.js 24.18.0 和 pnpm 11.10.0，不覆盖系统默认工具链。
- 不要求全局 Maven；`backend/mvnw` 已提交到项目骨架并负责下载固定版本。
- OrbStack 已运行，Compose 中的 MySQL 8.4 健康；数据库只绑定到 `127.0.0.1:3306`。
- 当前本机开发数据卷曾运行过试验迁移。它不影响关闭 Flyway 的空骨架；正式编写首个迁移前应执行一次 `docker compose down -v` 重建空库。该命令会删除本地数据库数据，因此不作为自动初始化步骤执行。
- 当前全局 Gradle 无法加载本机 native library；本方案不采用 Gradle，因此无需围绕它排障。

## 15. 官方依据

- [Spring Boot 3.5 系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)：3.5.16、Java 17-25、Maven/Gradle 支持范围。
- [Apache Maven Wrapper](https://maven.apache.org/tools/wrapper/index.html)：项目固定 Maven 版本，使用 `mvnw` 自动下载并运行。
- [Vue CLI 维护模式说明](https://cli.vuejs.org/guide/creating-a-project)：新项目推荐 `create-vue` 和 Vite。
- [Vite 官方入门](https://vite.dev/guide/)：Vue 模板、开发和构建入口。
- [Node.js 发布状态](https://nodejs.org/en/about/previous-releases)：生产应用应使用 LTS；当前 Node 24 为 LTS、26 为 Current。
- [pnpm 安装文档](https://pnpm.io/installation)：pnpm 11 和 Node.js 前置要求。
- [MyBatis-Plus Spring Boot 3 Starter](https://baomidou.com/en/getting-started/)：Boot 3 starter 和当前依赖方式。
- [SpringDoc 兼容矩阵](https://springdoc.org/)：Spring Boot 3.5.x 对应 SpringDoc 2.8.x。
- [Spring Security 6.5 PasswordEncoder](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/passwords/password-encoder.html)：密码安全存储接口。
- [Spring Security 6.5 SPA CSRF](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html)：Session/Cookie SPA 的 CSRF 处理。
- [Flyway 版本化迁移](https://documentation.red-gate.com/fd/versioned-migrations-273973333.html)：迁移顺序、历史表和校验和。
- [Docker Compose](https://docs.docker.com/compose/)：用单一 YAML 统一服务、网络和数据卷。
- [mise 入门](https://mise.jdx.dev/getting-started)：统一安装和选择多语言工具版本。
- [mise 环境文件](https://mise.jdx.dev/environments/)：通过 `env._.file` 加载并隐藏 `.env` 值。
- [mise TOML 任务](https://mise.jdx.dev/tasks/toml-tasks.html)：统一执行不同目录中的开发和测试命令。

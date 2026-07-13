# FlowSync 开发环境复现指南

本文面向首次拿到仓库的开发者。项目使用 mise 管理 Java、Node.js 和 pnpm，Maven Wrapper 管理 Maven，Docker Compose 管理 MySQL。除 Git、mise 和 Docker 运行时外，不要手动安装全局 Java、Node.js、pnpm、Maven 或 MySQL。

## 统一环境

仓库配置会提供：

- Eclipse Temurin JDK 21
- Node.js 24、pnpm 11
- Maven Wrapper 与后端依赖
- `pnpm-lock.yaml` 固定的前端依赖
- MySQL 8.4 容器

首次安装和首次构建需要联网；mise、Maven、pnpm 和 Docker 会自动下载并缓存所需内容。

## macOS

### 1. 安装必需软件

推荐使用 Homebrew 和 OrbStack：

```bash
brew install git mise orbstack
open -a OrbStack
```

也可以用 Docker Desktop 替代 OrbStack，但不要同时运行两个 Docker 引擎。

### 2. 启用目录级环境切换

在 `~/.zshrc` 中加入：

```zsh
eval "$(mise activate zsh)"
```

重新打开终端，或执行：

```bash
source ~/.zshrc
```

mise 只会在进入含 `mise.toml` 的目录时加载项目环境，离开后自动恢复。

### 3. 获取并初始化项目

```bash
git clone <repository-url> FlowSync
cd FlowSync
cp .env.example .env
mise trust
mise install
mise run db
```

按需修改 `.env` 中的本地密码和 `DASHSCOPE_API_KEY`，不要提交该文件。

## Windows

以下命令使用 PowerShell。Docker Desktop 使用 WSL 2 Linux 容器。

### 1. 安装必需软件

在管理员 PowerShell 中安装或更新 WSL，然后重启 Windows：

```powershell
wsl --install
wsl --update
```

在普通 PowerShell 中安装工具：

```powershell
winget install --id Git.Git -e
winget install --id jdx.mise -e
winget install --id Docker.DockerDesktop -e
```

启动 Docker Desktop，并确认使用 Linux containers。

### 2. 启用目录级环境切换

```powershell
New-Item -ItemType File -Path $PROFILE -Force
notepad $PROFILE
```

在打开的文件中加入并保存：

```powershell
(&mise activate pwsh) | Out-String | Invoke-Expression
```

重新打开 PowerShell。如果脚本被禁止，只需为当前用户执行一次：

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

### 3. 获取并初始化项目

```powershell
git clone <repository-url> FlowSync
Set-Location FlowSync
Copy-Item .env.example .env
mise trust
mise install
mise run db
```

## VS Code 与 mise

始终用 VS Code 打开仓库根目录 `FlowSync/`，不要只打开 `backend/` 或 `frontend/`。

1. 安装 `hverlin.mise-vscode` 扩展，以及项目所需的 Java 和 Vue/TypeScript 扩展。
2. 信任当前 VS Code Workspace 和根目录 `mise.toml`。
3. 运行 `Mise: Open extension settings`，开启 `Configure Extensions Automatically`，关闭 `Include Global Mise Tools`；保持 `Use Shims` 和自动更新环境变量开启。
4. 运行 `Mise: Reload configuration`，再运行 `Developer: Reload Window`。

如果 Java 扩展仍提示缺少 JDK，运行 `Mise: Configure extension sdk path...`，选择 Java 扩展和 mise 的 Temurin 21，然后运行 `Java: Clean Java Language Server Workspace`。关闭旧的集成终端并新建终端后，用 `mise current` 验证版本。

不要提交扩展生成的 `.vscode/settings.json` 或 SDK 绝对路径；仓库根目录已忽略 `.vscode/`。每位开发者在自己的 IDE 中配置一次即可。

## IntelliJ IDEA 与 mise

1. 在 `Settings | Plugins | Marketplace` 安装 `Mise` 插件并重启 IDEA。
2. 打开仓库根目录，信任 `mise.toml`，再将 `backend/pom.xml` 作为 Maven 项目导入。
3. 在 `File | Project Structure | Project SDK` 选择 mise 提供的 Temurin 21。如果未自动出现，在项目终端执行 `mise where java`，通过 `Add SDK | JDK` 添加该目录。
4. 在 `Settings | Build Tools | Maven` 中，将 Importer JDK 和 Runner JRE 都设为 `Project SDK`，避免 Maven 使用系统 JDK。
5. 如果 IDEA 提供 Node.js 支持，在 `Settings | Languages & Frameworks | Node.js` 中选择 mise 的 Node 24；未自动出现时，用 `mise which node` 获取本机路径。
6. 通过 Mise 工具窗口运行仓库任务，并启用插件对 Run Configuration 的 mise 环境加载，使 Spring Boot 获得 `.env` 中的数据库变量。

IDEA 启动后不会自动感知所有 `mise.toml` 或 `.env` 变更。修改配置后重新加载 Mise 配置、重启对应 Run Configuration；仍不一致时重启 IDEA。不要把 `.env` 内容或本机 SDK 路径写入共享的 `.idea/` 文件。

## 验证环境

在项目根目录执行：

```bash
mise current
java -version
node --version
pnpm --version
docker compose ps
mise run test:backend
mise run test:frontend
mise run test:mock
```

预期 Java 为 21、Node.js 为 24、pnpm 为 11，且 MySQL 状态为 `healthy`。后端和前端测试都应通过。

## 前端 Mock API

前端使用 MSW 在浏览器中拦截 `/api` 请求。截至当前实现，Mock 覆盖 `docs/api.md` 定义的 45 个接口，
用于后端尚未完成时独立开发页面、状态管理和错误提示。

- 接口契约以 `docs/api.md` 为准，数据关系以 `docs/relationship.md` 为准。
- Mock 入口位于 `frontend/src/main.ts`，handlers 位于 `frontend/src/mocks/handlers/`。
- 初始数据位于 `frontend/src/mocks/store.ts`。
- Mock 只在 Vite 开发模式且 `VITE_ENABLE_MOCK=true` 时启动。
- Mock 数据只保存在当前页面内存中，刷新页面会恢复初始数据。

### 启动 Mock 模式

确认仓库根目录的 `.env` 包含：

```dotenv
VITE_ENABLE_MOCK=true
```

然后在仓库根目录执行：

```bash
mise run frontend
```

`mise run frontend` 会先根据 `pnpm-lock.yaml` 安装依赖，再在 `http://localhost:8081` 启动前端。
Mock 模式不需要启动后端、MySQL 或配置 AI Key。修改 `.env` 后必须重启 Vite。

浏览器开发者工具出现 `[MSW] Mocking enabled`，并且
`http://localhost:8081/mockServiceWorker.js` 可以访问时，表示 Mock Worker 已启动。

### 切换到真实后端

将 `.env` 改为：

```dotenv
VITE_ENABLE_MOCK=false
```

先启动数据库：

```bash
mise run db
```

然后分别启动两个长驻进程。

终端 1：

```bash
mise run backend
```

终端 2：

```bash
mise run frontend
```

不要在同一个前端进程中同时使用 Mock 和真实后端；环境变量修改后未重启时，旧配置仍会继续生效。

### 初始账号和数据

以下账号只用于本地 Mock，不能用于真实环境：

| 用户名 | 密码 | 用途 |
| --- | --- | --- |
| `admin` | `admin1234` | ADMIN；仅用于系统管理，也是 Mock 启动后的默认当前用户 |
| `zhangsan` | `user1234` | 项目 owner |
| `lisi` | `user1234` | 项目成员和任务 assignee |
| `wangwu` | `user1234` | 有一条待处理邀请的普通用户 |
| `inactive` | `user1234` | 用于测试停用用户错误 |

初始状态包含普通项目、空项目、归档项目、项目成员、待处理邀请、任务、进度日志、总结和 AI Plan
响应。登录、创建、修改、归档、恢复和删除产生的变化会保留到当前页面刷新为止。

### CSRF 和会话限制

Mock 与接口契约一样，要求所有 `POST`、`PUT`、`DELETE` 请求携带 `X-CSRF-TOKEN`：

1. 调用 `GET /api/auth/csrf`。
2. 从响应读取 `token` 和 `headerName`。
3. 在后续写请求中使用返回的请求头名称和值。

Mock 使用内存中的当前用户模拟登录状态，不会创建真正的 HttpOnly Session Cookie。因此它可以验证页面权限和
CSRF 请求流程，但不能替代后端对 Cookie、`withCredentials`、多会话失效和会话过期的集成测试。

### 错误响应和输入校验

Mock 按 `docs/api.md` 返回 RFC 9457 Problem Details：

- JSON 语法错误或请求体不是 JSON 对象时返回 `400 BAD_REQUEST`，响应类型为
  `application/problem+json`。
- 查询枚举、布尔值、日期、联系方式、AI 参数和资源字段会在运行时校验；TypeScript 类型不能替代该校验。
- 字段或业务规则校验失败通常返回 `422 VALIDATION_ERROR`，资源冲突返回对应的 `409` 错误码。
- 批量成员、邀请和 AI Plan 导入会先校验全部元素。失败响应的 `errors` 包含
  `userIds[1]`、`items[2].title` 等字段路径，并且不会保留本次请求的部分写入。

### 运行 Mock 测试

首次克隆仓库且尚未运行过前端时，先安装锁定版本的依赖：

```bash
mise run frontend:install
```

只检查 Mock API：

```bash
mise run test:mock
```

也可以在 `frontend/` 目录执行：

```bash
pnpm test:mock
```

截至当前实现，基线应显示 3 个 Mock 测试文件、85 项测试全部通过。测试数量以后可能增加，最终以命令输出为准；
判断成功的标准是所有测试通过且命令退出码为 `0`。

Mock 专项测试覆盖：

- 45 个接口是否全部注册以及主要成功状态码。
- 资源响应字段、Problem Details 和 `Content-Type`。
- 登录、CSRF、角色和项目权限。
- 邀请接受、任务进度、AI Plan 导入等状态变化。
- 归档写保护、分页排序、查询参数、枚举、日期、联系方式和 AI 参数校验。
- 成员与 AI Plan 批量请求的字段错误路径和失败回滚。

它不启动 Spring Boot、MySQL 或真实 AI 服务，也不验证真实 Session Cookie、数据库事务和网络代理。完整前端测试使用：

```bash
mise run test:frontend
```

### Mock 常见问题

- 页面仍请求真实后端：确认 `.env` 位于仓库根目录、值严格为 `true`，然后重启 Vite。
- `/mockServiceWorker.js` 返回 404：确认 `frontend/public/mockServiceWorker.js` 存在；缺失时在
  `frontend/` 执行 `pnpm exec msw init public --save`。
- 控制台出现 `captured a request without a matching request handler`：请求的方法或路径没有对应 handler，
  对照 `docs/api.md` 和 `frontend/src/mocks/handlers.ts` 检查。
- 写请求返回 `403 CSRF_INVALID`：先调用 `/api/auth/csrf`，再携带返回的 CSRF 请求头。
- 修改数据后刷新即消失：这是内存 Mock 的预期行为，不是数据保存失败。
- 测试出现 Node `localStorage` ExperimentalWarning：只要测试最终全部通过，该警告不表示 Mock 失败。
- pnpm 报 MSW build script 被忽略：确认 `frontend/pnpm-workspace.yaml` 中允许 `msw`，然后重新执行
  `mise run frontend:install`。

生产构建不会执行或注册 Mock。Vite 仍可能把通用的 `mockServiceWorker.js` 复制到 `dist/`，但生产入口受
`import.meta.env.DEV` 限制，不会加载 handlers 或注册该 Worker。

## 日常开发

使用 Mock 独立开发前端时只需：

```bash
mise run frontend
```

与真实后端联调时分别打开两个终端。

终端 1：

```bash
mise run backend
```

终端 2：

```bash
mise run frontend
```

后端运行在 `http://localhost:8080`，健康检查为 `/actuator/health`；前端运行在 `http://localhost:8081`。`mise run frontend` 会自动按锁文件安装前端依赖。

停止 MySQL但保留数据：

```bash
docker compose stop
```

仅在确定要删除全部本地数据库数据时执行：

```bash
docker compose down -v
```

## 常见问题

- `mise` 未生效：新开终端，确认当前目录是仓库根目录，再运行 `mise doctor`。
- Docker 无法连接：先启动 OrbStack 或 Docker Desktop，再运行 `docker version`。
- 3306 端口被占用：停止本机其他 MySQL，避免修改团队共享的 Compose 端口。
- Windows 后端启动失败：使用 PowerShell 运行 `mise run backend`，任务会自动调用 `mvnw.cmd`。
- 配置修改后未生效：关闭旧终端并重新进入项目目录。
- IDE 终端版本正确但代码分析版本错误：重新配置 IDE 的 SDK/解释器；Shell 激活不会自动修改已经启动的语言服务器。

## 官方参考

- [mise 安装与 Shell 激活](https://mise.jdx.dev/installing-mise.html)
- [mise IDE 集成](https://mise.jdx.dev/ide-integration.html)
- [Mise VS Code 扩展](https://github.com/hverlin/mise-vscode)
- [Mise IntelliJ 插件](https://plugins.jetbrains.com/plugin/24904-mise)
- [OrbStack Quick Start](https://docs.orbstack.dev/quick-start)
- [Docker Desktop for Windows](https://docs.docker.com/desktop/setup/install/windows-install/)

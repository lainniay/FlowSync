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
```

预期 Java 为 21、Node.js 为 24、pnpm 为 11，且 MySQL 状态为 `healthy`。后端和前端测试都应通过。

## 日常开发

分别打开两个终端：

```bash
mise run backend
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

## 官方参考

- [mise 安装与 Shell 激活](https://mise.jdx.dev/installing-mise.html)
- [OrbStack Quick Start](https://docs.orbstack.dev/quick-start)
- [Docker Desktop for Windows](https://docs.docker.com/desktop/setup/install/windows-install/)

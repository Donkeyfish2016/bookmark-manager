# Bookmark Manager CLI

一个轻量级、基于 Java 的命令行工具，用于管理书签和文件夹。支持标准的 CRUD 操作、导入/导出功能以及邮件分享。

## 项目概览

**Bookmark Manager CLI** 是一个轻量级、基于 Java 的命令行工具，专为高效管理书签和文件夹而设计。它提供了完整的增删改查（CRUD）能力，支持从 HTML 文件导入/导出书签，并可通过电子邮件分享书签集合。项目采用本地 SQLite 数据库进行持久化存储，同时提供交互式 Shell 界面以提升操作体验。支持 Docker 部署，便于容器化运行。

### 主要特性

- **本地 SQLite 持久化**：数据存储在本地 SQLite 数据库中，无需外部依赖，部署简单。
- **交互式 Shell 界面**：提供简单交互式命令行 Shell，支持连续执行多条命令，操作更便捷。
- **Docker 部署支持**：提供 Dockerfile 和 Docker Compose 配置，支持容器化一键部署。
- **邮件分享**：通过配置 SMTP 服务，支持将书签以邮件形式发送。
- **HTML 导入/导出**：支持标准 HTML 书签格式的导入与导出，便于与浏览器或其他工具互通。

## 技术栈

| 技术 | 版本/说明 |
|------|-----------|
| **Java** | 21 |
| **Maven** | 构建与依赖管理 |
| **SQLite** | 本地数据持久化（sqlite-jdbc） |
| **Picocli** | 4.7.5 — 命令行参数解析框架 |
| **Jakarta Mail** | Angus Mail 2.0.3 — 邮件发送支持 |
| **Docker & Docker Compose** | 容器化部署 |
| **Jsoup** | 1.22.2 — HTML 解析与处理 |
| **JUnit 5** | 5.10.2 — 单元测试 |

## 相关配置

#### 环境变量

| 环境变量 | 说明 | 默认值 |
|----------|------|--------|
| `BOOKMARK_DB_PATH` | SQLite 数据库文件路径 | `./bookmarkmgr.db` |
| `SMTP_HOST` | SMTP 服务器地址 | — |
| `SMTP_PORT` | SMTP 服务器端口 | — |
| `SMTP_USER` | SMTP 认证用户名 | — |
| `SMTP_PASS` | SMTP 认证密码或授权码 | — |

**注意**：SMTP 相关环境变量为可选配置。未配置时，邮件发送功能参数需通过命令行参数传入。

### Docker Compose 配置

`docker-compose.yml` 将宿主机目录挂载到容器的 `/data`，和`/tmp/html`，用于持久化数据库和导入/导出文件。

## 开发与测试说明

### 语言合规性

本项目所有核心功能均采用 **Java** 实现，以满足“单一语言开发”的要求。

项目中存在 `record.py` 脚本，该脚本**严格用于 Git 提交时记录与 AI 代理的交互轮次**，属于辅助工具，**不属于项目核心逻辑的一部分**，也不参与项目的构建、测试或部署流程。

### 测试体系

项目通过以下方式保障代码质量：

- **JUnit 5 单元测试**：覆盖服务层（Service）、数据访问层（DAO）及解析器/写入器（Parser/Writer）的核心逻辑。
- **功能测试脚本**：通过自定义 Shell 脚本（`src/test/scripts/command_test.sh`）对 CLI 命令进行端到端功能验证。

## 项目结构

```
bookmark-manager/
├── src/
│   ├── main/java/com/bookmark/
│   │   ├── cli/          # 命令行入口与子命令
│   │   ├── service/      # 业务逻辑层
│   │   ├── model/        # 数据模型
│   │   ├── dao/          # 数据访问层
│   │   └── html/         # HTML 导入/导出解析器
│   └── test/             # 单元测试与测试脚本
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

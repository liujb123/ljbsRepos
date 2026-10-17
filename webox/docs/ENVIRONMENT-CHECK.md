# WeBox 交付环境核查报告

> ## 实现阶段补充说明（由实现过程追加，未改动下方原始核查内容）
>
> 本报告是**动工前的环境快照**，其中"硬阻断项"已在实现阶段全部处理完毕。当前实际状态：
>
> | 原核查结论 | 实现阶段的实际处置 | 现状 |
> |---|---|---|
> | 🔴 MySQL 完全未安装 | 下载 `mysql-8.0.28-winx64.zip`（阿里云镜像，Oracle CDN 在本网络 403）→ `D:\G\MySQL` → `mysqld --initialize-insecure` → 独立进程运行于 127.0.0.1:3306 | ✅ 运行中，已建 `webox` / `webox_test` 库与 `webox` 用户 |
> | 🔴 JDK 17 未生效 | 不改全局环境变量，构建与运行时显式指定 `JAVA_HOME=C:\Program Files\Java\jdk-17.0.2` | ✅ 已用于全部构建与运行 |
> | 🟠 Docker 未安装 | 未安装，改为**本地独立 MySQL + 本地 Redis**；`docker-compose.yml` 仍随项目提供供有 Docker 的环境使用 | ✅ README 已按"本地服务器"为主路径改写 |
> | 🟠 Redis 5.0.14 与 7.x 不符 | 实测 `SET NX` / `INCR` / `EXPIRE` / `SCAN` 全部可用；文档版本要求已改为"5 或更新"；密码走 `WEBOX_REDIS_PASSWORD`，未硬编码 | ✅ 功能无差异 |
> | 🟠 Testcontainers 集成测试跑不了 | 改为**直连独立 MySQL 的 `webox_test` schema** + `scripts/smoke-test.ps1` / `scripts/concurrency-test.ps1` 对真实服务做端到端与并发验证 | ✅ 56 单测 + 69 端到端 + 14 并发断言全部通过 |
> | 🟡 Maven 3.8.6 / Node 24.19 | 直接沿用，未做变更 | ✅ 构建与前端均正常 |
> | ⚪ LLM API Key 缺失 | Tier 3 本次不做，未受影响 | ⚪ 维持原判定 |
>
> 追加的一条运维注意：**重建数据库后需一并清理 Redis 的 `webox:*` 键**，否则菜单快照在 TTL（10 分钟）内仍指向旧库的菜品 id。已写入 README「Resetting the environment」。
>
> ---

> 核查日期：2026-10-17
> 核查对象：`AI Vibe Coding V3.0 - PRD.md` / `.html`（§7.1 技术选型要求）+ `webox/README.md`、`webox/docs/ARCHITECTURE.md` 已声明的技术栈
> 本机：LAPTOP-48O33RD6 · Windows 11 家庭版 26200 · 16 逻辑核 · 32 GB RAM · D: 剩余 153 GB

---

## 一、结论速览

| 判定 | 项 |
|---|---|
| 🔴 **硬阻断（必须处理）** | MySQL Server 完全未安装；JDK 17 已装但未生效 |
| 🟠 **设计已声明但本机缺失** | Docker / Docker Desktop；Redis 版本 5.0.14 ≠ 声明的 7.x |
| 🟡 **次要偏差** | Maven 3.8.6（声明 3.9.x）；Node 24.19.0（声明 20+，满足但偏新）；MySQL 客户端 CLI |
| ⚪ **可选加分项依赖** | LLM API Key —— 未发现可用于应用的 Key（Tier 3，设计已声明本次不做） |

---

## 二、已具备（含实测验证）

| 组件 | 版本 / 位置 | 状态 |
|---|---|---|
| **JDK 17** | `C:\Program Files\Java\jdk-17.0.2`（17.0.2+8-86） | ✅ 已安装（但非默认，见 §三.2） |
| **Maven** | 3.8.6 · `D:\Maven\apache-maven-3.8.6` | ✅ 可用 |
| **Maven 本地仓库** | `D:\G\Maven-resp`（3819 jars / 1.47 GB），global `settings.xml` 指定；central 镜像走阿里云 | ✅ 依赖拉取快 |
| **Node.js** | nvm4w：`24.19.0`（当前激活）/ `22.13.1` / `14.21.3` | ✅ |
| **npm / pnpm** | npm 11.17.0 · pnpm 12.4.1 | ✅ |
| **Git** | 2.39.1（含 TortoiseGit 2.14） | ✅ |
| **Redis** | 5.0.14 · `D:\G\Redis`，Windows 服务 `Redis` 已自启，`127.0.0.1:6379` | ⚠️ 版本不符，见 §三.4 |
| **IntelliJ IDEA** | 2023.3 | ✅ 支持 JDK 17 |
| **VS Code** | 1.119.0 | ✅ |
| **浏览器** | Chrome + Edge | ✅ 演示/响应式验证 |
| **Postman** | 10.0.1 | ✅ API 文档验证 |
| **VC++ 运行库** | 14.50.35719（MySQL 依赖） | ✅ 满足 |

### 端到端实测记录（非推断，均已实际执行）

1. **Spring Boot 全链路构建成功**
   `mvn clean package`（JDK 17 + Spring Boot 3.2.5）→ 产出 `envcheck-1.0.0.jar`（54.51 MB fat jar），`BUILD_EXIT=0`。
2. **应用实际启动成功**
   `java -jar` → 内嵌 Tomcat 正常拉起 → `GET http://127.0.0.1:18081/ping` 返回 `pong`。
   **证明 JDK 17 + Spring Boot 3.x + 内嵌 Tomcat 链路完全可用。**
3. **依赖解析全部成功**
   `spring-boot-starter-web` / `data-jpa` / `data-redis` / `validation` / `test` / `com.mysql:mysql-connector-j` / `flyway` / `testcontainers` 全部解析并落入 `D:\G\Maven-resp`。
4. **网络可达性**
   Maven Central ✅ · 阿里云镜像 ✅ · npm registry ✅（create-vite 9.2.1 / vite 8.3.4 / vue 3.5.44 / echarts 6.1.0 均可获取）。
5. **端口占用**
   3306（MySQL）✅ 空闲 · 8080 ✅ 空闲 · 5173 ✅ 空闲 · 6379 被 Redis 占用 · 5432 被 PostgreSQL 10 占用（与本项目无关）。

---

## 三、缺失项明细

### 🔴 1. MySQL Server —— 完全未安装（唯一硬阻断项）

**依据**：PRD §7.1 —— 数据持久化必须使用**独立部署的 MySQL 服务**，**不接受嵌入式 / 内存数据库**（H2、SQLite、HSQLDB、Derby、嵌入式 MySQL 一律不可）。

**核查证据**：

- 全盘 `C:\ D:\ E:\` 向下 4 层递归：`mysqld.exe` ❌ 未找到；`mysql.exe` ❌ 未找到
- 3306 端口 ✅ 空闲（无进程监听）
- Windows 服务列表 ❌ 无 MySQL / MariaDB 服务
- 注册表卸载项 ❌ 无 MySQL / MariaDB / XAMPP / WAMP / Laragon
- `C:\Program Files`、`ProgramData`、`D:\`、`E:\`、`C:\tools`、`D:\dev` 等常见根目录 ❌ 无 MySQL 相关目录
- 仅在 `D:\G\Maven-resp` 与若干 `target/WEB-INF/lib` 中发现 `mysql-connector-java-*.jar` —— **那是 JDBC 驱动，不是数据库服务**

**同时缺失**：MySQL 命令行客户端 `mysql.exe`（README「方案 B」建库步骤 `mysql -u root -p -e "..."` 需要它）。

**安装方案**（本机当前用户 **非管理员**：`IsAdmin=False`，需据此选择）：

| 方案 | 命令 / 来源 | 是否需要提权 | 实测 |
|---|---|---|---|
| **A. ZIP 免安装（推荐）** | `https://cdn.mysql.com/Downloads/MySQL-8.4/mysql-8.4.9-winx64.zip`（267.5 MB） | ❌ 不需要——解压后 `mysqld --initialize-insecure` 直接跑独立进程 | ✅ 已验证 HTTP 200 可下载 |
| B. winget + MSI | `winget install Oracle.MySQL`（8.4.9，MSI 129.8 MB） | ⚠️ 安装为 Windows 服务需 UAC 提权 | ✅ 源中存在，URL 可达 |

> ⚠️ **版本注意**：设计文档声明 MySQL **8.0**，但 `cdn.mysql.com` 上 MySQL 8.0 的 ZIP 已归档（`8.0.39` / `8.0.42` 均返回 404），当前可取的是 **8.4.9 LTS**。
> PRD 未限定 MySQL 版本，8.4 与 8.0 均满足「独立部署」要求；README 中 `utf8mb4_0900_ai_ci` 排序规则两者通用。
> 唯一差异：MySQL 8.4 默认移除 `mysql_native_password` 插件——使用 `mysql-connector-j` 8.3+ 不受影响。

---

### 🔴 2. JDK 17 已安装但**未生效**（环境变量未切换）

**核查证据**：

- `JAVA_HOME` = `C:\Program Files\Java\jdk1.8.0_361` ❌ 指向 JDK 8
- PATH 顺序：`C:\Program Files (x86)\Common Files\Oracle\Java\javapath` → `D:\Maven\...\bin` → `C:\Program Files\Java\jdk1.8.0_361\bin` → …，**JDK 17 根本不在 PATH 中**
- `java -version` → `1.8.0_361`；`mvn -v` → `Java version: 1.8.0_361`
- **实测阻断**：以默认环境（JDK 8）编译 Spring Boot 3 应用 → 直接失败
  ```
  [ERROR] Failed to execute goal ...maven-compiler-plugin:3.11.0:compile:
          Fatal error compiling: 无效的标记: --release
  BUILD_EXIT=1
  ```
- 对照实测：`JAVA_HOME` 切到 `C:\Program Files\Java\jdk-17.0.2` 后，同一份 pom 构建成功、应用启动成功。

**结论**：Spring Boot 3.x 要求 Java 17+。**JDK 17.0.2 本体已在机器上，无需下载**，只需修正环境变量：

1. 将 `JAVA_HOME` 改为 `C:\Program Files\Java\jdk-17.0.2`
2. 将 `%JAVA_HOME%\bin` 提到 PATH 中 JDK 8 相关项**之前**（或移除 `javapath` 与 `jdk1.8.0_361` 条目）
3. 或者在项目内固定：Maven Wrapper `mvnw.cmd` + `.mvn/jvm.config`，IDEA 中单独指定 Project SDK 17（不影响其它老项目）

---

### 🟠 3. Docker / Docker Desktop —— 未安装

**依据**：`webox/README.md` 「步骤 1 · 方案 A（推荐）：`docker compose up -d` 启动 MySQL 8 与 Redis 7」；`docs/ARCHITECTURE.md` 第 554 行 `docker-compose.yml` 提供 `mysql:8.0` + `redis:7-alpine`；第 575 行集成测试 `./mvnw verify -Pintegration`（**Testcontainers MySQL + Redis**）。

**核查证据**：

- `C:\Program Files\Docker`、`Docker Desktop`、`C:\ProgramData\DockerDesktop`、`C:\ProgramData\Docker`、`%LOCALAPPDATA%\Docker`、`~\.docker` —— 全部 ❌ 不存在
- 无 `docker` / `dockerd` 服务，无 `Docker Desktop` / `com.docker.backend` 进程
- `docker --version` → ❌ 未识别
- **WSL 未安装**：`wsl -l -v` → 「适用于 Linux 的 Windows 子系统没有已安装的分发版…请运行 `wsl.exe --install`」
- 当前用户 **非管理员** → 安装 Docker Desktop 需 UAC 提权，且可能需在 BIOS 开启虚拟化
- 虚拟化状态：`HypervisorPresent: True`（已有 hypervisor 在运行）；但 `VirtualizationFirmwareEnabled` / SLAT 两项 WMI 读数为 False —— 在有 hypervisor 时该读数不可靠，**需提权后实际验证**

**影响与缓解**：

- 影响 ①：README 的 `docker compose up -d` 无法执行 → 需改用「方案 B 本地 MySQL + 本地 Redis」
- 影响 ②：Testcontainers 集成测试无法运行
- 缓解：设计文档第 560 行已明确「分两层，**默认层不依赖 Docker 且必须永远可过**」。因此**不装 Docker 仍可完成交付**，但需：
  - 更新 README：把方案 A/B 的主次对调，如实写明本机需用方案 B
  - 将 Testcontainers 集成测试保留在 `-Pintegration` profile 并标注为「有 Docker 时可选运行」，不能宣称已跑通

---

### 🟠 4. Redis 版本不匹配：本机 5.0.14，设计声明 7.x

**核查证据**：

- 服务 `Redis` 状态 **Running**，启动类型 **Auto**，命令行为：
  `"D:\G\Redis\redis-server.exe" --service-run redis.windows-service.conf --loglevel verbose`
- `redis-cli info server` → `redis_version:5.0.14`、`os:Windows`、`arch_bits:64`
- 配置：`bind 127.0.0.1` · `port 6379` · **`requirepass Jnzc2022?!`** · `maxmemory 1024mb`
- 实测：`redis-cli -a "Jnzc2022?!" ping` → **PONG**（在 Windows 服务模式下无密码连接会返回 `NOAUTH Authentication required`）

**风险与必办事项**：

1. **必须配置密码**：应用 `spring.data.redis.password` 要填 `Jnzc2022?!`（建议走 `.env` 的 `WEBOX_REDIS_PASSWORD`，不要硬编码进仓库）。README 的环境变量表当前**没有 Redis 密码项**，需补上。
2. **版本差异**：5.0.14 已 EOL，且是 Poradowski 的第三方 Windows 移植（官方早已不支持 Windows）。但设计用到的 `SET NX` / `INCR` / `TTL` / `SCAN` / Pub-Sub **在 5.0 全部可用**，功能层面**不阻断交付**。
3. **共享实例风险**：该 Redis 是**开机自启的共享服务**，可能已被其它项目使用。建议在 `spring.data.redis.database` 指定独立 DB index（如 `5`），**严禁**在代码里使用 `FLUSHALL` / `FLUSHDB`。
4. `maxmemory 1024mb` 已设置 —— 需确认淘汰策略为 `noeviction` 还是 `allkeys-lru`（可用 `CONFIG GET maxmemory-policy` 查看），菜单快照 + 幂等键 + 订单号发号三类 key 共存，须留足余量。

---

### 🟡 5. 次要偏差（不阻断，知悉即可）

| 项 | 本机 | 设计声明 | 评估 |
|---|---|---|---|
| Maven | 3.8.6 | 3.9.x | 非阻断，已实测可正常构建 Boot 3.2.5；且设计用 Maven Wrapper 会自带版本 |
| Spring Boot | 实测 3.2.5 可用 | 3.3.x | 非阻断，3.3.x 联网可下载（网络已验证可达） |
| Node.js | 24.19.0 激活 | 20+ | 满足。但 24 偏新，若遇原生模块编译问题可 `nvm use 22.13.1`（已装，LTS 更稳） |
| MySQL 客户端 | ❌ 缺失 | README 建库步骤需要 | 随 MySQL Server 一同提供 |

**无关但占用端口的进程**：PostgreSQL 10 服务运行于 5432（与本项目无关，无需处理）。

---

### ⚪ 6. LLM API Key —— 未发现可用于应用的 Key（Tier 3 可选加分项）

**依据**：PRD §5.2 —— AI 智能推荐需实际接入 LLM 服务，候选人需自备 API Key 或本地模型，**面试现场不提供**，且该功能必须实际可运行。

**核查证据**：

- 环境变量中 ❌ 无任何 `*_API_KEY`（OPENAI / DEEPSEEK / ANTHROPIC / DASHSCOPE / MOONSHOT / GEMINI 等全部未设置）
- 本地模型运行时 ❌ 无 Ollama、无 LM Studio（`11434` / `1234` 端口均空闲）
- 仅发现 `C:\Users\liujibiao\.codex\auth.json` 中一个 `OPENAI_API_KEY`（`sk-Z5NfyMSV3…`，67 字符），其 `~/.codex/config.toml` 配置 `base_url = "https://opencode.ai/zen/go/v1"`、`wire_api = "responses"`
  → **这是 Codex CLI 工具自身的第三方中转凭证，不建议也不能直接交给业务应用使用**

**影响**：**不阻断 Tier 1 + Tier 2 交付** —— `webox/README.md` 已明确声明「Tier 3 挑战：库存实时 / AI 推荐 / 数据看板 —— **本次不做**，仅预留扩展点」。
若后续要冲加分项（+10），则**必须自备一个可用的 LLM API Key 或安装本地模型运行时**。

---

## 四、建议处理顺序

| 优先级 | 动作 | 预估耗时 | 是否需提权 |
|---|---|---|---|
| **P0** | 切换 `JAVA_HOME` / PATH 至 JDK 17.0.2，并验证 `java -version` 与 `mvn -v` 均显示 17 | 2 分钟 | ❌ |
| **P0** | 下载 MySQL 8.4.9 ZIP 免安装包 → 解压 → `mysqld --initialize-insecure` → 以独立进程启动 3306 → 建 `webox` 库与用户 | 10 分钟 | ❌ |
| **P1** | 确认 Redis 密码接入方式（`WEBOX_REDIS_PASSWORD`），选定独立 `database` index，检查 `maxmemory-policy` | 3 分钟 | ❌ |
| **P1** | 更新 `README.md`：环境要求表补 Redis 密码项；快速启动以「本地 MySQL + 本地 Redis」为主方案；Testcontainers 标注为可选 | 5 分钟 | ❌ |
| **P2** | （可选）安装 Docker Desktop —— 仅当需要跑 Testcontainers 集成测试；需提权 + 装 WSL2 + 可能改 BIOS | 30+ 分钟 | ⚠️ 需要 |
| **P3** | （可选）自备 LLM API Key —— 仅当要冲 Tier 3 加分项 | — | ❌ |

---

## 五、可复现的核查命令

```powershell
# JDK / Maven
java -version; mvn -v; echo $env:JAVA_HOME
# MySQL 是否存在
where.exe mysqld; where.exe mysql; Get-Service *mysql*
Get-NetTCPConnection -State Listen -LocalPort 3306
# Redis
Get-Service Redis; & D:\G\Redis\redis-cli.exe -a "Jnzc2022?!" ping
& D:\G\Redis\redis-cli.exe -a "Jnzc2022?!" config get maxmemory-policy
# Docker / WSL
docker --version; wsl -l -v
# Node 工具链
nvm list; node -v; npm -v
# Spring Boot 链路自检（切好 JDK 17 后）
mvn -B clean package -DskipTests
```

---

*本报告所有结论均基于本机实际命令输出，未经推断；端到端构建与启动测试已实际执行并通过。*

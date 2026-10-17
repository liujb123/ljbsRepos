# WeBox — 企业员工餐食订购平台

English version: [README.md](README.md)

面向企业食堂的全栈订餐系统。约 200 名员工在手机或电脑上浏览当日菜单、在截单时间前下单，食堂据此备餐。
管理员通过独立的 **Console** 后台管理菜品库并发布每日菜单。

依据 `AI Vibe Coding V3.0 - PRD.md` 实现：**Tier 1（核心功能）与 Tier 2（进阶功能）已完整交付**。
Tier 3（库存实时展示、AI 智能推荐、经营数据看板）不在本次范围内，扩展点已在设计中预留并写入文档。

> **系统语言**：按 PRD 第 1 章要求，所有面向用户的内容 —— 界面、菜单数据、校验与错误提示 —— 一律为英文。

---

## 交付状态

| 模块 | 状态 |
| --- | --- |
| 后端（JDK 17 + Spring Boot 3.3 + 独立 MySQL 8） | 已实现，运行中 |
| 前端（Vue 3 + TypeScript + Vite） | 已实现，类型检查与构建均通过 |
| Redis 缓存 / 幂等 / 订单号发号 | 已实现，可降级 |
| 单元测试（`mvn test`） | **56 项全部通过** |
| 端到端接口检查（`scripts/smoke-test.ps1`） | **69 项断言全部通过** |
| 并发检查（`scripts/concurrency-test.ps1`） | **14 项断言全部通过** |
| Tier 3 | 未实现 —— 扩展点已写入文档 |

---

## 功能范围

| 层级 | 功能 | 位置 |
| --- | --- | --- |
| 1 | 员工注册登录，刷新页面不丢登录态 | `/login`、`/register` |
| 1 | 每日菜单：图片、价格、菜系标签、多选筛选、搜索 | `/` |
| 1 | 菜品详情：成分、过敏原、辣度、选项选择时价格实时变化 | `/menu/dishes/:id` |
| 1 | 购物车：同菜品不同配置分行、数量调整、实时总价 | `/cart` |
| 1 | 结算：配送日期、用餐时段、配送地址、下单唯一性保证 | `/checkout` |
| 1 | 我的订单、含选项快照的订单详情、取消订单 | `/orders` |
| 2 | 饮食偏好：过敏原、菜系、辣度、口味浓淡、单餐预算 | `/settings` |
| 2 | 订餐规则：5 份上限、截单自动切换、同餐次唯一订单 | 结算页 + 菜单页 |
| 2 | Console：菜品表格、新增/编辑（含选项组）、上下架、图片上传 | `/console/dishes` |
| 2 | Console：设置某日菜单并逐菜品设定供应数量 | `/console/daily-menu` |

---

## 技术选型

| 层次 | 选型 | 理由 |
| --- | --- | --- |
| 语言 / 框架 | Java 17、Spring Boot 3.3 | PRD 强制要求 |
| 构建 | Maven + wrapper（`mvnw`） | 无需全局安装 Maven |
| 数据库 | **MySQL 8，独立部署** | PRD 强制要求；包括测试在内全程不使用嵌入式数据库 |
| Schema 管理 | Flyway 迁移 | schema 可复现；Hibernate 不参与任何结构变更 |
| 持久层 | Spring Data JPA + Hibernate | 默认参数绑定，从根上消除 SQL 注入面 |
| 缓存 | **Redis**（可选加速层） | 菜单快照缓存、幂等快速路径、订单号发号 |
| 鉴权 | Spring Security 6 + JWT | 无状态；角色声明直接映射到接口规则 |
| 接口文档 | springdoc-openapi | 见 `API.md` 或 `/swagger-ui.html` |
| 前端 | Vue 3、TypeScript、Vite | 开发迭代快，SPA 全程强类型 |
| 前端状态 | Pinia（认证/购物车/偏好）+ TanStack Query（服务端数据） | Query 为高频菜单接口提供缓存与请求去重 |
| 样式 | Tailwind CSS | 移动优先断点体系，满足"手机 + 电脑双端"要求 |

被否决的方案与每一项选择的完整理由记录在
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) 第 2 章。

---

## 仓库结构

```
webox/
├─ README.md                            英文版说明（本文件的中文版为 README.zh-CN.md）
├─ API.md                               接口清单与请求/响应示例
├─ docker-compose.yml                   MySQL 8 + Redis 7，一条命令起基础设施
├─ .env.example                         全部可配置的环境变量
├─ ai-conversations/                    AI 编程对话原始记录（PRD 7.2）
├─ docs/
│   ├─ ARCHITECTURE.md                  设计决策、数据模型、缓存、安全
│   ├─ BUSINESS-DESIGN.md               逐条业务规则实现与英文文案表
│   └─ REQUIREMENT-TRACEABILITY.md      112 条需求 → 设计 → 验收标准
├─ backend/                             Spring Boot 应用（Maven）
│   └─ src/main/resources/db/migration/ Flyway schema
├─ frontend/                            Vue 3 SPA
└─ scripts/
    ├─ dev.ps1                          构建并启动前后端
    ├─ smoke-test.ps1                   69 项端到端接口断言
    └─ concurrency-test.ps1             幂等与餐次竞争的并发断言
```

---

## 环境要求

| 依赖 | 实测版本 | 说明 |
| --- | --- | --- |
| JDK | **17**（实测 17.0.2） | Spring Boot 3 要求 17 或更高 |
| MySQL | **8.0**（实测 8.0.28） | 必须是独立服务，端口 3306 |
| Redis | 5 或更新（实测 5.0.14） | 可选，端口 6379 |
| Node.js | 20+（实测 24.19） | 用于前端 SPA |
| Docker | 任意近期版本 | 仅在使用 `docker-compose.yml` 时需要 |

构建前把 `JAVA_HOME` 指向 JDK 17：

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.2"
```

---

## 快速启动

### 步骤 1 —— 启动 MySQL 与 Redis

**方案 A —— Docker（一条命令）：**

```bash
docker compose up -d
docker compose ps          # 等待 webox-mysql 显示 healthy
```

该方案会创建 `webox` 与 `webox_test` 两个库，账号为 `webox` / `webox1234`。

**方案 B —— 使用已有的本地服务：**

```sql
CREATE DATABASE IF NOT EXISTS webox      CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS webox_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'webox'@'%' IDENTIFIED BY 'webox1234';
GRANT ALL PRIVILEGES ON webox.*      TO 'webox'@'%';
GRANT ALL PRIVILEGES ON webox_test.* TO 'webox'@'%';
FLUSH PRIVILEGES;
```

Redis 不需要任何配置。如果你的 Redis 设了密码，在步骤 2 里设置 `WEBOX_REDIS_PASSWORD`。

> **没有 Redis 应用照样跑。** 已实测：把应用指向一个无服务的 Redis 端口启动，注册、菜单（全部 50 道菜）
> 与下单全部成功，日志中出现 `Menu cache GET failed ... serving from MySQL` 与
> `Idempotency fast path unavailable`。变化的只是速度（每次菜单读取都落到 MySQL），以及幂等路径的
> 一个细节（改由数据库唯一索引承担，而不是 Redis 快速路径）。
>
> 因此 `/actuator/health` **默认不把 Redis 计入整体健康状态**，一个功能完好的实例不会仅仅因为缺少
> Redis 就报 `503`。设置 `MANAGEMENT_HEALTH_REDIS_ENABLED=true` 可把 Redis 重新纳入健康状态 ——
> 生产环境应当这样做。

### 步骤 2 —— 配置

```bash
cp .env.example .env
```

后端会从仓库根目录或 `backend/` 目录（哪个存在读哪个）导入 `.env`，所以无论你用
`./mvnw spring-boot:run` 还是 `java -jar` 都能读到。直接使用环境变量同样有效且优先级更高；
完全跳过这一步也可以，因为除 `WEBOX_DB_PASSWORD`（默认 `webox1234`）与 `WEBOX_JWT_SECRET` 之外，
每个变量都有可用的 localhost 默认值。

`.env` 已被 git 忽略。若 JWT 密钥为空，应用会在启动时生成一个临时密钥并打印警告 ——
功能一切正常，但重启后已签发的 token 会失效。

完整变量清单见 [`.env.example`](.env.example)：数据库、Redis、CORS 来源、业务时区、上传目录、
缓存 TTL 以及种子账号。

### 步骤 3 —— 启动后端

```bash
cd backend
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
```

首次启动时 Flyway 建表，种子程序填充菜品库。服务监听 `http://localhost:8080`。

### 步骤 4 —— 启动前端

```bash
cd frontend
npm install
npm run dev
```

Vite 在 `http://localhost:5173` 提供 SPA，并把 `/api` 与 `/dishes` 代理到后端，
因此浏览器始终只与一个来源通信。

### 步骤 5 —— 打开系统

| | 地址 |
| --- | --- |
| 员工端 | <http://localhost:5173> |
| Console（管理端） | <http://localhost:5173/console> |
| 接口文档 | <http://localhost:8080/swagger-ui.html> |
| 健康检查 | <http://localhost:8080/actuator/health> |

**或者一次启动全部：**

```powershell
pwsh -File scripts/dev.ps1              # 前提：MySQL 与 Redis 已在运行
pwsh -File scripts/dev.ps1 -WithDocker  # 先执行 docker compose
```

> `scripts/dev.ps1` 与两个测试脚本是 PowerShell，仅在 Windows 上可用。
> 上面的步骤 1–4 是跨平台的，macOS / Linux 请按步骤手动执行。

---

## 默认账号与种子数据

| 项 | 值 |
| --- | --- |
| 管理员 | `admin@webox.com` / `Admin1234` |
| 员工 | 在 `/register` 自助注册（任意合法邮箱，不限制公司域名） |

启动时的种子程序是**幂等**的 —— 重启既不会产生重复数据，也不会覆盖管理员已经做过的修改：

* 管理员账号（不存在时才创建）；
* **50 道菜品**：PRD 第 6 章的 9 道菜及其完整选项组，另补充英文菜品，使分页与列表性能具备演示意义；
* 20 张随包菜品图片，按序循环绑定到菜品；
* **当天与次日的已发布菜单**，每道菜 50 份 —— 系统启动即可下单，满足 PRD 4.3 的要求。

用 `WEBOX_SEED_ADMIN_EMAIL` / `WEBOX_SEED_ADMIN_PASSWORD` 修改种子账号，
或用 `WEBOX_SEED_ENABLED=false` 完全关闭种子数据。

### 重置环境

要推倒重来，删库后重启后端即可 —— Flyway 会重建 schema，种子程序会重新填充：

```sql
DROP DATABASE webox;
CREATE DATABASE webox CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

同时清理应用写在 Redis 里的键：

```bash
redis-cli --scan --pattern 'webox:*' | xargs -r redis-cli del
```

跳过这一步不会致命 —— 菜单快照带 10 分钟 TTL，会自行过期 —— 但在过期之前，
缓存里的菜单仍然指向旧数据库的菜品 id。这是整个系统里唯一一处"缓存寿命长于事实来源"的地方，
而 TTL 正是它能自愈的原因。

---

## 接口

完整参考见 [`API.md`](API.md)；可交互版本在 `/swagger-ui.html`。

约定用一段话说完：所有接口位于 `/api` 之下；每个响应都使用同一层封装
`{ code, message, data, requestId }`；`message` 一律为英文；金额以十进制字符串传输（`"22.50"`）；
员工角色可访问 `/api/**`，而 `/api/admin/**` 额外要求 `ADMIN` 角色；
`POST /api/orders` 必须携带 `Idempotency-Key` 请求头。

---

## 测试

```bash
cd backend

./mvnw test        # 56 项单元测试，无外部依赖，任何时候都能跑
```

单元测试锁定的是那些很容易写错却不容易发现的规则：

| 测试类 | 锁定内容 |
| --- | --- |
| `MealSlotResolverTest`（14） | 全部截单边界，包含 PRD 的 10:30 示例、`10:00:00` 与 `15:00:00` 整点，以及次日兜底 |
| `CartRuleValidatorTest`（11） | 必选组、单选组、非本菜品的选项 id、已下架选项，以及 3 + 3 = 6 份被拒 |
| `PriceCalculatorTest`（10） | 两位小数舍入、`0.1 + 0.2`、多次加价累加，以及一单完全手算对齐 |
| `SearchKeywordTest`（8） | `%` 与 `_` 转义，以及注入式关键词被当作惰性字面量 |
| `PasswordPolicyTest`（13） | 长度边界与"字母 + 数字"边界 |

**端到端与并发检查**针对真实运行的服务执行（请先启动服务）：

```powershell
pwsh -File scripts/smoke-test.ps1        # 69 项断言，覆盖 Tier 1 + Tier 2 全部流程
pwsh -File scripts/concurrency-test.ps1  # 14 项断言，覆盖幂等与餐次竞争
```

并发脚本发出的是**真正并行**的 HTTP 请求，断言的是最关键的几条保证：

```
== 同一 Idempotency-Key 并发提交 10 次
    statuses: 200
    distinct order numbers: 1
  [PASS] all 10 responses carry the same order number
  [PASS] 9 responses were recognised as replays
  [PASS] the database holds exactly one order for that key

== 餐次唯一性：10 个不同 key 并发抢同一餐次
    status distribution: 200x1, 409x9
  [PASS] exactly one racing submit won
  [PASS] the conflict reason is ORDER_SLOT_CONFLICT
```

---

## 架构概览

```
Vue 3 SPA  ──/api──▶  Spring Boot 3.3 (JDK 17)  ──JDBC──▶  MySQL 8   （唯一事实来源）
  Pinia + TanStack Query        │                              ▲
                                └──Lettuce──▶  Redis           │ 永远不是权威
                                              （缓存 · 幂等 · 订单号）
```

四条决策支撑了绝大部分设计：

1. **MySQL 是唯一事实来源。** Redis 是可降级的加速层；失去它只损失速度，绝不损失正确性。
   降级路径是直读 MySQL，**刻意不用进程内 Map** 冒充。
2. **并发正确性由唯一索引保证，而不是锁。** `UNIQUE(user_id, client_request_id)` 让提交幂等；
   `UNIQUE(user_id, active_slot_key)` —— 其中 `active_slot_key` 是一个在订单取消或完成后变为 NULL 的
   生成列 —— 让 MySQL 获得了"部分唯一索引"的等价能力，因此每位员工每个餐次最多只有一个有效订单。
   整个系统里没有任何分布式锁。
3. **菜单以"整日快照"的方式缓存。** 菜单数据量小，且在 09:30–10:00 高峰被所有人同时读取，
   因此一个缓存条目就能服务全部并发读者，而筛选、搜索、偏好排序与分页都在内存中完成。
   失效策略是显式的：菜品写操作清空全部，每日菜单写操作只清某一天，10 分钟 TTL 作为兜底。
4. **购物车放在浏览器，服务端全量重算。** 价格、份数上限、选项合法性、最终生效餐次与重复下单检查，
   全部在下单事务内用数据库数据重新推导。客户端提交的任何金额都不被信任。

[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) 包含数据模型、九条决策记录、安全设计与 Tier 3 扩展点。

---

## AI 编程对话记录

PRD 第 7.2 章要求提交构建本项目所用**全部 AI 编程会话的完整原始导出** —— 不接受截图、节选或事后整理的摘要。
文件位于 [`ai-conversations/`](ai-conversations/)；该目录下的 README 说明了每个文件是什么，以及导出方式。

---

## 上传到公开仓库之前

**推送到公开远端之前，请先清除原始对话记录中包含的本机凭据。**

[`ai-conversations/`](ai-conversations/) 下的会话记录是逐字导出，因此包含了开发过程中执行过的 shell 命令 ——
而那些命令里带着本机的 Redis 密码、本地 MySQL root 密码，以及本地运行用的临时 JWT 密钥。
`docs/ENVIRONMENT-CHECK.md` 里也重复出现了 Redis 密码。这些内容应用本身一概不用，
也不值得保护，但**不应该被公开发布**。

三选一：

| 方案 | 做法 | 代价 |
| --- | --- | --- |
| **A —— 仓库保持私有**（推荐） | 原样推送，直接把仓库分享给面试官 | 对话记录保持 100% 原始，正是 PRD 7.2 要求的 |
| **B —— 脱敏并明确声明** | 把凭据字符串替换为 `***`，并在 `ai-conversations/README.md` 中写清删掉了什么、且这是唯一的改动 | 对话记录不再是严格的逐字原始 |
| **C —— 排除导出文件** | 把 `ai-conversations/*.jsonl` 加入 `.gitignore`，原始记录单独交给面试官 | PRD 交付物仍需提交，只是不经由公开仓库 |

如果这台机器是共用的，请一并更换 Redis 密码：它存放在 `D:\G\Redis\redis.windows-service.conf`，
并且已以明文形式写入了上述文件。

仓库中其余内容都可以安全公开。在一个全新克隆上执行 `git status` 不应出现 `.env`、`*.log`
或 `backend/data/` —— 三者均已被忽略。

---

## 已知限制

| 项 | 说明 |
| --- | --- |
| JWT 存在 `localStorage` | 用 CSRF 暴露面换取实现简洁。缓解措施：从不渲染原始 HTML，且服务端把所有客户端输入视为不可信。长期部署应改为 httpOnly Cookie + CSRF token。 |
| 角色变更不即时生效 | 角色随 token 传递，因此在 8 小时有效期内变更不会立刻生效。生产环境应改为查库校验或引入 token 版本号。 |
| 上传图片存本地磁盘 | 单实例没问题；多实例应改为对象存储。 |
| 菜单缓存为整日快照 | 约 2000 道菜以内舒适；超过后计划改为数据库侧分页 + 筛选条件哈希作为缓存键，接口契约不变。 |
| Tier 3 未实现 | 库存实时与扣减、AI 推荐、经营看板。数据表已带 `total_qty` / `sold_qty`，扣减会以条件更新形式放在现有的下单事务内。 |

---

## 建议的演示顺序

1. 注册一名新员工 → 登录 → 刷新页面，登录态保持。
2. 菜单页：多选 `Chinese` + `Japanese`，搜索 `chicken`，切换 **Recommend for me**。
3. 打开一道带选项的菜品（Classic Beef Burger）—— 选择选项时价格实时变化。
4. 加入购物车：先在设置里勾选 `Peanuts` 过敏原以触发提醒弹窗；加到 5 份后观察按钮置灰并给出原因；
   用不同配置再加同一道菜，注意它成为独立的一行。
5. 结算页：若午餐已截单会出现切换提示；历史地址可一键选择；超预算时显示非阻断提示。
6. 连续点击 **Place Order** 多次 —— 只会创建一个订单。
7. 再次进入结算页 —— 按钮已变为 **View Existing Order**。
8. 在 **My Orders** 中取消该订单，然后重新下单同一餐次。
9. 用员工 token 调用 `/api/admin/dishes` —— 返回 `403`。
10. 进入 Console：搜索与筛选菜品、编辑一道菜、下架一道菜并确认它从员工端菜单消失，
    然后发布次日菜单并设定供应数量。

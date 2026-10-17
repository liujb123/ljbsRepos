# WeBox · 架构说明（Architecture Specification）

| 项目     | 内容                                       |
| ------ | ---------------------------------------- |
| 文档版本   | v1.0（设计阶段）                               |
| 对应需求   | `AI Vibe Coding V3.0 - PRD.md`           |
| 覆盖范围   | Tier 1 核心功能（PRD §3）+ Tier 2 进阶功能（PRD §4） |
| 明确不含   | Tier 3 挑战功能（PRD §5）——仅在本文件第 11 章预留扩展点    |
| 目标岗位口径 | 后端 Backend（评分侧重见 PRD 附录 A.2）             |
| 文档状态   | **设计定稿，待实现**。所有"实现文件"路径为规划值，实现阶段回填。      |

> 本文档描述**系统怎么搭**。业务规则怎么算见 [`BUSINESS-DESIGN.md`](./BUSINESS-DESIGN.md)；每条 PRD 需求落在哪里见 [`REQUIREMENT-TRACEABILITY.md`](./REQUIREMENT-TRACEABILITY.md)。

---

## 1. 架构目标与约束

### 1.1 来自 PRD 的强制约束

| 约束                      | 出处      | 对本架构的影响                                                                    |
| ----------------------- | ------- | -------------------------------------------------------------------------- |
| 必须完整前后端，可本地运行           | §7.1    | 单仓双模块（`backend/` + `frontend/`），一键启动脚本                                     |
| 后端 JDK 17 + Spring Boot | §7.1    | Spring Boot 3.3.x（3.x 最低要求 JDK 17）                                         |
| MySQL 必须独立部署，禁用嵌入式库     | §7.1    | Docker Compose 起 `mysql:8.0` 独立进程；**测试也不使用 H2**，集成测试同样连真实 MySQL（独立 `webox_test` schema），全程不引入嵌入式库 |
| 缓存方案不限，Redis 可选         | §7.1    | 采用 Redis，但设计为**可降级加速层**，不作为事实来源                                            |
| 所有面向用户内容一律英文            | §1      | 文案集中沉淀，见 `BUSINESS-DESIGN.md` §11；代码中不出现中文文案常量                             |
| 汇率/价格精确到分、避免浮点误差        | §6      | 服务端 `BigDecimal(scale=2)`，DB `DECIMAL(10,2)`，前端整数分运算                       |
| 长期运行项目、需可维护可扩展          | §7.3    | 分层清晰、Flyway 管理 schema、审计日志、ADR 记录决策、预留 Tier 3 扩展点                          |
| 高峰集中在 9:30–10:00，菜单高频访问 | §1、§3.2 | 菜单读取走缓存快照；写入路径极短；无跨请求锁                                                     |

### 1.2 架构原则

1. **MySQL 是唯一事实来源（single source of truth）。** 任何缓存、Redis 结构、前端状态丢失或不可用，都不得导致业务规则失效或数据错误。
2. **所有并发正确性由数据库约束兜底。** 应用层检查只负责"给出友好提示"，不承担并发正确性；正确性交给唯一索引 / 条件更新。
3. **写入路径短、读取路径快。** 高峰期 99% 流量是菜单读与加购（前端行为），下单是低频写。
4. **服务端不信任客户端。** 价格、库存、餐次、份数上限全部在提交事务内用数据库数据重算。
5. **可降级优先于强依赖。** Redis、文件存储、订单号发号器均有降级路径，单点故障不导致核心流程中断。

---

## 2. 技术选型与理由

### 2.1 选型总表

| 层次           | 选型                                 | 版本基线            | 理由                                        |
| ------------ | ---------------------------------- | --------------- | ----------------------------------------- |
| 语言           | Java                               | JDK 17          | PRD §7.1 强制                               |
| 应用框架         | Spring Boot                        | 3.3.x           | PRD §7.1 强制；3.3 为 JDK 17 上成熟稳定线           |
| 构建           | Maven + Maven Wrapper              | 3.9.x / wrapper | 评委无需安装 Maven，`./mvnw` 即可构建                |
| 数据库          | MySQL                              | 8.0             | PRD §7.1 强制独立部署；8.0 提供生成列、CTE、窗口函数        |
| Schema 管理    | Flyway                             | 10.x            | 版本化迁移，环境可复现；避免 `ddl-auto=update` 的隐式漂移    |
| 持久层          | Spring Data JPA + Hibernate        | 随 Boot          | 默认参数绑定，从根上消除字符串拼 SQL；Specification 支撑动态筛选 |
| 缓存 / 幂等 / 发号 | Redis                              | 5+（本地验证于 5.0.14）     | 后端 P6+「缓存策略」考察点；同时承载幂等快速路径与订单号发号          |
| 鉴权           | Spring Security 6 + JWT (jjwt)     | 6.x / 0.12.x    | 无状态，支持"刷新页面不丢登录态"；角色声明直接映射 Spring 权限      |
| 接口文档         | springdoc-openapi (Swagger UI)     | 2.6.x           | PRD §7.2 要求 API 文档，注解即文档，零额外维护成本          |
| 参数校验         | Jakarta Bean Validation            | 随 Boot          | 声明式长度/格式校验，与 PRD §3.1 的长度上限一一对应           |
| 测试           | JUnit 5 + Mockito + PowerShell 端到端脚本 | 5.x / 1.20.x    | 单元测试零依赖可跑；集成测试用真实 MySQL 保证并发语义可信          |
| 前端           | Vue 3 + TypeScript                 | 3.5 / 5.x       | 用户已确认选型                                   |
| 前端构建         | Vite                               | 5.x+            | 冷启动秒级，dev proxy 解决跨域                      |
| 前端状态         | Pinia                              | 2.x             | 认证态、购物车、偏好；购物车持久化到 localStorage           |
| 前端数据层        | TanStack Query for Vue             | 5.x             | 请求去重、缓存、失效、后台刷新——直接对应"菜单高频访问"场景           |
| 前端样式         | Tailwind CSS                       | 3.x             | 移动优先断点体系，最快产出响应式布局（§1 手机+电脑双端）            |
| 前端 HTTP      | axios                              | 1.x             | 拦截器统一注入 token、统一错误码→英文 toast              |

### 2.2 被否方案及原因

| 被否方案                       | 否掉原因                                                                        |
| -------------------------- | --------------------------------------------------------------------------- |
| MyBatis / MyBatis-Plus     | 需手写 SQL 与 XML，动态排序、动态筛选更易引入拼接字符串，与"SQL 注入防护"考察点形成不必要的风险面；1.5 小时时限下 JPA 出活更快 |
| H2 / SQLite 做运行库或测试库       | PRD §7.1 明令禁止嵌入式数据库。**集成测试同样连真实 MySQL（独立 schema）**，避免"测试通过但生产语义不同"       |
| 内存 Map（Caffeine）替代 Redis   | 已声明使用 Redis，PRD 附录 A.3 明确"不可用内存 Map 假替"。降级路径是**直读 MySQL**，不是内存缓存            |
| 服务端购物车表                    | §3.3 无跨端/跨设备要求；服务端购物车引入多端一致性与过期清理复杂度，收益为零。改为前端持久化 + 下单时服务端全量重建              |
| JWT 存 httpOnly Cookie      | 需额外引入 CSRF 防护；本项目纯 SPA + Bearer 头，Cookie 方案净增复杂度。取舍与风险见 §8.6                |
| Redis 分布式锁保护下单             | 引入锁超时、续期、脑裂等额外故障模式。改用数据库唯一索引 + 条件更新这一确定性方案                                  |
| schema 用 `ddl-auto=update` | 无法审计、无法回滚、多环境漂移                                                             |
| 图片存 MySQL BLOB             | 撑大数据库、破坏备份与缓存策略。改为本地磁盘 + 受控静态读取接口                                           |

---

## 3. 系统架构

### 3.1 部署形态

```
┌───────────────────────────── 浏览器（手机 / 电脑） ─────────────────────────────┐
│  Vue 3 SPA                                                                     │
│   ├─ 员工端   /               顶部导航（≥md） / 底部 Tab（<md）                  │
│   └─ 管理端   /console/*      左侧栏布局，UI 名称 Console                        │
│  状态：Pinia(auth / cart / preference / ui) · TanStack Query(菜单 / 订单 / 管理)  │
└──────────────────────────────┬─────────────────────────────────────────────────┘
                               │ HTTP + JSON
                               │ dev:  Vite proxy  /api → http://localhost:8080
                               │ prod: 同源（前端构建产物由 Spring Boot 托管）
┌──────────────────────────────▼─────────────────────────────────────────────────┐
│  Spring Boot 3.3 应用（JDK 17）                                     :8080        │
│                                                                                │
│  web 层        Controller · DTO(record) · Bean Validation · 全局异常处理         │
│  security 层   JwtAuthenticationFilter · SecurityFilterChain · ROLE_EMPLOYEE/ADMIN│
│  service 层    @Transactional 业务编排                                          │
│                 ├─ MealSlotResolver      纯函数 · 注入 Clock                     │
│                 ├─ PriceCalculator       纯函数 · BigDecimal                    │
│                 ├─ CartRuleValidator     纯函数 · 规则校验                       │
│                 ├─ MenuCacheService      Redis 快照 · 可降级                     │
│                 ├─ IdempotencyService    Redis SET NX · 快速路径                 │
│                 └─ OrderNoGenerator      Redis INCR · 可降级                     │
│  repository 层 Spring Data JPA · 参数绑定 · 无字符串拼 SQL                        │
│  common        ApiResponse · ErrorCode · BusinessException · RequestIdFilter     │
└───────┬────────────────────────────────────────────────┬───────────────────────┘
        │ JDBC (HikariCP)                                │ Lettuce
┌───────▼────────────────────┐              ┌────────────▼────────────────────────┐
│ MySQL 8（独立进程 / 容器）   │              │ Redis 7（独立进程 / 容器）            │
│ 唯一事实来源                 │              │ 可降级加速层，非事实来源               │
│ Flyway 管理 schema          │              │  webox:menu:{date}      菜单快照      │
│ DECIMAL(10,2) 金额          │              │  webox:idem:order:...   幂等键        │
│ 唯一索引承担并发正确性        │              │  webox:orderno:{day}    发号器        │
└────────────────────────────┘              └─────────────────────────────────────┘
        │
        ▼
  本地磁盘 ${WEBOX_UPLOAD_DIR:./data/uploads}   管理员上传的菜品图片
```

### 3.2 后端分层与依赖方向

```
controller ──▶ service ──▶ repository ──▶ entity
     │             │
     │             └──▶ cache (MenuCacheService / IdempotencyService)
     │
     └──▶ dto (request / response records)

common / exception / config 为横切层，任何层可依赖，横切层不反向依赖业务层
```

**硬性规则**：

- Controller 不含业务判断，只做参数绑定与响应封装。
- Entity 绝不出现在 Controller 出入参中，一律经 DTO（record）转换。
- `@Transactional` 只标在 service 层 public 方法上。
- 纯函数（`MealSlotResolver` / `PriceCalculator` / `CartRuleValidator`）不依赖 Spring 容器，可独立单测。

### 3.3 核心模块划分

| 模块         | 包路径（规划）              | 职责                                             |
| ---------- | -------------------- | ---------------------------------------------- |
| `auth`     | `com.webox.auth`     | 注册、登录、签发 JWT、`/auth/me`                        |
| `user`     | `com.webox.user`     | 用户实体、偏好读写、历史地址                                 |
| `dish`     | `com.webox.dish`     | 菜品主数据、选项组、枚举、查询与缓存                             |
| `menu`     | `com.webox.menu`     | 每日菜单、菜单视图装配、菜单缓存与失效                            |
| `order`    | `com.webox.order`    | 下单事务、幂等、餐次解析、订单查询与取消                           |
| `admin`    | `com.webox.admin`    | Console 专用接口：菜品 CRUD、上下架、每日菜单设置                |
| `file`     | `com.webox.file`     | 图片存储与受控读取                                      |
| `seed`     | `com.webox.seed`     | 幂等种子数据（菜品库、管理员、当日菜单）                           |
| `security` | `com.webox.security` | JWT 服务、认证过滤器、当前用户解析、密码策略                       |
| `common`   | `com.webox.common`   | 统一响应、错误码、异常、请求 ID 过滤器                          |
| `config`   | `com.webox.config`   | Security、Redis、Jackson、Clock、CORS、OpenAPI、上传配置 |

---

## 4. 数据流

### 4.1 注册 / 登录

```
[注册]  SPA ──POST /api/auth/register──▶ AuthController
        └─ Bean Validation（邮箱格式、≤200、密码 ≥8 且含字母与数字）
        └─ 邮箱 trim + lowercase ──▶ app_user 唯一索引
              ├─ 冲突 → 409 EMAIL_ALREADY_REGISTERED
              └─ 成功 → BCrypt 编码入库 → 201
[登录]  SPA ──POST /api/auth/login──▶ 校验 BCrypt
        └─ 失败 → 401 AUTH_INVALID_CREDENTIALS（不区分"邮箱不存在/密码错误"）
        └─ 成功 → 签发 JWT{ sub=userId, role, exp=8h } → 前端 localStorage
[恢复]  SPA 启动 ──GET /api/auth/me（Bearer）──▶ 200 + 用户信息与角色
        └─ 401 → 清除本地 token，跳登录页
```

### 4.2 菜单读取（缓存命中路径 / 穿透路径）

```
GET /api/menu?date&categories&keyword&page&size&prefer
   │
   ├─▶ MenuCacheService.getMenuSnapshot(date)
   │      │
   │      ├─ HIT  ──▶ 反序列化为 List<MenuItemView> ──┐
   │      │                                          │
   │      └─ MISS ──▶ 查 DB：daily_menu ─▶ daily_menu_item ─▶ dish
   │                 （可见性过滤：在当日菜单 且 dish.is_available = true）
   │                 装配 OptionGroup 摘要 + 过敏原/辣度标签
   │                 ──▶ SET webox:menu:{date} TTL=10min ──▶ 返回快照
   │                                          │
   └──────────────────────────────────────────┴─▶ 内存内：分类多选过滤 → 关键词模糊匹配
                                                   → prefer 排序 → 分页切片 → 响应
   ※ Redis 异常 → 捕获 → 直读 DB（fallbackToDatabase 计数打点）→ 响应，接口不失败
```

**为什么"全量快照 + 内存筛选分页"**：菜单是**有界热点数据**（PRD §1 当前约 50 条、持续增长）。高峰 30 分钟内并发读同一份数据，全量快照把 N 次查询压成 0 次 DB 访问，且只有**一个失效点**。相比"按筛选条件组合做缓存键"避免了键爆炸，相比"每次查 DB 再分页"避免了高峰 DB 压力。规模阈值切换方案见 §11.1。

### 4.3 加入购物车（前端行为，不经服务端）

```
DishCard [Add to Cart]
   ├─ 5 份上限已达？ ── 是 ──▶ 按钮禁用 + 英文原因提示
   ├─ 与 user.allergens 有交集？ ── 是 ──▶ 英文确认弹窗（可继续，不过滤菜品）
   ├─ 400ms 单飞锁（防高峰连点）
   └─ Pinia cart.add(dish, selectedOptions)
         └─ 行键 key = `${dishId}#${该菜品已选 optionId 升序拼接}`
               ├─ 命中已有行 → quantity += 1
               └─ 新行 → 按整数分计算 unitPrice 后插入
         └─ 持久化 localStorage
```

### 4.4 下单事务（核心写入路径）

```
SPA ──POST /api/orders──▶ 请求头 Idempotency-Key（结算页挂载时生成一次，重试复用）
   │
   ▼ OrderService.placeOrder()  @Transactional
   ①  校验 Idempotency-Key 存在 ── 缺失 → 400 IDEMPOTENCY_KEY_REQUIRED
   ②  幂等快速路径：Redis SET NX webox:idem:order:{userId}:{key} TTL=24h
         └─ 已存在 → 读取并返回既有订单 200（duplicated = true），事务结束
         └─ 键存在但订单尚不可见（首个请求仍在事务中）→ 最多轮询 3s（每 40ms）
              等待其提交后返回原订单；超时才返回 409 ORDER_IN_PROGRESS
         └─ Redis 不可用 → fail-open（视为获取成功），由数据库唯一索引兜底
   ③  校验每条明细：菜品存在 / 在当日菜单内 / 已上架
   ④  校验选项组合法性：必选组齐全 · SINGLE 组恰好一项 · 选项属于该菜品且未下架
   ⑤  服务端重新计价（绝不取信前端价格）
         unit_price = base_price + Σ option.price_delta
         subtotal   = unit_price × quantity
         total      = Σ subtotal         全程 BigDecimal，HALF_UP，scale = 2
   ⑥  总份数 = Σ quantity ≤ 5 ── 超出 → 400 ORDER_SIZE_EXCEEDED
   ⑦  MealSlotResolver(now, requestedSlot) → (mealDate, mealSlot, adjusted, reason)
         adjusted=true 时前端展示英文"已自动切换"提示
   ⑧  预检该 (userId, mealDate, mealSlot) 是否已有 Pending/Confirmed 订单
         └─ 有 → 409 ORDER_SLOT_CONFLICT（携带 existingOrderId）
   ⑨  生成订单号：Redis INCR webox:orderno:{yyyyMMdd} → WB20260324-000123
         └─ Redis 不可用 → 同格式的随机 6 位序号（`WB<yyyyMMdd>-<6位>`）；UNIQUE(order_no) 冲突重试 ≤ 3 次
   ⑩  写入 orders + order_item + order_item_option（快照）+ order_status_log
         │
         └─ 并发兜底：应用层 ⑦⑧ 可能同时通过，由数据库唯一索引拦截
              · UNIQUE(user_id, client_request_id)      → 同键重复提交
              · UNIQUE(user_id, active_slot_key)        → 同餐次重复下单
            捕获 DataIntegrityViolationException → 判定冲突类型 → 回查并返回
              幂等冲突 → 200 既有订单；餐次冲突 → 409 既有订单 ID
```

### 4.5 管理端写操作与缓存失效

```
Console 写入（新增/编辑菜品 · 上下架 · 设置每日菜单）
   └─▶ @Transactional 写 MySQL
   └─▶ 事务提交后失效缓存（TransactionSynchronization afterCommit）
         ├─ 菜品相关写  → MenuCacheEvictor.evictAll()   SCAN 游标批量 DEL（禁用 KEYS）
         └─ 每日菜单写  → MenuCacheEvictor.evict(menuDate)
   └─▶ TTL 10min 作为兜底，即使失效失败也会自愈
```

---

## 5. 数据模型

### 5.1 表清单（16 张）

**用户与偏好**

| 表                         | 关键字段                                                                                   | 约束 / 索引                                                                       |
| ------------------------- | -------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| `app_user`                | id, email, password_hash, display_name, role, phone, created_at, updated_at            | `UNIQUE(email)`；email 入库前 trim + lowercase                                    |
| `user_preference`         | user_id(PK,FK), spice_preference, taste_preference, budget_min, budget_max, updated_at | `CHECK(budget_min IS NULL OR budget_max IS NULL OR budget_min <= budget_max)` |
| `user_allergen`           | user_id, allergen                                                                      | `PRIMARY KEY(user_id, allergen)`                                              |
| `user_preferred_category` | user_id, category                                                                      | `PRIMARY KEY(user_id, category)`                                              |
| `user_address`            | id, user_id, address_text VARCHAR(200), last_used_at, use_count                        | `UNIQUE(user_id, address_text)`；索引 `(user_id, last_used_at DESC)`             |

**菜品主数据**

| 表                   | 关键字段                                                                                                                                                               | 约束 / 索引                                       |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------- |
| `dish`              | id, name VARCHAR(100), description VARCHAR(500), price DECIMAL(10,2), category, spice_level, image_url VARCHAR(300), is_available, version, created_at, updated_at | 索引 `(category, is_available)`；`version` 乐观锁字段 |
| `dish_protein`      | dish_id, protein                                                                                                                                                   | `UNIQUE(dish_id, protein)`                    |
| `dish_allergen`     | dish_id, allergen                                                                                                                                                  | `UNIQUE(dish_id, allergen)`                   |
| `dish_option_group` | id, dish_id, name VARCHAR(60), required, selection_type, min_select, max_select, display_order                                                                     | 索引 `(dish_id, display_order)`                 |
| `dish_option`       | id, group_id, name VARCHAR(60), price_delta DECIMAL(10,2) DEFAULT 0, is_available, display_order                                                                   | 索引 `(group_id, display_order)`                |

**每日菜单**

| 表                 | 关键字段                                                                     | 约束 / 索引                                         |
| ----------------- | ------------------------------------------------------------------------ | ----------------------------------------------- |
| `daily_menu`      | id, menu_date DATE, created_at, updated_at                               | `UNIQUE(menu_date)`                             |
| `daily_menu_item` | id, daily_menu_id, dish_id, total_qty, sold_qty DEFAULT 0, display_order | `UNIQUE(daily_menu_id, dish_id)`；索引 `(dish_id)` |

> `total_qty` / `sold_qty` 在 Tier 2（§4.3 设置供应数量）即落库，Tier 3 的扣减与实时展示直接复用这两列，无需改表。

**订单**

| 表                   | 关键字段                                                                                                                                                                                                                                                                  | 约束 / 索引                                                                                                                                                  |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `orders`            | id, order_no, user_id, status, meal_date DATE, meal_slot, requested_meal_slot, slot_adjusted, delivery_address VARCHAR(200), item_count, total_amount DECIMAL(10,2), client_request_id, active_slot_key(生成列), note VARCHAR(200), created_at, updated_at, cancelled_at | `UNIQUE(order_no)`；**`UNIQUE(user_id, client_request_id)`**；**`UNIQUE(user_id, active_slot_key)`**；索引 `(user_id, created_at DESC)`、`(meal_date, status)` |
| `order_item`        | id, order_id, dish_id NULL, dish_name, image_url, unit_base_price, unit_price, quantity, subtotal                                                                                                                                                                     | 索引 `(order_id)`。**除 dish_id 外全部为下单时快照**                                                                                                                  |
| `order_item_option` | id, order_item_id, group_name, option_name, price_delta, display_order                                                                                                                                                                                                | 索引 `(order_item_id)`。快照，归一化存储便于渲染与统计                                                                                                                     |
| `order_status_log`  | id, order_id, from_status, to_status, actor_user_id, created_at                                                                                                                                                                                                       | 索引 `(order_id, created_at)`。审计轨迹                                                                                                                         |

### 5.2 订单快照语义

`order_item` 与 `order_item_option` 保存**下单时刻的名称与价格**，不通过外键实时关联 `dish`。原因：

- 管理员改名/改价/删除菜品，历史订单展示与金额不得变化；
- 订单详情、看板统计可脱离 `dish` 表独立查询；
- `dish_id` 可为 NULL，仅作溯源参考，不设外键级联删除。

### 5.3 `active_slot_key` 生成列：并发防重复下单的数据库级方案

```sql
active_slot_key VARCHAR(32) GENERATED ALWAYS AS (
  CASE WHEN status IN ('Pending','Confirmed')
       THEN CONCAT(meal_date, '#', meal_slot)
       ELSE NULL END
) STORED,
UNIQUE KEY uk_orders_user_active (user_id, active_slot_key)
```

**为什么这样设计**：

- PRD §4.2 要求"同一天同一餐次每位员工只能有一个**有效**订单"——这是**条件唯一约束**（仅 Pending/Confirmed 受限）。
- MySQL 不支持部分索引（partial index），但**唯一索引不约束 NULL**。生成列在订单取消/完成时变为 NULL，自动从唯一约束中退出，等价于实现了部分唯一索引。
- 因此：并发双击、双标签页、双实例部署下，**数据库层面只可能成功一条**；订单取消后同一餐次立即可再次下单，无需任何额外清理逻辑。
- 应用层的预检只是为了返回友好的英文提示，**不承担正确性**。

### 5.4 枚举字典（存储与展示值均为英文）

| 枚举                | 取值                                                                     | 出处                   |
| ----------------- | ---------------------------------------------------------------------- | -------------------- |
| `DishCategory`    | `Chinese` `Western` `Japanese` `Light Meal` `Korean` `Southeast Asian` | PRD §3.2、§6          |
| `SpiceLevel`      | `None` `Mild` `Medium` `Hot`                                           | PRD §3.2、§6          |
| `Allergen`        | `Peanuts` `Dairy` `Egg` `Gluten` `Soy` `Fish` `Shellfish`              | PRD §4.1、§6          |
| `ProteinType`     | `Chicken` `Beef` `Pork` `Fish` `Shrimp` `Egg` `Tofu` `None`            | PRD §3.2、§6（多值，独立子表） |
| `OrderStatus`     | `Pending` `Confirmed` `Completed` `Cancelled`                          | PRD §3.4             |
| `MealSlot`        | `Lunch` `Dinner`                                                       | PRD §3.4             |
| `TastePreference` | `Light` `Balanced` `Rich`                                              | PRD §4.1（清淡/适中/重口）   |
| `UserRole`        | `EMPLOYEE` `ADMIN`                                                     | PRD §1、§3.1          |
| `SelectionType`   | `SINGLE` `MULTI`                                                       | PRD §3.2（选项组单选/多选）   |

所有枚举以 `VARCHAR` 列 + `@Enumerated(EnumType.STRING)` 存储，**不使用 MySQL `ENUM` 类型**，避免后续增删值必须 DDL。

### 5.5 时间与金额约定

| 关注点  | 约定                                                                                                                                          |
| ---- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| 业务时区 | 固定 `Asia/Shanghai`，配置项 `webox.time-zone`                                                                                                    |
| 时间源  | 注入 `java.time.Clock` Bean（`Clock.system(zone)`），测试可注入 `Clock.fixed(...)`                                                                    |
| 审计时间 | `created_at` / `updated_at` / `cancelled_at` 存 `DATETIME(3)` **UTC**，`hibernate.jdbc.time_zone=UTC`                                         |
| 业务日期 | `meal_date`、`menu_date` 存 `DATE`，语义为业务时区的日历日                                                                                                |
| 金额   | DB `DECIMAL(10,2)`；Java `BigDecimal`，统一 `setScale(2, RoundingMode.HALF_UP)`；Jackson `WRITE_BIGDECIMAL_AS_PLAIN` 输出如 `22.50`；前端解析为**整数分**再运算 |

---

## 6. API 契约

### 6.1 统一约定

- 前缀 `/api`，请求与响应均为 `application/json; charset=UTF-8`（图片上传除外）。
- 认证：`Authorization: Bearer <jwt>`。
- 幂等：`POST /api/orders` 必带 `Idempotency-Key`（客户端 UUID）。
- 时间格式：日期 `yyyy-MM-dd`；时间戳 ISO-8601 UTC。
- **所有 `message` 与错误提示均为英文。**

**成功响应**

```json
{
  "code": "OK",
  "message": "Success",
  "data": { },
  "requestId": "8f3c1a2e-..."
}
```

**分页响应**（`data` 内）

```json
{
  "items": [],
  "page": 0,
  "size": 12,
  "total": 50,
  "totalPages": 5
}
```

**失败响应**

```json
{
  "code": "ORDER_SLOT_CONFLICT",
  "message": "You already have an active order for this meal. Please view it instead.",
  "data": { "existingOrderId": 1024, "orderNo": "WB20260324-000123" },
  "requestId": "8f3c1a2e-..."
}
```

### 6.2 接口清单

| #   | 方法    | 路径                                    | 权限    | 说明                                                                                     |
| --- | ----- | ------------------------------------- | ----- | -------------------------------------------------------------------------------------- |
| 1   | POST  | `/api/auth/register`                  | 公开    | 注册；邮箱唯一；密码策略校验                                                                         |
| 2   | POST  | `/api/auth/login`                     | 公开    | 登录，返回 JWT + 用户信息 + 角色                                                                  |
| 3   | GET   | `/api/auth/me`                        | 登录    | 恢复登录态                                                                                  |
| 4   | GET   | `/api/menu`                           | 登录    | 菜单分页卡片；`date` `categories`(可重复) `keyword` `page` `size` `prefer`                       |
| 5   | GET   | `/api/menu/dates`                     | 登录    | `[今天, 今天+7]` 内**已发布菜单**的日期列表，供菜单页与结算页日期选择器使用                                           |
| 6   | GET   | `/api/menu/dishes/{id}`               | 登录    | 菜品详情：描述、蛋白质、过敏原、辣度、完整选项组                                                               |
| 7   | GET   | `/api/users/me/preferences`           | 登录    | 读取偏好                                                                                   |
| 8   | PUT   | `/api/users/me/preferences`           | 登录    | 保存偏好（整体替换，幂等）                                                                          |
| 9   | GET   | `/api/users/me/addresses`             | 登录    | 历史配送地址（按最近使用倒序）                                                                        |
| 10  | POST  | `/api/orders/checkout-preview`        | 登录    | **下单前预览与校验**：携带购物车明细 → 返回可订日期、餐次解析结果（含 `adjusted`/`reasonCode`）、服务端计价、不可用项、既有有效订单、预算判定 |
| 11  | POST  | `/api/orders`                         | 登录    | 下单；**必带 `Idempotency-Key`**；响应含解析后的餐次与 `slotAdjusted`                                  |
| 12  | GET   | `/api/orders`                         | 登录    | 我的订单（分页，创建时间倒序）                                                                        |
| 13  | GET   | `/api/orders/{id}`                    | 本人    | 订单详情（含选项快照）；非本人 → 403                                                                  |
| 14  | POST  | `/api/orders/{id}/cancel`             | 本人    | 取消；仅 `Pending` 可取消；已 `Cancelled` 幂等返回 200                                              |
| 15  | GET   | `/api/admin/dishes`                   | ADMIN | 菜品表格：`page` `size` `keyword` `category` `available`                                    |
| 16  | POST  | `/api/admin/dishes`                   | ADMIN | 新增菜品（含嵌套选项组）                                                                           |
| 17  | PUT   | `/api/admin/dishes/{id}`              | ADMIN | 编辑菜品（选项组整体替换）                                                                          |
| 18  | PATCH | `/api/admin/dishes/{id}/availability` | ADMIN | 上架 / 下架                                                                                |
| 19  | GET   | `/api/admin/daily-menus/{date}`       | ADMIN | 读取某日菜单与供应数量                                                                            |
| 20  | PUT   | `/api/admin/daily-menus/{date}`       | ADMIN | 整体替换某日菜单（幂等）                                                                           |
| 21  | POST  | `/api/admin/files/images`             | ADMIN | 图片上传（≤2MB，jpg/png/webp，校验 magic bytes）                                                 |
| 22  | GET   | `/api/files/images/{name}`            | 公开    | 受控读取图片；文件名严格白名单正则                                                                      |
| S1  | GET   | `/api/admin/orders`                   | ADMIN | **补充项**：管理端订单列表（按状态/日期筛选），对齐 PRD §1"管理员处理订单"                                           |
| S2  | PATCH | `/api/admin/orders/{id}/status`       | ADMIN | **补充项**：`Pending → Confirmed`、`Confirmed → Completed`；写 `order_status_log`             |

> S1 / S2 为**补充项**，PRD §4.3 未细化，实现优先级最低，可在时间不足时整块裁剪而不影响 Tier 1 + Tier 2 完成度。详见 [`BUSINESS-DESIGN.md`](./BUSINESS-DESIGN.md) §12。

### 6.3 错误码表（19 个）

> 与实现一致：权威定义见 `backend/src/main/java/com/webox/common/ErrorCode.java`。

| 错误码                        | HTTP | 触发场景                                        |
| -------------------------- | ---- | ------------------------------------------- |
| `VALIDATION_FAILED`        | 400  | 参数校验失败（长度/格式/范围）；`data.fieldErrors` 列出具体字段 |
| `IDEMPOTENCY_KEY_REQUIRED` | 400  | 下单缺少 `Idempotency-Key` 请求头                  |
| `ORDER_SIZE_EXCEEDED`      | 400  | 订单总份数 > 5                                   |
| `OPTION_INVALID`           | 400  | 必选组缺失 / 单选组多选 / 选项不属于该菜品 / 选项已下架            |
| `DISH_NOT_ON_MENU`         | 400  | 菜品不在**解析后**餐次日期的每日菜单中                       |
| `UPLOAD_INVALID_TYPE`      | 400  | 上传文件类型或 magic bytes 不合法                     |
| `UPLOAD_TOO_LARGE`         | 400  | 上传文件超过 2MB                                  |
| `UNAUTHENTICATED`          | 401  | 未携带或携带无效/过期 JWT                             |
| `AUTH_INVALID_CREDENTIALS` | 401  | 登录邮箱或密码错误（**刻意不区分二者**，防账号枚举）                |
| `FORBIDDEN`                | 403  | 角色不足（员工访问 `/api/admin/**`）或资源不属于当前用户        |
| `DISH_NOT_FOUND`           | 404  | 菜品不存在；或员工请求已下架菜品（下架即不可见）                    |
| `ORDER_NOT_FOUND`          | 404  | 订单不存在                                       |
| `NOT_FOUND`                | 404  | 未知路由或未知图片                                   |
| `EMAIL_ALREADY_REGISTERED` | 409  | 邮箱已被注册                                      |
| `DISH_UNAVAILABLE`         | 409  | 加入购物车后菜品被下架                                 |
| `ORDER_SLOT_CONFLICT`      | 409  | 同日同餐次已有有效订单（`data` 携带 `existingOrderId` 与 `orderNo`） |
| `ORDER_IN_PROGRESS`        | 409  | 同一幂等键仍在处理中。罕见：重复提交通常短暂等待后直接返回原订单            |
| `ORDER_NOT_CANCELLABLE`    | 409  | 订单状态非 `Pending` 却请求取消                       |
| `INTERNAL_ERROR`           | 500  | 未预期异常（响应体不含堆栈，仅返回 `requestId` 供日志关联）       |

---

## 7. 关键设计决策（ADR）

### ADR-01 缓存采用"有界热点数据全量快照"

- **状态**：接受
- **背景**：PRD §1、§3.2 指出菜单在 9:30–10:00 被高频访问，且菜品数量会持续增长。
- **决策**：Redis 缓存**整日菜单快照**（`webox:menu:{date}`，TTL 10 分钟），分类筛选、关键词匹配、偏好排序、分页在服务端内存中完成。
- **理由**：数据有界（数十至数百条），一次读取服务所有并发；单一失效点，无缓存键组合爆炸。
- **代价与缓解**：单键承载全量数据，规模超阈值时需切换方案——阈值与路径见 §11.1。

### ADR-02 Redis 不可用时降级为直读 MySQL，而非内存缓存

- **状态**：接受
- **背景**：PRD 附录 A.3 明确"若声明使用 Redis，则不可用内存 Map 假替"。
- **决策**：`MenuCacheService` 捕获 Redis 连接异常后**直接查询 MySQL**，并累加 `fallbackToDatabase` 指标；接口保持可用。
- **理由**：Redis 定位为加速层而非事实来源。降级到事实来源是正确行为；降级到进程内缓存会造成多实例数据不一致，且违反 PRD 底线。

### ADR-03 并发正确性交给数据库唯一索引，而非分布式锁

- **状态**：接受
- **决策**：下单幂等靠 `UNIQUE(user_id, client_request_id)`；同日同餐次唯一靠 `UNIQUE(user_id, active_slot_key)` 生成列。
- **理由**：唯一索引是确定性的、无超时、无脑裂、无续期。分布式锁把正确性建立在"锁服务始终可靠"的假设上，反而更脆弱，且需要处理锁超时后业务是否继续的语义。
- **代价**：冲突时依赖异常路径返回友好提示——通过解析约束名区分冲突类型，回查后返回 200（幂等）或 409（餐次冲突）。

### ADR-04 购物车为前端状态，服务端下单时全量重算

- **状态**：接受
- **理由**：§3.3 无跨端一致性要求；服务端购物车引入过期清理与多端合并复杂度而无收益。
- **安全边界**：所有规则（份数上限、选项合法性、价格、餐次、重复下单）在 `@Transactional` 内用数据库数据重新校验，客户端传入的价格与总价**一律忽略**。

### ADR-05 业务时区固定 + 可注入 Clock

- **状态**：接受
- **背景**：§4.2 截单规则（10:00 / 15:00）对时区极度敏感，且需覆盖跨日切换。
- **决策**：`webox.time-zone=Asia/Shanghai`；`MealSlotResolver` 接收 `Clock` 参数而非调用 `LocalDateTime.now()`。
- **理由**：规则可被确定性单测覆盖，杜绝"到点后行为不一致"这类难以排查的生产问题。

### ADR-06 金额全链路整数分级精度方案

- **状态**：接受
- **决策**：DB `DECIMAL(10,2)` → Java `BigDecimal`（`HALF_UP`, scale 2）→ JSON 十进制字面量（`WRITE_BIGDECIMAL_AS_PLAIN`）→ 前端解析为**整数分**运算与格式化。
- **理由**：PRD §6 明确要求避免浮点精度问题。任一层使用 `double`/`float` 都会在加料累加与总额汇总时产生分位误差。

### ADR-07 JWT 存 localStorage 而非 httpOnly Cookie

- **状态**：接受（附带风险说明）
- **理由**：纯 SPA + Bearer 头方案无需 CSRF 防护；httpOnly Cookie 需要额外 CSRF token 机制，净增复杂度。
- **风险**：XSS 可直接读取 token。
- **缓解**：不使用 `v-html`；Vue 模板默认转义；服务端响应由 Jackson 转义；token 有效期 8 小时；服务端不信任任何客户端提交的价格与规则判断。
- **生产建议**：长期演进应切换为 httpOnly Cookie + CSRF token，或引入刷新令牌与短期访问令牌（见 §11.3）。

### ADR-08 图片存本地磁盘 + 受控读取接口

- **状态**：接受
- **决策**：上传文件以 UUID 重命名落盘 `${WEBOX_UPLOAD_DIR:./data/uploads}`；`dish.image_url` 存相对路径；读取走 `GET /api/files/images/{name}`，文件名必须匹配 `^[a-f0-9-]{36}\.(jpg|jpeg|png|webp)$`。
- **理由**：避免 BLOB 撑大数据库；严格正则白名单杜绝 `../` 路径穿越。
- **代价**：多实例部署需共享存储——属 Tier 3 之外的演进项（§11.3）。

### ADR-09 菜单缓存失效采用"写后按需失效 + TTL 兜底"

- **状态**：接受
- **决策**：菜品写操作 → 失效全部菜单快照；每日菜单写操作 → 仅失效该日期快照；两者均在**事务提交后**执行；TTL 10 分钟作为最终兜底。
- **理由**：菜品变更影响所有日期，必须全清；每日菜单变更影响面窄，精确失效降低抖动。批量删除用 `SCAN` 游标迭代，禁用 `KEYS`（会阻塞 Redis）。
- **代价**：菜品写操作会导致一次缓存冷启动——低频操作，可接受。

---

## 8. 安全设计

### 8.1 认证

- JWT HS256，密钥来自环境变量 `WEBOX_JWT_SECRET`，**不写入代码库**；缺失时启动失败（fail fast），杜绝默认弱密钥。
- 载荷：`sub`（userId）、`role`、`iat`、`exp`（8 小时）。
- 过滤器：`JwtAuthenticationFilter extends OncePerRequestFilter`，解析 Bearer 头 → 校验签名与过期 → 写入 `SecurityContext`。
- `SessionCreationPolicy.STATELESS`；CSRF 关闭（无 Cookie 会话）；CORS 仅放行配置的来源（dev 为 `http://localhost:5173`）。

### 8.2 授权

- `/api/auth/**`、`/api/files/**`、`/dishes/**`、`/actuator/health`、`/v3/api-docs/**`、`/swagger-ui/**` → `permitAll`
- `/api/admin/**` → `hasRole('ADMIN')`
- 其余 `/api/**` → `authenticated()`
- 资源级校验：订单查询/取消必须校验 `order.userId == currentUserId`，否则 403（不返回 404，避免泄露资源存在性——此处选择 403 以给出明确语义，见 `BUSINESS-DESIGN.md` §5.7）。
- **诚实说明**：角色取自 JWT 声明，token 有效期内角色变更不即时生效。生产环境应加 DB/Redis 校验或令牌版本号以支持即时吊销；本项目在 README 中明示该取舍。

### 8.3 输入校验

- DTO 使用 `record` + Bean Validation 注解；服务端校验为权威，前端镜像同一套规则用于即时反馈。
- 统一长度上限：邮箱/地址 ≤ 200，搜索关键词 ≤ 50，备注 ≤ 200，菜品名 ≤ 100，描述 ≤ 500，选项组/选项名 ≤ 60。
- 密码：`^(?=.*[A-Za-z])(?=.*\d)\S{8,64}$`（≥8 位且同时包含字母与数字），BCrypt(strength 10) 存储。
- 未知字段拒绝（Jackson `FAIL_ON_UNKNOWN_PROPERTIES` 开启）——防止客户端夹带未定义字段。

### 8.4 SQL 注入防护（P6+ 考察点）

| 措施         | 说明                                                                         |
| ---------- | -------------------------------------------------------------------------- |
| 全参数绑定      | Repository 全部经 JPA 派生查询 / `@Query` 具名参数 / Criteria API；**代码中不存在字符串拼接 SQL** |
| LIKE 通配符转义 | 关键词先转义 `\` `%` `_`，查询显式 `ESCAPE '\\'`，防止用户用 `%` 拉全表                        |
| 排序字段白名单    | `sort` 参数映射到枚举白名单后再转 `Sort.Order`，**绝不把用户输入拼进 `order by`**                 |
| 分页参数限幅     | `size` 上限 50，`page` 非负                                                     |
| 无动态表名/列名   | 不存在任何以用户输入决定表名或列名的代码路径                                                     |
| 集成测试固化     | 用例：关键词 `' OR 1=1 --` 返回 0 条且无异常（见 §10）                                     |

### 8.5 文件上传

- 大小 ≤ 2MB；扩展名 + `Content-Type` + **magic bytes** 三重校验。
- UUID 重命名，丢弃原始文件名（原始名可能含路径分隔符或脚本后缀）。
- 存储目录在 Web 根之外，仅通过受控接口按白名单正则读取。
- 上传目录不开启目录列表；不以静态资源映射方式直接暴露。

### 8.6 其它

- 错误响应不含堆栈与 SQL 片段；`INTERNAL_ERROR` 仅返回 requestId 供日志关联。
- 登录失败不区分"邮箱不存在/密码错误"，防账号枚举。
- 日志脱敏：不打印密码、token、完整 JWT 载荷。

---

## 9. 可观测性与运维

| 关注点    | 方案                                                                                                                                                                              |
| ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 健康检查   | Spring Boot Actuator `/actuator/health`，就绪探针包含 `db`；**`redis` 默认不计入整体健康状态**（Redis 为可选加速层，缺失时实例功能完好，若计入会让 `/actuator/health` 误报 503；生产环境可设 `MANAGEMENT_HEALTH_REDIS_ENABLED=true` 打开）                                                                                                                   |
| 请求链路   | `RequestIdFilter` 生成/透传 `X-Request-Id`，写入 MDC 与响应体 `requestId`，异常日志可关联                                                                                                          |
| 缓存效果   | `/actuator/metrics/cache.gets` 暴露命中率；`MenuCacheService` 自维护 `hit` / `miss` / `fallbackToDatabase` 计数                                                                            |
| 关键日志   | 注册、登录失败、下单成功（orderNo）、幂等命中、餐次自动切换、缓存失效、上传失败                                                                                                                                     |
| 配置外置   | 全部经环境变量覆盖：`WEBOX_DB_URL` `WEBOX_DB_USERNAME` `WEBOX_DB_PASSWORD` `WEBOX_REDIS_HOST` `WEBOX_REDIS_PORT` `WEBOX_JWT_SECRET` `WEBOX_UPLOAD_DIR` `WEBOX_TIME_ZONE`，附 `.env.example` |
| 本地基础设施 | `docker-compose.yml` 提供 `mysql:8.0`（healthcheck + volume）与 `redis:7-alpine`                                                                                                     |

---

## 10. 测试策略

分两层：**默认层零外部依赖、永远可过**；**端到端层连真实服务、验证并发语义**。

> 本节记录的是**实际交付并通过的测试**。截至交付：`mvn test` 56 项全绿；`scripts/smoke-test.ps1` 69 项断言全绿；`scripts/concurrency-test.ps1` 14 项断言全绿。

### 10.1 单元层：`./mvnw test`（56 项，零外部依赖）

| 测试类 | 数量 | 覆盖内容 |
| --- | --- | --- |
| `MealSlotResolverTest` | 14 | 截单决策表全量：09:00/Lunch→今日午餐；09:00/Dinner→今日晚餐；**10:30/Lunch→今日晚餐（PRD 原例）**；10:30/Dinner→今日晚餐；15:30/Lunch 与 15:30/Dinner→次日午餐；`09:59:59` 与 `10:00:00` 边界；`14:59:59` 与 `15:00:00` 边界；未来日期不切换；跨日恒可订；过去日期与超出可订窗口被拒；`nearestBookable` 与 `isBookable` |
| `PriceCalculatorTest` | 10 | `HALF_UP` scale=2 归一化；`0.10 + 0.20 = 0.30`；十次 `0.10` 累加恰为 `1.00`；加价累加单价；单价×数量小计；总额守恒；一单端到端手算对齐；预算比较含等号；空集合与 null 视为零 |
| `CartRuleValidatorTest` | 11 | 合法选择解析；按选项组顺序返回；多选组加价；必选组缺失被拒；可选组可空；SINGLE 组多选被拒；不属于该菜品的选项 id 被拒；**已下架选项即使传 id 也被拒**；无选项菜品；**3+3=6 份被拒、5 份通过**；空购物车被拒 |
| `SearchKeywordTest` | 8 | trim/小写/50 字符截断；`%` 转义；`_` 转义；`\` 自身转义；`' OR 1=1 --` 变为惰性字面量；空串与 null 不产生模式；名称与描述的大小写不敏感匹配 |
| `PasswordPolicyTest` | 13 | 5 个合法样本；5 个非法样本（纯字母/纯数字/过短/含空格/尾部空格）；null；8 位边界；64 位与 65 位边界 |

### 10.2 端到端层：`scripts/smoke-test.ps1`（69 项断言）

针对**真实运行的服务**执行完整 Tier 1 + Tier 2 链路，覆盖单测无法证明的集成行为：

| 分组 | 关键断言 |
| --- | --- |
| 健康 | 应用可达；`components.db.status = UP` |
| 注册登录 | 注册 201 且返回 token；重复邮箱 409 `EMAIL_ALREADY_REGISTERED`；纯字母密码 400；错误密码 401 且**不区分"邮箱不存在"**；`/auth/me` 恢复会话 |
| 权限边界 | 匿名访问菜单 401；**员工 token 访问 `/api/admin/**` 返回 403**；种子管理员可登录且角色为 `ADMIN` |
| 每日菜单 | 当日菜单已预置；卡片含价格与分类；详情含过敏原与辣度；多选分类只返回所选菜系；关键词命中名称与描述；**`' OR 1=1 --` 返回 0 条且无异常** |
| 购物车规则 | 预览返回服务端计价与解析后餐次 |
| 下单与幂等 | 缺 `Idempotency-Key` → 400 `IDEMPOTENCY_KEY_REQUIRED`；订单号形如 `WB*`；状态 `Pending`；**同键重复提交返回同一订单且 `duplicated = true`**；库中该键只有一条；同餐次第二单 409 `ORDER_SLOT_CONFLICT`；预览回传 `existingOrder` 供按钮切为 `View Existing Order` |
| 订单与取消 | 详情含明细与总额；`Pending` 可取消；**重复取消幂等返回 200**；取消后同餐次可再次下单 |
| 归属隔离 | 他人 token 读订单 403 |
| 偏好 | 偏好保存并可回读；`budgetMin > budgetMax` 被拒 |
| 推荐排序 | 开启 `prefer` 后偏好菜系置顶、`recommended = true` 且带 `matchTags`；菜品总数不变 |
| 过敏原 | **含已标记过敏原的菜品仍留在菜单中**（提醒而非过滤） |
| Console 菜品 | 表格分页；种子约 50 条；关键词搜索；新增菜品并保留选项组；下架后**员工端详情返回 404**；缓存已记录命中 |
| Console 每日菜单 | 可发布次日菜单并回读；供应数量落库；员工端可见次日菜单 |
| 英文校验 | 错误响应与菜单负载中**不含任何中文字符** |

### 10.3 并发层：`scripts/concurrency-test.ps1`（14 项断言）

用 .NET `HttpClient` 并发发出真实请求（不是串行循环），验证两条硬保证：

| 场景 | 断言结果 |
| --- | --- |
| 同一 `Idempotency-Key` **并发 10 次** | 10 个响应全部 200、**同一 `orderNo`**；恰好 1 个 `duplicated = false`、9 个 `duplicated = true`；库中该键只有 1 条订单 |
| **10 个不同 key** 并发抢同一餐次 | 分布 `200×1 + 409×9`；全部冲突码为 `ORDER_SLOT_CONFLICT`；库中该餐次只有 1 条有效订单 |
| 取消释放餐次 | 取消后同餐次可再次下单成功 |

这两条保证由 `uk_orders_user_client_request` 与 `uk_orders_user_active_slot` 两个唯一索引承担，因此**在 Redis 不可用或多实例部署下依然成立**。

### 10.4 为什么没有用 Testcontainers

原设计计划用 Testcontainers 跑集成测试，实现阶段改为**直连本机独立 MySQL 的 `webox_test` schema**（Flyway 每次重建）。原因：演示环境不保证有 Docker，而 PRD 的硬性要求是"MySQL 作为独立进程真实运行"——直连同一个独立实例同样满足，且少一层 Docker 依赖。并发语义由上面的 PowerShell 并发脚本在真实服务上验证，比进程内测试更接近真实部署。

---

## 11. 演进路线（Tier 3 扩展点预留）

> Tier 3 本次**不实现**。以下仅说明当前架构不需返工即可接入。

### 11.1 规模阈值

菜单快照策略适用至约 2000 条菜品。超过后切换为：DB 侧分页 + 筛选条件哈希作为缓存键 + 分类维度分段缓存。切换只影响 `MenuService` 内部分页位置，接口契约与前端不变。

### 11.2 库存实时展示与扣减（§5.1）

- 数据模型已就绪：`daily_menu_item.total_qty` / `sold_qty` 已存在。
- 扣减放在**下单同一事务内**，用条件更新保证不超卖：
  
  ```sql
  UPDATE daily_menu_item
     SET sold_qty = sold_qty + :qty
   WHERE id = :itemId AND sold_qty + :qty <= total_qty
  ```
  
  受影响行数为 0 即库存不足 → 回滚事务并返回具体缺货菜品。
- 取消订单时反向归还 `sold_qty`。
- 实时推送：SSE（`GET /api/menu/stream`）比 WebSocket 更贴合单向广播场景，且不引入新版协议与心跳维护成本。
- **注意**：库存不得放进菜单快照缓存，否则实时性失效；应走独立的短 TTL 读取路径或 Redis 计数器。

### 11.3 AI 智能推荐（§5.2）

- 输入已在库中：`user_preference`、`user_allergen`、当日可用菜品、`orders` 近 7 天记录。
- 接入点：新增 `recommend` 模块，调用 LLM 时以 SSE 流式回传；推荐结果必须以菜品卡片渲染，需返回 `dishId` 列表供前端组装。
- 硬约束：过敏原过滤与售罄排除在**服务端**完成，不依赖 LLM 遵守。
- 需要自备 LLM API Key（PRD §5.2 明确现场不提供）。

### 11.4 经营数据看板（§5.3）

- 全部指标可由 `orders` + `order_item` 直接聚合，无需改表。
- 今日概览 / 菜品排行 Top10 / 时段分布 / 近 7 天趋势 / 库存预警（`sold_qty >= total_qty - 3` 口径与 §5.1 一致）。
- 前端引入图表库（ECharts）后置于 `/console/dashboard`。

### 11.5 生产化演进

- 令牌：访问令牌（短）+ 刷新令牌，或切换 httpOnly Cookie + CSRF。
- 图片存储：迁移对象存储（S3/OSS），多实例共享。
- schema 变更：Flyway 迁移纳入 CI 校验。
- 缓存：多实例下失效改为 Redis Pub/Sub 广播，避免各实例本地状态不一致。

---

## 12. 与 PRD 的对应关系

逐条需求到设计、接口、验收标准的映射见 [`REQUIREMENT-TRACEABILITY.md`](./REQUIREMENT-TRACEABILITY.md)。

后端 P6+ 五项考察点落点速查：

| 考察点       | 分值  | 落点                                                          |
| --------- | --- | ----------------------------------------------------------- |
| 缓存策略      | 4   | ADR-01 / ADR-02 / ADR-09，§4.2、§4.5                          |
| 下单幂等      | 4   | ADR-03，§4.4 步骤 ②⑩，§5.1 `UNIQUE(user_id, client_request_id)` |
| SQL 注入防护  | 4   | §8.4，§10.2 注入用例                                             |
| 用户权限校验    | 4   | §8.2，§6.3 `FORBIDDEN`                                       |
| 并发安全（防超卖） | 4   | ADR-03，§5.3 生成列唯一索引，§11.2 条件更新预留                            |

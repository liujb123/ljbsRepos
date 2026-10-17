# WeBox 需求实现审查报告（对照 PRD V3.0）

> 审查日期：2026-10-17
> 基准文档：`AI Vibe Coding V3.0 - PRD.html` / `.md`（两份内容已逐节比对，一致）
> 审查对象：`D:\G\MyRep\webox`（README.md、docs/、backend/、frontend/、scripts/、ai-conversations/）
> 审查方式：**静态代码走查 + 实机运行实测**。后端单元测试、项目自带冒烟/并发脚本、以及独立编写的 UI 端到端驱动（Chrome DevTools Protocol）全部实际执行。

---

## 一、总体结论

> **更新（2026-10-17 16:1x）：缺陷 1 与缺陷 2 已修复并验证通过，详见 §十「修复记录」。**
> 下表为**修复后**的当前状态。

| 维度 | 判定 |
|---|---|
| **Tier 1 核心功能** | ✅ 完整可用（§3.2 菜系多选筛选已修复） |
| **Tier 2 进阶功能** | ✅ 完整可用（Console 分类筛选已修复） |
| **Tier 3 挑战功能** | ❌ 未实现（README 已如实声明「本次不做」） |
| **交付物清单** | ✅ 齐全（代码 / README / API 文档 / 架构说明 / 测试 / AI 对话原始记录） |
| **系统语言（强制英文）** | ⚠️ **仍有 1 项待修**：分页参数越界时返回中文校验消息（4 个端点，缺陷 3） |
| **技术选型硬指标** | ✅ JDK 17 + Spring Boot + 独立 MySQL 8 + Redis，全部满足 §7.1 |

**剩余待办：1 个 P1 缺陷（缺陷 3）+ 若干文档/环境项**，均不阻断交付，详见 §七。

---

## 二、实测环境与执行记录

| 组件 | 实机状态 |
|---|---|
| MySQL | `D:\G\MySQL\mysql-8.0.28-winx64`，独立进程，监听 3306 ✅ 非嵌入式 |
| Redis | `D:\G\Redis`，Windows 服务，监听 6379，**已连接**（actuator health: `redis UP, version 5.0.14`） |
| 后端 | `java -jar target/webox-backend-1.0.0.jar`，JDK 17.0.2，监听 8080 |
| 前端 | Vite dev server，监听 5173 |

> 注意：`JAVA_HOME` 目前**仍指向 JDK 1.8**，后端是靠显式用 `jdk-17.0.2\bin\java.exe` 启动的。
> 建议按 `docs/ENVIRONMENT-CHECK.md` 修正环境变量，否则 `mvnw` 直接构建会失败。

### 实际执行的测试

| # | 测试 | 结果 |
|---|---|---|
| 1 | `mvn test`（后端单元测试） | **56 / 56 通过**，BUILD SUCCESS |
| 2 | `scripts/smoke-test.ps1`（Tier 1+2 端到端） | **69 / 69 断言通过** —— ⚠️ 但其中 1 条是**假通过**（见缺陷 2） |
| 3 | `scripts/concurrency-test.ps1`（10 并发） | **14 / 14 通过**，幂等键 10 并发仅产生 1 单；同餐次 10 并发 `200×1 + 409×9` |
| 4 | 独立 CDP UI 端到端驱动（5 轮） | 认证/菜单/详情/购物车/结算/下单/订单/偏好/过敏原/权限/响应式/Console 均实测通过 |
| 5 | SQL 层直查（schema、索引、密码存储、金额类型） | 见下方证据 |

---

## 三、需求对照总表

### Tier 1 · 核心功能（PRD 30 分）

| PRD | 要求 | 实现 | 实测证据 |
|---|---|---|---|
| §3.1 | 邮箱注册 + 格式校验 + 不可重复注册 | ✅ | 重复邮箱 → `409 EMAIL_ALREADY_REGISTERED` |
| §3.1 | 密码 ≥8 位含字母数字 | ✅ | 无数字密码 → `400`；`PasswordPolicyTest` 13 项 |
| §3.1 | 不得明文存储 | ✅ | DB 中为 **BCrypt `$2a$10$…`，60 字符**；列名 `password_hash` |
| §3.1 | 刷新不丢登录态 | ✅ | token 存 `localStorage['webox.token']`，刷新后 `/api/auth/me` 恢复 |
| §3.1 | 输入长度限制 | ✅ | UI 实测：邮箱 `maxlength=200`、搜索框 `maxlength=50` |
| §3.1 | 区分员工/管理员角色 | ✅ | 员工访问 `/console/dishes` → 重定向 `/403`；`/api/admin/**` → 403 |
| §3.2 | 菜品卡片：图片/名称/价格/菜系标签 | ✅ | UI 截图 `01-menu-desktop.png` |
| §3.2 | 价格精确到分（¥22.50） | ✅ | DB 全部 `decimal(10,2)`，**无 float/double**；JSON 用字符串 `"22.00"` |
| §3.2 | 详情：描述/蛋白质/过敏原/辣度 | ✅ | `05: Spice level: None / Protein / Allergens` |
| §3.2 | 自定义选项 + 选后**价格实时更新** | ✅ | 实选加料后价格 `¥28.50 → ¥34.50` |
| §3.2 | 必选/可选分组、加价 | ✅ | 汉堡实测：Bun(required/SINGLE)、Sauce(required/SINGLE)、Add-ons(MULTI, +3/+5/+2) |
| §3.2 | **菜系筛选，支持多选** | ❌ **失效** | `?categories=Chinese` → **HTTP 400**。见**缺陷 1** |
| §3.2 | 关键词搜索菜名/描述 | ✅ | `chicken` → 6 道命中；后端 `SearchKeyword` 做了 `%`/`_` 转义 + `ESCAPE '\'` |
| §3.2 | 菜品多、高并发仍流畅 | ✅ | 50 菜品分页 5 页；20 次连续菜单请求 **avg 31ms / max 36ms**；Redis 整日快照缓存 |
| §3.3 | 加入购物车、同菜品可累加 | ✅ | 连点 5 次成功累加至 5 份 |
| §3.3 | 调整数量 / 移除 | ✅ | cart store `setQuantity` / `remove` |
| §3.3 | 实时显示总价 | ✅ | `07: Total` 呈现 |
| §3.3 | **同菜品不同配置视为不同条目** | ✅ | `buildLineKey(dishId, options)` → `` `${dishId}#${optionIds}` `` |
| §3.3 | 早高峰频繁点击 | ✅ | `usePendingAction` 单飞守卫（400ms 冷却）+ `useDebouncedRef` 搜索防抖 300ms |
| §3.4 | 结算页明细 + 小计含加料 + 总价 | ✅ | `PricedItems: unitPrice/quantity/subtotal/total` |
| §3.4 | 配送日期（默认当天）/ 餐次 / 地址 | ✅ | 餐次 `Lunch`/`Dinner`；地址可从历史选择或新输入 |
| §3.4 | **每次请求只创建一个订单** | ✅ | `Idempotency-Key` 必填；10 并发生成 1 单；DB 唯一索引 `uk_orders_user_client_request` |
| §3.4 | 订单成功页含订单号 + 配送信息 | ✅ | `WB20261018-000061` + 日期/餐次/地址 |
| §3.4 | 我的订单 + 详情 + 取消（仅 Pending） | ✅ | 列表、详情、取消均实测通过；重复取消幂等 |
| §3.4 | 状态枚举 Pending/Confirmed/Completed/Cancelled | ✅ | 枚举常量名即英文标签，绑定正常 |

### Tier 2 · 进阶功能（PRD 20 分）

| PRD | 要求 | 实现 | 实测证据 |
|---|---|---|---|
| §4.1 | 过敏原标记（7 项枚举） | ✅ | UI 实测 7 个 chip：Peanuts/Dairy/Egg/Gluten/Soy/Fish/Shellfish |
| §4.1 | **加入时弹窗提醒（不是过滤）** | ✅ | 弹窗原文与 PRD 一字不差：`This dish contains an allergen you flagged: [Peanuts]. Add anyway?`；含过敏原菜品**仍在菜单可见** |
| §4.1 | 菜系偏好 + 「为我推荐」开关 | ✅ | `prefer=true` 时偏好菜系置顶 + `recommended` 标记 + `matchTags` 理由 |
| §4.1 | 辣度/口味偏好 | ✅ | `spicePreference` / `tastePreference` 落库 |
| §4.1 | 单餐预算 + 超预算提示（不阻止） | ✅ | `budgetMin/Max` 校验 `min>max` → 400；结算页非阻断提示 |
| §4.2 | **每单最多 5 份（按份数非品种）** | ✅ | 3+3=6 → `400 ORDER_SIZE_EXCEEDED`；3+2=5 → 200。UI 达上限后按钮禁用并说明原因 |
| §4.2 | 截单时间 + 自动切换最近可订餐次 | ✅ | 见下方专项证据 |
| §4.2 | 不可重复下单 + 按钮变 View Existing Order | ✅ | 同餐次再下单 → `409 ORDER_SLOT_CONFLICT`；UI 实测按钮翻转为 `View Existing Order` |
| §4.3 | Console 独立导航布局 | ✅ | `ConsoleLayout` 与员工端 `EmployeeLayout` 分离 |
| §4.3 | 普通员工不可访问 Console | ✅ | 实测重定向 `/403` |
| §4.3 | 菜品表格 + 搜索 | ✅ | 表格渲染 10 行/页；搜索 `burger` → 1 行 |
| §4.3 | 分类筛选 | ❌ **失效** | `?category=Japanese` → **HTTP 400**（同一根因） |
| §4.3 | 新增/编辑菜品 + 上传图片 | ✅ | `POST /api/admin/dishes` 实测 201 并保留选项组；图片校验 JPG/PNG/WebP ≤2MB |
| §4.3 | 上架/下架，下架员工端不可见 | ✅ | `PATCH …/availability` → 员工端详情 `404` |
| §4.3 | 设置每日菜单 + 每日供应数量 | ✅ | `PUT /api/admin/daily-menus/{date}` 全量替换（幂等），`totalQty` 落库并可回读 |
| §4.3 | 预置当日可下单菜单与库存种子数据 | ✅ | 启动时幂等预置**今日 + 明日**菜单，每菜 50 份 |

#### 专项证据：截单自动切换（PRD §4.2 核心规则）

实测时刻 `15:35`（午餐 10:00、晚餐 15:00 均已截单）：

```
请求: deliveryDate=2026-10-17, mealSlot=Lunch
响应: {"mealDate":"2026-10-18","mealSlot":"Lunch","adjusted":true,
       "reasonCode":"DINNER_CUTOFF_PASSED"}          ← 正确切换为次日午餐

请求: deliveryDate=2026-10-17, mealSlot=Dinner
响应: {"mealDate":"2026-10-18","mealSlot":"Lunch","adjusted":true}  ← 正确
```

`MealSlotResolverTest`（14 项）覆盖了全部边界，含 PRD 原文举例：`10:30 提交午餐 → 当日晚餐`（`LUNCH_CUTOFF_PASSED`）、`10:00:00` 与 `15:00:00` 整点包含性、跨日回退、未来日期不切换。

### Tier 3 · 挑战功能（可选加分）

| PRD | 要求 | 实现 | 说明 |
|---|---|---|---|
| §5.1 | 库存实时展示与扣减、防超卖 | ❌ | 数据模型有 `total_qty`/`sold_qty`/`remaining_qty`，**但 `soldQty` 只读、从不递增**；无扣减事务；无 `INSUFFICIENT_STOCK` 错误码 |
| §5.1 | 实时同步（无需刷新） | ❌ | 全代码库**无 SSE / WebSocket / STOMP** |
| §5.2 | AI 智能推荐（流式） | ❌ | **无任何 LLM 集成**（无 openai/anthropic/deepseek/ChatClient 引用） |
| §5.3 | 经营数据看板 | ❌ | 无 dashboard/statistics/revenue 相关代码 |

> README「Known limitations」已如实声明 Tier 3 未实现，且 `Suggestion` 指向 `daily_menu_item` 的现有字段作为扩展点。**如实声明不算隐瞒**，但 PRD 的「功能完成度以 Tier 1+2 = 50 分为基数」也意味着这一层不影响门槛。

### 交付物清单（PRD §7.2）

| 交付物 | 状态 | 证据 |
|---|---|---|
| 可运行代码 | ✅ | 前后端完整，MySQL/Flyway/Redis 全链路跑通 |
| README | ✅ | 395 行，技术栈/启动步骤/账号/测试/架构/已知限制/演示脚本齐全 |
| API 文档 | ✅ | `API.md` 覆盖全部端点 + 错误码表；Swagger UI `/swagger-ui.html` 可访问 |
| 架构说明 | ✅ | `docs/ARCHITECTURE.md`（ADR、数据模型、并发决策）+ README「Architecture in brief」 |
| 测试 | ✅ | 56 单测 + 69 冒烟 + 14 并发，全部实际可运行 |
| **AI 编程对话记录** | ✅ | `ai-conversations/` 三个**原生 JSONL** 导出（0.89 / 0.96 / 7.39 MB），首个文件 228+ 行，含 session 元数据行 |

---

## 四、发现的缺陷

### 🔴 缺陷 1（P0）：菜系筛选完全失效 —— PRD §3.2 / §4.3 —— ✅ **已修复**

> **状态：已修复并验证通过（2026-10-17）。** 修复内容与证据见 §十「修复记录」。
> 以下保留原始诊断，作为该缺陷的完整记录。

**现象**：任何带菜系参数的请求都返回 HTTP 400。

```
GET /api/menu?date=2026-10-17&categories=Chinese              → 400
GET /api/menu?date=2026-10-17&categories=Japanese             → 400
GET /api/menu?date=2026-10-17&categories=Light%20Meal         → 400
GET /api/admin/dishes?category=Japanese                       → 400

响应体: {"code":"VALIDATION_FAILED","message":"The request contains invalid fields."}
```

**根因**：`String → enum` 的 **@RequestParam 绑定**与 JSON 契约不一致。

- `DishCategory` 枚举常量名是 `CHINESE` / `LIGHT_MEAL` / `SOUTHEAST_ASIAN`，
  而对外标签是 `Chinese` / `Light Meal` / `Southeast Asian`（`label` 字段）。
- 该枚举通过 `@JsonValue jsonValue()` / `@JsonCreator fromJson()` 让 **JSON 体**使用标签 —— 这部分工作正常。
- `converter/DishCategoryConverter` 是 **JPA 的 `AttributeConverter`**，只作用于**数据库持久化**，对 MVC 参数绑定**毫无作用**。
- 全项目**没有注册任何 Spring MVC 的 `Converter<String, DishCategory>` 或 `Formatter`**（已 grep 确认无 `WebMvcConfigurer` / `addFormatters`）。
- 于是 Spring 回退到默认的 `StringToEnumConverterFactory`，按**常量名、大小写敏感**匹配 → `Chinese` 匹配不上 `CHINESE` → `MethodArgumentTypeMismatchException` → `GlobalExceptionHandler`（`HttpMessageNotReadableException` 那一组）→ 400。

**验证对照**（用常量名就通了，正好反证）：

```
categories=CHINESE          → 200, total=9
categories=JAPANESE         → 200, total=8
categories=LIGHT_MEAL       → 200, total=9
categories=SOUTHEAST_ASIAN  → 200, total=7
categories=Chinese          → 400   ← 前端/JSON 契约发的是这个
```

**影响面**：前端 `menuApi.list()` 用 `URLSearchParams.append('categories', category)` 发送的是**标签**（与 `types/api.ts`、`API.md`、Swagger 的契约一致），因此：

- 员工端**点任何一个菜系 chip，菜单立刻变空** —— PRD §3.2 明确要求的「筛选时支持多选」**完全不可用**；
- Console 菜品管理的分类下拉同样失效 —— PRD §4.3 要求项。
- 这是**唯一一个让 PRD 明文功能整体不可用**的缺陷。

**修复方向**（任选其一，推荐第 1 个）：

1. 注册一个 MVC 转换器，复用已有的 `DishCategory.fromLabel()`：
   ```java
   @Component
   public class DishCategoryRequestConverter implements Converter<String, DishCategory> {
       @Override public DishCategory convert(String source) { return DishCategory.fromLabel(source); }
   }
   ```
   （`@Component` 的 `Converter` 会被 Spring Boot 自动注册到 `WebMvcConfigurer` 的 formatter registry。）
   注意需同时覆盖 `List<DishCategory>` 的元素转换。
2. 或给 `DishCategory` 加 `@JsonCreator` 之外的 Spring `Formatter<DishCategory>` 并在 `WebMvcConfigurer#addFormatters` 注册。
3. 或统一把枚举常量名改成标签形式（改动大，且 `Light Meal` 含空格不可作常量名，**不推荐**）。

**顺带需一并检查**：`ProteinType` / `SpiceLevel` / `Allergen` / `TastePreference` 是否也存在「常量名 ≠ 标签」却可能被用作 query 参数的情况。目前它们只出现在 JSON body 中（Jackson 处理，实测正常），暂无实际故障，但**同一个坑**建议一次性加测试兜住。
（`OrderStatus` 之所以正常，是因为它的常量名恰好就是 `Pending`/`Confirmed`/`Cancelled`，等于标签。）

---

### 🔴 缺陷 2（P0）：自带冒烟测试对上述缺陷**假通过** —— ✅ **已修复**

> **状态：已修复并验证通过（2026-10-17）。** 新增 10 条断言，并已实测证明其非空性。
> 修复内容与证据见 §十「修复记录」。

`scripts/smoke-test.ps1` 第 145–148 行：

```powershell
$filtered = Invoke-Api -Path "/api/menu?date=$today&categories=Chinese&categories=Japanese&size=50" -Headers $employeeHeaders
$cats = $filtered.Body.data.items | ForEach-Object { $_.category } | Sort-Object -Unique
Assert-That "multi-select cuisine filter returns only those cuisines" (
    ($cats | Where-Object { $_ -ne "Chinese" -and $_ -ne "Japanese" }).Count -eq 0) "got=$($cats -join ',')"
```

**问题**：断言只检查「没有出现意外菜系」，**从不检查 HTTP 状态码**。当请求 400 时 `data.items` 为 `null` → `$cats` 为空 → 「意外菜系」数量为 0 → **断言为真，打印 `[PASS]`**。

已实测复现：

```
HTTP status          : 400
body                 : {"code":"VALIDATION_FAILED",...}
items parsed         : 1
distinct categories  : []
unexpected cuisines  : 0
>>> 断言求值为 TRUE => 打印 [PASS]，尽管端点返回 400 且零菜品
```

**后果**：`README.md` 第 278 行宣称「69 assertions across every Tier 1 + Tier 2 flow」，而这 69 条里恰好漏掉了唯一一个失效的功能。这是比缺陷 1 更值得警惕的信号 —— **测试给了虚假的安全感**。

**修复方向**：
1. 该断言加 `Assert-That "…returns 200" ($filtered.Status -eq 200)`，并断言 `total -ge 1`；
2. **通盘审查** `smoke-test.ps1` 中所有断言，凡是对响应体做「排除式」判断的，都补一条状态码断言；
3. 建议顺手加一条：`categories=Chinese` 单选用例 + `Light Meal`（含空格）用例。

---

### 🟠 缺陷 3（P1）：分页参数越界返回**中文**校验消息 —— 违反 PRD §1 强制英文

PRD §1 明文：*「本系统面向用户的所有内容一律使用英文，包括但不限于——…提示与错误信息（toast / 弹窗 / **校验提示**）」*。

**实测**（4 个端点，可稳定复现）：

| 请求 | 响应 |
|---|---|
| `GET /api/menu?size=999` | `{"code":"VALIDATION_FAILED","message":"最大不能超过50","data":{"fieldErrors":{"getMenu.size":"最大不能超过50"}}}` |
| `GET /api/menu?page=-1` | `…"最小不能小于0"…` |
| `GET /api/orders?size=999` | `…"最大不能超过50"…` |
| `GET /api/admin/dishes?size=999` | `…"最大不能超过50"…` |
| `GET /api/admin/orders?size=999` | 同上 |

**根因**：四个 Controller 的分页参数写成

```java
@RequestParam(defaultValue = "0") @Min(0) int page,
@RequestParam(defaultValue = "12") @Min(1) @Max(50) int size
```

`@Min` / `@Max` **没有指定 `message` 属性**，Hibernate Validator 于是回退到 `Locale.getDefault()`（本机 JVM 为 `zh_CN`）解析 `hibernate-validator` jar 内的 `ValidationMessages_zh_CN.properties`，产出中文。

**为什么只有这 4 处泄漏**：项目其他 DTO 的约束**都显式写了英文 message**（例如 `DailyMenuUpsertRequest` 的 `@Min(value = 0, message = "Daily quantity must be zero or greater.")`、`OrderLineRequest` 的 `@Min(…, message = "Quantity must be at least 1.")`）。所以英文覆盖是「逐个手写」而非「全局兜底」，一旦漏写就按 JVM locale 泄漏。实测 21 个异常路径探针中，**只有这 4 个分页参数越界的中文**，其余 17 个（缺失字段、格式错误、枚举非法、金额非法、空数组、畸形 JSON 等）全部返回英文。

**修复方向**（推荐 1 + 3 双保险）：

1. 给这 8 处约束补英文 message：
   ```java
   @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must be zero or greater.") int page,
   @RequestParam(defaultValue = "12") @Min(value = 1, message = "Page size must be at least 1.")
                                      @Max(value = 50, message = "Page size must be at most 50.") int size
   ```
2. 或移除参数级约束，改在 service 里显式校验（更可控的文案）。
3. **加全局兜底**：在 `src/main/resources/` 放 `ValidationMessages_zh_CN.properties`（英文内容），
   它会**遮蔽** hibernate-validator jar 内的同名资源，从而不论 JVM locale 如何都输出英文。
   或启动时 `Locale.setDefault(Locale.ENGLISH)`（较脆弱，仅作补充）。
4. 冒烟测试补一条针对 `size=999` 的中文字符断言（现有测试只检查了登录错误与菜单响应体，没覆盖参数校验路径）。

---

## 五、其他问题与观察（不阻断交付）

### 1. Console「订单处理」只有后端、没有前端页面

PRD §1 描述管理员职责为「管理菜品、**处理订单**」；`API.md` 也确实列出了
`GET /api/admin/orders` 与 `PATCH /api/admin/orders/{id}/status`（后端已实现且实测可用）。
但前端 `views/console/` 下只有 `ConsoleDishListView` / `ConsoleDishFormView` / `ConsoleDailyMenuView`，
**没有订单管理页面**，路由中也没有对应入口。

PRD §4.3 的**强制要求只有「菜品管理」**，因此这不算硬性缺失；但若面试官按 §1 的职责描述追问
「管理员怎么处理订单」，目前只能用 Swagger 演示。**建议补一个只读的 Console 订单列表页**（成本低、收益明显）。

### 2. README 的「Suggested demo walkthrough」第 2 步会当场翻车

`README.md` 第 383 行：

> 2. Menu: multi-select `Chinese` + `Japanese`, search for `chicken`, toggle **Recommend for me**.

这一步正是缺陷 1 中失效的功能 —— **现场按这个脚本演示会点出空列表**。修复缺陷 1 后此脚本才成立。

### 3. `ai-conversations/README.md` 自相矛盾

同一文件内两处陈述冲突：

- 「Before publishing」段：*「These transcripts contain local development credentials… this machine's Redis password, the local MySQL root password, and the throwaway JWT secret」*
- 「Scope and honesty notes」段：*「**No credentials, tokens or passwords appear in the transcripts**; the agent never echoed secrets.」*

第二句与第一句直接冲突，且与事实不符（第一句才是对的）。评审者若读到会质疑交付记录的严谨性。**建议删除或改写第二句。**

### 4. ⚠️ 明文凭据与对外发布

`docs/ENVIRONMENT-CHECK.md`（本报告作者先前生成的核查报告）中含本机 Redis 明文密码；
`ai-conversations/*.jsonl`（逐字导出）含开发期的 Redis 密码、MySQL root 密码与本地 JWT secret。
`README.md` 第 341–364 行的「Publishing this repository」已给出三个处理方案，**该提醒是充分的**。
但请注意：**这两个文件目前都在待提交清单里**（`git status` 显示 `webox/` 整体未跟踪）。
若走 PRD §7.2 的「提交到仓库」路线，务必先按 README 方案 A/B/C 处理。

> 我可以代为把 `docs/ENVIRONMENT-CHECK.md` 里的密码改为 `***`（只改这一处，并注明），
> 你确认后我再动手。

### 5. 菜品图片与菜名语义不符（观感问题）

UI 截图中：`Kung Pao Chicken` 配的是椰子水易拉罐图，`Caesar Salad` 配的是芒果果汁图，
`Salmon Sashimi Set` 配的是一碗绿色浓汤图。原因是 `README.md` 第 216 行所述
「20 bundled dish photos, bound **cyclically** to the catalog」。

PRD §3.2 只要求卡片「包含菜品图片」，**形式上满足**；但演示时观感明显不专业。
若时间允许，建议手工把 20 张图绑到语义相近的菜品上，或给未匹配的菜品用统一的占位图。

### 6. 环境变量仍未修正

`JAVA_HOME` 依旧指向 `C:\Program Files\Java\jdk1.8.0_361`（JDK 8）。
后端目前能跑是因为启动脚本显式指定了 JDK 17 的 `java.exe`。
按 README 的 `./mvnw spring-boot:run` 步骤执行时，**若 shell 的 JAVA_HOME 为 JDK 8 会直接失败**
（实测：`Fatal error compiling: 无效的标记: --release`）。建议修正 `JAVA_HOME` 并在 README「Prerequisites」中明确要求。

### 7. 脚本依赖 PowerShell 7（`pwsh`），本机只有 5.1

`README.md` 与 `scripts/*.ps1` 的示例命令都写 `pwsh -File scripts/…`，但本机**未安装 PowerShell 7**
（`pwsh` 不在 PATH）。实测用 `powershell.exe`（5.1）**可以正常运行** `smoke-test.ps1` 与 `concurrency-test.ps1`，
所以只是文档与环境的落差。建议 README 补一句「Windows PowerShell 5.1 亦可」，或安装 PowerShell 7。

---

## 六、通过项汇总（已实测，非推断）

### 后端

- ✅ `mvn test`：**56 项单元测试全过**（MealSlotResolver 14 / CartRuleValidator 11 / PriceCalculator 10 / PasswordPolicy 13 / SearchKeyword 8）
- ✅ 截单自动切换、5 份上限、幂等提交、同餐次冲突全部实测通过
- ✅ 数据库层强约束（**不是应用层加锁**）：
  - `uk_orders_user_client_request (user_id, client_request_id)` → 幂等
  - `uk_orders_user_active_slot (user_id, active_slot_key)` → 同餐次唯一有效订单（生成列在取消/完成后变 NULL，等价部分唯一索引）
- ✅ 金额列**全部 `decimal(10,2)`**，全库无 `float`/`double`
- ✅ 密码 **BCrypt `$2a$10$`，60 字符**，列名 `password_hash`，无明文
- ✅ Flyway `V1__schema.sql` 管理 schema，`ddl-auto: none`（Hibernate 不改结构）
- ✅ SQL 注入防护：关键字参数绑定 + `%`/`_` 转义 + `ESCAPE '\'`；实测 `' OR 1=1 --` 返回 0 条且不报错
- ✅ 缓存：整日菜单快照（Redis，TTL 10 分钟），命中计数可查；Redis 不可用时降级直读 MySQL（**不是内存 Map**，符合 PRD 附录 A.3 底线）

### 前端（CDP 驱动，真实交互）

- ✅ 注册 → 自动登录 → 落在菜单页；token 持久化，刷新不丢
- ✅ 菜单 12 卡片/页、搜索 `chicken` 命中 6 道（大小写不敏感）
- ✅ 详情页选项交互，选加料后价格 `¥28.50 → ¥34.50` **实时更新**
- ✅ 购物车达 5 份上限后：卡片按钮禁用 + 文案 `You can order at most 5 items per order.`，购物车 `+` 禁用并带 tooltip
- ✅ 结算页：地址为空时 `Place Order` 禁用，填入合法地址后启用（校验生效）
- ✅ 下单成功 → `/order-success/39`，订单号 `WB20261018-000061`
- ✅ 再次进入结算页，按钮翻转为 **`View Existing Order`**
- ✅ 过敏原：标记 Peanuts/Egg → 加含 Peanuts 的菜 → 弹窗文案与 PRD 完全一致 → `Add anyway` 成功加入、`Cancel` 不加入
- ✅ 权限：员工访问 `/console/dishes` → `/403`；未知路由 → 英文 404
- ✅ 响应式：375×812 视口单列渲染，**无横向溢出**
- ✅ Console：菜品表格 10 行、搜索 `burger` 过滤到 1 行、每日菜单页正常
- ✅ **全程 UI 文案无中文**（含菜单数据、错误提示、按钮）
- ✅ **浏览器控制台无未捕获错误**

---

## 七、修复优先级建议

| 优先级 | 事项 | 状态 |
|---|---|---|
| **P0** | 缺陷 1：注册 `Converter<String, DishCategory>`（MVC 层），使 `categories=Chinese` / `Light Meal` 可用 | ✅ **已完成** |
| **P0** | 缺陷 2：`smoke-test.ps1` 补状态码断言；审查其余「排除式」断言 | ✅ **已完成**（新增 10 条断言，共 79 条） |
| **P1** | 缺陷 3：8 处分页约束补英文 message + 加 `ValidationMessages_zh_CN.properties` 兜底 | ⬜ 待办（本轮按要求跳过） |
| **P1** | 修正 `JAVA_HOME` 指向 JDK 17（否则按 README 步骤启动会失败） | ⬜ 待办 |
| **P1** | 删除 `ai-conversations/README.md` 中自相矛盾的那句话 | ⬜ 待办 |
| **P2** | 发布前按 README 方案处理明文凭据（含 `docs/ENVIRONMENT-CHECK.md`） | ⬜ 待办 |
| **P2** | Console 补一个订单列表页（后端接口已就绪） | ⬜ 待办 |
| **P3** | 菜品图片改为语义匹配绑定 | ⬜ 待办 |
| **P3** | Tier 3（库存扣减/实时/AI 推荐/看板）—— 可选加分项，README 已声明不做 | ⬜ 待办 |

---

## 八、复现命令

```powershell
# 1. 环境（必须先切 JDK 17）
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17.0.2'

# 2. 后端单元测试
cd D:\G\MyRep\webox\backend; .\mvnw test

# 3. 端到端 + 并发（后端需先启动）
powershell -File scripts\smoke-test.ps1
powershell -File scripts\concurrency-test.ps1

# 4. 复现缺陷 1：菜系筛选
$t = (Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login `
      -ContentType application/json -Body '{"email":"admin@webox.com","password":"Admin1234"}').data.token
$h = @{ Authorization = "Bearer $t" }
$d = (Get-Date).ToString('yyyy-MM-dd')
try { Invoke-RestMethod "http://localhost:8080/api/menu?date=$d&categories=Chinese" -Headers $h }
catch { "EXPECTED FAILURE: HTTP $([int]$_.Exception.Response.StatusCode)" }

# 5. 复现缺陷 3：中文校验消息
try { Invoke-RestMethod "http://localhost:8080/api/menu?date=$d&size=999" -Headers $h }
catch { (New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd() }
# => {"code":"VALIDATION_FAILED","message":"最大不能超过50", ...}
```

---

## 九、审查方法说明与局限

**做了什么**：逐条比对 PRD 全部 5 章 + 附录 A 的要求；通读后端 88 个 Java 源文件的关键路径、前端 48 个源文件；实际运行 4 类测试套件；用 Chrome DevTools Protocol 编写 5 轮独立 UI 驱动脚本做真实交互（非仅截图）；直查 MySQL schema/索引/数据。

**诚实说明**：
- 首轮 UI 驱动有 9 条「失败」实为**我自己脚本的选择器错误**（如用 `input[type=text]` 匹配省略 `type` 属性的输入框、用 `innerText` 匹配 `<option>` 与 `placeholder`、未等待 400ms 单飞冷却就连点），已逐条重测修正。**最终报告中标注为缺陷的仅 3 项**，均为可稳定复现的真实问题。
- 未做：Tier 3 功能无法测试（未实现）；未做压力测试（仅 20 次串行延迟采样 + 项目自带的 10 并发）；未审查全部前端样式细节。
- 截图证据已存于 `docs/audit/`（10 张，来自本次实测）。

---

## 十、修复记录（缺陷 1 + 缺陷 2）

> 完成时间：2026-10-17 16:1x ｜ 范围：仅缺陷 1 与缺陷 2（缺陷 3 按要求本轮跳过）

### 10.1 改动清单

| # | 文件 | 类型 | 说明 |
|---|---|---|---|
| 1 | `backend/src/main/java/com/webox/converter/DishCategoryRequestConverter.java` | **新增**（46 行） | MVC 层 `Converter<String, DishCategory>`，复用已有的 `DishCategory.fromLabel()`；标注为 `@Component` 由 Spring Boot 自动注册进 MVC 转换服务 |
| 2 | `backend/src/test/java/com/webox/converter/DishCategoryRequestConverterTest.java` | **新增**（84 行） | 27 项参数化测试：6 个标签、6 个常量名、大小写/空格容错、URL 解码后的 `Light Meal`、全枚举往返、null 输入、6 个非法值抛异常 |
| 3 | `scripts/smoke-test.ps1` | **修改**（+约 35 行 / −3 行） | 菜系筛选补 6 条断言（含单选、含空格标签）；Console 分类筛选补 2 条；偏好排序补 2 条防空比较断言 |

**没有任何文件被重构，没有接口契约变化，没有数据库变更，前端零改动**（前端发的 `Chinese` 本来就符合 JSON 契约，错的是后端）。

**关键设计取舍**：转换器对未知值**抛异常而不是返回 null**。因为 `MenuService#toCategorySet` 内部用 `EnumSet.copyOf(categories)`，null 元素会炸成 HTTP 500；抛异常则规范地落到 400。

### 10.2 验证证据

| 验证 | 结果 |
|---|---|
| 后端单元测试 | **83 / 83 通过**（原 56 + 新增 27），`BUILD SUCCESS` |
| 冒烟测试 | **79 / 79 断言通过**（原 69 + 新增 10） |
| 接口层：6 个菜系标签 | 全部 **HTTP 200**，且返回结果只含该菜系 |
| 接口层：多选 | `categories=Chinese&categories=Japanese` → `200`，total=17，菜系=`[Chinese, Japanese]` |
| 接口层：Console 分类 | `?category=Japanese` → `200`，total=8 |
| 接口层：非法值回归 | `Seafood` → 400、`Unknown` → 400（**不是 500**） |
| 接口层：常量名兼容 | `categories=CHINESE` → `200`，total=9（未破坏原有调用） |
| **UI 验收（浏览器真实点击）** | 点 `Japanese` → 8 张卡片、菜系仅 `Japanese`；再加 `Chinese` → `Chinese,Japanese`；再加 `Light Meal` → 三种共存；`Clear filters` 恢复全部 6 类 |
| UI 验收（Console） | 分类下拉选 `Japanese` → 表格 8 行，Category 列取值全部为 `Japanese` |

截图：`docs/audit/28-cuisine-filter-fixed.png`、`29-cuisine-multiselect-fixed.png`、`30-console-category-fixed.png`。

### 10.3 证明新断言不再是「假通过」

只让测试变绿不算修好，必须证明**它现在真的会红**。做法：用非法菜系 `categories=Seafood` 制造一个**真实的 400 响应**（与修复前的失败完全同一条代码路径），再把新旧断言谓词各自代入。

| 谓词 | 喂入真实 400 时的结果 | 判定 |
|---|---|---|
| **旧**：`(unexpected cuisines).Count -eq 0` | `True` | ❌ **假通过** —— 400 时没有菜品，自然「没有意外菜系」 |
| **旧**：`$normal.total -eq $preferred.total` | `True` | ❌ **假通过** —— `null -eq null` |
| **新**：`$resp.Status -eq 200` | `False` | ✅ 正确判红 |
| **新**：`($cats -join ',') -eq "Japanese"` | `False` | ✅ 正确判红 |

同一组新断言在真实 200 响应上全部为 `True`。**结论：断言既不会在故障时漏报，也不会在正常时误报。**

### 10.4 复现与回滚

```powershell
# 复现修复后的行为
cd D:\G\MyRep\webox\backend
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17.0.2'
.\mvnw clean package            # 83 个单测
cd ..; powershell -File scripts\smoke-test.ps1   # 79 条断言
```

```powershell
# 回滚：删除两个新文件即可恢复原状（其余文件无功能耦合）
Remove-Item backend\src\main\java\com\webox\converter\DishCategoryRequestConverter.java
Remove-Item backend\src\test\java\com\webox\converter\DishCategoryRequestConverterTest.java
# scripts\smoke-test.ps1 的断言改动可保留——它只是更严格，不依赖上面的类
```

### 10.5 需要你知道的副作用

1. **后端已重启**（现 pid 见 `Get-NetTCPConnection -LocalPort 8080`），启动方式与原命令一致：
   `java -jar target\webox-backend-1.0.0.jar`，工作目录 `backend/`。
   本次额外显式设置了 `WEBOX_REDIS_PASSWORD`，并在 `backend/` 下生成 `backend.log` / `backend.err.log`
   （`.gitignore` 已忽略 `*.log`）。
2. **浏览器里的登录态已失效**：`WEBOX_JWT_SECRET` 未配置时使用临时密钥，重启即换。重新登录即可。
3. **`application.yml` 在 15:37 被并行改过**（Redis 健康检查改为 opt-in，默认 false），
   那是本次修复之外的改动，我未触碰。因此 `/actuator/health` 不再列出 `redis` 组件，属预期行为；
   Redis 本身连接正常（`fallbacks: 0`，`DBSIZE=398`）。

---

*本报告所有结论均基于实机命令输出或源码定位，未作推断。标注为「实测」的条目均可由第八节命令复现。*

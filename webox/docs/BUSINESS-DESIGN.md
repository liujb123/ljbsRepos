# WeBox · 业务实现方案（Business Design）

| 项目 | 内容 |
| --- | --- |
| 文档版本 | v1.0（设计阶段） |
| 对应需求 | `AI Vibe Coding V3.0 - PRD.md` |
| 覆盖范围 | Tier 1（PRD §3）+ Tier 2（PRD §4） |
| 明确不含 | Tier 3（PRD §5）：库存实时与扣减、AI 推荐、经营看板 |
| 文档状态 | **设计定稿，待实现** |

> 本文档回答**业务规则怎么算、怎么落库、怎么呈现**。系统怎么搭见 [`ARCHITECTURE.md`](./ARCHITECTURE.md)；每条 PRD 需求落在哪里见 [`REQUIREMENT-TRACEABILITY.md`](./REQUIREMENT-TRACEABILITY.md)。

---

## 1. 术语与枚举字典

| 中文 | 英文标识 | 取值 / 说明 |
| --- | --- | --- |
| 菜系分类 | `DishCategory` | `Chinese` `Western` `Japanese` `Light Meal` `Korean` `Southeast Asian` |
| 辣度 | `SpiceLevel` | `None` `Mild` `Medium` `Hot` |
| 过敏原 | `Allergen` | `Peanuts` `Dairy` `Egg` `Gluten` `Soy` `Fish` `Shellfish`（固定全集，海鲜统一用 `Fish`/`Shellfish`，不单列 `Seafood`） |
| 蛋白质来源 | `ProteinType` | `Chicken` `Beef` `Pork` `Fish` `Shrimp` `Egg` `Tofu` `None`（一菜多值） |
| 订单状态 | `OrderStatus` | `Pending` `Confirmed` `Completed` `Cancelled` |
| 用餐时段 | `MealSlot` | `Lunch` `Dinner` |
| 口味浓淡 | `TastePreference` | `Light` `Balanced` `Rich` |
| 用户角色 | `UserRole` | `EMPLOYEE` `ADMIN` |
| 选项组类型 | `SelectionType` | `SINGLE` `MULTI` |
| 餐次（日期+时段） | Meal | 业务日期 + 用餐时段的组合，是"同日同餐次唯一"的判定粒度 |

**系统语言**：以上枚举的存储值、接口返回值、界面展示值全部为英文。所有面向用户的文案见 §11，实现时照抄，不得出现中文。

---

## 2. §3.1 员工注册 / 登录

### 2.1 注册

| 环节 | 规则 |
| --- | --- |
| 邮箱 | 必填；格式校验；≤200 字符；入库前 `trim()` + 转小写；**不校验公司域名**（PRD 明确） |
| 邮箱唯一 | 应用层先查一次给友好提示；`UNIQUE(email)` 唯一索引为最终权威，并发注册时捕获冲突返回 `EMAIL_ALREADY_REGISTERED`（409） |
| 显示名 | 必填；≤50 字符 |
| 密码 | 必填；**≥8 位且同时包含字母与数字**；正则 `^(?=.*[A-Za-z])(?=.*\d)\S{8,64}$`；禁止空白字符 |
| 确认密码 | 仅前端校验一致性，不提交到服务端 |
| 存储 | BCrypt(strength 10)；**绝不落明文、绝不出参、绝不进日志** |
| 默认角色 | `EMPLOYEE`。管理员账号由种子数据创建，不开放自助注册 |

### 2.2 登录

- 校验失败（邮箱不存在 **或** 密码错误）统一返回 `AUTH_INVALID_CREDENTIALS`（401）+ 英文 `Incorrect email or password.`，**不区分二者**，避免账号枚举。
- 成功返回 JWT（HS256，8 小时）+ `{ userId, email, displayName, role }`。
- 前端将 token 存入 `localStorage`，axios 请求拦截器注入 `Authorization: Bearer`。

### 2.3 刷新页面不丢登录态

```
SPA 冷启动 → 若 localStorage 有 token → GET /api/auth/me
   ├─ 200 → 写入 auth store，按 role 决定默认落地页
   │         EMPLOYEE → /          ADMIN → /console/dishes
   ├─ 401 → 清除 token → 跳 /login
   └─ 网络错误 → 保留 token，展示可重试的英文提示（不误清登录态）
```

### 2.4 角色与权限边界

| 能力 | `EMPLOYEE` | `ADMIN` |
| --- | --- | --- |
| 浏览菜单 / 购物车 / 下单 / 我的订单 / 偏好设置 | ✅ | ✅ |
| 访问 `/console/*` 与 `/api/admin/**` | ❌ → 前端路由守卫拦截 + 后端 403 `FORBIDDEN` | ✅ |

**双层防护**：前端路由守卫只影响体验，**后端 `hasRole('ADMIN')` 才是安全边界**。演示时直接以员工 token 调 `/api/admin/dishes` 必须返回 403。

### 2.5 输入长度与安全处理（全局统一）

| 字段 | 上限 | 处理 |
| --- | --- | --- |
| 邮箱 | 200 | Bean Validation `@Email` + `@Size(max=200)` |
| 配送地址 | 200 | `@Size(max=200)` |
| 搜索关键词 | 50 | `@Size(max=50)`；LIKE 通配符转义（见 `ARCHITECTURE.md` §8.4） |
| 订单备注 | 200 | `@Size(max=200)` |
| 菜品名 / 描述 | 100 / 500 | 管理端 DTO 约束 |
| 选项组名 / 选项名 | 60 | 管理端 DTO 约束 |

特殊字符：服务端全程参数绑定；响应 JSON 由 Jackson 转义；前端**禁用 `v-html`**，菜品描述等一律文本插值渲染。

---

## 3. §3.2 每日菜单

### 3.1 可见性判定（唯一口径）

某菜品出现在日期 D 的员工端菜单中，当且仅当：

```
存在 daily_menu.menu_date = D
且 存在 daily_menu_item(daily_menu_id, dish_id)
且 dish.is_available = true          ← 下架菜品对员工不可见（§4.3）
```

管理端将已下架菜品加入某日菜单时给出英文警告，但不阻断（便于先排期后上架）。

### 3.2 菜单卡片

| 元素 | 数据来源 | 呈现 |
| --- | --- | --- |
| 图片 | `dish.image_url` | 固定宽高比容器，`loading="lazy"`，加载失败显示英文占位 |
| 名称 | `dish.name` | 主标题，超长省略并保留完整 title |
| 价格 | `dish.price` | **`¥22.50`**（符号 `¥` + 两位小数，精确到分，见 §10.2） |
| 菜系标签 | `dish.category` | 徽标，英文原值 |
| 辣度 | `dish.spice_level` | 非 `None` 时展示徽标 |
| 过敏原 | `dish_allergen` | 与当前用户 `user_allergen` 有交集时高亮警示徽标 |
| 加入购物车按钮 | — | 见 §4 份数上限与 §4.3 过敏原提醒 |

### 3.3 日期选择

- 菜单页默认展示**当日菜单**（PRD §3.2："主页面是当日菜单"）。
- 提供日期选择器，范围为 `[今天, 今天+7]`，**仅列出已发布菜单的日期**（`GET /api/menu/dates`）。
- 切换到尚无菜单的日期 → 英文空状态 `No menu has been published for this date.`

> 该选择器同时带来一个演示价值：管理员刚为次日排好菜单，可直接在员工端切到次日验证，无需改系统时间。

### 3.4 菜品详情

点击卡片进入详情，包含：

| 区块 | 内容 |
| --- | --- |
| 基础 | 图片、名称、菜系、当前价格（随选项实时变化） |
| 描述 | `dish.description` |
| 成分信息 | 蛋白质来源（多值）、过敏原（多值）、辣度等级 |
| 自定义选项 | 按 `dish_option_group.display_order` 渲染；标注 `Required` / `Optional` |
| 选项交互 | `SINGLE` 组用单选（必选组默认不预选，强制用户显式选择）；`MULTI` 组用多选；每个选项显示 `+¥3.00` 加价 |
| 价格联动 | 任一选项变化 → 前端以**整数分**重算：`unitPrice = basePriceCents + Σ selectedDeltaCents` |
| 必选校验 | 存在未选择的必选组时，`Add to Cart` 禁用并提示 `Please choose an option for "Bun".` |

### 3.5 分类筛选（多选）

- 请求：`GET /api/menu?categories=Chinese&categories=Japanese`（同名参数重复出现）。
- 服务端：`WHERE category IN (:categories)` 语义；**多选之间是 OR**（PRD 示例：同时查看 `Chinese` 和 `Japanese`）。
- 未选任何分类 = 不过滤。
- 前端：可多选的筛选 chips，选中态清晰，提供 `Clear filters`。
- 切换筛选后重置到第 1 页（避免停在越界页码）。

### 3.6 搜索

- 请求：`GET /api/menu?keyword=chicken`。
- 匹配范围：**菜品名称或描述**（PRD §3.2），大小写不敏感，子串匹配。
- 实现：`name LIKE :kw OR description LIKE :kw`（参数绑定），关键词先转义 `\` `%` `_` 并显式 `ESCAPE '\\'`。
- 上限 50 字符，超出截断并由服务端返回 `VALIDATION_FAILED`（前端同步限制输入框 `maxlength`）。
- 前端 300ms 防抖后再发请求。
- 空结果 → `No dishes match your search.` + `Clear search`。

### 3.7 性能策略（对应 PRD §3.2"数量较多、访问量较高时仍能流畅使用"）

| 层面 | 措施 |
| --- | --- |
| 服务端 | Redis 缓存**整日菜单快照**（`webox:menu:{date}`，TTL 10 分钟）；筛选/搜索/排序/分页在内存完成；一次读取服务 9:30–10:00 全部并发 |
| 失效 | 菜品写操作 → 全量失效；每日菜单写操作 → 按日期失效；事务提交后执行；TTL 兜底 |
| 降级 | Redis 不可用 → 直读 MySQL，接口不失败（不启用内存缓存假替） |
| 传输 | 分页默认 12 条 / 页，上限 50 |
| 前端 | 骨架屏占位、图片懒加载、TanStack Query 缓存与请求去重、筛选切换不闪烁 |
| 演进 | 快照规模超 2000 条时切 DB 侧分页（见 `ARCHITECTURE.md` §11.1） |

### 3.8 "为我推荐"开关（§4.1 联动）

- 菜单页提供开关，文案 **`Recommend for me`**；状态存前端 localStorage（纯视图偏好）。
- 开启时请求带 `prefer=true`；服务端排序键：

```
1. 菜品分类 ∈ user_preferred_category ? 0 : 1        （偏好菜系优先）
2. 菜品辣度 == user_preference.spice_preference ? 0 : 1 （辣度匹配优先）
3. daily_menu_item.display_order ASC                  （稳定默认顺序）
```

- 每道命中项返回 `recommended: true` 与 `matchTags: ["Preferred cuisine", "Matches your spice level"]`，前端卡片高亮并展示标签。
- 关闭时 → 纯 `display_order` 默认顺序（PRD 明确"关闭时按默认顺序展示"）。
- 未登录或未配置偏好时 `prefer=true` 为空操作，不报错。

---

## 4. §3.3 购物车

### 4.1 状态归属

购物车是**前端状态**：Pinia store + `localStorage` 持久化。服务端不存购物车。理由与安全边界见 `ARCHITECTURE.md` ADR-04：所有规则在下单事务内用数据库数据重新校验，客户端提交的价格一律忽略。

### 4.2 行标识：同菜品不同配置分行

```
lineKey = `${dishId}#${selectedOptionIds.sorted().join('-')}`
```

- 同菜品 + 同配置再次加入 → 命中已有行，`quantity += 1`（PRD："同一菜品可重复添加，每次点击数量加一"）。
- 同菜品 + 不同配置 → **新行**（PRD 示例：一个全麦汉堡和一个普通汉堡分别显示）。
- 每行结构：

```ts
{
  lineKey, dishId, menuDate,
  name, imageUrl, category,
  basePriceCents,                       // 整数分
  options: [{ groupId, groupName, optionId, optionName, priceDeltaCents }],
  unitPriceCents,                       // basePriceCents + Σ priceDeltaCents
  quantity,
  allergens: Allergen[]
}
```

### 4.3 加入购物车时的过敏原提醒（§4.1）

```
onAddToCart(dish):
  if (!allergensEnabled) return add(dish)                    // 未登录/未配置 → 直接加入
  flagged = dish.allergens ∩ user.allergens
  if (flagged.isEmpty()) return add(dish)
  showDialog({
    title: "Allergen alert",
    body:  `This dish contains an allergen you flagged: [${flagged.join(', ')}]. Add anyway?`,
    confirm: "Add anyway",  cancel: "Cancel"
  })
  // 用户确认 → add(dish)；取消 → 不加入
```

**关键约束（PRD 明确）**：**不是过滤掉含过敏原的菜品**。含过敏原的菜品照常出现在菜单中、照常可搜索、照常可加入（经确认）。`AllergenMatchTest` 用测试固化这一点。

### 4.4 每单数量上限（§4.2，5 份）

- 口径：**总份数** `totalQuantity = Σ line.quantity`，不是品种数。PRD 示例：3 份宫保鸡丁 + 3 份麻婆豆腐 = 6 份 → 超限。
- 达到 5 后：
  - 菜单页 `Add to Cart` 按钮 → 禁用，附英文原因 `You can order at most 5 items per order.`
  - 详情页 `Add to Cart` → 同上
  - 购物车行内 `+` 按钮 → 禁用，hover/长按展示同一原因
  - `−` 按钮与 `Remove` **保持可用**（否则用户无法脱困）
- 服务端在下单事务内再次强制（`ORDER_SIZE_EXCEEDED`，400），前端校验只是体验优化。

### 4.5 数量调整、移除、实时总价

- `+` / `−` / 直接输入数量（输入框限制 1..5 且不超过剩余可加份额）。
- `−` 到 0 时自动移除该行（或提供独立 `Remove`；两者都提供）。
- 总价实时计算并展示：

```
subtotalCents(line) = unitPriceCents × quantity
cartTotalCents      = Σ subtotalCents(line)
```

全程整数分，格式化统一 `formatCny(cents) → "¥22.50"`。

### 4.6 早高峰防抖（§3.3"大量员工频繁点击加入购物车"）

| 措施 | 说明 |
| --- | --- |
| 单飞锁 | `usePendingAction` 保证同一按钮 400ms 内只接受一次点击，期间显示 loading 态 |
| 即时反馈 | 点击后立即更新数量角标 + toast `Added to cart.`，不等待任何网络请求（纯本地操作） |
| 无网络依赖 | 加入购物车不发请求 → 高峰期后端零压力，这是本设计的关键收益 |
| 购物车持久化 | 写入 localStorage 做节流（≥200ms 合并），避免高频序列化卡顿 |

### 4.7 菜单日期切换时的购物车处理

- 购物车行携带 `menuDate`。若用户在菜单页切到另一个日期且购物车非空 → 弹英文确认：
  `Switching the menu date will clear your cart. Continue?` → `Switch and clear` / `Cancel`。
- 理由：菜品只在其所属日期的菜单上可下单，混装不同日期会导致结算语义不清。

---

## 5. §3.4 下单

### 5.1 结算页字段

| 字段 | 规则 |
| --- | --- |
| 订单明细 | 菜品名（+图片）、已选配置文本（如 `Whole Wheat Bun + Mustard`）、数量、**含加料费用的小计**、总价 |
| 配送日期 | 默认当天；范围 `[今天, 今天+7]`，**仅列出已发布菜单的日期** |
| 用餐时段 | `Lunch` / `Dinner`；已截单的选项禁用并给出英文原因 |
| 配送地址 | 从历史地址下拉选择，或输入新地址（≤200 字符）；新地址下单成功后写入历史 |
| 订单备注 | 选填，≤200 字符 |
| 预算提示 | 购物车总价 > 用户预算上限时显示**非阻断**英文提示条（见 §7.5） |

### 5.2 下单前预览与校验

`POST /api/orders/checkout-preview`（携带购物车明细 + 期望日期/时段），返回：

```json
{
  "bookableDates": ["2026-03-24", "2026-03-25"],
  "resolved": { "mealDate": "2026-03-24", "mealSlot": "Dinner", "adjusted": true,
                "reasonCode": "LUNCH_CUTOFF_PASSED" },
  "pricedItems": [ { "lineKey": "...", "unitPrice": "44.50", "subtotal": "89.00" } ],
  "total": "89.00",
  "unavailableItems": [ { "lineKey": "...", "reason": "DISH_NOT_ON_MENU" } ],
  "existingOrder": { "id": 1024, "orderNo": "WB20260324-000123", "status": "Pending" },
  "budget": { "min": "15.00", "max": "30.00", "exceedsMax": true }
}
```

用途：进入结算页即完成餐次解析与可用性校验，把"提交后才发现问题"提前到"进入页面就发现"。

### 5.3 餐次与截单规则求解（§4.2）

纯函数 `MealSlotResolver.resolve(now, wantedDate, wantedMeal)`，业务时区 `Asia/Shanghai`，`Clock` 注入。

```
cutoff(date, LUNCH)  = date 10:00
cutoff(date, DINNER) = date 15:00

1. wantedDate < today           → 400 VALIDATION_FAILED
2. wantedDate > today + 7       → 400 VALIDATION_FAILED
3. now < cutoff(wantedDate, wantedMeal)
      → (wantedDate, wantedMeal, adjusted = false)
4. 否则（已截单，自动切换到时间上最近的可订餐次）
   a. wantedMeal == LUNCH 且 now < cutoff(wantedDate, DINNER)
        → (wantedDate, DINNER, adjusted = true, reasonCode = LUNCH_CUTOFF_PASSED)
   b. 其它（含 wantedMeal == DINNER 且晚餐已截单）
        → (wantedDate + 1, LUNCH, adjusted = true, reasonCode = DINNER_CUTOFF_PASSED)
```

**决策表（`wantedDate` = 今天时）**

| 当前时间 | 期望时段 | 解析结果 | adjusted | reasonCode |
| --- | --- | --- | --- | --- |
| 09:00 | Lunch | 今日 Lunch | false | — |
| 09:00 | Dinner | 今日 Dinner | false | — |
| **10:30** | **Lunch** | **今日 Dinner** | **true** | **LUNCH_CUTOFF_PASSED** ← PRD 原例 |
| 10:30 | Dinner | 今日 Dinner | false | — |
| 15:30 | Lunch | 次日 Lunch | true | DINNER_CUTOFF_PASSED |
| 15:30 | Dinner | 次日 Lunch | true | DINNER_CUTOFF_PASSED |

**边界（必须由单测固化）**：`09:59:59` 午餐可订 / `10:00:00` 午餐已截单；`14:59:59` 晚餐可订 / `15:00:00` 晚餐已截单；分支 4b 的次日午餐截止时间恒在未来，故算法**不会递归、永不返回空**。

`wantedDate` 为未来日期时，其截止时间必在未来（分支 3），不触发切换。

**前端呈现**：`adjusted = true` 时，结算页顶部显示英文提示条（见 §11 `checkout.slotAdjusted.*`），并把用餐时段控件切到解析结果。

**自动切换引发的跨日期可用性**：切换后解析日期可能与购物车行的 `menuDate` 不同。服务端在 preview 与提交两处都以**解析后的日期**重新校验菜品可用性；不可用项在 `unavailableItems` 中列出，前端提示
`Some dishes are not available for {date} {mealSlot}: {list}.` 并要求调整。

### 5.4 提交订单：`POST /api/orders`

请求头 **`Idempotency-Key`**（客户端 UUID，**在结算页挂载时生成一次，重试时复用**，不是每次点击新生成）。请求体：明细 + 配送日期 + 用餐时段 + 配送地址 + 备注。

**服务端事务流程**（详见 `ARCHITECTURE.md` §4.4）：

```
①  校验 Idempotency-Key 存在               → 400 IDEMPOTENCY_KEY_REQUIRED
②  Redis SET NX webox:idem:order:{userId}:{key} TTL 24h
      已存在 → 返回既有订单 200（duplicated = true）
③  明细校验：菜品存在 / 在当日菜单 / 已上架  → 404 DISH_NOT_FOUND · 409 DISH_UNAVAILABLE · 400 DISH_NOT_ON_MENU
④  选项合法性：必选组齐全 / SINGLE 恰好一项 / 选项属于该菜品且未下架 → 400 OPTION_INVALID
⑤  服务端重新计价（BigDecimal, HALF_UP, scale 2）——忽略客户端提交的任何价格
⑥  总份数 ≤ 5                             → 400 ORDER_SIZE_EXCEEDED
⑦  MealSlotResolver 解析餐次（可能自动切换）
⑧  预检同餐次是否已有有效订单              → 409 ORDER_SLOT_CONFLICT（携带 existingOrderId）
⑨  生成订单号（Redis INCR，降级为随机后缀，UNIQUE 冲突重试 ≤ 3 次）
⑩  写入 orders / order_item / order_item_option / order_status_log
      并发兜底：UNIQUE(user_id, client_request_id) 与 UNIQUE(user_id, active_slot_key)
      捕获冲突 → 回查 → 幂等冲突返回 200 既有订单；餐次冲突返回 409 既有订单 ID
```

### 5.5 "每次请求只会创建一个订单"的三层防护（§3.4）

| 层 | 机制 | 作用 |
| --- | --- | --- |
| 前端 | 提交按钮进入 `submitting` 态即禁用；`Idempotency-Key` 页面挂载时生成一次并复用 | 拦截绝大多数重复点击 |
| 服务端快速路径 | Redis `SET NX` 幂等键（24h TTL） | 拦截跨请求、跨标签页的重复提交 |
| 数据库权威 | `UNIQUE(user_id, client_request_id)` | **最终保证**：并发/Redis 故障下依然只可能有一条订单 |

> 这是后端 P6+「下单幂等」4 分的直接落点，并被集成测试固化（并发 10 次提交 → 库中仅 1 条）。

### 5.6 不可重复下单（§4.2）

- 同一 `(userId, mealDate, mealSlot)` 只允许一个 `Pending` 或 `Confirmed` 订单。
- `UNIQUE(user_id, active_slot_key)` 生成列方案见 `ARCHITECTURE.md` §5.3——订单取消/完成后生成列变 NULL，自动释放餐次。
- **前端交互**：`checkout-preview` 返回 `existingOrder` 非空时，结算页主按钮由 `Place Order` 变为 **`View Existing Order`**，点击跳转 `/orders/{id}`（PRD 原文要求）。
- 服务端仍做强校验（用户可能绕过前端），冲突返回 409 `ORDER_SLOT_CONFLICT` + `existingOrderId`。

### 5.7 订单成功页

展示（PRD §3.4 要求"订单编号和预计配送信息"）：

| 元素 | 内容 |
| --- | --- |
| 标题 | `Order placed successfully` |
| 订单编号 | `WB20260324-000123`（可一键复制） |
| 配送日期 | `March 24, 2026` |
| 用餐时段 | `Lunch` / `Dinner` |
| 配送地址 | 完整地址文本 |
| 订单总额 | `¥89.00` |
| 操作 | `View my orders` · `Back to menu` |

### 5.8 我的订单与订单详情

| 页面 | 内容 |
| --- | --- |
| 订单列表 `/orders` | 分页；每行：订单号、下单时间、配送日期、用餐时段、状态徽标、总额；空态 `You have no orders yet.` |
| 订单详情 `/orders/{id}` | 每个菜品：图片、名称、**已选配置**、数量、**小计（含加料费）**；合计；配送信息；订单备注；状态时间线（来自 `order_status_log`） |
| 归属校验 | 非本人订单 → 403 `FORBIDDEN`（明确语义优先于隐藏存在性） |

**订单快照**：`order_item` / `order_item_option` 存的是**下单时刻**的名称与价格。管理员之后改名、调价、删除菜品，历史订单展示与金额**完全不变**。

### 5.9 取消订单

```
POST /api/orders/{id}/cancel
  ├─ 订单不属于当前用户        → 403 FORBIDDEN
  ├─ 状态 == Pending           → 置 Cancelled，写 cancelled_at，记 order_status_log → 200
  ├─ 状态 == Cancelled         → 200 幂等返回当前状态（不报错）
  └─ 状态 ∈ {Confirmed, Completed} → 409 ORDER_NOT_CANCELLABLE
```

前端：仅当状态为 `Pending` 时显示 `Cancel order`；点击弹英文确认框。取消后该餐次立即可重新下单（生成列变 NULL 已生效）。

### 5.10 订单状态机

```
                 ┌──────────── admin: confirm ────────────┐
   create ──▶ Pending ──────────────────────────────▶ Confirmed ──▶ Completed
                 │                                        │          (admin: complete)
                 └── owner/admin: cancel ──▶ Cancelled ◀───┘
                                            （仅 Pending 可取消；Cancelled / Completed 为终态）
```

Tier 1 + Tier 2 范围内 `Pending → Confirmed → Completed` 的流转**不是必需功能**（PRD §3.4 只要求"仅 Pending 可取消"）。为对齐 PRD §1"管理员处理订单"的表述，并提供 §5.3 看板所需的"已完成/待处理/已取消"口径，本方案**附带**最小管理端订单能力（见 §9"补充项"），可裁剪。

---

## 6. §4.1 饮食偏好设置

### 6.1 数据与接口

| 偏好 | 存储 | 驱动的行为 |
| --- | --- | --- |
| 过敏原（多选，7 值全集） | `user_allergen` | **加购时弹窗提醒**；从不出现在推荐中（Tier 3） |
| 偏好菜系（多选） | `user_preferred_category` | `Recommend for me` 开启时的排序与高亮 |
| 辣度偏好（单选 `None/Mild/Medium/Hot`） | `user_preference.spice_preference` | `Recommend for me` 开启时的排序与高亮 |
| 口味浓淡（单选 `Light/Balanced/Rich`） | `user_preference.taste_preference` | **仅落库**，作为 Tier 3 AI 推荐输入，不驱动菜单排序（PRD 明确） |
| 单餐预算（区间） | `user_preference.budget_min/max` | 结算页超出上限时**非阻断**提示 |

接口：`GET /api/users/me/preferences` · `PUT /api/users/me/preferences`（整体替换，幂等）。

### 6.2 过敏原

- UI 文案明确其语义：`We'll warn you when you add a dish containing these allergens. Dishes are never hidden.`
- 交集判定在**前端加购时**与**后端下单校验**两处执行：
  - 前端：弹窗提醒，用户可继续（PRD 明确"由员工自行决定是否继续"）。
  - 后端：**不阻断下单**。仅在订单响应中回传 `allergenWarnings: [Allergen]` 供前端在订单详情展示为提醒徽标。
  - 注意：后端不阻断是刻意的——PRD 说"提醒"，不是"禁止"。

### 6.3 菜系偏好与"为我推荐"开关

见 §3.8。开关为前端视图状态，偏好数据为服务端数据，两者解耦：即使关闭开关，偏好数据依然保存。

### 6.4 辣度偏好

- 排序/高亮权重低于菜系偏好（见 §3.8 排序键顺序）。
- 命中时卡片显示 `Matches your spice level` 标签，**不做过滤**（PRD 只说排序/高亮）。

### 6.5 单餐预算

- 校验：`budget_min ≤ budget_max`，否则 400 `VALIDATION_FAILED` + 英文 `Minimum budget cannot be greater than maximum budget.`
- 范围：`0.00 ~ 9999.99`。
- 结算页：`cartTotal > budget_max` 时显示非阻断提示条
  `Your cart total (¥89.00) is above your per-meal budget (¥30.00). You can still place the order.`
- **绝不阻止下单**（PRD 明确）。

---

## 7. §4.2 订餐规则

三条规则及落点汇总：

| 规则 | 前端落点 | 服务端落点 | 并发正确性 |
| --- | --- | --- | --- |
| 每单 ≤ 5 份 | §4.4 按钮禁用 + 原因 | §5.4 步骤 ⑥ `ORDER_SIZE_EXCEEDED` | 单请求内校验，无需并发控制 |
| 截单时间与自动切换 | §5.3 提示条 + 时段控件 | §5.3 `MealSlotResolver` | 纯函数，`Clock` 注入 |
| 同日同餐次唯一有效订单 | §5.6 `View Existing Order` | §5.4 步骤 ⑧ + `UNIQUE(user_id, active_slot_key)` | **数据库唯一索引权威** |

---

## 8. §4.3 Console 后台管理模块（菜品管理）

### 8.1 布局隔离

| | 员工端 | 管理端 |
| --- | --- | --- |
| 路由前缀 | `/` | `/console/*` |
| 布局 | ≥`md` 顶部导航；<`md` 底部 Tab | 左侧栏 + 顶部标题 **`Console`** |
| 导航项 | Menu · Cart · My Orders · Settings | Dishes · Daily Menu（+ 补充项：Orders） |
| 守卫 | `meta.requiresAuth` | `meta.requiresAdmin` → 未授权跳 `/403` |

PRD §4.3 明确"与员工端使用不同的导航布局"——两者是**独立的 layout 组件**，不是同一布局换配色。

### 8.2 菜品管理页

**表格列**：缩略图 · 名称 · 分类 · 价格 · 辣度 · 过敏原 · 蛋白质 · 状态 · 操作（`Edit` / `Set available` / `Set unavailable`）
**筛选**：关键词搜索（名称，≤50 字符，300ms 防抖）+ 分类下拉 + 状态下拉（`All` / `Available` / `Unavailable`）
**分页**：默认 10 条 / 页，上限 50

### 8.3 新增 / 编辑菜品

| 字段 | 控件 | 校验 |
| --- | --- | --- |
| Name | 文本 | 必填，≤100 |
| Description | 多行文本 | 必填，≤500 |
| Price | 数字（两位小数） | 必填，> 0，≤ 99999.99，两位小数 |
| Category | 下拉（6 值） | 必填 |
| Allergens | 多选 chips（7 值全集） | 可空 |
| Protein | 多选 chips（8 值） | 可空 |
| Spice level | 下拉（4 值） | 必填 |
| Image | 上传 + 预览 | ≤2MB，jpg/png/webp，magic bytes 校验 |
| Option groups | 嵌套编辑器 | 组名 ≤60；`Required` 开关；`SINGLE`/`MULTI`；选项名 ≤60；加价 ≥0 两位小数 |

**选项组约束**：
- `SINGLE` 组在运行时**恰好选一项**；`MULTI` 组可选 0..N 项。
- 必选组必须至少含 1 个选项（保存时校验 → 英文 `A required group must have at least one option.`）。
- 编辑保存时**在事务内整体替换**该菜品的选项组集合。若被替换掉的选项已被历史订单引用——**无影响**，因为订单存快照（§5.8）。
- 选项支持 `is_available` 软下架；已下架选项不出现在员工端，但历史订单仍正常展示。

### 8.4 上架 / 下架

- `PATCH /api/admin/dishes/{id}/availability`，body `{ "available": false }`。
- 下架后：员工端菜单快照立即失效并重载 → 该菜品消失（§3.1 可见性判定）。
- 已在购物车中的已下架菜品：结算 preview 时进入 `unavailableItems`，提示用户移除。

### 8.5 设置每日菜单

| 项 | 说明 |
| --- | --- |
| 日期 | 日期选择器，**默认次日**（PRD §4.3）；也可选当日用于初始化/调整当日菜单（PRD 数据初始化说明要求） |
| 选菜 | 搜索 + 勾选；仅为已上架菜品提供排期（已下架菜品置灰并附英文说明，允许先排期后上架时给出警告） |
| 供应数量 | 每个已选菜品一个数量输入（`Daily quantity`，≥0 整数）。**属 Tier 2 要求**；Tier 3 的扣减与实时展示直接复用该字段 |
| 保存 | `PUT /api/admin/daily-menus/{date}`，body 为完整菜品列表 → **整体替换**（幂等：重复提交结果一致） |
| 缓存 | 保存成功后按日期失效菜单快照 |
| 回显 | 再次打开同一日期，准确回显已排期菜品与数量 |

### 8.6 图片上传

- `POST /api/admin/files/images`，`multipart/form-data`。
- 校验：大小 ≤2MB；扩展名 ∈ {jpg, jpeg, png, webp}；`Content-Type` 合法；**magic bytes** 匹配（防伪装文件）。
- 处理：UUID 重命名，丢弃原始文件名，落盘 `${WEBOX_UPLOAD_DIR:./data/uploads}`。
- 读取：`GET /api/files/images/{name}`，文件名必须匹配 `^[a-f0-9-]{36}\.(jpg|jpeg|png|webp)$`，杜绝路径穿越。
- 失败文案：`Image must be a JPG, PNG, or WebP file under 2 MB.`

---

## 9. 种子数据方案（对应 PRD §4.3 数据初始化说明）

由 `DataSeeder`（`ApplicationRunner`）在应用启动时执行，**幂等**：重复启动不产生重复数据、不覆盖管理员已做的修改。

| 步骤 | 内容 | 幂等判据 |
| --- | --- | --- |
| 1 | 管理员账号 `admin@webox.com`（密码 BCrypt 运行时生成，README 公示；满足 ≥8 位含字母与数字） | 按邮箱不存在才创建 |
| 2 | 菜品库：PRD §6 的 **9 道菜**（含完整选项组）+ **补充约 41 道英文菜品**，合计约 50 条（PRD §6 原文允许"自行补充"），用于演示分页与列表性能 | `dish` 表为空才执行 |
| 3 | 图片绑定：20 张图从 `webox_product_images/product_images/` 复制为 `backend/src/main/resources/static/dishes/dish-01.jpg … dish-20.jpg`，按索引绑定到菜品 | 文件不存在才复制 |
| 4 | **当日菜单**：若今天尚无 `daily_menu`，则为今天生成菜单，覆盖全部已上架菜品，每项 `total_qty = 50`、`sold_qty = 0` | 按 `menu_date = 今天` 不存在才创建 |
| 5 | 次日菜单：同样为明天预置一份，便于演示"管理员设置次日菜单"与日期选择器 | 按 `menu_date = 明天` 不存在才创建 |

**为什么当日菜单用 Java 播种而非 Flyway SQL**：`menu_date` 必须等于**运行当天**，静态 SQL 无法表达"今天"；且第 2 步生成约 50 道菜及其选项组，Java 代码远比数百行 SQL 可维护。

**演示提示（写入 README）**：种子数据保证评分当天打开系统即可直接下单、切换菜单日期、并在 Console 看到完整菜品表格，无需任何手工准备。

---

## 10. 校验规则与精度

### 10.1 字段级校验规则表

| 字段 | 上限 | 格式 / 规则 | 服务端 | 前端 | 英文提示 |
| --- | --- | --- | --- | --- | --- |
| 邮箱 | 200 | RFC 邮箱格式 | `@Email @Size(max=200)` | 同规则 + `input[type=email]` | `Please enter a valid email address.` |
| 密码 | 8–64 | ≥8 位且同时含字母与数字，无空白 | `@Pattern` | 同规则 | `Password must be at least 8 characters and include both letters and numbers.` |
| 显示名 | 50 | 非空 | `@NotBlank @Size(max=50)` | 同规则 | `Display name is required.` |
| 搜索关键词 | 50 | 可为空 | `@Size(max=50)` | `maxlength=50` | `Search keywords must be 50 characters or fewer.` |
| 配送地址 | 200 | 非空 | `@NotBlank @Size(max=200)` | `maxlength=200` | `Delivery address is required and must be 200 characters or fewer.` |
| 订单备注 | 200 | 可空 | `@Size(max=200)` | `maxlength=200` | `Notes must be 200 characters or fewer.` |
| 菜品名 | 100 | 非空 | `@NotBlank @Size(max=100)` | 同规则 | `Dish name is required and must be 100 characters or fewer.` |
| 菜品描述 | 500 | 非空 | `@NotBlank @Size(max=500)` | 同规则 | `Description is required and must be 500 characters or fewer.` |
| 菜品价格 | — | > 0，最多两位小数 | `@DecimalMin @Digits` | 同规则 | `Price must be greater than 0.` |
| 选项组名 / 选项名 | 60 | 非空 | `@NotBlank @Size(max=60)` | 同规则 | `Option name must be 60 characters or fewer.` |
| 选项加价 | — | ≥ 0，最多两位小数 | `@DecimalMin("0") @Digits` | 同规则 | `Extra price cannot be negative.` |
| 每日供应数量 | — | 整数 ≥ 0 | `@Min(0)` | 同规则 | `Daily quantity must be zero or greater.` |
| 预算区间 | — | 0–9999.99，min ≤ max | `@DecimalMin @Digits` + 跨字段校验 | 同规则 | `Minimum budget cannot be greater than maximum budget.` |
| 分页 size | 50 | 1–50 | `@Max(50)` | — | — |

### 10.2 金额精度方案（PRD §6 明确要求避免浮点问题）

| 层 | 表示 | 规则 |
| --- | --- | --- |
| MySQL | `DECIMAL(10,2)` | 定点十进制，无二进制浮点误差 |
| Java | `BigDecimal` | 统一 `setScale(2, RoundingMode.HALF_UP)`；运算过程中不出现 `double`/`float` |
| JSON | 十进制字面量 | `WRITE_BIGDECIMAL_AS_PLAIN`，输出 `22.50` 而非科学计数法 |
| TypeScript | **整数分** | 边界处解析 `"22.50" → 2250`；所有加减乘在整数域；格式化 `formatCny(2250) → "¥22.50"` |
| 展示 | — | 统一 `¥` + 两位小数（PRD §6：货币符号保留 `¥`） |

**验证用例**：`¥22.00` + 加料 `¥5.00` + `¥3.00` × 2 份，与 `¥0.10` 级加料多次累加，结果须与手算精确相等（集成测试固化）。

---

## 11. 全链路英文文案表（实现时照抄）

> PRD §1 强制要求：所有面向用户的内容一律英文。本表为**唯一文案来源**，代码中不得出现未登记的用户可见字符串，也不得出现中文文案。

### 11.1 认证

| Key | 文案 |
| --- | --- |
| `auth.signIn.title` | `Sign in to WeBox` |
| `auth.signUp.title` | `Create your WeBox account` |
| `auth.field.email` | `Work email` |
| `auth.field.emailPlaceholder` | `you@company.com` |
| `auth.field.password` | `Password` |
| `auth.field.passwordPlaceholder` | `At least 8 characters, with letters and numbers` |
| `auth.field.displayName` | `Display name` |
| `auth.field.displayNamePlaceholder` | `e.g. Alex Chen` |
| `auth.action.signIn` | `Sign in` |
| `auth.action.signUp` | `Create account` |
| `auth.action.signOut` | `Sign out` |
| `auth.error.emailRegistered` | `This email is already registered.` |
| `auth.error.invalidCredentials` | `Incorrect email or password.` |
| `auth.error.invalidEmail` | `Please enter a valid email address.` |
| `auth.error.passwordPolicy` | `Password must be at least 8 characters and include both letters and numbers.` |
| `auth.error.displayNameRequired` | `Display name is required.` |

### 11.2 菜单与菜品

| Key | 文案 |
| --- | --- |
| `menu.title` | `Today's Menu` |
| `menu.titleWithDate` | `Menu for {date}` |
| `menu.search.placeholder` | `Search dishes by name or description` |
| `menu.filter.cuisine` | `Cuisine` |
| `menu.filter.clear` | `Clear filters` |
| `menu.toggle.recommend` | `Recommend for me` |
| `menu.empty.search` | `No dishes match your search.` |
| `menu.empty.noMenu` | `No menu has been published for this date.` |
| `menu.action.addToCart` | `Add to Cart` |
| `menu.action.viewDetail` | `View details` |
| `menu.toast.added` | `Added to cart.` |
| `menu.tag.recommendedCuisine` | `Preferred cuisine` |
| `menu.tag.recommendedSpice` | `Matches your spice level` |
| `menu.label.protein` | `Protein` |
| `menu.label.allergens` | `Allergens` |
| `menu.label.spiceLevel` | `Spice level` |
| `menu.detail.description` | `Description` |
| `menu.detail.ingredients` | `Ingredients` |
| `menu.detail.customization` | `Customization` |
| `menu.group.required` | `Required` |
| `menu.group.optional` | `Optional` |
| `menu.error.requiredGroup` | `Please choose an option for "{groupName}".` |
| `menu.error.imageFailed` | `Image unavailable` |

### 11.3 购物车

| Key | 文案 |
| --- | --- |
| `cart.title` | `Your Cart` |
| `cart.empty` | `Your cart is empty.` |
| `cart.column.subtotal` | `Subtotal` |
| `cart.label.total` | `Total` |
| `cart.label.quantity` | `Quantity` |
| `cart.action.remove` | `Remove` |
| `cart.action.checkout` | `Checkout` |
| `cart.action.continueShopping` | `Continue shopping` |
| `cart.error.maxItems` | `You can order at most 5 items per order.` |
| `cart.confirm.switchMenuDate` | `Switching the menu date will clear your cart. Continue?` |
| `cart.action.switchAndClear` | `Switch and clear` |

### 11.4 过敏原提醒

| Key | 文案 |
| --- | --- |
| `allergen.dialog.title` | `Allergen alert` |
| `allergen.dialog.body` | `This dish contains an allergen you flagged: [{allergens}]. Add anyway?` |
| `allergen.dialog.confirm` | `Add anyway` |
| `allergen.dialog.cancel` | `Cancel` |

### 11.5 结算与下单

| Key | 文案 |
| --- | --- |
| `checkout.title` | `Checkout` |
| `checkout.section.summary` | `Order summary` |
| `checkout.field.deliveryDate` | `Delivery date` |
| `checkout.field.mealSlot` | `Meal` |
| `checkout.field.address` | `Delivery address` |
| `checkout.field.selectSavedAddress` | `Select a saved address` |
| `checkout.field.newAddress` | `Or enter a new address` |
| `checkout.field.note` | `Order note (optional)` |
| `checkout.action.placeOrder` | `Place Order` |
| `checkout.action.viewExistingOrder` | `View Existing Order` |
| `checkout.notice.slotAdjustedToDinner` | `Lunch orders must be placed before 10:00. Your order will be for Dinner instead.` |
| `checkout.notice.slotAdjustedToNextLunch` | `Dinner orders must be placed before 15:00. Your order will be for Lunch on the next day instead.` |
| `checkout.notice.budgetExceeded` | `Your cart total ({total}) is above your per-meal budget ({budget}). You can still place the order.` |
| `checkout.error.unavailableItems` | `Some dishes are not available for {date} {mealSlot}: {list}. Please adjust your cart.` |
| `checkout.error.maxItems` | `Your order exceeds the 5-item limit. Please remove some items.` |
| `checkout.error.optionInvalid` | `Your selection for "{groupName}" is no longer valid. Please choose again.` |
| `checkout.error.slotConflict` | `You already have an active order for this meal. Please view it instead.` |
| `checkout.error.mealSlotClosed` | `This meal is closed for ordering.` |

### 11.6 订单

| Key | 文案 |
| --- | --- |
| `order.success.title` | `Order placed successfully` |
| `order.label.orderNo` | `Order number` |
| `order.label.deliveryDate` | `Delivery date` |
| `order.label.mealSlot` | `Meal` |
| `order.label.address` | `Delivery address` |
| `order.label.total` | `Total` |
| `order.action.viewMyOrders` | `View my orders` |
| `order.action.backToMenu` | `Back to menu` |
| `order.list.title` | `My Orders` |
| `order.list.empty` | `You have no orders yet.` |
| `order.detail.title` | `Order details` |
| `order.detail.timeline` | `Status history` |
| `order.action.cancel` | `Cancel order` |
| `order.confirm.cancel.title` | `Cancel this order?` |
| `order.confirm.cancel.body` | `This will cancel order {orderNo}. This cannot be undone.` |
| `order.confirm.cancel.confirm` | `Cancel order` |
| `order.confirm.cancel.dismiss` | `Keep order` |
| `order.error.notCancellable` | `Only pending orders can be cancelled.` |
| `order.status.Pending` | `Pending` |
| `order.status.Confirmed` | `Confirmed` |
| `order.status.Completed` | `Completed` |
| `order.status.Cancelled` | `Cancelled` |

### 11.7 偏好设置

| Key | 文案 |
| --- | --- |
| `prefs.title` | `Dietary preferences` |
| `prefs.section.allergens` | `Allergens` |
| `prefs.section.cuisines` | `Preferred cuisines` |
| `prefs.section.spice` | `Spice preference` |
| `prefs.section.taste` | `Taste preference` |
| `prefs.section.budget` | `Per-meal budget` |
| `prefs.hint.allergens` | `We'll warn you when you add a dish containing these allergens. Dishes are never hidden.` |
| `prefs.hint.cuisines` | `Used to sort and highlight dishes when "Recommend for me" is on.` |
| `prefs.hint.taste` | `Used for personalized recommendations.` |
| `prefs.field.budgetMin` | `Minimum` |
| `prefs.field.budgetMax` | `Maximum` |
| `prefs.action.save` | `Save preferences` |
| `prefs.toast.saved` | `Preferences saved.` |
| `prefs.error.budgetRange` | `Minimum budget cannot be greater than maximum budget.` |

### 11.8 Console 管理端

| Key | 文案 |
| --- | --- |
| `console.brand` | `Console` |
| `console.nav.dishes` | `Dishes` |
| `console.nav.dailyMenu` | `Daily Menu` |
| `console.dishes.title` | `All dishes` |
| `console.dishes.action.add` | `Add dish` |
| `console.dishes.action.edit` | `Edit` |
| `console.dishes.action.setAvailable` | `Set available` |
| `console.dishes.action.setUnavailable` | `Set unavailable` |
| `console.dishes.search.placeholder` | `Search dishes` |
| `console.dishes.filter.category` | `Category` |
| `console.dishes.filter.allCategories` | `All categories` |
| `console.dishes.filter.status` | `Status` |
| `console.dishes.filter.allStatuses` | `All` |
| `console.dishes.status.available` | `Available` |
| `console.dishes.status.unavailable` | `Unavailable` |
| `console.dish.form.createTitle` | `Add dish` |
| `console.dish.form.editTitle` | `Edit dish` |
| `console.dish.field.name` | `Name` |
| `console.dish.field.description` | `Description` |
| `console.dish.field.price` | `Price` |
| `console.dish.field.category` | `Category` |
| `console.dish.field.allergens` | `Allergens` |
| `console.dish.field.protein` | `Protein` |
| `console.dish.field.spiceLevel` | `Spice level` |
| `console.dish.field.image` | `Image` |
| `console.dish.action.uploadImage` | `Upload image` |
| `console.dish.section.optionGroups` | `Option groups` |
| `console.dish.action.addOptionGroup` | `Add option group` |
| `console.dish.field.groupName` | `Group name` |
| `console.dish.field.required` | `Required` |
| `console.dish.field.selectionType` | `Selection type` |
| `console.dish.selection.single` | `Single choice` |
| `console.dish.selection.multi` | `Multiple choice` |
| `console.dish.action.addOption` | `Add option` |
| `console.dish.field.optionName` | `Option name` |
| `console.dish.field.extraPrice` | `Extra price` |
| `console.dish.action.remove` | `Remove` |
| `console.dish.action.save` | `Save` |
| `console.dish.action.cancel` | `Cancel` |
| `console.dish.error.price` | `Price must be greater than 0.` |
| `console.dish.error.requiredGroupEmpty` | `A required group must have at least one option.` |
| `console.dish.error.upload` | `Image must be a JPG, PNG, or WebP file under 2 MB.` |
| `console.dailyMenu.title` | `Daily Menu` |
| `console.dailyMenu.field.date` | `Date` |
| `console.dailyMenu.action.selectDishes` | `Select dishes for this date` |
| `console.dailyMenu.field.dailyQuantity` | `Daily quantity` |
| `console.dailyMenu.action.save` | `Save daily menu` |
| `console.dailyMenu.toast.saved` | `Daily menu saved.` |
| `console.dailyMenu.empty` | `No dishes selected for this date.` |
| `console.dailyMenu.warning.unavailable` | `This dish is currently unavailable and will not be shown to employees.` |

### 11.9 通用

| Key | 文案 |
| --- | --- |
| `common.loading` | `Loading...` |
| `common.retry` | `Try again` |
| `common.error.generic` | `Something went wrong. Please try again.` |
| `common.error.forbidden` | `You don't have permission to access this page.` |
| `common.error.notFound` | `Page not found.` |
| `common.action.backHome` | `Back to home` |
| `common.label.required` | `Required` |
| `common.date.today` | `Today` |
| `common.date.tomorrow` | `Tomorrow` |

---

## 12. 补充项（PRD §1 提及但 §4.3 未细化，可裁剪）

PRD §1 描述管理员职责为"管理菜品、**处理订单**、了解平台经营情况"，但 §4.3 在 Tier 2 只细化了菜品管理。以下为对齐 §1 表述的**最小补充**，实现优先级最低，可在时间不足时整块裁剪而不影响 Tier 1 + Tier 2 完成度：

| 补充 | 接口 | 说明 |
| --- | --- | --- |
| 管理端订单列表 | `GET /api/admin/orders?status=&date=&page=&size=` | 按状态/日期筛选，供处理订单 |
| 订单状态流转 | `PATCH /api/admin/orders/{id}/status` | 允许 `Pending → Confirmed`、`Confirmed → Completed`；写 `order_status_log` |
| 取消（管理端） | 复用 `POST /api/orders/{id}/cancel` | 仍遵守"仅 Pending 可取消"（PRD §3.4） |

**明确裁剪**：Tier 3 的库存实时展示与扣减、AI 智能推荐、经营数据看板，本方案不实现，仅预留扩展点（见 `ARCHITECTURE.md` §11）。

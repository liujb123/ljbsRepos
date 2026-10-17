-- ============================================================================
-- WeBox schema  (MySQL 8.0)
--
-- Design notes
--   * Money is DECIMAL(10,2) everywhere - never a floating point type.
--   * Audit timestamps are stored as UTC DATETIME(3).
--   * Business dates (meal_date, menu_date) are DATE in the business time zone.
--   * Concurrency correctness is owned by UNIQUE constraints, not by application
--     code: uk_orders_user_client_request (idempotent submit) and
--     uk_orders_user_active_slot (one active order per meal).
--   * MySQL ENUM types are deliberately NOT used: values are VARCHAR + CHECK so
--     the value set can evolve without DDL on every change.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Users
-- ---------------------------------------------------------------------------
CREATE TABLE app_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(200) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(50)  NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    phone         VARCHAR(30)  NULL,
    created_at    DATETIME(3)  NOT NULL,
    updated_at    DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_email (email),
    CONSTRAINT ck_app_user_role CHECK (role IN ('EMPLOYEE', 'ADMIN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_preference (
    user_id          BIGINT      NOT NULL,
    spice_preference VARCHAR(20) NULL,
    taste_preference VARCHAR(20) NULL,
    budget_min       DECIMAL(10, 2) NULL,
    budget_max       DECIMAL(10, 2) NULL,
    updated_at       DATETIME(3) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_preference_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_preference_spice CHECK (spice_preference IS NULL OR spice_preference IN ('None', 'Mild', 'Medium', 'Hot')),
    CONSTRAINT ck_user_preference_taste CHECK (taste_preference IS NULL OR taste_preference IN ('Light', 'Balanced', 'Rich')),
    CONSTRAINT ck_user_preference_budget CHECK (budget_min IS NULL OR budget_max IS NULL OR budget_min <= budget_max)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_allergen (
    user_id  BIGINT      NOT NULL,
    allergen VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, allergen),
    CONSTRAINT fk_user_allergen_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_allergen_value CHECK (allergen IN ('Peanuts', 'Dairy', 'Egg', 'Gluten', 'Soy', 'Fish', 'Shellfish'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_preferred_category (
    user_id  BIGINT      NOT NULL,
    category VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, category),
    CONSTRAINT fk_user_preferred_category_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_preferred_category_value CHECK (category IN ('Chinese', 'Western', 'Japanese', 'Light Meal', 'Korean', 'Southeast Asian'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Delivery addresses remembered per user, offered as "saved addresses" at checkout.
CREATE TABLE user_address (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    address_text  VARCHAR(200) NOT NULL,
    use_count     INT          NOT NULL DEFAULT 1,
    last_used_at  DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_address_user_text (user_id, address_text),
    KEY idx_user_address_recent (user_id, last_used_at DESC),
    CONSTRAINT fk_user_address_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Dish master data
-- ---------------------------------------------------------------------------
CREATE TABLE dish (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    name         VARCHAR(100)   NOT NULL,
    description  VARCHAR(500)   NOT NULL,
    price        DECIMAL(10, 2) NOT NULL,
    category     VARCHAR(30)    NOT NULL,
    spice_level  VARCHAR(20)    NOT NULL,
    image_url    VARCHAR(300)   NULL,
    is_available TINYINT(1)     NOT NULL DEFAULT 1,
    version      INT            NOT NULL DEFAULT 0,
    created_at   DATETIME(3)    NOT NULL,
    updated_at   DATETIME(3)    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_dish_category_available (category, is_available),
    KEY idx_dish_name (name),
    CONSTRAINT ck_dish_category CHECK (category IN ('Chinese', 'Western', 'Japanese', 'Light Meal', 'Korean', 'Southeast Asian')),
    CONSTRAINT ck_dish_spice CHECK (spice_level IN ('None', 'Mild', 'Medium', 'Hot')),
    CONSTRAINT ck_dish_price CHECK (price >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE dish_protein (
    dish_id BIGINT      NOT NULL,
    protein VARCHAR(20) NOT NULL,
    PRIMARY KEY (dish_id, protein),
    CONSTRAINT fk_dish_protein_dish FOREIGN KEY (dish_id) REFERENCES dish (id) ON DELETE CASCADE,
    CONSTRAINT ck_dish_protein_value CHECK (protein IN ('Chicken', 'Beef', 'Pork', 'Fish', 'Shrimp', 'Egg', 'Tofu', 'None'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE dish_allergen (
    dish_id  BIGINT      NOT NULL,
    allergen VARCHAR(20) NOT NULL,
    PRIMARY KEY (dish_id, allergen),
    CONSTRAINT fk_dish_allergen_dish FOREIGN KEY (dish_id) REFERENCES dish (id) ON DELETE CASCADE,
    CONSTRAINT ck_dish_allergen_value CHECK (allergen IN ('Peanuts', 'Dairy', 'Egg', 'Gluten', 'Soy', 'Fish', 'Shellfish'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE dish_option_group (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    dish_id        BIGINT      NOT NULL,
    name           VARCHAR(60) NOT NULL,
    is_required    TINYINT(1)  NOT NULL DEFAULT 0,
    selection_type VARCHAR(10) NOT NULL,
    display_order  INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_dish_option_group_dish (dish_id, display_order),
    CONSTRAINT fk_dish_option_group_dish FOREIGN KEY (dish_id) REFERENCES dish (id) ON DELETE CASCADE,
    CONSTRAINT ck_dish_option_group_type CHECK (selection_type IN ('SINGLE', 'MULTI'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE dish_option (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    group_id      BIGINT         NOT NULL,
    name          VARCHAR(60)    NOT NULL,
    price_delta   DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    is_available  TINYINT(1)     NOT NULL DEFAULT 1,
    display_order INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_dish_option_group (group_id, display_order),
    CONSTRAINT fk_dish_option_group FOREIGN KEY (group_id) REFERENCES dish_option_group (id) ON DELETE CASCADE,
    CONSTRAINT ck_dish_option_price CHECK (price_delta >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Daily menu
--   total_qty / sold_qty belong to Tier 2 (admin sets the daily quantity) and are
--   reused directly by the Tier 3 stock deduction, so no schema change is needed.
-- ---------------------------------------------------------------------------
CREATE TABLE daily_menu (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    menu_date  DATE        NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_daily_menu_date (menu_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE daily_menu_item (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    daily_menu_id BIGINT NOT NULL,
    dish_id       BIGINT NOT NULL,
    total_qty     INT    NOT NULL DEFAULT 0,
    sold_qty      INT    NOT NULL DEFAULT 0,
    display_order INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_daily_menu_item (daily_menu_id, dish_id),
    KEY idx_daily_menu_item_dish (dish_id),
    CONSTRAINT fk_daily_menu_item_menu FOREIGN KEY (daily_menu_id) REFERENCES daily_menu (id) ON DELETE CASCADE,
    CONSTRAINT fk_daily_menu_item_dish FOREIGN KEY (dish_id) REFERENCES dish (id) ON DELETE CASCADE,
    CONSTRAINT ck_daily_menu_item_qty CHECK (total_qty >= 0 AND sold_qty >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Orders
--   active_slot_key: a STORED generated column that is NULL unless the order is
--   active (Pending / Confirmed). Combined with uk_orders_user_active_slot it gives
--   MySQL the equivalent of a partial unique index:
--       "at most one ACTIVE order per user per meal"
--   Cancelling or completing an order flips the column to NULL, which releases the
--   meal slot automatically - no cleanup code required.
--
--   uk_orders_user_client_request is the authoritative idempotency guarantee for
--   POST /api/orders: the same Idempotency-Key can never create two orders.
-- ---------------------------------------------------------------------------
CREATE TABLE orders (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    order_no            VARCHAR(32)    NOT NULL,
    user_id             BIGINT         NOT NULL,
    status              VARCHAR(20)    NOT NULL,
    meal_date           DATE           NOT NULL,
    meal_slot           VARCHAR(10)    NOT NULL,
    requested_meal_slot VARCHAR(10)    NOT NULL,
    slot_adjusted       TINYINT(1)     NOT NULL DEFAULT 0,
    delivery_address    VARCHAR(200)   NOT NULL,
    note                VARCHAR(200)   NULL,
    item_count          INT            NOT NULL,
    total_amount        DECIMAL(10, 2) NOT NULL,
    client_request_id   VARCHAR(64)    NOT NULL,
    active_slot_key     VARCHAR(32) GENERATED ALWAYS AS (
                            CASE
                                WHEN status IN ('Pending', 'Confirmed')
                                    THEN CONCAT(meal_date, '#', meal_slot)
                                ELSE NULL
                            END
                        ) STORED,
    created_at          DATETIME(3)    NOT NULL,
    updated_at          DATETIME(3)    NOT NULL,
    cancelled_at        DATETIME(3)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_no (order_no),
    UNIQUE KEY uk_orders_user_client_request (user_id, client_request_id),
    UNIQUE KEY uk_orders_user_active_slot (user_id, active_slot_key),
    KEY idx_orders_user_created (user_id, created_at DESC),
    KEY idx_orders_meal_date_status (meal_date, status),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_orders_status CHECK (status IN ('Pending', 'Confirmed', 'Completed', 'Cancelled')),
    CONSTRAINT ck_orders_meal_slot CHECK (meal_slot IN ('Lunch', 'Dinner')),
    CONSTRAINT ck_orders_requested_meal_slot CHECK (requested_meal_slot IN ('Lunch', 'Dinner')),
    CONSTRAINT ck_orders_total CHECK (total_amount >= 0),
    CONSTRAINT ck_orders_item_count CHECK (item_count > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Order lines are IMMUTABLE SNAPSHOTS taken at checkout. Editing or deleting a dish
-- afterwards must never change the historical order's text or amounts, so dish_name /
-- prices are copied here rather than joined at read time. dish_id is a nullable
-- reference for traceability only.
CREATE TABLE order_item (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    order_id        BIGINT         NOT NULL,
    dish_id         BIGINT         NULL,
    dish_name       VARCHAR(100)   NOT NULL,
    image_url       VARCHAR(300)   NULL,
    unit_base_price DECIMAL(10, 2) NOT NULL,
    unit_price      DECIMAL(10, 2) NOT NULL,
    quantity        INT            NOT NULL,
    subtotal        DECIMAL(10, 2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_item_order (order_id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT ck_order_item_quantity CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE order_item_option (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    order_item_id BIGINT         NOT NULL,
    group_name    VARCHAR(60)    NOT NULL,
    option_name   VARCHAR(60)    NOT NULL,
    price_delta   DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    display_order INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_order_item_option_item (order_item_id),
    CONSTRAINT fk_order_item_option_item FOREIGN KEY (order_item_id) REFERENCES order_item (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE order_status_log (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    order_id      BIGINT      NOT NULL,
    from_status   VARCHAR(20) NULL,
    to_status     VARCHAR(20) NOT NULL,
    actor_user_id BIGINT      NULL,
    created_at    DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_status_log_order (order_id, created_at),
    CONSTRAINT fk_order_status_log_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

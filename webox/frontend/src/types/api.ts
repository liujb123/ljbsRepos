/**
 * API contract types.
 *
 * Money is delivered as a decimal *string* ("22.50") by the backend, never as a JSON number,
 * so it can be parsed into integer cents without ever touching a float. See utils/money.ts.
 */

export type DishCategory =
  | 'Chinese'
  | 'Western'
  | 'Japanese'
  | 'Light Meal'
  | 'Korean'
  | 'Southeast Asian'

export type SpiceLevel = 'None' | 'Mild' | 'Medium' | 'Hot'

export type Allergen = 'Peanuts' | 'Dairy' | 'Egg' | 'Gluten' | 'Soy' | 'Fish' | 'Shellfish'

export type ProteinType =
  | 'Chicken'
  | 'Beef'
  | 'Pork'
  | 'Fish'
  | 'Shrimp'
  | 'Egg'
  | 'Tofu'
  | 'None'

export type SelectionType = 'SINGLE' | 'MULTI'

export type OrderStatus = 'Pending' | 'Confirmed' | 'Completed' | 'Cancelled'

export type MealSlot = 'Lunch' | 'Dinner'

export type TastePreference = 'Light' | 'Balanced' | 'Rich'

export type UserRole = 'EMPLOYEE' | 'ADMIN'

export const DISH_CATEGORIES: DishCategory[] = [
  'Chinese',
  'Western',
  'Japanese',
  'Light Meal',
  'Korean',
  'Southeast Asian'
]

export const SPICE_LEVELS: SpiceLevel[] = ['None', 'Mild', 'Medium', 'Hot']

export const ALLERGENS: Allergen[] = ['Peanuts', 'Dairy', 'Egg', 'Gluten', 'Soy', 'Fish', 'Shellfish']

export const PROTEIN_TYPES: ProteinType[] = [
  'Chicken',
  'Beef',
  'Pork',
  'Fish',
  'Shrimp',
  'Egg',
  'Tofu',
  'None'
]

export const TASTE_PREFERENCES: TastePreference[] = ['Light', 'Balanced', 'Rich']

export const MEAL_SLOTS: MealSlot[] = ['Lunch', 'Dinner']

/** Uniform response envelope. `code` is `OK` on success. */
export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  requestId?: string
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  total: number
  totalPages: number
}

/** Transport-level failure, normalized so views never inspect axios internals. */
export interface ApiError {
  status: number
  code: string
  message: string
  data?: Record<string, unknown> | null
}

// ---------------------------------------------------------------------------
// Authentication
// ---------------------------------------------------------------------------

export interface UserResponse {
  id: number
  email: string
  displayName: string
  role: UserRole
}

export interface AuthResponse {
  token: string
  tokenType: string
  expiresInSeconds: number
  user: UserResponse
}

export interface RegisterRequest {
  email: string
  password: string
  displayName: string
}

export interface LoginRequest {
  email: string
  password: string
}

// ---------------------------------------------------------------------------
// Menu
// ---------------------------------------------------------------------------

export interface MenuItemView {
  dishId: number
  name: string
  description: string
  price: string
  category: DishCategory
  spiceLevel: SpiceLevel
  imageUrl: string | null
  proteins: ProteinType[]
  allergens: Allergen[]
  dailyQty: number
  remainingQty: number
  /** True when the dish has customization groups, so the card routes to the detail page. */
  hasOptions: boolean
  recommended: boolean
  matchTags: string[]
}

export interface OptionView {
  optionId: number
  name: string
  priceDelta: string
  available: boolean
}

export interface OptionGroupView {
  groupId: number
  name: string
  required: boolean
  selectionType: SelectionType
  options: OptionView[]
}

export interface DishDetailView {
  dishId: number
  name: string
  description: string
  price: string
  category: DishCategory
  spiceLevel: SpiceLevel
  imageUrl: string | null
  proteins: ProteinType[]
  allergens: Allergen[]
  available: boolean
  dailyQty: number
  remainingQty: number
  optionGroups: OptionGroupView[]
}

export interface MenuQuery {
  date?: string
  categories?: DishCategory[]
  keyword?: string
  page?: number
  size?: number
  prefer?: boolean
}

// ---------------------------------------------------------------------------
// Preferences
// ---------------------------------------------------------------------------

export interface PreferencesResponse {
  allergens: Allergen[]
  preferredCategories: DishCategory[]
  spicePreference: SpiceLevel | null
  tastePreference: TastePreference | null
  budgetMin: string | null
  budgetMax: string | null
  configured: boolean
}

export interface PreferencesRequest {
  allergens: Allergen[]
  preferredCategories: DishCategory[]
  spicePreference: SpiceLevel | null
  tastePreference: TastePreference | null
  budgetMin: string | null
  budgetMax: string | null
}

export interface AddressResponse {
  id: number
  addressText: string
  useCount: number
  lastUsedAt: string
}

// ---------------------------------------------------------------------------
// Orders
// ---------------------------------------------------------------------------

export interface OrderLineRequest {
  lineKey: string
  dishId: number
  quantity: number
  optionIds: number[]
}

export interface CheckoutPreviewRequest {
  items: OrderLineRequest[]
  deliveryDate?: string
  mealSlot?: MealSlot
}

export interface PricedLine {
  lineKey: string
  dishId: number
  name: string
  unitPrice: string
  quantity: number
  subtotal: string
  optionLabels: string[]
}

export interface UnavailableLine {
  lineKey: string
  dishId: number
  name: string | null
  reason: string
}

export interface ExistingOrderRef {
  id: number
  orderNo: string
  status: OrderStatus
}

export interface BudgetInfo {
  min: string | null
  max: string | null
  cartTotal: string
  exceedsMax: boolean
}

export interface ResolvedMeal {
  mealDate: string
  mealSlot: MealSlot
  adjusted: boolean
  reasonCode: 'LUNCH_CUTOFF_PASSED' | 'DINNER_CUTOFF_PASSED' | null
}

export interface CheckoutPreviewResponse {
  bookableDates: string[]
  resolved: ResolvedMeal
  pricedItems: PricedLine[]
  total: string
  unavailableItems: UnavailableLine[]
  existingOrder: ExistingOrderRef | null
  budget: BudgetInfo | null
}

export interface PlaceOrderRequest {
  items: OrderLineRequest[]
  deliveryDate: string
  mealSlot: MealSlot
  deliveryAddress: string
  note?: string | null
}

export interface SelectedOption {
  groupName: string
  optionName: string
  priceDelta: string
}

export interface OrderLine {
  id: number
  dishId: number | null
  dishName: string
  imageUrl: string | null
  unitBasePrice: string
  unitPrice: string
  quantity: number
  subtotal: string
  options: SelectedOption[]
}

export interface OrderResponse {
  id: number
  orderNo: string
  status: OrderStatus
  mealDate: string
  mealSlot: MealSlot
  requestedMealSlot: MealSlot
  slotAdjusted: boolean
  deliveryAddress: string
  note: string | null
  itemCount: number
  totalAmount: string
  createdAt: string
  cancelledAt: string | null
  duplicated: boolean
  items: OrderLine[]
}

export interface OrderSummaryResponse {
  id: number
  orderNo: string
  status: OrderStatus
  mealDate: string
  mealSlot: MealSlot
  itemCount: number
  totalAmount: string
  createdAt: string
}

// ---------------------------------------------------------------------------
// Console (admin)
// ---------------------------------------------------------------------------

export interface AdminOptionView {
  id: number | null
  name: string
  priceDelta: string
  available: boolean
}

export interface AdminOptionGroupView {
  id: number | null
  name: string
  required: boolean
  selectionType: SelectionType
  options: AdminOptionView[]
}

export interface DishAdminView {
  id: number
  name: string
  description: string
  price: string
  category: DishCategory
  spiceLevel: SpiceLevel
  imageUrl: string | null
  available: boolean
  allergens: Allergen[]
  proteins: ProteinType[]
  updatedAt: string
  optionGroups: AdminOptionGroupView[]
}

export interface DishUpsertRequest {
  name: string
  description: string
  price: string
  category: DishCategory
  spiceLevel: SpiceLevel
  imageUrl: string | null
  allergens: Allergen[]
  proteins: ProteinType[]
  available: boolean
  optionGroups: AdminOptionGroupView[]
}

export interface DailyMenuView {
  menuDate: string
  items: DailyMenuItemView[]
}

export interface DailyMenuItemView {
  dishId: number
  name: string
  category: string | null
  available: boolean
  totalQty: number
  soldQty: number
  displayOrder: number
}

export interface CacheStats {
  hits: number
  misses: number
  fallbacks: number
  ttlMinutes: number
}

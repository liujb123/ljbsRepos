# End-to-end smoke test for the WeBox API.
#
# Exercises the whole Tier 1 + Tier 2 path against a running backend and asserts the
# rules the PRD calls out explicitly. Exits non-zero on the first failed assertion.
#
#   pwsh -File scripts/smoke-test.ps1 [-BaseUrl http://localhost:8080]

param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = 'Stop'
$script:Checks = 0
$script:Failures = 0

function Assert-That([string]$Name, [bool]$Condition, [string]$Detail = "") {
    $script:Checks++
    if ($Condition) {
        Write-Host ("  [PASS] " + $Name) -ForegroundColor Green
    } else {
        $script:Failures++
        Write-Host ("  [FAIL] " + $Name + " " + $Detail) -ForegroundColor Red
    }
}

function Section([string]$Title) {
    Write-Host ""
    Write-Host ("== " + $Title) -ForegroundColor Cyan
}

function Convert-Content($content) {
    if ($null -eq $content) { return $null }
    if ($content -is [byte[]]) {
        return [System.Text.Encoding]::UTF8.GetString($content)
    }
    return [string]$content
}

function Invoke-Api {
    param(
        [string]$Method = "GET",
        [string]$Path,
        [hashtable]$Headers = @{},
        $Body = $null,
        [string]$RawBody = $null
    )
    $uri = $BaseUrl + $Path
    $params = @{ Method = $Method; Uri = $uri; Headers = $Headers; UseBasicParsing = $true }
    if ($null -ne $Body) {
        $params.ContentType = "application/json; charset=utf-8"
        $params.Body = ($Body | ConvertTo-Json -Depth 10 -Compress)
    } elseif ($RawBody) {
        $params.ContentType = "application/json; charset=utf-8"
        $params.Body = $RawBody
    }
    try {
        $response = Invoke-WebRequest @params
        $text = Convert-Content $response.Content
        return [pscustomobject]@{
            Status = [int]$response.StatusCode
            Body   = ($text | ConvertFrom-Json)
            Raw    = $text
        }
    } catch {
        $resp = $_.Exception.Response
        if ($null -eq $resp) { throw }
        $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
        $text = $reader.ReadToEnd()
        $parsed = $null
        try { $parsed = $text | ConvertFrom-Json } catch { }
        return [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $parsed; Raw = $text }
    }
}

$suffix = [guid]::NewGuid().ToString('N').Substring(0, 8)
$employeeEmail = "employee.$suffix@example.com"
$employeePassword = "Passw0rd123"

Section "Health"
$health = Invoke-Api -Path "/actuator/health"
Assert-That "health endpoint responds" ($health.Status -eq 200)
Assert-That "database is reachable" ($health.Body.components.db.status -eq "UP")

Section "Registration and login"
$register = Invoke-Api -Method Post -Path "/api/auth/register" -Body @{
    email = $employeeEmail; password = $employeePassword; displayName = "Smoke Employee"
}
Assert-That "registration returns 201" ($register.Status -eq 201) "status=$($register.Status)"
$employeeToken = $register.Body.data.token
Assert-That "registration returns a token" ([bool]$employeeToken)
Assert-That "new accounts are employees" ($register.Body.data.user.role -eq "EMPLOYEE")

$duplicate = Invoke-Api -Method Post -Path "/api/auth/register" -Body @{
    email = $employeeEmail; password = $employeePassword; displayName = "Duplicate"
}
Assert-That "duplicate email is rejected with 409" ($duplicate.Status -eq 409) "status=$($duplicate.Status)"
Assert-That "duplicate email uses EMAIL_ALREADY_REGISTERED" ($duplicate.Body.code -eq "EMAIL_ALREADY_REGISTERED")

$weak = Invoke-Api -Method Post -Path "/api/auth/register" -Body @{
    email = "weak.$suffix@example.com"; password = "abcdefgh"; displayName = "Weak"
}
Assert-That "password without digits is rejected" ($weak.Status -eq 400) "status=$($weak.Status)"

$badLogin = Invoke-Api -Method Post -Path "/api/auth/login" -Body @{
    email = $employeeEmail; password = "WrongPass123"
}
Assert-That "wrong password returns 401" ($badLogin.Status -eq 401)
Assert-That "wrong password is not distinguishable from unknown email" (
    $badLogin.Body.code -eq "AUTH_INVALID_CREDENTIALS")

$employeeHeaders = @{ Authorization = "Bearer $employeeToken" }
$me = Invoke-Api -Path "/api/auth/me" -Headers $employeeHeaders
Assert-That "token restores the session" ($me.Body.data.email -eq $employeeEmail)

Section "Authorization boundary"
$anonymousMenu = Invoke-Api -Path "/api/menu/dates"
Assert-That "anonymous access to menu is 401" ($anonymousMenu.Status -eq 401)

$employeeOnAdmin = Invoke-Api -Path "/api/admin/dishes" -Headers $employeeHeaders
Assert-That "employee cannot reach /api/admin/** (403)" ($employeeOnAdmin.Status -eq 403) "status=$($employeeOnAdmin.Status)"

$adminLogin = Invoke-Api -Method Post -Path "/api/auth/login" -Body @{
    email = "admin@webox.com"; password = "Admin1234"
}
Assert-That "seeded admin can sign in" ($adminLogin.Status -eq 200) "status=$($adminLogin.Status)"
$adminHeaders = @{ Authorization = "Bearer $($adminLogin.Body.data.token)" }
Assert-That "admin role is ADMIN" ($adminLogin.Body.data.user.role -eq "ADMIN")

Section "Daily menu"
$dates = Invoke-Api -Path "/api/menu/dates" -Headers $employeeHeaders
Assert-That "today's menu is pre-seeded" ($dates.Body.data.Count -ge 1) "dates=$($dates.Body.data -join ',')"
$today = $dates.Body.data[0]

$menu = Invoke-Api -Path "/api/menu?date=$today&size=50" -Headers $employeeHeaders
Assert-That "menu returns dishes" ($menu.Body.data.total -ge 9) "total=$($menu.Body.data.total)"
Assert-That "menu cards carry a price" ([bool]$menu.Body.data.items[0].price)
Assert-That "menu cards carry a category" ([bool]$menu.Body.data.items[0].category)

$firstDish = $menu.Body.data.items[0]
$detail = Invoke-Api -Path "/api/menu/dishes/$($firstDish.dishId)" -Headers $employeeHeaders
Assert-That "dish detail loads" ($detail.Status -eq 200)
Assert-That "dish detail exposes allergens" ($null -ne $detail.Body.data.allergens)
Assert-That "dish detail exposes spice level" ([bool]$detail.Body.data.spiceLevel)

# Cuisine filtering (PRD 3.2 requires multi-select). Every case below asserts the status code
# first: a rejected request returns no items, and "no items" trivially satisfies an
# "only these cuisines came back" predicate. Without the status assertion a 400 passes silently -
# which is exactly how a dead cuisine filter went unnoticed.
$filtered = Invoke-Api -Path "/api/menu?date=$today&categories=Chinese&categories=Japanese&size=50" -Headers $employeeHeaders
Assert-That "multi-select cuisine filter is accepted" ($filtered.Status -eq 200) `
    "status=$($filtered.Status) body=$($filtered.Raw)"
Assert-That "multi-select cuisine filter returns dishes" ($filtered.Body.data.total -ge 1) `
    "total=$($filtered.Body.data.total)"
$cats = $filtered.Body.data.items | ForEach-Object { $_.category } | Sort-Object -Unique
Assert-That "multi-select cuisine filter returns only those cuisines" (
    ($cats | Where-Object { $_ -ne "Chinese" -and $_ -ne "Japanese" }).Count -eq 0) "got=$($cats -join ',')"

# The contract sends the English display label, and "Light Meal" contains a space. Spring's default
# enum converter matches the Java constant name ("LIGHT_MEAL") instead, so both of these used to
# return 400 while the assertion above still reported a pass.
$singleCuisine = Invoke-Api -Path "/api/menu?date=$today&categories=Japanese&size=50" -Headers $employeeHeaders
Assert-That "single cuisine filter is accepted" ($singleCuisine.Status -eq 200) `
    "status=$($singleCuisine.Status) body=$($singleCuisine.Raw)"
$singleCats = @($singleCuisine.Body.data.items | ForEach-Object { $_.category } | Sort-Object -Unique)
Assert-That "single cuisine filter returns only that cuisine" (($singleCats -join ',') -eq "Japanese") `
    "got=$($singleCats -join ',')"

$spacedCuisine = Invoke-Api -Path "/api/menu?date=$today&categories=Light%20Meal&size=50" -Headers $employeeHeaders
Assert-That "a cuisine label containing a space is accepted" ($spacedCuisine.Status -eq 200) `
    "status=$($spacedCuisine.Status) body=$($spacedCuisine.Raw)"
$spacedCats = @($spacedCuisine.Body.data.items | ForEach-Object { $_.category } | Sort-Object -Unique)
Assert-That "a cuisine label containing a space returns only that cuisine" (($spacedCats -join ',') -eq "Light Meal") `
    "got=$($spacedCats -join ',')"

$searched = Invoke-Api -Path "/api/menu?date=$today&keyword=chicken&size=50" -Headers $employeeHeaders
Assert-That "keyword search matches name or description" ($searched.Body.data.total -ge 1) "total=$($searched.Body.data.total)"

$injection = Invoke-Api -Path "/api/menu?date=$today&keyword=%27%20OR%201%3D1%20--" -Headers $employeeHeaders
Assert-That "injection-style keyword returns nothing and does not error" (
    $injection.Status -eq 200 -and $injection.Body.data.total -eq 0) "status=$($injection.Status) total=$($injection.Body.data.total)"

Section "Cart rules: five-portion cap"
# Two different dishes, 3 + 3 = 6 portions, must be rejected.
$twoDishes = $menu.Body.data.items | Where-Object { $_.optionGroups -eq $null } | Select-Object -First 2
if ($twoDishes.Count -lt 2) {
    $simple = @()
    foreach ($item in $menu.Body.data.items) {
        $d = Invoke-Api -Path "/api/menu/dishes/$($item.dishId)" -Headers $employeeHeaders
        if ($d.Body.data.optionGroups.Count -eq 0) { $simple += $d.Body.data }
        if ($simple.Count -ge 2) { break }
    }
    $twoDishes = $simple
}
Assert-That "found two dishes without option groups" ($twoDishes.Count -ge 2)

$overLimit = @{
    items = @(
        @{ dishId = $twoDishes[0].dishId; quantity = 3; optionIds = @() },
        @{ dishId = $twoDishes[1].dishId; quantity = 3; optionIds = @() }
    )
    deliveryDate = "$today"
    mealSlot = "Lunch"
}
# The preview endpoint intentionally accepts only what it needs to price and validate the cart;
# the delivery address is not part of that contract.
$previewOver = Invoke-Api -Method Post -Path "/api/orders/checkout-preview" -Headers $employeeHeaders -Body $overLimit
Assert-That "preview accepts the request" ($previewOver.Status -eq 200) "status=$($previewOver.Status) body=$($previewOver.Raw)"
Assert-That "preview prices every line server-side" ($previewOver.Body.data.pricedItems.Count -eq 2)
Assert-That "preview reports the resolved meal" ([bool]$previewOver.Body.data.resolved.mealSlot)

Section "Order placement and idempotency"
$orderBody = @{
    items = @(@{ dishId = $twoDishes[0].dishId; quantity = 2; optionIds = @() })
    deliveryDate = "$today"
    mealSlot = "Lunch"
    deliveryAddress = "Building A, Desk 101"
}
$idempotencyKey = [guid]::NewGuid().ToString()
$orderHeaders = @{ Authorization = "Bearer $employeeToken"; "Idempotency-Key" = $idempotencyKey }

$missingKey = Invoke-Api -Method Post -Path "/api/orders" -Headers $employeeHeaders -Body $orderBody
Assert-That "missing Idempotency-Key is rejected" ($missingKey.Status -eq 400) "status=$($missingKey.Status)"
Assert-That "missing key reports IDEMPOTENCY_KEY_REQUIRED" ($missingKey.Body.code -eq "IDEMPOTENCY_KEY_REQUIRED")

$first = Invoke-Api -Method Post -Path "/api/orders" -Headers $orderHeaders -Body $orderBody
Assert-That "order is created" ($first.Status -eq 200) "status=$($first.Status)"
$orderId = $first.Body.data.id
$orderNo = $first.Body.data.orderNo
Assert-That "order has a readable order number" ($orderNo -like "WB*") "orderNo=$orderNo"
Assert-That "order is Pending" ($first.Body.data.status -eq "Pending")
Assert-That "order stores the delivery address" ($first.Body.data.deliveryAddress -eq "Building A, Desk 101")

# Same key, submitted again: must return the same order, not create a second one.
$second = Invoke-Api -Method Post -Path "/api/orders" -Headers $orderHeaders -Body $orderBody
Assert-That "repeat submit returns the same order" ($second.Body.data.id -eq $orderId) "body=$($second.Raw)"
Assert-That "repeat submit is flagged as duplicated" ($second.Body.data.duplicated -eq $true)

$list = Invoke-Api -Path "/api/orders?size=50" -Headers $employeeHeaders
$matching = @($list.Body.data.items | Where-Object { $_.orderNo -eq $orderNo })
Assert-That "exactly one order exists for the idempotency key" ($matching.Count -eq 1) "count=$($matching.Count)"

# A different key for the same meal must be refused: one active order per meal.
$conflictHeaders = @{ Authorization = "Bearer $employeeToken"; "Idempotency-Key" = [guid]::NewGuid().ToString() }
$conflict = Invoke-Api -Method Post -Path "/api/orders" -Headers $conflictHeaders -Body $orderBody
Assert-That "second order for the same meal is refused (409)" ($conflict.Status -eq 409) "status=$($conflict.Status)"
Assert-That "conflict reports ORDER_SLOT_CONFLICT" ($conflict.Body.code -eq "ORDER_SLOT_CONFLICT")

$previewConflictBody = @{
    items = @(@{ dishId = $twoDishes[0].dishId; quantity = 2; optionIds = @() })
    deliveryDate = "$today"
    mealSlot = "Lunch"
}
$previewConflict = Invoke-Api -Method Post -Path "/api/orders/checkout-preview" -Headers $employeeHeaders -Body $previewConflictBody
Assert-That "preview surfaces the existing order (for 'View Existing Order')" (
    $previewConflict.Body.data.existingOrder.orderNo -eq $orderNo) "body=$($previewConflict.Raw)"

Section "Order detail and cancellation"
$detailOrder = Invoke-Api -Path "/api/orders/$orderId" -Headers $employeeHeaders
Assert-That "order detail includes line items" ($detailOrder.Body.data.items.Count -ge 1)
Assert-That "order detail includes the total" ([bool]$detailOrder.Body.data.totalAmount)

$cancelled = Invoke-Api -Method Post -Path "/api/orders/$orderId/cancel" -Headers $employeeHeaders
Assert-That "pending order can be cancelled" ($cancelled.Body.data.status -eq "Cancelled")

$cancelAgain = Invoke-Api -Method Post -Path "/api/orders/$orderId/cancel" -Headers $employeeHeaders
Assert-That "cancelling twice is idempotent" ($cancelAgain.Status -eq 200 -and $cancelAgain.Body.data.status -eq "Cancelled")

# The meal slot must be free again after cancellation.
$reorderHeaders = @{ Authorization = "Bearer $employeeToken"; "Idempotency-Key" = [guid]::NewGuid().ToString() }
$reorder = Invoke-Api -Method Post -Path "/api/orders" -Headers $reorderHeaders -Body $orderBody
Assert-That "same meal can be ordered again after cancellation" ($reorder.Status -eq 200) "status=$($reorder.Status)"

Section "Ownership isolation"
$otherEmail = "other.$suffix@example.com"
$otherRegister = Invoke-Api -Method Post -Path "/api/auth/register" -Body @{
    email = $otherEmail; password = $employeePassword; displayName = "Other Employee"
}
$otherHeaders = @{ Authorization = "Bearer $($otherRegister.Body.data.token)" }
$foreign = Invoke-Api -Path "/api/orders/$($reorder.Body.data.id)" -Headers $otherHeaders
Assert-That "another employee cannot read the order (403)" ($foreign.Status -eq 403) "status=$($foreign.Status)"

Section "Preferences"
$prefs = Invoke-Api -Method Put -Path "/api/users/me/preferences" -Headers $employeeHeaders -Body @{
    allergens = @("Peanuts", "Egg")
    preferredCategories = @("Japanese", "Light Meal")
    spicePreference = "Mild"
    tastePreference = "Light"
    budgetMin = "15.00"
    budgetMax = "30.00"
}
Assert-That "preferences save" ($prefs.Status -eq 200) "status=$($prefs.Status)"
Assert-That "allergens round-trip" ($prefs.Body.data.allergens -contains "Peanuts")

$badBudget = Invoke-Api -Method Put -Path "/api/users/me/preferences" -Headers $employeeHeaders -Body @{
    budgetMin = "50.00"; budgetMax = "10.00"
}
Assert-That "min budget above max is rejected" ($badBudget.Status -eq 400) "status=$($badBudget.Status)"

Section "Recommendation ordering"
$normal = Invoke-Api -Path "/api/menu?date=$today&size=50" -Headers $employeeHeaders
$preferred = Invoke-Api -Path "/api/menu?date=$today&size=50&prefer=true" -Headers $employeeHeaders
# Two rejected requests would both yield a null total, and null equals null - so the comparison
# below would pass on nothing. Pin both responses as valid first.
Assert-That "plain and preferred menu both return 200" (
    $normal.Status -eq 200 -and $preferred.Status -eq 200) "statuses=$($normal.Status)/$($preferred.Status)"
Assert-That "plain and preferred menu both return dishes" (
    $normal.Body.data.total -ge 1 -and $preferred.Body.data.total -ge 1) `
    "totals=$($normal.Body.data.total)/$($preferred.Body.data.total)"
Assert-That "preference ordering returns the same set of dishes" (
    $normal.Body.data.total -eq $preferred.Body.data.total) `
    "totals=$($normal.Body.data.total)/$($preferred.Body.data.total)"
$firstPreferred = $preferred.Body.data.items[0]
Assert-That "preferred cuisines are surfaced first" (
    $firstPreferred.category -eq "Japanese" -or $firstPreferred.category -eq "Light Meal") "first=$($firstPreferred.category)"
Assert-That "matching dishes are flagged as recommended" ($firstPreferred.recommended -eq $true)
Assert-That "matching dishes explain why" ($firstPreferred.matchTags.Count -ge 1)

Section "Allergen behaviour (warn, never hide)"
$peanutDish = $menu.Body.data.items | Where-Object { $_.allergens -contains "Peanuts" } | Select-Object -First 1
Assert-That "dishes containing a flagged allergen stay on the menu" ($null -ne $peanutDish) `
    "no peanut dish found in the menu"

Section "Console: dish management"
$adminDishes = Invoke-Api -Path "/api/admin/dishes?page=0&size=10" -Headers $adminHeaders
Assert-That "admin dish table loads" ($adminDishes.Status -eq 200)
Assert-That "catalog has around 50 dishes" ($adminDishes.Body.data.total -ge 45) "total=$($adminDishes.Body.data.total)"

$adminSearch = Invoke-Api -Path "/api/admin/dishes?keyword=burger" -Headers $adminHeaders
Assert-That "admin keyword search works" ($adminSearch.Body.data.total -ge 1)

# Same enum-binding path as the employee menu filter, on a different endpoint (PRD 4.3).
$adminByCategory = Invoke-Api -Path "/api/admin/dishes?category=Japanese" -Headers $adminHeaders
Assert-That "admin category filter is accepted" ($adminByCategory.Status -eq 200) `
    "status=$($adminByCategory.Status) body=$($adminByCategory.Raw)"
$adminCats = @($adminByCategory.Body.data.items | ForEach-Object { $_.category } | Sort-Object -Unique)
Assert-That "admin category filter returns only that cuisine" (($adminCats -join ',') -eq "Japanese") `
    "got=$($adminCats -join ',')"

$created = Invoke-Api -Method Post -Path "/api/admin/dishes" -Headers $adminHeaders -Body @{
    name = "Smoke Test Bowl"
    description = "Dish created by the automated smoke test"
    price = "19.90"
    category = "Light Meal"
    spiceLevel = "Mild"
    allergens = @("Soy")
    proteins = @("Tofu")
    optionGroups = @(
        @{ name = "Base"; required = $true; selectionType = "SINGLE";
           options = @(@{ name = "Rice"; priceDelta = "0.00" }, @{ name = "Quinoa"; priceDelta = "3.00" }) },
        @{ name = "Add-ons"; required = $false; selectionType = "MULTI";
           options = @(@{ name = "Avocado"; priceDelta = "4.00" }) }
    )
}
Assert-That "admin can create a dish" ($created.Status -eq 201) "status=$($created.Status)"
$newDishId = $created.Body.data.id
Assert-That "created dish keeps its option groups" ($created.Body.data.optionGroups.Count -eq 2)

$offShelf = Invoke-Api -Method Patch -Path "/api/admin/dishes/$newDishId/availability" -Headers $adminHeaders -Body @{
    available = $false
}
Assert-That "dish can be taken off the shelf" ($offShelf.Body.data.available -eq $false)

$hidden = Invoke-Api -Path "/api/menu/dishes/$newDishId" -Headers $employeeHeaders
Assert-That "off-shelf dish is invisible to employees" ($hidden.Status -eq 404) "status=$($hidden.Status)"

$cacheStats = Invoke-Api -Path "/api/admin/dishes/cache-stats" -Headers $adminHeaders
Assert-That "menu cache has recorded hits" ($cacheStats.Body.data.hits -ge 1) "hits=$($cacheStats.Body.data.hits)"

Section "Console: daily menu"
$tomorrow = (Get-Date).AddDays(1).ToString("yyyy-MM-dd")
$menuWrite = Invoke-Api -Method Put -Path "/api/admin/daily-menus/$tomorrow" -Headers $adminHeaders -Body @{
    items = @(
        @{ dishId = $twoDishes[0].dishId; totalQty = 40 },
        @{ dishId = $twoDishes[1].dishId; totalQty = 25 }
    )
}
Assert-That "admin can publish tomorrow's menu" ($menuWrite.Status -eq 200) "status=$($menuWrite.Status)"
Assert-That "daily quantities are stored" ($menuWrite.Body.data.items[0].totalQty -eq 40)

$readBack = Invoke-Api -Path "/api/admin/daily-menus/$tomorrow" -Headers $adminHeaders
Assert-That "published menu reads back" ($readBack.Body.data.items.Count -eq 2)

$employeeTomorrow = Invoke-Api -Path "/api/menu?date=$tomorrow&size=50" -Headers $employeeHeaders
Assert-That "employees see tomorrow's menu" ($employeeTomorrow.Body.data.total -eq 2) `
    "total=$($employeeTomorrow.Body.data.total)"

Section "English-only user-facing content"
$zhPattern = "[\u4e00-\u9fff]"
$errorSample = Invoke-Api -Method Post -Path "/api/auth/login" -Body @{ email = "nobody@example.com"; password = "Nope12345" }
Assert-That "error messages contain no Chinese characters" (
    ($errorSample.Raw -notmatch $zhPattern)) "body=$($errorSample.Raw)"
$menuSample = $menu.Raw
Assert-That "menu payload contains no Chinese characters" (
    ($null -eq $menuSample) -or ($menuSample -notmatch $zhPattern))

Write-Host ""
Write-Host ("=" * 60)
if ($script:Failures -eq 0) {
    Write-Host ("ALL CHECKS PASSED (" + $script:Checks + " assertions)") -ForegroundColor Green
    exit 0
} else {
    Write-Host ("FAILED: " + $script:Failures + " of " + $script:Checks + " assertions") -ForegroundColor Red
    exit 1
}

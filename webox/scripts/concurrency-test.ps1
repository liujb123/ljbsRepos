# Concurrency test for the two guarantees that make ordering safe under load.
#
#   1. Idempotency  - the same Idempotency-Key submitted 10x in parallel creates exactly one order.
#   2. Slot safety  - different keys racing for the same meal produce exactly one success.
#
# Both are enforced by unique indexes in MySQL, not by application-level locking, so this test
# is meaningful even with several application instances behind a load balancer.
#
#   pwsh -File scripts/concurrency-test.ps1 [-BaseUrl http://localhost:8080] [-Parallel 10]

param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$Parallel = 10
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

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

function Invoke-Json {
    param(
        [string]$Method = "GET",
        [string]$Path,
        [hashtable]$Headers = @{},
        $Body = $null
    )
    $params = @{
        Method      = $Method
        Uri         = $BaseUrl + $Path
        Headers     = $Headers
        UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json; charset=utf-8'
        $params.Body = ($Body | ConvertTo-Json -Depth 10 -Compress)
    }
    try {
        $response = Invoke-WebRequest @params
        $text = if ($response.Content -is [byte[]]) {
            [System.Text.Encoding]::UTF8.GetString($response.Content)
        } else { [string]$response.Content }
        return [pscustomobject]@{ Status = [int]$response.StatusCode; Body = ($text | ConvertFrom-Json); Raw = $text }
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

<#
  Fires N requests genuinely in parallel using the .NET HTTP stack, so the server sees
  simultaneous arrivals rather than a sequential loop.
#>
function Invoke-ParallelPosts {
    param(
        [string]$Path,
        [string]$Token,
        [string[]]$IdempotencyKeys,
        [string]$JsonBody
    )
    $handler = New-Object System.Net.Http.HttpClientHandler
    $client = New-Object System.Net.Http.HttpClient($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(60)

    $tasks = @()
    foreach ($key in $IdempotencyKeys) {
        $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Post, ($BaseUrl + $Path))
        $request.Headers.Authorization =
            New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $Token)
        $request.Headers.Add("Idempotency-Key", $key)
        $content = New-Object System.Net.Http.StringContent(
            $JsonBody, [System.Text.Encoding]::UTF8, "application/json")
        $request.Content = $content
        $tasks += $client.SendAsync($request)
    }

    [System.Threading.Tasks.Task]::WaitAll($tasks)
    $results = @()
    foreach ($task in $tasks) {
        $response = $task.Result
        $body = $response.Content.ReadAsStringAsync().Result
        $parsed = $null
        try { $parsed = $body | ConvertFrom-Json } catch { }
        $results += [pscustomobject]@{
            Status = [int]$response.StatusCode
            Body   = $parsed
            Raw    = $body
        }
    }
    $client.Dispose()
    $handler.Dispose()
    return $results
}

$suffix = [guid]::NewGuid().ToString('N').Substring(0, 8)
$email = "concurrency.$suffix@example.com"
$password = "Passw0rd123"

Section "Setup"
$register = Invoke-Json -Method Post -Path "/api/auth/register" -Body @{
    email = $email; password = $password; displayName = "Concurrency Tester"
}
$token = $register.Body.data.token
$headers = @{ Authorization = "Bearer $token" }
Assert-That "test employee registered" ([bool]$token)

$dates = Invoke-Json -Path "/api/menu/dates" -Headers $headers
$today = $dates.Body.data[0]

# Pick a dish without option groups so the payload needs no configuration.
$menu = Invoke-Json -Path "/api/menu?date=$today&size=50" -Headers $headers
$dish = $menu.Body.data.items | Where-Object { -not $_.hasOptions } | Select-Object -First 1
Assert-That "found a dish without options" ($null -ne $dish)

$orderBody = @{
    items = @(@{ dishId = $dish.dishId; quantity = 2; optionIds = @() })
    deliveryDate = "$today"
    mealSlot = "Lunch"
    deliveryAddress = "Concurrency Lab, Desk 7"
} | ConvertTo-Json -Depth 8 -Compress

Section "Idempotency under $Parallel parallel submissions with the same key"
$sharedKey = [guid]::NewGuid().ToString()
$parallelKeys = @(1..$Parallel | ForEach-Object { $sharedKey })
$sameKeyResults = Invoke-ParallelPosts -Path "/api/orders" -Token $token `
    -IdempotencyKeys $parallelKeys -JsonBody $orderBody

$statuses = @($sameKeyResults | ForEach-Object { $_.Status } | Sort-Object -Unique)
$orderNos = @($sameKeyResults |
    Where-Object { $_.Body -and $_.Body.data -and $_.Body.data.orderNo } |
    ForEach-Object { $_.Body.data.orderNo } |
    Sort-Object -Unique)

Write-Host ("    statuses: " + ($statuses -join ', '))
Write-Host ("    distinct order numbers: " + $orderNos.Count)

Assert-That "every parallel submit returned the same single order" (
    $statuses.Count -eq 1 -and $statuses[0] -eq 200) "statuses=$($statuses -join ',')"
Assert-That "all $Parallel responses carry the same order number" ($orderNos.Count -eq 1) `
    "orderNos=$($orderNos -join ',')"
Assert-That "exactly one response created the order, the rest are replays" (
    (@($sameKeyResults | Where-Object { $_.Body.data.duplicated -eq $false }).Count) -eq 1)
Assert-That "$($Parallel - 1) responses were recognised as replays" (
    (@($sameKeyResults | Where-Object { $_.Body.data.duplicated -eq $true }).Count) -eq ($Parallel - 1))

$list = Invoke-Json -Path "/api/orders?size=50" -Headers $headers
$created = @($list.Body.data.items | Where-Object { $_.orderNo -eq $orderNos[0] })
Assert-That "the database holds exactly one order for that key" ($created.Count -eq 1) `
    "count=$($created.Count)"
$orderId = $created[0].id

# Free the meal so the next phase races for an empty slot.
$null = Invoke-Json -Method Post -Path "/api/orders/$orderId/cancel" -Headers $headers
$afterCancel = Invoke-Json -Path "/api/orders?size=50" -Headers $headers
$stillActive = @($afterCancel.Body.data.items | Where-Object {
    $_.status -eq 'Pending' -or $_.status -eq 'Confirmed' })
Assert-That "the meal slot is free before the race" ($stillActive.Count -eq 0) `
    "active=$($stillActive.Count)"

Section "Meal-slot safety: $Parallel different keys racing for the same meal"
$distinctKeys = @(1..$Parallel | ForEach-Object { [guid]::NewGuid().ToString() })
$raceResults = Invoke-ParallelPosts -Path "/api/orders" -Token $token `
    -IdempotencyKeys $distinctKeys -JsonBody $orderBody

$raceStatuses = $raceResults | ForEach-Object { $_.Status } | Group-Object | Sort-Object Name
Write-Host ("    status distribution: " + (($raceStatuses | ForEach-Object { "$($_.Name)x$($_.Count)" }) -join ', '))

$successes = @($raceResults | Where-Object { $_.Status -eq 200 })
$conflicts = @($raceResults | Where-Object { $_.Status -eq 409 })

Assert-That "exactly one racing submit won" ($successes.Count -eq 1) "winners=$($successes.Count)"
Assert-That "every other racing submit got 409" (($successes.Count + $conflicts.Count) -eq $Parallel) `
    "200=$($successes.Count) 409=$($conflicts.Count)"
Assert-That "the conflict reason is ORDER_SLOT_CONFLICT" (
    (@($conflicts | Where-Object { $_.Body.code -eq 'ORDER_SLOT_CONFLICT' }).Count) -eq $conflicts.Count)

$afterRace = Invoke-Json -Path "/api/orders?size=50" -Headers $headers
$forToday = @($afterRace.Body.data.items | Where-Object { $_.status -eq 'Pending' -or $_.status -eq 'Confirmed' })
Assert-That "only one active order exists for the meal" ($forToday.Count -eq 1) "active=$($forToday.Count)"

Section "Slot is released by cancellation"
# The race left one active order behind; cancelling it must free the meal again.
$activeAfterRace = Invoke-Json -Path "/api/orders?size=50" -Headers $headers
$active = @($activeAfterRace.Body.data.items | Where-Object {
    $_.status -eq 'Pending' -or $_.status -eq 'Confirmed' })
Assert-That "the race left exactly one active order" ($active.Count -eq 1) "active=$($active.Count)"

$null = Invoke-Json -Method Post -Path "/api/orders/$($active[0].id)/cancel" -Headers $headers
$releaseHeaders = @{ Authorization = "Bearer $token"; "Idempotency-Key" = [guid]::NewGuid().ToString() }
$reorder = Invoke-Json -Method Post -Path "/api/orders" -Headers $releaseHeaders -Body ($orderBody | ConvertFrom-Json)
Assert-That "the meal can be ordered again after cancelling" ($reorder.Status -eq 200) `
    "status=$($reorder.Status)"

Write-Host ""
Write-Host ("=" * 60)
if ($script:Failures -eq 0) {
    Write-Host ("ALL CONCURRENCY CHECKS PASSED (" + $script:Checks + " assertions)") -ForegroundColor Green
    exit 0
} else {
    Write-Host ("FAILED: " + $script:Failures + " of " + $script:Checks + " assertions") -ForegroundColor Red
    exit 1
}

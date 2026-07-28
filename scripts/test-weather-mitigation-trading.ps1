param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function Json($value) {
    return ($value | ConvertTo-Json -Depth 50 -Compress)
}

$TradableWeatherItems = @(
    "MulchBag",
    "OrganicCompostBag",
    "SupportStakeKit",
    "TrellisKit",
    "RaisedBedKit",
    "IrrigationSystemKit",
    "WaterStorageTankKit",
    "ShadeNetKit",
    "WindbreakKit",
    "GreenhouseKit",
    "DrainageCanalKit"
)

function New-InventorySlots {
    param(
        [bool]$IncludeWeatherItems
    )

    $slots = @()
    for ($i = 0; $i -lt 36; $i++) {
        $slots += @{ itemType = "None"; amount = 0; liquidMl = 0; sprayerLiquid = "None" }
    }

    if ($IncludeWeatherItems) {
        for ($i = 0; $i -lt $TradableWeatherItems.Count; $i++) {
            $slots[$i] = @{
                itemType = $TradableWeatherItems[$i]
                amount = 5
                liquidMl = 0
                sprayerLiquid = "None"
            }
        }
    }

    return $slots
}

function New-FarmSnapshot {
    param(
        [string]$District,
        [int]$Money,
        [bool]$IncludeWeatherItems
    )

    return @{
        area = @{
            x = 0.30; y = 0.47; width = 0.02; height = 0.02
            districtName = $District; terrainSeed = 12345
        }
        soil = @{
            sampleALatitude = 7.0; sampleALongitude = 125.5
            sampleA = @{ sand = 45; silt = 28; clay = 27; phh2o = 6.1; soc = 22; cfvo = 6; bdod = 125; nitrogen = 14 }
            sampleBLatitude = 7.01; sampleBLongitude = 125.51
            sampleB = @{ sand = 40; silt = 30; clay = 30; phh2o = 6.3; soc = 24; cfvo = 5; bdod = 120; nitrogen = 16 }
        }
        player = @{ position = @{x=10;y=5;z=10}; rotation = @{x=0;y=0;z=0;w=1} }
        inventory = @{
            selectedSlotIndex = 0
            money = $Money
            slots = New-InventorySlots -IncludeWeatherItems $IncludeWeatherItems
        }
        gameTime = @{ totalGameDays = 4.33; daysPerMonth = 16; monthsPerYear = 12 }
        weather = @{ currentEvent = "Clear"; currentTemperatureC = 27; currentHumidity = 78; currentRainIntensity = 0; remainingEventDays = 0; dailyLowTemperatureC = 24; dailyHighTemperatureC = 31 }
        climateEvent = @{ isTrackingEvent = $false; activeEventType = "Clear"; activeDurationDays = 0; eventStartGameDay = 0; beforeSnapshots = @(); actionRecords = @() }
        crops = @()
        worldObjects = @()
    }
}

function Register-Player {
    param([string]$DisplayName)

    $stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $email = "$($DisplayName.ToLower()).$stamp@example.com"
    $codeRequest = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/register/request" `
        -ContentType "application/json" -Body (Json @{
            email = $email
            password = "TestFarm123!"
            confirmPassword = "TestFarm123!"
            displayName = $DisplayName
        })
    if ([string]::IsNullOrWhiteSpace($codeRequest.devCode)) {
        throw "No devCode returned. Start the backend with APP_VERIFICATION_EXPOSE_CODE=true (the dev default)."
    }
    return Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/register/verify" `
        -ContentType "application/json" -Body (Json @{
            email = $email
            code = $codeRequest.devCode
        })
}

function Headers($auth) {
    return @{ Authorization = "Bearer $($auth.accessToken)" }
}

Write-Host "1. Checking backend"
$health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health"
if ($health.status -ne "UP") {
    throw "Backend is not UP."
}

Write-Host "2. Creating seller and buyer"
$seller = Register-Player "WeatherSeller"
Start-Sleep -Milliseconds 10
$buyer = Register-Player "WeatherBuyer"
$sellerHeaders = Headers $seller
$buyerHeaders = Headers $buyer

Write-Host "3. Creating farm snapshots"
Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/farms/me" -Headers $sellerHeaders `
    -ContentType "application/json" -Body (Json @{
        expectedRevision = 0
        schemaVersion = 1
        generatorVersion = "davao-terrain-v1"
        snapshot = New-FarmSnapshot "Calinan" 100000 $true
    }) | Out-Null

Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/farms/me" -Headers $buyerHeaders `
    -ContentType "application/json" -Body (Json @{
        expectedRevision = 0
        schemaVersion = 1
        generatorVersion = "davao-terrain-v1"
        snapshot = New-FarmSnapshot "Toril" 100000 $false
    }) | Out-Null

Write-Host "4. Testing marketplace create and purchase for every new item"
foreach ($item in $TradableWeatherItems) {
    $fee = Invoke-RestMethod -Method Get `
        -Uri "$BaseUrl/api/marketplace/fee?itemType=$item&quantity=1&askingPrice=1" `
        -Headers $sellerHeaders
    if ($fee.itemBaseValue -le 0) {
        throw "$item returned an invalid marketplace base value."
    }

    $listing = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/marketplace/listings" `
        -Headers $sellerHeaders -ContentType "application/json" -Body (Json @{
            itemType = $item
            quantity = 1
            askingPrice = 1
        })

    Invoke-RestMethod -Method Post `
        -Uri "$BaseUrl/api/marketplace/listings/$($listing.id)/buy" `
        -Headers $buyerHeaders -ContentType "application/json" -Body "{}" | Out-Null

    Write-Host "   PASS marketplace: $item"
}

Write-Host "5. Marking both players online"
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/presence/heartbeat" -Headers $sellerHeaders | Out-Null
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/presence/heartbeat" -Headers $buyerHeaders | Out-Null

Write-Host "6. Testing one direct trade containing all new items"
$trade = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/trades/invite" `
    -Headers $sellerHeaders -ContentType "application/json" `
    -Body (Json @{ targetPlayerId = $buyer.user.id })

$trade = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/trades/$($trade.id)/accept" `
    -Headers $buyerHeaders -ContentType "application/json" -Body "{}"

$tradeItems = @()
foreach ($item in $TradableWeatherItems) {
    $tradeItems += @{ itemType = $item; quantity = 1 }
}

Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/trades/$($trade.id)/offer" `
    -Headers $sellerHeaders -ContentType "application/json" `
    -Body (Json @{ money = 0; items = $tradeItems }) | Out-Null

Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/trades/$($trade.id)/offer" `
    -Headers $buyerHeaders -ContentType "application/json" `
    -Body (Json @{ money = 0; items = @() }) | Out-Null

Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/trades/$($trade.id)/agree" `
    -Headers $sellerHeaders -ContentType "application/json" -Body "{}" | Out-Null
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/trades/$($trade.id)/agree" `
    -Headers $buyerHeaders -ContentType "application/json" -Body "{}" | Out-Null
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/trades/$($trade.id)/confirm" `
    -Headers $sellerHeaders -ContentType "application/json" -Body "{}" | Out-Null
$completed = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/trades/$($trade.id)/confirm" `
    -Headers $buyerHeaders -ContentType "application/json" -Body "{}"

if ($completed.status -ne "COMPLETED") {
    throw "Direct trade did not complete."
}

Write-Host ""
Write-Host "ALL 11 WEATHER-MITIGATION ITEMS PASSED MARKETPLACE AND DIRECT-TRADE TESTS"

param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function Json($value) {
    return ($value | ConvertTo-Json -Depth 80 -Compress)
}

Write-Host "1. Backend health"
$health = Invoke-RestMethod -Method Get -Uri "$BaseUrl/actuator/health"
if ($health.status -ne "UP") { throw "Backend is not UP." }

$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$email = "task.persistence.$stamp@example.com"
$password = "TestFarm123!"

Write-Host "2. Registering temporary player (request code + verify)"
$codeRequest = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/register/request" `
    -ContentType "application/json" `
    -Body (Json @{
        email = $email
        password = $password
        confirmPassword = $password
        displayName = "Task Persistence Test"
    })
if ([string]::IsNullOrWhiteSpace($codeRequest.devCode)) {
    throw "No devCode returned. Start the backend with APP_VERIFICATION_EXPOSE_CODE=true (the dev default)."
}
$auth = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/register/verify" `
    -ContentType "application/json" `
    -Body (Json @{
        email = $email
        code = $codeRequest.devCode
    })
$headers = @{ Authorization = "Bearer $($auth.accessToken)" }

$action = @{
    actionType = "WaterCrop"
    itemType = "WateringCan"
    cropType = "Coconut"
    cropId = "test-coconut-1"
    gameDay = 2.35
    quantity = 1
    actionSucceeded = $true
    recordOrigin = "Direct"
    beforeMoisture = 0.22
    afterMoisture = 0.62
    details = "Persistence test action"
}

$snapshot = @{
    area = @{ x = 0.30; y = 0.47; width = 0.02; height = 0.02; districtName = "Marilog"; terrainSeed = 12345 }
    soil = @{
        sampleALatitude = 7.0; sampleALongitude = 125.5
        sampleA = @{ sand = 45; silt = 28; clay = 27; phh2o = 6.1; soc = 22; cfvo = 6; bdod = 125; nitrogen = 14 }
        sampleBLatitude = 7.01; sampleBLongitude = 125.51
        sampleB = @{ sand = 40; silt = 30; clay = 30; phh2o = 6.3; soc = 24; cfvo = 5; bdod = 120; nitrogen = 16 }
    }
    player = @{ position = @{x=10;y=5;z=10}; rotation = @{x=0;y=0;z=0;w=1} }
    inventory = @{ selectedSlotIndex = 0; money = 435; slots = @() }
    gameTime = @{ totalGameDays = 2.125; daysPerMonth = 16; monthsPerYear = 12 }
    weather = @{ currentEvent = "Clear"; currentTemperatureC = 23.1; currentHumidity = 78; currentRainIntensity = 0; remainingEventDays = 0; dailyLowTemperatureC = 21; dailyHighTemperatureC = 29 }
    climateEvent = @{ isTrackingEvent = $false; activeEventType = "Clear"; activeDurationDays = 0; eventStartGameDay = 0; beforeSnapshots = @(); actionRecords = @() }
    dailyTasks = @{
        assignedAbsoluteDay = 3
        rewardMoney = 400
        rewardClaimed = $false
        skipUsed = $false
        tasks = @(
            @{ instanceId = "daily-1"; templateId = 1; title = "Water Coconut"; description = "Water one coconut."; kind = "WaterCrop"; difficulty = "Easy"; cropType = "Coconut"; cropId = "test-coconut-1"; targetAmount = 1; progressAmount = 1; completed = $true; progressKeys = @("test-coconut-1") },
            @{ instanceId = "daily-2"; templateId = 66; title = "Prepare a planting spot"; description = "Dig one spot."; kind = "DigPlantingSpot"; difficulty = "Easy"; targetAmount = 1; progressAmount = 0; completed = $false; progressKeys = @() }
        )
    }
    aiAdvisorTask = @{
        active = $true
        checking = $false
        taskId = "ai-task-persistence-test"
        dialogue = "I reviewed your coconut stock."
        taskText = "sell at least P1000 worth of Coconut"
        rewardMoney = 1200
        startedGameDay = 2.0
        objective = @{ type = "SellCropValue"; cropType = "Coconut"; targetValue = 1000; baselineMetric = 0 }
        beforeSnapshots = @()
        actionRecords = @($action)
        lastVerdict = ""
        lastTips = ""
    }
    crops = @()
    worldObjects = @()
}

Write-Host "3. Saving schema version 3 task state"
$saved = Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/farms/me" `
    -Headers $headers -ContentType "application/json" `
    -Body (Json @{
        expectedRevision = 0
        schemaVersion = 3
        generatorVersion = "davao-terrain-v1"
        snapshot = $snapshot
    })
if ($saved.revision -ne 1) { throw "Expected first revision to be 1." }

Write-Host "4. Loading and verifying round-trip"
$loaded = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/farms/me" -Headers $headers
if ($loaded.snapshot.dailyTasks.tasks.Count -ne 2) { throw "Daily tasks did not round-trip." }
if ($loaded.snapshot.dailyTasks.tasks[0].progressAmount -ne 1) { throw "Daily progress did not round-trip." }
if (-not $loaded.snapshot.aiAdvisorTask.active) { throw "AI task active state did not round-trip." }
if ($loaded.snapshot.aiAdvisorTask.taskId -ne "ai-task-persistence-test") { throw "AI task ID did not round-trip." }
if ($loaded.snapshot.aiAdvisorTask.actionRecords.Count -ne 1) { throw "AI action history did not round-trip." }
if ($loaded.snapshot.aiAdvisorTask.actionRecords[0].cropId -ne "test-coconut-1") { throw "AI action contents did not round-trip." }
if ($loaded.snapshot.aiAdvisorTask.actionRecords[0].recordOrigin -ne "Direct") { throw "Action origin did not round-trip." }

Write-Host "PASS: daily tasks, AI task, objective, and recorded actions are stored in farm_save.snapshot JSONB." -ForegroundColor Green

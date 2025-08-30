require("RibsFramework")

local sandbox = RibsFramework.Sandbox:new({ ID = "SmartHutch", autoModOptions = true })

local function getHutchs()
    local allZones = DesignationZoneAnimal.getAllZones()
    if not allZones then return {} end

    local hutches = {}
    local function forEachZone(zone)
        local zoneHutches = zone:getHutchs()

        if not zoneHutches then return end

        for indexHutch = 0, zoneHutches:size() - 1 do
            local zoneHutch = zoneHutches:get(indexHutch)
            hutches[zoneHutch] = true
        end
    end
    for indexZone = 0, allZones:size() - 1 do forEachZone(allZones:get(indexZone)) end

    return hutches
end

local function autoHeal(hutch)
    local animals = transformIntoKahluaTable(hutch:getAnimalInside())
    if not animals then return end

    local function forEachAnimal(animal)
        local currentHealth = animal:getHealth()

        if currentHealth >= 1 or currentHealth == 0 then
            return
        end

        local minHealth = sandbox:getValue("MinHealth")
        local maxHealth = sandbox:getValue("MaxHealth")
        local healthBoost = sandbox:getValue("HealthBoost")

        local newHealth = currentHealth

        if currentHealth < minHealth then
            newHealth = minHealth
        end

        if newHealth < 1 and newHealth < maxHealth then
            newHealth = newHealth + healthBoost
        end

        if newHealth > 1 then newHealth = 1 end

        if newHealth ~= currentHealth then
            animal:setHealth(newHealth)
        end
    end
    for _, animal in pairs(animals) do forEachAnimal(animal) end
end

RibsFramework.Interval:new({
    seconds = (function()
        return sandbox:getValue("HealthInterval")
    end),
    handlers = {(function()
        if not sandbox:getValue("AutoHeal") then return end

        for hutch in pairs(getHutchs()) do
            autoHeal(hutch)
        end
    end)}
})

local function autoClose(hutch)
    if not (hutch and hutch.getHutchDirt) then return end

    local dirtiness = hutch:getHutchDirt()
    local threshold = sandbox:getValue("DirtinessThreshold")

    if dirtiness < threshold then return end

    if hutch:isDoorClosed() then return end

    hutch:toggleDoor()

    if sandbox:getValue("ReleaseChickensAfterClosing") then
        hutch:releaseAllAnimals()
    end
end

RibsFramework.Interval:new({
    seconds = (function()
        return sandbox:getValue("CheckInterval")
    end),
    handlers = {(function()
        if not sandbox:getValue("AutoClose") then return end

        for hutch in pairs(getHutchs()) do
            autoClose(hutch)
        end
    end)}
})


local function autoClean(hutch)
    if not (hutch and hutch.getHutchDirt) then return end

    if not hutch:isDoorClosed() then return end

    local dirtiness = hutch:getHutchDirt()
    local stopThreshold = sandbox:getValue("CleanStopThreshold")

    if dirtiness <= stopThreshold then return end

    local amount = sandbox:getValue("CleanAmount")

    local newDirtiness = dirtiness - amount

    if newDirtiness < 0 then newDirtiness = 0 end

    hutch:setHutchDirt(newDirtiness)

    if newDirtiness <= stopThreshold and sandbox:getValue("AutoOpenOnCleanStop") and hutch:isDoorClosed() then
        hutch:toggleDoor()
    end
end

RibsFramework.Interval:new({
    seconds = (function()
        return sandbox:getValue("CleanInterval")
    end),
    handlers = {(function()
        if not sandbox:getValue("AutoClean") then return end

        for hutch in pairs(getHutchs()) do
            autoClean(hutch)
        end
    end)}
})

local function passiveClean(hutch)
    if not (hutch and hutch.getHutchDirt) then return end

    local dirtiness = hutch:getHutchDirt()

    if dirtiness <= 0 then return end

    local multiplier = sandbox:getValue("PassiveCleanMultiplier")

    local dirtinessMultiplier = dirtiness * multiplier

    local newDirtiness = dirtiness - dirtinessMultiplier

    if newDirtiness < 0 then newDirtiness = 0 end

    hutch:setHutchDirt(newDirtiness)
end
RibsFramework.IntervalIngame:new({
    EveryOneMinute = (function()
        return sandbox:getValue("PassiveCleanInterval")
    end),
    handlers = {(function()
        for hutch in pairs(getHutchs()) do
            passiveClean(hutch)
        end
    end)}
})

local function toggleDoor(hutch, open)
    if not (hutch and hutch.isDoorClosed) then return end

    if open and not hutch:isDoorClosed() then return end

    if not open and hutch:isDoorClosed() then return end

    hutch:toggleDoor()
end
RibsFramework.IntervalIngame:new({
    EveryHours = 1,
    handlers = {(function()
        if not sandbox:getValue("TimedDoorEnabled") then return end

        local hour = math.floor(GameTime:getInstance():getTimeOfDay())

        if hour == sandbox:getValue("TimedDoorOpenHour") then
            for hutch in pairs(getHutchs()) do
                toggleDoor(hutch, true)
            end
            return
        end

        if hour == sandbox:getValue("TimedDoorCloseHour") then
            for hutch in pairs(getHutchs()) do
                toggleDoor(hutch, false)
            end
            return
        end
    end)}
})
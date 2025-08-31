require("RibsFramework")

SmartHutch = SmartHutch or {}

SmartHutch.sandbox = RibsFramework.Sandbox:new({ ID = "SmartHutch", autoModOptions = true })

function SmartHutch.getHutchs()
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

function SmartHutch.autoHeal(hutch)
    local animals = transformIntoKahluaTable(hutch:getAnimalInside())
    if not animals then return end

    local function forEachAnimal(animal)
        local currentHealth = animal:getHealth()

        if currentHealth >= 1 or currentHealth == 0 then
            return
        end

        local minHealth = SmartHutch.sandbox:getValue("MinHealth")
        local maxHealth = SmartHutch.sandbox:getValue("MaxHealth")
        local healthBoost = SmartHutch.sandbox:getValue("HealthBoost")

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

SmartHutch.AutoHealInterval = RibsFramework.Interval:new({
    seconds = (function()
        return SmartHutch.sandbox:getValue("HealthInterval")
    end),
    handlers = { (function()
        if not SmartHutch.sandbox:getValue("AutoHeal") then return end

        for hutch in pairs(SmartHutch.getHutchs()) do
            SmartHutch.autoHeal(hutch)
        end
    end) }
})

function SmartHutch.autoClose(hutch)
    if not (hutch and hutch.getHutchDirt) then return end

    local dirtiness = hutch:getHutchDirt()
    local threshold = SmartHutch.sandbox:getValue("DirtinessThreshold")

    if dirtiness < threshold then return end

    if hutch:isDoorClosed() then return end

    hutch:toggleDoor()

    if SmartHutch.sandbox:getValue("ReleaseChickensAfterClosing") then
        hutch:releaseAllAnimals()
    end
end

SmartHutch.AutoCloseInterval = RibsFramework.Interval:new({
    seconds = (function()
        return SmartHutch.sandbox:getValue("CheckInterval")
    end),
    handlers = { (function()
        if not SmartHutch.sandbox:getValue("AutoClose") then return end

        for hutch in pairs(SmartHutch.getHutchs()) do
            SmartHutch.autoClose(hutch)
        end
    end) }
})


function SmartHutch.autoClean(hutch)
    if not (hutch and hutch.getHutchDirt) then return end

    if not hutch:isDoorClosed() then return end

    local dirtiness = hutch:getHutchDirt()
    local stopThreshold = SmartHutch.sandbox:getValue("CleanStopThreshold")

    if dirtiness <= stopThreshold then return end

    local amount = SmartHutch.sandbox:getValue("CleanAmount")

    local newDirtiness = dirtiness - amount

    if newDirtiness < 0 then newDirtiness = 0 end

    hutch:setHutchDirt(newDirtiness)

    if newDirtiness <= stopThreshold and SmartHutch.sandbox:getValue("AutoOpenOnCleanStop") and hutch:isDoorClosed() then
        hutch:toggleDoor()
    end
end

SmartHutch.CleanInterval = RibsFramework.Interval:new({
    seconds = (function()
        return SmartHutch.sandbox:getValue("CleanInterval")
    end),
    handlers = { (function()
        if not SmartHutch.sandbox:getValue("AutoClean") then return end

        for hutch in pairs(SmartHutch.getHutchs()) do
            SmartHutch.autoClean(hutch)
        end
    end) }
})

function SmartHutch.passiveClean(hutch)
    if not (hutch and hutch.getHutchDirt) then return end

    local dirtiness = hutch:getHutchDirt()

    if dirtiness <= 0 then return end

    local multiplier = SmartHutch.sandbox:getValue("PassiveCleanMultiplier")

    local dirtinessMultiplier = dirtiness * multiplier

    local newDirtiness = dirtiness - dirtinessMultiplier

    if newDirtiness < 0 then newDirtiness = 0 end

    hutch:setHutchDirt(newDirtiness)
end

SmartHutch.PassiveCleanInterval = RibsFramework.IntervalIngame:new({
    EveryOneMinute = (function()
        return SmartHutch.sandbox:getValue("PassiveCleanInterval")
    end),
    handlers = { (function()
        for hutch in pairs(SmartHutch.getHutchs()) do
            SmartHutch.passiveClean(hutch)
        end
    end) }
})

function SmartHutch.toggleDoor(hutch, open)
    if not (hutch and hutch.isDoorClosed) then return end

    if open and not hutch:isDoorClosed() then return end

    if not open and hutch:isDoorClosed() then return end

    hutch:toggleDoor()
end

SmartHutch.TimedDoorInterval = RibsFramework.IntervalIngame:new({
    EveryHours = 1,
    handlers = { (function()
        if not SmartHutch.sandbox:getValue("TimedDoorEnabled") then return end

        local hour = math.floor(GameTime:getInstance():getTimeOfDay())

        if hour == SmartHutch.sandbox:getValue("TimedDoorOpenHour") then
            for hutch in pairs(SmartHutch.getHutchs()) do
                SmartHutch.toggleDoor(hutch, true)
            end
            return
        end

        if hour == SmartHutch.sandbox:getValue("TimedDoorCloseHour") then
            for hutch in pairs(SmartHutch.getHutchs()) do
                SmartHutch.toggleDoor(hutch, false)
            end
            return
        end
    end) }
})

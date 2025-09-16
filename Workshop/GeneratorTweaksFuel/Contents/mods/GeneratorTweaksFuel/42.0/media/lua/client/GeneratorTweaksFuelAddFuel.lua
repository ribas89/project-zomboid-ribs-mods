require "ISUI/ISWorldObjectContextMenu"
require "TimedActions/ISBaseTimedAction"
require "GeneratorTweaksFuel"

GeneratorTweaksFuelAddFuel = ISAddFuel:derive("GeneratorTweaksFuelAddFuel")

function GeneratorTweaksFuelAddFuel:isValid()
    if self.generator:getFuel() >= GeneratorTweaksFuel.sandbox:getValue("MaxFuel") then
        ISBaseTimedAction.stop(self)
    end

    return true
end

function GeneratorTweaksFuelAddFuel:complete()
    local endFuel = 0;
    while self.fluidCont and self.fluidCont:getAmount() >= 0.098 and self.generator:getFuel() + endFuel < GeneratorTweaksFuel.sandbox:getValue("MaxFuel") do
        local amount = self.fluidCont:getAmount() - 0.1;
        self.fluidCont:adjustAmount(amount);
        endFuel = endFuel + 1;
    end

    self.petrol:syncItemFields()
    self.generator:setFuel(self.generator:getFuel() + endFuel)
    self.generator:sync()

    return true;
end

local function getFuelContainers(playerObj)
    local inventory = playerObj and playerObj:getInventory()
    if not inventory then return {} end
    local fuelContainers = {}
    local allowedFluids = GeneratorTweaksFuel.sandbox:getValue("AllowedFluids")
    local list = inventory:getAllEvalRecurse(function(item)
        local fluidContainer = item.getFluidContainer and item:getFluidContainer() or nil

        if not fluidContainer then return false end

        local primaryFluid = fluidContainer.getPrimaryFluid and fluidContainer:getPrimaryFluid() or nil
        if not primaryFluid then return false end

        local typeString = primaryFluid.getFluidTypeString and primaryFluid:getFluidTypeString() or nil
        if not typeString then return false end

        if not allowedFluids:find(typeString, 1, true) then return false end

        return true
    end)
    if list and not list:isEmpty() then
        for i = 0, list:size() - 1 do
            table.insert(fuelContainers, list:get(i))
        end
    end
    return fuelContainers
end

local function addFuelNoCap(worldobjects, generator, playerNum)
    local playerObj = getSpecificPlayer(playerNum)
    if not playerObj or not generator then return end
    if generator:getFuel() >= GeneratorTweaksFuel.sandbox:getValue("MaxFuel") then return end

    local containers = getFuelContainers(playerObj)
    for _, container in ipairs(containers) do
        ISWorldObjectContextMenu.equip(playerObj, playerObj:getPrimaryHandItem(), container, true, false)
        local amount = container:getFluidContainer():getAmount()
        ISTimedActionQueue.add(GeneratorTweaksFuelAddFuel:new(playerObj, generator, container, 70 + amount * 40))
    end
end

local function onFill(playerNum, context, worldobjects, test)
    if test then return end

    if not GeneratorTweaksFuel.sandbox:getValue("EnableAddFuelNoCap") then return end

    local generator = nil
    for _, object in ipairs(worldobjects) do
        if instanceof(object, "IsoGenerator") then
            generator = object
            break
        end
    end
    if not generator then return end

    if generator:getFuel() >= GeneratorTweaksFuel.sandbox:getValue("MaxFuel") then return end

    context:addOption(getText("Sandbox_GeneratorTweaksFuel_EnableAddFuelNoCapLabel"), worldobjects, addFuelNoCap, generator, playerNum)
end

Events.OnFillWorldObjectContextMenu.Add(onFill)

require "ISUI/ISWorldObjectContextMenu"
require "TimedActions/ISBaseTimedAction"
require "GeneratorTweaksCondition"

GeneratorTweaksConditionFixCondition = ISFixGenerator:derive("GeneratorTweaksCondition")

local function getFixITem(inventory)
    local allowedItems = GeneratorTweaksCondition.sandbox:getValue("AllowedItems")

    for i = 0, inventory:getItems():size() - 1 do
        local item = inventory:getItems():get(i)
        local itemType = item:getType()

        if allowedItems:find(itemType, 1, true) then
            local fixItem = inventory:getFirstTypeRecurse(itemType)
            return fixItem
        end
    end

    return nil
end


function GeneratorTweaksConditionFixCondition:getFixItem()
    return getFixITem(self.character:getInventory())
end

function GeneratorTweaksConditionFixCondition:isValid()
    if self.generator:getObjectIndex() == -1 then return false end

    if self.generator:getCondition() >= GeneratorTweaksCondition.sandbox:getValue("MaxCondition") then return false end

    if not self:getFixItem() then return false end

    return true
end

function GeneratorTweaksConditionFixCondition:continueFixing()
    if self.generator:getCondition() >= GeneratorTweaksCondition.sandbox:getValue("MaxCondition") then return end

    local scrapItem = self:getFixItem()
    if not scrapItem then return end

    local previousAction = self
    if scrapItem:getContainer() ~= self.character:getInventory() then
        local action = ISInventoryTransferAction:new(self.character, scrapItem, scrapItem:getContainer(), self.character:getInventory(), nil)
        ISTimedActionQueue.addAfter(self, action)
        previousAction = action
    end
    ISTimedActionQueue.addAfter(previousAction, GeneratorTweaksConditionFixCondition:new(self.character, self.generator))
end

function GeneratorTweaksConditionFixCondition:complete()
    local scrapItem = self:getFixItem()

    if not scrapItem then return false end
    self.character:removeFromHands(scrapItem)
    self.character:getInventory():Remove(scrapItem)
    sendRemoveItemFromContainer(self.character:getInventory(), scrapItem)

    self.generator:setCondition(self.generator:getCondition() + 4 + (1 * (self.character:getPerkLevel(Perks.Electricity)) / 2))
    addXp(self.character, Perks.Electricity, 5)

    if not isClient() and not isServer() then
        self:continueFixing()
    end

    return true
end

local onFixGeneratorNoCap = function(worldobjects, generator, player)
    local playerObj = getSpecificPlayer(player)

    local scrapItem = getFixITem(playerObj:getInventory())
    if not scrapItem then return end

    ISInventoryPaneContextMenu.transferIfNeeded(playerObj, scrapItem)
    ISTimedActionQueue.add(GeneratorTweaksConditionFixCondition:new(playerObj, generator))
end


local function onFill(playerNum, context, worldobjects, test)
    if test then return end

    if not GeneratorTweaksCondition.sandbox:getValue("EnableFixGeneratorNoCap") then return end

    local generator = nil
    for _, object in ipairs(worldobjects) do
        if instanceof(object, "IsoGenerator") then
            generator = object
            break
        end
    end
    if not generator then return end

    if generator:getCondition() >= GeneratorTweaksCondition.sandbox:getValue("MaxCondition") then return end

    context:addOption(getText("Sandbox_GeneratorTweaksCondition_EnableFixGeneratorNoCapLabel"), worldobjects, onFixGeneratorNoCap, generator, playerNum)
end

Events.OnFillWorldObjectContextMenu.Add(onFill)

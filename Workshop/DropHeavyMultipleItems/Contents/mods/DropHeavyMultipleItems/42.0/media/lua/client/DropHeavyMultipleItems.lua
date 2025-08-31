require("RibsFramework")

DropHeavyMultipleItems = DropHeavyMultipleItems or {}

DropHeavyMultipleItems.sandbox = RibsFramework.Sandbox:new({ ID = "DropHeavyMultipleItems", autoModOptions = true })

DropHeavyMultipleItems.itemCountArray = {}
DropHeavyMultipleItems.currentActionType = nil
DropHeavyMultipleItems.previousItems = {}

function DropHeavyMultipleItems.copyInventory(playerObject)
    local inventory = playerObject and playerObject.getInventory and playerObject:getInventory()
    if not inventory then return {} end

    local items = {}

    local list = inventory:getItems()

    for i = 0, list:size() - 1 do
        items[#items + 1] = list:get(i)
    end
    return items
end

function DropHeavyMultipleItems.isPlayerItem(itemObject)
    if itemObject:isEquipped() then return true end

    if itemObject:isFavorite() then return true end

    return false
end

function DropHeavyMultipleItems.isNewItem(itemObject)
    if DropHeavyMultipleItems.isPlayerItem(itemObject) then return false end

    for _, previousItem in ipairs(DropHeavyMultipleItems.previousItems) do
        if previousItem == itemObject then return false end
    end

    return true
end

function DropHeavyMultipleItems.shouldCount(itemObject, newItems)
    if DropHeavyMultipleItems.isPlayerItem(itemObject) then return false end

    local fullType = itemObject:getFullType()
    local alwaysDrop = DropHeavyMultipleItems.sandbox:getValue("AlwaysDropItems")
    if alwaysDrop:find(fullType, 1, true) then return true end

    if not newItems[fullType] then return false end

    local excludedItems = DropHeavyMultipleItems.sandbox:getValue("IgnoredItems")
    if excludedItems:find(fullType, 1, true) then return false end

    return true
end

function DropHeavyMultipleItems.countItem(itemObject)
    local fullType = itemObject:getFullType()

    local itemCount = DropHeavyMultipleItems.itemCountArray[fullType]
    if not itemCount then
        local newItemCount = { quantity = 1, weight = itemObject:getWeight() }
        DropHeavyMultipleItems.itemCountArray[fullType] = newItemCount
        return
    end

    itemCount.quantity = itemCount.quantity + 1
    itemCount.weight = itemCount.weight + itemObject:getWeight()
end

function DropHeavyMultipleItems.shouldDrop(itemObject)
    if DropHeavyMultipleItems.isPlayerItem(itemObject) then return false end

    local fullType = itemObject:getFullType()

    local itemCount = DropHeavyMultipleItems.itemCountArray[fullType]

    if not itemCount then return false end

    local alwaysDrop = DropHeavyMultipleItems.sandbox:getValue("AlwaysDropItems")
    if alwaysDrop:find(fullType, 1, true) then return true end

    local quantityThreshold = DropHeavyMultipleItems.sandbox:getValue("QuantityThreshold")
    if itemCount.quantity < quantityThreshold then return false end

    local weightThreshold = DropHeavyMultipleItems.sandbox:getValue("WeightThreshold")
    if itemCount.weight < weightThreshold then return false end

    return true
end

function DropHeavyMultipleItems.dropItem(playerObject, itemObject)
    playerObject:getInventory():Remove(itemObject)
    playerObject:getSquare():AddWorldInventoryItem(itemObject, 0.5, 0.5, 0)
end

function DropHeavyMultipleItems.scanForItemsToDrop(playerObject)
    local ignoredActions = DropHeavyMultipleItems.sandbox:getValue("IgnoredActions")

    if ignoredActions:find(DropHeavyMultipleItems.currentActionType, 1, true) then return end

    local inventory = DropHeavyMultipleItems.copyInventory(playerObject)
    if #inventory == 0 then return end

    local newItems = {}
    for _, itemObject in ipairs(inventory) do
        if DropHeavyMultipleItems.isNewItem(itemObject) then
            local fullType = itemObject:getFullType()
            newItems[fullType] = true
        end
    end

    for _, itemObject in ipairs(inventory) do
        if DropHeavyMultipleItems.shouldCount(itemObject, newItems) then
            DropHeavyMultipleItems.countItem(itemObject)
        end
    end

    for _, itemObject in ipairs(inventory) do
        if DropHeavyMultipleItems.shouldDrop(itemObject) then
            DropHeavyMultipleItems.dropItem(playerObject, itemObject)
        end
    end
end

function DropHeavyMultipleItems.checkStartedAction(playerObject)
    local isDoingAction = ISTimedActionQueue.isPlayerDoingAction(playerObject)

    if not isDoingAction then return end

    if DropHeavyMultipleItems.currentActionType then return end

    local queue = ISTimedActionQueue.getTimedActionQueue(playerObject)

    DropHeavyMultipleItems.currentActionType = queue and queue.current and queue.current.Type
    DropHeavyMultipleItems.itemCountArray = {}
end

function DropHeavyMultipleItems.checkFinishedAction(playerObject)
    local isDoingAction = ISTimedActionQueue.isPlayerDoingAction(playerObject)

    if isDoingAction then return end

    if not DropHeavyMultipleItems.currentActionType then return end

    DropHeavyMultipleItems.scanForItemsToDrop(playerObject)

    DropHeavyMultipleItems.currentActionType = nil
    DropHeavyMultipleItems.itemCountArray = {}
    DropHeavyMultipleItems.previousItems = DropHeavyMultipleItems.copyInventory(playerObject)
end

function DropHeavyMultipleItems.onPlayerUpdate(playerObject)
    if playerObject:isDead() then return end

    DropHeavyMultipleItems.checkStartedAction(playerObject)
    DropHeavyMultipleItems.checkFinishedAction(playerObject)
end

Events.OnPlayerUpdate.Add(DropHeavyMultipleItems.onPlayerUpdate)

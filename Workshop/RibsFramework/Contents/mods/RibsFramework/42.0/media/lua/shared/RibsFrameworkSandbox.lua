function RibsFramework.Sandbox:new(args)
    args = args or {}

    local instance = setmetatable({}, self)

    instance.ID = args.ID or ""
    instance.autoModOptions = args.instances and true or args.autoModOptions or false

    instance.customOptions = args.customOptions or ""

    instance.autoModShowOption = args.autoModShowOption or "EnableModOptions"

    instance.instances = args.instances or {}
    instance.modOptions = instance.instances.modOptions or RibsFramework.ModOptions:new(args)
    instance.classVersion = instance.instances.classVersion or RibsFramework.ClassVersion:new({
        ID = instance.ID,
        instances = {
            modOptions = instance.modOptions
        }
    })

    instance.javaClassVersion = args.javaClassVersion

    instance.javaClassesVersions = args.javaClassesVersions or {}

    instance.modOptionApplyHandlers = {}

    instance.onGameStart = args.onGameStart or function()
        instance:autoSandboxToModOptions()
    end
    Events.OnGameStart.Add(instance.onGameStart)

    instance.OnGameTimeLoaded = args.OnGameTimeLoaded or function()
        instance:generateModOptions()
    end
    Events.OnGameTimeLoaded.Add(instance.OnGameTimeLoaded)

    return instance
end

function RibsFramework.Sandbox:getOption(optionName)
    local fullOptionName = optionName
    if self.ID ~= "" and not optionName:find(self.ID, 1, true) and not self.customOptions:find(optionName, 1, true) then
        fullOptionName = self.ID .. "." .. optionName
    end

    local option = getSandboxOptions():getOptionByName(fullOptionName)
    if not option then
        print("BP2Fmw.Sandbox: getSandboxOptions():getOptionByName('" .. fullOptionName .. "') not found")
        return nil
    end

    return option
end

function RibsFramework.Sandbox:getValue(optionName)
    local sandboxOption = self:getOption(optionName)
    if not sandboxOption then return nil end
    return sandboxOption:getValue()
end

function RibsFramework.Sandbox:setValue(optionName, newValue)
    local sandboxOption = self:getOption(optionName)
    if not sandboxOption then return nil end
    sandboxOption:setValue(newValue)
end

function RibsFramework.Sandbox:castTypeValue(optionName, value)
    local sandboxOption = self:getOption(optionName)
    if not sandboxOption then return nil end
    local sandboxValue = sandboxOption:getValue()

    local configOption = sandboxOption:asConfigOption()
    local name = configOption:getName()
    local typeString = configOption:getType()

    local numberTypes = {
        integer = true,
        double = true,
        float = true,
        enum = true,
    }

    if typeString == "boolean" then
        local stringValue = tostring(value):lower()
        local booleanValue = (stringValue == "true" or stringValue == "1")
        return { forSandbox = booleanValue, forModOptions = booleanValue }
    end

    if numberTypes[typeString] then
        local castedValue = tonumber(value)
        if not castedValue then
            print("BP2Fmw.Sandbox: could not cast value for option '" .. name .. "' using sandbox value")
            castedValue = sandboxValue
        end
        return { forSandbox = castedValue, forModOptions = tostring(castedValue) }
    end

    if typeString == "array" then
        local arrayValue = value

        if type(value) == "string" then
            arrayValue = {}
            for item in value:gmatch("[^,]+") do
                table.insert(arrayValue, item:match("^%s*(.-)%s*$"))
            end
        end

        if type(arrayValue) ~= "table" then
            print("BP2Fmw.Sandbox: could not cast value for option '" .. name .. "' to array, using sandbox value")
            arrayValue = sandboxValue
        end

        return { forSandbox = arrayValue, forModOptions = configOption:getValueAsString() }
    end

    if typeString == "string" then return { forSandbox = tostring(value), forModOptions = tostring(value) } end
    print("BP2Fmw.Sandbox: unknown type '" .. tostring(typeString) .. "' for option '" .. name .. "', using sandbox value")

    return { forSandbox = sandboxValue, forModOptions = configOption:getValueAsString() }
end

function RibsFramework.Sandbox:isAutoModOptions()
    if not self.autoModOptions then return false end

    if not self.modOptions:isModOptionsAvailable() then return false end

    if not self:getValue(self.autoModShowOption) then return false end

    if not self.classVersion:isInstalled() then return false end

    return true
end

function RibsFramework.Sandbox:checkChange(optionName)
    local sandboxValue = self:getValue(optionName)
    local modOptionsValue = self.modOptions:getModOption(optionName):getValue()

    local isChanged = tostring(sandboxValue) ~= tostring(modOptionsValue)

    return { isChanged = isChanged, sandboxValue = sandboxValue, modOptionsValue = modOptionsValue }
end

function RibsFramework.Sandbox:modOptionsToSandbox(optionName)
    local modOption = self.modOptions:getModOption(optionName)

    local castedValue = self:castTypeValue(optionName, modOption:getValue())
    self:setValue(optionName, castedValue.forSandbox)
end

function RibsFramework.Sandbox:sandboxToModOptions(optionName)
    local modOption = self.modOptions:getModOption(optionName)

    local castedValue = self:castTypeValue(optionName, self:getValue(optionName))
    modOption:setValue(castedValue.forModOptions)
end

function RibsFramework.Sandbox:getNewOption(option)
    local config = option:asConfigOption()
    local name = config:getName()

    if not (name:find(self.ID, 1, true) or self.customOptions:find(name, 1, true)) then return end

    if name:find(self.autoModShowOption, 1, true) then return end

    local newItem = {
        name = name,
        translatedName = option:getTranslatedName(),
        translatedTooltip = option:getTooltip(),
        value = option:getValue(),
        typeString = config:getType(),
        valueAsString = config:getValueAsString(),
        option = option,
        config = config
    }

    return newItem
end

function RibsFramework.Sandbox:getSandboxOptions()
    local allSandboxOptions = getSandboxOptions()
    local sandboxOptionsMod = {}
    local count = allSandboxOptions:getNumOptions()

    for i = 0, count - 1 do
        local option = allSandboxOptions:getOptionByIndex(i)

        local newOption = self:getNewOption(option)
        if newOption then
            sandboxOptionsMod[#sandboxOptionsMod + 1] = newOption
        end
    end

    return sandboxOptionsMod
end

function RibsFramework.Sandbox:autoSandboxToModOptions()
    if not self:isAutoModOptions() then return end

    local options = self:getSandboxOptions()
    for i = 1, #options do
        self:sandboxToModOptions(options[i].name)
    end
end

function RibsFramework.Sandbox:sendToServer()
    if not isClient() then return end
    getSandboxOptions():sendToServer()
end

function RibsFramework.Sandbox:autoModOptionsToSandbox()
    if not self:isAutoModOptions() then return end

    local options = self:getSandboxOptions()
    for i = 1, #options do
        self:modOptionsToSandbox(options[i].name)
    end

    self:sendToServer()
end

function RibsFramework.Sandbox:translationFromName(name, suffix)
    local option = name:match("^[^.]+%.(.+)$")

    if not option then return nil end

    local translationKey = "Sandbox_" .. self.ID .. "_" .. option .. suffix

    return getTextOrNull(translationKey)
end

function RibsFramework.Sandbox:generateModOptions()
    if not self.classVersion:modOptionsCheckDependencies(self.modOptions, self.ID) then return end

    if not self:isAutoModOptions() then return end

    if self.javaClassVersion and not self.classVersion:modOptionsVersionCheck(self.javaClassVersion) then return end

    local options = self:getSandboxOptions()

    self.modOptions:generateModOptions(options)

    self.modOptions:addApplyHandler(function() self:autoModOptionsToSandbox() end)

    return self.modOptions
end

function RibsFramework.Sandbox:new(args)
    args = args or {}

    local instance = setmetatable({}, self)

    instance.ID = args.ID or ""
    instance.autoModOptions = args.autoModOptions or false

    instance.modOptions = args.modOptions or false

    instance.modOptionApplyHandlers = {}


    Events.OnGameStart.Add(function()
        instance:autoSandboxToModOptions()
    end)

    instance:generateModOptions()

    return instance
end

function RibsFramework.Sandbox:getOption(optionName)

    local fullOptionName = optionName
    if self.ID ~= "" and not string.find(optionName, self.ID, 1, true) then
        fullOptionName = self.ID .. "." .. optionName
    end

    local option = getSandboxOptions():getOptionByName(fullOptionName)
    if not option then
        error("BP2Fmw.Sandbox: getSandboxOptions():getOptionByName('" .. fullOptionName .. "') not found")
    end

    return option
end

function RibsFramework.Sandbox:getValue(optionName)
    local sandboxOption = self:getOption(optionName)
    return sandboxOption:getValue()
end

function RibsFramework.Sandbox:setValue(optionName, newValue)
    local sandboxOption = self:getOption(optionName)
    sandboxOption:setValue(newValue)
end

function RibsFramework.Sandbox:getModOption(optionName)
    local options = PZAPI.ModOptions:getOptions(self.ID)
    if not options then
        error("BP2Fmw.Sandbox: PZAPI.ModOptions:getOptions('" .. self.ID .. "') not found")
    end

    local modOption = options:getOption(optionName)
    if not modOption then
        error("BP2Fmw.Sandbox: options:getOption('" .. optionName .. "') not found")
    end

    return modOption
end

function RibsFramework.Sandbox:castTypeValue(optionName, value)
    local sandboxOption = self:getOption(optionName)
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
            for item in string.gmatch(value, "[^,]+") do
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
    print("BP2Fmw.Sandbox: unknown type '" ..
    tostring(typeString) .. "' for option '" .. name .. "', using sandbox value")

    return { forSandbox = sandboxValue, forModOptions = configOption:getValueAsString() }
end

function RibsFramework.Sandbox:checkChange(optionName)
    if not (PZAPI and PZAPI.ModOptions) then return { isChanged = false } end

    local sandboxValue = self:getValue(optionName)
    local modOptionsValue = self:getModOption(optionName):getValue()

    local isChanged = tostring(sandboxValue) ~= tostring(modOptionsValue)

    return { isChanged = isChanged, sandboxValue = sandboxValue, modOptionsValue = modOptionsValue }
end

function RibsFramework.Sandbox:modOptionsToSandbox(optionName)
    local modOption = self:getModOption(optionName)

    local castedValue = self:castTypeValue(optionName, modOption:getValue())
    self:setValue(optionName, castedValue.forSandbox)
end

function RibsFramework.Sandbox:sandboxToModOptions(optionName)
    local modOption = self:getModOption(optionName)

    local castedValue = self:castTypeValue(optionName, self:getValue(optionName))
    modOption:setValue(castedValue.forModOptions)
end

function RibsFramework.Sandbox:getSandboxOptions()
    local allSandboxOptions = getSandboxOptions()
    local sandboxOptionsMod = {}
    local count = allSandboxOptions:getNumOptions()

    for i = 0, count - 1 do
        local option = allSandboxOptions:getOptionByIndex(i)
        local config = option:asConfigOption()
        local name = config:getName()

        if string.find(name, self.ID, 1, true) then
            local index = #sandboxOptionsMod + 1
            sandboxOptionsMod[index] = {
                name = name,
                translatedName = option:getTranslatedName(),
                translatedTooltip = option:getTooltip(),
                value = option:getValue(),
                typeString = config:getType(),
                option = option,
                config = config
            }
        end
    end

    return sandboxOptionsMod
end

function RibsFramework.Sandbox:autoSandboxToModOptions()
    if not (PZAPI and PZAPI.ModOptions) then return {} end
    if not self.autoModOptions then return end

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
    if not (PZAPI and PZAPI.ModOptions) then return {} end

    local options = self:getSandboxOptions()
    for i = 1, #options do
        self:modOptionsToSandbox(options[i].name)
    end

    self:sendToServer()
end

function RibsFramework.Sandbox:createModOptionFromSandbox(data)
    local textTypes = {
        integer = true,
        double = true,
        string = true,
        float = true,
        enum = true,
    }

    if textTypes[data.typeString] then
        self.modOptions:addTextEntry(data.name, data.translatedName, data.config:getValueAsString(),
            data.translatedTooltip)
    end

    if data.typeString == "boolean" then
        self.modOptions:addTickBox(data.name, data.translatedName, data.config:getValue(), data.translatedTooltip)
    end
end

function RibsFramework.Sandbox:isModOptionsEnabled()
    if (isClient() and not getCore():isDedicated()) then return false end
    if getCore():isDedicated() then return false end
    if not (PZAPI and PZAPI.ModOptions) then return false end
    return true
end

function RibsFramework.Sandbox:generateModOptions()
    if not self:isModOptionsEnabled() then return nil end

    if not self.autoModOptions then return nil end

    if not self.modOptions then
        self.modOptions = PZAPI.ModOptions:create(self.ID, getText("Sandbox_" .. self.ID))
    end

    local options = self:getSandboxOptions()

    for i = 1, #options do
        self:createModOptionFromSandbox(options[i])
    end

    self.modOptions.apply = (function() self:autoModOptionsToSandbox() end)

    return self.modOptions
end

function RibsFramework.ModOptions:new(args)
    args = args or {}

    local instance = setmetatable({}, self)

    instance.ID = args.ID or ""

    instance.modOptions = args.modOptions or instance:create()

    instance.maxDescriptions = args.maxDescriptions or 10

    instance.maxTitles = args.maxTitles or 10

    instance.customOptions = args.customOptions or ""

    instance.applyHandlers = {}

    return instance
end

function RibsFramework.ModOptions:getModOptions()
    return self.modOptions
end

function RibsFramework.ModOptions:addDescription(...)
    return self.modOptions:addDescription(...)
end

function RibsFramework.ModOptions:addTitle(...)
    return self.modOptions:addTitle(...)
end

function RibsFramework.ModOptions:isModOptionsAvailable()
    if (isClient() and not getCore():isDedicated()) then return false end
    if getCore():isDedicated() then return false end
    if not (PZAPI and PZAPI.ModOptions) then return false end
    return true
end

function RibsFramework.ModOptions:create()
    if not self:isModOptionsAvailable() then return nil end

    if not self.modOptions then
        self.modOptions = PZAPI.ModOptions:create(self.ID, getText("Sandbox_" .. self.ID))
    end

    self.modOptions.apply = (function()
        for _, handler in ipairs(self.applyHandlers) do
            handler()
        end
    end)

    return self.modOptions
end

function RibsFramework.ModOptions:translationFromName(name, suffix)
    local option = name:match("^[^.]+%.(.+)$")

    if not option then return nil end

    local translationKey = "Sandbox_" .. self.ID .. "_" .. option .. suffix

    return getTextOrNull(translationKey)
end

function RibsFramework.ModOptions:createModOptionFromSandbox(data)
    local textTypes = {
        integer = true,
        double = true,
        string = true,
        float = true,
        enum = true,
    }

    for i = 1, self.maxTitles do
        local title = self:translationFromName(data.name, "_mtitle" .. i)
        if title then self.modOptions:addTitle(title) end
    end

    for i = 1, self.maxDescriptions do
        local desc = self:translationFromName(data.name, "_mdesc" .. i)
        if desc then self.modOptions:addDescription(desc) end
    end

    if textTypes[data.typeString] then
        self.modOptions:addTextEntry(data.name, data.translatedName, data.valueAsString, data.translatedTooltip)
    end

    if data.typeString == "boolean" then
        self.modOptions:addTickBox(data.name, data.translatedName, data.value, data.translatedTooltip)
    end
end

function RibsFramework.ModOptions:generateMixModOptions(options)
    local customOptionsTable = {}
    local currentModOptions = {}
    for i = 1, #options do
        local option = options[i]
        if self.customOptions:find(option.name, 1, true) then
            table.insert(customOptionsTable, option)
        else
            table.insert(currentModOptions, option)
        end
    end

    self.modOptions:addTitle("UI_RibsFramework_Custom_Vanilla_Options_title")
    self.modOptions:addDescription("UI_RibsFramework_Custom_Vanilla_Options_desc")
    for i = 1, #customOptionsTable do
        self:createModOptionFromSandbox(customOptionsTable[i])
    end

    self.modOptions:addTitle("UI_RibsFramework_Custom_CurrentMod_Options_title")
    for i = 1, #currentModOptions do
        self:createModOptionFromSandbox(currentModOptions[i])
    end
end

function RibsFramework.ModOptions:generateModOptions(options)
    if self.customOptions ~= "" then
        self:generateMixModOptions(options)
    else
        for i = 1, #options do
            self:createModOptionFromSandbox(options[i])
        end
    end
end

function RibsFramework.ModOptions:getModOption(optionName)
    local options = PZAPI.ModOptions:getOptions(self.ID)
    if not options then
        error("RibsFramework.ModOptions: PZAPI.ModOptions:getOptions('" .. self.ID .. "') not found")
    end

    local modOption = options:getOption(optionName)

    if not modOption then
        error("RibsFramework.ModOptions: options:getOption('" .. optionName .. "') not found")
    end

    return modOption
end

function RibsFramework.ModOptions:addApplyHandler(handler)
    table.insert(self.applyHandlers, handler)
end

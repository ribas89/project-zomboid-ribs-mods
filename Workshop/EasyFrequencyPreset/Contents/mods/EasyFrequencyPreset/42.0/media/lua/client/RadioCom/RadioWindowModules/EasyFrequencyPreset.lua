EasyFrequencyPreset = {}

EasyFrequencyPreset.sandbox = RibsFramework.Sandbox:new({
    ID = "EasyFrequencyPreset",
    autoModOptions = true,
})

Events.OnGameStart.Add(function()
    if not (RWMSubEditPreset and RWMSubEditPreset.createChildren) then return end

    local FONT_HGT_SMALL = getTextManager():getFontHeight(UIFont.Small)
    local BUTTON_HGT = FONT_HGT_SMALL + 6
    local UI_BORDER_SPACING = 10

    local zomboidRadio = getZomboidRadio()
    local unknownChannel = getText("IGUI_RadioUknownChannel")
    local fullChannelList = transformIntoKahluaTable(zomboidRadio:getFullChannelList())

    function RWMSubEditPreset:channelNameFromText(textValue)
        if not textValue then return end

        local frequencyInt = math.floor(tonumber(textValue) * 1000 + 0.5)
        local channelName = zomboidRadio:getChannelName(frequencyInt) or unknownChannel
        self.entryName:setText(channelName)
    end

    local originalCreateChildren = RWMSubEditPreset.createChildren
    function RWMSubEditPreset:createChildren(...)
        if (EasyFrequencyPreset.sandbox:getValue("EnableInputSelect")) then
            self.comboBoxFrequency = ISComboBox:new(0, UI_BORDER_SPACING + 1, self.width, BUTTON_HGT, self, RWMSubEditPreset.comboChange)
            self.comboBoxFrequency:initialise()


            self:addChild(self.comboBoxFrequency)
            self:addLinePair(getText("IGUI_RadioSelectChannel"), self.comboBoxFrequency)


            local allowedStations = EasyFrequencyPreset.sandbox:getValue("ListRadios")

            local stations = {}
            for stationName in string.gmatch(allowedStations, "([^;]+)") do
                local station = fullChannelList[stationName]

                if station then
                    for key, value in pairs(transformIntoKahluaTable(station)) do
                        local numberFrequency = tonumber(tostring(key))
                        local freq = numberFrequency / 1000
                        local label = string.format("%.1f %s", freq, value)
                        table.insert(stations, { label = label, freq = freq })
                    end
                end
            end

            table.sort(stations, function(a, b) return a.freq < b.freq end)
            for _, station in ipairs(stations) do
                self.comboBoxFrequency:addOptionWithData(station.label, station.freq)
            end
        end

        if (EasyFrequencyPreset.sandbox:getValue("EnableInputText")) then
            self.frequencyTextBox = ISTextEntryBox:new("", 0, UI_BORDER_SPACING + 1, self.width, BUTTON_HGT)
            self:addChild(self.frequencyTextBox)
            self:addLinePair(getText("IGUI_RadioFrequency"), self.frequencyTextBox)

            self.frequencyTextBox.onCommandEntered = function(textBox)
                local textInputValue = textBox:getText()

                if not textInputValue then return end

                self:channelNameFromText(textInputValue)
                self.frequencySlider:setCurrentValue(textInputValue)
            end
        end

        originalCreateChildren(self, ...)
    end

    local originalSliderChange = RWMSubEditPreset.onSliderChange
    function RWMSubEditPreset:onSliderChange(...)
        originalSliderChange(self, ...)

        if (EasyFrequencyPreset.sandbox:getValue("EnableInputText")) then
            self.frequencyTextBox:setText(tostring(...))
        end
        self:channelNameFromText(tostring(...))
    end

    function RWMSubEditPreset:comboChange(comboBox)
        local optionIndex = comboBox.selected
        if optionIndex <= 0 then return end
        local frequency = comboBox:getOptionData(optionIndex)

        self.frequencySlider:setCurrentValue(frequency)
        if (EasyFrequencyPreset.sandbox:getValue("EnableInputText")) then
            self.frequencyTextBox:setText(tostring(frequency))
        end
        self:channelNameFromText(frequency)
    end
end)

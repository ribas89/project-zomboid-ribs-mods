Events.OnGameStart.Add(function()
    if not (RWMSubEditPreset and RWMSubEditPreset.createChildren) then return end

    local FONT_HGT_SMALL = getTextManager():getFontHeight(UIFont.Small)
    local BUTTON_HGT = FONT_HGT_SMALL + 6
    local UI_BORDER_SPACING = 10

    local zomboidRadio = getZomboidRadio()
    local unknownChannel = getText("IGUI_RadioUknownChannel")
    local stations = transformIntoKahluaTable(zomboidRadio:getFullChannelList())

    function RWMSubEditPreset:channelNameFromText(textValue)
        if not textValue then return end

        local frequencyInt = math.floor(tonumber(textValue) * 1000 + 0.5)
        local channelName = zomboidRadio:getChannelName(frequencyInt) or unknownChannel
        self.entryName:setText(channelName)
    end

    local originalCreateChildren = RWMSubEditPreset.createChildren
    function RWMSubEditPreset:createChildren(...)
        self.comboBoxFrequency = ISComboBox:new(0, UI_BORDER_SPACING + 1, self.width, BUTTON_HGT, self, RWMSubEditPreset.comboChange)
        self.comboBoxFrequency:initialise()
        self:addChild(self.comboBoxFrequency)
        self:addLinePair(nil, self.comboBoxFrequency)

        local function forEachStation(station)
            for key, value in pairs(transformIntoKahluaTable(station)) do
                local numberFrequency = tonumber(tostring(key))
                local mhzFrequency = numberFrequency / 1000
                local optionLabel = string.format("%.1f %s", mhzFrequency, value)
                self.comboBoxFrequency:addOptionWithData(optionLabel, mhzFrequency)
            end
        end
        forEachStation(stations.Amateur)
        forEachStation(stations.Radio)


        self.frequencyTextBox = ISTextEntryBox:new("", 0, UI_BORDER_SPACING + 1, self.width, BUTTON_HGT)
        self:addChild(self.frequencyTextBox)
        self:addLinePair(getText("IGUI_RadioFrequency"), self.frequencyTextBox)
        originalCreateChildren(self, ...)


        self.frequencyTextBox.onCommandEntered = function(textBox)
            local textInputValue = textBox:getText()

            if not textInputValue then return end

            self:channelNameFromText(textInputValue)
            self.frequencySlider:setCurrentValue(textInputValue)
        end
    end

    local originalSliderChange = RWMSubEditPreset.onSliderChange
    function RWMSubEditPreset:onSliderChange(...)
        originalSliderChange(self, ...)

        self.frequencyTextBox:setText(tostring(...))
        self:channelNameFromText(tostring(...))
    end

    function RWMSubEditPreset:comboChange(comboBox)
        local optionIndex = comboBox.selected
        if optionIndex <= 0 then return end
        local frequency = comboBox:getOptionData(optionIndex)

        self.frequencySlider:setCurrentValue(frequency)
        self.frequencyTextBox:setText(tostring(frequency))
        self:channelNameFromText(frequency)
    end
end)

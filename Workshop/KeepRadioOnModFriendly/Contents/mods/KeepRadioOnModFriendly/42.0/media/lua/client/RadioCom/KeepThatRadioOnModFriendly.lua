Events.OnGameStart.Add(function()
    if not (ISRadioWindow and ISRadioWindow.update) then return end

    local originalUpdate = ISRadioWindow.update

    function ISRadioWindow:update(...)
        local deviceData = nil

        if (self.deviceData and self.deviceData.getIsTurnedOn and self.deviceData:getIsTurnedOn()) then
            deviceData = self.deviceData
        end

        originalUpdate(self, ...)

        if not (deviceData and deviceData.getIsTurnedOn and deviceData.setIsTurnedOn) then return end

        if deviceData:getIsTurnedOn() then return end

        deviceData:setIsTurnedOn(true)
    end
end)

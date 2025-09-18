Events.OnTick.Add(function()
    if DeviceData and DeviceData.updateAllEmitters then
        DeviceData.updateAllEmitters()
    end
end)

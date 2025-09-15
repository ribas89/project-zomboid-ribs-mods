UALBroadcastVoicer = UALBroadcastVoicer or {}

UALBroadcastVoicer.sandbox = RibsFramework.Sandbox:new({
    ID = "UALBroadcastVoicer",
    instances = {
        classVersion = RadioTVCore.classVersion
    }
})

if not RadioTVCore.classVersion:isInstalled() then return end

local RadioWavs = require "RadioCom/RadioWavs"

Events.OnTick.Remove(RadioWavs.adjustSounds)
local minRange = 5
local maxRange = 50
local p = nil
local X = 0
local Y = 0
local dropoffRange = 0
local volumeModifier = 0
local distanceToRadio = 0
local finalVolume = 0
local tickCounter1 = 0
local tickCounter2 = 0
RadioWavs.adjustSounds = function()
    -- TODO: tickrates depend on framerate. find something time-based instead
    if tickCounter2 < 1000 then
        tickCounter2 = tickCounter2 + 1
    else
        p = getPlayer()
        X = p:getX()
        Y = p:getY()
        --attract zombies
        for _, t in ipairs(RadioWavs.soundCache) do
            if RadioWavs.isPlaying(t) and t.deviceData:getHeadphoneType() == -1 and t.device == t.deviceData:getParent() then
                local range = t.deviceData:getDeviceVolume() * t.sound.volumeModifier * 2.5 * maxRange
                if t.deviceData:isInventoryDevice() or t.deviceData:isVehicleDevice() then
                    addSound(p, X, Y, p:getZ(), range / 4, range / 2)
                else
                    addSound(t.device, t.device:getX() + 0.5, t.device:getY() + 0.5, t.device:getZ(), range / 4, range / 2)
                end
            end
        end
        tickCounter2 = 0
    end
    if tickCounter1 < 5 then
        tickCounter1 = tickCounter1 + 1
        return
    end
    tickCounter1 = 0


    p = getPlayer()
    X = p:getX()
    Y = p:getY()
    highestVolume = 0
    for _, t in ipairs(RadioWavs.soundCache) do
        -- Play Queue
        t.sound:playQueue()
        -- sync states
        if t.sound and t.sound:isPlaying() then
            if not t.deviceData:isVehicleDevice() and t.device ~= t.deviceData:getParent() then
                -- device object changed, this happens when the player picks up or places objects. no idea what's up with car radios
                t.device = t.deviceData:getParent() -- update our device reference
                if t.deviceData:isInventoryDevice() then
                    t.sound:set3D(false)
                else
                    t.sound:setPosAtObject(t.device)
                end
            end
            if not t.deviceData:getIsTurnedOn() and not t.muted then -- device was switched off without player action
                t.muted = true                                       -- sync sound accordingly
                RadioWavs.updateVolume(t)
            end
        end
        --adjust volume based on distance
        if RadioWavs.isPlaying(t) then
            if t.deviceData:isInventoryDevice() then
                highestVolume = 1
            else
                distanceToRadio = IsoUtils.DistanceManhatten(t.device:getX(), t.device:getY(), X, Y)
                if distanceToRadio < maxRange then
                    dropoffRange = (maxRange - minRange) * 0.2 +
                        t.deviceData:getDeviceVolume() * t.sound.volumeModifier * 2.5 * (maxRange - minRange) *
                        0.8
                    volumeModifier = (minRange + dropoffRange - distanceToRadio) / dropoffRange
                    if volumeModifier < 0 then volumeModifier = 0 end
                    t.sound:setVolume(t.deviceData:getDeviceVolume() * volumeModifier)
                    finalVolume = t.deviceData:getDeviceVolume() * t.sound.volumeModifier * volumeModifier
                    if finalVolume > highestVolume then highestVolume = finalVolume end
                end
            end
        end
    end
    --adjust Zomboid music volume
    local optionsVolume = getCore():getOptionMusicVolume() / 10
    local optionsVolumeModified = optionsVolume - optionsVolume * highestVolume * 10
    if optionsVolumeModified < 0 then optionsVolumeModified = 0 end
    getSoundManager():setMusicVolume(optionsVolumeModified)
end

Events.OnTick.Add(RadioWavs.adjustSounds)

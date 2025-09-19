InternetRadio = {}

InternetRadio.stations = {}

InternetRadio.modOptions = RibsFramework.ModOptions:new({
    ID = "InternetRadio"
})

InternetRadio.classVersion = RibsFramework.ClassVersion:new({
    ID = "InternetRadio",
    instances = InternetRadio,
    classesData = {
        FMODSoundEmitter = { name = "ribsVersionFMODSoundEmitter", version = "1.0.1" }
    }
})

InternetRadio.sandbox = RibsFramework.Sandbox:new({
    ID = "InternetRadio",
    instances = InternetRadio
})


function InternetRadio:addStation(frequencyMHz, name, url)
    local freq = math.floor((frequencyMHz * 1000) / 200 + 0.5) * 200

    for _, station in ipairs(self.stations) do
        if station.frequency == freq then
            return self:addStation(frequencyMHz + 0.2, name, url)
        end
    end

    table.insert(self.stations, { frequency = freq, name = name, url = url })
end

function InternetRadio:getUrlForFrequency(freqKHz)
    for _, station in ipairs(self.stations) do
        if station.frequency == freqKHz then
            return station.url
        end
    end
    return nil
end

function InternetRadio:loadFromString(stationsString)
    for entry in stationsString:gmatch("[^;]+") do
        local freq, name, url = entry:match("([^|]+)|([^|]+)|([^|]+)")
        if freq and name and url then
            self:addStation(tonumber(freq), name, url)
        end
    end
end

function InternetRadio:OnGameTimeLoaded()
    local zomboidRadio = getZomboidRadio()
    if not zomboidRadio then return end

    self:addStation(95.1, "Walking In the Old Paths", "https://s4.yesstreaming.net:7119/;audio.mp3?hash=1538009522657")

    local stationsString = InternetRadio.sandbox:getValue("CustomStations")

    self:loadFromString(stationsString)

    for _, station in ipairs(InternetRadio.stations) do
        zomboidRadio:addChannelName(station.name, station.frequency, "Radio", true)
    end
end

Events.OnGameTimeLoaded.Add(function()
    InternetRadio:OnGameTimeLoaded()
end)

local originalOnCompleted = ISTimedActionQueue.onCompleted
function ISTimedActionQueue:onCompleted(...)
    originalOnCompleted(self, ...)

    local args = { ... }
    local action = args[1]
    if not action then return end

    local actionType = action.Type
    if actionType ~= "ISRadioAction" then return end

    local currentMode = action.mode

    local secondaryItem = action.secondaryItem
    if not secondaryItem then return end

    local deviceData = action.deviceData
    if not deviceData then return end

    local emitter = deviceData:getEmitter()
    if not emitter then return end

    if currentMode == "SetChannel" then
        emitter:stopAll()
        deviceData:setChannelRaw(secondaryItem)
        local url = InternetRadio:getUrlForFrequency(secondaryItem)
        if not url then return end
        return deviceData:playSound(url, deviceData:getDeviceVolume(), true)
    end

    if currentMode == "MuteVolume" then
        return emitter:setVolumeAll(0)
    end

    if currentMode == "UnMuteVolume" or currentMode == "SetVolume" then
        return emitter:setVolumeAll(secondaryItem)
    end
end

Events.OnGameStart.Add(function()
    if not (ISRadioWindow and ISRadioWindow.readFromObject) then return end

    local originalReadFromObject = ISRadioWindow.readFromObject
    function ISRadioWindow:readFromObject(_player, _deviceObject)
        local deviceData = _deviceObject and _deviceObject.getDeviceData and _deviceObject:getDeviceData();

        if deviceData and deviceData.setMaxChannelRange then
            local maxFrequency = InternetRadio.sandbox:getValue("MaxFrequency")
            local minFrequency = InternetRadio.sandbox:getValue("MinFrequency")
            deviceData:setMaxChannelRange(maxFrequency * 1000)
            deviceData:setMinChannelRange(minFrequency * 1000)
        end

        originalReadFromObject(self, _player, _deviceObject)
    end
end)

Events.OnGameStart.Add(function()
    if not (RWMSubEditPreset and RWMSubEditPreset.createChildren) then return end

    local originalSetValues = RWMSubEditPreset.setValues
    function RWMSubEditPreset:setValues(...)
        originalSetValues(self, ...)

        if not self.frequencySlider then return end
        self.frequencySlider.maxValue = InternetRadio.sandbox:getValue("MaxFrequency")
        self.frequencySlider.minValue = InternetRadio.sandbox:getValue("MinFrequency")
    end
end)

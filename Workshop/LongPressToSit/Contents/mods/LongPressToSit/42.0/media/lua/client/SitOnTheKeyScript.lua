local LongPressToSitOptions = PZAPI.ModOptions:create("LongPressToSit", "Long Press to sit")
local LongPressToSitKeyBind = LongPressToSitOptions:addKeyBind("sit", "hotkey", Keyboard.KEY_C, "")
local LongPressToSitDelay = LongPressToSitOptions:addTextEntry("LongPressDelay", "delay", "500")
local LongPressToSitStartTime = nil

local function LongPressToSitStart(key)
    if key ~= LongPressToSitKeyBind.key then return end

	local player = getSpecificPlayer(0)
	if player and player:isSitOnGround() then
		LongPressToSitStartTime = nil
		return
	end

    LongPressToSitStartTime = getTimestampMs()
end


local function LongPressToSitKeep(key)
    if key ~= LongPressToSitKeyBind.key then return end
	if not LongPressToSitStartTime then return end

	local LongPressToSitTimeElapsed = (getTimestampMs() - LongPressToSitStartTime)
	local LongPressToSitDelayNumber = tonumber(LongPressToSitDelay:getValue())
	if LongPressToSitTimeElapsed < LongPressToSitDelayNumber then return end

	local player = getSpecificPlayer(0)
	if player and player:isSitOnGround() then
		LongPressToSitStartTime = nil
		return
	end
	
	if player and player:isSneaking() then
        player:setSneaking(false)
    end

	player:reportEvent("EventSitOnGround")
	LongPressToSitStartTime = nil
end

local function LongPressToSitRelease(key)
    if key ~= LongPressToSitKeyBind.key then return end
    LongPressToSitStartTime = nil
end

Events.OnKeyStartPressed.Add(LongPressToSitStart)
Events.OnKeyKeepPressed.Add(LongPressToSitKeep)
Events.OnCustomUIKeyReleased.Add(LongPressToSitRelease)
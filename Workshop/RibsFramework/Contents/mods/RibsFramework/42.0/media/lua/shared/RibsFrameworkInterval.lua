function RibsFramework.Interval:new(args)
    args = args or {}

    local instance = setmetatable({}, self)

    instance.interval = args.interval or 10
    instance.start = args.start or 0

    instance.multipliers = args.multipliers or {
        seconds = 1000,
        minutes = 1000 * 60,
        hours = 1000 * 60 * 60,
        days = 1000 * 60 * 60 * 24
    }

    instance.unit = args.unit or "seconds"

    for unit, _ in pairs(instance.multipliers) do
        instance:argsMultiplier(args, unit)
    end

    instance.startAt = getTimestampMs() - (instance.start * instance:getMultiplier())
    instance.millisecondsElapsed = 0

    instance.handlers = args.handlers or {}

    instance.eventOnTick = function()
        instance:onTick()
    end

    Events.OnTick.Add(instance.eventOnTick)

    return instance
end

function RibsFramework.Interval:argsMultiplier(args, unit)
    if not args[unit] then return end

    self.interval = args[unit]
    self.unit = unit
end

function RibsFramework.Interval:getMultiplier()
    local multiplier = self.multipliers[self.unit]
    if multiplier then return multiplier end

    print("RibsFramework.Interval:getMultiplier(): unit " .. self.unit .. " not found, using seconds instead")
    return 1000
end

function RibsFramework.Interval:onTick()
    local nowMilliseconds = getTimestampMs()
    self.millisecondsElapsed = (nowMilliseconds - self.startAt)

    if not self.handlers then return end

    if not self:itsTime() then return end

    for _, handler in ipairs(self.handlers) do
        handler()
    end
end

function RibsFramework.Interval:itsTime()
    local elapsedMeasured = self.millisecondsElapsed / self:getMultiplier()


    local interval = (type(self.interval) == "function" and self.interval(self)) or self.interval

    if elapsedMeasured < interval then return false end

    self.startAt = getTimestampMs()
    return true
end

function RibsFramework.Interval:add(handler)
    table.insert(self.handlers, handler)
end

function RibsFramework.Interval:destroy()
    if self.eventOnTick then Events.OnTick.Remove(self.eventOnTick) end
    for key in pairs(self) do self[key] = nil end
end
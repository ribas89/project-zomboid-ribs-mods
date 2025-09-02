function RibsFramework.IntervalIngame:new(args)
    args = args or {}

    local instance = setmetatable({}, self)

    instance.interval = args.interval or 1
    instance.start = args.start or 0

    instance.events = args.events or {
        EveryOneMinute = Events.EveryOneMinute,
        EveryTenMinutes = Events.EveryTenMinutes,
        EveryDays = Events.EveryDays,
        EveryHours = Events.EveryHours,
        OnDusk = Events.OnDusk,
        OnDawn = Events.OnDawn
    }

    instance.eventType = args.eventType or "EveryOneMinute"

    for eventType, _ in pairs(instance.events) do
        instance:argsMultiplier(args, eventType)
    end

    instance.triggerCount = instance.start

    instance.handlers = args.handlers or {}

    instance.eventFunction = args.eventFunction or function()
        instance:onTrigger()
    end

    instance.events[instance.eventType].Add(instance.eventFunction)

    return instance
end

function RibsFramework.IntervalIngame:argsMultiplier(args, eventType)
    if not args[eventType] then return end

    self.interval = args[eventType]
    self.eventType = eventType
end

function RibsFramework.IntervalIngame:onTrigger()
    self.triggerCount = self.triggerCount + 1

    if not self.handlers then return end

    if not self:itsTime() then return end

    for _, handler in ipairs(self.handlers) do
        handler()
    end
end

function RibsFramework.IntervalIngame:itsTime()
    local interval = (type(self.interval) == "function" and self.interval(self)) or self.interval

    if self.triggerCount < interval then return false end

    self.triggerCount = 0
    return true
end

function RibsFramework.IntervalIngame:add(handler)
    table.insert(self.handlers, handler)
end

function RibsFramework.IntervalIngame:destroy()
    if self.eventFunction then self.events[self.eventType].Remove(self.eventFunction) end
    for key in pairs(self) do self[key] = nil end
end

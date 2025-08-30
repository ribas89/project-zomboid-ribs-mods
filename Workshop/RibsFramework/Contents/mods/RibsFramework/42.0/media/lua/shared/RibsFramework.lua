RibsFramework = RibsFramework or {}

RibsFramework.Interval={}
RibsFramework.Interval.__index = RibsFramework.Interval
require("RibsFrameworkInterval")

RibsFramework.IntervalIngame={}
RibsFramework.IntervalIngame.__index = RibsFramework.IntervalIngame
require("RibsFrameworkIntervalIngame")

RibsFramework.Sandbox = {}
RibsFramework.Sandbox.__index = RibsFramework.Sandbox
require("RibsFrameworkSandbox")

return RibsFramework

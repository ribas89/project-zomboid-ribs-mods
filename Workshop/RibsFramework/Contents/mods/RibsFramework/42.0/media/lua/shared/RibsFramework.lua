RibsFramework = RibsFramework or {}

RibsFramework.Interval={}
RibsFramework.Interval.__index = RibsFramework.Interval
require("RibsFrameworkInterval")

RibsFramework.Sandbox = {}
RibsFramework.Sandbox.__index = RibsFramework.Sandbox
require("RibsFrameworkSandbox")

return RibsFramework

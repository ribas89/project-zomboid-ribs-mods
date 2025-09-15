CustomMediaDropArea = {}

CustomMediaDropArea.sandbox = RibsFramework.Sandbox:new({
    ID = "CustomMediaDropArea",
    autoModOptions = true,
})

Events.OnGameStart.Add(function()
    if not (RWMMedia and RWMMedia.createChildren) then return end

    local CustomSize = tonumber(CustomMediaDropArea.sandbox:getValue("CustomSize"))

    local originalCreateChildren = RWMMedia.createChildren
    function RWMMedia:createChildren(...)
        originalCreateChildren(self, ...)

        local oldRight = self.itemDropBox:getRight()
        self.itemDropBox:setHeight(self.itemDropBox:getHeight() + CustomSize)
        self.itemDropBox:setWidth(self.itemDropBox:getWidth() + CustomSize)

        local newRight = self.itemDropBox:getRight() - oldRight
        self.toggleOnOffButton:setX(self.toggleOnOffButton:getX() + newRight)
        self.toggleOnOffButton:setWidth(self.toggleOnOffButton:getWidth() - newRight)

        self:setHeight(self:getHeight() + CustomSize)
    end
end)

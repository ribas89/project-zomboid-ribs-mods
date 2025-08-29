require "Farming/SPlantGlobalObject"

function SPlantGlobalObject:rottenThis()
    local prop = farming_vegetableconf.props[self.typeOfSeed]
	if not prop then return end

    self.nbOfGrow = prop.fullGrown
    self.nextGrowing = SFarmingSystem.instance.hoursElapsed + prop.timeToGrow
	self:saveData()
end
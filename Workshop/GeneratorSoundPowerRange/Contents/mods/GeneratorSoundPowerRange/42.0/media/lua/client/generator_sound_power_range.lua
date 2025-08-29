local options = PZAPI.ModOptions:create("GeneratorSoundPowerRange", "Generator Sound and Power Range")

options:addTitle("Zombie Hearing Radius")
options:addDescription("Controls how far ZOMBIES can hear the generator.")
options:addDescription("Does NOT affect the sound volume for the player. Visit the steam mod page for more info.")
options:addDescription("Min = 1 Max = 100")

options:addTextEntry("sound_radius_generator_yellow", "Premium Technologies (Yellow)", "20")
options:addTextEntry("sound_radius_generator", "Lectromax (Red)", "20")
options:addTextEntry("sound_radius_generator_blue", "ValuTech (Blue)", "23")
options:addTextEntry("sound_radius_generator_old", "Old (Round)", "25")

options:addTitle("Generator Power Range")
options:addDescription("Controls how far a generator provides electrical power.")
options:addDescription("Requires take/drop the generator or restart the game to work.")

options:addDescription("Min = 1 Max = 100 Default = 20")
options:addTextEntry("power_radius", "Horizontal range (square radius)", "20")

options:addDescription("Min = 1 Max = 32 Default = 3")
options:addDescription("There is a hard cap of 15. Visit the steam mod page for more info.")
options:addTextEntry("power_radius_vertical", "Vertical range (floors)", "3")


local function GeneratorChangeRange()
    local function getValue(key)
        return options:getOption(key):getValue()
    end

    local function setSoundRadius(itemKey, optionKey)
        ScriptManager.instance:getItem(itemKey):DoParam("SoundRadius = " .. getValue(optionKey))
    end

    setSoundRadius("Base.Generator_Yellow", "sound_radius_generator_yellow")
    setSoundRadius("Base.Generator", "sound_radius_generator")
    setSoundRadius("Base.Generator_Blue", "sound_radius_generator_blue")
    setSoundRadius("Base.Generator_Old", "sound_radius_generator_old")

    local function setSandboxValueInteger(sandboxKey, optionKey)
        getSandboxOptions():getOptionByName(sandboxKey):setValue(tonumber(getValue(optionKey)))
    end

    setSandboxValueInteger("GeneratorTileRange", "power_radius")
    setSandboxValueInteger("GeneratorVerticalPowerRange", "power_radius_vertical")

    if isClient() then getSandboxOptions():sendToServer() end
end

options.apply = function(self)
    GeneratorChangeRange()
end

Events.OnGameStart.Add(function()
    GeneratorChangeRange()
end)
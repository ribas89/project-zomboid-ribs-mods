function RibsFramework.ClassVersion:new(args)
    args = args or {}

    local instance = setmetatable({}, self)

    instance.ID = args.ID or ""

    instance.instances = args.instances or {}
    instance.modOptions = instance.instances.modOptions or RibsFramework.ModOptions:new(args)

    instance.installed = true

    instance.versions = {}

    instance.autoModOptions = args.autoModOptions or true

    instance.classesData = args.classesData or {}

    instance.OnGameTimeLoaded = args.OnGameTimeLoaded or function()
        instance:autoCheckClasses()
    end
    Events.OnGameTimeLoaded.Add(instance.OnGameTimeLoaded)

    return instance
end

function RibsFramework.ClassVersion:isInstalled()
    return self.installed
end

function RibsFramework.ClassVersion:versionToNumber(stringVersion)
    local major, minor, patch = stringVersion:match("^(%d+)%.(%d+)%.(%d+)$")
    return {
        major = tonumber(major) or 0,
        minor = tonumber(minor) or 0,
        patch = tonumber(patch) or 0,
    }
end

function RibsFramework.ClassVersion:compareVersions(versionStringClass, versionStringMod)
    local classVersion = self:versionToNumber(versionStringClass)
    local modVersion = self:versionToNumber(versionStringMod)

    if classVersion.major ~= modVersion.major then
        return classVersion.major > modVersion.major
    end

    if classVersion.minor ~= modVersion.minor then
        return classVersion.minor > modVersion.minor
    end

    return classVersion.patch >= modVersion.patch
end

function RibsFramework.ClassVersion:getClassInfo(pJavaClass, pInfo)
    local classTable = pJavaClass
    local versionName = "ribsVersion"
    local minVersion = "0.0.0"

    if type(classTable) == "number" then
        classTable = pInfo
    elseif type(pInfo) == "table" then
        versionName = pInfo.name or versionName
        minVersion = pInfo.version or minVersion
    end

    if not classTable then return {} end
    local className = ""
    if type(classTable) == "string" then
        className = classTable .. " - "
        classTable = _G[classTable] or {}
    end

    local ribsVersion = classTable[versionName]

    return { className = className, ribsVersion = ribsVersion, minVersion = minVersion }
end

function RibsFramework.ClassVersion:isUpdated(pJavaClass, pVersionName, pVersion)
    local classInfo = self:getClassInfo(pJavaClass, pVersionName)

    local modVersion = pVersion or "0.0.0"
    local result = self:compareVersions(classInfo.ribsVersion, modVersion)

    return result
end

function RibsFramework.ClassVersion:modOptionsVersionCheck(pJavaClass, info)
    if not self.modOptions then return true end
    local classInfo = self:getClassInfo(pJavaClass, info)

    if not classInfo.ribsVersion then
        self.modOptions:addTitle(classInfo.className .. getText("UI_RibsFramework_Installation_Status_Not_Installed"))
        self.modOptions:addDescription("UI_RibsFramework_Not_Installed1")
        self.modOptions:addDescription("UI_RibsFramework_Not_Installed2")
        self.modOptions:addDescription("UI_RibsFramework_Not_Installed3")
        self.installed = false
        return false
    end

    local isUpdated = self:compareVersions(classInfo.ribsVersion, classInfo.minVersion)
    if not isUpdated then
        self.modOptions:addTitle(classInfo.className .. getText("UI_RibsFramework_Installation_Status_Outdated"))
        local modVersion = getText("UI_RibsFramework_OutdatedModVersion") .. classInfo.minVersion
        local ribsVersion = getText("UI_RibsFramework_OutdatedClassVersion") .. classInfo.ribsVersion
        self.modOptions:addDescription(modVersion .. ribsVersion)
        self.modOptions:addDescription("UI_RibsFramework_Outdated1")
        self.modOptions:addDescription("UI_RibsFramework_Outdated2")
        self.modOptions:addDescription("UI_RibsFramework_Outdated3")
        self.installed = false
        return false
    end

    self.modOptions:addDescription(classInfo.className .. getText("UI_RibsFramework_Installed_Version") .. classInfo.ribsVersion)
    return true
end

function RibsFramework.ClassVersion:autoCheckClasses()
    for key, value in pairs(self.classesData) do
        self:modOptionsVersionCheck(key, value)
    end
end

function RibsFramework.ClassVersion:modOptionsCheckDependencies(modOptions, modID)
    if self:isInstalled() then return true end
    if not modOptions then return end

    if modID == self.ID then return false end

    modOptions:addTitle(getText("UI_RibsFramework_Dependency_Missing_Title") .. getText("Sandbox_" .. self.ID))
    modOptions:addDescription("UI_RibsFramework_Dependency_Missing1")
    modOptions:addDescription("UI_RibsFramework_Dependency_Missing2")
    modOptions:addDescription("UI_RibsFramework_Dependency_Missing3")

    return false
end

GeneratorTweaksCore = {}

GeneratorTweaksCore.modOptions = RibsFramework.ModOptions:new({
    ID = "GeneratorTweaksCore"
})

GeneratorTweaksCore.classVersion = RibsFramework.ClassVersion:new({
    ID = "GeneratorTweaksCore",
    instances = GeneratorTweaksCore,
    classesData = {
        IsoGenerator = { name = "ribsVersion", version = "1.0.0" },
    }
})

GeneratorTweaksCore.sandbox = RibsFramework.Sandbox:new({
    ID = "GeneratorTweaksCore",
    instances = GeneratorTweaksCore
})

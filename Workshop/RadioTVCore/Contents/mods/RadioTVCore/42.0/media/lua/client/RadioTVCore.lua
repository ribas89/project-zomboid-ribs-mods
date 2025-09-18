RadioTVCore = {}

RadioTVCore.modOptions = RibsFramework.ModOptions:new({
    ID = "RadioTVCore"
})

RadioTVCore.classVersion = RibsFramework.ClassVersion:new({
    ID = "RadioTVCore",
    instances = RadioTVCore,
    classesData = {
        DeviceData = { name = "ribsVersionDeviceData", version = "1.1.1" },
        WaveSignalDevice = { name = "ribsVersionWaveSignalDevice", version = "2.1.0" },
        ZomboidRadio = { name = "ribsVersionZomboidRadio", version = "1.1.0" }
    }
})

RadioTVCore.sandbox = RibsFramework.Sandbox:new({
    ID = "RadioTVCore",
    instances = RadioTVCore
})

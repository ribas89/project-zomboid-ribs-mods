package zombie.radio.devices;

import fmod.fmod.FMODSoundEmitter;
import fmod.fmod.FMOD_STUDIO_PARAMETER_DESCRIPTION;
import fmod.fmod.IFMODParameterUpdater;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Map;
import zombie.GameTime;
import zombie.GameWindow;
import zombie.WorldSoundManager;
import zombie.audio.BaseSoundEmitter;
import zombie.audio.FMODParameter;
import zombie.audio.FMODParameterList;
import zombie.audio.GameSoundClip;
import zombie.audio.parameters.ParameterDeviceVolume;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.core.Color;
import zombie.core.logger.ExceptionLogger;
import zombie.core.math.PZMath;
import zombie.core.network.ByteBufferWriter;
import zombie.core.raknet.UdpConnection;
import zombie.core.raknet.VoiceManager;
import zombie.core.random.Rand;
import zombie.inventory.InventoryItem;
import zombie.inventory.InventoryItemFactory;
import zombie.inventory.ItemContainer;
import zombie.inventory.ItemUser;
import zombie.inventory.types.DrainableComboItem;
import zombie.inventory.types.Radio;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.IsoUtils;
import zombie.iso.IsoWorld;
import zombie.iso.objects.IsoGenerator;
import zombie.iso.objects.IsoWaveSignal;
import zombie.network.GameClient;
import zombie.network.GameServer;
import zombie.network.PacketTypes;
import zombie.radio.ZomboidRadio;
import zombie.radio.devices.DevicePresets;
import zombie.radio.devices.PresetEntry;
import zombie.radio.devices.WaveSignalDevice;
import zombie.radio.media.MediaData;
import zombie.vehicles.VehiclePart;
import zombie.SandboxOptions;

public final class DeviceData
        implements Cloneable,
        IFMODParameterUpdater {
    private static final float deviceSpeakerSoundMod = 1.0f;
    private static final float deviceButtonSoundVol = 1.0f;
    protected String deviceName = "WaveSignalDevice";
    protected boolean twoWay = false;
    protected int transmitRange = 1000;
    protected int micRange = 5;
    protected boolean micIsMuted = false;
    protected float baseVolumeRange = 15.0f;
    protected float deviceVolume = 1.0f;
    protected boolean isPortable = false;
    protected boolean isTelevision = false;
    protected boolean isHighTier = false;
    protected boolean isTurnedOn = false;
    protected int channel = 88000;
    protected int minChannelRange = 200;
    protected int maxChannelRange = 1000000;
    protected DevicePresets presets = null;
    protected boolean isBatteryPowered = true;
    protected boolean hasBattery = true;
    protected float powerDelta = 1.0f;
    protected float useDelta = 0.001f;
    protected int lastRecordedDistance = -1;
    protected int headphoneType = -1;
    protected WaveSignalDevice parent = null;
    protected GameTime gameTime = null;
    protected boolean channelChangedRecently = false;
    protected BaseSoundEmitter emitter = null;
    protected FMODParameterList parameterList = new FMODParameterList();
    protected ParameterDeviceVolume parameterDeviceVolume = new ParameterDeviceVolume(this);
    protected short mediaIndex = (short) -1;
    protected byte mediaType = (byte) -1;
    protected String mediaItem = null;
    protected MediaData playingMedia = null;
    protected boolean isPlayingMedia = false;
    protected int mediaLineIndex = 0;
    protected float lineCounter = 0.0f;
    protected String currentMediaLine = null;
    protected Color currentMediaColor = null;
    protected boolean isStoppingMedia = false;
    protected float stopMediaCounter = 0.0f;
    protected boolean noTransmit = false;
    private float soundCounterStatic = 0.0f;
    protected long radioLoopSound = 0L;
    protected boolean doTriggerWorldSound = false;
    protected long lastMinuteStamp = -1L;
    protected int listenCnt = 0;
    float nextStaticSound = 0.0f;
    protected float voipCounter = 0.0f;
    protected float signalCounter = 0.0f;
    protected float soundCounter = 0.0f;
    float minmod = 1.5f;
    float maxmod = 5.0f;
    public static String ribsVersionDeviceData = "1.1.1";
    private static final ArrayList<DeviceData> activeDevices = new ArrayList<>();

    public DeviceData() {
        this(null);
    }

    public DeviceData(WaveSignalDevice waveSignalDevice) {
        this.parent = waveSignalDevice;
        this.presets = new DevicePresets();
        this.gameTime = GameTime.getInstance();
        this.parameterList.add(this.parameterDeviceVolume);
    }

    private void registerActive() {
        if (!activeDevices.contains(this)) {
            activeDevices.add(this);
        }
    }

    private void unregisterActive() {
        activeDevices.remove(this);
    }

    private <T> T getSandboxValue(String optionName, T defaultValue) {
        SandboxOptions.SandboxOption opt = SandboxOptions.getInstance().getOptionByName(optionName);
        if (opt == null) {
            return defaultValue;
        }
        try {
            Object objectValue = opt.asConfigOption().getValueAsObject();
            if (objectValue == null) {
                return defaultValue;
            }
            Number castedValue = null;
            if (defaultValue instanceof Integer) {
                castedValue = ((Number) objectValue).intValue();
            }
            if (defaultValue instanceof Double) {
                castedValue = ((Number) objectValue).doubleValue();
            }
            if (defaultValue instanceof Float) {
                castedValue = Float.valueOf(((Number) objectValue).floatValue());
            }
            if (defaultValue instanceof Long) {
                castedValue = ((Number) objectValue).longValue();
            }
            if (objectValue instanceof Boolean) {
                return (T) objectValue;
            }
            if (objectValue instanceof String) {
                return (T) objectValue.toString();
            }
            return (T) castedValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public void generatePresets() {
        block12: {
            Map<Integer, String> map;
            int n;
            block11: {
                if (this.presets == null) {
                    this.presets = new DevicePresets();
                }
                this.presets.clearPresets();
                if (!this.isTelevision)
                    break block11;
                Map<Integer, String> map2 = ZomboidRadio.getInstance().GetChannelList("Television");
                if (map2 == null)
                    break block12;
                for (Map.Entry<Integer, String> entry : map2.entrySet()) {
                    if (entry.getKey() < this.minChannelRange || entry.getKey() > this.maxChannelRange)
                        continue;
                    this.presets.addPreset(entry.getValue(), entry.getKey());
                }
                break block12;
            }
            int n2 = n = this.twoWay ? 100 : 300;
            if (this.isHighTier) {
                n = 800;
            }
            if ((map = ZomboidRadio.getInstance().GetChannelList("Emergency")) != null) {
                for (Map.Entry<Integer, String> entry : map.entrySet()) {
                    if (entry.getKey() < this.minChannelRange || entry.getKey() > this.maxChannelRange || Rand.Next(1000) >= n)
                        continue;
                    this.presets.addPreset(entry.getValue(), entry.getKey());
                }
            }
            n = this.twoWay ? 100 : 800;
            map = ZomboidRadio.getInstance().GetChannelList("Radio");
            if (map != null) {
                for (Map.Entry<Integer, String> entry : map.entrySet()) {
                    if (entry.getKey() < this.minChannelRange || entry.getKey() > this.maxChannelRange || Rand.Next(1000) >= n)
                        continue;
                    this.presets.addPreset(entry.getValue(), entry.getKey());
                }
            }
            if (this.twoWay && (map = ZomboidRadio.getInstance().GetChannelList("Amateur")) != null) {
                for (Map.Entry<Integer, String> entry : map.entrySet()) {
                    if (entry.getKey() < this.minChannelRange || entry.getKey() > this.maxChannelRange || Rand.Next(1000) >= n)
                        continue;
                    this.presets.addPreset(entry.getValue(), entry.getKey());
                }
            }
            if (this.isHighTier && (map = ZomboidRadio.getInstance().GetChannelList("Military")) != null) {
                for (Map.Entry<Integer, String> entry : map.entrySet()) {
                    if (entry.getKey() < this.minChannelRange || entry.getKey() > this.maxChannelRange || Rand.Next(1000) >= 10)
                        continue;
                    this.presets.addPreset(entry.getValue(), entry.getKey());
                }
            }
        }
    }

    protected Object clone() throws CloneNotSupportedException {
        DeviceData deviceData = (DeviceData) super.clone();
        deviceData.setDevicePresets((DevicePresets) this.presets.clone());
        deviceData.setParent(null);
        deviceData.emitter = null;
        deviceData.parameterDeviceVolume = new ParameterDeviceVolume(deviceData);
        deviceData.parameterList = new FMODParameterList();
        deviceData.parameterList.add(deviceData.parameterDeviceVolume);
        return deviceData;
    }

    public DeviceData getClone() {
        DeviceData deviceData;
        try {
            deviceData = (DeviceData) this.clone();
        } catch (Exception exception) {
            ExceptionLogger.logException(exception);
            deviceData = new DeviceData();
        }
        return deviceData;
    }

    public WaveSignalDevice getParent() {
        return this.parent;
    }

    public void setParent(WaveSignalDevice waveSignalDevice) {
        this.parent = waveSignalDevice;
    }

    public DevicePresets getDevicePresets() {
        return this.presets;
    }

    public void setDevicePresets(DevicePresets devicePresets) {
        if (devicePresets == null) {
            devicePresets = new DevicePresets();
        }
        this.presets = devicePresets;
    }

    public void cloneDevicePresets(DevicePresets devicePresets) throws CloneNotSupportedException {
        this.presets.clearPresets();
        if (devicePresets == null) {
            return;
        }
        for (int i = 0; i < devicePresets.presets.size(); ++i) {
            PresetEntry presetEntry = devicePresets.presets.get(i);
            this.presets.addPreset(presetEntry.name, presetEntry.frequency);
        }
    }

    public int getMinChannelRange() {
        return this.minChannelRange;
    }

    public void setMinChannelRange(int n) {
        this.minChannelRange = n >= 200 && n <= 1000000 ? n : 200;
    }

    public int getMaxChannelRange() {
        return this.maxChannelRange;
    }

    public void setMaxChannelRange(int n) {
        this.maxChannelRange = n >= 200 && n <= 1000000 ? n : 1000000;
    }

    public boolean getIsHighTier() {
        return this.isHighTier;
    }

    public void setIsHighTier(boolean bl) {
        this.isHighTier = bl;
    }

    public boolean getIsBatteryPowered() {
        return this.isBatteryPowered;
    }

    public void setIsBatteryPowered(boolean bl) {
        this.isBatteryPowered = bl;
    }

    public boolean getHasBattery() {
        return this.hasBattery;
    }

    public void setHasBattery(boolean bl) {
        this.hasBattery = bl;
    }

    public void addBattery(DrainableComboItem drainableComboItem) {
        ItemContainer itemContainer;
        if (!this.hasBattery && drainableComboItem != null && drainableComboItem.getFullType().equals("Base.Battery") && (itemContainer = drainableComboItem.getContainer()) != null) {
            if (itemContainer.getType().equals("floor") && drainableComboItem.getWorldItem() != null && drainableComboItem.getWorldItem().getSquare() != null) {
                drainableComboItem.getWorldItem().getSquare().transmitRemoveItemFromSquare(drainableComboItem.getWorldItem());
                drainableComboItem.getWorldItem().getSquare().getWorldObjects().remove(drainableComboItem.getWorldItem());
                drainableComboItem.getWorldItem().getSquare().chunk.recalcHashCodeObjects();
                drainableComboItem.getWorldItem().getSquare().getObjects().remove(drainableComboItem.getWorldItem());
                drainableComboItem.setWorldItem(null);
            }
            this.powerDelta = drainableComboItem.getCurrentUsesFloat();
            itemContainer.DoRemoveItem(drainableComboItem);
            this.hasBattery = true;
            this.transmitDeviceDataState((short) 2);
        }
    }

    public InventoryItem getBattery(ItemContainer itemContainer) {
        if (this.hasBattery) {
            DrainableComboItem drainableComboItem = (DrainableComboItem) InventoryItemFactory.CreateItem("Base.Battery");
            drainableComboItem.setCurrentUses((int) ((float) drainableComboItem.getMaxUses() * this.powerDelta));
            this.powerDelta = 0.0f;
            itemContainer.AddItem(drainableComboItem);
            this.hasBattery = false;
            this.transmitDeviceDataState((short) 2);
            return drainableComboItem;
        }
        return null;
    }

    public void transmitBattryChange() {
        this.transmitDeviceDataState((short) 2);
    }

    public void addHeadphones(InventoryItem inventoryItem) {
        ItemContainer itemContainer;
        if (this.headphoneType < 0 && (inventoryItem.getFullType().equals("Base.Headphones") || inventoryItem.getFullType().equals("Base.Earbuds"))
                && (itemContainer = inventoryItem.getContainer()) != null) {
            if (itemContainer.getType().equals("floor") && inventoryItem.getWorldItem() != null && inventoryItem.getWorldItem().getSquare() != null) {
                inventoryItem.getWorldItem().getSquare().transmitRemoveItemFromSquare(inventoryItem.getWorldItem());
                inventoryItem.getWorldItem().getSquare().getWorldObjects().remove(inventoryItem.getWorldItem());
                inventoryItem.getWorldItem().getSquare().chunk.recalcHashCodeObjects();
                inventoryItem.getWorldItem().getSquare().getObjects().remove(inventoryItem.getWorldItem());
                inventoryItem.setWorldItem(null);
            }
            int n = inventoryItem.getFullType().equals("Base.Headphones") ? 0 : 1;
            itemContainer.DoRemoveItem(inventoryItem);
            this.setHeadphoneType(n);
            this.transmitDeviceDataState((short) 6);
        }
    }

    public InventoryItem getHeadphones(ItemContainer itemContainer) {
        if (this.headphoneType >= 0) {
            InventoryItem inventoryItem = null;
            if (this.headphoneType == 0) {
                inventoryItem = InventoryItemFactory.CreateItem("Base.Headphones");
            } else if (this.headphoneType == 1) {
                inventoryItem = InventoryItemFactory.CreateItem("Base.Earbuds");
            }
            if (inventoryItem != null) {
                itemContainer.AddItem(inventoryItem);
            }
            this.setHeadphoneType(-1);
            this.transmitDeviceDataState((short) 6);
        }
        return null;
    }

    public int getMicRange() {
        return this.micRange;
    }

    public void setMicRange(int n) {
        this.micRange = n;
    }

    public boolean getMicIsMuted() {
        return this.micIsMuted;
    }

    public void setMicIsMuted(boolean bl) {
        this.micIsMuted = bl;
        if (this.getParent() != null && this.getParent() instanceof Radio && ((Radio) this.getParent()).getEquipParent() != null && ((Radio) this.getParent()).getEquipParent() instanceof IsoPlayer) {
            IsoPlayer isoPlayer = (IsoPlayer) ((Radio) this.getParent()).getEquipParent();
            isoPlayer.updateEquippedRadioFreq();
        }
    }

    public int getHeadphoneType() {
        return this.headphoneType;
    }

    public void setHeadphoneType(int n) {
        this.headphoneType = n;
    }

    public float getBaseVolumeRange() {
        return this.baseVolumeRange;
    }

    public void setBaseVolumeRange(float f) {
        this.baseVolumeRange = f;
    }

    public float getDeviceVolume() {
        return this.deviceVolume;
    }

    public void setDeviceVolume(float f) {
        this.deviceVolume = f < 0.0f ? 0.0f : (f > 1.0f ? 1.0f : f);
        this.transmitDeviceDataState((short) 4);
    }

    public void setDeviceVolumeRaw(float f) {
        this.deviceVolume = f < 0.0f ? 0.0f : (f > 1.0f ? 1.0f : f);
    }

    public boolean getIsTelevision() {
        return this.isTelevision;
    }

    public boolean isTelevision() {
        return this.getIsTelevision();
    }

    public void setIsTelevision(boolean bl) {
        this.isTelevision = bl;
    }

    public boolean canPlayerRemoteInteract(IsoGameCharacter isoGameCharacter) {
        if (!this.isTelevision() || isoGameCharacter == null || this.getIsoObject() == null) {
            return false;
        }
        if (!isoGameCharacter.CanSee(this.getIsoObject())) {
            return false;
        }
        if (isoGameCharacter.getPrimaryHandItem() != null && isoGameCharacter.getPrimaryHandItem().hasTag("TVRemote")) {
            return true;
        }
        return isoGameCharacter.getSecondaryHandItem() != null && isoGameCharacter.getSecondaryHandItem().hasTag("TVRemote");
    }

    public String getDeviceName() {
        return this.deviceName;
    }

    public void setDeviceName(String string) {
        this.deviceName = string;
    }

    public boolean getIsTwoWay() {
        return this.twoWay;
    }

    public void setIsTwoWay(boolean bl) {
        this.twoWay = bl;
    }

    public int getTransmitRange() {
        return this.transmitRange;
    }

    public void setTransmitRange(int n) {
        this.transmitRange = n > 0 ? n : 0;
    }

    public boolean getIsPortable() {
        return this.isPortable;
    }

    public void setIsPortable(boolean bl) {
        this.isPortable = bl;
    }

    public boolean getIsTurnedOn() {
        return this.isTurnedOn;
    }

    public void setIsTurnedOn(boolean bl) {
        if (this.canBePoweredHere()) {
            this.isTurnedOn = !this.isBatteryPowered || this.powerDelta > 0.0f ? bl : false;
            this.transmitDeviceDataState((short) 0);
        } else if (this.isTurnedOn) {
            this.isTurnedOn = false;
            this.transmitDeviceDataState((short) 0);
        }
        if (this.getParent() != null && this.getParent() instanceof Radio && ((Radio) this.getParent()).getEquipParent() != null && ((Radio) this.getParent()).getEquipParent() instanceof IsoPlayer) {
            IsoPlayer isoPlayer = (IsoPlayer) ((Radio) this.getParent()).getEquipParent();
            isoPlayer.updateEquippedRadioFreq();
        }
        IsoGenerator.updateGenerator(this.getParent().getSquare());
    }

    public void setTurnedOnRaw(boolean bl) {
        this.isTurnedOn = bl;
        if (this.getParent() != null && this.getParent() instanceof Radio && ((Radio) this.getParent()).getEquipParent() != null && ((Radio) this.getParent()).getEquipParent() instanceof IsoPlayer) {
            IsoPlayer isoPlayer = (IsoPlayer) ((Radio) this.getParent()).getEquipParent();
            isoPlayer.updateEquippedRadioFreq();
        }
    }

    public boolean canBePoweredHere() {
        if (this.isBatteryPowered) {
            return true;
        }
        if (this.parent instanceof VehiclePart) {
            VehiclePart vehiclePart = (VehiclePart) this.parent;
            if (vehiclePart.isInventoryItemUninstalled()) {
                return false;
            }
            return vehiclePart.hasDevicePower();
        }
        boolean bl = false;
        if (this.parent.getSquare().hasGridPower()) {
            bl = true;
        }
        if (this.parent == null || this.parent.getSquare() == null) {
            bl = false;
        } else if (this.parent.getSquare().haveElectricity()) {
            bl = true;
        } else if (this.parent.getSquare().getRoom() == null) {
            bl = false;
        }
        return bl;
    }

    public void setRandomChannel() {
        if (this.presets != null && this.presets.getPresets().size() > 0) {
            int n = Rand.Next(0, this.presets.getPresets().size());
            this.channel = this.presets.getPresets().get(n).getFrequency();
        } else {
            this.channel = Rand.Next(this.minChannelRange, this.maxChannelRange);
            this.channel -= this.channel % 200;
        }
    }

    public int getChannel() {
        return this.channel;
    }

    public void setChannel(int n) {
        this.setChannel(n, true);
    }

    public void setChannel(int n, boolean bl) {
        if (n >= this.minChannelRange && n <= this.maxChannelRange) {
            this.channel = n;
            if (this.isTelevision) {
                this.playSoundSend("TelevisionZap", true);
            } else if (this.isVehicleDevice()) {
                this.playSoundSend("VehicleRadioZap", true);
            } else {
                this.playSoundSend("RadioZap", true);
            }
            if (this.radioLoopSound > 0L) {
                this.emitter.stopSound(this.radioLoopSound);
                this.radioLoopSound = 0L;
            }
            this.transmitDeviceDataState((short) 1);
            if (bl) {
                this.TriggerPlayerListening(true);
            }
        }
    }

    public void setChannelRaw(int n) {
        this.channel = n;
    }

    public float getUseDelta() {
        return this.useDelta;
    }

    public void setUseDelta(float f) {
        this.useDelta = f / 60.0f;
    }

    public float getPower() {
        return this.powerDelta;
    }

    public void setPower(float f) {
        if (f > 1.0f) {
            f = 1.0f;
        }
        if (f < 0.0f) {
            f = 0.0f;
        }
        this.powerDelta = f;
    }

    public void setInitialPower() {
        this.lastMinuteStamp = this.gameTime.getMinutesStamp();
        this.setPower(this.powerDelta - this.useDelta * (float) this.lastMinuteStamp);
    }

    public void TriggerPlayerListening(boolean bl) {
        if (this.isTurnedOn) {
            ZomboidRadio.getInstance().PlayerListensChannel(this.channel, true, this.isTelevision);
        }
    }

    public void playSoundSend(String string, boolean bl) {
        this.playSound(string, bl ? this.deviceVolume * 1.0f : 1.0f, true);
    }

    public void playSoundLocal(String string, boolean bl) {
        this.playSound(string, bl ? this.deviceVolume * 1.0f : 1.0f, false);
    }

    public void playSound(String string, float f, boolean bl) {
        if (GameServer.bServer) {
            return;
        }
        this.setEmitterAndPos();
        if (this.emitter != null) {
            long l = bl ? this.emitter.playSound(string) : this.emitter.playSoundImpl(string, (IsoObject) null);
            this.setSoundVolume(l, f);
        }
    }

    private void setSoundVolume(long l, float f) {
        if (this.emitter.isUsingParameter(l, "DeviceVolume")) {
            return;
        }
        this.emitter.setVolume(l, f);
    }

    public void stopOrTriggerSoundByName(String string) {
        if (this.emitter == null) {
            return;
        }
        this.emitter.stopOrTriggerSoundByName(string);
    }

    public void cleanSoundsAndEmitter() {
        if (this.emitter == null) {
            return;
        }

        boolean enabled = this.getSandboxValue("UALUnequipAndListen.EnableRadioSound", false);
        if (enabled && this.isPortable && this.isTurnedOn) {
            // keep ticking while portable and on
            this.registerActive();
            return;
        }

        // otherwise unregister
        this.unregisterActive();

        this.emitter.stopAll();
        BaseSoundEmitter baseSoundEmitter = this.emitter;
        if (baseSoundEmitter instanceof FMODSoundEmitter) {
            FMODSoundEmitter fMODSoundEmitter = (FMODSoundEmitter) baseSoundEmitter;
            fMODSoundEmitter.parameterUpdater = null;
        }
        IsoWorld.instance.returnOwnershipOfEmitter(this.emitter);
        this.emitter = null;
        this.radioLoopSound = 0L;

    }

    public IsoObject getIsoObject() {
        if (this.parent == null) {
            return null;
        }
        Object object = this.parent;
        if (object instanceof IsoObject) {
            IsoObject isoObject = (IsoObject) object;
            return isoObject;
        }
        object = this.parent;
        if (object instanceof Radio) {
            Radio radio = (Radio) object;
            return (object = radio.getOutermostContainer()) == null ? null : ((ItemContainer) object).getParent();
        }
        object = this.parent;
        if (object instanceof VehiclePart) {
            VehiclePart vehiclePart = (VehiclePart) object;
            return vehiclePart.getVehicle();
        }
        return null;
    }

    protected void setEmitterAndPos() {
        IsoObject isoObject = this.getIsoObject();
        if (isoObject != null) {
            float f = isoObject.getX() + (this.isVehicleDevice() || isoObject instanceof IsoGameCharacter ? 0.0f : 0.5f);
            float f2 = isoObject.getY() + (this.isVehicleDevice() || isoObject instanceof IsoGameCharacter ? 0.0f : 0.5f);
            if (this.emitter == null) {
                this.emitter = IsoWorld.instance.getFreeEmitter(f, f2, PZMath.fastfloor(isoObject.getZ()));
                IsoWorld.instance.takeOwnershipOfEmitter(this.emitter);
                BaseSoundEmitter baseSoundEmitter = this.emitter;
                if (baseSoundEmitter instanceof FMODSoundEmitter) {
                    FMODSoundEmitter fMODSoundEmitter = (FMODSoundEmitter) baseSoundEmitter;
                    fMODSoundEmitter.parameterUpdater = this;
                }
            } else {
                this.emitter.setPos(f, f2, PZMath.fastfloor(isoObject.getZ()));
            }
            if (this.radioLoopSound != 0L) {
                this.setSoundVolume(this.radioLoopSound, this.deviceVolume * 1.0f);
            }
        }
    }

    private float getClosestListener(float f, float f2, float f3) {
        float f4 = Float.MAX_VALUE;
        for (int i = 0; i < IsoPlayer.numPlayers; ++i) {
            IsoPlayer isoPlayer = IsoPlayer.players[i];
            if (isoPlayer == null || isoPlayer.isDeaf() || isoPlayer.getCurrentSquare() == null)
                continue;
            float f5 = isoPlayer.getX();
            float f6 = isoPlayer.getY();
            float f7 = isoPlayer.getZ();
            float f8 = IsoUtils.DistanceToSquared(f5, f6, f7 * 3.0f, f, f2, f3 * 3.0f);
            if (!((f8 *= PZMath.pow(isoPlayer.getHearDistanceModifier(), 2.0f)) < f4))
                continue;
            f4 = f8;
        }
        return f4;
    }

    protected void updateEmitter() {
        float f;
        if (GameServer.bServer) {
            return;
        }
        this.parameterList.update();
        IsoObject isoObject = this.getIsoObject();
        float f2 = f = isoObject == null ? Float.MAX_VALUE : this.getClosestListener(isoObject.getX(), isoObject.getY(), isoObject.getZ());
        if (!this.isTurnedOn || f > 256.0f) {
            if (this.emitter != null && (this.emitter.isPlaying("RadioButton") || this.emitter.isPlaying("TelevisionOff") || this.emitter.isPlaying("VehicleRadioButton"))) {
                if (this.radioLoopSound > 0L) {
                    this.emitter.stopSound(this.radioLoopSound);
                }
                this.setEmitterAndPos();
                this.emitter.tick();
                return;
            }
            this.cleanSoundsAndEmitter();
            return;
        }
        this.setEmitterAndPos();
        if (this.emitter != null) {
            String string;
            String string2 = "RadioTalk";
            if (this.isVehicleDevice()) {
                string2 = "VehicleRadioProgram";
            }
            if (this.isEmergencyBroadcast()) {
                string2 = "BroadcastEmergency";
            }
            if (this.signalCounter > 0.0f && !this.emitter.isPlaying(string2)) {
                if (this.radioLoopSound > 0L) {
                    this.emitter.stopSound(this.radioLoopSound);
                }
                this.radioLoopSound = this.emitter.playSoundImpl(string2, (IsoObject) null);
                this.setSoundVolume(this.radioLoopSound, this.deviceVolume * 1.0f);
            }
            String string3 = string = !this.isTelevision ? "RadioStatic" : "TelevisionTestBeep";
            if (this.isVehicleDevice()) {
                string = "VehicleRadioStatic";
            }
            if (this.radioLoopSound == 0L || this.signalCounter <= 0.0f && !this.emitter.isPlaying(string)) {
                if (this.radioLoopSound > 0L) {
                    this.emitter.stopOrTriggerSound(this.radioLoopSound);
                    if (!this.isTelevision) {
                        if (this.isVehicleDevice()) {
                            this.playSoundSend("VehicleRadioZap", true);
                        } else {
                            this.playSoundLocal("RadioZap", true);
                        }
                    }
                }
                this.radioLoopSound = this.emitter.playSoundImpl(string, (IsoObject) null);
                this.setSoundVolume(this.radioLoopSound, this.deviceVolume * 1.0f);
            }
            this.emitter.tick();
        }
    }

    public BaseSoundEmitter getEmitter() {
        return this.emitter;
    }

    public static void updateAllEmitters() {
        for (int i = 0; i < activeDevices.size(); i++) {
            DeviceData dd = activeDevices.get(i);
            if (dd != null && dd.isTurnedOn) {
                dd.updateEmitter();
            }
        }
    }

    public void update(boolean bl, boolean bl2) {
        if (this.lastMinuteStamp == -1L) {
            this.lastMinuteStamp = this.gameTime.getMinutesStamp();
        }
        if (this.gameTime.getMinutesStamp() > this.lastMinuteStamp) {
            long l = this.gameTime.getMinutesStamp() - this.lastMinuteStamp;
            this.lastMinuteStamp = this.gameTime.getMinutesStamp();
            this.listenCnt = (int) ((long) this.listenCnt + l);
            if (this.listenCnt >= 10) {
                this.listenCnt = 0;
            }
            if (!GameServer.bServer && this.isTurnedOn && bl2 && (this.listenCnt == 0 || this.listenCnt == 5)) {
                this.TriggerPlayerListening(true);
            }
            if (this.isTurnedOn && this.isBatteryPowered && this.powerDelta > 0.0f) {
                float f = this.powerDelta - this.powerDelta % 0.01f;
                this.setPower(this.powerDelta - this.useDelta * (float) l);
                if (this.listenCnt == 0 || this.powerDelta == 0.0f || this.powerDelta < f) {
                    if (bl && GameServer.bServer) {
                        this.transmitDeviceDataStateServer((short) 3, null);
                    } else if (!bl && GameClient.bClient) {
                        this.transmitDeviceDataState((short) 3);
                    }
                }
            }
        }
        if (this.isTurnedOn && (this.isBatteryPowered && this.powerDelta <= 0.0f || !this.canBePoweredHere())) {
            this.isTurnedOn = false;
            if (bl && GameServer.bServer) {
                this.transmitDeviceDataStateServer((short) 0, null);
            } else if (!bl && GameClient.bClient) {
                this.transmitDeviceDataState((short) 0);
            }
        }
        this.updateMediaPlaying();
        this.updateEmitter();
        this.updateSimple();
    }

    public void updateSimple() {
        if (this.voipCounter >= 0.0f) {
            this.voipCounter -= 1.25f * GameTime.getInstance().getMultiplier();
        }
        if (this.signalCounter >= 0.0f) {
            this.signalCounter -= 1.25f * GameTime.getInstance().getMultiplier();
        }
        if (this.soundCounter >= 0.0f) {
            this.soundCounter = (float) ((double) this.soundCounter - 1.25 * (double) GameTime.getInstance().getMultiplier());
        }
        if (this.signalCounter <= 0.0f && this.voipCounter <= 0.0f && this.lastRecordedDistance >= 0) {
            this.lastRecordedDistance = -1;
        }
        this.updateStaticSounds();
        if (GameClient.bClient) {
            this.updateEmitter();
        }
        if (this.doTriggerWorldSound && this.soundCounter <= 0.0f) {
            if (this.isTurnedOn && this.deviceVolume > 0.0f && (!this.isInventoryDevice() || this.headphoneType < 0)
                    && (!GameClient.bClient && !GameServer.bServer || GameClient.bClient && this.isInventoryDevice() || GameServer.bServer && !this.isInventoryDevice())) {
                IsoObject isoObject = null;
                if (this.parent != null && this.parent instanceof IsoObject) {
                    isoObject = (IsoObject) ((Object) this.parent);
                } else if (this.parent != null && this.parent instanceof Radio) {
                    isoObject = IsoPlayer.getInstance();
                } else if (this.parent instanceof VehiclePart) {
                    isoObject = ((VehiclePart) this.parent).getVehicle();
                }
                if (isoObject != null) {
                    int n = (int) (100.0f * this.deviceVolume);
                    int n2 = this.getDeviceSoundVolumeRange();
                    WorldSoundManager.instance.addSoundRepeating(isoObject, PZMath.fastfloor(isoObject.getX()), PZMath.fastfloor(isoObject.getY()), PZMath.fastfloor(isoObject.getZ()), n2, n, n > 50);
                }
            }
            this.doTriggerWorldSound = false;
            this.soundCounter = 300 + Rand.Next(0, 300);
        }
    }

    private void updateStaticSounds() {
        if (!this.isTurnedOn) {
            return;
        }
        float f = GameTime.getInstance().getMultiplier();
        this.nextStaticSound -= f;
        if (this.nextStaticSound <= 0.0f) {
            if (this.parent != null && this.signalCounter <= 0.0f && !this.isNoTransmit() && !this.isPlayingMedia()) {
                this.parent.AddDeviceText(ZomboidRadio.getInstance().getRandomBzztFzzt(), 1.0f, 1.0f, 1.0f, null, null, -1);
                this.doTriggerWorldSound = true;
            }
            this.setNextStaticSound();
        }
    }

    private void setNextStaticSound() {
        this.nextStaticSound = Rand.Next(250.0f, 1500.0f);
    }

    public int getDeviceVolumeRange() {
        return 5 + (int) (this.baseVolumeRange * this.deviceVolume);
    }

    public int getDeviceSoundVolumeRange() {
        if (this.isInventoryDevice()) {
            Radio radio = (Radio) this.getParent();
            if (radio.getPlayer() != null && radio.getPlayer().getSquare() != null && radio.getPlayer().getSquare().getRoom() != null) {
                return 3 + (int) (this.baseVolumeRange * 0.4f * this.deviceVolume);
            }
            return 5 + (int) (this.baseVolumeRange * this.deviceVolume);
        }
        if (this.isIsoDevice()) {
            IsoWaveSignal isoWaveSignal = (IsoWaveSignal) this.getParent();
            if (isoWaveSignal.getSquare() != null && isoWaveSignal.getSquare().getRoom() != null) {
                return 3 + (int) (this.baseVolumeRange * 0.5f * this.deviceVolume);
            }
            return 5 + (int) (this.baseVolumeRange * 0.75f * this.deviceVolume);
        }
        return 5 + (int) (this.baseVolumeRange / 2.0f * this.deviceVolume);
    }

    public void doReceiveSignal(int n) {
        if (this.isTurnedOn) {
            this.lastRecordedDistance = n;
            if (this.deviceVolume > 0.0f && (this.isIsoDevice() || this.headphoneType < 0)) {
                IsoObject isoObject = null;
                if (this.parent != null && this.parent instanceof IsoObject) {
                    isoObject = (IsoObject) ((Object) this.parent);
                } else if (this.parent != null && this.parent instanceof Radio) {
                    isoObject = IsoPlayer.getInstance();
                } else if (this.parent instanceof VehiclePart) {
                    isoObject = ((VehiclePart) this.parent).getVehicle();
                }
                if (isoObject != null && this.soundCounter <= 0.0f) {
                    int n2 = (int) (100.0f * this.deviceVolume);
                    int n3 = this.getDeviceSoundVolumeRange();
                    WorldSoundManager.instance.addSound(isoObject, (int) isoObject.getX(), (int) isoObject.getY(), (int) isoObject.getZ(), n3, n2, n2 > 50);
                    this.soundCounter = 120.0f;
                }
            }
            this.signalCounter = 300.0f;
            this.doTriggerWorldSound = true;
            this.setNextStaticSound();
        }
    }

    public void doReceiveMPSignal(float f) {
        this.lastRecordedDistance = (int) f;
        this.voipCounter = 10.0f;
    }

    public boolean isReceivingSignal() {
        return this.signalCounter > 0.0f || this.voipCounter > 0.0f;
    }

    public int getLastRecordedDistance() {
        return this.lastRecordedDistance;
    }

    public boolean isIsoDevice() {
        return this.getParent() != null && this.getParent() instanceof IsoWaveSignal;
    }

    public boolean isInventoryDevice() {
        return this.getParent() != null && this.getParent() instanceof Radio;
    }

    public boolean isVehicleDevice() {
        return this.getParent() instanceof VehiclePart;
    }

    public void transmitPresets() {
        this.transmitDeviceDataState((short) 5);
    }

    private void transmitDeviceDataState(short s) {
        if (GameClient.bClient) {
            try {
                VoiceManager.getInstance().UpdateChannelsRoaming(GameClient.connection);
                this.sendDeviceDataStatePacket(GameClient.connection, s);
            } catch (Exception exception) {
                System.out.print(exception.getMessage());
            }
        }
    }

    private void transmitDeviceDataStateServer(short s, UdpConnection udpConnection) {
        if (GameServer.bServer) {
            try {
                for (int i = 0; i < GameServer.udpEngine.connections.size(); ++i) {
                    UdpConnection udpConnection2 = GameServer.udpEngine.connections.get(i);
                    if (udpConnection != null && udpConnection == udpConnection2)
                        continue;
                    this.sendDeviceDataStatePacket(udpConnection2, s);
                }
            } catch (Exception exception) {
                System.out.print(exception.getMessage());
            }
        }
    }

    private void sendDeviceDataStatePacket(UdpConnection var1, short var2) {
        ByteBufferWriter var3 = var1.startPacket();
        PacketTypes.PacketType.RadioDeviceDataState.doPacket(var3);
        boolean var4 = false;
        if (this.isIsoDevice()) {
            IsoWaveSignal var5 = (IsoWaveSignal) this.getParent();
            IsoGridSquare var6 = var5.getSquare();
            if (var6 != null) {
                var3.putByte((byte) 1);
                var3.putInt(var6.getX());
                var3.putInt(var6.getY());
                var3.putInt(var6.getZ());
                var3.putInt(var6.getObjects().indexOf(var5));
                var4 = true;
            }
        } else if (this.isInventoryDevice()) {
            Radio var7 = (Radio) this.getParent();
            IsoPlayer var10 = null;
            if (var7.getEquipParent() != null && var7.getEquipParent() instanceof IsoPlayer) {
                var10 = (IsoPlayer) var7.getEquipParent();
            }

            if (var10 != null) {
                var3.putByte((byte) 0);
                if (GameServer.bServer) {
                    var3.putShort(var10 != null ? var10.OnlineID : -1);
                } else {
                    var3.putByte((byte) var10.PlayerIndex);
                }

                if (var10.getPrimaryHandItem() == var7) {
                    var3.putByte((byte) 1);
                } else if (var10.getSecondaryHandItem() == var7) {
                    var3.putByte((byte) 2);
                } else {
                    var3.putByte((byte) 0);
                }

                var4 = true;
            }
        } else if (this.isVehicleDevice()) {
            VehiclePart var8 = (VehiclePart) this.getParent();
            var3.putByte((byte) 2);
            var3.putShort(var8.getVehicle().VehicleID);
            var3.putShort((short) var8.getIndex());
            var4 = true;
        }

        if (var4) {
            var3.putShort(var2);
            switch (var2) {
                case 0:
                    var3.putByte((byte) (this.isTurnedOn ? 1 : 0));
                    break;
                case 1:
                    var3.putInt(this.channel);
                    break;
                case 2:
                    var3.putByte((byte) (this.hasBattery ? 1 : 0));
                    var3.putFloat(this.powerDelta);
                    break;
                case 3:
                    var3.putFloat(this.powerDelta);
                    break;
                case 4:
                    var3.putFloat(this.deviceVolume);
                    break;
                case 5:
                    var3.putInt(this.presets.getPresets().size());

                    for (PresetEntry var11 : this.presets.getPresets()) {
                        GameWindow.WriteString(var3.bb, var11.getName());
                        var3.putInt(var11.getFrequency());
                    }
                    break;
                case 6:
                    var3.putInt(this.headphoneType);
                    break;
                case 7:
                    var3.putShort(this.mediaIndex);
                    var3.putByte((byte) (this.mediaItem != null ? 1 : 0));
                    if (this.mediaItem != null) {
                        GameWindow.WriteString(var3.bb, this.mediaItem);
                    }
                    break;
                case 8:
                    if (GameServer.bServer) {
                        var3.putShort(this.mediaIndex);
                        var3.putByte((byte) (this.mediaItem != null ? 1 : 0));
                        if (this.mediaItem != null) {
                            GameWindow.WriteString(var3.bb, this.mediaItem);
                        }
                    }
                case 9:
                default:
                    break;
                case 10:
                    if (GameServer.bServer) {
                        var3.putShort(this.mediaIndex);
                        var3.putInt(this.mediaLineIndex);
                    }
            }

            PacketTypes.PacketType.RadioDeviceDataState.send(var1);
        } else {
            var1.cancelPacket();
        }

    }

    public void receiveDeviceDataStatePacket(ByteBuffer byteBuffer, UdpConnection udpConnection) throws IOException {
        if (!GameClient.bClient && !GameServer.bServer) {
            return;
        }
        boolean bl = GameServer.bServer;
        boolean bl2 = this.isIsoDevice() || this.isVehicleDevice();
        short s = byteBuffer.getShort();
        switch (s) {
            case 0: {
                if (bl && bl2) {
                    this.setIsTurnedOn(byteBuffer.get() == 1);
                } else {
                    boolean bl3 = this.isTurnedOn = byteBuffer.get() == 1;
                }
                if (!bl)
                    break;
                this.transmitDeviceDataStateServer(s, !bl2 ? udpConnection : null);
                break;
            }
            case 1: {
                int n = byteBuffer.getInt();
                if (bl && bl2) {
                    this.setChannel(n);
                } else {
                    this.channel = n;
                }
                if (!bl)
                    break;
                this.transmitDeviceDataStateServer(s, !bl2 ? udpConnection : null);
                break;
            }
            case 2: {
                boolean bl4 = byteBuffer.get() == 1;
                float f = byteBuffer.getFloat();
                if (bl && bl2) {
                    this.hasBattery = bl4;
                    this.setPower(f);
                } else {
                    this.hasBattery = bl4;
                    this.powerDelta = f;
                }
                if (!bl)
                    break;
                this.transmitDeviceDataStateServer(s, !bl2 ? udpConnection : null);
                break;
            }
            case 3: {
                float f = byteBuffer.getFloat();
                if (bl && bl2) {
                    this.setPower(f);
                } else {
                    this.powerDelta = f;
                }
                if (!bl)
                    break;
                this.transmitDeviceDataStateServer(s, !bl2 ? udpConnection : null);
                break;
            }
            case 4: {
                float f = byteBuffer.getFloat();
                if (bl && bl2) {
                    this.setDeviceVolume(f);
                } else {
                    this.deviceVolume = f;
                }
                if (!bl)
                    break;
                this.transmitDeviceDataStateServer(s, !bl2 ? udpConnection : null);
                break;
            }
            case 5: {
                int n = byteBuffer.getInt();
                for (int i = 0; i < n; ++i) {
                    String string = GameWindow.ReadString(byteBuffer);
                    int n2 = byteBuffer.getInt();
                    if (i < this.presets.getPresets().size()) {
                        PresetEntry presetEntry = this.presets.getPresets().get(i);
                        if (presetEntry.getName().equals(string) && presetEntry.getFrequency() == n2)
                            continue;
                        presetEntry.setName(string);
                        presetEntry.setFrequency(n2);
                        continue;
                    }
                    this.presets.addPreset(string, n2);
                }
                if (!bl)
                    break;
                this.transmitDeviceDataStateServer((short) 5, !bl2 ? udpConnection : null);
                break;
            }
            case 6: {
                this.headphoneType = byteBuffer.getInt();
                if (!bl)
                    break;
                this.transmitDeviceDataStateServer(s, !bl2 ? udpConnection : null);
                break;
            }
            case 7: {
                this.mediaIndex = byteBuffer.getShort();
                if (byteBuffer.get() == 1) {
                    this.mediaItem = GameWindow.ReadString(byteBuffer);
                }
                if (!bl)
                    break;
                this.transmitDeviceDataStateServer(s, !bl2 ? udpConnection : null);
                break;
            }
            case 8: {
                if (GameServer.bServer) {
                    this.StartPlayMedia();
                    break;
                }
                this.mediaLineIndex = 0;
                this.mediaIndex = byteBuffer.getShort();
                if (byteBuffer.get() == 1) {
                    this.mediaItem = GameWindow.ReadString(byteBuffer);
                }
                this.isPlayingMedia = true;
                if (this.isInventoryDevice()) {
                    this.playingMedia = ZomboidRadio.getInstance().getRecordedMedia().getMediaDataFromIndex(this.mediaIndex);
                }
                this.televisionMediaSwitch();
                break;
            }
            case 9: {
                if (GameServer.bServer) {
                    this.StopPlayMedia();
                    break;
                }
                this.isPlayingMedia = false;
                this.televisionMediaSwitch();
                break;
            }
            case 10: {
                if (!GameClient.bClient)
                    break;
                this.mediaIndex = byteBuffer.getShort();
                int n = byteBuffer.getInt();
                MediaData mediaData = this.getMediaData();
                if (mediaData == null || n < 0 || n >= mediaData.getLineCount())
                    break;
                MediaData.MediaLineData mediaLineData = mediaData.getLine(n);
                String string = mediaLineData.getTranslatedText();
                Color color = mediaLineData.getColor();
                String string2 = mediaLineData.getTextGuid();
                String string3 = mediaLineData.getCodes();
                this.parent.AddDeviceText(string, color.r, color.g, color.b, string2, string3, 0);
            }
        }
    }

    public void save(ByteBuffer byteBuffer, boolean bl) throws IOException {
        GameWindow.WriteString(byteBuffer, this.deviceName);
        byteBuffer.put(this.twoWay ? (byte) 1 : 0);
        byteBuffer.putInt(this.transmitRange);
        byteBuffer.putInt(this.micRange);
        byteBuffer.put(this.micIsMuted ? (byte) 1 : 0);
        byteBuffer.putFloat(this.baseVolumeRange);
        byteBuffer.putFloat(this.deviceVolume);
        byteBuffer.put(this.isPortable ? (byte) 1 : 0);
        byteBuffer.put(this.isTelevision ? (byte) 1 : 0);
        byteBuffer.put(this.isHighTier ? (byte) 1 : 0);
        byteBuffer.put(this.isTurnedOn ? (byte) 1 : 0);
        byteBuffer.putInt(this.channel);
        byteBuffer.putInt(this.minChannelRange);
        byteBuffer.putInt(this.maxChannelRange);
        byteBuffer.put(this.isBatteryPowered ? (byte) 1 : 0);
        byteBuffer.put(this.hasBattery ? (byte) 1 : 0);
        byteBuffer.putFloat(this.powerDelta);
        byteBuffer.putFloat(this.useDelta);
        byteBuffer.putInt(this.headphoneType);
        if (this.presets != null) {
            byteBuffer.put((byte) 1);
            this.presets.save(byteBuffer, bl);
        } else {
            byteBuffer.put((byte) 0);
        }
        byteBuffer.putShort(this.mediaIndex);
        byteBuffer.put(this.mediaType);
        byteBuffer.put(this.mediaItem != null ? (byte) 1 : 0);
        if (this.mediaItem != null) {
            GameWindow.WriteString(byteBuffer, this.mediaItem);
        }
        byteBuffer.put(this.noTransmit ? (byte) 1 : 0);
    }

    public void load(ByteBuffer byteBuffer, int n, boolean bl) throws IOException {
        if (this.presets == null) {
            this.presets = new DevicePresets();
        }
        this.deviceName = GameWindow.ReadString(byteBuffer);
        this.twoWay = byteBuffer.get() == 1;
        this.transmitRange = byteBuffer.getInt();
        this.micRange = byteBuffer.getInt();
        this.micIsMuted = byteBuffer.get() == 1;
        this.baseVolumeRange = byteBuffer.getFloat();
        this.deviceVolume = byteBuffer.getFloat();
        this.isPortable = byteBuffer.get() == 1;
        this.isTelevision = byteBuffer.get() == 1;
        this.isHighTier = byteBuffer.get() == 1;
        this.isTurnedOn = byteBuffer.get() == 1;
        this.channel = byteBuffer.getInt();
        this.minChannelRange = byteBuffer.getInt();
        this.maxChannelRange = byteBuffer.getInt();
        this.isBatteryPowered = byteBuffer.get() == 1;
        this.hasBattery = byteBuffer.get() == 1;
        this.powerDelta = byteBuffer.getFloat();
        this.useDelta = byteBuffer.getFloat();
        this.headphoneType = byteBuffer.getInt();
        if (byteBuffer.get() == 1) {
            this.presets.load(byteBuffer, n, bl);
        }
        this.mediaIndex = byteBuffer.getShort();
        this.mediaType = byteBuffer.get();
        if (byteBuffer.get() == 1) {
            this.mediaItem = GameWindow.ReadString(byteBuffer);
        }
        this.noTransmit = byteBuffer.get() == 1;
    }

    public boolean hasMedia() {
        return this.mediaIndex >= 0;
    }

    public short getMediaIndex() {
        return this.mediaIndex;
    }

    public void setMediaIndex(short s) {
        this.mediaIndex = s;
    }

    public byte getMediaType() {
        return this.mediaType;
    }

    public void setMediaType(byte by) {
        this.mediaType = by;
    }

    public void addMediaItem(InventoryItem inventoryItem) {
        ItemContainer itemContainer;
        if (this.mediaIndex < 0 && inventoryItem.isRecordedMedia() && inventoryItem.getMediaType() == this.mediaType && (itemContainer = inventoryItem.getContainer()) != null) {
            this.mediaIndex = inventoryItem.getRecordedMediaIndex();
            this.mediaItem = inventoryItem.getFullType();
            ItemUser.RemoveItem(inventoryItem);
            this.transmitDeviceDataState((short) 7);
        }
    }

    public InventoryItem removeMediaItem(ItemContainer itemContainer) {
        if (this.hasMedia()) {
            InventoryItem inventoryItem = InventoryItemFactory.CreateItem(this.mediaItem);
            inventoryItem.setRecordedMediaIndex(this.mediaIndex);
            itemContainer.AddItem(inventoryItem);
            this.mediaIndex = (short) -1;
            this.mediaItem = null;
            if (this.isPlayingMedia()) {
                this.StopPlayMedia();
            }
            this.transmitDeviceDataState((short) 7);
            return inventoryItem;
        }
        return null;
    }

    public boolean isPlayingMedia() {
        return this.isPlayingMedia;
    }

    public void StartPlayMedia() {
        if (GameClient.bClient) {
            this.transmitDeviceDataState((short) 8);
        } else if (!this.isPlayingMedia() && this.getIsTurnedOn() && this.hasMedia()) {
            this.playingMedia = ZomboidRadio.getInstance().getRecordedMedia().getMediaDataFromIndex(this.mediaIndex);
            if (this.playingMedia != null) {
                this.isPlayingMedia = true;
                this.mediaLineIndex = 0;
                this.prePlayingMedia();
                if (GameServer.bServer) {
                    this.transmitDeviceDataStateServer((short) 8, null);
                }
            }
        }
    }

    private void prePlayingMedia() {
        this.lineCounter = 60.0f * this.maxmod * 0.5f;
        this.televisionMediaSwitch();
    }

    private void postPlayingMedia() {
        this.isStoppingMedia = true;
        this.stopMediaCounter = 60.0f * this.maxmod * 0.5f;
        this.televisionMediaSwitch();
    }

    private void televisionMediaSwitch() {
        if (this.mediaType == 1) {
            ZomboidRadio.getInstance().getRandomBzztFzzt();
            this.parent.AddDeviceText(ZomboidRadio.getInstance().getRandomBzztFzzt(), 0.5f, 0.5f, 0.5f, null, null, 0);
            this.playSoundLocal("TelevisionZap", true);
        }
    }

    public void StopPlayMedia() {
        if (GameClient.bClient) {
            this.transmitDeviceDataState((short) 9);
        } else {
            if (GameServer.bServer) {
                this.isPlayingMedia = false;
            }
            this.playingMedia = null;
            this.postPlayingMedia();
            if (GameServer.bServer) {
                this.transmitDeviceDataStateServer((short) 9, null);
            }
        }
    }

    public void updateMediaPlaying() {
        if (!(!GameClient.bClient || this.isTurnedOn && this.deviceVolume > 0.0f && this.isInventoryDevice() && this.headphoneType >= 0)) {
            return;
        }
        if (this.isStoppingMedia) {
            this.stopMediaCounter -= 1.25f * GameTime.getInstance().getMultiplier();
            if (this.stopMediaCounter <= 0.0f) {
                this.isPlayingMedia = false;
                this.isStoppingMedia = false;
            }
            return;
        }
        if (this.hasMedia() && this.isPlayingMedia()) {
            if (!this.getIsTurnedOn()) {
                this.StopPlayMedia();
                return;
            }
            if (this.playingMedia != null) {
                this.lineCounter -= 1.25f * GameTime.getInstance().getMultiplier();
                if (this.lineCounter <= 0.0f) {
                    MediaData.MediaLineData mediaLineData = this.playingMedia.getLine(this.mediaLineIndex);
                    if (mediaLineData != null) {
                        String string = mediaLineData.getTranslatedText();
                        Color color = mediaLineData.getColor();
                        this.lineCounter = (float) string.length() / 10.0f * 60.0f;
                        if (this.lineCounter < 60.0f * this.minmod) {
                            this.lineCounter = 60.0f * this.minmod;
                        } else if (this.lineCounter > 60.0f * this.maxmod) {
                            this.lineCounter = 60.0f * this.maxmod;
                        }
                        if (GameServer.bServer) {
                            this.currentMediaLine = string;
                            this.currentMediaColor = color;
                            this.transmitDeviceDataStateServer((short) 10, null);
                        } else {
                            String string2 = mediaLineData.getTextGuid();
                            String string3 = mediaLineData.getCodes();
                            this.parent.AddDeviceText(string, color.r, color.g, color.b, string2, string3, 0);
                        }
                        ++this.mediaLineIndex;
                    } else {
                        this.StopPlayMedia();
                    }
                }
            }
        }
    }

    public MediaData getMediaData() {
        if (this.mediaIndex >= 0) {
            return ZomboidRadio.getInstance().getRecordedMedia().getMediaDataFromIndex(this.mediaIndex);
        }
        return null;
    }

    public boolean isNoTransmit() {
        return this.noTransmit;
    }

    public void setNoTransmit(boolean bl) {
        this.noTransmit = bl;
    }

    public boolean isEmergencyBroadcast() {
        if (this.isTelevision) {
            return false;
        }
        int n = this.getChannel();
        Map<Integer, String> map = ZomboidRadio.getInstance().GetChannelList("Emergency");
        if (map == null) {
            return false;
        }
        for (Map.Entry<Integer, String> entry : map.entrySet()) {
            if (entry.getKey() != n)
                continue;
            return true;
        }
        return false;
    }

    @Override
    public FMODParameterList getFMODParameters() {
        return this.parameterList;
    }

    @Override
    public void startEvent(long l, GameSoundClip gameSoundClip, BitSet bitSet) {
        FMODParameterList fMODParameterList = this.getFMODParameters();
        ArrayList<FMOD_STUDIO_PARAMETER_DESCRIPTION> arrayList = gameSoundClip.eventDescription.parameters;
        for (int i = 0; i < arrayList.size(); ++i) {
            FMODParameter fMODParameter;
            FMOD_STUDIO_PARAMETER_DESCRIPTION fMOD_STUDIO_PARAMETER_DESCRIPTION = arrayList.get(i);
            if (bitSet.get(fMOD_STUDIO_PARAMETER_DESCRIPTION.globalIndex) || (fMODParameter = fMODParameterList.get(fMOD_STUDIO_PARAMETER_DESCRIPTION)) == null)
                continue;
            fMODParameter.startEventInstance(l);
        }
    }

    @Override
    public void updateEvent(long l, GameSoundClip gameSoundClip) {
    }

    @Override
    public void stopEvent(long l, GameSoundClip gameSoundClip, BitSet bitSet) {
        FMODParameterList fMODParameterList = this.getFMODParameters();
        ArrayList<FMOD_STUDIO_PARAMETER_DESCRIPTION> arrayList = gameSoundClip.eventDescription.parameters;
        for (int i = 0; i < arrayList.size(); ++i) {
            FMODParameter fMODParameter;
            FMOD_STUDIO_PARAMETER_DESCRIPTION fMOD_STUDIO_PARAMETER_DESCRIPTION = arrayList.get(i);
            if (bitSet.get(fMOD_STUDIO_PARAMETER_DESCRIPTION.globalIndex) || (fMODParameter = fMODParameterList.get(fMOD_STUDIO_PARAMETER_DESCRIPTION)) == null)
                continue;
            fMODParameter.stopEventInstance(l);
        }
    }
}

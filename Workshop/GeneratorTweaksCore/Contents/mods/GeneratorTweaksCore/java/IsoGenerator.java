package zombie.iso.objects;

import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import zombie.GameTime;
import zombie.SandboxOptions;
import zombie.WorldSoundManager;
import zombie.audio.BaseSoundEmitter;
import zombie.core.Translator;
import zombie.core.logger.ExceptionLogger;
import zombie.core.math.PZMath;
import zombie.core.network.ByteBufferWriter;
import zombie.core.properties.PropertyContainer;
import zombie.core.random.Rand;
import zombie.inventory.InventoryItem;
import zombie.inventory.ItemContainer;
import zombie.inventory.types.Food;
import zombie.iso.IsoCell;
import zombie.iso.IsoChunk;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.IsoUtils;
import zombie.iso.IsoWorld;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.objects.IsoClothingDryer;
import zombie.iso.objects.IsoClothingWasher;
import zombie.iso.objects.IsoCombinationWasherDryer;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoFireManager;
import zombie.iso.objects.IsoLightSwitch;
import zombie.iso.objects.IsoRadio;
import zombie.iso.objects.IsoStackedWasherDryer;
import zombie.iso.objects.IsoStove;
import zombie.iso.objects.IsoTelevision;
import zombie.iso.objects.IsoWindow;
import zombie.iso.objects.IsoWorldInventoryObject;
import zombie.iso.sprite.IsoSpriteManager;
import zombie.network.GameClient;
import zombie.network.GameServer;
import zombie.network.ServerMap;
import zombie.scripting.ScriptManager;
import zombie.scripting.objects.Item;

public class IsoGenerator extends IsoObject {
    private static final int MaximumGeneratorCondition = 100;
    private static final float MaximumGeneratorFuel = 100.0f;
    private static final int GeneratorMinimumCondition = 0;
    private static final int GeneratorCriticalCondition = 20;
    private static final int GeneratorWarningCondition = 30;
    private static final int GeneratorLowCondition = 40;
    private static final int GeneratorBackfireChanceCritical = 5;
    private static final int GeneratorBackfireChanceWarning = 10;
    private static final int GeneratorBackfireChanceLow = 15;
    private static final int GeneratorFireChance = 10;
    private static final int GeneratorExplodeChance = 20;
    private static final float GeneratorSoundOffset = 0.5f;
    private static final int GeneratorDefaultSoundRadius = 40;
    private static final int GeneratorDefaultSoundVolume = 60;
    private static final int GeneratorConditionLowerChanceDefault = 30;
    private static final float GeneratorBasePowerConsumption = 0.02f;
    private static int GeneratorVerticalPowerRange = 3;
    private static final int IsoGeneratorFireStartingEnergy = 1000;
    private static int GeneratorChunkRange = 2;
    private static final int GeneratorMinZ = 0;
    private static final int GeneratorMaxZ = 8;
    private static final float ClothingAppliancePowerConsumption = 0.09f;
    private static final float TelevisionPowerConsumption = 0.03f;
    private static final float RadioPowerConsumption = 0.01f;
    private static final float StovePowerConsumption = 0.09f;
    private static final float FridgeFreezerPowerConsumption = 0.13f;
    private static final float SingleFridgeOrFreezerPowerConsumption = 0.08f;
    private static final float LightSwitchPowerConsumption = 0.002f;
    private static final float PipedFuelPowerConsumption = 0.03f;
    private static final float StackedWasherDryerPowerConsumption = 0.9f;
    public float fuel = 0.0f;
    public int fuelCycle = 0;
    public boolean activated = false;
    public int condition = 0;
    public int conditionCycle = 0;
    private int lastHour = -1;
    public boolean connected = false;
    private boolean updateSurrounding = false;
    private final HashMap<String, String> itemsPowered = new HashMap();
    private float totalPowerUsing = 0.0f;
    private static final ArrayList<IsoGenerator> AllGenerators = new ArrayList();
    private static int GENERATOR_RADIUS = 20;
    private static final int GENERATOR_SOUND_RADIUS = 20;
    private static final int GENERATOR_SOUND_VOLUME = 20;
    public static String ribsVersion = "1.0.0";

    public IsoGenerator(IsoCell v1) {
        super(v1);
        this.setGeneratorRange();
    }

    public IsoGenerator(InventoryItem v1, IsoCell v2, IsoGridSquare v3) {
        super(v2, v3, IsoSpriteManager.instance.getSprite(v1.getScriptItem().getWorldObjectSprite()));
        String v4 = v1.getScriptItem().getWorldObjectSprite();
        this.setInfoFromItem(v1);
        this.sprite = IsoSpriteManager.instance.getSprite(v4);
        this.square = v3;
        v3.AddSpecialObject(this);
        if (GameServer.bServer) {
            this.transmitCompleteItemToClients();
        }
        this.setGeneratorRange();
    }

    public IsoGenerator(InventoryItem v1, IsoCell v2, IsoGridSquare v3, boolean i4) {
        super(v2, v3, IsoSpriteManager.instance.getSprite(v1.getScriptItem().getWorldObjectSprite()));
        String v5 = v1.getScriptItem().getWorldObjectSprite();
        this.setInfoFromItem(v1);
        this.sprite = IsoSpriteManager.instance.getSprite(v5);
        this.square = v3;
        v3.AddSpecialObject(this);
        if (GameClient.bClient && !i4) {
            this.transmitCompleteItemToServer();
        }
        this.setGeneratorRange();
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

    private int getHorizontalRange() {
        int defaultSandboxValue = SandboxOptions.getInstance().GeneratorTileRange.getValue();
        int newHorizontal = this.getSandboxValue("GeneratorTweaksPower.GeneratorTileRange", defaultSandboxValue);

        return newHorizontal;
    }

    private int getVerticalRange() {
        int defaultSandboxValue = SandboxOptions.getInstance().GeneratorVerticalPowerRange.getValue();
        int newVertical = this.getSandboxValue("GeneratorTweaksPower.GeneratorVerticalPowerRange", defaultSandboxValue);

        return newVertical;
    }

    private void setGeneratorRange() {
        GeneratorVerticalPowerRange = this.getVerticalRange();
        GENERATOR_RADIUS = this.getHorizontalRange();
        GeneratorChunkRange = GENERATOR_RADIUS / 10 + 1;
    }

    private boolean checkRangeChanged() {
        if (this.getVerticalRange() != GeneratorVerticalPowerRange) {
            return true;
        }

        if (this.getHorizontalRange() != GENERATOR_RADIUS) {
            return true;
        }

        return false;
    }

    public void setInfoFromItem(InventoryItem inventoryItem) {
        this.condition = inventoryItem.getCondition();
        if (inventoryItem.getModData().rawget("fuel") instanceof Double) {
            this.fuel = ((Double) inventoryItem.getModData().rawget("fuel")).floatValue();
        }
        this.getModData().rawset("generatorFullType", (Object) String.valueOf(inventoryItem.getFullType()));
    }

    private IsoGridSquare[] getObjectSides(IsoObject object) {
        boolean isNorth;
        IsoGridSquare objectSquare = object.getSquare();
        if (objectSquare == null) {
            return null;
        }
        if (object instanceof IsoDoor) {
            isNorth = ((IsoDoor) object).getNorth();
        } else if (object instanceof IsoWindow) {
            isNorth = ((IsoWindow) object).getNorth();
        } else {
            return null;
        }
        IsoCell cell = objectSquare.getCell();
        int z = objectSquare.getZ();
        int x = objectSquare.getX();
        int y = objectSquare.getY();
        IsoGridSquare north = cell.getGridSquare(x, y - 1, z);
        IsoGridSquare south = cell.getGridSquare(x, y + 1, z);
        IsoGridSquare west = cell.getGridSquare(x - 1, y, z);
        IsoGridSquare east = cell.getGridSquare(x + 1, y, z);
        IsoGridSquare firstSide = isNorth ? north : west;
        IsoGridSquare secondSide = isNorth ? south : east;
        return new IsoGridSquare[] { firstSide, secondSide };
    }

    private boolean isFacingOutside(IsoObject object) {
        boolean checkFacingOutside = this.getSandboxValue("GeneratorTweaksIndoors.CheckFacingOutside", true);
        if (!checkFacingOutside) {
            return true;
        }
        IsoGridSquare[] sides = this.getObjectSides(object);
        if (sides == null) {
            return true;
        }
        IsoGridSquare firstSide = sides[0];
        IsoGridSquare secondSide = sides[1];
        boolean firstOutside = firstSide != null && firstSide.getProperties().Is(IsoFlagType.exterior);
        boolean secondOutside = secondSide != null && secondSide.getProperties().Is(IsoFlagType.exterior);
        return firstOutside || secondOutside;
    }

    private boolean isSameRoom(IsoObject object) {
        boolean checkSameRoom = this.getSandboxValue("GeneratorTweaksIndoors.CheckSameRoom", true);
        if (!checkSameRoom) {
            return true;
        }
        IsoGridSquare startSquare = this.getSquare();
        if (startSquare == null) {
            return true;
        }
        long startRoomId = startSquare.getRoomID();
        if (startRoomId < 0L) {
            return true;
        }
        IsoGridSquare[] sides = this.getObjectSides(object);
        if (sides == null) {
            return true;
        }
        IsoGridSquare firstSide = sides[0];
        IsoGridSquare secondSide = sides[1];
        boolean firstSameRoom = firstSide != null && firstSide.getRoomID() == startRoomId;
        boolean secondSameRoom = secondSide != null && secondSide.getRoomID() == startRoomId;
        return firstSameRoom || secondSameRoom;
    }

    private boolean hasOpenDoorWindow() {
        boolean enabled = this.getSandboxValue("GeneratorTweaksIndoors.Enabled", false);
        if (!enabled) {
            return false;
        }
        boolean scanForWindowsDoors = this.getSandboxValue("GeneratorTweaksIndoors.ScanForWindowsDoors", true);
        if (!scanForWindowsDoors) {
            return true;
        }
        IsoGridSquare startSquare = this.getSquare();
        if (startSquare == null) {
            return true;
        }
        int range = this.getSandboxValue("GeneratorTweaksIndoors.Range", 1);

        range = range < 1 ? 1 : range;

        IsoCell cell = startSquare.getCell();
        int squareZ = startSquare.getZ();
        int squareX = startSquare.getX();
        int squareY = startSquare.getY();
        for (int offsetX = -range; offsetX <= range; ++offsetX) {
            for (int offsetY = -range; offsetY <= range; ++offsetY) {
                IsoGridSquare currentSquare = cell.getGridSquare(squareX + offsetX, squareY + offsetY, squareZ);
                if (currentSquare == null) {
                    continue;
                }

                for (int index = 0; index < currentSquare.getObjects().size(); ++index) {
                    IsoObject object = currentSquare.getObjects().get(index);
                    if (object == null) {
                        continue;
                    }

                    if (!((object instanceof IsoDoor) || (object instanceof IsoWindow))) {
                        continue;
                    }

                    if ((object instanceof IsoDoor) && !((IsoDoor) object).IsOpen()) {
                        continue;
                    }

                    if ((object instanceof IsoWindow) && !((IsoWindow) object).IsOpen()) {
                        continue;
                    }

                    if (!this.isFacingOutside(object)) {
                        continue;
                    }

                    if (!this.isSameRoom(object)) {
                        continue;
                    }

                    return true;
                }
            }
        }
        return false;
    }

    public void checkLowFuel() {
        boolean lowFuelSoundEnabled = this.getSandboxValue("GeneratorTweaksFuel.LowFuelSoundEnabled", false);
        float lowFuelThreshold = this.getSandboxValue("GeneratorTweaksFuel.LowFuelSoundThreshold", 10.0f);

        if (!lowFuelSoundEnabled || this.fuel > lowFuelThreshold) {
            return;
        }

        String lowFuelSound = this.getSandboxValue("GeneratorTweaksFuel.LowFuelSoundFile", "MicrowaveTimerExpired");
        float lowFuelVolume = this.getSandboxValue("GeneratorTweaksFuel.LowFuelSoundVolume", 3.0f);
        this.playGeneratorSound(lowFuelSound, lowFuelVolume);
    }

    public BaseSoundEmitter getEmitter() {
        if (GameServer.bServer) {
            return null;
        }

        if (this.emitter == null) {
            this.emitter = IsoWorld.instance.getFreeEmitter(this.getX() + 0.5f, this.getY() + 0.5f, this.getZ());
            IsoWorld.instance.takeOwnershipOfEmitter(this.emitter);
        }

        return this.emitter;
    }

    public void emitTick() {
        if (this.getEmitter() != null) {
            this.getEmitter().tick();
        }
    }

    public void emitPlayerSoundLoop() {
        if (this.getEmitter() == null) {
            return;
        }

        String runningSoundFile = this.getSandboxValue("GeneratorTweaksSound.RunningSoundFile", "GeneratorLoop");
        if (this.getEmitter().isPlaying(runningSoundFile)) {
            return;
        }

        float runningVolume = this.getSandboxValue("GeneratorTweaksSound.RunningVolume", 1.0f);
        this.playGeneratorSound(runningSoundFile, runningVolume);
    }

    public void emitZombieSoundLoop() {
        int runningZombieRadius = this.getSandboxValue("GeneratorTweaksSound.RunningZombieRadius", 20);
        int runningZombieVolume = this.getSandboxValue("GeneratorTweaksSound.RunningZombieVolume", 20);

        int x = PZMath.fastfloor(this.getX());
        int y = PZMath.fastfloor(this.getY());
        int z = PZMath.fastfloor(this.getZ());

        WorldSoundManager.instance.addSoundRepeating(this, x, y, z, runningZombieRadius, runningZombieVolume, false);
    }

    @Override
    public void update() {
        if (this.updateSurrounding && this.getSquare() != null) {
            this.setSurroundingElectricity();
            this.updateSurrounding = false;
        }

        if (!this.isActivated()) {
            this.emitTick();
            return;
        }

        if (this.checkRangeChanged() && this.getSquare() != null) {
            this.setGeneratorRange();
            this.setSurroundingElectricity();
        }

        this.emitPlayerSoundLoop();

        if (GameClient.bClient) {
            this.emitTick();
            return;
        }

        this.emitZombieSoundLoop();

        boolean hourChanged = (int) GameTime.getInstance().getWorldAgeHours() != this.lastHour;
        if (!hourChanged) {
            this.emitTick();
            return;
        }

        if (!this.getSquare().getProperties().Is(IsoFlagType.exterior) && this.getSquare().getBuilding() != null) {
            this.getSquare().getBuilding().setToxic(false);
            this.getSquare().getBuilding().setToxic(this.isActivated() && !this.hasOpenDoorWindow());
        }

        int hoursElapsed = (int) GameTime.getInstance().getWorldAgeHours() - this.lastHour;
        float fuelSpent = 0.0f;
        float newFuelValue = this.fuel;
        double fuelConsumptionMultiplier = SandboxOptions.instance.GeneratorFuelConsumption.getValue();

        int newCondition = this.condition;
        float conditionLossProbability = this.getSandboxValue("GeneratorTweaksCondition.ConditionLossProbability", 0.033f);
        int conditionLossMin = this.getSandboxValue("GeneratorTweaksCondition.ConditionLossMin", 1);
        int conditionLossMax = this.getSandboxValue("GeneratorTweaksCondition.ConditionLossMax", 2);

        for (int i = 0; i < hoursElapsed; ++i) {
            float consumedFuel = (float) ((double) this.totalPowerUsing * fuelConsumptionMultiplier);
            newFuelValue -= consumedFuel;

            if (Rand.NextBoolFromChance(conditionLossProbability)) {
                int conditionLost = Rand.Next(conditionLossMin, conditionLossMax + 1);
                newCondition -= conditionLost;
            }

            if (newFuelValue <= 0.0f || newCondition <= 0) {
                break;
            }
        }

        int fuelCalculationCycle = this.getSandboxValue("GeneratorTweaksFuel.FuelConsumptionInterval", 1);
        this.fuelCycle = this.fuelCycle + 1;
        if (this.fuelCycle >= fuelCalculationCycle) {
            this.fuel = newFuelValue;
            this.fuelCycle = 0;
        }
        this.checkLowFuel();
        if (this.fuel <= 0.0f) {
            this.setActivated(false);
            this.fuel = 0.0f;
        }
        int conditionLossInterval = this.getSandboxValue("GeneratorTweaksCondition.ConditionLossInterval", 1);
        this.conditionCycle = this.conditionCycle + 1;
        if (this.conditionCycle >= conditionLossInterval) {
            this.condition = newCondition;
            this.conditionCycle = 0;
        }
        if (this.condition <= 0) {
            this.setActivated(false);
            this.condition = 0;
        }

        float backfireThreshold = this.getSandboxValue("GeneratorTweaksCondition.BackfireThreshold", 40.0f);
        boolean triggerBackfire = false;
        if (this.condition < backfireThreshold) {
            float conditionSeverity = backfireThreshold - this.condition;
            float conditionSeverityPercent = conditionSeverity / backfireThreshold;
            float backfireMaxChance = this.getSandboxValue("GeneratorTweaksCondition.BackfireMaxChance", 0.2f);
            float relativeBackfireChance = backfireMaxChance * conditionSeverityPercent;
            triggerBackfire = Rand.NextBoolFromChance(relativeBackfireChance);
        }
        if (triggerBackfire) {
            String backfireSoundFile = this.getSandboxValue("GeneratorTweaksSound.BackfireSoundFile", "GeneratorBackfire");
            float backfireVolume = this.getSandboxValue("GeneratorTweaksSound.BackfireVolume", 1.0f);
            this.playGeneratorSound(backfireSoundFile, backfireVolume);

            int backfireZombieRadius = this.getSandboxValue("GeneratorTweaksSound.BackfireZombieRadius", 40);
            int backfireZombieVolume = this.getSandboxValue("GeneratorTweaksSound.BackfireZombieVolume", 60);
            WorldSoundManager.instance.addSound(this, this.square.getX(), this.square.getY(),
                    this.square.getZ(), backfireZombieRadius, backfireZombieVolume, false, 0.0f, 15.0f);
        }

        float failureThreshold = this.getSandboxValue("GeneratorTweaksCondition.FailureThreshold", 20.0f);
        if (this.condition < failureThreshold) {
            boolean triggerFire = false;
            boolean triggerExplosion = false;

            float conditionSeverity = failureThreshold - this.condition;
            float conditionSeverityPercent = conditionSeverity / failureThreshold;

            float fireMaxChance = this.getSandboxValue("GeneratorTweaksCondition.FireMaxChance", 0.1f);
            float fireChance = fireMaxChance * conditionSeverityPercent;

            float explodeMaxChance = this.getSandboxValue("GeneratorTweaksCondition.ExplodeMaxChance", 0.05f);
            float explodeChance = explodeMaxChance * conditionSeverityPercent;

            triggerFire = Rand.NextBoolFromChance(fireChance);
            triggerExplosion = Rand.NextBoolFromChance(explodeChance);

            if (triggerFire) {
                IsoFireManager.StartFire(this.getCell(), this.square, true, 1000);
            } else if (triggerExplosion) {
                this.square.explode();
            }

            if (triggerFire || triggerExplosion) {
                this.condition = 0;
                this.setActivated(false);
            }
        }
        this.lastHour = (int) GameTime.getInstance().getWorldAgeHours();
        if (GameServer.bServer) {
            this.syncIsoObject(false, (byte) 0, null, null);
        }

        if (this.getEmitter() != null) {
            this.getEmitter().tick();
        }
    }

    public void setIsoObjectEletricity(IsoObject isoObject) {
        if (isoObject == null || isoObject instanceof IsoWorldInventoryObject) {
            return;
        }

        if (isoObject instanceof IsoClothingDryer && ((IsoClothingDryer) isoObject).isActivated()) {
            float clothingDryerConsumption = this.getSandboxValue("GeneratorTweaksFuel.ClothingDryerConsumption", 0.09f);
            this.addPoweredItem(isoObject, clothingDryerConsumption);
        }

        if (isoObject instanceof IsoClothingWasher && ((IsoClothingWasher) isoObject).isActivated()) {
            float clothingWasherConsumption = this.getSandboxValue("GeneratorTweaksFuel.ClothingWasherConsumption", 0.09f);
            this.addPoweredItem(isoObject, clothingWasherConsumption);
        }

        if (isoObject instanceof IsoCombinationWasherDryer && ((IsoCombinationWasherDryer) isoObject).isActivated()) {
            float comboWasherDryerConsumption = this.getSandboxValue("GeneratorTweaksFuel.ComboWasherDryerConsumption", 0.09f);
            this.addPoweredItem(isoObject, comboWasherDryerConsumption);
        }

        if (isoObject instanceof IsoStackedWasherDryer) {
            IsoStackedWasherDryer castedObject = (IsoStackedWasherDryer) isoObject;
            float stackedConsumption = 0.0f;
            if (castedObject.isDryerActivated()) {
                float stackedDryerConsumption = this.getSandboxValue("GeneratorTweaksFuel.StackedDryerConsumption", 0.9f);
                stackedConsumption += stackedDryerConsumption;
            }
            if (castedObject.isWasherActivated()) {
                float stackedWasherConsumption = this.getSandboxValue("GeneratorTweaksFuel.StackedWasherConsumption", 0.9f);
                stackedConsumption += stackedWasherConsumption;
            }
            if (stackedConsumption > 0.0f) {
                this.addPoweredItem(isoObject, stackedConsumption);
            }
        }

        if (isoObject instanceof IsoTelevision && ((IsoTelevision) isoObject).getDeviceData().getIsTurnedOn()) {
            float televisionConsumption = this.getSandboxValue("GeneratorTweaksFuel.TelevisionConsumption", 0.03f);
            this.addPoweredItem(isoObject, televisionConsumption);
        }

        if (isoObject instanceof IsoRadio && ((IsoRadio) isoObject).getDeviceData().getIsTurnedOn()
                && !((IsoRadio) isoObject).getDeviceData().getIsBatteryPowered()) {
            float radioConsumption = this.getSandboxValue("GeneratorTweaksFuel.RadioConsumption", 0.01f);
            this.addPoweredItem(isoObject, radioConsumption);
        }

        if (isoObject instanceof IsoStove && ((IsoStove) isoObject).Activated()) {
            float stoveConsumption = this.getSandboxValue("GeneratorTweaksFuel.StoveConsumption", 0.09f);
            this.addPoweredItem(isoObject, stoveConsumption);
        }

        boolean fridgeContainer = isoObject.getContainerByType("fridge") != null;
        boolean freezerContainer = isoObject.getContainerByType("freezer") != null;

        if (fridgeContainer && freezerContainer) {
            float fridgeFreezerComboConsumption = this.getSandboxValue("GeneratorTweaksFuel.FridgeFreezerComboConsumption", 0.13f);
            this.addPoweredItem(isoObject, fridgeFreezerComboConsumption);
        } else if (fridgeContainer || freezerContainer) {
            float singleFridgeOrFreezerConsumption = this.getSandboxValue("GeneratorTweaksFuel.SingleFridgeOrFreezerConsumption", 0.08f);
            this.addPoweredItem(isoObject, singleFridgeOrFreezerConsumption);
        }

        if (isoObject instanceof IsoLightSwitch && ((IsoLightSwitch) isoObject).Activated && !((IsoLightSwitch) isoObject).bStreetLight) {
            float lightSwitchConsumption = this.getSandboxValue("GeneratorTweaksFuel.LightSwitchConsumption", 0.002f);
            this.addPoweredItem(isoObject, lightSwitchConsumption);
        }

        if (isoObject.getPipedFuelAmount() > 0) {
            float fuelPumpConsumption = this.getSandboxValue("GeneratorTweaksFuel.FuelPumpConsumption", 0.03f);
            this.addPoweredItem(isoObject, fuelPumpConsumption);
        }

        isoObject.checkHaveElectricity();
    }

    private IsoGridSquare getPoweredIsoSquare(int x, int y, int z) {
        IsoGridSquare isoSquare = this.getCell().getGridSquare(x, y, z);

        if (isoSquare == null) {
            return null;
        }

        float squarePosition = IsoUtils.DistanceToSquared(
                (float) x + 0.5f,
                (float) y + 0.5f,
                (float) this.getSquare().getX() + 0.5f,
                (float) this.getSquare().getY() + 0.5f);

        if (squarePosition > (float) (GENERATOR_RADIUS * GENERATOR_RADIUS)) {
            return null;
        }

        return isoSquare;
    }

    public void setSurroundingElectricity() {
        this.itemsPowered.clear();
        this.totalPowerUsing = 0.02f;
        if (this.square == null || this.square.chunk == null) {
            return;
        }
        int chunkWX = this.square.chunk.wx;
        int chunkWY = this.square.chunk.wy;
        for (int x = -GeneratorChunkRange; x <= GeneratorChunkRange; ++x) {
            for (int y = -GeneratorChunkRange; y <= GeneratorChunkRange; ++y) {
                IsoChunk isoChunk = null;

                if (GameServer.bServer) {
                    isoChunk = ServerMap.instance.getChunk(chunkWX + y, chunkWY + x);
                } else {
                    isoChunk = IsoWorld.instance.CurrentCell.getChunk(chunkWX + y, chunkWY + x);
                }

                if (isoChunk == null || !this.touchesChunk(isoChunk))
                    continue;
                if (this.isActivated()) {
                    isoChunk.addGeneratorPos(this.square.x, this.square.y, this.square.z);
                    continue;
                }
                isoChunk.removeGeneratorPos(this.square.x, this.square.y, this.square.z);
            }
        }

        int startX = this.square.getX() - GENERATOR_RADIUS;
        int endX = this.square.getX() + GENERATOR_RADIUS;
        int startY = this.square.getY() - GENERATOR_RADIUS;
        int endY = this.square.getY() + GENERATOR_RADIUS;
        int startZ = this.getSquare().getZ() - GeneratorVerticalPowerRange;
        int endZ = this.getSquare().getZ() + GeneratorVerticalPowerRange;

        for (int x = startX; x <= endX; ++x) {
            for (int y = startY; y <= endY; ++y) {
                for (int z = startZ; z < endZ; ++z) {
                    IsoGridSquare isoGridSquare = this.getPoweredIsoSquare(x, y, z);
                    if (isoGridSquare == null) {
                        continue;
                    }

                    for (int isoObjectIndex = 0; isoObjectIndex < isoGridSquare.getObjects().size(); ++isoObjectIndex) {
                        IsoObject isoObject = isoGridSquare.getObjects().get(isoObjectIndex);
                        this.setIsoObjectEletricity(isoObject);
                    }
                }
            }
        }
    }

    private void addPoweredItem(IsoObject isoObject, float consumption) {
        String itemName = Translator.getText("IGUI_VehiclePartCatOther");
        if (isoObject.getPipedFuelAmount() > 0) {
            itemName = Translator.getText("IGUI_GasPump");
        }
        PropertyContainer objectProps = isoObject.getProperties();
        if (objectProps != null && objectProps.Is("CustomName")) {
            String movableObject = "Moveable Object";
            if (objectProps.Is("CustomName")) {
                movableObject = objectProps.Is("GroupName")
                        ? objectProps.Val("GroupName") + " " + objectProps.Val("CustomName")
                        : objectProps.Val("CustomName");
            }
            itemName = Translator.getMoveableDisplayName(movableObject);
        }
        if (isoObject instanceof IsoLightSwitch) {
            itemName = Translator.getText("IGUI_Lights");
        }
        int itemConsumption = 1;
        for (String itemKey : this.itemsPowered.keySet()) {
            if (!itemKey.startsWith(itemName))
                continue;
            itemConsumption = Integer.parseInt(itemKey.replaceAll("[\\D]", ""));
            this.totalPowerUsing -= consumption * (float) itemConsumption;
            ++itemConsumption;
            this.itemsPowered.remove(itemKey);
            break;
        }
        this.itemsPowered.put(itemName + " x" + itemConsumption, new DecimalFormat(" (#.### L/h)").format(consumption * (float) itemConsumption));
        this.totalPowerUsing += consumption * (float) itemConsumption;
    }

    private void updateFridgeFreezerItems(IsoObject v1) {
        for (int i2 = 0; i2 < v1.getContainerCount(); ++i2) {
            ItemContainer v3 = v1.getContainerByIndex(i2);
            if (!"fridge".equals(v3.getType()) && !"freezer".equals(v3.getType()))
                continue;
            ArrayList<InventoryItem> v4 = v3.getItems();
            for (int i5 = 0; i5 < v4.size(); ++i5) {
                InventoryItem v6 = v4.get(i5);
                if (!(v6 instanceof Food))
                    continue;
                v6.updateAge();
            }
        }
    }

    private void updateFridgeFreezerItems(IsoGridSquare v1) {
        int i2 = v1.getObjects().size();
        IsoObject[] v3 = v1.getObjects().getElements();
        for (int i4 = 0; i4 < i2; ++i4) {
            IsoObject v5 = v3[i4];
            this.updateFridgeFreezerItems(v5);
        }
    }

    private void updateFridgeFreezerItems() {
        if (this.square == null) {
            return;
        }
        int startX = this.square.getX() - GENERATOR_RADIUS;
        int endX = this.square.getX() + GENERATOR_RADIUS;
        int startY = this.square.getY() - GENERATOR_RADIUS;
        int endY = this.square.getY() + GENERATOR_RADIUS;
        int startZ = this.square.getZ() - GeneratorVerticalPowerRange;
        int endZ = this.square.getZ() + GeneratorVerticalPowerRange;
        float maxSquareDistance = (float) (GENERATOR_RADIUS * GENERATOR_RADIUS);

        for (int x = startX; x <= endX; ++x) {
            for (int y = startY; y <= endY; ++y) {
                for (int z = startZ; z < endZ; ++z) {
                    if (IsoUtils.DistanceToSquared(x, y, this.square.x, this.square.y) > maxSquareDistance) {
                        continue;
                    }

                    IsoGridSquare square = this.getCell().getGridSquare(x, y, z);

                    if (square == null) {
                        continue;
                    }

                    this.updateFridgeFreezerItems(square);
                }
            }
        }
    }

    @Override
    public void load(ByteBuffer v1, int i2, boolean i3) {
        try {
            super.load(v1, i2, i3);
        } catch (Exception exception) {
            // empty catch block
        }
        this.connected = v1.get() == 1;
        this.activated = v1.get() == 1;
        this.fuel = v1.getFloat();
        this.condition = v1.getInt();
        this.lastHour = v1.getInt();
        this.updateSurrounding = true;
    }

    @Override
    public void save(ByteBuffer v1, boolean i2) {
        try {
            super.save(v1, i2);
        } catch (Exception exception) {
            // empty catch block
        }
        v1.put(this.isConnected() ? (byte) 1 : 0);
        v1.put(this.isActivated() ? (byte) 1 : 0);
        v1.putFloat(this.getFuel());
        v1.putInt(this.getCondition());
        v1.putInt(this.lastHour);
    }

    public void remove() {
        if (this.getSquare() == null) {
            return;
        }
        this.getSquare().transmitRemoveItemFromSquare(this);
    }

    @Override
    public void addToWorld() {
        this.getCell().addToProcessIsoObject(this);
        if (!AllGenerators.contains(this)) {
            AllGenerators.add(this);
        }
    }

    @Override
    public void removeFromWorld() {
        AllGenerators.remove(this);
        if (this.emitter != null) {
            this.emitter.stopAll();
            IsoWorld.instance.returnOwnershipOfEmitter(this.emitter);
            this.emitter = null;
        }
        super.removeFromWorld();
    }

    @Override
    public String getObjectName() {
        return "IsoGenerator";
    }

    public float getFuel() {
        return this.fuel;
    }

    public void setFuel(float f1) {
        this.fuel = Math.max(0.0f, f1);
        if (GameServer.bServer) {
            this.syncIsoObject(false, (byte) 0, null, null);
        }
        if (GameClient.bClient) {
            this.syncIsoObject(false, (byte) 0, null, null);
        }
    }

    public boolean isActivated() {
        return this.activated;
    }

    public void setActivated(boolean activate) {
        if (activate == this.activated) {
            return;
        }
        if (!this.getSquare().getProperties().Is(IsoFlagType.exterior) && this.getSquare().getBuilding() != null) {
            this.getSquare().getBuilding().setToxic(false);
            this.getSquare().getBuilding().setToxic(activate && !this.hasOpenDoorWindow());
        }

        if (activate) {
            this.lastHour = (int) GameTime.getInstance().getWorldAgeHours();
            this.playGeneratorSound("Starting");
        }

        if (!activate && this.getEmitter() != null) {
            if (!this.getEmitter().isEmpty()) {
                this.getEmitter().stopAll();
            }
            this.playGeneratorSound("Stopping");
        }
        try {
            this.updateFridgeFreezerItems();
        } catch (Throwable v2) {
            ExceptionLogger.logException(v2);
        }
        this.activated = activate;
        this.setSurroundingElectricity();
        if (GameClient.bClient) {
            this.syncIsoObject(false, (byte) 0, null, null);
        }
        if (GameServer.bServer) {
            this.syncIsoObject(false, (byte) 0, null, null);
        }
    }

    public void failToStart() {
        this.playGeneratorSound("FailedToStart");
    }

    public int getCondition() {
        return this.condition;
    }

    public void setCondition(int i1) {
        this.condition = Math.max(0, i1);
        if (GameServer.bServer) {
            this.syncIsoObject(false, (byte) 0, null, null);
        }
        if (GameClient.bClient) {
            this.syncIsoObject(false, (byte) 0, null, null);
        }
    }

    public boolean isConnected() {
        return this.connected;
    }

    public void setConnected(boolean i1) {
        this.connected = i1;
        if (GameClient.bClient) {
            this.syncIsoObject(false, (byte) 0, null, null);
        }
        if (GameServer.bServer) {
            this.syncIsoObject(false, (byte) 0, null, null);
        }
    }

    @Override
    public void syncIsoObjectSend(ByteBufferWriter v1) {
        byte i2 = (byte) this.getObjectIndex();
        v1.putInt(this.square.getX());
        v1.putInt(this.square.getY());
        v1.putInt(this.square.getZ());
        v1.putByte(i2);
        v1.putByte((byte) 1);
        v1.putByte((byte) 0);
        v1.putFloat(this.fuel);
        v1.putInt(this.condition);
        v1.putByte(this.activated ? (byte) 1 : 0);
        v1.putByte(this.connected ? (byte) 1 : 0);
    }

    @Override
    public void syncIsoObjectReceive(ByteBuffer v1) {
        float f2 = v1.getFloat();
        int i3 = v1.getInt();
        boolean i4 = v1.get() == 1;
        boolean i5 = v1.get() == 1;
        this.fuel = f2;
        this.condition = i3;
        this.connected = i5;
        if (this.activated != i4) {
            try {
                this.updateFridgeFreezerItems();
            } catch (Throwable v6) {
                ExceptionLogger.logException(v6);
            }
            this.activated = i4;
            if (i4) {
                this.lastHour = (int) GameTime.getInstance().getWorldAgeHours();
            } else if (this.emitter != null) {
                this.emitter.stopAll();
            }
            this.setSurroundingElectricity();
        }
    }

    private boolean touchesChunk(IsoChunk v1) {
        IsoGridSquare v2 = this.getSquare();
        assert (v2 != null);
        if (v2 == null) {
            return false;
        }
        int i3 = v1.wx * 8;
        int i4 = v1.wy * 8;
        int i5 = i3 + 8 - 1;
        int i6 = i4 + 8 - 1;
        if (v2.x - GENERATOR_RADIUS > i5) {
            return false;
        }
        if (v2.x + GENERATOR_RADIUS < i3) {
            return false;
        }
        if (v2.y - GENERATOR_RADIUS > i6) {
            return false;
        }
        return v2.y + GENERATOR_RADIUS >= i4;
    }

    public static void chunkLoaded(IsoChunk v0) {
        int i1;
        v0.checkForMissingGenerators();
        for (i1 = -GeneratorChunkRange; i1 <= GeneratorChunkRange; ++i1) {
            for (int i2 = -GeneratorChunkRange; i2 <= GeneratorChunkRange; ++i2) {
                IsoChunk v3;
                if (i2 == 0 && i1 == 0)
                    continue;
                IsoChunk isoChunk = v3 = GameServer.bServer ? ServerMap.instance.getChunk(v0.wx + i2, v0.wy + i1)
                        : IsoWorld.instance.CurrentCell.getChunk(v0.wx + i2, v0.wy + i1);
                if (v3 == null)
                    continue;
                v3.checkForMissingGenerators();
            }
        }
        for (i1 = 0; i1 < AllGenerators.size(); ++i1) {
            IsoGenerator v2 = AllGenerators.get(i1);
            if (v2.updateSurrounding || !v2.touchesChunk(v0))
                continue;
            v2.updateSurrounding = true;
        }
    }

    public static void updateSurroundingNow() {
        for (int i0 = 0; i0 < AllGenerators.size(); ++i0) {
            IsoGenerator v1 = AllGenerators.get(i0);
            if (!v1.updateSurrounding || v1.getSquare() == null)
                continue;
            v1.updateSurrounding = false;
            v1.setSurroundingElectricity();
        }
    }

    public static void updateGenerator(IsoGridSquare isoGridSquare) {
        if (isoGridSquare == null) {
            return;
        }
        for (int i1 = 0; i1 < AllGenerators.size(); ++i1) {
            float f3;
            IsoGenerator v2 = AllGenerators.get(i1);
            if (v2.getSquare() == null || !((f3 = IsoUtils.DistanceToSquared((float) isoGridSquare.x + 0.5f, (float) isoGridSquare.y + 0.5f,
                    (float) v2.getSquare().getX() + 0.5f,
                    (float) v2.getSquare().getY() + 0.5f)) <= (float) (GENERATOR_RADIUS * GENERATOR_RADIUS)))
                continue;
            v2.updateSurrounding = true;
        }
    }

    public static void Reset() {
        assert (AllGenerators.isEmpty());
        AllGenerators.clear();
    }

    public static boolean isPoweringSquare(int i0, int i1, int i2, int i3, int i4, int i5) {
        int i6 = i2 - GeneratorVerticalPowerRange;
        int i7 = i2 + GeneratorVerticalPowerRange;
        if (i5 < i6 || i5 > i7) {
            return false;
        }
        return IsoUtils.DistanceToSquared((float) i0 + 0.5f, (float) i1 + 0.5f, (float) i3 + 0.5f,
                (float) i4 + 0.5f) <= (float) (GENERATOR_RADIUS * GENERATOR_RADIUS);
    }

    public ArrayList<String> getItemsPowered() {
        ArrayList<String> v1 = new ArrayList<String>();
        for (String v3 : this.itemsPowered.keySet()) {
            v1.add(v3 + this.itemsPowered.get(v3));
        }
        v1.sort(String::compareToIgnoreCase);
        return v1;
    }

    public float getTotalPowerUsing() {
        return this.totalPowerUsing;
    }

    public void setTotalPowerUsing(float f1) {
        this.totalPowerUsing = f1;
    }

    public String getSoundPrefix() {
        if (this.getSprite() == null) {
            return "Generator";
        }
        PropertyContainer v1 = this.getSprite().getProperties();
        if (v1.Is("GeneratorSound")) {
            return v1.Val("GeneratorSound");
        }
        return "Generator";
    }

    private void playGeneratorSound(String fmodSoundName, float volume) {
        if (this.getEmitter() == null) {
            return;
        }

        this.getEmitter().playSound(fmodSoundName, this);
        this.getEmitter().setVolumeAll(volume);
    }

    private void playGeneratorSound(String fmodSoundName) {
        float baseVolume = this.getSandboxValue("GeneratorTweaksSound.BaseVolume", 1.0f);
        this.playGeneratorSound(this.getSoundPrefix() + fmodSoundName, baseVolume);
    }
}

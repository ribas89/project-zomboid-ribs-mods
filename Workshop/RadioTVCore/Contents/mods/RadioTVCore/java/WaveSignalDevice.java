package zombie.radio.devices;

import zombie.Lua.LuaEventManager;
import zombie.characters.IsoPlayer;
import zombie.chat.ChatManager;
import zombie.inventory.InventoryItem;
import zombie.iso.IsoGridSquare;
import zombie.iso.objects.IsoWaveSignal;
import zombie.radio.ZomboidRadio;
import zombie.radio.devices.DeviceData;
import zombie.ui.UIFont;
import zombie.vehicles.VehiclePart;
import zombie.SandboxOptions;

public interface WaveSignalDevice {
    public static String ribsVersionWaveSignalDevice = "2.1.0";

    public DeviceData getDeviceData();

    public void setDeviceData(DeviceData var1);

    public float getDelta();

    public void setDelta(float var1);

    public IsoGridSquare getSquare();

    public float getX();

    public float getY();

    public float getZ();

    public void AddDeviceText(String var1, float var2, float var3, float var4, String var5, String var6, int var7);

    public boolean HasPlayerInRange();

    default public void AddDeviceText(IsoPlayer isoPlayer, String string, float f, float f2, float f3, String string2, String string3, int n) {
        if (this.getDeviceData() == null) {
            return;
        }

        if (this.getDeviceData().getDeviceVolume() <= 0.0f) {
            return;
        }

        if (!ZomboidRadio.isStaticSound(string)) {
            this.getDeviceData().doReceiveSignal(n);
        }

        if (isoPlayer == null) {
            return;
        }

        if (!isoPlayer.isLocalPlayer()) {
            return;
        }

        if (isoPlayer.Traits.Deaf.isSet()) {
            return;
        }
        boolean checkInventory = true;

        if (this.getDeviceData().getParent() instanceof InventoryItem) {
            SandboxOptions.SandboxOption sandboxEnableRadio = SandboxOptions.getInstance().getOptionByName("UALUnequipAndListen.EnableRadioText");
            if (sandboxEnableRadio == null || !((Boolean) sandboxEnableRadio.asConfigOption().getValueAsObject())) {
                checkInventory = isoPlayer.isEquipped((InventoryItem) this.getDeviceData().getParent());
            }
        }

        if (this.getDeviceData().getParent() instanceof InventoryItem && checkInventory) {
            isoPlayer.getChatElement().addChatLine(string, f, f2, f3, UIFont.Medium, this.getDeviceData().getDeviceVolumeRange(), "default", true, true, true, false, false, true);
        }

        if (this.getDeviceData().getParent() instanceof IsoWaveSignal) {
            ((IsoWaveSignal) this.getDeviceData().getParent()).getChatElement().addChatLine(string, f, f2, f3, UIFont.Medium, this.getDeviceData().getDeviceVolumeRange(), "default", true,
                    true, true, true, true, true);
        }

        if (this.getDeviceData().getParent() instanceof VehiclePart) {
            ((VehiclePart) this.getDeviceData().getParent()).getChatElement().addChatLine(string, f, f2, f3, UIFont.Medium, this.getDeviceData().getDeviceVolumeRange(), "default", true, true,
                    true, true, true, true);
        }

        if (ZomboidRadio.isStaticSound(string)) {
            ChatManager.getInstance().showStaticRadioSound(string);
        } else {
            ChatManager.getInstance().showRadioMessage(string, this.getDeviceData().getChannel());
        }

        if (string3 != null) {
            LuaEventManager.triggerEvent("OnDeviceText", string2, string3, -1, -1, -1, string, this);
        }

    }
}

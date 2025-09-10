package zombie.core.textures;

import java.util.ArrayList;
import se.krka.kahlua.vm.KahluaTable;
import zombie.CombatManager;
import zombie.GameTime;
import zombie.SandboxOptions;
import zombie.IndieGL;
import zombie.Lua.LuaManager;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.core.PerformanceSettings;
import zombie.core.SceneShaderStore;
import zombie.core.SpriteRenderer;
import zombie.core.textures.Texture;
import zombie.core.textures.TextureFBO;
import zombie.core.utils.ImageUtils;
import zombie.debug.DebugLog;
import zombie.debug.DebugOptions;
import zombie.iso.IsoCamera;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoUtils;
import zombie.iso.PlayerCamera;
import zombie.iso.sprite.IsoCursor;
import zombie.iso.sprite.IsoReticle;
import zombie.network.GameServer;
import zombie.network.ServerGUI;
import zombie.util.Type;
import zombie.Lua.LuaManager;

public final class MultiTextureFBO2 {
    private final float[] zoomLevelsDefault = new float[] { 2.5f, 2.25f, 2.0f, 1.75f, 1.5f, 1.25f, 1.0f, 0.75f, 0.5f, 0.25f };
    private float[] zoomLevels;
    public TextureFBO Current;
    public volatile TextureFBO FBOrendered = null;
    private final float[] zoom = new float[4];
    private final float[] targetZoom = new float[4];
    private final float[] startZoom = new float[4];
    private float zoomedInLevel;
    private float zoomedOutLevel;
    public final boolean[] bAutoZoom = new boolean[4];
    public boolean bZoomEnabled = true;

    public MultiTextureFBO2() {
        for (int i = 0; i < 4; ++i) {
            this.startZoom[i] = 1.0f;
            this.targetZoom[i] = 1.0f;
            this.zoom[i] = 1.0f;
        }
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

    private float[] stringToZoomLevels(String stringValues) {
        if (stringValues == null || stringValues.isEmpty()) {
            return this.zoomLevelsDefault;
        }
        String[] arrayValues = stringValues.split(";");
        float[] floatResult = new float[arrayValues.length];
        for (int i = 0; i < arrayValues.length; ++i) {
            String stringValue = arrayValues[i];
            try {
                float floatValue;
                floatResult[i] = floatValue = Float.parseFloat(stringValue.trim());
                continue;
            } catch (Exception ignored) {
                floatResult[i] = 1.0f;
            }
        }
        return floatResult;
    }

    public Object getLuaPath(String path, Object defaultValue) {
        if (path == null || path.isEmpty()) {
            return defaultValue;
        }
        String[] paths = path.split("\\.");
        Object currentTable = LuaManager.env.rawget(paths[0]);
        for (int i = 1; i < paths.length; ++i) {
            String currentPath = paths[i];
            if (!(currentTable instanceof KahluaTable)) {
                return defaultValue;
            }
            if ((currentTable = ((KahluaTable) currentTable).rawget(currentPath)) != null)
                continue;
            return defaultValue;
        }
        return currentTable;
    }

    private float[] getModOptionsZoomLevels() {
        String defaultZooms = "2.5;2.25;2.0;1.75;1.5;1.25;1.0;0.75;0.5;0.25";
        // Object stringZoom =
        // this.getLuaPath("PZAPI.ModOptions.Dict.CustomZoomParameter.dict.stringzoom.value",
        // "");
        String sandboxZooms = this.getSandboxValue("CustomZoomParameter.CustomZooms", defaultZooms);
        float[] newZoomLevels = this.stringToZoomLevels(sandboxZooms);
        return newZoomLevels;
    }

    public int getWidth(int n) {
        return (int) ((float) IsoCamera.getScreenWidth(n) * this.getDisplayZoom(n) * ((float) Core.TileScale / 2.0f));
    }

    public int getHeight(int n) {
        return (int) ((float) IsoCamera.getScreenHeight(n) * this.getDisplayZoom(n) * ((float) Core.TileScale / 2.0f));
    }

    public void setZoom(int n, float f) {
        this.zoom[n] = f;
    }

    public void setZoomAndTargetZoom(int n, float f) {
        this.zoom[n] = f;
        this.targetZoom[n] = f;
    }

    public float getZoom(int n) {
        return this.zoom[n];
    }

    public float getTargetZoom(int n) {
        return this.targetZoom[n];
    }

    public float getDisplayZoom(int n) {
        if ((float) Core.width > Core.initialWidth) {
            return this.zoom[n] * (Core.initialWidth / (float) Core.width);
        }
        return this.zoom[n];
    }

    public void setTargetZoom(int n, float f) {
        if (this.targetZoom[n] != f) {
            this.targetZoom[n] = f;
            this.startZoom[n] = this.zoom[n];
        }
    }

    public ArrayList<Integer> getDefaultZoomLevels() {
        ArrayList<Integer> arrayList = new ArrayList<Integer>();
        float[] fArray = this.getModOptionsZoomLevels();
        for (int i = 0; i < fArray.length; ++i) {
            arrayList.add(Math.round(fArray[i] * 100.0f));
        }
        return arrayList;
    }

    public void setZoomLevels(Double... doubleArray) {
        this.setZoomLevelsFromModOptions();
    }

    public void setZoomLevelsFromOption(String string) {
        this.setZoomLevelsFromModOptions();
    }

    public void setZoomLevelsFromModOptions() {
        this.zoomLevels = this.getModOptionsZoomLevels();
    }

    public void destroy() {
        if (this.Current == null) {
            return;
        }
        this.Current.destroy();
        this.Current = null;
        this.FBOrendered = null;
        for (int i = 0; i < 4; ++i) {
            this.targetZoom[i] = 1.0f;
            this.zoom[i] = 1.0f;
        }
    }

    public void create(int n, int n2) throws Exception {
        this.setRibsVersion("MultiTextureFBO2", "2.9.0");
        if (!this.bZoomEnabled) {
            return;
        }
        if (this.zoomLevels == null) {
            this.zoomLevels = this.getModOptionsZoomLevels();
        }
        this.zoomedInLevel = this.zoomLevels[this.zoomLevels.length - 1];
        this.zoomedOutLevel = this.zoomLevels[0];
        int n3 = ImageUtils.getNextPowerOfTwoHW(n);
        int n4 = ImageUtils.getNextPowerOfTwoHW(n2);
        this.Current = this.createTexture(n3, n4, false);
    }

    public static void setRibsVersion(String className, String version) {
        if (LuaManager.env == null) {
            return;
        }

        Object objectTable = LuaManager.env.rawget(className);

        if (!(objectTable instanceof KahluaTable)) {
            objectTable = LuaManager.platform.newTable();
            LuaManager.env.rawset(className, objectTable);
        }

        KahluaTable luaTable = (KahluaTable) objectTable;
        if (luaTable.rawget("ribsVersion") != null) {
            return;
        }

        luaTable.rawset("ribsVersion", version);
    }

    private void syncZoomLevels() {
        float[] newLevels = this.getModOptionsZoomLevels();
        if (compareFloatArray(this.zoomLevels, newLevels)) {
            return;
        }

        this.zoomLevels = newLevels;
        int playerIndex = IsoPlayer.getPlayerIndex();
        this.targetZoom[playerIndex] = 1.0F;
        this.zoom[playerIndex] = 1.0F;
    }

    private boolean compareFloatArray(float[] a, float[] b) {
        if (a == b)
            return true;
        if (a == null || b == null)
            return false;
        if (a.length != b.length)
            return false;
        for (int i = 0; i < a.length; i++) {
            if (Float.compare(a[i], b[i]) != 0) {
                return false;
            }
        }
        return true;
    }

    public void update() {
        int n = IsoPlayer.getPlayerIndex();
        if (!this.bZoomEnabled) {
            this.targetZoom[n] = 1.0F;
            this.zoom[n] = 1.0F;
        }

        IsoGameCharacter isoGameCharacter = IsoCamera.getCameraCharacter();
        float f;
        if (this.bAutoZoom[n] && isoGameCharacter != null && this.bZoomEnabled) {
            f = IsoUtils.DistanceTo(IsoCamera.getRightClickOffX(), IsoCamera.getRightClickOffY(), 0.0F, 0.0F);
            float f2 = f / 300.0F;
            if (f2 > 1.0F) {
                f2 = 1.0F;
            }

            float f3 = this.shouldAutoZoomIn() ? this.zoomedInLevel : this.zoomedOutLevel;
            if ((f3 += f2) > this.zoomLevels[0]) {
                f3 = this.zoomLevels[0];
            }

            if (isoGameCharacter.getVehicle() != null) {
                f3 = this.getMaxZoom();
            }

            this.setTargetZoom(n, f3);
        }

        f = 0.004F * GameTime.instance.getMultiplier() / GameTime.instance.getTrueMultiplier() * (Core.TileScale == 2 ? 1.5F : 1.5F);
        if (!this.bAutoZoom[n]) {
            f *= 5.0F;
        } else if (this.targetZoom[n] > this.zoom[n]) {
            f *= 1.0F;
        }

        if (this.targetZoom[n] > this.zoom[n]) {
            this.zoom[n] += f;
            IsoPlayer.players[n].dirtyRecalcGridStackTime = 2.0F;
            if (this.zoom[n] > this.targetZoom[n] || Math.abs(this.zoom[n] - this.targetZoom[n]) < 0.001F) {
                this.zoom[n] = this.targetZoom[n];
            }
        }

        if (this.targetZoom[n] < this.zoom[n]) {
            this.zoom[n] -= f;
            IsoPlayer.players[n].dirtyRecalcGridStackTime = 2.0F;
            if (this.zoom[n] < this.targetZoom[n] || Math.abs(this.zoom[n] - this.targetZoom[n]) < 0.001F) {
                this.zoom[n] = this.targetZoom[n];
            }
        }

        this.setCameraToCentre();
    }

    private boolean shouldAutoZoomIn() {
        IsoGameCharacter isoGameCharacter = IsoCamera.getCameraCharacter();
        if (isoGameCharacter == null) {
            return false;
        }
        IsoGridSquare isoGridSquare = isoGameCharacter.getCurrentSquare();
        if (isoGridSquare != null && !isoGridSquare.isOutside()) {
            return true;
        }
        IsoPlayer isoPlayer = Type.tryCastTo(isoGameCharacter, IsoPlayer.class);
        if (isoPlayer == null) {
            return false;
        }
        if (isoPlayer.isRunning() || isoPlayer.isSprinting()) {
            return false;
        }
        if (isoPlayer.closestZombie < 6.0f && isoPlayer.isTargetedByZombie()) {
            return true;
        }
        return isoPlayer.lastTargeted < (float) (PerformanceSettings.getLockFPS() * 4);
    }

    private void setCameraToCentre() {
        PlayerCamera playerCamera = IsoCamera.cameras[IsoPlayer.getPlayerIndex()];
        playerCamera.center();
    }

    private TextureFBO createTexture(int n, int n2, boolean bl) {
        if (bl) {
            Texture texture = new Texture(n, n2, 16);
            TextureFBO textureFBO = new TextureFBO(texture);
            textureFBO.destroy();
            return null;
        }
        Texture texture = new Texture(n, n2, 19);
        return new TextureFBO(texture);
    }

    public void render() {
        int n;
        if (this.Current == null) {
            return;
        }
        int n2 = 0;
        for (n = 3; n >= 0; --n) {
            if (IsoPlayer.players[n] == null)
                continue;
            n2 = n > 1 ? 3 : n;
            break;
        }
        n2 = Math.max(n2, IsoPlayer.numPlayers - 1);
        for (n = 0; n <= n2; ++n) {
            if (SceneShaderStore.WeatherShader != null && DebugOptions.instance.FBORenderChunk.UseWeatherShader.getValue()) {
                IndieGL.StartShader(SceneShaderStore.WeatherShader, n);
            }
            int n3 = IsoCamera.getScreenLeft(n);
            int n4 = IsoCamera.getScreenTop(n);
            int n5 = IsoCamera.getScreenWidth(n);
            int n6 = IsoCamera.getScreenHeight(n);
            if (!(IsoPlayer.players[n] != null || GameServer.bServer && ServerGUI.isCreated())) {
                SpriteRenderer.instance.renderi(null, n3, n4, n5, n6, 0.0f, 0.0f, 0.0f, 1.0f, null);
                continue;
            }
            ((Texture) this.Current.getTexture()).rendershader2(n3, n4, n5, n6, n3, n4, n5, n6, 1.0f, 1.0f, 1.0f, 1.0f);
        }
        if (SceneShaderStore.WeatherShader != null) {
            IndieGL.EndShader();
        }
        switch (CombatManager.targetReticleMode) {
            case 1: {
                IsoReticle.getInstance().render(0);
                break;
            }
            default: {
                IsoCursor.getInstance().render(0);
            }
        }
    }

    public TextureFBO getCurrent(int n) {
        return this.Current;
    }

    public Texture getTexture(int n) {
        return (Texture) this.Current.getTexture();
    }

    public void doZoomScroll(int n, int n2) {
        this.syncZoomLevels();
        this.targetZoom[n] = this.getNextZoom(n, n2);
    }

    public float getNextZoom(int n, int n2) {
        block4: {
            block3: {
                if (!this.bZoomEnabled || this.zoomLevels == null) {
                    return 1.0f;
                }
                if (n2 <= 0)
                    break block3;
                for (int i = this.zoomLevels.length - 1; i > 0; --i) {
                    if (this.targetZoom[n] != this.zoomLevels[i])
                        continue;
                    return this.zoomLevels[i - 1];
                }
                break block4;
            }
            if (n2 >= 0)
                break block4;
            for (int i = 0; i < this.zoomLevels.length - 1; ++i) {
                if (this.targetZoom[n] != this.zoomLevels[i])
                    continue;
                return this.zoomLevels[i + 1];
            }
        }
        return this.targetZoom[n];
    }

    public float getMinZoom() {
        if (!this.bZoomEnabled || this.zoomLevels == null || this.zoomLevels.length == 0) {
            return 1.0f;
        }
        return this.zoomLevels[this.zoomLevels.length - 1];
    }

    public float getMaxZoom() {
        if (!this.bZoomEnabled || this.zoomLevels == null || this.zoomLevels.length == 0) {
            return 1.0f;
        }
        return this.zoomLevels[0];
    }

    public boolean test() {
        try {
            this.createTexture(16, 16, true);
        } catch (Exception exception) {
            DebugLog.General.error("Failed to create Test FBO");
            exception.printStackTrace();
            Core.SafeMode = true;
            return false;
        }
        return true;
    }
};

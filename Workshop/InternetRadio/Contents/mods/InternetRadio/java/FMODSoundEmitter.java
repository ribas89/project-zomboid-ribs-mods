package fmod.fmod;

import fmod.FMOD_STUDIO_EVENT_PROPERTY;
import fmod.fmod.EmitterType;
import fmod.fmod.FMODManager;
import fmod.fmod.FMOD_STUDIO_EVENT_DESCRIPTION;
import fmod.fmod.FMOD_STUDIO_PARAMETER_DESCRIPTION;
import fmod.fmod.FMOD_STUDIO_PLAYBACK_STATE;
import fmod.fmod.IFMODParameterUpdater;
import fmod.javafmod;
import fmod.javafmodJNI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import zombie.GameSounds;
import zombie.SoundManager;
import zombie.audio.BaseSoundEmitter;
import zombie.audio.FMODParameter;
import zombie.audio.GameSound;
import zombie.audio.GameSoundClip;
import zombie.audio.parameters.ParameterOcclusion;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.core.math.PZMath;
import zombie.debug.DebugLog;
import zombie.debug.DebugOptions;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoObject;
import zombie.iso.IsoUtils;
import zombie.iso.IsoWorld;
import zombie.iso.areas.IsoRoom;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoWindow;
import zombie.network.GameClient;
import zombie.network.GameServer;
import zombie.popman.ObjectPool;
import zombie.scripting.ScriptManager;
import zombie.scripting.objects.SoundTimelineScript;
import zombie.SandboxOptions;

public final class FMODSoundEmitter extends BaseSoundEmitter {
    private final ArrayList<Sound> ToStart = new ArrayList();
    private final ArrayList<Sound> Instances = new ArrayList();
    private final ArrayList<Sound> Stopped = new ArrayList();
    public float x;
    public float y;
    public float z;
    public EmitterType emitterType;
    public IsoObject parent;
    private final ParameterOcclusion occlusion = new ParameterOcclusion(this);
    private final ArrayList<FMODParameter> parameters = new ArrayList();
    public IFMODParameterUpdater parameterUpdater;
    private final ArrayList<ParameterValue> parameterValues = new ArrayList();
    private static final ObjectPool<ParameterValue> parameterValuePool = new ObjectPool<ParameterValue>(ParameterValue::new);
    private static BitSet parameterSet;
    private final ArrayDeque<EventSound> eventSoundPool = new ArrayDeque();
    private final ArrayDeque<FileSound> fileSoundPool = new ArrayDeque();
    private static long CurrentTimeMS;
    public static String ribsVersionFMODSoundEmitter = "1.2.0";

    public FMODSoundEmitter() {
        SoundManager.instance.registerEmitter(this);
        if (parameterSet == null) {
            parameterSet = new BitSet(FMODManager.instance.getParameterCount());
        }
    }

    @Override
    public void randomStart() {
    }

    @Override
    public void setPos(float f, float f2, float f3) {
        this.x = f;
        this.y = f2;
        this.z = f3;
    }

    @Override
    public int stopSound(long l) {
        Sound sound;
        int n;
        for (n = 0; n < this.ToStart.size(); ++n) {
            sound = this.ToStart.get(n);
            if (sound.getRef() != l)
                continue;
            this.sendStopSound(sound.name, false);
            sound.release();
            this.ToStart.remove(n--);
        }
        for (n = 0; n < this.Instances.size(); ++n) {
            sound = this.Instances.get(n);
            if (sound.getRef() != l)
                continue;
            sound.stop();
            this.sendStopSound(sound.name, false);
            this.Stopped.add(sound);
            this.Instances.remove(n--);
        }
        return 0;
    }

    @Override
    public void stopSoundLocal(long l) {
        Sound sound;
        int n;
        for (n = 0; n < this.ToStart.size(); ++n) {
            sound = this.ToStart.get(n);
            if (sound.getRef() != l)
                continue;
            sound.release();
            this.ToStart.remove(n--);
        }
        for (n = 0; n < this.Instances.size(); ++n) {
            sound = this.Instances.get(n);
            if (sound.getRef() != l)
                continue;
            sound.stop();
            sound.release();
            this.Instances.remove(n--);
        }
    }

    @Override
    public void stopOrTriggerSoundLocal(long l) {
        Sound sound;
        int n;
        for (n = 0; n < this.ToStart.size(); ++n) {
            sound = this.ToStart.get(n);
            if (sound.getRef() != l)
                continue;
            sound.release();
            this.ToStart.remove(n--);
        }
        for (n = 0; n < this.Instances.size(); ++n) {
            sound = this.Instances.get(n);
            if (sound.getRef() != l)
                continue;
            if (sound.clip.hasSustainPoints()) {
                sound.triggerCue();
                continue;
            }
            sound.stop();
            sound.release();
            this.Instances.remove(n--);
        }
    }

    @Override
    public int stopSoundByName(String string) {
        Sound sound;
        int n;
        GameSound gameSound = GameSounds.getSound(string);
        if (gameSound == null) {
            return 0;
        }
        for (n = 0; n < this.ToStart.size(); ++n) {
            sound = this.ToStart.get(n);
            if (!gameSound.clips.contains(sound.clip))
                continue;
            sound.release();
            this.ToStart.remove(n--);
        }
        for (n = 0; n < this.Instances.size(); ++n) {
            sound = this.Instances.get(n);
            if (!gameSound.clips.contains(sound.clip))
                continue;
            sound.stop();
            sound.release();
            this.Instances.remove(n--);
        }
        return 0;
    }

    @Override
    public void stopOrTriggerSound(long l) {
        int n = this.findToStart(l);
        if (n != -1) {
            Sound sound = this.ToStart.remove(n);
            this.sendStopSound(sound.name, true);
            sound.release();
            return;
        }
        n = this.findInstance(l);
        if (n != -1) {
            Sound sound = this.Instances.get(n);
            this.sendStopSound(sound.name, true);
            if (sound instanceof EventSound) {
                EventSound eventSound = (EventSound) sound;
                FMOD_STUDIO_PARAMETER_DESCRIPTION fMOD_STUDIO_PARAMETER_DESCRIPTION = FMODManager.instance.getParameterDescription("ActionProgressPercent");
                if (eventSound.clip.eventDescription.hasParameter(fMOD_STUDIO_PARAMETER_DESCRIPTION)) {
                    eventSound.setParameterValue(fMOD_STUDIO_PARAMETER_DESCRIPTION, 100.0f);
                    eventSound.bTriggeredCue = true;
                    eventSound.checkTimeMS = CurrentTimeMS;
                    return;
                }
            }
            if (sound.clip.hasSustainPoints()) {
                sound.triggerCue();
            } else {
                this.Instances.remove(n);
                sound.stop();
                sound.release();
            }
        }
    }

    @Override
    public void stopOrTriggerSoundByName(String string) {
        Sound sound;
        int n;
        GameSound gameSound = GameSounds.getSound(string);
        if (gameSound == null) {
            return;
        }
        for (n = 0; n < this.ToStart.size(); ++n) {
            sound = this.ToStart.get(n);
            if (!gameSound.clips.contains(sound.clip))
                continue;
            this.ToStart.remove(n--);
            sound.release();
        }
        for (n = 0; n < this.Instances.size(); ++n) {
            sound = this.Instances.get(n);
            if (!gameSound.clips.contains(sound.clip))
                continue;
            if (sound.clip.hasSustainPoints()) {
                sound.triggerCue();
                continue;
            }
            sound.stop();
            sound.release();
            this.Instances.remove(n--);
        }
    }

    private void limitSound(GameSound gameSound, int n) {
        Sound sound;
        int n2;
        int n3 = this.countToStart(gameSound) + this.countInstances(gameSound);
        if (n3 <= n) {
            return;
        }
        for (n2 = 0; n2 < this.ToStart.size(); ++n2) {
            sound = this.ToStart.get(n2);
            if (!gameSound.clips.contains(sound.clip))
                continue;
            this.ToStart.remove(n2--);
            sound.release();
            if (--n3 > n)
                continue;
            return;
        }
        for (n2 = 0; n2 < this.Instances.size(); ++n2) {
            sound = this.Instances.get(n2);
            if (!gameSound.clips.contains(sound.clip))
                continue;
            if (sound.clip.hasSustainPoints()) {
                if (sound.isTriggeredCue())
                    continue;
                sound.triggerCue();
                continue;
            }
            sound.stop();
            sound.release();
            this.Instances.remove(n2--);
            if (--n3 > n)
                continue;
            return;
        }
    }

    @Override
    public void setVolume(long l, float f) {
        Sound sound;
        int n;
        for (n = 0; n < this.ToStart.size(); ++n) {
            sound = this.ToStart.get(n);
            if (sound.getRef() != l)
                continue;
            sound.volume = f;
        }
        for (n = 0; n < this.Instances.size(); ++n) {
            sound = this.Instances.get(n);
            if (sound.getRef() != l)
                continue;
            sound.volume = f;
        }
    }

    @Override
    public void setPitch(long l, float f) {
        Sound sound;
        int n;
        for (n = 0; n < this.ToStart.size(); ++n) {
            sound = this.ToStart.get(n);
            if (sound.getRef() == l) {
                DebugLog.log("Set pitch for ToStart");
            }
            sound.pitch = f;
        }
        for (n = 0; n < this.Instances.size(); ++n) {
            sound = this.Instances.get(n);
            if (sound.getRef() == l) {
                DebugLog.log("Set pitch for Instance");
            }
            sound.pitch = f;
        }
    }

    @Override
    public boolean hasSustainPoints(long l) {
        Sound sound;
        int n;
        for (n = 0; n < this.ToStart.size(); ++n) {
            sound = this.ToStart.get(n);
            if (sound.getRef() != l)
                continue;
            if (sound.clip.eventDescription == null) {
                return false;
            }
            return sound.clip.eventDescription.bHasSustainPoints;
        }
        for (n = 0; n < this.Instances.size(); ++n) {
            sound = this.Instances.get(n);
            if (sound.getRef() != l)
                continue;
            if (sound.clip.eventDescription == null) {
                return false;
            }
            return sound.clip.eventDescription.bHasSustainPoints;
        }
        return false;
    }

    @Override
    public void triggerCue(long l) {
        for (int i = 0; i < this.Instances.size(); ++i) {
            Sound sound = this.Instances.get(i);
            if (sound.getRef() != l)
                continue;
            sound.triggerCue();
        }
    }

    @Override
    public void setVolumeAll(float f) {
        Sound sound;
        int n;
        for (n = 0; n < this.ToStart.size(); ++n) {
            sound = this.ToStart.get(n);
            sound.volume = f;
        }
        for (n = 0; n < this.Instances.size(); ++n) {
            sound = this.Instances.get(n);
            sound.volume = f;
        }
    }

    @Override
    public void stopAll() {
        Sound sound;
        int n;
        for (n = 0; n < this.ToStart.size(); ++n) {
            sound = this.ToStart.get(n);
            sound.release();
        }
        for (n = 0; n < this.Instances.size(); ++n) {
            sound = this.Instances.get(n);
            sound.stop();
            sound.release();
        }
        this.ToStart.clear();
        this.Instances.clear();
    }

    @Override
    public long playSound(String string) {
        if (GameClient.bClient) {
            if (this.parent instanceof IsoMovingObject) {
                if (!(this.parent instanceof IsoPlayer) || !((IsoPlayer) this.parent).isInvisible()) {
                    GameClient.instance.PlaySound(string, false, (IsoMovingObject) this.parent);
                }
            } else {
                GameClient.instance.PlayWorldSound(string, (int) this.x, (int) this.y, (byte) this.z);
            }
        }
        if (GameServer.bServer) {
            return 0L;
        }

        if (string != null && string.startsWith("http")) {
            return this.playStreamImpl(string, this.parent);
        }

        return this.playSoundImpl(string, (IsoObject) null);
    }

    @Override
    public long playSound(String string, IsoGameCharacter isoGameCharacter) {
        if (GameClient.bClient) {
            if (!isoGameCharacter.isInvisible()) {
                GameClient.instance.PlaySound(string, false, isoGameCharacter);
            }
            return !isoGameCharacter.isInvisible() || DebugOptions.instance.Character.Debug.PlaySoundWhenInvisible.getValue() ? this.playSoundImpl(string, (IsoObject) null) : 0L;
        }
        if (GameServer.bServer) {
            return 0L;
        }
        return this.playSoundImpl(string, (IsoObject) null);
    }

    @Override
    public long playSound(String string, int n, int n2, int n3) {
        this.x = n;
        this.y = n2;
        this.z = n3;
        return this.playSound(string);
    }

    @Override
    public long playSound(String string, IsoGridSquare isoGridSquare) {
        this.x = (float) isoGridSquare.x + 0.5f;
        this.y = (float) isoGridSquare.y + 0.5f;
        this.z = isoGridSquare.z;
        return this.playSound(string);
    }

    @Override
    public long playSoundImpl(String string, IsoGridSquare isoGridSquare) {
        this.x = (float) isoGridSquare.x + 0.5f;
        this.y = (float) isoGridSquare.y + 0.5f;
        this.z = (float) isoGridSquare.z + 0.5f;
        return this.playSoundImpl(string, (IsoObject) null);
    }

    @Override
    public long playSound(String string, boolean bl) {
        return this.playSound(string);
    }

    @Override
    public long playSoundImpl(String string, boolean bl, IsoObject isoObject) {
        return this.playSoundImpl(string, isoObject);
    }

    @Override
    public long playSoundLooped(String string) {
        if (GameClient.bClient) {
            if (this.parent instanceof IsoMovingObject) {
                GameClient.instance.PlaySound(string, true, (IsoMovingObject) this.parent);
            } else {
                GameClient.instance.PlayWorldSound(string, (int) this.x, (int) this.y, (byte) this.z);
            }
        }
        return this.playSoundLoopedImpl(string);
    }

    @Override
    public long playSoundLoopedImpl(String string) {
        return this.playSoundImpl(string, false, null);
    }

    @Override
    public long playSound(String string, IsoObject isoObject) {
        if (GameClient.bClient) {
            if (isoObject instanceof IsoMovingObject) {
                GameClient.instance.PlaySound(string, false, (IsoMovingObject) this.parent);
            } else {
                GameClient.instance.PlayWorldSound(string, (int) this.x, (int) this.y, (byte) this.z);
            }
        }
        if (GameServer.bServer) {
            return 0L;
        }
        return this.playSoundImpl(string, isoObject);
    }

    @Override
    public long playSoundImpl(String string, IsoObject isoObject) {
        if (string != null && string.startsWith("http")) {
            return this.playStreamImpl(string, isoObject);
        }

        if (string.startsWith("Radio") || string.startsWith("VehicleRadio")) {
            return 0L;
        }

        GameSound gameSound = GameSounds.getSound(string);
        if (gameSound == null) {
            return 0L;
        }
        GameSoundClip gameSoundClip = gameSound.getRandomClip();
        return this.playClip(gameSoundClip, isoObject);
    }

    @Override
    public long playClip(GameSoundClip gameSoundClip, IsoObject isoObject) {
        Sound sound = this.addSound(gameSoundClip, 1.0f, isoObject);
        return sound == null ? 0L : sound.getRef();
    }

    @Override
    public long playAmbientSound(String string) {
        if (GameServer.bServer) {
            return 0L;
        }
        GameSound gameSound = GameSounds.getSound(string);
        if (gameSound == null) {
            return 0L;
        }
        GameSoundClip gameSoundClip = gameSound.getRandomClip();
        Sound sound = this.addSound(gameSoundClip, 1.0f, null);
        if (sound instanceof FileSound) {
            ((FileSound) sound).ambient = true;
        }
        return sound == null ? 0L : sound.getRef();
    }

    @Override
    public long playAmbientLoopedImpl(String string) {
        if (GameServer.bServer) {
            return 0L;
        }
        GameSound gameSound = GameSounds.getSound(string);
        if (gameSound == null) {
            return 0L;
        }
        GameSoundClip gameSoundClip = gameSound.getRandomClip();
        Sound sound = this.addSound(gameSoundClip, 1.0f, null);
        return sound == null ? 0L : sound.getRef();
    }

    @Override
    public void set3D(long l, boolean bl) {
        Sound sound;
        int n;
        for (n = 0; n < this.ToStart.size(); ++n) {
            sound = this.ToStart.get(n);
            if (sound.getRef() != l)
                continue;
            sound.set3D(bl);
        }
        for (n = 0; n < this.Instances.size(); ++n) {
            sound = this.Instances.get(n);
            if (sound.getRef() != l)
                continue;
            sound.set3D(bl);
        }
    }

    @Override
    public void tick() {
        Object object;
        int n;
        if (!this.isEmpty()) {
            this.occlusion.update();
            for (n = 0; n < this.parameters.size(); ++n) {
                object = this.parameters.get(n);
                ((FMODParameter) object).update();
            }
        }
        for (n = 0; n < this.ToStart.size(); ++n) {
            object = this.ToStart.get(n);
            this.Instances.add((Sound) object);
        }
        for (n = 0; n < this.Instances.size(); ++n) {
            boolean bl;
            object = this.Instances.get(n);
            if (!((Sound) object).tick(bl = this.ToStart.contains(object)))
                continue;
            this.Instances.remove(n--);
            ((Sound) object).release();
        }
        this.ToStart.clear();
        for (n = 0; n < this.Stopped.size(); ++n) {
            object = this.Stopped.get(n);
            if (!((Sound) object).tickWhileStopped())
                continue;
            this.Stopped.remove(n--);
            ((Sound) object).release();
        }
    }

    @Override
    public boolean hasSoundsToStart() {
        return !this.ToStart.isEmpty();
    }

    @Override
    public boolean isEmpty() {
        return this.ToStart.isEmpty() && this.Instances.isEmpty();
    }

    @Override
    public boolean isPlaying(long l) {
        int n;
        for (n = 0; n < this.ToStart.size(); ++n) {
            if (this.ToStart.get(n).getRef() != l)
                continue;
            return true;
        }
        for (n = 0; n < this.Instances.size(); ++n) {
            if (this.Instances.get(n).getRef() != l)
                continue;
            return true;
        }
        return false;
    }

    @Override
    public boolean isPlaying(String string) {
        int n;
        for (n = 0; n < this.ToStart.size(); ++n) {
            if (!string.equals(this.ToStart.get((int) n).name))
                continue;
            return true;
        }
        for (n = 0; n < this.Instances.size(); ++n) {
            if (!string.equals(this.Instances.get((int) n).name))
                continue;
            return true;
        }
        return false;
    }

    @Override
    public boolean restart(long l) {
        int n = this.findToStart(l);
        if (n != -1) {
            return true;
        }
        n = this.findInstance(l);
        return n != -1 && this.Instances.get(n).restart();
    }

    private int findInstance(long l) {
        for (int i = 0; i < this.Instances.size(); ++i) {
            Sound sound = this.Instances.get(i);
            if (sound.getRef() != l)
                continue;
            return i;
        }
        return -1;
    }

    private int findInstance(String string) {
        GameSound gameSound = GameSounds.getSound(string);
        if (gameSound == null) {
            return -1;
        }
        for (int i = 0; i < this.Instances.size(); ++i) {
            Sound sound = this.Instances.get(i);
            if (!gameSound.clips.contains(sound.clip))
                continue;
            return i;
        }
        return -1;
    }

    private int findToStart(long l) {
        for (int i = 0; i < this.ToStart.size(); ++i) {
            Sound sound = this.ToStart.get(i);
            if (sound.getRef() != l)
                continue;
            return i;
        }
        return -1;
    }

    private int findToStart(String string) {
        GameSound gameSound = GameSounds.getSound(string);
        if (gameSound == null) {
            return -1;
        }
        for (int i = 0; i < this.ToStart.size(); ++i) {
            Sound sound = this.ToStart.get(i);
            if (!gameSound.clips.contains(sound.clip))
                continue;
            return i;
        }
        return -1;
    }

    private int findStopped(long l) {
        for (int i = 0; i < this.Stopped.size(); ++i) {
            Sound sound = this.Stopped.get(i);
            if (!(sound instanceof EventSound))
                continue;
            EventSound eventSound = (EventSound) sound;
            if (eventSound.eventInstanceStopped != l)
                continue;
            return i;
        }
        return -1;
    }

    private int countToStart(GameSound gameSound) {
        int n = 0;
        for (int i = 0; i < this.ToStart.size(); ++i) {
            Sound sound = this.ToStart.get(i);
            if (!gameSound.clips.contains(sound.clip))
                continue;
            ++n;
        }
        return n;
    }

    private int countInstances(GameSound gameSound) {
        int n = 0;
        for (int i = 0; i < this.Instances.size(); ++i) {
            Sound sound = this.Instances.get(i);
            if (!gameSound.clips.contains(sound.clip))
                continue;
            ++n;
        }
        return n;
    }

    public void addParameter(FMODParameter fMODParameter) {
        this.parameters.add(fMODParameter);
    }

    @Override
    public void setParameterValue(long l, FMOD_STUDIO_PARAMETER_DESCRIPTION fMOD_STUDIO_PARAMETER_DESCRIPTION, float f) {
        if (l == 0L || fMOD_STUDIO_PARAMETER_DESCRIPTION == null) {
            return;
        }
        int n = this.findInstance(l);
        if (n != -1) {
            Sound sound = this.Instances.get(n);
            sound.setParameterValue(fMOD_STUDIO_PARAMETER_DESCRIPTION, f);
            return;
        }
        n = this.findParameterValue(l, fMOD_STUDIO_PARAMETER_DESCRIPTION);
        if (n != -1) {
            this.parameterValues.get((int) n).value = f;
            return;
        }
        n = this.findStopped(l);
        if (n != -1) {
            javafmod.FMOD_Studio_EventInstance_SetParameterByID(l, fMOD_STUDIO_PARAMETER_DESCRIPTION.id, f, false);
            return;
        }
        n = this.findToStart(l);
        if (n == -1) {
            return;
        }
        ParameterValue parameterValue = parameterValuePool.alloc();
        parameterValue.eventInstance = l;
        parameterValue.parameterDescription = fMOD_STUDIO_PARAMETER_DESCRIPTION;
        parameterValue.value = f;
        this.parameterValues.add(parameterValue);
    }

    @Override
    public void setParameterValueByName(long l, String string, float f) {
        FMOD_STUDIO_PARAMETER_DESCRIPTION fMOD_STUDIO_PARAMETER_DESCRIPTION = FMODManager.instance.getParameterDescription(string);
        this.setParameterValue(l, fMOD_STUDIO_PARAMETER_DESCRIPTION, f);
    }

    @Override
    public boolean isUsingParameter(long l, String string) {
        FMOD_STUDIO_PARAMETER_DESCRIPTION fMOD_STUDIO_PARAMETER_DESCRIPTION = FMODManager.instance.getParameterDescription(string);
        if (fMOD_STUDIO_PARAMETER_DESCRIPTION == null) {
            return false;
        }
        int n = this.findToStart(l);
        if (n != -1) {
            Sound sound = this.ToStart.get(n);
            return sound.clip.eventDescription != null && sound.clip.hasParameter(fMOD_STUDIO_PARAMETER_DESCRIPTION);
        }
        n = this.findInstance(l);
        if (n != -1) {
            Sound sound = this.Instances.get(n);
            return sound.clip.eventDescription != null && sound.clip.hasParameter(fMOD_STUDIO_PARAMETER_DESCRIPTION);
        }
        return false;
    }

    @Override
    public void setTimelinePosition(long l, String string) {
        if (l == 0L) {
            return;
        }
        int n = this.findToStart(l);
        if (n != -1) {
            Sound sound = this.ToStart.get(n);
            sound.setTimelinePosition(string);
        }
    }

    private int findParameterValue(long l, FMOD_STUDIO_PARAMETER_DESCRIPTION fMOD_STUDIO_PARAMETER_DESCRIPTION) {
        for (int i = 0; i < this.parameterValues.size(); ++i) {
            ParameterValue parameterValue = this.parameterValues.get(i);
            if (parameterValue.eventInstance != l || parameterValue.parameterDescription != fMOD_STUDIO_PARAMETER_DESCRIPTION)
                continue;
            return i;
        }
        return -1;
    }

    public void clearParameters() {
        this.occlusion.resetToDefault();
        this.parameters.clear();
        parameterValuePool.releaseAll(this.parameterValues);
        this.parameterValues.clear();
    }

    private void startEvent(long l, GameSoundClip gameSoundClip) {
        parameterSet.clear();
        ArrayList<FMODParameter> arrayList = this.parameters;
        ArrayList<FMOD_STUDIO_PARAMETER_DESCRIPTION> arrayList2 = gameSoundClip.eventDescription.parameters;
        block0: for (int i = 0; i < arrayList2.size(); ++i) {
            FMOD_STUDIO_PARAMETER_DESCRIPTION fMOD_STUDIO_PARAMETER_DESCRIPTION = arrayList2.get(i);
            int n = this.findParameterValue(l, fMOD_STUDIO_PARAMETER_DESCRIPTION);
            if (n != -1) {
                ParameterValue parameterValue = this.parameterValues.get(n);
                javafmod.FMOD_Studio_EventInstance_SetParameterByID(l, fMOD_STUDIO_PARAMETER_DESCRIPTION.id, parameterValue.value, false);
                parameterSet.set(fMOD_STUDIO_PARAMETER_DESCRIPTION.globalIndex, true);
                continue;
            }
            if (fMOD_STUDIO_PARAMETER_DESCRIPTION == this.occlusion.getParameterDescription()) {
                this.occlusion.startEventInstance(l);
                parameterSet.set(fMOD_STUDIO_PARAMETER_DESCRIPTION.globalIndex, true);
                continue;
            }
            for (int j = 0; j < arrayList.size(); ++j) {
                FMODParameter fMODParameter = arrayList.get(j);
                if (fMODParameter.getParameterDescription() != fMOD_STUDIO_PARAMETER_DESCRIPTION)
                    continue;
                fMODParameter.startEventInstance(l);
                parameterSet.set(fMOD_STUDIO_PARAMETER_DESCRIPTION.globalIndex, true);
                continue block0;
            }
        }
        if (this.parameterUpdater != null) {
            this.parameterUpdater.startEvent(l, gameSoundClip, parameterSet);
        }
    }

    private void updateEvent(long l, GameSoundClip gameSoundClip) {
        if (this.parameterUpdater != null) {
            this.parameterUpdater.updateEvent(l, gameSoundClip);
        }
    }

    private void stopEvent(long l, GameSoundClip gameSoundClip) {
        parameterSet.clear();
        ArrayList<FMODParameter> arrayList = this.parameters;
        ArrayList<FMOD_STUDIO_PARAMETER_DESCRIPTION> arrayList2 = gameSoundClip.eventDescription.parameters;
        block0: for (int i = 0; i < arrayList2.size(); ++i) {
            FMOD_STUDIO_PARAMETER_DESCRIPTION fMOD_STUDIO_PARAMETER_DESCRIPTION = arrayList2.get(i);
            int n = this.findParameterValue(l, fMOD_STUDIO_PARAMETER_DESCRIPTION);
            if (n != -1) {
                ParameterValue parameterValue = this.parameterValues.remove(n);
                parameterValuePool.release(parameterValue);
                parameterSet.set(fMOD_STUDIO_PARAMETER_DESCRIPTION.globalIndex, true);
                continue;
            }
            if (fMOD_STUDIO_PARAMETER_DESCRIPTION == this.occlusion.getParameterDescription()) {
                this.occlusion.stopEventInstance(l);
                parameterSet.set(fMOD_STUDIO_PARAMETER_DESCRIPTION.globalIndex, true);
                continue;
            }
            for (int j = 0; j < arrayList.size(); ++j) {
                FMODParameter fMODParameter = arrayList.get(j);
                if (fMODParameter.getParameterDescription() != fMOD_STUDIO_PARAMETER_DESCRIPTION)
                    continue;
                fMODParameter.stopEventInstance(l);
                parameterSet.set(fMOD_STUDIO_PARAMETER_DESCRIPTION.globalIndex, true);
                continue block0;
            }
        }
        if (this.parameterUpdater != null) {
            this.parameterUpdater.stopEvent(l, gameSoundClip, parameterSet);
        }
    }

    private EventSound allocEventSound() {
        return this.eventSoundPool.isEmpty() ? new EventSound(this) : this.eventSoundPool.pop();
    }

    private void releaseEventSound(EventSound eventSound) {
        assert (!this.eventSoundPool.contains(eventSound));
        this.eventSoundPool.push(eventSound);
    }

    private FileSound allocFileSound() {
        return this.fileSoundPool.isEmpty() ? new FileSound(this) : this.fileSoundPool.pop();
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

    public long playStreamImpl(String url, IsoObject parent) {
        DebugLog.log("Internet Radio - Trying to play the stream: " + url);

        long sound = javafmod.FMOD_System_CreateSound(url, FMODManager.FMOD_CREATESTREAM);
        DebugLog.log("Internet Radio - FMOD_System_CreateSound returned handle: " + sound);
        if (sound == 0L) {
            DebugLog.log("Internet Radio - Failed to create sound for: " + url);
            return 0L;
        }

        long channel = javafmod.FMOD_System_PlaySound(sound, true);
        DebugLog.log("Internet Radio - FMOD_System_PlaySound returned channel: " + channel);
        if (channel == 0L) {
            DebugLog.log("Internet Radio - Failed to play sound for: " + url);
            return 0L;
        }

        javafmod.FMOD_Channel_SetMode(channel, FMODManager.FMOD_3D);
        DebugLog.log("Internet Radio - Set channel mode to FMOD_3D");

        javafmod.FMOD_Channel_Set3DAttributes(channel, this.x, this.y, this.z * 3.0f, 0.0f, 0.0f, 0.0f);
        DebugLog.log("Internet Radio - Set 3D attributes: x=" + this.x + " y=" + this.y + " z=" + this.z);

        Float minDistance = this.getSandboxValue("InternetRadio.MinDistance", 1.0f);
        Float maxDistance = this.getSandboxValue("InternetRadio.MaxDistance", 30.0f);
        DebugLog.log("Internet Radio - Loaded sandbox distances: min=" + minDistance + " max=" + maxDistance);

        javafmod.FMOD_Channel_Set3DMinMaxDistance(channel, minDistance, maxDistance);
        DebugLog.log("Internet Radio - Applied 3DMinMaxDistance: min=" + minDistance + " max=" + maxDistance);

        javafmod.FMOD_Channel_SetVolume(channel, 1.0f);
        DebugLog.log("Internet Radio - Set volume to 1.0");

        GameSound dummyGameSound = new GameSound();
        dummyGameSound.name = "StreamSound";
        dummyGameSound.loop = true;
        dummyGameSound.is3D = true;
        dummyGameSound.maxInstancesPerEmitter = 1;
        DebugLog.log("Internet Radio - Created dummy GameSound: " + dummyGameSound.name);

        GameSoundClip dummyClip = new GameSoundClip(dummyGameSound);
        dummyClip.file = url;
        dummyClip.distanceMin = minDistance;
        dummyClip.distanceMax = maxDistance;
        dummyGameSound.clips.add(dummyClip);
        DebugLog.log("Internet Radio - Created dummy GameSoundClip with file: " + url);
        DebugLog.log("Internet Radio - DummyClip distances set: min=" + dummyClip.distanceMin + " max=" + dummyClip.distanceMax);

        FileSound fileSound = this.allocFileSound();
        fileSound.clip = dummyClip;
        fileSound.name = dummyGameSound.getName();
        fileSound.sound = sound;
        fileSound.channel = channel;
        fileSound.parent = parent;
        fileSound.volume = 1.0f;
        fileSound.setVolume = 1.0f;
        fileSound.is3D = (byte) 1;
        DebugLog.log("Internet Radio - Allocated FileSound with channel=" + channel + " sound=" + sound);

        this.ToStart.add(fileSound);
        DebugLog.log("Internet Radio - Added FileSound to ToStart list, size now=" + this.ToStart.size());

        long reference = fileSound.getRef();
        DebugLog.log("Internet Radio - Returning reference: " + reference);

        return reference;
    }

    private void releaseFileSound(FileSound fileSound) {
        assert (!this.fileSoundPool.contains(fileSound));
        this.fileSoundPool.push(fileSound);
    }

    private Sound addSound(GameSoundClip gameSoundClip, float f, IsoObject isoObject) {
        if (gameSoundClip == null) {
            DebugLog.log("null sound passed to SoundEmitter.playSoundImpl");
            return null;
        }
        if (gameSoundClip.gameSound.maxInstancesPerEmitter > 0) {
            this.limitSound(gameSoundClip.gameSound, gameSoundClip.gameSound.maxInstancesPerEmitter - 1);
        }
        if (gameSoundClip.event != null && !gameSoundClip.event.isEmpty()) {
            long l;
            if (gameSoundClip.eventDescription == null) {
                return null;
            }
            FMOD_STUDIO_EVENT_DESCRIPTION fMOD_STUDIO_EVENT_DESCRIPTION = gameSoundClip.eventDescription;
            if (gameSoundClip.eventDescriptionMP != null && this.parent instanceof IsoPlayer && !((IsoPlayer) this.parent).isLocalPlayer()) {
                fMOD_STUDIO_EVENT_DESCRIPTION = gameSoundClip.eventDescriptionMP;
            }
            if ((l = javafmod.FMOD_Studio_System_CreateEventInstance(fMOD_STUDIO_EVENT_DESCRIPTION.address)) < 0L) {
                return null;
            }
            if (gameSoundClip.hasMinDistance()) {
                javafmodJNI.FMOD_Studio_EventInstance_SetProperty(l, FMOD_STUDIO_EVENT_PROPERTY.FMOD_STUDIO_EVENT_PROPERTY_MINIMUM_DISTANCE.ordinal(), gameSoundClip.getMinDistance());
            }
            if (gameSoundClip.hasMaxDistance()) {
                javafmodJNI.FMOD_Studio_EventInstance_SetProperty(l, FMOD_STUDIO_EVENT_PROPERTY.FMOD_STUDIO_EVENT_PROPERTY_MAXIMUM_DISTANCE.ordinal(), gameSoundClip.getMaxDistance());
            }
            EventSound eventSound = this.allocEventSound();
            eventSound.clip = gameSoundClip;
            eventSound.name = gameSoundClip.gameSound.getName();
            eventSound.eventInstance = l;
            eventSound.volume = f;
            eventSound.parent = isoObject;
            eventSound.setVolume = 1.0f;
            eventSound.setZ = 0.0f;
            eventSound.setY = 0.0f;
            eventSound.setX = 0.0f;
            this.ToStart.add(eventSound);
            return eventSound;
        }
        if (gameSoundClip.file != null && !gameSoundClip.file.isEmpty()) {
            long l = FMODManager.instance.loadSound(gameSoundClip.file);
            if (l == 0L) {
                return null;
            }
            long l2 = javafmod.FMOD_System_PlaySound(l, true);
            javafmod.FMOD_Channel_SetVolume(l2, 0.0f);
            javafmod.FMOD_Channel_SetPriority(l2, 9 - gameSoundClip.priority);
            javafmod.FMOD_Channel_SetChannelGroup(l2, FMODManager.instance.channelGroupInGameNonBankSounds);
            if (gameSoundClip.distanceMax == 0.0f || this.x == 0.0f && this.y == 0.0f) {
                javafmod.FMOD_Channel_SetMode(l2, FMODManager.FMOD_2D);
            }
            FileSound fileSound = this.allocFileSound();
            fileSound.clip = gameSoundClip;
            fileSound.name = gameSoundClip.gameSound.getName();
            fileSound.sound = l;
            fileSound.pitch = gameSoundClip.pitch;
            fileSound.channel = l2;
            fileSound.parent = isoObject;
            fileSound.volume = f;
            fileSound.setVolume = 1.0f;
            fileSound.setZ = 0.0f;
            fileSound.setY = 0.0f;
            fileSound.setX = 0.0f;
            fileSound.is3D = (byte) -1;
            fileSound.ambient = false;
            this.ToStart.add(fileSound);
            return fileSound;
        }
        return null;
    }

    private void sendStopSound(String string, boolean bl) {
        if (GameClient.bClient && this.parent instanceof IsoMovingObject) {
            GameClient.instance.StopSound((IsoMovingObject) this.parent, string, bl);
        }
    }

    public static void update() {
        CurrentTimeMS = System.currentTimeMillis();
    }

    static {
        CurrentTimeMS = 0L;
    }

    private static abstract class Sound {
        public final FMODSoundEmitter emitter;
        public GameSoundClip clip;
        public String name;
        public float volume = 1.0f;
        public float pitch = 1.0f;
        public IsoObject parent;
        public float setVolume = 1.0f;
        public float setX = 0.0f;
        public float setY = 0.0f;
        public float setZ = 0.0f;

        public Sound(FMODSoundEmitter fMODSoundEmitter) {
            this.emitter = fMODSoundEmitter;
        }

        abstract long getRef();

        abstract void stop();

        abstract void set3D(boolean var1);

        abstract void release();

        abstract boolean tick(boolean var1);

        abstract boolean tickWhileStopped();

        public float getVolume() {
            this.clip = this.clip.checkReloaded();
            return this.volume * this.clip.getEffectiveVolume();
        }

        abstract void setParameterValue(FMOD_STUDIO_PARAMETER_DESCRIPTION var1, float var2);

        abstract void setTimelinePosition(String var1);

        abstract void triggerCue();

        abstract boolean isTriggeredCue();

        abstract boolean restart();
    }

    private static final class EventSound
            extends Sound {
        private long eventInstance;
        private long eventInstanceStopped;
        private boolean bTriggeredCue = false;
        private long checkTimeMS = 0L;

        public EventSound(FMODSoundEmitter fMODSoundEmitter) {
            super(fMODSoundEmitter);
        }

        @Override
        public long getRef() {
            return this.eventInstance;
        }

        @Override
        public void stop() {
            if (this.eventInstance == 0L) {
                return;
            }
            this.emitter.stopEvent(this.eventInstance, this.clip);
            javafmod.FMOD_Studio_EventInstance_Stop(this.eventInstance, false);
            this.eventInstanceStopped = this.eventInstance;
            this.eventInstance = 0L;
        }

        @Override
        public void set3D(boolean bl) {
        }

        @Override
        public void release() {
            this.stop();
            this.checkTimeMS = 0L;
            this.bTriggeredCue = false;
            this.emitter.releaseEventSound(this);
        }

        @Override
        public boolean tick(boolean bl) {
            float f;
            int n;
            IsoPlayer isoPlayer = IsoPlayer.getInstance();
            if (IsoPlayer.numPlayers > 1) {
                isoPlayer = null;
            }
            if (!bl) {
                n = javafmod.FMOD_Studio_GetPlaybackState(this.eventInstance);
                if (n == FMOD_STUDIO_PLAYBACK_STATE.FMOD_STUDIO_PLAYBACK_STOPPING.index) {
                    return false;
                }
                if (n == FMOD_STUDIO_PLAYBACK_STATE.FMOD_STUDIO_PLAYBACK_STOPPED.index) {
                    javafmod.FMOD_Studio_ReleaseEventInstance(this.eventInstance);
                    this.emitter.stopEvent(this.eventInstance, this.clip);
                    this.eventInstance = 0L;
                    return true;
                }
                if (this.bTriggeredCue && CurrentTimeMS - this.checkTimeMS > 250L && n == FMOD_STUDIO_PLAYBACK_STATE.FMOD_STUDIO_PLAYBACK_SUSTAINING.index) {
                    javafmodJNI.FMOD_Studio_EventInstance_KeyOff(this.eventInstance);
                }
                if (this.bTriggeredCue && this.clip.eventDescription.length > 0L && CurrentTimeMS - this.checkTimeMS > 1500L) {
                    long l = javafmodJNI.FMOD_Studio_GetTimelinePosition(this.eventInstance);
                    if (l > this.clip.eventDescription.length + 1000L) {
                        javafmod.FMOD_Studio_EventInstance_Stop(this.eventInstance, false);
                    }
                    this.checkTimeMS = CurrentTimeMS;
                }
            }
            int n2 = n = Float.compare(this.emitter.x, this.setX) != 0 || Float.compare(this.emitter.y, this.setY) != 0 || Float.compare(this.emitter.z, this.setZ) != 0 ? 1 : 0;
            if (n != 0) {
                this.setX = this.emitter.x;
                this.setY = this.emitter.y;
                this.setZ = this.emitter.z;
                javafmod.FMOD_Studio_EventInstance3D(this.eventInstance, this.emitter.x, this.emitter.y, this.emitter.z * 3.0f);
            }
            if (Float.compare(f = this.getVolume(), this.setVolume) != 0) {
                this.setVolume = f;
                javafmod.FMOD_Studio_EventInstance_SetVolume(this.eventInstance, f);
            }
            if (bl) {
                this.emitter.startEvent(this.eventInstance, this.clip);
                javafmod.FMOD_Studio_StartEvent(this.eventInstance);
            } else {
                this.emitter.updateEvent(this.eventInstance, this.clip);
            }
            return false;
        }

        @Override
        public boolean tickWhileStopped() {
            int n = javafmod.FMOD_Studio_GetPlaybackState(this.eventInstanceStopped);
            if (n == FMOD_STUDIO_PLAYBACK_STATE.FMOD_STUDIO_PLAYBACK_STOPPED.index) {
                javafmod.FMOD_Studio_ReleaseEventInstance(this.eventInstanceStopped);
                this.eventInstanceStopped = 0L;
                return true;
            }
            return false;
        }

        @Override
        public void setParameterValue(FMOD_STUDIO_PARAMETER_DESCRIPTION fMOD_STUDIO_PARAMETER_DESCRIPTION, float f) {
            if (this.eventInstance == 0L) {
                return;
            }
            javafmod.FMOD_Studio_EventInstance_SetParameterByID(this.eventInstance, fMOD_STUDIO_PARAMETER_DESCRIPTION.id, f, false);
        }

        @Override
        public void setTimelinePosition(String string) {
            if (this.eventInstance == 0L || this.clip == null || this.clip.event == null) {
                return;
            }
            SoundTimelineScript soundTimelineScript = ScriptManager.instance.getSoundTimeline(this.clip.event);
            if (soundTimelineScript == null) {
                return;
            }
            int n = soundTimelineScript.getPosition(string);
            if (n == -1) {
                return;
            }
            javafmodJNI.FMOD_Studio_EventInstance_SetTimelinePosition(this.eventInstance, n);
        }

        @Override
        public void triggerCue() {
            if (this.eventInstance == 0L) {
                return;
            }
            if (!this.clip.hasSustainPoints()) {
                return;
            }
            javafmodJNI.FMOD_Studio_EventInstance_KeyOff(this.eventInstance);
            this.bTriggeredCue = true;
            this.checkTimeMS = CurrentTimeMS;
        }

        @Override
        public boolean isTriggeredCue() {
            return this.bTriggeredCue;
        }

        @Override
        public boolean restart() {
            if (this.eventInstance == 0L) {
                return false;
            }
            javafmodJNI.FMOD_Studio_StartEvent(this.eventInstance);
            return true;
        }
    }

    private static final class FileSound
            extends Sound {
        private long sound;
        private long channel;
        private byte is3D = (byte) -1;
        boolean ambient;
        private float lx;
        private float ly;
        private float lz;

        private FileSound(FMODSoundEmitter fMODSoundEmitter) {
            super(fMODSoundEmitter);
        }

        @Override
        public long getRef() {
            return this.channel;
        }

        @Override
        public void stop() {
            if (this.channel == 0L) {
                return;
            }
            javafmod.FMOD_Channel_Stop(this.channel);
            this.sound = 0L;
            this.channel = 0L;
        }

        @Override
        public void set3D(boolean bl) {
            if (this.is3D != (byte) (bl ? 1 : 0)) {
                javafmod.FMOD_Channel_SetMode(this.channel, bl ? (long) FMODManager.FMOD_3D : (long) FMODManager.FMOD_2D);
                if (bl) {
                    javafmod.FMOD_Channel_Set3DAttributes(this.channel, this.emitter.x, this.emitter.y, this.emitter.z * 3.0f, 0.0f, 0.0f, 0.0f);
                }
                this.is3D = (byte) (bl ? 1 : 0);
            }
        }

        @Override
        public void release() {
            this.stop();
            this.emitter.releaseFileSound(this);
        }

        @Override
        public boolean tick(boolean bl) {
            int n;
            int n2;
            int n3;
            float f;
            if (bl && this.clip.gameSound.isLooped()) {
                javafmod.FMOD_Channel_SetMode(this.channel, FMODManager.FMOD_LOOP_NORMAL);
            }
            float f2 = this.clip.distanceMin;
            if (!bl && !javafmod.FMOD_Channel_IsPlaying(this.channel)) {
                return true;
            }
            float f3 = this.emitter.x;
            float f4 = this.emitter.y;
            float f5 = this.emitter.z;
            if (!this.clip.gameSound.is3D || f3 == 0.0f && f4 == 0.0f) {
                if (!(f3 == 0.0f && f4 == 0.0f || !bl && f3 == this.lx && f4 == this.ly || this.is3D != 1)) {
                    javafmod.FMOD_Channel_Set3DAttributes(this.channel, f3, f4, f5 * 3.0f, 0.0f, 0.0f, 0.0f);
                }
                javafmod.FMOD_Channel_SetVolume(this.channel, this.getVolume());
                javafmod.FMOD_Channel_SetPitch(this.channel, this.pitch);
                if (bl) {
                    javafmod.FMOD_Channel_SetPaused(this.channel, false);
                }
                return false;
            }
            this.lx = f3;
            this.ly = f4;
            this.lz = f5;
            javafmod.FMOD_Channel_Set3DAttributes(this.channel, f3, f4, f5 * 3.0f, f3 - this.lx, f4 - this.ly, f5 * 3.0f - this.lz * 3.0f);
            float f6 = Float.MAX_VALUE;
            for (int i = 0; i < IsoPlayer.numPlayers; ++i) {
                IsoPlayer isoPlayer = IsoPlayer.players[i];
                if (isoPlayer == null || isoPlayer.isDeaf())
                    continue;
                f = IsoUtils.DistanceTo(f3, f4, f5 * 3.0f, isoPlayer.getX(), isoPlayer.getY(), isoPlayer.getZ() * 3.0f);
                f6 = PZMath.min(f6, f);
            }
            float f7 = 2.0f;
            float f8 = f6 >= f7 ? 1.0f : 1.0f - (f7 - f6) / f7;
            javafmodJNI.FMOD_Channel_Set3DLevel(this.channel, f8);
            if (IsoPlayer.numPlayers > 1) {
                if (bl) {
                    javafmod.FMOD_System_SetReverbDefault(0, FMODManager.FMOD_PRESET_OFF);
                    javafmod.FMOD_Channel_Set3DMinMaxDistance(this.channel, this.clip.distanceMin, this.clip.distanceMax);
                    javafmod.FMOD_Channel_Set3DOcclusion(this.channel, 0.0f, 0.0f);
                }
                javafmod.FMOD_Channel_SetVolume(this.channel, this.getVolume());
                if (bl) {
                    javafmod.FMOD_Channel_SetPaused(this.channel, false);
                }
                javafmod.FMOD_Channel_SetReverbProperties(this.channel, 0, 0.0f);
                javafmod.FMOD_Channel_SetReverbProperties(this.channel, 1, 0.0f);
                javafmod.FMOD_System_SetReverbDefault(1, FMODManager.FMOD_PRESET_OFF);
                javafmod.FMOD_Channel_Set3DOcclusion(this.channel, 0.0f, 0.0f);
                return false;
            }
            f6 = this.clip.reverbMaxRange;
            f7 = IsoUtils.DistanceManhatten(f3, f4, IsoPlayer.getInstance().getX(), IsoPlayer.getInstance().getY(), f5, IsoPlayer.getInstance().getZ()) / f6;
            IsoGridSquare isoGridSquare = IsoPlayer.getInstance().getCurrentSquare();
            if (isoGridSquare == null) {
                javafmod.FMOD_Channel_Set3DMinMaxDistance(this.channel, f2, this.clip.distanceMax);
                javafmod.FMOD_Channel_SetVolume(this.channel, this.getVolume());
                if (bl) {
                    javafmod.FMOD_Channel_SetPaused(this.channel, false);
                }
                return false;
            }
            if (isoGridSquare.getRoom() == null) {
                if (!this.ambient) {
                    f7 += IsoPlayer.getInstance().numNearbyBuildingsRooms / 32.0f;
                }
                if (!this.ambient) {
                    f7 += 0.08f;
                }
            } else {
                f = isoGridSquare.getRoom().Squares.size();
                if (!this.ambient) {
                    f7 += f / 500.0f;
                }
            }
            if (f7 > 1.0f) {
                f7 = 1.0f;
            }
            f7 *= f7;
            f7 *= f7;
            f7 *= this.clip.reverbFactor;
            f7 *= 10.0f;
            if (IsoPlayer.getInstance().getCurrentSquare().getRoom() == null && f7 < 0.1f) {
                f7 = 0.1f;
            }
            if (!this.ambient) {
                if (isoGridSquare.getRoom() != null) {
                    n3 = 0;
                    n2 = 1;
                    n = 2;
                } else {
                    n3 = 2;
                    n2 = 0;
                    n = 1;
                }
            } else {
                n3 = 2;
                n2 = 0;
                n = 1;
            }
            Object object = IsoWorld.instance.CurrentCell.getGridSquare(f3, f4, f5);
            if (object != null && ((IsoGridSquare) object).getZone() != null
                    && (((IsoGridSquare) object).getZone().getType().equals("Forest") || ((IsoGridSquare) object).getZone().getType().equals("DeepForest"))) {
                n3 = 1;
                n2 = 0;
                n = 2;
            }
            javafmod.FMOD_Channel_SetReverbProperties(this.channel, n3, 0.0f);
            javafmod.FMOD_Channel_SetReverbProperties(this.channel, n2, 0.0f);
            javafmod.FMOD_Channel_SetReverbProperties(this.channel, n, 0.0f);
            javafmod.FMOD_Channel_Set3DMinMaxDistance(this.channel, f2, this.clip.distanceMax);
            IsoGridSquare isoGridSquare2 = IsoWorld.instance.CurrentCell.getGridSquare(f3, f4, f5);
            float f9 = 0.0f;
            float f10 = 0.0f;
            if (isoGridSquare2 != null) {
                if (this.emitter.parent instanceof IsoWindow || this.emitter.parent instanceof IsoDoor) {
                    object = IsoPlayer.getInstance().getCurrentSquare().getRoom();
                    if (object != this.emitter.parent.square.getRoom()) {
                        if (object != null && ((IsoRoom) object).getBuilding() == this.emitter.parent.square.getBuilding()) {
                            f9 = 0.33f;
                            f10 = 0.33f;
                        } else {
                            IsoGridSquare isoGridSquare3 = null;
                            if (this.emitter.parent instanceof IsoDoor) {
                                IsoDoor isoDoor = (IsoDoor) this.emitter.parent;
                                isoGridSquare3 = isoDoor.north ? IsoWorld.instance.CurrentCell.getGridSquare(isoDoor.getX(), isoDoor.getY() - 1.0f, isoDoor.getZ())
                                        : IsoWorld.instance.CurrentCell.getGridSquare(isoDoor.getX() - 1.0f, isoDoor.getY(), isoDoor.getZ());
                            } else {
                                IsoWindow isoWindow = (IsoWindow) this.emitter.parent;
                                isoGridSquare3 = isoWindow.isNorth() ? IsoWorld.instance.CurrentCell.getGridSquare(isoWindow.getX(), isoWindow.getY() - 1.0f, isoWindow.getZ())
                                        : IsoWorld.instance.CurrentCell.getGridSquare(isoWindow.getX() - 1.0f, isoWindow.getY(), isoWindow.getZ());
                            }
                            if (isoGridSquare3 != null && ((object = IsoPlayer.getInstance().getCurrentSquare().getRoom()) != null || isoGridSquare3.getRoom() == null)) {
                                if (object != null && isoGridSquare3.getRoom() != null && ((IsoRoom) object).building == isoGridSquare3.getBuilding()) {
                                    if (object != isoGridSquare3.getRoom()) {
                                        if (((IsoRoom) object).def.level == isoGridSquare3.getZ()) {
                                            f9 = 0.33f;
                                            f10 = 0.33f;
                                        } else {
                                            f9 = 0.6f;
                                            f10 = 0.6f;
                                        }
                                    }
                                } else {
                                    f9 = 0.33f;
                                    f10 = 0.33f;
                                }
                            }
                        }
                    }
                } else if (isoGridSquare2.getRoom() != null) {
                    object = IsoPlayer.getInstance().getCurrentSquare().getRoom();
                    if (object == null) {
                        f9 = 0.33f;
                        f10 = 0.23f;
                    } else if (object != isoGridSquare2.getRoom()) {
                        f9 = 0.24f;
                        f10 = 0.24f;
                    }
                    if (object != null && isoGridSquare2.getRoom().getBuilding() != ((IsoRoom) object).getBuilding()) {
                        f9 = 1.0f;
                        f10 = 0.8f;
                    }
                    if (object != null && isoGridSquare2.getRoom().def.level != (int) IsoPlayer.getInstance().getZ()) {
                        f9 = 0.6f;
                        f10 = 0.6f;
                    }
                } else {
                    object = IsoPlayer.getInstance().getCurrentSquare().getRoom();
                    if (object != null) {
                        f9 = 0.79f;
                        f10 = 0.59f;
                    }
                }
                if (!isoGridSquare2.isCouldSee(IsoPlayer.getPlayerIndex()) && isoGridSquare2 != IsoPlayer.getInstance().getCurrentSquare()) {
                    f9 += 0.4f;
                }
            } else {
                if (IsoWorld.instance.MetaGrid.getRoomAt((int) Math.floor(f3), PZMath.fastfloor(f4), (int) Math.floor(f5)) != null) {
                    f9 = 1.0f;
                    f10 = 1.0f;
                }
                f9 = (object = IsoPlayer.getInstance().getCurrentSquare().getRoom()) != null ? (f9 += 0.94f) : (f9 += 0.6f);
            }
            if (isoGridSquare2 != null && (int) IsoPlayer.getInstance().getZ() != isoGridSquare2.getZ()) {
                f9 *= 1.3f;
            }
            if (f9 > 0.9f) {
                f9 = 0.9f;
            }
            if (f10 > 0.9f) {
                f10 = 0.9f;
            }
            if (this.emitter.emitterType == EmitterType.Footstep && f5 > IsoPlayer.getInstance().getZ() && isoGridSquare2.getBuilding() == IsoPlayer.getInstance().getBuilding()) {
                f9 = 0.0f;
                f10 = 0.0f;
            }
            if ("HouseAlarm".equals(this.name)) {
                f9 = 0.0f;
                f10 = 0.0f;
            }
            javafmod.FMOD_Channel_Set3DOcclusion(this.channel, f9, f10);
            javafmod.FMOD_Channel_SetVolume(this.channel, this.getVolume());
            javafmod.FMOD_Channel_SetPitch(this.channel, this.pitch);
            if (bl) {
                javafmod.FMOD_Channel_SetPaused(this.channel, false);
            }
            this.lx = f3;
            this.ly = f4;
            this.lz = f5;
            return false;
        }

        @Override
        public boolean tickWhileStopped() {
            return true;
        }

        @Override
        void setParameterValue(FMOD_STUDIO_PARAMETER_DESCRIPTION fMOD_STUDIO_PARAMETER_DESCRIPTION, float f) {
        }

        @Override
        void setTimelinePosition(String string) {
        }

        @Override
        void triggerCue() {
        }

        @Override
        boolean isTriggeredCue() {
            return false;
        }

        @Override
        boolean restart() {
            return false;
        }
    }

    private static final class ParameterValue {
        private long eventInstance;
        private FMOD_STUDIO_PARAMETER_DESCRIPTION parameterDescription;
        private float value;

        private ParameterValue() {
        }
    }
}

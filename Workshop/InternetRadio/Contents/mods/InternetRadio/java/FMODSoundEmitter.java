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
import java.util.List;
import zombie.GameSounds;
import zombie.SandboxOptions;
import zombie.SoundManager;
import zombie.UsedFromLua;
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
import zombie.network.PacketTypes;
import zombie.network.packets.INetworkPacket;
import zombie.popman.ObjectPool;
import zombie.scripting.ScriptManager;
import zombie.scripting.objects.CharacterTrait;
import zombie.scripting.objects.SoundTimelineScript;

@UsedFromLua
public final class FMODSoundEmitter extends BaseSoundEmitter {

    private final ArrayList<Sound> toStart = new ArrayList();
    private final ArrayList<Sound> instances = new ArrayList();
    private final ArrayList<Sound> stopped = new ArrayList();
    public float x;
    public float y;
    public float z;
    public EmitterType emitterType;
    public IsoObject parent;
    private final ParameterOcclusion occlusion = new ParameterOcclusion(this);
    private final ArrayList<FMODParameter> parameters = new ArrayList();
    public IFMODParameterUpdater parameterUpdater;
    private final ArrayList<ParameterValue> parameterValues = new ArrayList();
    private static final ObjectPool<ParameterValue> parameterValuePool =
        new ObjectPool<ParameterValue>(ParameterValue::new);
    private static BitSet parameterSet;
    private final ArrayDeque<EventSound> eventSoundPool = new ArrayDeque();
    private final ArrayDeque<FileSound> fileSoundPool = new ArrayDeque();
    private static long currentTimeMs;
    public static String ribsVersionFMODSoundEmitter = "2.0.0";

    public FMODSoundEmitter() {
        SoundManager.instance.registerEmitter(this);
        if (parameterSet == null) {
            parameterSet = new BitSet(FMODManager.instance.getParameterCount());
        }
    }

    @Override
    public void randomStart() {}

    @Override
    public void setPos(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public int stopSound(long soundRef) {
        return this.stopSound(soundRef, true);
    }

    @Override
    public int stopSoundDelayRelease(long soundRef) {
        return this.stopSound(soundRef, false);
    }

    private int stopSound(long soundRef, boolean bReleaseEvent) {
        Sound s;
        int i;
        for (i = 0; i < this.toStart.size(); ++i) {
            s = this.toStart.get(i);
            if (s.getRef() != soundRef) continue;
            this.sendStopSound(s.name, false);
            s.release(s.clip.isStopImmediate());
            this.toStart.remove(i--);
        }
        for (i = 0; i < this.instances.size(); ++i) {
            s = this.instances.get(i);
            if (s.getRef() != soundRef) continue;
            s.stop(bReleaseEvent, s.clip.isStopImmediate());
            this.sendStopSound(s.name, false);
            if (bReleaseEvent) {
                s.release(s.clip.isStopImmediate());
            } else {
                this.stopped.add(s);
            }
            this.instances.remove(i--);
        }
        return 0;
    }

    @Override
    public void stopSoundLocal(long soundRef) {
        Sound s;
        int i;
        for (i = 0; i < this.toStart.size(); ++i) {
            s = this.toStart.get(i);
            if (s.getRef() != soundRef) continue;
            s.release(s.clip.isStopImmediate());
            this.toStart.remove(i--);
        }
        for (i = 0; i < this.instances.size(); ++i) {
            s = this.instances.get(i);
            if (s.getRef() != soundRef) continue;
            s.stop(true, s.clip.isStopImmediate());
            s.release(s.clip.isStopImmediate());
            this.instances.remove(i--);
        }
    }

    @Override
    public void stopOrTriggerSoundLocal(long soundRef) {
        Sound s;
        int i;
        for (i = 0; i < this.toStart.size(); ++i) {
            s = this.toStart.get(i);
            if (s.getRef() != soundRef) continue;
            s.release(s.clip.isStopImmediate());
            this.toStart.remove(i--);
        }
        for (i = 0; i < this.instances.size(); ++i) {
            s = this.instances.get(i);
            if (s.getRef() != soundRef) continue;
            if (s.clip.hasSustainPoints()) {
                s.triggerCue();
                continue;
            }
            s.stop(true, s.clip.isStopImmediate());
            s.release(s.clip.isStopImmediate());
            this.instances.remove(i--);
        }
    }

    @Override
    public int stopSoundByName(String name) {
        Sound s;
        int i;
        GameSound gameSound = GameSounds.getSound(name);
        if (gameSound == null) {
            return 0;
        }
        for (i = 0; i < this.toStart.size(); ++i) {
            s = this.toStart.get(i);
            if (!gameSound.clips.contains(s.clip)) continue;
            s.release(s.clip.isStopImmediate());
            this.toStart.remove(i--);
        }
        for (i = 0; i < this.instances.size(); ++i) {
            s = this.instances.get(i);
            if (!gameSound.clips.contains(s.clip)) continue;
            s.stop(true, s.clip.isStopImmediate());
            s.release(s.clip.isStopImmediate());
            this.instances.remove(i--);
        }
        return 0;
    }

    @Override
    public void stopOrTriggerSound(long handle) {
        int index = this.findToStart(handle);
        if (index != -1) {
            Sound s = this.toStart.remove(index);
            this.sendStopSound(s.name, true);
            s.release(s.clip.isStopImmediate());
            return;
        }
        index = this.findInstance(handle);
        if (index != -1) {
            Sound s = this.instances.get(index);
            this.sendStopSound(s.name, true);
            if (s instanceof EventSound) {
                EventSound eventSound = (EventSound) s;
                FMOD_STUDIO_PARAMETER_DESCRIPTION parameterDescription =
                    FMODManager.instance.getParameterDescription(
                        "ActionProgressPercent"
                    );
                if (
                    eventSound.clip.eventDescription.hasParameter(
                        parameterDescription
                    )
                ) {
                    eventSound.setParameterValue(parameterDescription, 100.0f);
                    eventSound.triggeredCue = true;
                    eventSound.checkTimeMs = currentTimeMs;
                    return;
                }
            }
            if (s.clip.hasSustainPoints()) {
                s.triggerCue();
            } else {
                this.instances.remove(index);
                s.stop(true, s.clip.isStopImmediate());
                s.release(s.clip.isStopImmediate());
            }
        }
    }

    @Override
    public void stopOrTriggerSoundByName(String name) {
        Sound s;
        int i;
        GameSound gameSound = GameSounds.getSound(name);
        if (gameSound == null) {
            return;
        }
        for (i = 0; i < this.toStart.size(); ++i) {
            s = this.toStart.get(i);
            if (!gameSound.clips.contains(s.clip)) continue;
            this.toStart.remove(i--);
            s.release(s.clip.isStopImmediate());
        }
        for (i = 0; i < this.instances.size(); ++i) {
            s = this.instances.get(i);
            if (!gameSound.clips.contains(s.clip)) continue;
            if (s.clip.hasSustainPoints()) {
                s.triggerCue();
                continue;
            }
            s.stop(true, s.clip.isStopImmediate());
            s.release(s.clip.isStopImmediate());
            this.instances.remove(i--);
        }
    }

    private void limitSound(GameSound gameSound, int maxInstances) {
        Sound s;
        int i;
        int total =
            this.countToStart(gameSound) + this.countInstances(gameSound);
        if (total <= maxInstances) {
            return;
        }
        for (i = 0; i < this.toStart.size(); ++i) {
            s = this.toStart.get(i);
            if (!gameSound.clips.contains(s.clip)) continue;
            this.toStart.remove(i--);
            s.release(s.clip.isStopImmediate());
            if (--total > maxInstances) continue;
            return;
        }
        for (i = 0; i < this.instances.size(); ++i) {
            s = this.instances.get(i);
            if (!gameSound.clips.contains(s.clip)) continue;
            if (s.clip.hasSustainPoints()) {
                if (s.isTriggeredCue()) continue;
                s.triggerCue();
                continue;
            }
            s.stop(true, s.clip.isStopImmediate());
            s.release(s.clip.isStopImmediate());
            this.instances.remove(i--);
            if (--total > maxInstances) continue;
            return;
        }
    }

    @Override
    public void setVolume(long soundRef, float volume) {
        Sound s;
        int i;
        for (i = 0; i < this.toStart.size(); ++i) {
            s = this.toStart.get(i);
            if (s.getRef() != soundRef) continue;
            s.volume = volume;
        }
        for (i = 0; i < this.instances.size(); ++i) {
            s = this.instances.get(i);
            if (s.getRef() != soundRef) continue;
            s.volume = volume;
        }
    }

    @Override
    public void setPitch(long soundRef, float pitch) {
        Sound s;
        int i;
        for (i = 0; i < this.toStart.size(); ++i) {
            s = this.toStart.get(i);
            if (s.getRef() == soundRef) {
                DebugLog.log("Set pitch for ToStart");
            }
            s.pitch = pitch;
        }
        for (i = 0; i < this.instances.size(); ++i) {
            s = this.instances.get(i);
            if (s.getRef() == soundRef) {
                DebugLog.log("Set pitch for Instance");
            }
            s.pitch = pitch;
        }
    }

    @Override
    public boolean hasSustainPoints(long soundRef) {
        Sound s;
        int i;
        for (i = 0; i < this.toStart.size(); ++i) {
            s = this.toStart.get(i);
            if (s.getRef() != soundRef) continue;
            if (s.clip.eventDescription == null) {
                return false;
            }
            return s.clip.eventDescription.hasSustainPoints;
        }
        for (i = 0; i < this.instances.size(); ++i) {
            s = this.instances.get(i);
            if (s.getRef() != soundRef) continue;
            if (s.clip.eventDescription == null) {
                return false;
            }
            return s.clip.eventDescription.hasSustainPoints;
        }
        return false;
    }

    @Override
    public void triggerCue(long soundRef) {
        for (int i = 0; i < this.instances.size(); ++i) {
            Sound s = this.instances.get(i);
            if (s.getRef() != soundRef) continue;
            s.triggerCue();
        }
    }

    @Override
    public void setVolumeAll(float volume) {
        Sound s;
        int i;
        for (i = 0; i < this.toStart.size(); ++i) {
            s = this.toStart.get(i);
            s.volume = volume;
        }
        for (i = 0; i < this.instances.size(); ++i) {
            s = this.instances.get(i);
            s.volume = volume;
        }
    }

    @Override
    public void stopAll() {
        Sound s;
        int i;
        for (i = 0; i < this.toStart.size(); ++i) {
            s = this.toStart.get(i);
            s.release(s.clip.isStopImmediate());
        }
        for (i = 0; i < this.instances.size(); ++i) {
            s = this.instances.get(i);
            s.stop(true, s.clip.isStopImmediate());
            s.release(s.clip.isStopImmediate());
        }
        this.toStart.clear();
        this.instances.clear();
    }

    @Override
    public long playSound(String file) {
        if (GameClient.client) {
            IsoObject isoObject = this.parent;
            if (isoObject instanceof IsoMovingObject) {
                IsoPlayer player;
                IsoMovingObject movingObject = (IsoMovingObject) isoObject;
                IsoObject isoObject2 = this.parent;
                if (
                    !(isoObject2 instanceof IsoPlayer) ||
                    !(player = (IsoPlayer) isoObject2).isInvisible()
                ) {
                    INetworkPacket.send(
                        PacketTypes.PacketType.PlaySound,
                        file,
                        false,
                        movingObject
                    );
                }
            } else {
                GameClient.instance.PlayWorldSound(
                    file,
                    PZMath.fastfloor(this.x),
                    PZMath.fastfloor(this.y),
                    (byte) PZMath.fastfloor(this.z)
                );
            }
        }
        if (GameServer.server) {
            return 0L;
        }

        if (file != null && file.startsWith("http")) {
            return this.playStreamImpl(file, this.parent);
        }
        return this.playSoundImpl(file, (IsoObject) null);
    }

    @Override
    public long playSound(String file, IsoGameCharacter character) {
        if (GameClient.client) {
            if (!character.isInvisible()) {
                INetworkPacket.send(
                    PacketTypes.PacketType.PlaySound,
                    file,
                    false,
                    character
                );
            }
            return !character.isInvisible() ||
                DebugOptions.instance.character.debug.playSoundWhenInvisible.getValue()
                ? this.playSoundImpl(file, (IsoObject) null)
                : 0L;
        }
        if (GameServer.server) {
            return 0L;
        }
        return this.playSoundImpl(file, (IsoObject) null);
    }

    @Override
    public long playSound(String file, int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this.playSound(file);
    }

    @Override
    public long playSound(String file, IsoGridSquare square) {
        this.x = (float) square.x + 0.5f;
        this.y = (float) square.y + 0.5f;
        this.z = square.z;
        return this.playSound(file);
    }

    @Override
    public long playSoundImpl(String file, IsoGridSquare square) {
        this.x = (float) square.x + 0.5f;
        this.y = (float) square.y + 0.5f;
        this.z = (float) square.z + 0.5f;
        return this.playSoundImpl(file, (IsoObject) null);
    }

    @Override
    public long playSound(String file, boolean doWorldSound) {
        return this.playSound(file);
    }

    @Override
    public long playSoundImpl(
        String file,
        boolean doWorldSound,
        IsoObject parent
    ) {
        return this.playSoundImpl(file, parent);
    }

    @Override
    public long playSoundLooped(String file) {
        if (GameClient.client) {
            IsoObject isoObject = this.parent;
            if (isoObject instanceof IsoMovingObject) {
                IsoMovingObject isoMovingObject = (IsoMovingObject) isoObject;
                INetworkPacket.send(
                    PacketTypes.PacketType.PlaySound,
                    file,
                    true,
                    isoMovingObject
                );
            } else {
                GameClient.instance.PlayWorldSound(
                    file,
                    PZMath.fastfloor(this.x),
                    PZMath.fastfloor(this.y),
                    (byte) PZMath.fastfloor(this.z)
                );
            }
        }
        return this.playSoundLoopedImpl(file);
    }

    @Override
    public long playSoundLoopedImpl(String file) {
        return this.playSoundImpl(file, false, null);
    }

    @Override
    public long playSound(String file, IsoObject parent) {
        if (GameClient.client) {
            if (parent instanceof IsoMovingObject) {
                IsoMovingObject isoMovingObject = (IsoMovingObject) parent;
                INetworkPacket.send(
                    PacketTypes.PacketType.PlaySound,
                    file,
                    false,
                    isoMovingObject
                );
            } else {
                GameClient.instance.PlayWorldSound(
                    file,
                    PZMath.fastfloor(this.x),
                    PZMath.fastfloor(this.y),
                    (byte) PZMath.fastfloor(this.z)
                );
            }
        }
        if (GameServer.server) {
            return 0L;
        }
        return this.playSoundImpl(file, parent);
    }

    @Override
    public long playSoundImpl(String file, IsoObject parent) {
        if (file != null && file.startsWith("http")) {
            return this.playStreamImpl(file, parent);
        }

        if (file.startsWith("Radio") || file.startsWith("VehicleRadio")) {
            return 0L;
        }

        GameSound gameSound = GameSounds.getSound(file);
        if (gameSound == null) {
            return 0L;
        }
        GameSoundClip clip = gameSound.getRandomClip();
        return this.playClip(clip, parent);
    }

    @Override
    public long playClip(GameSoundClip clip, IsoObject parent) {
        Sound s = this.addSound(clip, 1.0f, parent);
        return s == null ? 0L : s.getRef();
    }

    @Override
    public long playAmbientSound(String name) {
        if (GameServer.server) {
            return 0L;
        }
        GameSound gameSound = GameSounds.getSound(name);
        if (gameSound == null) {
            return 0L;
        }
        GameSoundClip clip = gameSound.getRandomClip();
        Sound s = this.addSound(clip, 1.0f, null);
        if (s instanceof FileSound) {
            FileSound fileSound = (FileSound) s;
            fileSound.ambient = true;
        }
        return s == null ? 0L : s.getRef();
    }

    @Override
    public long playAmbientLoopedImpl(String file) {
        if (GameServer.server) {
            return 0L;
        }
        GameSound gameSound = GameSounds.getSound(file);
        if (gameSound == null) {
            return 0L;
        }
        GameSoundClip clip = gameSound.getRandomClip();
        Sound s = this.addSound(clip, 1.0f, null);
        return s == null ? 0L : s.getRef();
    }

    @Override
    public void set3D(long soundRef, boolean is3D) {
        Sound s;
        int i;
        for (i = 0; i < this.toStart.size(); ++i) {
            s = this.toStart.get(i);
            if (s.getRef() != soundRef) continue;
            s.set3D(is3D);
        }
        for (i = 0; i < this.instances.size(); ++i) {
            s = this.instances.get(i);
            if (s.getRef() != soundRef) continue;
            s.set3D(is3D);
        }
    }

    @Override
    public void tick() {
        Sound s;
        int i;
        if (!this.isEmpty()) {
            this.occlusion.update();
            for (i = 0; i < this.parameters.size(); ++i) {
                FMODParameter parameter = this.parameters.get(i);
                parameter.update();
            }
        }
        for (i = 0; i < this.toStart.size(); ++i) {
            Sound sound = this.toStart.get(i);
            this.instances.add(sound);
        }
        for (i = 0; i < this.instances.size(); ++i) {
            boolean isStarting;
            s = this.instances.get(i);
            if (!s.tick(isStarting = this.toStart.contains(s))) continue;
            this.instances.remove(i--);
            s.release(s.clip.isStopImmediate());
        }
        this.toStart.clear();
        for (i = 0; i < this.stopped.size(); ++i) {
            s = this.stopped.get(i);
            if (!s.tickWhileStopped()) continue;
            this.stopped.remove(i--);
            s.release(s.clip.isStopImmediate());
        }
    }

    @Override
    public boolean hasSoundsToStart() {
        return !this.toStart.isEmpty();
    }

    @Override
    public boolean isEmpty() {
        return (
            this.toStart.isEmpty() &&
            this.instances.isEmpty() &&
            this.stopped.isEmpty()
        );
    }

    @Override
    public boolean isPlaying(long soundRef) {
        int i;
        for (i = 0; i < this.toStart.size(); ++i) {
            if (this.toStart.get(i).getRef() != soundRef) continue;
            return true;
        }
        for (i = 0; i < this.instances.size(); ++i) {
            if (this.instances.get(i).getRef() != soundRef) continue;
            return true;
        }
        return false;
    }

    @Override
    public boolean isPlaying(String alias) {
        int i;
        for (i = 0; i < this.toStart.size(); ++i) {
            if (!alias.equals(this.toStart.get((int) i).name)) continue;
            return true;
        }
        for (i = 0; i < this.instances.size(); ++i) {
            if (!alias.equals(this.instances.get((int) i).name)) continue;
            return true;
        }
        return false;
    }

    @Override
    public boolean restart(long handle) {
        int index = this.findToStart(handle);
        if (index != -1) {
            return true;
        }
        index = this.findInstance(handle);
        return index != -1 && this.instances.get(index).restart();
    }

    private int findInstance(long soundRef) {
        for (int i = 0; i < this.instances.size(); ++i) {
            Sound sound = this.instances.get(i);
            if (sound.getRef() != soundRef) continue;
            return i;
        }
        return -1;
    }

    private int findInstance(String name) {
        GameSound gameSound = GameSounds.getSound(name);
        if (gameSound == null) {
            return -1;
        }
        for (int i = 0; i < this.instances.size(); ++i) {
            Sound sound = this.instances.get(i);
            if (!gameSound.clips.contains(sound.clip)) continue;
            return i;
        }
        return -1;
    }

    private int findToStart(long soundRef) {
        for (int i = 0; i < this.toStart.size(); ++i) {
            Sound sound = this.toStart.get(i);
            if (sound.getRef() != soundRef) continue;
            return i;
        }
        return -1;
    }

    private int findToStart(String name) {
        GameSound gameSound = GameSounds.getSound(name);
        if (gameSound == null) {
            return -1;
        }
        for (int i = 0; i < this.toStart.size(); ++i) {
            Sound sound = this.toStart.get(i);
            if (!gameSound.clips.contains(sound.clip)) continue;
            return i;
        }
        return -1;
    }

    private int findStopped(long soundRef) {
        for (int i = 0; i < this.stopped.size(); ++i) {
            Sound sound = this.stopped.get(i);
            if (!(sound instanceof EventSound)) continue;
            EventSound eventSound = (EventSound) sound;
            if (eventSound.eventInstanceStopped != soundRef) continue;
            return i;
        }
        return -1;
    }

    private int countToStart(GameSound gameSound) {
        int count = 0;
        for (int i = 0; i < this.toStart.size(); ++i) {
            Sound sound = this.toStart.get(i);
            if (!gameSound.clips.contains(sound.clip)) continue;
            ++count;
        }
        return count;
    }

    private int countInstances(GameSound gameSound) {
        int count = 0;
        for (int i = 0; i < this.instances.size(); ++i) {
            Sound sound = this.instances.get(i);
            if (!gameSound.clips.contains(sound.clip)) continue;
            ++count;
        }
        return count;
    }

    public void addParameter(FMODParameter parameter) {
        this.parameters.add(parameter);
    }

    @Override
    public void setParameterValue(
        long soundRef,
        FMOD_STUDIO_PARAMETER_DESCRIPTION parameterDescription,
        float value
    ) {
        if (soundRef == 0L || parameterDescription == null) {
            return;
        }
        int index = this.findInstance(soundRef);
        if (index != -1) {
            Sound sound = this.instances.get(index);
            sound.setParameterValue(parameterDescription, value);
            return;
        }
        index = this.findParameterValue(soundRef, parameterDescription);
        if (index != -1) {
            this.parameterValues.get((int) index).value = value;
            return;
        }
        index = this.findStopped(soundRef);
        if (index != -1) {
            javafmod.FMOD_Studio_EventInstance_SetParameterByID(
                soundRef,
                parameterDescription.id,
                value,
                false
            );
            return;
        }
        index = this.findToStart(soundRef);
        if (index == -1) {
            return;
        }
        ParameterValue parameterValue = parameterValuePool.alloc();
        parameterValue.eventInstance = soundRef;
        parameterValue.parameterDescription = parameterDescription;
        parameterValue.value = value;
        this.parameterValues.add(parameterValue);
    }

    @Override
    public void setParameterValueByName(
        long soundRef,
        String parameterName,
        float value
    ) {
        FMOD_STUDIO_PARAMETER_DESCRIPTION parameterDescription =
            FMODManager.instance.getParameterDescription(parameterName);
        this.setParameterValue(soundRef, parameterDescription, value);
    }

    @Override
    public boolean isUsingParameter(long handle, String parameterName) {
        FMOD_STUDIO_PARAMETER_DESCRIPTION parameterDescription =
            FMODManager.instance.getParameterDescription(parameterName);
        if (parameterDescription == null) {
            return false;
        }
        int index = this.findToStart(handle);
        if (index != -1) {
            Sound sound = this.toStart.get(index);
            return (
                sound.clip.eventDescription != null &&
                sound.clip.hasParameter(parameterDescription)
            );
        }
        index = this.findInstance(handle);
        if (index != -1) {
            Sound sound = this.instances.get(index);
            return (
                sound.clip.eventDescription != null &&
                sound.clip.hasParameter(parameterDescription)
            );
        }
        return false;
    }

    @Override
    public void setTimelinePosition(long soundRef, String positionName) {
        if (soundRef == 0L) {
            return;
        }
        int index = this.findToStart(soundRef);
        if (index != -1) {
            Sound sound = this.toStart.get(index);
            sound.setTimelinePosition(positionName);
        }
    }

    private int findParameterValue(
        long soundRef,
        FMOD_STUDIO_PARAMETER_DESCRIPTION parameterDescription
    ) {
        for (int i = 0; i < this.parameterValues.size(); ++i) {
            ParameterValue parameterValue = this.parameterValues.get(i);
            if (
                parameterValue.eventInstance != soundRef ||
                parameterValue.parameterDescription != parameterDescription
            ) continue;
            return i;
        }
        return -1;
    }

    public void clearParameters() {
        this.occlusion.resetToDefault();
        this.parameters.clear();
        parameterValuePool.releaseAll(
            (List<ParameterValue>) this.parameterValues
        );
        this.parameterValues.clear();
    }

    private void startEvent(long eventInstance, GameSoundClip clip) {
        parameterSet.clear();
        ArrayList<FMODParameter> myParameters = this.parameters;
        ArrayList<FMOD_STUDIO_PARAMETER_DESCRIPTION> eventParameters =
            clip.eventDescription.parameters;
        block0: for (int i = 0; i < eventParameters.size(); ++i) {
            FMOD_STUDIO_PARAMETER_DESCRIPTION eventParameter =
                eventParameters.get(i);
            int index = this.findParameterValue(eventInstance, eventParameter);
            if (index != -1) {
                ParameterValue parameterValue = this.parameterValues.get(index);
                javafmod.FMOD_Studio_EventInstance_SetParameterByID(
                    eventInstance,
                    eventParameter.id,
                    parameterValue.value,
                    false
                );
                parameterSet.set(eventParameter.globalIndex, true);
                continue;
            }
            if (eventParameter == this.occlusion.getParameterDescription()) {
                this.occlusion.startEventInstance(eventInstance);
                parameterSet.set(eventParameter.globalIndex, true);
                continue;
            }
            for (int j = 0; j < myParameters.size(); ++j) {
                FMODParameter fmodParameter = myParameters.get(j);
                if (
                    fmodParameter.getParameterDescription() != eventParameter
                ) continue;
                fmodParameter.startEventInstance(eventInstance);
                parameterSet.set(eventParameter.globalIndex, true);
                continue block0;
            }
        }
        if (this.parameterUpdater != null) {
            this.parameterUpdater.startEvent(eventInstance, clip, parameterSet);
        }
    }

    private void updateEvent(long eventInstance, GameSoundClip clip) {
        if (this.parameterUpdater != null) {
            this.parameterUpdater.updateEvent(eventInstance, clip);
        }
    }

    private void stopEvent(long eventInstance, GameSoundClip clip) {
        parameterSet.clear();
        ArrayList<FMODParameter> myParameters = this.parameters;
        ArrayList<FMOD_STUDIO_PARAMETER_DESCRIPTION> eventParameters =
            clip.eventDescription.parameters;
        block0: for (int i = 0; i < eventParameters.size(); ++i) {
            FMOD_STUDIO_PARAMETER_DESCRIPTION eventParameter =
                eventParameters.get(i);
            int index = this.findParameterValue(eventInstance, eventParameter);
            if (index != -1) {
                ParameterValue parameterValue = this.parameterValues.remove(
                    index
                );
                parameterValuePool.release(parameterValue);
                parameterSet.set(eventParameter.globalIndex, true);
                continue;
            }
            if (eventParameter == this.occlusion.getParameterDescription()) {
                this.occlusion.stopEventInstance(eventInstance);
                parameterSet.set(eventParameter.globalIndex, true);
                continue;
            }
            for (int j = 0; j < myParameters.size(); ++j) {
                FMODParameter fmodParameter = myParameters.get(j);
                if (
                    fmodParameter.getParameterDescription() != eventParameter
                ) continue;
                fmodParameter.stopEventInstance(eventInstance);
                parameterSet.set(eventParameter.globalIndex, true);
                continue block0;
            }
        }
        if (this.parameterUpdater != null) {
            this.parameterUpdater.stopEvent(eventInstance, clip, parameterSet);
        }
    }

    private EventSound allocEventSound() {
        return this.eventSoundPool.isEmpty()
            ? new EventSound(this)
            : this.eventSoundPool.pop();
    }

    private <T> T getSandboxValue(String optionName, T defaultValue) {
        SandboxOptions.SandboxOption opt =
            SandboxOptions.getInstance().getOptionByName(optionName);
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
                castedValue = Float.valueOf(
                    ((Number) objectValue).floatValue()
                );
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

        long sound = javafmod.FMOD_System_CreateSound(
            url,
            FMODManager.FMOD_CREATESTREAM
        );
        DebugLog.log(
            "Internet Radio - FMOD_System_CreateSound returned handle: " + sound
        );
        if (sound == 0L) {
            DebugLog.log("Internet Radio - Failed to create sound for: " + url);
            return 0L;
        }

        long channel = javafmod.FMOD_System_PlaySound(sound, true);
        DebugLog.log(
            "Internet Radio - FMOD_System_PlaySound returned channel: " +
                channel
        );
        if (channel == 0L) {
            DebugLog.log("Internet Radio - Failed to play sound for: " + url);
            return 0L;
        }

        javafmod.FMOD_Channel_SetMode(channel, FMODManager.FMOD_3D);
        DebugLog.log("Internet Radio - Set channel mode to FMOD_3D");

        javafmod.FMOD_Channel_Set3DAttributes(
            channel,
            this.x,
            this.y,
            this.z * 3.0f,
            0.0f,
            0.0f,
            0.0f
        );
        DebugLog.log(
            "Internet Radio - Set 3D attributes: x=" +
                this.x +
                " y=" +
                this.y +
                " z=" +
                this.z
        );

        Float minDistance = this.getSandboxValue(
            "InternetRadio.MinDistance",
            1.0f
        );
        Float maxDistance = this.getSandboxValue(
            "InternetRadio.MaxDistance",
            30.0f
        );
        DebugLog.log(
            "Internet Radio - Loaded sandbox distances: min=" +
                minDistance +
                " max=" +
                maxDistance
        );

        javafmod.FMOD_Channel_Set3DMinMaxDistance(
            channel,
            minDistance,
            maxDistance
        );
        DebugLog.log(
            "Internet Radio - Applied 3DMinMaxDistance: min=" +
                minDistance +
                " max=" +
                maxDistance
        );

        javafmod.FMOD_Channel_SetVolume(channel, 1.0f);
        DebugLog.log("Internet Radio - Set volume to 1.0");

        GameSound dummyGameSound = new GameSound();
        dummyGameSound.name = "StreamSound";
        dummyGameSound.loop = true;
        dummyGameSound.maxInstancesPerEmitter = 1;
        DebugLog.log(
            "Internet Radio - Created dummy GameSound: " + dummyGameSound.name
        );

        GameSoundClip dummyClip = new GameSoundClip(dummyGameSound);
        dummyClip.file = url;
        dummyClip.distanceMin = minDistance;
        dummyClip.distanceMax = maxDistance;
        dummyGameSound.clips.add(dummyClip);
        DebugLog.log(
            "Internet Radio - Created dummy GameSoundClip with file: " + url
        );
        DebugLog.log(
            "Internet Radio - DummyClip distances set: min=" +
                dummyClip.distanceMin +
                " max=" +
                dummyClip.distanceMax
        );

        FileSound fileSound = this.allocFileSound();
        fileSound.clip = dummyClip;
        fileSound.name = dummyGameSound.getName();
        fileSound.sound = sound;
        fileSound.channel = channel;
        fileSound.parent = parent;
        fileSound.volume = 1.0f;
        fileSound.setVolume = 1.0f;
        fileSound.is3d = (byte) 1;
        DebugLog.log(
            "Internet Radio - Allocated FileSound with channel=" +
                channel +
                " sound=" +
                sound
        );

        this.toStart.add(fileSound);
        DebugLog.log(
            "Internet Radio - Added FileSound to ToStart list, size now=" +
                this.toStart.size()
        );

        long reference = fileSound.getRef();
        DebugLog.log("Internet Radio - Returning reference: " + reference);

        return reference;
    }

    private void releaseEventSound(EventSound sound) {
        assert (!this.eventSoundPool.contains(sound));
        this.eventSoundPool.push(sound);
    }

    private FileSound allocFileSound() {
        return this.fileSoundPool.isEmpty()
            ? new FileSound(this)
            : this.fileSoundPool.pop();
    }

    private void releaseFileSound(FileSound sound) {
        assert (!this.fileSoundPool.contains(sound));
        this.fileSoundPool.push(sound);
    }

    private Sound addSound(GameSoundClip clip, float volume, IsoObject parent) {
        if (clip == null) {
            DebugLog.log("null sound passed to SoundEmitter.playSoundImpl");
            return null;
        }
        if (clip.gameSound.maxInstancesPerEmitter > 0) {
            this.limitSound(
                clip.gameSound,
                clip.gameSound.maxInstancesPerEmitter - 1
            );
        }
        if (clip.event != null && !clip.event.isEmpty()) {
            long eventInstance;
            IsoPlayer isoPlayer;
            IsoObject isoObject;
            if (clip.eventDescription == null) {
                return null;
            }
            FMOD_STUDIO_EVENT_DESCRIPTION eventDescription =
                clip.eventDescription;
            if (
                clip.eventDescriptionMp != null &&
                (isoObject = this.parent) instanceof IsoPlayer &&
                !(isoPlayer = (IsoPlayer) isoObject).isLocalPlayer()
            ) {
                eventDescription = clip.eventDescriptionMp;
            }
            if (
                (eventInstance =
                        javafmod.FMOD_Studio_System_CreateEventInstance(
                            eventDescription.address
                        )) <
                0L
            ) {
                return null;
            }
            if (clip.hasMinDistance()) {
                javafmodJNI.FMOD_Studio_EventInstance_SetProperty(
                    eventInstance,
                    FMOD_STUDIO_EVENT_PROPERTY.FMOD_STUDIO_EVENT_PROPERTY_MINIMUM_DISTANCE.ordinal(),
                    clip.getMinDistance()
                );
            }
            if (clip.hasMaxDistance()) {
                javafmodJNI.FMOD_Studio_EventInstance_SetProperty(
                    eventInstance,
                    FMOD_STUDIO_EVENT_PROPERTY.FMOD_STUDIO_EVENT_PROPERTY_MAXIMUM_DISTANCE.ordinal(),
                    clip.getMaxDistance()
                );
            }
            EventSound s = this.allocEventSound();
            s.clip = clip;
            s.name = clip.gameSound.getName();
            s.eventInstance = eventInstance;
            s.eventInstanceStopped = 0L;
            s.volume = volume;
            s.parent = parent;
            s.setVolume = 1.0f;
            s.setZ = 0.0f;
            s.setY = 0.0f;
            s.setX = 0.0f;
            this.toStart.add(s);
            return s;
        }
        if (clip.file != null && !clip.file.isEmpty()) {
            long sound = FMODManager.instance.loadSound(clip.file);
            if (sound == 0L) {
                return null;
            }
            long channel = javafmod.FMOD_System_PlaySound(sound, true);
            javafmod.FMOD_Channel_SetVolume(channel, 0.0f);
            javafmod.FMOD_Channel_SetPriority(channel, 9 - clip.priority);
            javafmod.FMOD_Channel_SetChannelGroup(
                channel,
                FMODManager.instance.channelGroupInGameNonBankSounds
            );
            if (
                clip.distanceMax == 0.0f || (this.x == 0.0f && this.y == 0.0f)
            ) {
                javafmod.FMOD_Channel_SetMode(channel, 8L);
            }
            FileSound s = this.allocFileSound();
            s.clip = clip;
            s.name = clip.gameSound.getName();
            s.sound = sound;
            s.pitch = clip.pitch;
            s.channel = channel;
            s.parent = parent;
            s.volume = volume;
            s.setVolume = 1.0f;
            s.setZ = 0.0f;
            s.setY = 0.0f;
            s.setX = 0.0f;
            s.is3d = (byte) -1;
            s.ambient = false;
            this.toStart.add(s);
            return s;
        }
        return null;
    }

    private void sendStopSound(String soundName, boolean triggerCue) {
        IsoObject isoObject;
        if (
            GameClient.client &&
            (isoObject = this.parent) instanceof IsoMovingObject
        ) {
            IsoMovingObject isoMovingObject = (IsoMovingObject) isoObject;
            GameClient.instance.StopSound(
                isoMovingObject,
                soundName,
                triggerCue
            );
        }
    }

    public static void update() {
        currentTimeMs = System.currentTimeMillis();
    }

    private abstract static class Sound {

        public final FMODSoundEmitter emitter;
        public GameSoundClip clip;
        public String name;
        public float volume = 1.0f;
        public float pitch = 1.0f;
        public IsoObject parent;
        public float setVolume = 1.0f;
        public float setX;
        public float setY;
        public float setZ;

        public Sound(FMODSoundEmitter emitter) {
            this.emitter = emitter;
        }

        abstract long getRef();

        abstract void stop(boolean var1, boolean var2);

        abstract void set3D(boolean var1);

        abstract void release(boolean var1);

        abstract boolean tick(boolean var1);

        abstract boolean tickWhileStopped();

        public float getVolume() {
            this.clip = this.clip.checkReloaded();
            return this.volume * this.clip.getEffectiveVolume();
        }

        abstract void setParameterValue(
            FMOD_STUDIO_PARAMETER_DESCRIPTION var1,
            float var2
        );

        abstract void setTimelinePosition(String var1);

        abstract void triggerCue();

        abstract boolean isTriggeredCue();

        abstract boolean restart();
    }

    private static final class EventSound extends Sound {

        private long eventInstance;
        private long eventInstanceStopped;
        private boolean triggeredCue;
        private long checkTimeMs;

        public EventSound(FMODSoundEmitter emitter) {
            super(emitter);
        }

        @Override
        public long getRef() {
            return this.eventInstance;
        }

        @Override
        public void stop(boolean bReleaseEvent, boolean bImmediate) {
            if (this.eventInstance == 0L) {
                return;
            }
            this.emitter.stopEvent(this.eventInstance, this.clip);
            javafmod.FMOD_Studio_EventInstance_Stop(
                this.eventInstance,
                bImmediate
            );
            if (bReleaseEvent) {
                javafmod.FMOD_Studio_ReleaseEventInstance(this.eventInstance);
            }
            this.eventInstanceStopped = this.eventInstance;
            this.eventInstance = 0L;
        }

        @Override
        public void set3D(boolean is3D) {}

        @Override
        public void release(boolean bImmediate) {
            this.stop(true, bImmediate);
            this.checkTimeMs = 0L;
            this.triggeredCue = false;
            this.emitter.releaseEventSound(this);
        }

        @Override
        public boolean tick(boolean isStarting) {
            float volume;
            boolean bPositionChanged;
            IsoPlayer player = IsoPlayer.getInstance();
            if (IsoPlayer.numPlayers > 1) {
                player = null;
            }
            if (!isStarting) {
                int state = javafmod.FMOD_Studio_GetPlaybackState(
                    this.eventInstance
                );
                if (
                    state ==
                    FMOD_STUDIO_PLAYBACK_STATE.FMOD_STUDIO_PLAYBACK_STOPPING.index
                ) {
                    return false;
                }
                if (
                    state ==
                    FMOD_STUDIO_PLAYBACK_STATE.FMOD_STUDIO_PLAYBACK_STOPPED.index
                ) {
                    javafmod.FMOD_Studio_ReleaseEventInstance(
                        this.eventInstance
                    );
                    this.emitter.stopEvent(this.eventInstance, this.clip);
                    this.eventInstance = 0L;
                    return true;
                }
                if (
                    this.triggeredCue &&
                    currentTimeMs - this.checkTimeMs > 250L &&
                    state ==
                    FMOD_STUDIO_PLAYBACK_STATE.FMOD_STUDIO_PLAYBACK_SUSTAINING.index
                ) {
                    javafmodJNI.FMOD_Studio_EventInstance_KeyOff(
                        this.eventInstance
                    );
                }
                if (
                    this.triggeredCue &&
                    this.clip.eventDescription.length > 0L &&
                    currentTimeMs - this.checkTimeMs > 1500L
                ) {
                    long position = javafmodJNI.FMOD_Studio_GetTimelinePosition(
                        this.eventInstance
                    );
                    if (position > this.clip.eventDescription.length + 1000L) {
                        javafmod.FMOD_Studio_EventInstance_Stop(
                            this.eventInstance,
                            false
                        );
                    }
                    this.checkTimeMs = currentTimeMs;
                }
            }
            boolean bl = bPositionChanged =
                Float.compare(this.emitter.x, this.setX) != 0 ||
                Float.compare(this.emitter.y, this.setY) != 0 ||
                Float.compare(this.emitter.z, this.setZ) != 0;
            if (bPositionChanged) {
                this.setX = this.emitter.x;
                this.setY = this.emitter.y;
                this.setZ = this.emitter.z;
                javafmod.FMOD_Studio_EventInstance3D(
                    this.eventInstance,
                    this.emitter.x,
                    this.emitter.y,
                    this.emitter.z * 3.0f
                );
            }
            if (Float.compare(volume = this.getVolume(), this.setVolume) != 0) {
                this.setVolume = volume;
                javafmod.FMOD_Studio_EventInstance_SetVolume(
                    this.eventInstance,
                    volume
                );
            }
            if (isStarting) {
                this.emitter.startEvent(this.eventInstance, this.clip);
                javafmod.FMOD_Studio_StartEvent(this.eventInstance);
            } else {
                this.emitter.updateEvent(this.eventInstance, this.clip);
            }
            return false;
        }

        @Override
        public boolean tickWhileStopped() {
            int state = javafmod.FMOD_Studio_GetPlaybackState(
                this.eventInstanceStopped
            );
            if (
                state ==
                FMOD_STUDIO_PLAYBACK_STATE.FMOD_STUDIO_PLAYBACK_STOPPING.index
            ) {
                boolean bl = true;
            }
            if (
                state ==
                FMOD_STUDIO_PLAYBACK_STATE.FMOD_STUDIO_PLAYBACK_STOPPED.index
            ) {
                javafmod.FMOD_Studio_ReleaseEventInstance(
                    this.eventInstanceStopped
                );
                this.eventInstanceStopped = 0L;
                return true;
            }
            return false;
        }

        @Override
        public void setParameterValue(
            FMOD_STUDIO_PARAMETER_DESCRIPTION parameterDescription,
            float value
        ) {
            if (this.eventInstance == 0L) {
                return;
            }
            javafmod.FMOD_Studio_EventInstance_SetParameterByID(
                this.eventInstance,
                parameterDescription.id,
                value,
                false
            );
        }

        @Override
        public void setTimelinePosition(String positionName) {
            if (
                this.eventInstance == 0L ||
                this.clip == null ||
                this.clip.event == null
            ) {
                return;
            }
            SoundTimelineScript script =
                ScriptManager.instance.getSoundTimeline(this.clip.event);
            if (script == null) {
                return;
            }
            int position = script.getPosition(positionName);
            if (position == -1) {
                return;
            }
            javafmodJNI.FMOD_Studio_EventInstance_SetTimelinePosition(
                this.eventInstance,
                position
            );
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
            this.triggeredCue = true;
            this.checkTimeMs = currentTimeMs;
        }

        @Override
        public boolean isTriggeredCue() {
            return this.triggeredCue;
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

    private static final class FileSound extends Sound {

        private long sound;
        private long channel;
        private byte is3d = (byte) -1;
        boolean ambient;
        private float lx;
        private float ly;
        private float lz;

        private FileSound(FMODSoundEmitter emitter) {
            super(emitter);
        }

        @Override
        public long getRef() {
            return this.channel;
        }

        @Override
        public void stop(boolean bReleaseEvent, boolean bImmediate) {
            if (this.channel == 0L) {
                return;
            }
            javafmod.FMOD_Channel_Stop(this.channel);
            this.sound = 0L;
            this.channel = 0L;
        }

        @Override
        public void set3D(boolean is3D) {
            if (this.is3d != (byte) (is3D ? 1 : 0)) {
                javafmod.FMOD_Channel_SetMode(this.channel, is3D ? 16L : 8L);
                if (is3D) {
                    javafmod.FMOD_Channel_Set3DAttributes(
                        this.channel,
                        this.emitter.x,
                        this.emitter.y,
                        this.emitter.z * 3.0f,
                        0.0f,
                        0.0f,
                        0.0f
                    );
                }
                this.is3d = (byte) (is3D ? 1 : 0);
            }
        }

        @Override
        public void release(boolean bImmediate) {
            this.stop(true, bImmediate);
            this.emitter.releaseFileSound(this);
        }

        @Override
        public boolean tick(boolean isStarting) {
            int presetOffB;
            int presetOffA;
            int preset;
            if (isStarting && this.clip.gameSound.isLooped()) {
                javafmod.FMOD_Channel_SetMode(this.channel, 2L);
            }
            float range = this.clip.distanceMin;
            if (!isStarting && !javafmod.FMOD_Channel_IsPlaying(this.channel)) {
                return true;
            }
            float x = this.emitter.x;
            float y = this.emitter.y;
            float z = this.emitter.z;
            if (!this.clip.gameSound.is3d || (x == 0.0f && y == 0.0f)) {
                if (
                    !((x == 0.0f && y == 0.0f) ||
                        (!isStarting && x == this.lx && y == this.ly) ||
                        this.is3d != 1)
                ) {
                    javafmod.FMOD_Channel_Set3DAttributes(
                        this.channel,
                        x,
                        y,
                        z * 3.0f,
                        0.0f,
                        0.0f,
                        0.0f
                    );
                }
                javafmod.FMOD_Channel_SetVolume(this.channel, this.getVolume());
                javafmod.FMOD_Channel_SetPitch(this.channel, this.pitch);
                if (isStarting) {
                    javafmod.FMOD_Channel_SetPaused(this.channel, false);
                }
                return false;
            }
            this.lx = x;
            this.ly = y;
            this.lz = z;
            javafmod.FMOD_Channel_Set3DAttributes(
                this.channel,
                x,
                y,
                z * 3.0f,
                x - this.lx,
                y - this.ly,
                z * 3.0f - this.lz * 3.0f
            );
            float minDistFromListener = Float.MAX_VALUE;
            for (int i = 0; i < IsoPlayer.numPlayers; ++i) {
                IsoPlayer player = IsoPlayer.players[i];
                if (
                    player == null || player.hasTrait(CharacterTrait.DEAF)
                ) continue;
                float dist = IsoUtils.DistanceTo(
                    x,
                    y,
                    z * 3.0f,
                    player.getX(),
                    player.getY(),
                    player.getZ() * 3.0f
                );
                minDistFromListener = PZMath.min(minDistFromListener, dist);
            }
            float soundSize = 2.0f;
            float level = minDistFromListener >= 2.0f
                ? 1.0f
                : 1.0f - (2.0f - minDistFromListener) / 2.0f;
            javafmodJNI.FMOD_Channel_Set3DLevel(this.channel, level);
            if (IsoPlayer.numPlayers > 1) {
                if (isStarting) {
                    javafmod.FMOD_System_SetReverbDefault(0, 0);
                    javafmod.FMOD_Channel_Set3DMinMaxDistance(
                        this.channel,
                        this.clip.distanceMin,
                        this.clip.distanceMax
                    );
                    javafmod.FMOD_Channel_Set3DOcclusion(
                        this.channel,
                        0.0f,
                        0.0f
                    );
                }
                javafmod.FMOD_Channel_SetVolume(this.channel, this.getVolume());
                if (isStarting) {
                    javafmod.FMOD_Channel_SetPaused(this.channel, false);
                }
                javafmod.FMOD_Channel_SetReverbProperties(
                    this.channel,
                    0,
                    0.0f
                );
                javafmod.FMOD_Channel_SetReverbProperties(
                    this.channel,
                    1,
                    0.0f
                );
                javafmod.FMOD_System_SetReverbDefault(1, 0);
                javafmod.FMOD_Channel_Set3DOcclusion(this.channel, 0.0f, 0.0f);
                return false;
            }
            float maxrange = this.clip.reverbMaxRange;
            float reverb =
                IsoUtils.DistanceManhatten(
                    x,
                    y,
                    IsoPlayer.getInstance().getX(),
                    IsoPlayer.getInstance().getY(),
                    z,
                    IsoPlayer.getInstance().getZ()
                ) /
                maxrange;
            IsoGridSquare current = IsoPlayer.getInstance().getCurrentSquare();
            if (current == null) {
                javafmod.FMOD_Channel_Set3DMinMaxDistance(
                    this.channel,
                    range,
                    this.clip.distanceMax
                );
                javafmod.FMOD_Channel_SetVolume(this.channel, this.getVolume());
                if (isStarting) {
                    javafmod.FMOD_Channel_SetPaused(this.channel, false);
                }
                return false;
            }
            if (current.getRoom() == null) {
                if (!this.ambient) {
                    reverb +=
                        IsoPlayer.getInstance().numNearbyBuildingsRooms / 32.0f;
                }
                if (!this.ambient) {
                    reverb += 0.08f;
                }
            } else {
                float numTiles = current.getRoom().squares.size();
                if (!this.ambient) {
                    reverb += numTiles / 500.0f;
                }
            }
            if (reverb > 1.0f) {
                reverb = 1.0f;
            }
            reverb *= reverb;
            reverb *= reverb;
            reverb *= this.clip.reverbFactor;
            reverb *= 10.0f;
            if (
                IsoPlayer.getInstance().getCurrentSquare().getRoom() == null &&
                reverb < 0.1f
            ) {
                reverb = 0.1f;
            }
            if (!this.ambient) {
                if (current.getRoom() != null) {
                    preset = 0;
                    presetOffA = 1;
                    presetOffB = 2;
                } else {
                    preset = 2;
                    presetOffA = 0;
                    presetOffB = 1;
                }
            } else {
                preset = 2;
                presetOffA = 0;
                presetOffB = 1;
            }
            IsoGridSquare sq = IsoWorld.instance.currentCell.getGridSquare(
                x,
                y,
                z
            );
            if (
                sq != null &&
                sq.getZone() != null &&
                (sq.getZone().getType().equals("Forest") ||
                    sq.getZone().getType().equals("DeepForest"))
            ) {
                preset = 1;
                presetOffA = 0;
                presetOffB = 2;
            }
            javafmod.FMOD_Channel_SetReverbProperties(
                this.channel,
                preset,
                0.0f
            );
            javafmod.FMOD_Channel_SetReverbProperties(
                this.channel,
                presetOffA,
                0.0f
            );
            javafmod.FMOD_Channel_SetReverbProperties(
                this.channel,
                presetOffB,
                0.0f
            );
            javafmod.FMOD_Channel_Set3DMinMaxDistance(
                this.channel,
                range,
                this.clip.distanceMax
            );
            IsoGridSquare sq2 = IsoWorld.instance.currentCell.getGridSquare(
                x,
                y,
                z
            );
            float directOcclude = 0.0f;
            float reverbOcclude = 0.0f;
            if (sq2 != null) {
                if (
                    this.emitter.parent instanceof IsoWindow ||
                    this.emitter.parent instanceof IsoDoor
                ) {
                    IsoRoom r = IsoPlayer.getInstance()
                        .getCurrentSquare()
                        .getRoom();
                    if (r != this.emitter.parent.square.getRoom()) {
                        if (
                            r != null &&
                            r.getBuilding() ==
                            this.emitter.parent.square.getBuilding()
                        ) {
                            directOcclude = 0.33f;
                            reverbOcclude = 0.33f;
                        } else {
                            IsoGridSquare doorother = null;
                            IsoObject isoObject = this.emitter.parent;
                            if (isoObject instanceof IsoDoor) {
                                IsoDoor door = (IsoDoor) isoObject;
                                doorother = door.north
                                    ? IsoWorld.instance.currentCell.getGridSquare(
                                          door.getX(),
                                          door.getY() - 1.0f,
                                          door.getZ()
                                      )
                                    : IsoWorld.instance.currentCell.getGridSquare(
                                          door.getX() - 1.0f,
                                          door.getY(),
                                          door.getZ()
                                      );
                            } else {
                                IsoWindow door =
                                    (IsoWindow) this.emitter.parent;
                                doorother = door.isNorth()
                                    ? IsoWorld.instance.currentCell.getGridSquare(
                                          door.getX(),
                                          door.getY() - 1.0f,
                                          door.getZ()
                                      )
                                    : IsoWorld.instance.currentCell.getGridSquare(
                                          door.getX() - 1.0f,
                                          door.getY(),
                                          door.getZ()
                                      );
                            }
                            if (
                                doorother != null &&
                                ((r = IsoPlayer.getInstance()
                                                .getCurrentSquare()
                                                .getRoom()) !=
                                        null ||
                                    doorother.getRoom() == null)
                            ) {
                                if (
                                    r != null &&
                                    doorother.getRoom() != null &&
                                    r.building == doorother.getBuilding()
                                ) {
                                    if (r != doorother.getRoom()) {
                                        if (r.def.level == doorother.getZ()) {
                                            directOcclude = 0.33f;
                                            reverbOcclude = 0.33f;
                                        } else {
                                            directOcclude = 0.6f;
                                            reverbOcclude = 0.6f;
                                        }
                                    }
                                } else {
                                    directOcclude = 0.33f;
                                    reverbOcclude = 0.33f;
                                }
                            }
                        }
                    }
                } else if (sq2.getRoom() != null) {
                    IsoRoom r = IsoPlayer.getInstance()
                        .getCurrentSquare()
                        .getRoom();
                    if (r == null) {
                        directOcclude = 0.33f;
                        reverbOcclude = 0.23f;
                    } else if (r != sq2.getRoom()) {
                        directOcclude = 0.24f;
                        reverbOcclude = 0.24f;
                    }
                    if (
                        r != null &&
                        sq2.getRoom().getBuilding() != r.getBuilding()
                    ) {
                        directOcclude = 1.0f;
                        reverbOcclude = 0.8f;
                    }
                    if (
                        r != null &&
                        sq2.getRoom().def.level !=
                        IsoPlayer.getInstance().getZi()
                    ) {
                        directOcclude = 0.6f;
                        reverbOcclude = 0.6f;
                    }
                } else {
                    IsoRoom r = IsoPlayer.getInstance()
                        .getCurrentSquare()
                        .getRoom();
                    if (r != null) {
                        directOcclude = 0.79f;
                        reverbOcclude = 0.59f;
                    }
                }
                if (
                    !sq2.isCouldSee(IsoPlayer.getPlayerIndex()) &&
                    sq2 != IsoPlayer.getInstance().getCurrentSquare()
                ) {
                    directOcclude += 0.4f;
                }
            } else {
                if (
                    IsoWorld.instance.metaGrid.getRoomAt(
                        (int) Math.floor(x),
                        PZMath.fastfloor(y),
                        (int) Math.floor(z)
                    ) !=
                    null
                ) {
                    directOcclude = 1.0f;
                    reverbOcclude = 1.0f;
                }
                IsoRoom r = IsoPlayer.getInstance()
                    .getCurrentSquare()
                    .getRoom();
                directOcclude = r != null
                    ? (directOcclude += 0.94f)
                    : (directOcclude += 0.6f);
            }
            if (sq2 != null && IsoPlayer.getInstance().getZi() != sq2.getZ()) {
                directOcclude *= 1.3f;
            }
            if (directOcclude > 0.9f) {
                directOcclude = 0.9f;
            }
            if (reverbOcclude > 0.9f) {
                reverbOcclude = 0.9f;
            }
            if (
                this.emitter.emitterType == EmitterType.Footstep &&
                z > IsoPlayer.getInstance().getZ() &&
                sq2.getBuilding() == IsoPlayer.getInstance().getBuilding()
            ) {
                directOcclude = 0.0f;
                reverbOcclude = 0.0f;
            }
            if ("HouseAlarm".equals(this.name)) {
                directOcclude = 0.0f;
                reverbOcclude = 0.0f;
            }
            javafmod.FMOD_Channel_Set3DOcclusion(
                this.channel,
                directOcclude,
                reverbOcclude
            );
            javafmod.FMOD_Channel_SetVolume(this.channel, this.getVolume());
            javafmod.FMOD_Channel_SetPitch(this.channel, this.pitch);
            if (isStarting) {
                javafmod.FMOD_Channel_SetPaused(this.channel, false);
            }
            this.lx = x;
            this.ly = y;
            this.lz = z;
            return false;
        }

        @Override
        public boolean tickWhileStopped() {
            return true;
        }

        @Override
        void setParameterValue(
            FMOD_STUDIO_PARAMETER_DESCRIPTION parameterDescription,
            float value
        ) {}

        @Override
        void setTimelinePosition(String positionName) {}

        @Override
        void triggerCue() {}

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

        private ParameterValue() {}
    }
}

package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.network.CampaignConversationViewPayload;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

final class CampaignConversationMedia {
    private String heard = "";
    private SoundInstance voice;
    private boolean voicedLine;

    void update(UUID session, CampaignConversationViewPayload.Line line) {
        if (session == null || line == null) {
            stop();
            return;
        }
        String key = session + "/" + line.key() + "/" + line.voice();
        if (key.equals(heard)) return;
        stop();
        heard = key;
        if (line.voice() == null) return;
        var manager = Minecraft.getInstance().getSoundManager();
        if (manager.getSoundEvent(line.voice()) == null) return;
        voicedLine = true;
        voice = new SimpleSoundInstance(line.voice(), SoundSource.VOICE, 1.0F, 1.0F, RandomSource.create(),
                false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        manager.play(voice);
    }

    boolean voicedLine() { return voicedLine; }

    void stop() {
        if (voice != null) Minecraft.getInstance().getSoundManager().stop(voice);
        voice = null;
        voicedLine = false;
    }
}

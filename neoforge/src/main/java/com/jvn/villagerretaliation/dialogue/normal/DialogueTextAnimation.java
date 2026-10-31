package com.jvn.villagerretaliation.dialogue.normal;

import com.google.gson.JsonObject;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record DialogueTextAnimation(float strength, float speed) {
    public static final DialogueTextAnimation DEFAULT = new DialogueTextAnimation(1, 1);

    public DialogueTextAnimation {
        if (!Float.isFinite(strength) || strength < 0 || strength > 4
                || !Float.isFinite(speed) || speed < 0 || speed > 10)
            throw new IllegalArgumentException("Invalid dialogue animation strength or speed");
    }

    public static DialogueTextAnimation read(JsonObject value) {
        var motion = com.jvn.toucanlib.text.ToucanTextMotion.read(value);
        return new DialogueTextAnimation(motion.strength(), motion.speed());
    }

    public static JsonObject schema() {
        return com.jvn.toucanlib.text.ToucanTextMotion.schema();
    }

    public void write(RegistryFriendlyByteBuf buffer) { buffer.writeFloat(strength); buffer.writeFloat(speed); }
    public static DialogueTextAnimation read(RegistryFriendlyByteBuf buffer) { return new DialogueTextAnimation(buffer.readFloat(), buffer.readFloat()); }
}

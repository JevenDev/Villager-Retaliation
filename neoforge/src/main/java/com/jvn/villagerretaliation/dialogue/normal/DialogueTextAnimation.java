package com.jvn.villagerretaliation.dialogue.normal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record DialogueTextAnimation(float strength, float speed) {
    public static final DialogueTextAnimation DEFAULT = new DialogueTextAnimation(1, 1);

    public DialogueTextAnimation {
        if (!Float.isFinite(strength) || strength < 0 || strength > 4
                || !Float.isFinite(speed) || speed < 0 || speed > 10)
            throw new IllegalArgumentException("Invalid dialogue animation strength or speed");
    }

    public static DialogueTextAnimation read(JsonObject value) {
        var issues = QuestJsonSchema.validate(value, schema());
        if (!issues.isEmpty()) throw new IllegalArgumentException("Invalid dialogue animation: " + issues);
        return new DialogueTextAnimation(value.has("strength") ? value.get("strength").getAsFloat() : 1,
                value.has("speed") ? value.get("speed").getAsFloat() : 1);
    }

    public static JsonObject schema() {
        return JsonParser.parseString("""
                {"type":"object","additionalProperties":false,"properties":{
                  "strength":{"type":"number","minimum":0,"maximum":4},
                  "speed":{"type":"number","minimum":0,"maximum":10}
                }}
                """).getAsJsonObject();
    }

    public void write(RegistryFriendlyByteBuf buffer) { buffer.writeFloat(strength); buffer.writeFloat(speed); }
    public static DialogueTextAnimation read(RegistryFriendlyByteBuf buffer) { return new DialogueTextAnimation(buffer.readFloat(), buffer.readFloat()); }
}

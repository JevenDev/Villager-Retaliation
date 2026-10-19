package com.jvn.villagerretaliation.progress;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public final class ProgressConditionChecks {
    private ProgressConditionChecks() { }

    public static int run() {
        int count = 0;
        for (String json : List.of(
                """
                {"type":"player_stat","category":"mined","target":"minecraft:stone","min":0,"max":0}""",
                """
                {"type":"event_history","event":"villagerretaliation:player_died","min_age_ticks":0,"max_age_ticks":24000}""",
                """
                {"type":"personal_trade","metric":"items_paid","item":"minecraft:emerald"}""",
                """
                {"type":"settlement_need","need":"housing"}""",
                """
                {"type":"settlement_contribution","metric":"food","min":64}""")) {
            check(!(parse(json) instanceof DialogueCondition.Invalid), "valid progress condition parses");
            count++;
        }
        for (String json : List.of(
                """
                {"type":"player_stat","category":"unknown","target":"minecraft:stone"}""",
                """
                {"type":"player_stat","category":"mined","target":"minecraft:stone","min":5,"max":4}""",
                """
                {"type":"player_stat","category":"mined","target":"minecraft:stone","min":2147483648}""",
                """
                {"type":"event_history","event":"test:event","min_age_ticks":6,"max_age_ticks":5}""",
                """
                {"type":"event_history","event":"test:event","since":"first"}""",
                """
                {"type":"event_history","event":"test:event","min":-1}""",
                """
                {"type":"personal_trade","metric":"trades","item":"minecraft:emerald"}""",
                """
                {"type":"settlement_need","need":"happiness"}""",
                """
                {"type":"settlement_contribution","metric":"construction","item":"minecraft:stone"}""")) {
            check(parse(json) instanceof DialogueCondition.Invalid, "invalid progress condition fails closed");
            count++;
        }
        var data = new EventHistorySavedData();
        var actor = java.util.UUID.randomUUID();
        var event = ResourceLocation.parse("test:event");
        data.add("world", null, actor, event, "", 2, 64, 12);
        data.add("world", null, actor, event, "", 3, 16, 27);
        var saved = data.save(new net.minecraft.nbt.CompoundTag(), null);
        var restored = EventHistorySavedData.load(saved, null).save(new net.minecraft.nbt.CompoundTag(), null);
        check(saved.equals(restored), "aggregate history round-trips without losing first or last occurrence");
        var entry = restored.getList("Entries", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
        check(entry.getLong("Count") == 5 && entry.getLong("Amount") == 80 && entry.getLong("First") == 12 && entry.getLong("Last") == 27,
                "repeated events preserve totals and both timestamps");
        var saturated = new EventHistorySavedData.Summary(Long.MAX_VALUE, Long.MAX_VALUE, 0, 1)
                .merge(new EventHistorySavedData.Summary(1, 1, 2, 3));
        check(saturated.count() == Long.MAX_VALUE && saturated.amount() == Long.MAX_VALUE, "overflow cannot turn history negative");
        return count + 3;
    }

    private static DialogueCondition parse(String json) {
        return DialogueCondition.readObjects(ResourceLocation.parse("test:progress"), "checks",
                List.of(JsonParser.parseString(json)), null).getFirst();
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}

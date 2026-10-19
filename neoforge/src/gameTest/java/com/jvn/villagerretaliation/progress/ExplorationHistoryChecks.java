package com.jvn.villagerretaliation.progress;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class ExplorationHistoryChecks {
    private ExplorationHistoryChecks() { }

    public static int run() {
        int count = 0;
        for (String json : List.of(
                """
                {"type":"player_structure_visited","structures":["minecraft:monument"],"min":2,"max":3,"since":"first","min_age_ticks":10}
                """,
                """
                {"type":"player_settlement_visited","settlement":"any","min":0,"max":0}
                """,
                """
                {"type":"player_settlement_visited","settlement":"id","id":"12345678-1234-1234-1234-123456789abc"}
                """,
                """
                {"type":"player_destination_count","destination":"structures","structures":["minecraft:monument"],"dimension":"minecraft:overworld"}
                """,
                """
                {"type":"player_destination_count","destination":"structure_types"}
                """,
                """
                {"type":"player_destination_count","destination":"settlements"}
                """,
                """
                {"type":"player_item_possessed","items":["modded:artifact","minecraft:diamond"],"all":false}
                """)) {
            check(!(parse(json) instanceof DialogueCondition.Invalid), "valid exploration checks parse"); count++;
        }
        for (String json : List.of(
                """
                {"type":"player_structure_visited","structures":[]}
                """,
                """
                {"type":"player_structure_visited","structures":["BAD ID"]}
                """,
                """
                {"type":"player_structure_visited","structures":["minecraft:monument"],"min":4,"max":3}
                """,
                """
                {"type":"player_settlement_visited","settlement":"id"}
                """,
                """
                {"type":"player_settlement_visited","settlement":"id","id":"not-a-uuid"}
                """,
                """
                {"type":"player_settlement_visited","settlement":"any","id":"12345678-1234-1234-1234-123456789abc"}
                """,
                """
                {"type":"player_destination_count","destination":"settlements","structures":["minecraft:monument"]}
                """,
                """
                {"type":"player_destination_count","destination":"structures","min":-1}
                """,
                """
                {"type":"player_structure_visited","structures":["minecraft:monument"],"min_age_ticks":5,"max_age_ticks":4}
                """,
                """
                {"type":"player_item_possessed","items":["minecraft:diamond"],"min":2}
                """)) {
            check(parse(json) instanceof DialogueCondition.Invalid, "invalid exploration checks fail closed"); count++;
        }
        var player = UUID.randomUUID();
        var dimension = ResourceLocation.parse("minecraft:overworld");
        var first = new ExplorationHistorySavedData.StructureDestination(dimension, ResourceLocation.parse("minecraft:monument"), 1);
        var second = new ExplorationHistorySavedData.StructureDestination(dimension, first.structure(), 2);
        var data = new ExplorationHistorySavedData();
        check(data.observeStructures(player, dimension, Set.of(first), Set.of(), 10).size() == 1, "entry is recorded once");
        check(data.observeStructures(player, dimension, Set.of(first), Set.of(), 20).isEmpty(), "staying inside is not another visit");
        data.observeStructures(player, dimension, Set.of(), Set.of(first), 25);
        check(data.observeStructures(player, dimension, Set.of(first), Set.of(), 30).isEmpty(), "incomplete loaded data cannot invent an exit");
        data.observeStructures(player, dimension, Set.of(), Set.of(), 35);
        data.observeStructures(player, dimension, Set.of(first, second), Set.of(), 40);
        check(data.structures(player).size() == 2 && data.structures(player).get(first).count() == 2, "revisits and distinct structures remain separate");
        var restored = ExplorationHistorySavedData.load(data.save(new CompoundTag(), null), null);
        check(restored.structures(player).equals(data.structures(player)), "all visit counts and timestamps survive reload");
        check(restored.observeStructures(player, dimension, Set.of(first, second), Set.of(), 50).isEmpty(), "reconnecting inside does not add a visit");
        var item = ResourceLocation.parse("modded:artifact");
        check(restored.possess(player, item, 60) && !restored.possess(player, item, 70), "possessions are distinct item types");
        var afterLoss = ExplorationHistorySavedData.load(restored.save(new CompoundTag(), null), null);
        check(afterLoss.possessed(player).contains(item) && afterLoss.possessed(UUID.randomUUID()).isEmpty(), "discovery survives loss without leaking to other players");
        var otherDimension = ResourceLocation.parse("minecraft:the_nether");
        var elsewhere = new ExplorationHistorySavedData.StructureDestination(otherDimension, first.structure(), first.startChunk());
        restored.observeStructures(player, otherDimension, Set.of(elsewhere), Set.of(), 80);
        check(restored.structures(player).size() == 3, "matching start chunks in different dimensions identify different destinations");
        check(restored.observeStructures(player, dimension, Set.of(first), Set.of(elsewhere), 90).size() == 1, "dimension changes clear incompatible active destinations");
        check(ExplorationHistorySavedData.load(new CompoundTag(), null).structures(player).isEmpty(), "missing history loads as an empty old save");
        return count + 11;
    }

    private static DialogueCondition parse(String json) {
        return DialogueCondition.readObjects(ResourceLocation.parse("test:exploration"), "checks", List.of(JsonParser.parseString(json)), null).getFirst();
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}

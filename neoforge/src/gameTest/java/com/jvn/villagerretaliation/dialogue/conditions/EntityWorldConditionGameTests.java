package com.jvn.villagerretaliation.dialogue.conditions;

import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.EntityDialogueContext;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("entity_narrative_boundary")
@PrefixGameTestTemplate(false)
public final class EntityWorldConditionGameTests {
    private EntityWorldConditionGameTests() { }

    @GameTest(template = "empty")
    public static void commonConditionsUseMobAndPlayerInputsWithoutVillagerState(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var cow = helper.spawn(EntityType.COW, 1, 1, 1);
        cow.setBaby(true);
        cow.setNoAi(true);
        var pig = helper.spawn(EntityType.PIG, 2, 1, 1);
        pig.setNoAi(true);
        var context = new EntityDialogueContext(level, player, cow, "en_us", "");
        var biome = level.getBiome(cow.blockPosition()).unwrapKey().orElseThrow().location();
        helper.assertTrue(DialogueCondition.matchesAll(context, List.of(
                new DialogueCondition.Biome(Set.of(biome), Set.of()),
                new DialogueCondition.NearbyEntity(Set.of(ResourceLocation.parse("minecraft:pig")), Set.of(), 3, 1, null, false),
                new DialogueCondition.VillagerAge(true, false),
                new DialogueCondition.PlayerItem(com.jvn.villagerretaliation.util.VillagerPlayerItemCondition.empty()),
                new DialogueCondition.Advancement(Set.of(), true),
                new DialogueCondition.Not(new DialogueCondition.Scoreboard("test_missing_objective", null, null)),
                new DialogueCondition.Weather(EnumSet.allOf(DialogueContext.WeatherState.class)),
                new DialogueCondition.Time(EnumSet.allOf(DialogueContext.TimeOfDay.class)))),
                "entity contexts reuse the shared world and player predicates");
        helper.assertFalse(DialogueCondition.matchesAll(context, List.of(new DialogueCondition.Biome(
                Set.of(ResourceLocation.parse("test:missing_biome")), Set.of()))), "unmatched world predicates remain unmet");
        helper.succeed();
    }
}

package com.jvn.villagerretaliation.quest.pool;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class QuestPoolGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private QuestPoolGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questPoolSelectionIsDeterministicAndBounded(GameTestHelper helper) {
        Collection<QuestDefinition> quests = VillagerQuestResources.quests(helper.getLevel().getServer());
        QuestPoolDefinition pool = new QuestPoolDefinition(
                ResourceLocation.fromNamespaceAndPath("test", "commissions"),
                true,
                QuestPoolDefinition.Scope.PLAYER,
                24_000L,
                3,
                0,
                1,
                41L,
                Set.of(),
                Set.of("pool.quest_board"),
                Set.of(),
                Set.of(),
                Set.of(),
                Map.of());
        Set<ResourceLocation> first = QuestPoolSelector.select(pool, quests, "player", 4L);
        Set<ResourceLocation> repeated = QuestPoolSelector.select(pool, quests, "player", 4L);
        helper.assertValueEqual(repeated, first, "same pool epoch must select the same quests");
        helper.assertTrue(first.size() <= 3, "pool must respect max_offers");
        helper.assertTrue(first.stream().allMatch(id -> VillagerQuestResources.quest(
                        helper.getLevel().getServer(), id).orElseThrow().tags().contains("pool.quest_board")),
                "pool must only select matching quest tags");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questPoolParserSupportsDurationAndSelectors(GameTestHelper helper) {
        QuestPoolDefinition pool = QuestPoolResources.parse(
                ResourceLocation.fromNamespaceAndPath("test", "quest_pools/daily.json"),
                JsonParser.parseString("""
                        {
                          "schema": "villagerretaliation:quest_pool/v1",
                          "id": "test:daily",
                          "scope": "village",
                          "refresh_days": 2,
                          "max_offers": 4,
                          "any_tags": ["pool.daily"],
                          "exclude_tags": ["difficulty.extreme"],
                          "weights": {"test:special": 5, "test:disabled": 0}
                        }
                        """).getAsJsonObject());
        helper.assertTrue(pool != null, "valid pool should parse");
        helper.assertValueEqual(pool.scope(), QuestPoolDefinition.Scope.VILLAGE, "scope");
        helper.assertValueEqual(pool.refreshTicks(), 48_000L, "refresh duration");
        helper.assertValueEqual(pool.maxOffers(), 4, "max offers");
        helper.assertValueEqual(pool.weights().get(ResourceLocation.fromNamespaceAndPath("test", "special")), 5, "weight");
        helper.assertValueEqual(pool.weights().get(ResourceLocation.fromNamespaceAndPath("test", "disabled")), 0,
                "zero weight disables a pool entry");
        helper.succeed();
    }
}

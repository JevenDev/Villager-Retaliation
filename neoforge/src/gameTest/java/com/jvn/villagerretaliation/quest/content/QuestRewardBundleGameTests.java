package com.jvn.villagerretaliation.quest.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.quest.content.reward.BundledQuestReward;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardCatalog;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardRegistryContext;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardResolver;
import com.jvn.villagerretaliation.quest.tracking.QuestRewardPreviewResources;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class QuestRewardBundleGameTests {
    private static final String NS = "villagerretaliation";
    private static final ResourceLocation PROBE_ID = id("villagerretaliation:quest/reward_probe");
    private static final ResourceLocation EXTERNAL_ID = id("villagerretaliation:test/external_reward");

    private QuestRewardBundleGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "quest_reward_bundle")
    public static void wrapperUsesDirectCodecGenericAndEmptyParameterContract(GameTestHelper helper) {
        BundledQuestReward.ParseResult valid = BundledQuestReward.parse(
                wrapper(PROBE_ID, table("minecraft:generic", "minecraft:stick")),
                QuestRewardRegistryContext.create(helper.getLevel().getServer()));
        helper.assertTrue(valid.valid(), "valid generic reward rejected: " + valid.errors());
        helper.assertValueEqual(valid.reward().table().getLootTableId(), PROBE_ID, "stable table ID not assigned");
        helper.assertTrue(valid.reward().table().isFrozen(), "parsed reward table not frozen");

        BundledQuestReward.ParseResult questType = BundledQuestReward.parse(
                wrapper(PROBE_ID, object("{\"type\":\"minecraft:quest\",\"pools\":[]}")),
                QuestRewardRegistryContext.create(helper.getLevel().getServer()));
        helper.assertTrue(!questType.valid() && diagnostic(questType, "minecraft:quest"),
                "minecraft:quest did not produce a clear codec diagnostic: " + questType.errors());

        BundledQuestReward.ParseResult wrongContract = BundledQuestReward.parse(
                wrapper(PROBE_ID, object("{\"type\":\"minecraft:empty\",\"pools\":[]}")),
                QuestRewardRegistryContext.create(helper.getLevel().getServer()));
        helper.assertTrue(!wrongContract.valid() && diagnostic(wrongContract, "minecraft:generic"),
                "non-generic reward contract accepted");

        JsonObject unavailable = object("""
                {"type":"minecraft:generic","pools":[{"rolls":1,"entries":[{
                  "type":"minecraft:item","name":"minecraft:stick","conditions":[{
                    "condition":"minecraft:entity_properties","entity":"this","predicate":{}
                  }]
                }]}]}
                """);
        BundledQuestReward.ParseResult unavailableResult = BundledQuestReward.parse(
                wrapper(PROBE_ID, unavailable), QuestRewardRegistryContext.create(helper.getLevel().getServer()));
        helper.assertTrue(!unavailableResult.valid() && diagnostic(unavailableResult, "unavailable"),
                "reward logic requiring unavailable parameters accepted: " + unavailableResult.errors());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "quest_reward_bundle")
    public static void bundledPrecedenceAndGlmsAreSharedAndExactOnce(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        QuestContentCatalogs.invalidate();
        QuestContentCatalog base = QuestContentCatalogs.current(server);
        BundledQuestReward.ParseResult parsed = BundledQuestReward.parse(
                wrapper(PROBE_ID, table("minecraft:generic", "minecraft:stick")),
                QuestRewardRegistryContext.create(helper.getLevel().getServer()));
        helper.assertTrue(parsed.valid(), "probe reward failed to compile: " + parsed.errors());
        QuestContentCatalog installed = new QuestContentCatalog(
                base.generation(),
                base.compiledQuestCatalog(),
                base.dialogueCatalog(),
                base.quests(),
                base.objectiveEventQuestIds(),
                base.factQuestIds(),
                base.memoryEventQuestIds(),
                base.exclusiveGroupQuestIds(),
                base.triggerEventQuestIds(),
                base.scenes(),
                base.encounters(),
                base.pools(),
                base.bundles(),
                base.localization(),
                new QuestRewardCatalog(Map.of(PROBE_ID, parsed.reward())));
        QuestContentCatalogs.installForTests(server, installed, QuestContentCatalogs.loadReport(server));
        try {
            QuestRewardResolver.Resolution resolution = QuestRewardResolver.resolve(server, PROBE_ID);
            helper.assertValueEqual(resolution.source(), QuestRewardResolver.Source.BUNDLED,
                    "external collision won over bundled reward");

            long seed = 0x6e591376L;
            QuestRewardResolver.RollResult execution = QuestRewardResolver.roll(
                    helper.getLevel(), 1.25F, PROBE_ID, RandomSource.create(seed));
            QuestRewardResolver.RollResult preview = QuestRewardResolver.rollPreview(
                    helper.getLevel(), 1.25F, PROBE_ID, RandomSource.create(seed));
            assertStacksEqual(helper, execution.items(), preview.items(), "preview/execution roll path diverged");
            helper.assertValueEqual(count(execution.items(), Items.STICK), 1, "bundled table did not roll");
            helper.assertValueEqual(count(execution.items(), Items.APPLE), 1,
                    "old-ID targeted GLM did not apply exactly once");
            helper.assertValueEqual(count(execution.items(), Items.DIRT), 0,
                    "external registry collision leaked through bundled precedence");

            List<QuestRewardPreviewResources.ItemPreview> staticPreview =
                    QuestRewardPreviewResources.itemPreviews(server, PROBE_ID);
            helper.assertTrue(staticPreview.stream().anyMatch(item -> item.itemId().equals("minecraft:stick"))
                            && staticPreview.stream().noneMatch(item -> item.itemId().equals("minecraft:dirt")),
                    "static preview did not use bundled-first source resolution");

            QuestRewardResolver.RollResult external = QuestRewardResolver.roll(
                    helper.getLevel(), 0.0F, EXTERNAL_ID, RandomSource.create(seed));
            helper.assertValueEqual(external.resolution().source(), QuestRewardResolver.Source.EXTERNAL,
                    "external registry fallback was not used");
            helper.assertValueEqual(count(external.items(), Items.COBBLESTONE), 1,
                    "external registry reward did not roll");

            ResourceLocation missing = id("villagerretaliation:quest/missing_reward");
            QuestRewardResolver.Resolution unresolved = QuestRewardResolver.resolve(server, missing);
            helper.assertTrue(!unresolved.resolved() && unresolved.diagnostic().contains(missing.toString()),
                    "unresolved reward diagnostic omitted the stable ID");
            helper.succeed();
        } finally {
            QuestContentCatalogs.invalidate();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "quest_reward_bundle")
    public static void allMigratedGenericRewardsCompileAndResolveFromBundles(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        QuestContentCatalogs.invalidate();
        QuestRewardCatalog rewards = QuestContentCatalogs.current(server).rewards();
        helper.assertValueEqual(rewards.bundled().size(), 91, "migrated reward ID set changed");

        for (Map.Entry<ResourceLocation, BundledQuestReward> entry : rewards.bundled().entrySet()) {
            ResourceLocation rewardId = entry.getKey();
            BundledQuestReward reward = entry.getValue();
            helper.assertValueEqual(reward.tableJson().get("type").getAsString(), "minecraft:generic",
                    rewardId + " changed loot context type");
            helper.assertValueEqual(reward.table().getLootTableId(), rewardId,
                    rewardId + " lost its stable loot-table ID");
            QuestRewardResolver.Resolution resolution = QuestRewardResolver.resolve(server, rewardId);
            helper.assertTrue(
                    resolution.resolved() && resolution.source() == QuestRewardResolver.Source.BUNDLED,
                    rewardId + " did not resolve from the bundled catalog");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "quest_reward_bundle")
    public static void nestedTablesRemainExternalRegistryReferences(GameTestHelper helper) {
        ResourceLocation ownerReward = id("villagerretaliation:quest/nested_owner");
        BundledQuestReward.ParseResult external = BundledQuestReward.parse(
                wrapper(ownerReward, nestedTable(EXTERNAL_ID)),
                QuestRewardRegistryContext.create(helper.getLevel().getServer()));
        helper.assertTrue(external.valid(), "external registry nested table rejected: " + external.errors());

        String line = "test";
        String slug = "nested_owner";
        String prefix = "villagerretaliation.quest.nested_owner";
        ResourceLocation privateSibling = id("villagerretaliation:quest/private_sibling");
        List<QuestBundleTransactions.RawResource> resources = new ArrayList<>();
        resources.add(raw(0, "low", questPath(line, slug), quest(slug, line, prefix)));
        resources.add(raw(0, "low", localePath(line, slug), object(
                "{\"" + prefix + ".title\":\"Nested Owner\"}")));
        resources.add(raw(0, "low", "quests/" + line + "/" + slug + "/rewards/owner.json",
                wrapper(ownerReward, nestedTable(privateSibling))));
        resources.add(raw(0, "low", "quests/" + line + "/" + slug + "/rewards/sibling.json",
                wrapper(privateSibling, table("minecraft:generic", "minecraft:stick"))));

        QuestBundleTransactions.Result result = QuestBundleTransactions.compile(
                resources,
                QuestBundleTransactions.CompatibilityRules.empty(),
                QuestRewardRegistryContext.create(helper.getLevel().getServer()));
        helper.assertTrue(!result.bundles().containsKey(QuestBundlePath.Owner.quest(NS, line, slug))
                        && result.diagnostics().stream().anyMatch(diagnostic ->
                                diagnostic.message().contains(privateSibling.toString())),
                "private bundled sibling was resolved as a nested registry table");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "quest_reward_bundle")
    public static void invalidRewardPatchRollsBackTheWholeOwnerTransaction(GameTestHelper helper) {
        String line = "test";
        String slug = "atomic_reward";
        String prefix = "villagerretaliation.quest.atomic_reward";
        ResourceLocation rewardId = id("villagerretaliation:quest/atomic_reward");
        List<QuestBundleTransactions.RawResource> resources = new ArrayList<>();
        resources.add(raw(0, "low", questPath(line, slug), quest(slug, line, prefix)));
        resources.add(raw(0, "low", localePath(line, slug), object(
                "{\"" + prefix + ".title\":\"Atomic Reward\"}")));
        resources.add(raw(0, "low", rewardPath(line, slug), wrapper(
                rewardId, table("minecraft:generic", "minecraft:stick"))));
        resources.add(raw(1, "high", rewardPath(line, slug), wrapper(
                rewardId, object("{\"type\":\"minecraft:quest\",\"pools\":[]}"))));

        QuestBundleTransactions.Result result = QuestBundleTransactions.compile(
                resources,
                QuestBundleTransactions.CompatibilityRules.empty(),
                QuestRewardRegistryContext.create(helper.getLevel().getServer()));
        QuestBundleTransactions.EffectiveBundle bundle = result.bundles().get(
                QuestBundlePath.Owner.quest(NS, line, slug));
        helper.assertTrue(bundle != null && bundle.rewards().containsKey(rewardId),
                "invalid high reward rejected the lower valid bundle");
        JsonObject effective = bundle.rewards().get(rewardId).tableJson();
        helper.assertTrue(effective.toString().contains("minecraft:stick")
                        && result.diagnostics().stream().anyMatch(diagnostic ->
                                diagnostic.message().contains("minecraft:quest")),
                "invalid high reward did not roll back atomically with codec diagnostics");
        helper.succeed();
    }

    private static QuestBundleTransactions.RawResource raw(
            int layer, String pack, String path, JsonObject root) {
        return QuestBundleTransactions.RawResource.valid(
                layer, pack, ResourceLocation.fromNamespaceAndPath(NS, path), root);
    }

    private static JsonObject quest(String slug, String line, String prefix) {
        return object("""
                {"schema":"villagerretaliation:quest/v2","id":"villagerretaliation:%s",
                 "localization_prefix":"%s",
                 "metadata":{"questline":"%s","title":{"key":"#title"}}}
                """.formatted(slug, prefix, line));
    }

    private static JsonObject wrapper(ResourceLocation id, JsonObject table) {
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("schema", BundledQuestReward.SCHEMA);
        wrapper.addProperty("id", id.toString());
        wrapper.add("table", table.deepCopy());
        return wrapper;
    }

    private static JsonObject nestedTable(ResourceLocation nested) {
        return object("""
                {"type":"minecraft:generic","pools":[{"rolls":1,"entries":[
                  {"type":"minecraft:loot_table","value":"%s"}
                ]}]}
                """.formatted(nested));
    }

    private static JsonObject table(String type, String item) {
        return object("""
                {"type":"%s","pools":[{"rolls":1,"entries":[
                  {"type":"minecraft:item","name":"%s"}
                ]}]}
                """.formatted(type, item));
    }

    private static boolean diagnostic(BundledQuestReward.ParseResult result, String fragment) {
        return result.errors().stream().anyMatch(error ->
                error.toLowerCase(java.util.Locale.ROOT).contains(fragment.toLowerCase(java.util.Locale.ROOT)));
    }

    private static void assertStacksEqual(
            GameTestHelper helper, List<ItemStack> first, List<ItemStack> second, String message) {
        helper.assertValueEqual(first.size(), second.size(), message + " item count");
        for (int index = 0; index < first.size(); index++) {
            ItemStack left = first.get(index);
            ItemStack right = second.get(index);
            helper.assertTrue(left.getCount() == right.getCount()
                            && ItemStack.isSameItemSameComponents(left, right),
                    message + " at stack " + index + ": " + left + " != " + right);
        }
    }

    private static int count(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static ResourceLocation rewardId(ResourceLocation file) {
        String path = file.getPath();
        return ResourceLocation.fromNamespaceAndPath(
                file.getNamespace(),
                path.substring("loot_table/".length(), path.length() - ".json".length()));
    }

    private static ResourceLocation locationFor(ResourceLocation rewardId) {
        return ResourceLocation.fromNamespaceAndPath(
                rewardId.getNamespace(), "loot_table/" + rewardId.getPath() + ".json");
    }

    private static String questPath(String line, String slug) {
        return "quests/" + line + "/" + slug + "/quest.json";
    }

    private static String localePath(String line, String slug) {
        return "quests/" + line + "/" + slug + "/locales/en_us.json";
    }

    private static String rewardPath(String line, String slug) {
        return "quests/" + line + "/" + slug + "/rewards/reward.json";
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }

    private static JsonObject object(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}

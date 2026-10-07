package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignBindingConditionGameTests {
    private static final ResourceLocation CAMPAIGN = ResourceLocation.parse("campaign_fixture:characters");

    private CampaignBindingConditionGameTests() { }

    @GameTest(template = "empty", batch = "campaign_binding_conditions")
    public static void malformedBindingConditionsFailInBothQuestAndRuntimeParsing(GameTestHelper helper) {
        for (String kind : List.of("character", "location")) {
            var valid = condition(kind);
            helper.assertTrue(QuestV2Parser.parse(CAMPAIGN, quest(valid)).isPresent(), "valid nested binding condition compiles");
            var mutations = kind.equals("character")
                    ? List.of("{\"role\":null}", "{\"state\":\"dead\"}", "{\"state\":true}", "{\"radius\":4}", "{\"campaign\":\"bad name\"}")
                    : List.of("{\"location\":null}", "{\"radius\":0}", "{\"radius\":257}", "{\"radius\":1.5}", "{\"radius\":\"12\"}", "{\"state\":\"near\"}");
            for (String mutation : mutations) {
                var candidate = valid.deepCopy();
                JsonParser.parseString(mutation).getAsJsonObject().entrySet().forEach(entry -> candidate.add(entry.getKey(), entry.getValue()));
                helper.assertTrue(kind.equals("character") ? CampaignCharacterPredicate.read(candidate) == null : CampaignLocationPredicate.read(candidate) == null,
                        "runtime rejects malformed " + kind + " condition: " + mutation);
                helper.assertTrue(QuestV2Parser.parse(CAMPAIGN, quest(candidate)).isEmpty(), "quest parser enforces the same condition contract");
            }
            for (String required : kind.equals("character") ? List.of("campaign", "role", "state") : List.of("campaign", "location")) {
                var candidate = valid.deepCopy(); candidate.remove(required);
                helper.assertTrue(QuestV2Parser.parse(CAMPAIGN, quest(candidate)).isEmpty(), "binding identities must be explicit: " + required);
            }
        }
        for (int radius : List.of(1, 12, 256)) {
            var condition = condition("location"); condition.addProperty("radius", radius);
            helper.assertValueEqual(CampaignLocationPredicate.read(condition).radius(), radius, "bounded integer radius is accepted");
        }
        blocked(helper, new DialogueCondition.CampaignCharacter(new CampaignCharacterPredicate(CAMPAIGN, "mentor", CampaignCharacterPredicate.State.BOUND)), null);
        blocked(helper, new DialogueCondition.CampaignLocation(new CampaignLocationPredicate(CAMPAIGN, "home", 12)), null);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_binding_conditions")
    public static void evaluatingOptionsNeverCastsSpawnsOrSearchesForLocations(GameTestHelper helper) throws IOException {
        for (String kind : List.of("characters", "spawns")) {
            try (var fixture = new CampaignCharacterTestFixture(helper, kind, kind.equals("characters") ? "character_greeting" : "spawn_greeting")) {
                var context = context(helper);
                int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
                for (var state : CampaignCharacterPredicate.State.values()) {
                    var result = character(fixture, state).evaluate(context);
                    helper.assertFalse(result.matches(), "unbound role does not match " + state);
                    helper.assertTrue(result.diagnostic().isBlank(), "unbound is a valid story state");
                }
                helper.assertFalse(location(fixture).evaluate(context).matches(), "unbound location has no position");
                blocked(helper, new DialogueCondition.CampaignLocation(location(fixture)), context);
                helper.assertTrue(CampaignCharacterSavedData.get(helper.getLevel()).bindings().isEmpty(), "condition cannot cast an eligible villager");
                helper.assertTrue(CampaignSpawnSavedData.get(helper.getLevel()).receipts().isEmpty(), "condition cannot reserve or spawn an NPC");
                helper.assertTrue(CampaignLocationSavedData.get(helper.getLevel()).bindings().isEmpty(), "condition cannot search and bind a location");
                helper.assertTrue(CampaignSavedData.get(helper.getLevel()).instances().isEmpty(), "reading options cannot start a campaign instance");
                helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "conditions never force chunks");
                try (var files = Files.list(fixture.disk.root)) { helper.assertTrue(files.findAny().isEmpty(), "read-only conditions write no ledgers"); }
                if (kind.equals("spawns")) {
                    var future = new CompoundTag(); future.putInt("DataVersion", 99);
                    fixture.server.overworld().getDataStorage().set(CampaignSpawnSavedData.DATA_NAME, CampaignSpawnSavedData.load(future, fixture.disk.root));
                    blocked(helper, new DialogueCondition.CampaignCharacter(character(fixture, CampaignCharacterPredicate.State.BOUND)), context);
                }
                context.villager().discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_binding_conditions")
    public static void characterConditionsUseTheSavedIdentityAndItsLiveDownedState(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var context = context(helper);
            var binding = fixture.resolve().binding();
            helper.assertValueEqual(binding.entityId(), context.villager().getUUID(), "fixture casts its speaker");
            helper.assertTrue(character(fixture, CampaignCharacterPredicate.State.SPEAKER).evaluate(context).matches(), "bound character is the speaker");
            var other = helper.spawn(EntityType.VILLAGER, 2, 1, 1);
            var otherContext = VillagerInteractionService.createDialogueContext(helper.getLevel(), context.player(), other);
            helper.assertFalse(character(fixture, CampaignCharacterPredicate.State.SPEAKER).evaluate(otherContext).matches(), "equivalent villager cannot impersonate the role");
            context.villager().setPos(context.villager().getX() + 20, context.villager().getY(), context.villager().getZ());
            fixture.reload();
            helper.assertTrue(character(fixture, CampaignCharacterPredicate.State.AVAILABLE).evaluate(context).matches(), "condition follows the UUID after movement and reload");
            context.villager().hurt(context.villager().damageSources().genericKill(), 1000);
            helper.assertTrue(character(fixture, CampaignCharacterPredicate.State.DOWNED).evaluate(context).matches(), "downed role is explicit");
            helper.assertFalse(character(fixture, CampaignCharacterPredicate.State.AVAILABLE).evaluate(context).matches(), "downed character is unavailable for conversation");
            helper.assertFalse(character(fixture, CampaignCharacterPredicate.State.LOST).evaluate(context).matches(), "downed does not mean permanently lost");
            VillagerDownedService.recover(context.villager());
            helper.assertTrue(character(fixture, CampaignCharacterPredicate.State.AVAILABLE).evaluate(context).matches(), "recovery restores availability");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_binding_conditions")
    public static void unloadedIdentityRemainsBoundWithoutInventingCharacterDeath(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var context = context(helper); fixture.resolve();
            var snapshot = context.villager().saveWithoutId(new CompoundTag());
            context.villager().remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(character(fixture, CampaignCharacterPredicate.State.BOUND).evaluate(context).matches(), "unloaded role remains bound");
            helper.assertFalse(character(fixture, CampaignCharacterPredicate.State.AVAILABLE).evaluate(context).matches(), "unloaded character cannot converse");
            helper.assertFalse(character(fixture, CampaignCharacterPredicate.State.LOST).evaluate(context).matches(), "missing loaded entity is never proof of death");
            var restored = EntityType.VILLAGER.create(helper.getLevel()); restored.load(snapshot);
            helper.assertTrue(helper.getLevel().addFreshEntity(restored), "same UUID returns");
            helper.assertFalse(character(fixture, CampaignCharacterPredicate.State.SPEAKER).evaluate(context).matches(), "stale entity object cannot speak for a recovered UUID");
            var live = VillagerInteractionService.createDialogueContext(helper.getLevel(), context.player(), restored);
            helper.assertTrue(character(fixture, CampaignCharacterPredicate.State.SPEAKER).evaluate(live).matches(), "fresh live speaker resumes the same role");
            restored.discard(); fixture.reload();
            helper.assertTrue(character(fixture, CampaignCharacterPredicate.State.LOST).evaluate(live).matches(), "known permanent removal survives reload");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_binding_conditions")
    public static void missingContentWorldModesAndCorruptBindingsCannotUnlockNegation(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var context = context(helper); fixture.resolve();
            var character = new DialogueCondition.CampaignCharacter(character(fixture, CampaignCharacterPredicate.State.LOST));
            var location = new DialogueCondition.CampaignLocation(location(fixture));
            for (String mode : List.of("sandbox", "legacy")) {
                fixture.mode(mode);
                blocked(helper, character, context); blocked(helper, location, context);
            }
            fixture.mode("campaign");
            CampaignResources.installForTests(fixture.server, new CampaignContentCatalog(fixture.catalog, Map.of(), List.of()));
            blocked(helper, character, context); blocked(helper, location, context);
            CampaignResources.installForTests(fixture.server, fixture.campaigns);
            var data = CampaignCharacterSavedData.get(helper.getLevel());
            var future = data.save(new CompoundTag(), helper.getLevel().registryAccess()); future.putInt("DataVersion", 99);
            fixture.server.overworld().getDataStorage().set(CampaignCharacterSavedData.DATA_NAME, CampaignCharacterSavedData.load(future, fixture.disk.root));
            blocked(helper, character, context);
            helper.assertValueEqual(CampaignCharacterSavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess()), future, "condition preserves dormant character bytes");
            fixture.reload();
            var raw = fixture.raw(); raw.addProperty("revision", 2); fixture.install(raw);
            blocked(helper, character, context); blocked(helper, location, context);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_binding_conditions")
    public static void locationConditionsMeasureTheSavedDestinationAndDimension(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var context = context(helper); fixture.resolve();
            context.player().setPos(fixture.origin.getX() + 0.5, fixture.origin.getY() + 0.5, fixture.origin.getZ() + 0.5);
            helper.assertTrue(location(fixture).evaluate(context).matches(), "player is at the bound destination");
            context.player().setPos(context.player().getX() + 12, context.player().getY(), context.player().getZ());
            helper.assertTrue(location(fixture).evaluate(context).matches(), "authored radius includes its boundary");
            context.player().setPos(context.player().getX() + 0.1, context.player().getY(), context.player().getZ());
            helper.assertFalse(location(fixture).evaluate(context).matches(), "nearby unrelated position does not substitute for the saved destination");
        }
        try (var fixture = fixture(helper)) {
            var context = context(helper);
            var raw = fixture.raw();
            var location = raw.getAsJsonArray("locations").get(0).getAsJsonObject();
            location.addProperty("dimension", "minecraft:the_nether"); location.getAsJsonObject("origin").addProperty("y", 64);
            fixture.install(raw);
            var bound = CampaignLocationService.resolve(fixture.server, fixture.campaignId, "home");
            helper.assertTrue(bound.available(), bound.diagnostic());
            context.player().setPos(bound.binding().position().getX() + 0.5, 64.5, bound.binding().position().getZ() + 0.5);
            helper.assertFalse(location(fixture).evaluate(context).matches(), "matching coordinates in another dimension cannot satisfy the destination");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_binding_conditions")
    public static void pendingOrCorruptOwnedSpawnsStayUnavailableWithoutBeingCompletedByConditions(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignCharacterTestFixture(helper, "spawns", "spawn_greeting")) {
            helper.getLevel().setBlockAndUpdate(fixture.origin.below(), Blocks.STONE.defaultBlockState());
            var result = fixture.resolve(); helper.assertTrue(result.available(), result.diagnostic());
            var player = helper.makeMockServerPlayerInLevel();
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, result.entity());
            var condition = new DialogueCondition.CampaignCharacter(character(fixture, CampaignCharacterPredicate.State.AVAILABLE));
            helper.assertTrue(condition.matches(context), "committed owned character is available");
            var raw = CampaignSpawnSavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess());
            raw.getList("Receipts", Tag.TAG_COMPOUND).getCompound(0).putLong("CommittedAt", -1L);
            fixture.server.overworld().getDataStorage().set(CampaignSpawnSavedData.DATA_NAME, CampaignSpawnSavedData.load(raw, fixture.disk.root));
            blocked(helper, condition, context);
            helper.assertValueEqual(CampaignSpawnSavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess()), raw, "option checks cannot commit a prepared spawn");
            raw.putInt("DataVersion", 99);
            fixture.server.overworld().getDataStorage().set(CampaignSpawnSavedData.DATA_NAME, CampaignSpawnSavedData.load(raw, fixture.disk.root));
            blocked(helper, condition, context);
            helper.assertValueEqual(CampaignSpawnSavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess()), raw, "option checks preserve future receipts");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_binding_conditions")
    public static void registeredBindingConditionsShareQuestAndDialogueEvaluation(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var context = context(helper); fixture.resolve();
            for (String kind : List.of("character", "location")) {
                var raw = quest(condition(kind));
                var conditions = DialogueCondition.readList(CAMPAIGN, "binding fixture", raw);
                helper.assertValueEqual(conditions.size(), 1, "authored nested condition is compiled through the shared registry");
                helper.assertFalse(DialogueCondition.matchesAll(context, conditions), "negated matching binding blocks the authored path");
            }
            blocked(helper, new DialogueCondition.CampaignCharacter(new CampaignCharacterPredicate(CAMPAIGN, "missing", CampaignCharacterPredicate.State.BOUND)), context);
            blocked(helper, new DialogueCondition.CampaignLocation(new CampaignLocationPredicate(CAMPAIGN, "missing", 12)), context);
        }
        helper.succeed();
    }

    private static CampaignCharacterTestFixture fixture(GameTestHelper helper) throws IOException {
        return new CampaignCharacterTestFixture(helper, "characters", "character_greeting");
    }

    private static DialogueContext context(GameTestHelper helper) {
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(villager.getX() + 1, villager.getY(), villager.getZ());
        return VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
    }

    private static CampaignCharacterPredicate character(CampaignCharacterTestFixture fixture, CampaignCharacterPredicate.State state) {
        return new CampaignCharacterPredicate(fixture.campaignId, "mentor", state);
    }

    private static CampaignLocationPredicate location(CampaignCharacterTestFixture fixture) { return new CampaignLocationPredicate(fixture.campaignId, "home", 12); }

    private static void blocked(GameTestHelper helper, DialogueCondition condition, DialogueContext context) {
        helper.assertFalse(condition.matches(context), "unavailable campaign binding does not match");
        helper.assertFalse(new DialogueCondition.Not(condition).matches(context), "unavailable campaign binding cannot unlock negation");
        helper.assertFalse(condition.unavailableReason(context).isBlank(), "binding failure explains its repair path");
    }

    private static JsonObject condition(String kind) {
        return JsonParser.parseString(kind.equals("character")
                ? "{\"type\":\"campaign_character\",\"campaign\":\"campaign_fixture:characters\",\"role\":\"mentor\",\"state\":\"available\"}"
                : "{\"type\":\"campaign_location\",\"campaign\":\"campaign_fixture:characters\",\"location\":\"home\"}").getAsJsonObject();
    }

    private static JsonObject quest(JsonObject condition) {
        var root = JsonParser.parseString("""
                {"schema":"villagerretaliation:quest/v2","id":"campaign_fixture:characters","localization_prefix":"quest.bindings.",
                 "provider":{"type":"villagerretaliation:villager"},"availability":{"conditions":[{"type":"not"}]},
                 "entry_stage":"begin","stages":[{"id":"begin","objectives":[]}]}
                """).getAsJsonObject();
        root.getAsJsonObject("availability").getAsJsonArray("conditions").get(0).getAsJsonObject().add("condition", condition);
        return root;
    }
}

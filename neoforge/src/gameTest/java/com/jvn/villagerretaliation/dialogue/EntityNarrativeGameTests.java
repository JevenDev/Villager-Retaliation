package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import com.jvn.villagerretaliation.dialogue.conditions.ConditionContext;
import com.jvn.villagerretaliation.dialogue.conditions.ConditionEvaluator;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationDefinition.SpeakerKind;
import com.jvn.villagerretaliation.dialogue.normal.DialogueEntryMetadata;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextVariant;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueUsagePolicy;
import com.jvn.villagerretaliation.dialogue.normal.DialogueUsageSavedData;
import com.jvn.villagerretaliation.dialogue.normal.DialogueUsageService;
import com.jvn.villagerretaliation.dialogue.normal.DialoguePlaceholders;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestExecutionContext;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.provider.QuestProviderBinding;
import com.jvn.villagerretaliation.quest.provider.QuestProviderType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("entity_narrative_boundary")
@PrefixGameTestTemplate(false)
public final class EntityNarrativeGameTests {
    private EntityNarrativeGameTests() { }

    @GameTest(template = "empty", batch = "entity_narrative_boundary")
    public static void explicitlyRegisteredActorsOverrideDefaultVillagerAdapters(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var actor = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        try (var factory = NarrativeActors.register(ResourceLocation.parse("minecraft:villager"),
                (level, viewer, speaker) -> new EntityDialogueContext(level, viewer, speaker, "en_us", ""));
                var access = com.jvn.villagerretaliation.dialogue.conversation.ConversationActors.register(ResourceLocation.parse("minecraft:villager"),
                        new com.jvn.villagerretaliation.dialogue.conversation.ConversationActors.Access() {
                            @Override public boolean matches(net.minecraft.server.level.ServerPlayer viewer,
                                    com.jvn.villagerretaliation.dialogue.conversation.ConversationDefinition definition,
                                    net.minecraft.world.entity.LivingEntity speaker) { return false; }
                            @Override public boolean interrupted(net.minecraft.server.level.ServerPlayer viewer,
                                    net.minecraft.world.entity.LivingEntity speaker) { return true; }
                        })) {
            helper.assertTrue(com.jvn.villagerretaliation.dialogue.conversation.ConversationActors.context(player, actor) instanceof EntityDialogueContext,
                    "an explicit entity factory takes precedence over the VR context adapter");
            helper.assertTrue(com.jvn.villagerretaliation.dialogue.conversation.ConversationActors.interrupted(player, actor),
                    "an explicit conversation adapter takes precedence over VR defaults");
        }
        actor.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "entity_narrative_boundary")
    public static void mobDialogueSelectsTextAndResolvesEverySpeakerKind(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var wolf = helper.spawn(EntityType.WOLF, 1, 1, 1);
        wolf.setNoAi(true);
        wolf.setCustomName(Component.literal("Scout"));
        wolf.setHealth(wolf.getMaxHealth() / 2);
        var context = new EntityDialogueContext(level, player, wolf, "en_us", "");
        var injured = new DialogueCondition.Health("speaker", 0, 0.6);
        var unsupported = new DialogueCondition.Not(new DialogueCondition.Reputation(Set.of(), 1, null));
        var usable = new DialogueTextVariant("injured", "Help {speaker}.", "", 1, 1, 1,
                List.of(injured), DialogueEntryMetadata.EMPTY, DialogueUsagePolicy.DEFAULT);
        var unavailable = new DialogueTextVariant("villager_only", "Wrong.", "", 100, 1, 1,
                List.of(unsupported), DialogueEntryMetadata.EMPTY, DialogueUsagePolicy.DEFAULT);
        helper.assertValueEqual(DialogueTextVariant.select(List.of(unavailable, usable), context, List.of())
                .orElseThrow().id(), "injured", "mob health works and unavailable villager state fails closed");
        var node = new DialogueTreeDefinition.Node("start", List.of(), List.of(usable), List.of(),
                List.of(injured), List.of(), true);
        helper.assertTrue(node.matches(context), "tree node conditions accept an entity context");
        helper.assertValueEqual(node.selectText(context).text(), "Help {speaker}.", "tree text uses the shared selector");
        var lines = DialogueExchange.resolve(List.of(
                new DialogueExchange(SpeakerKind.ROLE, "Help {speaker}.", ""),
                new DialogueExchange(SpeakerKind.PLAYER, "I hear {speaker_possessive} call.", ""),
                new DialogueExchange(SpeakerKind.NARRATOR, "{player} helps {speaker}.", "")), context, Map.of());
        helper.assertValueEqual(lines.get(0).speaker(), wolf.getUUID(), "the role resolves to the mob");
        helper.assertValueEqual(lines.get(0).name(), "Scout", "the mob supplies its display name");
        helper.assertValueEqual(lines.get(0).text(), "Help Scout.", "generic speaker placeholders resolve");
        helper.assertValueEqual(lines.get(1).speaker(), player.getUUID(), "player exchanges keep player identity");
        helper.assertValueEqual(lines.get(1).text(), "I hear Scout's call.", "speaker possessives resolve");
        helper.assertTrue(lines.get(2).speaker() == null && lines.get(2).entityId() == -1, "narrators remain detached");
        helper.assertTrue(!DialoguePlaceholders.base(context).containsKey("profession"), "mob contexts invent no villager attributes");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "entity_narrative_boundary")
    public static void usageScopesSeparateParticipantsAndSurviveSaving(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var otherPlayer = helper.makeMockServerPlayerInLevel();
        var first = helper.spawn(EntityType.WOLF, 1, 1, 1);
        var second = helper.spawn(EntityType.COW, 2, 1, 1);
        var context = new EntityDialogueContext(level, player, first, "en_us", "test:group");
        var otherSpeaker = new EntityDialogueContext(level, player, second, "en_us", "test:group");
        var otherListener = new EntityDialogueContext(level, otherPlayer, first, "en_us", "test:group");
        String prefix = UUID.randomUUID().toString();
        for (var scope : DialogueUsagePolicy.Scope.values()) {
            String id = prefix + ":" + scope;
            var policy = new DialogueUsagePolicy(40, 1, true, scope);
            DialogueUsageService.record(context, id, policy);
            helper.assertFalse(DialogueUsageService.available(context, id, policy), "the recorded scope enforces limits");
            boolean separatesSpeakers = scope == DialogueUsagePolicy.Scope.PLAYER_VILLAGER || scope == DialogueUsagePolicy.Scope.VILLAGER;
            boolean separatesListeners = scope == DialogueUsagePolicy.Scope.PLAYER_VILLAGER || scope == DialogueUsagePolicy.Scope.PLAYER;
            helper.assertValueEqual(DialogueUsageService.available(otherSpeaker, id, policy), separatesSpeakers,
                    "speaker identity follows the selected scope");
            helper.assertValueEqual(DialogueUsageService.available(otherListener, id, policy), separatesListeners,
                    "listener identity follows the selected scope");
            helper.assertValueEqual(DialogueUsageService.lastUsedAmong(context, Set.of(id), policy).orElseThrow(), id,
                    "anti-repeat lookup uses the same scope");
        }
        var ledger = DialogueUsageSavedData.get(level);
        var loaded = DialogueUsageSavedData.load(ledger.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        helper.assertValueEqual(loaded.usage("player_villager:" + player.getUUID() + ":" + first.getUUID(),
                prefix + ":" + DialogueUsagePolicy.Scope.PLAYER_VILLAGER).count(), 1, "entity usage survives ledger serialization");
        var cooldown = new DialogueUsagePolicy(40, 0, false, DialogueUsagePolicy.Scope.PLAYER_VILLAGER);
        String cooldownId = prefix + ":cooldown";
        DialogueUsageService.record(context, cooldownId, cooldown);
        helper.assertFalse(DialogueUsageService.available(context, cooldownId, cooldown), "cooldowns work without a maximum use count");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "entity_narrative_boundary")
    public static void playerOnlyDialogueSupportsEveryUsageScope(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        for (DialogueRuntimeContext context : List.of(DialogueContext.forPlayer(player),
                new EntityDialogueContext(level, player, null, null, null))) {
            for (var scope : DialogueUsagePolicy.Scope.values()) {
                String id = UUID.randomUUID().toString();
                var policy = new DialogueUsagePolicy(0, 1, true, scope);
                DialogueUsageService.record(context, id, policy);
                helper.assertFalse(DialogueUsageService.available(context, id, policy), "player-only usage needs no speaker");
            }
        }
        var context = new EntityDialogueContext(level, player, null, null, null);
        helper.assertValueEqual(ConditionEvaluator.evaluate(context.conditions(), null,
                new DialogueCondition.Not(new DialogueCondition.Health("speaker", 0, 1))), ConditionOutcome.UNKNOWN,
                "missing speaker state cannot become a successful negation");
        helper.assertTrue(DialogueCondition.matchesAll(context, List.of(new DialogueCondition.Health("player", 0, 1))),
                "player conditions remain available without a speaker");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "entity_narrative_boundary")
    public static void villagerAdapterPreservesLegacyUsageAndPlaceholders(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        villager.setNoAi(true);
        var context = VillagerInteractionService.createDialogueContext(level, player, villager);
        String legacyId = UUID.randomUUID().toString();
        VillagerInteractionTracker.rememberDialogue(level, villager, player, DialogueRequestType.STORY, legacyId);
        var once = new DialogueUsagePolicy(0, 1, true, DialogueUsagePolicy.Scope.PLAYER_VILLAGER);
        helper.assertFalse(DialogueUsageService.available(context, legacyId, once), "legacy villager history still enforces usage limits");
        String storedId = UUID.randomUUID().toString();
        DialogueUsageSavedData.get(level).remember("player_villager:" + player.getUUID() + ":" + villager.getUUID(),
                storedId, level.getGameTime());
        helper.assertFalse(DialogueUsageService.available(context, storedId, once), "existing ledger keys remain readable");
        var values = DialoguePlaceholders.base(context);
        helper.assertValueEqual(values.get("villager"), villager.getName().getString(), "the original villager token remains available");
        helper.assertValueEqual(values.get("reputation"), Integer.toString(context.reputation()), "reputation stays in the villager adapter");
        helper.assertTrue(values.containsKey("profession") && values.containsKey("speaker"), "legacy and generic tokens coexist");
        helper.assertValueEqual(DialogueTemplates.resolveTemplate("{item}. Bring {item}.", Map.of("item", "an apple")),
                "An apple. Bring an apple.", "shared templates retain sentence capitalization");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "entity_narrative_boundary")
    public static void questConditionsUseLiveAndSavedEntityProviders(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var wolf = helper.spawn(EntityType.WOLF, 1, 1, 1);
        wolf.setNoAi(true);
        var type = provider(ResourceLocation.parse("test:wolf"));
        var binding = QuestProviderBinding.fromEntity(type.id(), wolf, "test:pack");
        var context = QuestExecutionContext.fromLiveProvider(level, player, null, "mob_test", type, binding, wolf);
        helper.assertTrue(context.dialogueContext().isEmpty(), "entity providers need no legacy dialogue context");
        helper.assertTrue(context.hasCapability(QuestExecutionContext.LIVE_PROVIDER)
                && !context.hasCapability(QuestExecutionContext.VILLAGER_PROVIDER), "entity providers do not acquire villager capabilities");
        var conditions = context.conditions(null);
        helper.assertValueEqual(conditions.providerId(), wolf.getUUID(), "conditions retain entity provider identity");
        helper.assertTrue(conditions.liveProvider() == wolf, "conditions retain the live mob");
        helper.assertValueEqual(ConditionEvaluator.evaluate(conditions, null, new DialogueCondition.Health("speaker", 0, 1)),
                ConditionOutcome.MATCHED, "quest conditions evaluate mob health");
        var savedBinding = new QuestProviderBinding(type.id(), wolf.getUUID(), "Scout", level.dimension(),
                wolf.blockPosition(), "test:pack", false);
        var saved = QuestExecutionContext.fromSavedProvider(level, player, null, "saved_mob_test", type, savedBinding);
        var savedConditions = saved.conditions(null);
        helper.assertValueEqual(savedConditions.providerId(), wolf.getUUID(), "saved conditions use the supplied entity binding");
        helper.assertValueEqual(savedConditions.savedProvider().pos(), wolf.blockPosition(), "saved conditions preserve the provider snapshot");
        helper.assertValueEqual(ConditionEvaluator.evaluate(savedConditions, null, new DialogueCondition.Health("speaker", 0, 1)),
                ConditionOutcome.UNKNOWN, "saved providers do not pretend to have live health");
        helper.assertValueEqual(ConditionEvaluator.evaluate(savedConditions, null,
                new DialogueCondition.Dimension(Set.of(level.dimension().location()))), ConditionOutcome.MATCHED,
                "saved providers retain world conditions");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "entity_narrative_boundary")
    public static void questConditionAdapterControlsProviderSpecificState(GameTestHelper helper) {
        var marker = new DialogueCondition.Reputation(Set.of(), 1, null);
        var type = new QuestProviderType() {
            @Override
            public ResourceLocation id() { return ResourceLocation.parse("test:custom_conditions"); }
            @Override
            public boolean matchesOffer(QuestExecutionContext context, QuestDefinition definition) { return true; }
            @Override
            public boolean matchesIssuerLock(QuestExecutionContext context, QuestDefinition definition,
                    VillagerQuestSavedData.QuestProgress progress) { return true; }
            @Override
            public ConditionContext conditions(QuestExecutionContext context, VillagerQuestSavedData.QuestProgress progress) {
                var common = QuestProviderType.super.conditions(context, progress);
                return new ConditionContext(common.playerId(), common.providerId(), common.savedProvider(), common.liveProvider(),
                        common.questRun(), common.capabilities(), (condition, trigger) -> condition == marker
                                ? ConditionOutcome.MATCHED : common.queries().evaluate(condition, trigger));
            }
        };
        var context = QuestExecutionContext.fromSavedProvider(helper.getLevel(), null, null, "custom", type, null);
        helper.assertValueEqual(ConditionEvaluator.evaluate(context.conditions(null), null, marker), ConditionOutcome.MATCHED,
                "execution delegates condition assembly to the selected provider type");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "entity_narrative_boundary")
    public static void liveQuestProvidersRejectMismatchedBindings(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var wolf = helper.spawn(EntityType.WOLF, 1, 1, 1);
        var cow = helper.spawn(EntityType.COW, 2, 1, 1);
        var type = provider(ResourceLocation.parse("test:wolf"));
        var wrongType = QuestProviderBinding.fromEntity(ResourceLocation.parse("test:other"), wolf, "");
        var wrongEntity = QuestProviderBinding.fromEntity(type.id(), cow, "");
        var saved = new QuestProviderBinding(type.id(), wolf.getUUID(), "", level.dimension(), wolf.blockPosition(), "", false);
        for (var binding : List.of(wrongType, wrongEntity, saved)) {
            boolean rejected = false;
            try {
                QuestExecutionContext.fromLiveProvider(level, player, null, "", type, binding, wolf);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            helper.assertTrue(rejected, "live provider identity, type and liveness must agree with the binding");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "entity_narrative_boundary")
    public static void contextHooksSupplyLocalizedTextAndCustomPlaceholders(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var cow = helper.spawn(EntityType.COW, 1, 1, 1);
        var base = new EntityDialogueContext(helper.getLevel(), player, cow, "en_us", "");
        DialogueRuntimeContext context = new DialogueRuntimeContext() {
            @Override
            public net.minecraft.server.level.ServerLevel level() { return base.level(); }
            @Override
            public net.minecraft.server.level.ServerPlayer player() { return base.player(); }
            @Override
            public net.minecraft.world.entity.LivingEntity speaker() { return base.speaker(); }
            @Override
            public String locale() { return base.locale(); }
            @Override
            public ConditionContext conditions() { return base.conditions(); }
            @Override
            public java.util.Optional<String> message(String key) {
                return key.equals("test:greeting") ? java.util.Optional.of("Welcome to {home}.") : java.util.Optional.empty();
            }
            @Override
            public Map<String, String> replacements() { return Map.of("home", "the pasture"); }
        };
        var variant = new DialogueTextVariant("localized", "Fallback.", "test:greeting", 0, 1, 1,
                List.of(), DialogueEntryMetadata.EMPTY, DialogueUsagePolicy.DEFAULT);
        var node = new DialogueTreeDefinition.Node("start", List.of(), List.of(variant), List.of(),
                List.of(), List.of(), true);
        helper.assertValueEqual(node.selectText(context).text(), "Welcome to {home}.", "tree text uses the context localization hook");
        helper.assertValueEqual(new DialogueExchange(SpeakerKind.ROLE, "Fallback.", "test:greeting")
                .resolve(context, Map.of()).text(), "Welcome to the pasture.", "exchanges use custom messages and replacements");
        helper.assertValueEqual(new DialogueExchange(SpeakerKind.ROLE, "Fallback.", "test:missing")
                .resolve(context, Map.of()).text(), "Fallback.", "missing localization preserves authored fallback text");
        helper.succeed();
    }

    private static QuestProviderType provider(ResourceLocation id) {
        return new QuestProviderType() {
            @Override
            public ResourceLocation id() { return id; }
            @Override
            public boolean matchesOffer(QuestExecutionContext context, QuestDefinition definition) {
                return context.providerBinding().map(binding -> id.equals(binding.providerType())).orElse(false);
            }
            @Override
            public boolean matchesIssuerLock(QuestExecutionContext context, QuestDefinition definition,
                    VillagerQuestSavedData.QuestProgress progress) {
                return progress == null || context.providerBinding().map(binding -> binding.matchesProviderId(progress.startedVillagerId())).orElse(false);
            }
        };
    }
}

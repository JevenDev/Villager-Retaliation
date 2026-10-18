package com.jvn.villagerretaliation.dialogue.normal;

import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Focused server tests. These do not claim client UI or camera verification. */
@GameTestHolder("paired_greet")
@PrefixGameTestTemplate(false)
public final class PairedGreetGameTests {
    private PairedGreetGameTests() { }

    @GameTest(template = "empty", batch = "paired_greet")
    public static void unpairedReputationQuestionVariantsKeepTheirOriginalFallback(GameTestHelper helper) {
        var seed = context(helper);
        var expectedOwners = java.util.Map.of("reputation_neutral_question_1", VillagerReputationLevel.NEUTRAL,
                "reputation_respected_question_1", VillagerReputationLevel.RESPECTED,
                "reputation_revered_question_1", VillagerReputationLevel.REVERED);
        var admitted = java.util.Map.of(
                "reputation_neutral_question_1", java.util.Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 12, 14, 16, 17, 18, 19),
                "reputation_respected_question_1", java.util.Set.of(0, 1, 3, 4, 6, 7, 10, 15, 16, 17),
                "reputation_revered_question_1", java.util.Set.of(0, 2, 5, 7, 13, 16, 19));
        int retained = 0, excluded = 0;
        for (var entry : expectedOwners.entrySet()) {
            com.jvn.villagerretaliation.reputation.VillagerReputationManager.setReputation(seed.level(), seed.villager(), seed.player().getUUID(), reputationFor(entry.getValue()));
            var actual = VillagerInteractionService.createDialogueContext(seed.level(), seed.player(), seed.villager());
            var owner = VillagerDialogueResources.lines(actual).stream().filter(line -> line.id().equals(entry.getKey())).findFirst().orElseThrow();
            var pairs = com.jvn.villagerretaliation.dialogue.resources.PairedGreetingResources.pairs(actual);
            var approvedIds = admitted.get(entry.getKey()).stream().map(index -> entry.getKey() + "#line_" + index)
                    .collect(java.util.stream.Collectors.toSet());
            helper.assertValueEqual(pairs.stream().filter(pair -> pair.ownerId().equals(entry.getKey())).map(PairedGreetingService.Pair::npcVariantId)
                    .collect(java.util.stream.Collectors.toSet()), approvedIds, "only the exact reviewed current-regard subset receives player questions");
            var option = DialogueOptionDefinition.simple("adult_question", "Ask a question", DialogueRequestType.QUESTION, 2);
            helper.assertTrue(owner.matches(actual, DialogueRequestType.QUESTION, option.id(), VillagerDialogueService.dispositionFor(entry.getValue())),
                    "original question owner is genuinely available at its allowed reputation tier");
            helper.assertValueEqual(owner.textVariants().size(), 20, "all original question variants remain loaded without replacement");
            for (var variant : owner.textVariants()) {
                retained++;
                if (pairs.stream().noneMatch(pair -> pair.npcVariantId().equals(variant.id()))) {
                    excluded++;
                    var original = VillagerDialogueService.DialogueResult.fromText(variant.id(), variant.text(), owner.textEffects());
                    helper.assertTrue(PairedGreetingService.attach(actual, option, original, false) == original,
                            "unapproved event/history premise receives no invented player question: " + variant.id());
                }
            }
        }
        helper.assertValueEqual(retained, 60, "all sixty existing reputation-question variants are preserved");
        helper.assertValueEqual(excluded, 27, "all twenty-seven unsupported history/event claims remain unpaired");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "paired_greet")
    public static void optionalBindingOptionDefaultsSafelyAndRejectsInvalidTargets(GameTestHelper helper) {
        var root = com.google.gson.JsonParser.parseString("""
                {"pairs":[{"id":"fixture.parse","owner_id":"fixture.owner","npc_variant_id":"fixture.owner#one",
                "npc_text":"Hello there.","reputation_level":"neutral","familiarity":"first","player":"Hello.","weight":1}]}
                """).getAsJsonObject();
        var location = net.minecraft.resources.ResourceLocation.parse("fixture:paired_options");
        var parsed = com.jvn.villagerretaliation.dialogue.resources.PairedGreetingResources.readPairs(location, root);
        helper.assertValueEqual(parsed.getFirst().optionId(), "adult_greeting", "delivered data without option_id keeps its original Greet binding");
        var pair = root.getAsJsonArray("pairs").get(0).getAsJsonObject();
        pair.addProperty("option_id", "adult_question");
        helper.assertValueEqual(com.jvn.villagerretaliation.dialogue.resources.PairedGreetingResources.readPairs(location, root).getFirst().optionId(),
                "adult_question", "explicit Ask a question binding is admitted");
        for (String invalid : List.of("", "adult_general_question", "quest_accept", "unknown")) {
            pair.addProperty("option_id", invalid);
            helper.assertTrue(com.jvn.villagerretaliation.dialogue.resources.PairedGreetingResources.readPairs(location, root).isEmpty(),
                    "invalid or unrelated explicit option cannot silently default to Greet: " + invalid);
        }
        pair.add("option_id", com.google.gson.JsonNull.INSTANCE);
        helper.assertTrue(com.jvn.villagerretaliation.dialogue.resources.PairedGreetingResources.readPairs(location, root).isEmpty(), "explicit null option fails closed");
        helper.assertTrue(PairedGreetingService.supportsOptionId("adult_greeting") && PairedGreetingService.supportsOptionId("adult_question"),
                "client/server allowlist admits only the two approved existing choices");
        helper.assertTrue(PairedGreetingService.supportsOptionId("adult_general_question"), "staged Small talk now uses the shared correlation transport");
        helper.succeed();
    }

    private static DialogueOptionDefinition optionWith(DialogueOptionDefinition base, String name, Object value) throws Exception {
        var components = DialogueOptionDefinition.class.getRecordComponents();
        var types = new Class<?>[components.length];
        var values = new Object[components.length];
        for (int index = 0; index < components.length; index++) {
            types[index] = components[index].getType();
            values[index] = components[index].getName().equals(name) ? value : components[index].getAccessor().invoke(base);
        }
        return DialogueOptionDefinition.class.getDeclaredConstructor(types).newInstance(values);
    }

    @GameTest(template = "empty", batch = "paired_greet")
    public static void questionPairsRequireExactOptionAndPreserveSpecialBranches(GameTestHelper helper) throws Exception {
        var context = context(helper);
        var question = DialogueOptionDefinition.simple("adult_question", "Ask a question", DialogueRequestType.QUESTION, 2);
        var smallTalk = DialogueOptionDefinition.simple("adult_general_question", "Small talk", DialogueRequestType.QUESTION, 1);
        var original = new VillagerDialogueService.DialogueResult("fixture.owner#question", "I think well of you.");
        var binding = new PairedGreetingService.Pair("fixture.question", "fixture.owner", "fixture.owner#question", original.text(),
                context.reputationLevel(), context.firstConversation(), "What do you think of me?", 1, "adult_question");
        var result = PairedGreetingService.attach(context, question, original, false, List.of(binding));
        helper.assertValueEqual(result.exchanges().size(), 2, "exact Ask a question owner receives matching paired speech");
        helper.assertTrue(PairedGreetingService.attach(context, smallTalk, original, false, List.of(binding)) == original,
                "Small talk sharing the QUESTION request type cannot use Ask a question bindings");
        helper.assertTrue(PairedGreetingService.attach(context, greet(context), original, false, List.of(binding)) == original,
                "Greet cannot accidentally select an Ask a question opener");
        helper.assertTrue(PairedGreetingService.attach(context, optionWith(question, "requestType", DialogueRequestType.GREETING), original, false, List.of(binding)) == original,
                "an allowlisted ID with the wrong request type fails closed");
        helper.assertTrue(PairedGreetingService.attach(context, question, original, true, List.of(binding)) == original,
                "final reputation response overrides remain authoritative");
        var tree = DialogueTreeReference.entry(net.minecraft.resources.ResourceLocation.parse("fixture:tree"), "start");
        helper.assertTrue(PairedGreetingService.attach(context, optionWith(question, "treeReference", tree), original, false, List.of(binding)) == original,
                "existing tree owner cannot gain an unrelated player opener");
        var quest = new DialogueQuestAction(net.minecraft.resources.ResourceLocation.parse("fixture:quest"), DialogueQuestAction.Action.START);
        helper.assertTrue(PairedGreetingService.attach(context, optionWith(question, "questAction", quest), original, false, List.of(binding)) == original,
                "quest actions stay outside ordinary paired scope");
        var removal = new com.jvn.villagerretaliation.util.VillagerInventoryItemRemoval(List.of(
                new com.jvn.villagerretaliation.util.VillagerInventoryItemRemoval.ItemSelector(net.minecraft.world.item.Items.EMERALD, null,
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("emerald"))), 1);
        var payment = new DialogueItemPayment(removal, List.of(), List.of(), DialogueItemPayment.DialogueItemDestination.DISCARD, null, true);
        helper.assertTrue(PairedGreetingService.attach(context, optionWith(question, "itemPayment", payment), original, false, List.of(binding)) == original,
                "payment-bearing choices keep their existing branch and speech");
        var existingSpeech = new VillagerDialogueService.DialogueResult(original.lineId(), original.text(), original.textEffects(), original.textSegments(),
                List.of(com.jvn.villagerretaliation.dialogue.DialogueSpeech.npc(context.villager(), original.textSegments(), null)));
        helper.assertTrue(PairedGreetingService.attach(context, question, existingSpeech, false, List.of(binding)) == existingSpeech,
                "existing exchange owners are never doubled");
        context.villager().setAge(-24000);
        helper.assertTrue(PairedGreetingService.attach(context, question, original, false, List.of(binding)) == original, "adult question pairing cannot leak onto children");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "paired_greet")
    public static void conditionalActionPromptsRemainOriginalNpcOnlyFallbacks(GameTestHelper helper) {
        var context = context(helper);
        var pairs = com.jvn.villagerretaliation.dialogue.resources.PairedGreetingResources.pairs(context);
        var omitted = List.of("fletcher_new_greeting_table#line_11", "librarian_new_greeting_lectern#line_5",
                "toolsmith_new_greeting_table#line_11", "toolsmith_new_greeting_table#line_20", "weaponsmith_new_greeting_grindstone#line_20");
        for (String variantId : omitted) {
            String ownerId = variantId.substring(0, variantId.indexOf('#'));
            String professionName = ownerId.substring(0, ownerId.indexOf('_'));
            context.villager().setVillagerData(context.villager().getVillagerData().setProfession(
                    net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(professionName))));
            var actual = VillagerInteractionService.createDialogueContext(context.level(), context.player(), context.villager());
            var owner = VillagerDialogueResources.lines(actual).stream().filter(line -> line.id().equals(ownerId)).findFirst().orElseThrow();
            var variant = owner.textVariants().stream().filter(candidate -> candidate.id().equals(variantId)).findFirst().orElseThrow();
            helper.assertTrue(variant.matches(actual), "omitted conditional-action prompt is preserved and remains an original eligible NPC candidate");
            helper.assertTrue(pairs.stream().noneMatch(pair -> pair.npcVariantId().equals(variantId)),
                    "conditional item/action premise is not silently turned into a player greeting");
            var original = VillagerDialogueService.DialogueResult.fromText(variant.id(), variant.text(), owner.textEffects());
            helper.assertTrue(PairedGreetingService.attach(actual, greet(actual), original, false) == original,
                    "omitted prompt keeps the exact original NPC-only fallback");
        }
        helper.assertTrue(pairs.stream().noneMatch(pair -> pair.ownerId().startsWith("fletcher_") || pair.ownerId().startsWith("weaponsmith_")),
                "Fletcher and Weaponsmith remain entirely original NPC-only in this bounded group");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "paired_greet")
    public static void timeGoodwillUsesAllFourActualWorldPhases(GameTestHelper helper) {
        var initial = context(helper);
        long originalTime = helper.getLevel().getDayTime();
        try {
            long[] times = {1000, 7000, 12500, 18000};
            String[] phases = {"morning", "afternoon", "evening", "night"};
            for (int index = 0; index < times.length; index++) {
                helper.getLevel().setDayTime(times[index]);
                var actual = VillagerInteractionService.createDialogueContext(helper.getLevel(), initial.player(), initial.villager());
                helper.assertValueEqual(actual.timeOfDay().name().toLowerCase(java.util.Locale.ROOT), phases[index],
                        "fixture reads the actual world phase");
                helper.assertValueEqual(PairedGreetingService.renderPlayer("Have a good <time>.", actual).orElseThrow(),
                        "Have a good " + phases[index] + ".", "goodwill opener uses current world time without invented past events");
                helper.assertValueEqual(PairedGreetingService.renderPlayer("Hello.", actual).orElseThrow(), "Hello.",
                        "plain fallback is safe at every world phase");
            }
        } finally { helper.getLevel().setDayTime(originalTime); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "paired_greet")
    public static void deliveredPairedResourcesRemainByteIdentical(GameTestHelper helper) throws Exception {
        var expected = java.util.Map.of(
                "00_greet_pairs.json", "4fcc3aa36ab4e7251fafb1ee09f3c5691e51585dc05e967c4ff858ca569ec6c6",
                "01_ordinary_greet_extension.json", "37debf9c3c1750a6e3484b90c1353023fafcb282d0785ad73d0e887e6d22e78a");
        for (var entry : expected.entrySet()) {
            var resource = helper.getLevel().getServer().getResourceManager().getResource(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("villagerretaliation", "paired_greetings/en_us/" + entry.getKey())).orElseThrow();
            try (var stream = resource.open()) {
                String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
                helper.assertValueEqual(hash, entry.getValue(), "previously delivered pair resource is untouched: " + entry.getKey());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "paired_greet")
    public static void productionRequestCorrelationRejectsStaleRepliesAndTimesOut(GameTestHelper helper) {
        var state = new com.jvn.villagerretaliation.client.interaction.PairedGreetingRequestState();
        helper.assertFalse(state.pending(), "no request is pending before Greet");
        UUID first = state.begin(1000);
        helper.assertTrue(first != null && state.pending(), "Greet establishes one pending token");
        helper.assertTrue(state.begin(1001) == null, "duplicate Greet cannot replace the outstanding token");
        helper.assertFalse(state.accept(UUID.randomUUID(), 2000), "unrelated notice cannot match the pending request");
        helper.assertTrue(state.pending(), "unrelated notice does not consume the correct request");
        state.cancel();
        UUID second = state.begin(3000);
        helper.assertFalse(first.equals(second), "new interaction uses a new correlation token");
        helper.assertFalse(state.accept(first, 3001), "late cancelled reply cannot interrupt the new request");
        helper.assertTrue(state.accept(second, 3002), "only the new matching reply starts automatic playback");
        helper.assertFalse(state.accept(second, 3003), "duplicate delivery cannot replay an accepted pair");
        UUID expiring = state.begin(10000);
        helper.assertFalse(state.expire(19999), "pending request remains valid before the ten-second boundary");
        helper.assertTrue(state.expire(20000), "ten-second timeout releases the pending interaction");
        helper.assertFalse(state.pending(), "timed-out request cannot keep original options locked");
        helper.assertFalse(state.accept(expiring, 20001), "timed-out response cannot replay later");
        UUID transferring = state.begin(30000);
        var replacement = new com.jvn.villagerretaliation.client.interaction.PairedGreetingRequestState();
        replacement.copyFrom(state); state.cancel();
        helper.assertTrue(replacement.accept(transferring, 39999), "same-conversation replacement retains token and deadline independently");
        helper.assertFalse(state.accept(transferring, 39999), "removed source screen cannot accept transferred work");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "paired_greet")
    public static void automaticModePacketOptInRoundTripsAndLegacyDefaultsRemainManual(GameTestHelper helper) {
        var legacy = new com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload(1, "Legacy", "Resident", List.of(), List.of());
        helper.assertFalse(legacy.autoAdvance(), "legacy packet constructors keep manual Continue behavior");
        var speeches = List.of(
                new com.jvn.villagerretaliation.dialogue.DialogueSpeech(UUID.randomUUID(), 1, "Player", DialogueTextSegment.plain("Hello.", DialogueTextEffects.NONE)),
                new com.jvn.villagerretaliation.dialogue.DialogueSpeech(UUID.randomUUID(), 2, "Resident", DialogueTextSegment.plain("Hello there.", DialogueTextEffects.NONE)));
        var requestId = UUID.randomUUID();
        var automatic = new com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload(2, "Hello.", "Player", speeches.getFirst().segments(), speeches, true, requestId);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload.STREAM_CODEC.encode(buffer, automatic);
            var decoded = com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload.STREAM_CODEC.decode(buffer);
            helper.assertTrue(decoded.autoAdvance(),
                    "explicit automatic mode survives actual packet encoding");
            helper.assertValueEqual(decoded.greetingRequestId(), requestId, "request correlation token survives packet encoding");
            helper.assertValueEqual(decoded.exchanges(), speeches, "both exact ordered speeches survive packet encoding");
            buffer.clear();
            com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload.STREAM_CODEC.encode(buffer, legacy);
            helper.assertFalse(com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload.STREAM_CODEC.decode(buffer).autoAdvance(),
                    "manual mode survives actual packet encoding");
            buffer.clear();
            var request = new com.jvn.villagerretaliation.network.VillagerDialogueRequestPayload(2, "adult_greeting", requestId);
            com.jvn.villagerretaliation.network.VillagerDialogueRequestPayload.STREAM_CODEC.encode(buffer, request);
            helper.assertValueEqual(com.jvn.villagerretaliation.network.VillagerDialogueRequestPayload.STREAM_CODEC.decode(buffer), request,
                    "client request token roundtrips before server correlation");
            helper.assertTrue(new com.jvn.villagerretaliation.network.VillagerDialogueRequestPayload(2, "adult_question").greetingRequestId() == null,
                    "existing request callers never implicitly opt into automatic greeting playback");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "paired_greet")
    public static void productionAutomaticTransitionWaitsForRevealAndReadableHold(GameTestHelper helper) {
        var auto = new com.jvn.villagerretaliation.client.interaction.DialogueExchangeAutoAdvance();
        var none = com.jvn.villagerretaliation.client.interaction.DialogueExchangeAutoAdvance.Step.NONE;
        auto.start();
        auto.lineStarted(5);
        helper.assertValueEqual(auto.poll(1000, false, true), none, "player typewriter cannot be advanced early");
        helper.assertValueEqual(auto.poll(20000, false, true), none, "elapsed time never skips unfinished typewriter");
        helper.assertValueEqual(auto.poll(20000, true, true), none, "readable hold begins only once fully revealed");
        helper.assertValueEqual(auto.poll(20999, true, true), none, "minimum readable hold is not truncated");
        helper.assertValueEqual(auto.poll(21000, true, true), com.jvn.villagerretaliation.client.interaction.DialogueExchangeAutoAdvance.Step.ADVANCE,
                "player advances to NPC only after reveal and hold");
        auto.lineStarted(40);
        helper.assertValueEqual(auto.poll(21000, false, false), none, "NPC also receives complete typewriter time");
        helper.assertValueEqual(auto.poll(23000, true, false), none, "NPC readable hold starts after reveal");
        helper.assertValueEqual(auto.poll(24849, true, false), none, "longer NPC line remains readable before original options return");
        helper.assertValueEqual(auto.poll(24850, true, false), com.jvn.villagerretaliation.client.interaction.DialogueExchangeAutoAdvance.Step.FINISH,
                "final NPC hold finishes without a Continue action");
        helper.assertFalse(auto.active(), "finished auto sequence cannot replay");
        helper.assertValueEqual(auto.poll(999999, true, true), none, "finished queue cannot emit stale extra speech");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "paired_greet")
    public static void productionAutomaticTransitionCancelsAndTransfersWithoutStaleReplay(GameTestHelper helper) {
        var none = com.jvn.villagerretaliation.client.interaction.DialogueExchangeAutoAdvance.Step.NONE;
        var auto = new com.jvn.villagerretaliation.client.interaction.DialogueExchangeAutoAdvance();
        helper.assertValueEqual(auto.poll(999999, true, true), none, "manual exchanges never auto-advance without explicit opt-in");
        for (boolean hasNext : List.of(true, false)) {
            auto.start(); auto.lineStarted(20); auto.poll(1000, true, hasNext); auto.cancel();
            helper.assertFalse(auto.active(), "dismissal cancels during either player or NPC line");
            helper.assertValueEqual(auto.poll(999999, true, hasNext), none, "dismissed line can never dispatch a stale successor");
        }
        auto.start(); auto.lineStarted(5); auto.poll(1000, true, true);
        var replacement = new com.jvn.villagerretaliation.client.interaction.DialogueExchangeAutoAdvance();
        replacement.copyFrom(auto); auto.cancel();
        helper.assertTrue(replacement.active(), "same-conversation state transfer is independent of removed source screen");
        helper.assertValueEqual(replacement.poll(1999, true, true), none, "replacement preserves the original hold deadline");
        helper.assertValueEqual(replacement.poll(2000, true, true), com.jvn.villagerretaliation.client.interaction.DialogueExchangeAutoAdvance.Step.ADVANCE,
                "replacement neither resets timing nor skips its pending successor");
        replacement.cancel();
        helper.assertValueEqual(replacement.poll(999999, true, true), none, "plain or unrelated replacement cancels old work");
        helper.assertValueEqual(com.jvn.villagerretaliation.client.interaction.DialogueExchangeAutoAdvance.holdMillis(0), 1000L, "short lines still have readable hold");
        helper.assertValueEqual(com.jvn.villagerretaliation.client.interaction.DialogueExchangeAutoAdvance.holdMillis(1000), 4000L, "long-line hold remains bounded after full reveal");
        helper.succeed();
    }

    private static DialogueContext context(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "PairReader"));
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        villager.setNoAi(true);
        return VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
    }

    private static DialogueOptionDefinition greet(DialogueContext context) {
        return VillagerDialogueResources.dialogueOption(context, "adult_greeting").orElseThrow();
    }

    private static PairedGreetingService.Pair pair(DialogueContext context, String id, String player) {
        return new PairedGreetingService.Pair(id, "fixture.owner", "fixture.owner#one", "Hello there.",
                context.reputationLevel(), context.firstConversation(), player, 1);
    }

    private static int reputationFor(VillagerReputationLevel tier) {
        return switch (tier) {
            case ROYALTY -> com.jvn.villagerretaliation.config.VillagerRetaliationConfig.ROYALTY_THRESHOLD.get();
            case REVERED -> com.jvn.villagerretaliation.config.VillagerRetaliationConfig.REVERED_THRESHOLD.get();
            case RESPECTED -> com.jvn.villagerretaliation.config.VillagerRetaliationConfig.RESPECTED_THRESHOLD.get();
            case TRUSTED -> com.jvn.villagerretaliation.config.VillagerRetaliationConfig.TRUSTED_THRESHOLD.get();
            case NEUTRAL -> 0;
            case SUSPICIOUS -> com.jvn.villagerretaliation.config.VillagerRetaliationConfig.SUSPICIOUS_THRESHOLD.get();
            case HOSTILE -> com.jvn.villagerretaliation.config.VillagerRetaliationConfig.HOSTILE_THRESHOLD.get();
            case DESPISED -> com.jvn.villagerretaliation.config.VillagerRetaliationConfig.DESPISED_THRESHOLD.get();
            case FEARED -> com.jvn.villagerretaliation.config.VillagerRetaliationConfig.FEARED_THRESHOLD.get();
        };
    }

    @GameTest(template = "empty", batch = "paired_greet", timeoutTicks = 400)
    public static void allBundledOwnersTiersAndContactStatesRespectRealNpcGates(GameTestHelper helper) {
        try {
        var sample = context(helper);
        var pairs = com.jvn.villagerretaliation.dialogue.resources.PairedGreetingResources.pairs(sample);
        helper.assertValueEqual(pairs.size(), 3899, "all four frozen paired resource files load without skipped records");
        helper.assertValueEqual(pairs.stream().filter(pair -> pair.optionId().equals("adult_greeting")).count(), 3659L, "all Greet bindings retain their original option identity");
        helper.assertValueEqual(pairs.stream().filter(pair -> pair.optionId().equals("adult_question")).count(), 240L, "bounded social-regard bindings target only Ask a question");
        var owners = pairs.stream().map(PairedGreetingService.Pair::ownerId).collect(java.util.stream.Collectors.toSet());
        helper.assertValueEqual(owners.size(), 16, "all sixteen existing owners load");
        var reached = new java.util.HashSet<String>();
        for (String ownerId : owners) {
            String professionName = ownerId.substring(0, ownerId.indexOf('_'));
            var profession = professionName.equals("unemployed") || professionName.equals("reputation") ? net.minecraft.world.entity.npc.VillagerProfession.NONE
                    : net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(professionName));
            var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
            villager.setNoAi(true);
            villager.setVillagerData(villager.getVillagerData().setProfession(profession));
            villager.setCustomName(Component.literal("Ada"));
            com.jvn.villagerretaliation.mood.VillagerMoodService.setMood(helper.getLevel(), villager,
                    com.jvn.villagerretaliation.mood.VillagerMood.CONTENT, 50, "paired_test", null, null, 10000);
            for (boolean first : List.of(true, false)) {
                var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "BundleReader"));
                if (!first) VillagerInteractionTracker.rememberDialogue(helper.getLevel(), villager, player, DialogueRequestType.GREETING, "fixture.previous");
                for (var tier : VillagerReputationLevel.values()) {
                    com.jvn.villagerretaliation.reputation.VillagerReputationManager.setReputation(helper.getLevel(), villager, player.getUUID(), reputationFor(tier));
                    var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
                    helper.assertValueEqual(context.reputationLevel(), tier, "test uses actual stored reputation for " + tier);
                    helper.assertValueEqual(context.firstConversation(), first, "test uses actual pair contact history");
                    var owner = VillagerDialogueResources.lines(context).stream().filter(line -> line.id().equals(ownerId)).findFirst().orElseThrow();
                    var variants = owner.textVariants().stream().collect(java.util.stream.Collectors.toMap(DialogueTextVariant::id, variant -> variant));
                    var compatible = pairs.stream().filter(pair -> pair.ownerId().equals(ownerId) && pair.reputationLevel() == tier && pair.firstConversation() == first).toList();
                    for (var binding : compatible) {
                        var variant = variants.get(binding.npcVariantId());
                        var requestType = binding.optionId().equals("adult_question") ? DialogueRequestType.QUESTION : DialogueRequestType.GREETING;
                        helper.assertTrue(owner.matches(context, requestType, binding.optionId(), VillagerDialogueService.dispositionFor(tier)),
                                "bound NPC owner passes its actual request/age/reputation gates: " + binding.id());
                        helper.assertTrue(variant != null && variant.matches(context), "bound NPC variant passes its actual adult/reputation/mood gates: " + binding.id());
                        var original = VillagerDialogueService.DialogueResult.fromText(variant.id(), variant.text(), owner.textEffects());
                        helper.assertValueEqual(original.text(), DialogueTextSegment.plainText(DialogueTextSegment.parse(binding.npcText(), DialogueTextEffects.NONE)),
                                "every binding preserves its exact loaded NPC counterpart");
                        helper.assertTrue(compatible.stream().anyMatch(fallback -> fallback.npcVariantId().equals(binding.npcVariantId()) && !fallback.player().contains("<")),
                                "every exact tier/contact binding has a token-free fallback");
                        var result = PairedGreetingService.attach(context, DialogueOptionDefinition.simple(binding.optionId(), "Existing choice", requestType, 0), original, false, List.of(binding));
                        helper.assertValueEqual(result.exchanges().size(), 2, "every bundled eligible opener renders safely: " + binding.id());
                        helper.assertValueEqual(result.exchanges().getLast().segments(), original.textSegments(), "NPC styling survives all bundled bindings");
                        reached.add(binding.id());
                    }
                    for (var variant : variants.values()) {
                        var variantPairs = pairs.stream().filter(pair -> pair.npcVariantId().equals(variant.id())).toList();
                        if (!variantPairs.isEmpty() && (!variant.conditions().isEmpty() || !owner.reputationCondition().isEmpty())
                                && variantPairs.stream().noneMatch(pair -> pair.reputationLevel() == tier)) {
                            helper.assertFalse(owner.matches(context, owner.requestType(), variantPairs.getFirst().optionId(), VillagerDialogueService.dispositionFor(tier)) && variant.matches(context),
                                    "wrong stored reputation cannot unlock tier-specific NPC text: " + variant.id());
                        }
                    }
                }
            }
            villager.discard();
        }
        helper.assertValueEqual(reached.size(), pairs.size(), "all 3899 bundled pair records exercised under their actual owner/option/tier/contact state");
        helper.succeed();

        } catch (RuntimeException error) { com.mojang.logging.LogUtils.getLogger().error("allBundledOwnersTiersAndContactStatesRespectRealNpcGates failed", error); throw error; }
    }

    @GameTest(template = "empty", batch = "paired_greet", timeoutTicks = 200)
    public static void actualGreetRequestSendsOneOrderedPayloadAndRecordsOneInteraction(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var profile = new GameProfile(UUID.randomUUID(), "PairNetwork");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        var player = new net.minecraft.server.level.ServerPlayer(server, level, profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        var channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER));
        try {
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
            player.setPos(villager.getX() + 1, villager.getY(), villager.getZ());
            helper.assertTrue(com.jvn.villagerretaliation.interaction.VillagerConversationService.start(player, villager), "real normal conversation begins");
            var context = VillagerInteractionService.createDialogueContext(level, player, villager);
            var bindings = com.jvn.villagerretaliation.dialogue.resources.PairedGreetingResources.pairs(context);
            long chosenSeed = -1;
            String expectedNpc = "";
            for (long seed = 0; seed < 2048; seed++) {
                context.random().setSeed(seed);
                var selected = VillagerDialogueService.select(context, greet(context), List.of());
                boolean bound = bindings.stream().anyMatch(pair -> pair.npcVariantId().equals(selected.lineId())
                        && DialogueTextSegment.plainText(DialogueTextSegment.parse(pair.npcText(), DialogueTextEffects.NONE)).equals(selected.text())
                        && pair.reputationLevel() == context.reputationLevel() && pair.firstConversation() == context.firstConversation()
                        && PairedGreetingService.renderPlayer(pair.player(), context).isPresent());
                if (bound) { chosenSeed = seed; expectedNpc = selected.text(); break; }
            }
            helper.assertTrue(chosenSeed >= 0, "bundled paired Greet is reachable through the real NPC selector");
            while (channel.readOutbound() != null) { }
            context.random().setSeed(chosenSeed);
            UUID requestId = UUID.randomUUID();
            com.jvn.villagerretaliation.interaction.VillagerDialogueRequestHandler.handle(player, villager.getId(), "adult_greeting", requestId);
            channel.runPendingTasks();
            var notices = new java.util.ArrayList<com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload>();
            Object outgoing;
            while ((outgoing = channel.readOutbound()) != null) {
                if (outgoing instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket packet
                        && packet.payload() instanceof com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload notice) notices.add(notice);
            }
            helper.assertValueEqual(notices.size(), 1, "one actual request emits exactly one dialogue notice to its player");
            helper.assertTrue(notices.getFirst().autoAdvance(), "actual paired Greet handler opts into automatic client playback");
            helper.assertValueEqual(notices.getFirst().greetingRequestId(), requestId, "actual handler preserves this request's correlation token");
            var speech = notices.getFirst().exchanges();
            helper.assertValueEqual(speech.size(), 2, "actual dispatched notice carries exactly the paired speeches");
            helper.assertValueEqual(speech.stream().map(line -> line.speaker()).toList(), List.of(player.getUUID(), villager.getUUID()),
                    "sent payload orders player before NPC exactly once");
            helper.assertValueEqual(speech.getLast().text(), expectedNpc, "handler dispatch preserves the originally selected NPC reply");
            helper.assertValueEqual(VillagerInteractionTracker.getState(level, villager, player).greetingUseCount(), 1,
                    "actual paired request counts exactly one greeting interaction");
            helper.assertFalse(VillagerInteractionTracker.getState(level, villager, player).firstConversation(),
                    "only completed dispatch establishes the actual contact history");
            com.jvn.villagerretaliation.interaction.VillagerDialogueRequestHandler.handle(player,villager.getId(),"adult_greeting",requestId);
            channel.runPendingTasks();
            helper.assertTrue(channel.readOutbound()==null,"replayed token cannot send or execute the action twice");
            helper.assertValueEqual(VillagerInteractionTracker.getState(level,villager,player).greetingUseCount(),1,"replay preserves exact interaction count");
            var fallbackContext = VillagerInteractionService.createDialogueContext(level, player, villager);
            var recent = VillagerInteractionTracker.getState(level, villager, player).recentDialogueIds();
            long fallbackSeed = -1;
            for (long seed = 0; seed < 2048; seed++) {
                fallbackContext.random().setSeed(seed);
                var selected = VillagerDialogueService.select(fallbackContext, greet(fallbackContext), recent);
                if (bindings.stream().noneMatch(pair -> pair.npcVariantId().equals(selected.lineId())
                        && pair.reputationLevel() == fallbackContext.reputationLevel()
                        && pair.firstConversation() == fallbackContext.firstConversation())) {
                    fallbackSeed = seed; break;
                }
            }
            helper.assertTrue(fallbackSeed >= 0, "an intentionally unbound original NPC fallback remains selectable");
            UUID fallbackRequestId = UUID.randomUUID();
            fallbackContext.random().setSeed(fallbackSeed);
            com.jvn.villagerretaliation.interaction.VillagerDialogueRequestHandler.handle(player, villager.getId(), "adult_greeting", fallbackRequestId);
            channel.runPendingTasks(); notices.clear();
            while ((outgoing = channel.readOutbound()) != null) {
                if (outgoing instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket packet
                        && packet.payload() instanceof com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload notice) notices.add(notice);
            }
            helper.assertValueEqual(notices.size(), 1, "unbound original fallback also emits one notice");
            helper.assertFalse(notices.getFirst().autoAdvance(), "unbound original NPC fallback keeps manual/legacy presentation");
            helper.assertValueEqual(notices.getFirst().greetingRequestId(), fallbackRequestId,
                    "manual fallback still carries its exact request token so stale delivery can be rejected");
            helper.assertValueEqual(VillagerInteractionTracker.getState(level, villager, player).greetingUseCount(), 2,
                    "one paired request and one ordinary fallback count as exactly two interactions");
            var questionContext = VillagerInteractionService.createDialogueContext(level, player, villager);
            var questionOption = VillagerDialogueResources.dialogueOption(questionContext, "adult_question").orElseThrow();
            var questionRecent = VillagerInteractionTracker.getState(level, villager, player).recentDialogueIds();
            long questionSeed = -1;
            for (long seed = 0; seed < 4096; seed++) {
                questionContext.random().setSeed(seed);
                var selected = VillagerDialogueService.select(questionContext, questionOption, questionRecent);
                if (bindings.stream().anyMatch(pair -> pair.optionId().equals("adult_question") && pair.npcVariantId().equals(selected.lineId())
                        && pair.reputationLevel() == questionContext.reputationLevel() && pair.firstConversation() == questionContext.firstConversation())) {
                    questionSeed = seed; break;
                }
            }
            helper.assertTrue(questionSeed >= 0, "bounded social-regard pairing is reachable through existing Ask a question selection");
            UUID questionRequestId = null;
            int questionAttempts=0;
            // Menu construction evaluates live quest offers too; do not assume it consumes no RNG.
            for (int attempt=0;attempt<64;attempt++) {
                questionRequestId=UUID.randomUUID();
                questionContext.random().setSeed(questionSeed+attempt);
                com.jvn.villagerretaliation.interaction.VillagerDialogueRequestHandler.handle(player,villager.getId(),"adult_question",questionRequestId);
                questionAttempts++;
                channel.runPendingTasks();notices.clear();
                while ((outgoing=channel.readOutbound())!=null) {
                    if (outgoing instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket packet
                            && packet.payload() instanceof com.jvn.villagerretaliation.network.VillagerInteractionNoticePayload notice) notices.add(notice);
                }
                helper.assertValueEqual(notices.size(),1,"each actual question request sends one response");
                if (notices.getFirst().autoAdvance()) break;
            }
            helper.assertTrue(notices.getFirst().autoAdvance(), "exact paired question receives automatic player-to-NPC delivery");
            helper.assertValueEqual(notices.getFirst().greetingRequestId(), questionRequestId, "question response echoes its own token");
            helper.assertValueEqual(notices.getFirst().exchanges().stream().map(line -> line.speaker()).toList(), List.of(player.getUUID(), villager.getUUID()),
                    "actual question response preserves exact ordered speakers");
            helper.assertValueEqual(VillagerInteractionTracker.getState(level, villager, player).questionUseCount(), questionAttempts,
                    "each request records exactly one QUESTION interaction");
            helper.assertValueEqual(VillagerInteractionTracker.getState(level, villager, player).greetingUseCount(), 2,
                    "question pairing cannot increment the Greet request counter");
        } finally {
            com.jvn.villagerretaliation.interaction.VillagerConversationService.endForPlayer(player, false);
            server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "paired_greet")
    public static void styledWellbeingReplyRemainsStyledAndKeepsQuestionIntent(GameTestHelper helper) {
        var context = context(helper);
        String authored = "I'm feeling <aqua>content</aqua>, thanks for asking.";
        var original = VillagerDialogueService.DialogueResult.fromText("fixture.owner#one", authored, DialogueTextEffects.NONE);
        var binding = new PairedGreetingService.Pair("fixture.wellbeing", "fixture.owner", "fixture.owner#one", authored,
                context.reputationLevel(), context.firstConversation(), "Hello. How are you feeling?", 1);
        var result = PairedGreetingService.attach(context, greet(context), original, false, List.of(binding));
        helper.assertValueEqual(result.exchanges().size(), 2, "styled NPC text remains an eligible exact pair");
        helper.assertValueEqual(result.exchanges().getFirst().text(), binding.player(), "wellbeing answer receives the corresponding question");
        helper.assertValueEqual(result.exchanges().getLast().segments(), original.textSegments(), "authored aqua emphasis survives pairing exactly");
        helper.assertTrue(result.exchanges().getLast().segments().stream().anyMatch(segment -> segment.effects().color() != null),
                "styled wellbeing fixture actually exercises colored text");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "paired_greet")
    public static void exactBindingProducesOneOrderedPairWithoutNewOptions(GameTestHelper helper) {
        var context = context(helper);
        var option = greet(context);
        var optionsBefore = VillagerDialogueResources.dialogueOptions(context, VillagerDialogueService.dispositionFor(context.reputationLevel())).stream()
                .map(value -> value.withSelectionToken(new UUID(0, 0))).toList();
        var original = new VillagerDialogueService.DialogueResult("fixture.owner#one", "Hello there.");
        var result = PairedGreetingService.attach(context, option, original, false,
                List.of(pair(context, "fixture.pair.one", "Good morning.")));
        helper.assertValueEqual(result.exchanges().size(), 2, "one Greet result contains exactly two speeches");
        helper.assertValueEqual(result.exchanges().stream().map(line -> line.speaker()).toList(),
                List.of(context.player().getUUID(), context.villager().getUUID()), "the player speaks before the selected NPC");
        helper.assertValueEqual(result.exchanges().stream().map(line -> line.text()).toList(),
                List.of("Good morning.", "Hello there."), "the exact authored NPC response is retained once");
        helper.assertValueEqual(result.lineId(), original.lineId(), "pairing cannot replace the selected source owner");
        helper.assertValueEqual(result.textSegments(), original.textSegments(), "pairing preserves NPC styling and content");
        helper.assertTrue(result.exchanges().stream().allMatch(line -> line.camera() == null), "pairing introduces no camera change");
        helper.assertValueEqual(VillagerDialogueResources.dialogueOptions(context, VillagerDialogueService.dispositionFor(context.reputationLevel())).stream()
                        .map(value -> value.withSelectionToken(new UUID(0, 0))).toList(),
                optionsBefore, "pairing neither adds a Continue choice nor changes the existing menu");
        helper.assertTrue(PairedGreetingService.attach(context, option, result, false,
                List.of(pair(context, "fixture.pair.one", "Good morning."))) == result, "already paired speech cannot be attached twice");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "paired_greet")
    public static void mismatchedBindingsAndFinalOverridesFallBackWithoutChangingNpc(GameTestHelper helper) {
        var context = context(helper);
        var original = new VillagerDialogueService.DialogueResult("fixture.owner#one", "Hello there.");
        var differentTier = context.reputationLevel() == VillagerReputationLevel.TRUSTED
                ? VillagerReputationLevel.NEUTRAL : VillagerReputationLevel.TRUSTED;
        var invalid = List.of(
                new PairedGreetingService.Pair("wrong-tier", "fixture.owner", "fixture.owner#one", original.text(), differentTier, context.firstConversation(), "Wrong tier.", 1),
                new PairedGreetingService.Pair("wrong-history", "fixture.owner", "fixture.owner#one", original.text(), context.reputationLevel(), !context.firstConversation(), "Wrong history.", 1),
                new PairedGreetingService.Pair("wrong-owner", "other.owner", "other.owner#one", original.text(), context.reputationLevel(), context.firstConversation(), "Wrong owner.", 1),
                new PairedGreetingService.Pair("wrong-answer", "fixture.owner", "fixture.owner#one", "I am well.", context.reputationLevel(), context.firstConversation(), "How are you?", 1));
        helper.assertTrue(PairedGreetingService.attach(context, greet(context), original, false, invalid) == original,
                "tier, contact-state, owner and exact-answer mismatches all preserve the original branch");
        helper.assertTrue(PairedGreetingService.attach(context, greet(context), original, true,
                List.of(pair(context, "valid", "Hello."))) == original, "a final reputation/payment override cannot be paired with unrelated intent");
        var otherOption = VillagerDialogueResources.dialogueOptions(context, VillagerDialogueService.dispositionFor(context.reputationLevel()))
                .stream().filter(candidate -> !candidate.id().equals("adult_greeting")).findFirst().orElseThrow();
        helper.assertTrue(PairedGreetingService.attach(context, otherOption, original, false,
                List.of(pair(context, "valid", "Hello."))) == original, "non-Greet branches remain untouched");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "paired_greet")
    public static void alternativePairsAvoidImmediateRepeatWithoutDoubleCountingRequests(GameTestHelper helper) {
        var context = context(helper);
        var original = new VillagerDialogueService.DialogueResult("fixture.owner#one", "Hello there.");
        var pairs = List.of(pair(context, "fixture.rotation.one", "Hello."), pair(context, "fixture.rotation.two", "Greetings."));
        int countBefore = VillagerInteractionTracker.getState(context.level(), context.villager(), context.player()).greetingUseCount();
        var first = PairedGreetingService.attach(context, greet(context), original, false, pairs);
        var second = PairedGreetingService.attach(context, greet(context), original, false, pairs);
        helper.assertFalse(first.exchanges().getFirst().text().equals(second.exchanges().getFirst().text()),
                "an unused compatible pair is preferred over immediately repeating the used pair");
        helper.assertValueEqual(VillagerInteractionTracker.getState(context.level(), context.villager(), context.player()).greetingUseCount(), countBefore,
                "pair rotation bookkeeping cannot count a second greeting request");
        String scope = "player_villager:" + context.player().getUUID() + ":" + context.villager().getUUID();
        var stored = DialogueUsageSavedData.get(context.level());
        var loaded = DialogueUsageSavedData.load(stored.save(new net.minecraft.nbt.CompoundTag(), context.level().registryAccess()), context.level().registryAccess());
        String firstUsageId = PairedGreetingService.playerUsageId("Hello.");
        String secondUsageId = PairedGreetingService.playerUsageId("Greetings.");
        var ids = java.util.Set.of(firstUsageId, secondUsageId);
        helper.assertValueEqual(loaded.lastUsedAmong(scope, ids), stored.lastUsedAmong(scope, ids),
                "same-tick pair ordering survives serialization for anti-repeat after reload");
        helper.assertValueEqual(loaded.usage(scope, firstUsageId).count(), 1,
                "first compatible pair was used once");
        helper.assertValueEqual(loaded.usage(scope, secondUsageId).count(), 1,
                "second compatible pair was used once");
        helper.assertTrue(loaded.lastUsedAmong("player_villager:" + UUID.randomUUID() + ":" + context.villager().getUUID(), ids).isEmpty(),
                "another player cannot inherit opener-repeat history");
        helper.assertTrue(loaded.lastUsedAmong("player_villager:" + context.player().getUUID() + ":" + UUID.randomUUID(), ids).isEmpty(),
                "another villager cannot inherit opener-repeat history");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "paired_greet")
    public static void playerWordingRotationWorksAcrossDifferentNpcVariantBindings(GameTestHelper helper) {
        var context = context(helper);
        var firstResult = new VillagerDialogueService.DialogueResult("fixture.owner#one", "Hello there.");
        var secondResult = new VillagerDialogueService.DialogueResult("fixture.owner#two", "Good to see you.");
        var first = PairedGreetingService.attach(context, greet(context), firstResult, false,
                List.of(pair(context, "fixture.cross.one", "Hello."), pair(context, "fixture.cross.two", "Greetings.")));
        var secondBindings = List.of(
                new PairedGreetingService.Pair("fixture.cross.three", "fixture.owner", "fixture.owner#two", secondResult.text(),
                        context.reputationLevel(), context.firstConversation(), "Hello.", 1),
                new PairedGreetingService.Pair("fixture.cross.four", "fixture.owner", "fixture.owner#two", secondResult.text(),
                        context.reputationLevel(), context.firstConversation(), "Greetings.", 1));
        var second = PairedGreetingService.attach(context, greet(context), secondResult, false, secondBindings);
        helper.assertFalse(first.exchanges().getFirst().text().equals(second.exchanges().getFirst().text()),
                "changing NPC variant IDs cannot immediately repeat the same player wording when another opener exists");
        helper.assertValueEqual(second.exchanges().getLast().text(), secondResult.text(), "opener rotation cannot change the selected NPC variant");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "paired_greet")
    public static void playerTemplatesResolveTimeAndFailClosedForUnsafeNames(GameTestHelper helper) {
        var context = context(helper);
        helper.assertTrue(PairedGreetingService.renderPlayer("Hello.", context).isPresent(), "plain greeting is a safe fallback");
        helper.assertTrue(PairedGreetingService.renderPlayer("Hello, <villager>.", context).isEmpty(),
                "first contact does not silently invent knowledge of a resident's name");
        var time = PairedGreetingService.renderPlayer("<time_greeting> during the <day_phase> (<time>).", context).orElseThrow();
        helper.assertFalse(time.contains("<"), "time tokens resolve before speech is parsed");
        helper.assertTrue(PairedGreetingService.renderPlayer("Hello <unsupported>.", context).isEmpty(), "unknown placeholders fall back safely");
        VillagerInteractionTracker.rememberDialogue(context.level(), context.villager(), context.player(), DialogueRequestType.GREETING, "fixture.introduced");
        context.villager().setCustomName(Component.literal("<shake>Injected</shake>"));
        var familiar = VillagerInteractionService.createDialogueContext(context.level(), context.player(), context.villager());
        var unsafe = PairedGreetingService.renderPlayer("Hello, <villager>.", familiar);
        helper.assertTrue(unsafe.isEmpty() || DialogueTextSegment.parse(unsafe.orElse(""), DialogueTextEffects.NONE).stream()
                .allMatch(segment -> !segment.effects().active()), "a custom name cannot inject styling into player speech");
        context.villager().setCustomName(Component.literal("Ada $\\ Reader"));
        helper.assertValueEqual(PairedGreetingService.renderPlayer("Hello, <villager>.", familiar).orElseThrow(), "Hello, Ada $\\ Reader.",
                "real familiar names safely escape regex replacement metacharacters");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "paired_greet")
    public static void openingDoesNotInventPriorConversationAndHistoryIsPairScoped(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "GreetOne"));
        var otherPlayer = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "GreetTwo"));
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        var otherVillager = helper.spawn(EntityType.VILLAGER, 3, 1, 1);
        villager.setNoAi(true);
        otherVillager.setNoAi(true);
        helper.assertTrue(VillagerInteractionService.createDialogueContext(level, player, villager).firstConversation(),
                "an untouched player-villager pair is genuinely first-time");
        VillagerInteractionTracker.rememberConversationOpened(level, villager, player);
        helper.assertTrue(VillagerInteractionService.createDialogueContext(level, player, villager).firstConversation(),
                "merely opening the menu does not invent an earlier conversation");
        VillagerInteractionTracker.rememberDialogue(level, villager, player, DialogueRequestType.GREETING, "fixture.paired.first");
        helper.assertFalse(VillagerInteractionService.createDialogueContext(level, player, villager).firstConversation(),
                "a completed actual greeting establishes familiarity for this pair");
        helper.assertTrue(VillagerInteractionService.createDialogueContext(level, otherPlayer, villager).firstConversation(),
                "another player's interaction cannot establish familiarity");
        helper.assertTrue(VillagerInteractionService.createDialogueContext(level, player, otherVillager).firstConversation(),
                "another villager's interaction cannot establish familiarity");
        helper.assertTrue(VillagerInteractionTracker.getState(level, villager, player).recentDialogueIds().contains("fixture.paired.first"),
                "actual speaker pair owns its selection history");
        helper.assertTrue(VillagerInteractionTracker.getState(level, villager, otherPlayer).recentDialogueIds().isEmpty(),
                "selection history cannot leak between players");
        helper.assertTrue(VillagerInteractionTracker.getState(level, otherVillager, player).recentDialogueIds().isEmpty(),
                "selection history cannot leak between villagers");
        helper.succeed();
    }
}

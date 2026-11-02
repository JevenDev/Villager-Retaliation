package com.jvn.villagerretaliation.dialogue.normal;

import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueTreeResources;
import com.jvn.villagerretaliation.dialogue.resources.DialogueWordPoolResources;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.mood.VillagerMoodService;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("greeting_content")
@PrefixGameTestTemplate(false)
public final class SharedGreetingGameTests {
    private static final String OWNER = "greeting.shared.ordinary";

    private SharedGreetingGameTests() { }

    @GameTest(template = "empty", batch = "greeting_content")
    public static void greetSelectsTheSharedExchangeWithoutAddingAnotherMenuEntry(GameTestHelper helper) {
        var context = context(helper);
        var menu = VillagerDialogueResources.dialogueOptions(context, VillagerDialogueService.moodFor(context));
        helper.assertValueEqual(menu.stream().filter(option -> option.requestType() == DialogueRequestType.GREETING).count(),
                1L, "the existing Greet action remains the only adult greeting entry");
        helper.assertTrue(menu.stream().anyMatch(option -> option.id().equals("adult_greeting")), "the public Greet ID remains stable");
        var selected = VillagerDialogueService.select(context,
                VillagerDialogueResources.dialogueOption(context, "adult_greeting").orElseThrow(), List.of());
        helper.assertTrue(DialogueTreeResources.hasLineEntry(context, selected.lineId()), "normal ordinary greetings use the shared flow");
        helper.assertValueEqual(selected.exchanges().size(), 2, "the player and villager each speak");
        helper.assertValueEqual(selected.exchanges().getFirst().speaker(), context.player().getUUID(), "the player greets first");
        helper.assertValueEqual(selected.exchanges().getLast().speaker(), context.villager().getUUID(), "the villager answers");
        assertResolved(helper, selected);
        helper.assertValueEqual(DialogueTreeService.activeOptions(context).orElseThrow().size(), 3, "ordinary replies are offered");
        DialogueTreeService.leaveActiveSession(context);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "greeting_content")
    public static void everyPlayerResponseSpeaksAndReturnsToTheNormalMenu(GameTestHelper helper) {
        var context = context(helper);
        for (String finalReply : List.of("return_greeting", "brief_hello",
                "player_well", "player_steady", "player_struggling", "player_private")) {
            DialogueTreeResources.startLineEntry(context, OWNER).orElseThrow();
            if (finalReply.startsWith("player_")) {
                var questionChoice = response(context, "ask_wellbeing");
                var question = choose(context, "ask_wellbeing");
                helper.assertValueEqual(question.exchanges().getFirst().text(), questionChoice.label(), "the selected question is spoken");
                helper.assertValueEqual(DialogueTreeService.activeOptions(context).orElseThrow().size(), 4, "wellbeing has four honest answers");
                assertResolved(helper, question);
            }
            var option = response(context, finalReply);
            helper.assertTrue(DialogueTreeResources.isLineResponse(context, option), "follow-up choices do not count as new reputation actions");
            var result = DialogueTreeService.handleDialogueOption(context, option).orElseThrow();
            helper.assertValueEqual(result.exchanges().size(), 2, "the chosen answer receives a villager reply");
            helper.assertValueEqual(result.exchanges().getFirst().text(), option.label(), "the spoken player response matches its button");
            assertResolved(helper, result);
            helper.assertTrue(DialogueTreeService.activeOptions(context).isEmpty(), "the exchange releases its session");
            helper.assertTrue(VillagerDialogueResources.dialogueOption(context, "adult_greeting").isPresent(), "the normal menu is restored");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "greeting_content")
    public static void wellbeingReflectsActualMoodAndInjury(GameTestHelper helper) {
        var initial = context(helper);
        var poolId = ResourceLocation.parse("villagerretaliation:greetings/professions/unemployed/wellbeing");
        for (var mood : VillagerMood.values()) {
            VillagerMoodService.setMood(initial.level(), initial.villager(), VillagerMood.NEUTRAL, 0, "greeting_test", null, null, 10000);
            VillagerMoodService.setMood(initial.level(), initial.villager(), mood, 60, "greeting_test", null, null, 10000);
            var context = VillagerInteractionService.createDialogueContext(initial.level(), initial.player(), initial.villager());
            helper.assertValueEqual(context.moodState().primaryMood(), mood, "each mood case starts from its intended state");
            var pool = DialogueWordPoolResources.localizedPool(context.level().getServer(), context.locale(), poolId);
            var candidates = pool.candidates(rules -> DialogueCondition.matchesAll(context, rules));
            for (int seed = 0; seed < 8; seed++) {
                DialogueTreeResources.startLineEntry(context, OWNER).orElseThrow();
                var result = choose(context, "ask_wellbeing");
                String npc = result.exchanges().getLast().text();
                helper.assertTrue(candidates.entries().stream().anyMatch(entry -> npc.startsWith(pool.format(entry.text()))),
                        "wellbeing uses a currently eligible mood answer: " + mood);
                assertResolved(helper, result);
                DialogueTreeService.leaveActiveSession(context);
            }
        }
        initial.villager().setHealth(initial.villager().getMaxHealth() / 4);
        var injured = VillagerInteractionService.createDialogueContext(initial.level(), initial.player(), initial.villager());
        var pool = DialogueWordPoolResources.localizedPool(injured.level().getServer(), injured.locale(), poolId);
        helper.assertTrue(pool.candidates(rules -> DialogueCondition.matchesAll(injured, rules)).entries().stream()
                .allMatch(entry -> entry.id().startsWith("injured_")), "actual injury takes precedence over ordinary mood wording");
        VillagerMoodService.setMood(initial.level(), initial.villager(), VillagerMood.NEUTRAL, 0, "greeting_test", null, null, 10000);
        VillagerMoodService.setMood(initial.level(), initial.villager(), VillagerMood.STRESSED, 60, "greeting_test", null, null, 10000);
        var quiet = VillagerInteractionService.createDialogueContext(initial.level(), initial.player(), initial.villager());
        DialogueTreeResources.startLineEntry(quiet, OWNER).orElseThrow();
        var result = choose(quiet, "ask_wellbeing");
        helper.assertTrue(result.exchanges().getLast().segments().stream().anyMatch(segment -> segment.effects().italic()),
                "quiet styling survives message and pool resolution");
        DialogueTreeService.leaveActiveSession(quiet);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "greeting_content")
    public static void reputationAndRecentWordingStayContextual(GameTestHelper helper) {
        var initial = context(helper);
        var poolId = ResourceLocation.parse("villagerretaliation:greetings/voice");
        for (var tier : VillagerReputationLevel.values()) {
            int reputation = switch (tier) {
                case ROYALTY -> com.jvn.villagerretaliation.config.VillagerRetaliationConfig.ROYALTY_THRESHOLD.get();
                case REVERED -> com.jvn.villagerretaliation.config.VillagerRetaliationConfig.REVERED_THRESHOLD.get();
                case RESPECTED -> com.jvn.villagerretaliation.config.VillagerRetaliationConfig.RESPECTED_THRESHOLD.get();
                case TRUSTED -> com.jvn.villagerretaliation.config.VillagerRetaliationConfig.TRUSTED_THRESHOLD.get();
                case SUSPICIOUS -> com.jvn.villagerretaliation.config.VillagerRetaliationConfig.SUSPICIOUS_THRESHOLD.get();
                case HOSTILE -> com.jvn.villagerretaliation.config.VillagerRetaliationConfig.HOSTILE_THRESHOLD.get();
                case DESPISED -> com.jvn.villagerretaliation.config.VillagerRetaliationConfig.DESPISED_THRESHOLD.get();
                case FEARED -> com.jvn.villagerretaliation.config.VillagerRetaliationConfig.FEARED_THRESHOLD.get();
                case NEUTRAL -> 0;
            };
            VillagerReputationManager.setReputation(initial.level(), initial.villager(), initial.player().getUUID(), reputation);
            var context = VillagerInteractionService.createDialogueContext(initial.level(), initial.player(), initial.villager());
            helper.assertValueEqual(context.reputationLevel(), tier, "the test exercises actual stored reputation");
            var pool = DialogueWordPoolResources.localizedPool(context.level().getServer(), context.locale(), poolId);
            var candidates = pool.candidates(rules -> DialogueCondition.matchesAll(context, rules));
            String previous = null;
            for (int attempt = 0; attempt < 6; attempt++) {
                String text = DialogueWordPoolResources.resolver(context.level().getServer(), context.locale(), context.random(), context)
                        .apply("{word:villagerretaliation:greetings/voice}");
                helper.assertTrue(candidates.entries().stream().anyMatch(entry -> pool.format(entry.text()).equals(text)),
                        "salutations respect the current reputation");
                helper.assertFalse(text.equals(previous), "the same salutation is not immediately repeated");
                previous = text;
            }
            var greeting = DialogueTreeResources.startLineEntry(context, OWNER).orElseThrow();
            assertProfessionGreeting(helper, context, greeting.exchanges().getLast().text(),
                    ResourceLocation.parse("villagerretaliation:greetings/professions/unemployed/ordinary"));
            assertResolved(helper, choose(context, "ask_wellbeing"));
            DialogueTreeService.leaveActiveSession(context);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "greeting_content")
    public static void professionChildAndContextualGreetingsKeepTheirExistingOwners(GameTestHelper helper) {
        var context = context(helper);
        for (var line : VillagerDialogueResources.lines(context)) {
            boolean ordinary = line.id().equals(OWNER) || line.requestType() == DialogueRequestType.GREETING
                    && line.source().getPath().contains("/professions/") && line.source().getPath().endsWith("/lines/greetings/ordinary.json");
            helper.assertValueEqual(DialogueTreeResources.hasLineEntry(context, line.id()), ordinary,
                    "only ordinary shared and profession greetings use the scene: " + line.id());
        }
        helper.assertTrue(DialogueTreeResources.startLineEntry(context, "missing.greeting").isEmpty(), "unbound replies retain their normal fallback");
        context.villager().setAge(-1000);
        var child = VillagerInteractionService.createDialogueContext(context.level(), context.player(), context.villager());
        helper.assertFalse(DialogueTreeResources.hasLineEntry(child, OWNER), "adult exchanges are unavailable to children");
        helper.assertTrue(VillagerDialogueResources.dialogueOption(child, "baby_greeting").isPresent(), "the child menu remains intact");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "greeting_content")
    public static void allVanillaProfessionsKeepTheSharedFlowAndSpeakTheirOwnWording(GameTestHelper helper) {
        var initial = context(helper);
        com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker.rememberDialogue(
                initial.level(), initial.villager(), initial.player(), DialogueRequestType.GREETING, OWNER);
        var questions = new java.util.HashSet<String>();
        for (String profession : List.of("armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman", "fletcher",
                "leatherworker", "librarian", "mason", "nitwit", "shepherd", "toolsmith", "unemployed", "weaponsmith")) {
            var type = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION
                    .get(ResourceLocation.withDefaultNamespace(profession.equals("unemployed") ? "none" : profession));
            initial.villager().setVillagerData(initial.villager().getVillagerData().setProfession(type));
            var context = VillagerInteractionService.createDialogueContext(initial.level(), initial.player(), initial.villager());
            var menu = VillagerDialogueResources.dialogueOptions(context, VillagerDialogueService.moodFor(context));
            helper.assertValueEqual(menu.stream().filter(option -> option.requestType() == DialogueRequestType.GREETING).count(), 1L,
                    "one Greet option for " + profession);
            var owner = VillagerDialogueResources.lines(context).stream()
                    .filter(line -> line.requestType() == DialogueRequestType.GREETING && line.professions().contains(type))
                    .findFirst().orElseThrow();
            var poolId = ResourceLocation.parse("villagerretaliation:greetings/professions/" + profession + "/ordinary");
            var opening = VillagerDialogueResources.openingSpeech(context, VillagerDialogueService.moodFor(context)).orElseThrow();
            assertProfessionGreeting(helper, context, opening.text(),
                    ResourceLocation.parse("villagerretaliation:greetings/professions/" + profession + "/opening"));
            for (String entry : List.of(owner.id(), OWNER)) {
                for (String finalReply : List.of("return_greeting", "brief_hello", "player_well", "player_steady", "player_struggling", "player_private")) {
                    var greeting = DialogueTreeResources.startLineEntry(context, entry).orElseThrow();
                    assertProfessionGreeting(helper, context, greeting.exchanges().getLast().text(), poolId);
                    assertResolved(helper, greeting);
                    if (finalReply.startsWith("player_")) {
                        var question = response(context, "ask_wellbeing");
                        questions.add(question.label());
                        var answer = DialogueTreeService.handleDialogueOption(context, question).orElseThrow();
                        helper.assertValueEqual(answer.exchanges().getFirst().text(), question.label(), "profession questions are spoken exactly");
                        helper.assertValueEqual(DialogueTreeService.activeOptions(context).orElseThrow().size(), 4, "four wellbeing choices");
                        assertResolved(helper, answer);
                    }
                    var choice = response(context, finalReply);
                    var result = DialogueTreeService.handleDialogueOption(context, choice).orElseThrow();
                    helper.assertValueEqual(result.exchanges().getFirst().text(), choice.label(), "player labels and speech stay synchronized");
                    helper.assertValueEqual(result.exchanges().size(), 2, "the player receives a villager answer");
                    assertResolved(helper, result);
                    helper.assertTrue(DialogueTreeService.activeOptions(context).isEmpty(), "each profession returns to the normal menu");
                }
            }
            String previous = null;
            for (int sample = 0; sample < 5; sample++) {
                String text = DialogueWordPoolResources.resolver(context.level().getServer(), context.locale(), context.random(), context)
                        .apply("{word:" + poolId + "}");
                helper.assertFalse(text.equals(previous), "profession wording avoids immediate repetition");
                previous = text;
            }
        }
        helper.assertValueEqual(questions.size(), 15, "every profession has its own wellbeing question");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "greeting_content")
    public static void professionWellbeingPreservesMoodInjuryAndQuietStyling(GameTestHelper helper) {
        var initial = context(helper);
        for (String profession : List.of("armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman", "fletcher",
                "leatherworker", "librarian", "mason", "nitwit", "shepherd", "toolsmith", "unemployed", "weaponsmith")) {
            var type = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION
                    .get(ResourceLocation.withDefaultNamespace(profession.equals("unemployed") ? "none" : profession));
            initial.villager().setVillagerData(initial.villager().getVillagerData().setProfession(type));
            var poolId = ResourceLocation.parse("villagerretaliation:greetings/professions/" + profession + "/wellbeing");
            for (var mood : List.of(VillagerMood.CONTENT, VillagerMood.STRESSED)) {
                initial.villager().setHealth(initial.villager().getMaxHealth());
                VillagerMoodService.setMood(initial.level(), initial.villager(), VillagerMood.NEUTRAL, 0, "greeting_test", null, null, 10000);
                VillagerMoodService.setMood(initial.level(), initial.villager(), mood, 60, "greeting_test", null, null, 10000);
                var context = VillagerInteractionService.createDialogueContext(initial.level(), initial.player(), initial.villager());
                var pool = DialogueWordPoolResources.localizedPool(context.level().getServer(), context.locale(), poolId);
                var candidates = pool.candidates(rules -> DialogueCondition.matchesAll(context, rules));
                var greeting = DialogueTreeResources.startLineEntry(context, OWNER).orElseThrow();
                assertProfessionGreeting(helper, context, greeting.exchanges().getLast().text(),
                        ResourceLocation.parse("villagerretaliation:greetings/professions/" + profession + "/ordinary"));
                var result = choose(context, "ask_wellbeing");
                helper.assertTrue(candidates.entries().stream().anyMatch(entry -> result.exchanges().getLast().text().startsWith(pool.format(entry.text()))),
                        "the profession wellbeing answer respects its actual mood");
                assertResolved(helper, result);
                DialogueTreeService.leaveActiveSession(context);
            }
            initial.villager().setHealth(initial.villager().getMaxHealth() / 4);
            VillagerMoodService.setMood(initial.level(), initial.villager(), VillagerMood.NEUTRAL, 0, "greeting_test", null, null, 10000);
            var injured = VillagerInteractionService.createDialogueContext(initial.level(), initial.player(), initial.villager());
            var pool = DialogueWordPoolResources.localizedPool(injured.level().getServer(), injured.locale(), poolId);
            var candidates = pool.candidates(rules -> DialogueCondition.matchesAll(injured, rules));
            helper.assertTrue(candidates.entries().stream().allMatch(entry -> entry.id().startsWith("injured_")), "injury overrides profession banter");
            var greeting = DialogueTreeResources.startLineEntry(injured, OWNER).orElseThrow();
            assertProfessionGreeting(helper, injured, greeting.exchanges().getLast().text(),
                    ResourceLocation.parse("villagerretaliation:greetings/professions/" + profession + "/ordinary"));
            var result = choose(injured, "ask_wellbeing");
            helper.assertTrue(result.exchanges().getLast().segments().stream().anyMatch(segment -> segment.effects().italic()),
                    "each profession preserves quiet styling when injured");
            DialogueTreeService.leaveActiveSession(injured);
        }
        helper.succeed();
    }

    private static void assertProfessionGreeting(GameTestHelper helper, DialogueContext context, String text, ResourceLocation bodyId) {
        var salutations = DialogueWordPoolResources.localizedPool(context.level().getServer(), context.locale(),
                ResourceLocation.parse("villagerretaliation:greetings/professions/salutations"));
        var body = DialogueWordPoolResources.localizedPool(context.level().getServer(), context.locale(), bodyId);
        var prefixes = salutations.candidates(rules -> DialogueCondition.matchesAll(context, rules));
        var endings = body.candidates(rules -> DialogueCondition.matchesAll(context, rules));
        helper.assertTrue(prefixes.entries().stream().anyMatch(prefix -> endings.entries().stream()
                        .anyMatch(ending -> text.equals(salutations.format(prefix.text()) + ", " + body.format(ending.text())))),
                "profession greetings join an eligible salutation and message with a comma");
    }

    private static DialogueOptionDefinition response(DialogueContext context, String id) {
        return DialogueTreeService.activeOptions(context).orElseThrow().stream()
                .filter(option -> option.treeReference().responseId().equals(id)).findFirst().orElseThrow();
    }

    private static VillagerDialogueService.DialogueResult choose(DialogueContext context, String id) {
        return DialogueTreeService.handleDialogueOption(context, response(context, id)).orElseThrow();
    }

    private static void assertResolved(GameTestHelper helper, VillagerDialogueService.DialogueResult result) {
        for (var speech : result.exchanges()) {
            helper.assertFalse(speech.text().isBlank(), "each speech is nonblank");
            helper.assertFalse(speech.text().contains("{") || speech.text().contains("<"), "pools, keys and styles resolve before delivery");
        }
    }

    private static DialogueContext context(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "HelloReader"));
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        villager.setNoAi(true);
        return VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
    }
}

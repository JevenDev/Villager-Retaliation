package com.jvn.villagerretaliation.dialogue.forced;

import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueContext;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueDefinition;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueFollowUp;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueItemPayment;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueOption;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueOutput;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueOutputMode;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueStolenItemReturn;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueTrigger;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.LocalizedText;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.SocialAttributeCondition;
import com.jvn.villagerretaliation.util.VillagerEquipmentCondition;
import com.jvn.villagerretaliation.util.VillagerPlayerItemCondition;
import com.jvn.villagerretaliation.util.VillagerReputationCondition;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_foundation")
@PrefixGameTestTemplate(false)
public final class ForcedDialogueRuntimeGameTests {
    private ForcedDialogueRuntimeGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_forced_runtime")
    public static void requestsRespectActorAndTimeoutBeforeCallingGameplay(GameTestHelper helper) {
        var player = player(helper);
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        var other = helper.spawn(EntityType.VILLAGER, 2, 1, 1);
        var selected = new ArrayList<String>();
        var runtime = runtime(selected);
        var definition = definition(List.of(option("continue", 0)), option("leave", 1));
        long now = helper.getLevel().getGameTime();
        runtime.put(player.getUUID(), session(helper, villager, definition, now - 20, List.of()));
        helper.assertFalse(runtime.handleRequest(player, other, "continue"), "another actor cannot consume the session");
        helper.assertTrue(runtime.hasSession(player, villager), "wrong actor leaves the session intact");
        helper.assertTrue(runtime.isStaleConversationEndRequest(player, other.getId()), "stale end targets are rejected");
        helper.assertTrue(runtime.handleRequest(player, villager, "continue"), "the timeout boundary remains inclusive");
        helper.assertValueEqual(selected, List.of("continue:false:false"), "only the matching live request reaches gameplay");
        runtime.put(player.getUUID(), session(helper, villager, definition, now - 21, List.of()));
        helper.assertFalse(runtime.handleRequest(player, villager, "continue"), "expired ordinary requests are not executed");
        helper.assertFalse(runtime.containsKey(player.getUUID()), "expired sessions are removed");
        helper.assertValueEqual(selected.size(), 1, "expiration cannot execute a gameplay effect");
        villager.discard();
        other.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_forced_runtime")
    public static void hiddenAndDisabledOptionsCannotReachGameplay(GameTestHelper helper) {
        var player = player(helper);
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        var selected = new ArrayList<String>();
        var runtime = runtime(selected);
        var unavailable = option("conditional", 0, List.of(new DialogueCondition.Invalid("fixture unavailable")));
        var definition = definition(List.of(unavailable, option("disabled", 1), option("z", 2), option("a", 2)), option("leave", 3));
        var session = session(helper, villager, definition, helper.getLevel().getGameTime(), List.of("disabled"));
        runtime.put(player.getUUID(), session);
        helper.assertValueEqual(runtime.options(definition, helper.getLevel(), villager, player, session).stream()
                .map(DialogueOptionDefinition::id).toList(), List.of("a", "z"),
                "presentation filters conditions and disabled choices before stable sorting");
        helper.assertTrue(runtime.handleRequest(player, villager, "conditional"), "unavailable option intent is handled");
        helper.assertTrue(selected.isEmpty(), "unavailable conditions stop gameplay");
        helper.assertFalse(runtime.containsKey(player.getUUID()), "invalid selections close the old session");
        runtime.put(player.getUUID(), session);
        helper.assertTrue(runtime.handleRequest(player, villager, "disabled"), "disabled option intent is handled");
        helper.assertTrue(selected.isEmpty(), "disabled options stop gameplay");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_forced_runtime")
    public static void leaveRoutingRetainsFallbackAndExplicitEndSemantics(GameTestHelper helper) {
        var player = player(helper);
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        var selected = new ArrayList<String>();
        var runtime = runtime(selected);
        var definition = definition(List.of(option("continue", 0)), option("fallback", 1));
        var session = session(helper, villager, definition, helper.getLevel().getGameTime() - 21, List.of());
        runtime.put(player.getUUID(), session);
        helper.assertTrue(runtime.handleLeaveRequest(player, villager, true), "explicit leave retains its cleanup path after expiry");
        helper.assertValueEqual(selected, List.of("fallback:true:true"), "leave forwards the authored fallback and end flags");
        runtime.put(player.getUUID(), session(helper, villager, definition, helper.getLevel().getGameTime(), List.of()));
        helper.assertTrue(runtime.handleRequest(player, villager, "leave"), "the leave option uses leave routing");
        helper.assertValueEqual(selected.getLast(), "fallback:false:true", "ordinary leave does not force the next conversation to end");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_forced_runtime")
    public static void gameplayPolicyControlsLeaveAndPresentationWithoutOwningRouting(GameTestHelper helper) {
        var player = player(helper);
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        var selected = new ArrayList<String>();
        var runtime = new ForcedDialogueRuntime<TestSession>(20, (session, option) -> !option.id().equals("blocked"),
                (requester, speaker, session, option, forceEnd, leave) -> { selected.add(option.id()); return true; });
        var definition = definition(List.of(option("blocked", 0), option("allowed", 1)), option("blocked", 0));
        var session = session(helper, villager, definition, helper.getLevel().getGameTime(), List.of());
        runtime.put(player.getUUID(), session);
        helper.assertValueEqual(runtime.options(definition, helper.getLevel(), villager, player, session).stream()
                .map(DialogueOptionDefinition::id).toList(), List.of("allowed"), "gameplay can suppress resolved choices");
        helper.assertTrue(runtime.handleRequest(player, villager, "blocked"), "hidden gameplay options cannot be submitted directly");
        helper.assertTrue(selected.isEmpty(), "a forged hidden choice cannot reach gameplay");
        runtime.put(player.getUUID(), session);
        helper.assertTrue(runtime.handleLeaveRequest(player, villager, false), "a suppressed fallback still closes cleanly");
        helper.assertTrue(selected.isEmpty(), "a resolved gameplay fallback is not repeated");
        helper.assertFalse(runtime.containsKey(player.getUUID()), "the runtime owns session cleanup");
        villager.discard();
        helper.succeed();
    }

    private static ForcedDialogueRuntime<TestSession> runtime(List<String> selected) {
        return new ForcedDialogueRuntime<>(20, (session, option) -> true,
                (player, villager, session, option, forceEnd, leave) -> {
                    selected.add(option.id() + ":" + forceEnd + ":" + leave);
                    return true;
                });
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "forced-runtime"));
    }

    private static TestSession session(GameTestHelper helper, Villager villager,
            ForcedDialogueDefinition definition, long started, List<String> disabled) {
        var context = new ForcedDialogueContext("Witness", "Player", "", "", "", "", "", 0,
                "", "", "", "", 0, 0, 0, 0, 0, helper.getLevel().getServer(), helper.getLevel().getRandom(), "en_us");
        return new TestSession(villager.getUUID(), definition, context, started, disabled);
    }

    private static ForcedDialogueOption option(String id, int order) {
        return option(id, order, List.of());
    }

    private static ForcedDialogueOption option(String id, int order, List<DialogueCondition> conditions) {
        return new ForcedDialogueOption(id, LocalizedText.inline(id), List.of(), 0, false, 0, false, order,
                ForcedDialogueStolenItemReturn.empty(), ForcedDialogueItemPayment.empty(),
                VillagerReputationCondition.empty(), SocialAttributeCondition.EMPTY, conditions, ForcedDialogueFollowUp.empty());
    }

    private static ForcedDialogueDefinition definition(List<ForcedDialogueOption> options, ForcedDialogueOption leave) {
        return new ForcedDialogueDefinition("fixture", null, ForcedDialogueTrigger.QUEST,
                new ForcedDialogueOutput(ForcedDialogueOutputMode.FORCED_DIALOGUE, 0, false), List.of(),
                true, false, true, false, 0, 1, 0, 0, 1, 0, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE,
                Set.of(), Set.of(), Set.of(), 1, 5, false, false, 0, false,
                VillagerEquipmentCondition.empty(), VillagerPlayerItemCondition.empty(), VillagerReputationCondition.empty(),
                0, options, leave, List.of(leave), null, List.of());
    }

    private record TestSession(UUID villagerId, ForcedDialogueDefinition definition, ForcedDialogueContext context,
            long startedGameTime, List<String> disabledOptionIds) implements ForcedDialogueRuntime.Session {
    }
}

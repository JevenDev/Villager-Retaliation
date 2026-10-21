package com.jvn.villagerretaliation.interaction;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("narrative_interaction")
@PrefixGameTestTemplate(false)
public final class EntityInteractionGameTests {
    private EntityInteractionGameTests() { }

    @GameTest(template = "empty", batch = "interaction_lifecycle")
    public static void sessionsEnforceOneOwnerAndRetireReplacedIdentities(GameTestHelper helper) {
        var first = helper.makeMockServerPlayerInLevel();
        var second = helper.makeMockServerPlayerInLevel();
        var cow = helper.spawn(EntityType.COW, 1, 1, 1);
        var wolf = helper.spawn(EntityType.WOLF, 2, 1, 1);
        var policy = new Policy();
        try {
            var original = InteractionSessions.start(first, cow, policy, InteractionSessions.StartMode.NORMAL).orElseThrow();
            helper.assertTrue(InteractionSessions.start(second, cow, policy, InteractionSessions.StartMode.NORMAL).isEmpty(),
                    "another player cannot claim an occupied actor");
            helper.assertFalse(InteractionSessions.validate(second, cow, original.id()), "session identity does not grant authority to another player");
            helper.assertTrue(InteractionSessions.validate(first, cow, original.id()), "the owner retains its original interaction");
            var replaced = InteractionSessions.start(first, wolf, policy, InteractionSessions.StartMode.NORMAL).orElseThrow();
            helper.assertFalse(original.active(), "switching actors deactivates the old session");
            helper.assertValueEqual(policy.closed, List.of(original.id()), "replacement closes the original session once");
            helper.assertFalse(InteractionSessions.isConversing(cow), "switching actors releases the first actor");
            helper.assertTrue(InteractionSessions.start(second, cow, policy, InteractionSessions.StartMode.NORMAL).isPresent(),
                    "a released actor can accept a different player");
            helper.assertFalse(InteractionSessions.validate(first, wolf, original.id()), "old session identities cannot act on a replacement session");
            helper.assertTrue(InteractionSessions.validate(first, wolf, replaced.id()), "a stale request leaves the replacement session intact");
        } finally {
            InteractionSessions.endForPlayer(first, false, false);
            InteractionSessions.endForPlayer(second, false, false);
            cow.discard();
            wolf.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "interaction_lifecycle")
    public static void activityAndTimeoutsRequireTheCurrentActorAndSession(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var cow = helper.spawn(EntityType.COW, 1, 1, 1);
        var other = helper.spawn(EntityType.COW, 2, 1, 1);
        var policy = new Policy();
        policy.timeout = 10;
        try {
            var session = InteractionSessions.start(player, cow, policy, InteractionSessions.StartMode.NORMAL).orElseThrow();
            long now = helper.getLevel().getGameTime();
            session.touch(now - 5);
            InteractionSessions.recordActivity(player, other.getId());
            helper.assertValueEqual(session.lastInteractionGameTime(), now - 5, "unrelated entity activity cannot extend the session");
            helper.assertFalse(InteractionSessions.validate(player, cow, UUID.randomUUID()), "a forged session cannot send activity");
            helper.assertValueEqual(session.lastInteractionGameTime(), now - 5, "forged activity leaves the timeout unchanged");
            InteractionSessions.recordActivity(player, cow.getId());
            helper.assertValueEqual(session.lastInteractionGameTime(), now, "authorized activity refreshes the existing session");
            session.touch(now - 11);
            helper.assertFalse(InteractionSessions.validate(player, cow, session.id()), "idle sessions expire before processing a request");
            helper.assertTrue(policy.notified && !session.active(), "expiry notifies the adapter after retiring the session");
            helper.assertValueEqual(policy.closed, List.of(session.id()), "expiry closes the session exactly once");
        } finally {
            InteractionSessions.endForPlayer(player, false, false);
            cow.discard();
            other.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "interaction_lifecycle")
    public static void actorRemovalAndInvalidationReleaseBothSides(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var cow = helper.spawn(EntityType.COW, 1, 1, 1);
        var policy = new Policy();
        try {
            var session = InteractionSessions.start(player, cow, policy, InteractionSessions.StartMode.FORCED).orElseThrow();
            helper.assertTrue(InteractionSessions.isForced(player, cow), "forced state belongs to an arbitrary living actor");
            policy.allowed = false;
            InteractionSessions.tick(cow);
            helper.assertFalse(session.active() || InteractionSessions.isConversing(player) || InteractionSessions.isConversing(cow),
                    "adapter invalidation releases the player and actor");
            policy.allowed = true;
            session = InteractionSessions.start(player, cow, policy, InteractionSessions.StartMode.NORMAL).orElseThrow();
            InteractionSessions.endForEntityLeaving(cow, true);
            helper.assertFalse(session.active() || InteractionSessions.isConversing(player), "actor departure closes its session");
            helper.assertValueEqual(policy.closed.size(), 2, "each lifecycle ends once");
        } finally {
            InteractionSessions.endForPlayer(player, false, false);
            cow.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "interaction_lifecycle")
    public static void vrAndGenericInteractionsShareTheSameOwnershipRules(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var stranger = helper.makeMockServerPlayerInLevel();
        var cow = helper.spawn(EntityType.COW, 1, 1, 1);
        var villager = helper.spawn(EntityType.VILLAGER, 2, 1, 1);
        villager.setNoAi(true);
        player.teleportTo(cow.getX(), cow.getY(), cow.getZ());
        stranger.teleportTo(cow.getX(), cow.getY(), cow.getZ());
        var policy = new Policy();
        try {
            var original = InteractionSessions.start(player, cow, policy, InteractionSessions.StartMode.NORMAL).orElseThrow();
            helper.assertTrue(VillagerConversationService.start(player, villager), "VR opens through the shared session engine");
            helper.assertFalse(original.active(), "a VR conversation retires the player's prior generic interaction");
            helper.assertTrue(VillagerConversationService.validate(player, villager), "VR keeps its existing validation contract");
            helper.assertTrue(InteractionSessions.start(stranger, villager, policy, InteractionSessions.StartMode.NORMAL).isEmpty(),
                    "generic callers cannot bypass ownership of an active VR actor");
            var legacy = new VillagerConversationSession(player.getUUID(), villager.getUUID(), villager.getId(),
                    helper.getLevel().dimension(), villager.blockPosition(), helper.getLevel().getGameTime(), false);
            helper.assertValueEqual(legacy.actorId(), legacy.villagerId(), "legacy session accessors retain actor identity");
            helper.assertValueEqual(legacy.entityId(), legacy.villagerEntityId(), "legacy session accessors retain runtime identity");
        } finally {
            InteractionSessions.endForPlayer(player, false, false);
            InteractionSessions.endForPlayer(stranger, false, false);
            cow.discard();
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "interaction_lifecycle")
    public static void lifecycleCallbacksCannotOverwriteAReentrantSession(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var cow = helper.spawn(EntityType.COW, 1, 1, 1);
        var wolf = helper.spawn(EntityType.WOLF, 2, 1, 1);
        var replacementPolicy = new Policy();
        var policy = new Policy() {
            @Override public void ended(ServerPlayer owner, InteractionSession session, boolean notifyClient, boolean finishExchanges) {
                super.ended(owner, session, notifyClient, finishExchanges);
                InteractionSessions.start(owner, wolf, replacementPolicy, InteractionSessions.StartMode.NORMAL).orElseThrow();
            }
        };
        try {
            InteractionSessions.start(player, cow, policy, InteractionSessions.StartMode.NORMAL).orElseThrow();
            helper.assertTrue(InteractionSessions.start(player, cow, replacementPolicy, InteractionSessions.StartMode.NORMAL).isEmpty(),
                    "a replacement that loses ownership during a lifecycle callback cannot overwrite the callback's session");
            helper.assertValueEqual(InteractionSessions.current(player).orElseThrow().actorId(), wolf.getUUID(),
                    "the callback's session retains the player");
            helper.assertFalse(InteractionSessions.isConversing(cow), "the interrupted replacement leaves no stale actor reservation");
        } finally {
            InteractionSessions.endForPlayer(player, false, false);
            cow.discard();
            wolf.discard();
        }
        helper.succeed();
    }

    private static class Policy implements InteractionSessionPolicy {
        private boolean allowed = true;
        private long timeout = 2400;
        private final List<UUID> closed = new ArrayList<>();
        private boolean notified;

        @Override public boolean canStart(ServerPlayer player, LivingEntity actor, InteractionSessions.StartMode mode) { return this.allowed; }
        @Override public boolean valid(ServerPlayer player, LivingEntity actor, InteractionSession session) { return this.allowed; }
        @Override public long idleTimeout(ServerPlayer player, LivingEntity actor, InteractionSession session) { return this.timeout; }
        @Override public void ended(ServerPlayer player, InteractionSession session, boolean notifyClient, boolean finishExchanges) {
            this.closed.add(session.id());
            this.notified |= notifyClient;
        }
    }
}

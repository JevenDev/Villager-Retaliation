package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.allegiance.*;
import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.combat.VillagerRetaliationRetaliationUtil;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.diplomacy.*;
import com.jvn.villagerretaliation.interaction.VillagerContractTime;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("village_diplomacy")
@PrefixGameTestTemplate(false)
public final class VillageDiplomacyCombatGameTests {
    @GameTest(template = "empty")
    public static void neutralHostileAndTruceRespectCombatContexts(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var neutral = decision(f, f.target, AllegianceCombatContext.PARTY_ATTACK, false);
            helper.assertValueEqual(neutral.reason(), AllegianceCombatDecision.Reason.NO_PARTY_AUTHORIZATION,
                    "neutral retains existing non-party order denial");
            VillageDiplomacyApi.set(f.level, f.home, f.foreign, VillageRelation.HOSTILE);
            for (var context : AllegianceCombatContext.values()) {
                if (context == AllegianceCombatContext.DISCIPLINE) continue;
                var hostile = decision(f, f.target, context, false);
                helper.assertValueEqual(hostile.reason(), AllegianceCombatDecision.Reason.HOSTILE_VILLAGES,
                        "hostility allows existing context " + context);
                helper.assertFalse(hostile.requiresPartyAuthorization(), "diplomacy never requires a party token");
            }
            helper.assertTrue(VillagerRetaliationRetaliationUtil.tryAnger(f.actor, f.target,
                    new HashMap<>(), "DiplomacyTest"), "hostility enables ordinary civilian retaliation");
            VillageDiplomacyApi.truce(f.level, f.home, f.foreign, 0);
            for (var context : new AllegianceCombatContext[]{AllegianceCombatContext.PARTY_ATTACK,
                    AllegianceCombatContext.PARTY_DEFEND, AllegianceCombatContext.CUSTOM_TARGET,
                    AllegianceCombatContext.TARGET_CONTINUATION, AllegianceCombatContext.DAMAGE,
                    AllegianceCombatContext.ORDINARY_RETALIATION}) {
                helper.assertValueEqual(decision(f, f.target, context, true).reason(),
                        AllegianceCombatDecision.Reason.VILLAGE_TRUCE, "truce overrides authorization in " + context);
            }
            helper.assertFalse(VillagerRetaliationRetaliationUtil.tryAnger(f.actor, f.target,
                    new HashMap<>(), "DiplomacyTest"), "truce blocks ordinary anger");
            helper.assertFalse(VillagerRetaliationHandler.canEngageCustomTarget(f.actor, f.target), "truce prevents custom engagement");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void partyAndCommunityProtectionsOutrankHostility(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            VillageDiplomacyApi.set(f.level, f.home, f.foreign, VillageRelation.HOSTILE);
            var parties = PartySavedData.get(f.level);
            var actorParty = parties.createParty(UUID.randomUUID(), 0);
            var targetParty = parties.createParty(UUID.randomUUID(), 0);
            parties.addVillager(actorParty, member(f.actor, actorParty, f.level.getGameTime()));
            parties.addVillager(targetParty, member(f.target, targetParty, f.level.getGameTime()));
            try {
                actorParty.addAlliance(targetParty.id());
                targetParty.addAlliance(actorParty.id());
                helper.assertValueEqual(decision(f, f.target, AllegianceCombatContext.DAMAGE, true).reason(),
                        AllegianceCombatDecision.Reason.SAME_PARTY, "allied parties protect foreign residents");
                targetParty.removeAlliance(actorParty.id());
                actorParty.removeAlliance(targetParty.id());
                parties.removeVillager(targetParty, f.target.getUUID());
                parties.addVillager(actorParty, member(f.target, actorParty, f.level.getGameTime()));
                helper.assertValueEqual(decision(f, f.target, AllegianceCombatContext.DAMAGE, true).reason(),
                        AllegianceCombatDecision.Reason.SAME_PARTY, "same party protects foreign residents");
                parties.removeVillager(actorParty, f.target.getUUID());
                f.target.setAge(-24000);
                VillageAllegianceApi.assign(f.target, VillageAllegianceData.known(f.foreign, AllegianceAssignmentSource.BIRTH,
                        AllegianceConfidence.INHERITED, 0, f.level.dimension().location(), f.target.blockPosition(), List.of(f.home)));
                helper.assertValueEqual(decision(f, f.target, AllegianceCombatContext.DAMAGE, true).reason(),
                        AllegianceCombatDecision.Reason.SAME_CANONICAL_ALLEGIANCE, "inherited community protection wins");
                f.target.setAge(0);
                VillageAllegianceApi.assignKnown(f.level, f.target, f.home, AllegianceAssignmentSource.ADMIN);
                helper.assertValueEqual(decision(f, f.target, AllegianceCombatContext.DAMAGE, true).reason(),
                        AllegianceCombatDecision.Reason.SAME_CANONICAL_ALLEGIANCE, "same identity remains protected");
                VillageAllegianceApi.assign(f.target, VillageAllegianceData.unknown(AllegianceAssignmentSource.ADMIN,
                        AllegianceConfidence.AUTHORITATIVE, 0, f.level.dimension().location(), f.target.blockPosition()));
                helper.assertValueEqual(decision(f, f.target, AllegianceCombatContext.DAMAGE, true).reason(),
                        AllegianceCombatDecision.Reason.UNKNOWN_TARGET, "unknown target cannot become a diplomatic enemy");
            } finally {
                parties.removeParty(actorParty.id());
                parties.removeParty(targetParty.id());
            }
            var trader = EntityType.WANDERING_TRADER.create(f.level);
            var golem = EntityType.IRON_GOLEM.create(f.level);
            try {
                VillageAllegianceApi.assignKnown(f.level, trader, f.foreign, AllegianceAssignmentSource.ADMIN);
                VillageAllegianceApi.assignKnown(f.level, golem, f.foreign, AllegianceAssignmentSource.ADMIN);
                helper.assertValueEqual(decision(f, trader, AllegianceCombatContext.DAMAGE, false).reason(),
                        AllegianceCombatDecision.Reason.NEUTRAL_TRADER, "trader protection wins");
                helper.assertValueEqual(decision(f, golem, AllegianceCombatContext.PARTY_ATTACK, false).reason(),
                        AllegianceCombatDecision.Reason.GOLEM_RESTRICTED, "golem order restriction wins");
            } finally {
                trader.discard();
                golem.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void newTruceStopsAuthorizedProjectileDamageAndPreservesPlayerExceptions(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var parties = PartySavedData.get(f.level);
            var party = parties.createParty(UUID.randomUUID(), 0);
            parties.addVillager(party, member(f.actor, party, f.level.getGameTime()));
            var arrow = EntityType.ARROW.create(f.level);
            arrow.setOwner(f.actor);
            try {
                helper.assertTrue(VillageCombatAuthorizationService.authorize(f.level, f.actor, f.target), "neutral party conflict authorized");
                VillageCombatAuthorizationService.associateProjectile(arrow, f.actor, f.target);
                VillageDiplomacyApi.truce(f.level, f.home, f.foreign, 0);
                helper.assertTrue(VillageCombatAuthorizationService.projectileAuthorized(arrow, f.actor, f.target), "cached token still exists");
                var damage = new LivingIncomingDamageEvent(f.target, new DamageContainer(f.level.damageSources().arrow(arrow, f.actor), 4));
                VillagerRetaliationHandler.onLivingDamagePre(damage);
                helper.assertValueEqual(damage.getAmount(), 0F, "truce rechecked at projectile impact");
                helper.assertFalse(VillagerRetaliationHandler.canEngageCustomTarget(f.actor, f.target), "cached authorization cannot continue targeting");
                helper.assertValueEqual(decision(f, f.target, AllegianceCombatContext.DISCIPLINE, true).reason(),
                        AllegianceCombatDecision.Reason.DISCIPLINARY_RESPONSE, "discipline exception retained");
                var player = helper.makeMockServerPlayerInLevel();
                helper.assertValueEqual(VillageAllegianceCombatPolicy.evaluate(f.level, player, f.target,
                        AllegianceCombatContext.DIRECT_PLAYER_DAMAGE, false).reason(),
                        AllegianceCombatDecision.Reason.DIRECT_PLAYER_DAMAGE, "player damage remains independent");
            } finally {
                VillageCombatAuthorizationService.clearFor(arrow);
                arrow.discard();
                parties.removeParty(party.id());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void commandsValidatePermissionsIdsAndTruceDurations(GameTestHelper helper) throws Exception {
        try (var f = new Fixture(helper)) {
            var dispatcher = f.level.getServer().getCommands().getDispatcher();
            var source = f.level.getServer().createCommandSourceStack().withPermission(2).withSuppressedOutput();
            String pair = f.home + " " + f.foreign;
            helper.assertValueEqual(dispatcher.execute("vr admin diplomacy set " + pair + " hostile", source), 1, "set command");
            helper.assertValueEqual(dispatcher.execute("vr admin diplomacy get " + pair, source), 1, "query command");
            helper.assertValueEqual(dispatcher.execute("vr admin diplomacy truce " + pair + " 10", source), 1, "timed command");
            helper.assertValueEqual(VillageDiplomacyApi.query(f.level, f.home, f.foreign).expiresAt().orElseThrow(),
                    f.level.getServer().overworld().getGameTime() + 10, "duration uses server clock");
            helper.assertValueEqual(dispatcher.execute("vr admin diplomacy truce " + pair + " indefinite", source), 1, "indefinite command");
            helper.assertTrue(VillageDiplomacyApi.query(f.level, f.home, f.foreign).expiresAt().isEmpty(), "indefinite override");
            int previous = VillagerRetaliationConfig.DEFAULT_TRUCE_DURATION_TICKS.get();
            try {
                VillagerRetaliationConfig.DEFAULT_TRUCE_DURATION_TICKS.set(20);
                dispatcher.execute("vr admin diplomacy truce " + pair, source);
                helper.assertValueEqual(VillageDiplomacyApi.query(f.level, f.home, f.foreign).expiresAt().orElseThrow(),
                        f.level.getServer().overworld().getGameTime() + 20, "configured default");
            } finally {
                VillagerRetaliationConfig.DEFAULT_TRUCE_DURATION_TICKS.set(previous);
            }
            helper.assertValueEqual(dispatcher.execute("vr admin diplomacy set " + f.home + " " + UUID.randomUUID() + " hostile", source),
                    0, "unknown UUID rejected");
            helper.assertValueEqual(dispatcher.execute("vr admin diplomacy get " + f.home + " " + f.home, source), 0, "same village rejected");
            for (String invalid : new String[]{"0", "-1", "nonsense"}) {
                helper.assertFalse(dispatcher.parse("vr admin diplomacy truce " + pair + " " + invalid, source).getExceptions().isEmpty(),
                        "invalid duration rejected: " + invalid);
            }
            helper.assertTrue(dispatcher.parse("vr admin diplomacy get " + pair, source.withPermission(0)).getReader().canRead(),
                    "operator permission required");
        }
        helper.succeed();
    }

    private static AllegianceCombatDecision decision(Fixture f, LivingEntity target, AllegianceCombatContext context, boolean authorized) {
        return VillageAllegianceCombatPolicy.evaluate(f.level, f.actor, target, context, authorized);
    }

    private static PartyVillagerRecord member(Villager villager, PartyRecord party, long now) {
        return new PartyVillagerRecord(villager.getUUID(), party.leaderId(), UUID.randomUUID(), 0,
                PartyCommandMode.FOLLOW, null, null, now, VillagerContractTime.endAfterDays(now, 1),
                1, 32, "Diplomacy resident", "minecraft:none", net.minecraft.world.level.Level.OVERWORLD.location(), BlockPos.ZERO);
    }

    private static final class Fixture implements AutoCloseable {
        private final ServerLevel level;
        private final Villager actor;
        private final Villager target;
        private final VillageAllegianceId home;
        private final VillageAllegianceId foreign;

        private Fixture(GameTestHelper helper) {
            this.level = helper.getLevel();
            var registry = VillageAllegianceRegistrySavedData.get(this.level);
            this.home = registry.create(0, this.level.dimension().location(), helper.absolutePos(new BlockPos(2, 2, 2)), "");
            this.foreign = registry.create(0, this.level.dimension().location(), helper.absolutePos(new BlockPos(4, 2, 2)), "");
            this.actor = spawn(helper, new BlockPos(2, 2, 2), this.home);
            this.target = spawn(helper, new BlockPos(4, 2, 2), this.foreign);
        }

        private Villager spawn(GameTestHelper helper, BlockPos offset, VillageAllegianceId home) {
            var villager = EntityType.VILLAGER.create(this.level);
            villager.setPos(helper.absolutePos(offset).getBottomCenter());
            villager.setNoAi(true);
            VillageAllegianceApi.assignKnown(this.level, villager, home, AllegianceAssignmentSource.ADMIN);
            this.level.addFreshEntity(villager);
            return villager;
        }

        @Override
        public void close() {
            VillageCombatAuthorizationService.clearFor(this.actor);
            VillageCombatAuthorizationService.clearFor(this.target);
            this.actor.discard();
            this.target.discard();
        }
    }
}

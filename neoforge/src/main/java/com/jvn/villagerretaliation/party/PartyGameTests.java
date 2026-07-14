package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.allegiance.VillagerAbuseSavedData;
import com.jvn.villagerretaliation.allegiance.VillagerDisciplineService;
import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.combat.VillagerRetaliationRetaliationUtil;
import com.jvn.villagerretaliation.combat.VillagerCombatLoadoutService;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.VillagerContractTime;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.interaction.VillagerWalletService;
import com.jvn.villagerretaliation.interaction.work.HiredRangedAmmo;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.VillagerInventoryMenu;
import com.jvn.villagerretaliation.mixin.AbstractArrowAccessor;
import com.jvn.villagerretaliation.quest.PartyQuestService;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestFactScope;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
import com.jvn.villagerretaliation.village.VillageMembership;
import com.jvn.villagerretaliation.village.VillageScopeKeys;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Container;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.util.Unit;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class PartyGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    static {
        configureGameTestStructures();
    }

    private PartyGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void quickCommandStatePersistsWeaponPreferenceAndRegroupSuppression(GameTestHelper helper) {
        long now = helper.getLevel().getGameTime();
        PartyVillagerRecord record = villagerRecord(UUID.randomUUID(), UUID.randomUUID(), 0, now);
        helper.assertValueEqual(record.weaponPreference(), PartyWeaponPreference.AUTO,
                "legacy/default weapon preference should be AUTO");
        record.setWeaponPreference(PartyWeaponPreference.RANGED);
        record.setRegrouping(true);

        PartyVillagerRecord loaded = PartyVillagerRecord.load(record.save());
        helper.assertValueEqual(loaded.weaponPreference(), PartyWeaponPreference.RANGED,
                "weapon preference should survive party record serialization");
        helper.assertTrue(loaded.regrouping(),
                "regroup acquisition suppression should survive unload/reload");

        CompoundTag legacy = record.save();
        legacy.remove("WeaponPreference");
        legacy.remove("Regrouping");
        PartyVillagerRecord legacyLoaded = PartyVillagerRecord.load(legacy);
        helper.assertValueEqual(legacyLoaded.weaponPreference(), PartyWeaponPreference.AUTO,
                "missing legacy preference should migrate to AUTO");
        helper.assertFalse(legacyLoaded.regrouping(),
                "legacy records should not begin with target suppression");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void rangeAndMeleeCommandsEquipWeaponsOutsideCombat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer leader = fakePlayer(level, uniqueName("party_idle_loadout"));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        long now = level.getGameTime();
        PartyRecord party = PartySavedData.get(level).createParty(leader.getUUID(), now);
        PartySavedData.get(level).addVillager(
                party, villagerRecord(villager.getUUID(), leader.getUUID(), 0, now));
        HiredJobInventory jobInventory = HiredJobInventory.getJobInventory(villager);
        jobInventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_SWORD));
        jobInventory.setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.BOW));
        jobInventory.markPlayerPlacedSupply(HiredJobInventory.HOTBAR_START);
        jobInventory.setItem(HiredJobInventory.HOTBAR_START + 1, new ItemStack(Items.ARROW, 4));
        jobInventory.markPlayerPlacedSupply(HiredJobInventory.HOTBAR_START + 1);

        PartyQuickCommandService.handle(leader, new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                PartyQuickCommand.MELEE));
        helper.assertTrue(com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons
                        .isMeleeWeapon(villager.getMainHandItem()),
                "melee should equip a carried melee weapon immediately while idle");
        VillagerRetaliationHandler.clearCustomTarget(villager);
        helper.assertTrue(com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons
                        .isMeleeWeapon(villager.getMainHandItem()),
                "idle combat cleanup must retain the selected melee loadout");

        PartyQuickCommandService.handle(leader, new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                PartyQuickCommand.RANGE));
        helper.assertTrue(com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons
                        .isBowWeapon(villager.getMainHandItem()),
                "range should swap immediately to a carried bow while idle");
        VillagerRetaliationHandler.clearCustomTarget(villager);
        helper.assertTrue(com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons
                        .isBowWeapon(villager.getMainHandItem()),
                "idle combat cleanup must retain the selected ranged loadout");

        PartyQuickCommandService.handle(leader, new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                PartyQuickCommand.MELEE));
        helper.assertTrue(com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons
                        .isMeleeWeapon(villager.getMainHandItem()),
                "melee should re-equip the stored sword after switching to range");
        VillagerRetaliationHandler.clearCustomTarget(villager);
        helper.assertTrue(com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons
                        .isMeleeWeapon(villager.getMainHandItem()),
                "idle combat cleanup must retain melee after switching back from range");

        PartyService.deleteParty(level, party.id());
        PartyQuickCommandService.clearRuntimeState();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void partyLimitsOrderingAndIndexesSurviveSerialization(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID leader = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        UUID fourth = UUID.randomUUID();
        UUID rejectedPlayer = UUID.randomUUID();
        long now = 1_000L;

        helper.assertTrue(PartyAttackMode.ANIMALS.allows(true, false, false, false, false),
                "animal mode should allow animals");
        helper.assertFalse(PartyAttackMode.ANIMALS.allows(false, true, false, false, false),
                "animal mode should reject hostiles");
        helper.assertTrue(PartyAttackMode.HOSTILES.allows(false, true, false, false, false),
                "hostile mode should allow hostiles");
        helper.assertTrue(PartyAttackMode.PLAYERS.allows(false, false, true, false, false),
                "player mode should allow players");
        helper.assertTrue(PartyAttackMode.VILLAGERS.allows(false, false, false, true, false),
                "villager mode should allow villagers");
        helper.assertTrue(PartyAttackMode.VILLAGERS.allows(false, false, false, false, true, false),
                "villager mode should also allow iron golems");
        helper.assertValueEqual(PartyAttackMode.byName("villagers"), PartyAttackMode.VILLAGERS,
                "villager mode should survive its persisted name");
        helper.assertValueEqual(PartyAttackMode.PLAYERS.next(), PartyAttackMode.VILLAGERS,
                "attack-mode UI cycle should include villagers after players");
        helper.assertTrue(PartyAttackMode.PARTIES.allows(false, false, false, false, true),
                "party mode should allow members of other parties");
        helper.assertTrue(PartyAttackMode.ALL.allows(false, false, false, false, false),
                "all mode should preserve unrestricted party attacks");

        PartySavedData data = new PartySavedData();
        PartyRecord party = data.createParty(leader, now);
        helper.assertValueEqual(party.combatMode(), PartyCombatMode.ATTACK_WITH_PARTY,
                "new parties default to attack with party");
        party.setAttackMode(PartyAttackMode.HOSTILES);
        party.setSharedVillagerInventories(false);
        helper.assertTrue(data.addPlayer(party, second), "second player should join");
        helper.assertTrue(data.addPlayer(party, third), "third player should join");
        helper.assertTrue(data.addPlayer(party, fourth), "fourth player should join");
        helper.assertFalse(data.addPlayer(party, rejectedPlayer), "leader plus three players must fill the player cap");

        List<UUID> villagers = new ArrayList<>();
        for (int order = 0; order < PartyService.MAX_VILLAGERS; order++) {
            UUID villagerId = UUID.randomUUID();
            villagers.add(villagerId);
            helper.assertTrue(data.addVillager(party, villagerRecord(villagerId, leader, order, now)),
                    "villager within separate cap should join");
            if (order == 0) {
                helper.assertValueEqual(party.villager(villagerId).combatMode(),
                        PartyCombatMode.ATTACK_WITH_PARTY,
                        "new recruits inherit the default attack-with-party mode");
                party.setCombatMode(PartyCombatMode.KILL_ON_SIGHT);
            }
        }
        helper.assertFalse(data.addVillager(
                        party,
                        villagerRecord(UUID.randomUUID(), leader, PartyService.MAX_VILLAGERS, now)),
                "fifth villager must be rejected independently of player count");

        PartyVillagerRecord staying = party.villager(villagers.get(1));
        staying.setStaying(Level.OVERWORLD.location(), new BlockPos(12, 65, -4));
        PartyVillagerRecord customized = party.villager(villagers.getFirst());
        customized.setCombatMode(PartyCombatMode.ATTACK_WITH_PARTY);
        customized.setAttackMode(PartyAttackMode.PLAYERS);
        customized.setDropCollectionMode(PartyDropCollectionMode.SLAIN_ENTITIES);
        customized.setQuickCommandsEnabled(false);
        helper.assertValueEqual(PartySyncService.combatModeState(party), PartyCombatModeState.CUSTOM,
                "mixed per-villager combat modes should synchronize as custom");
        helper.assertValueEqual(PartySyncService.attackModeState(party), PartyAttackModeState.CUSTOM,
                "mixed per-villager attack modes should synchronize as custom");
        PartySharedQuestRecord shared = new PartySharedQuestRecord(
                VillagerRetaliation.id("party_persistence_fixture"),
                villagers.getFirst(),
                now);
        shared.enroll(leader, false);
        shared.enroll(second, true);
        shared.mergeObjectiveCounter("kills", 2);
        shared.markObjectiveComplete("items");
        UUID death = UUID.randomUUID();
        helper.assertTrue(shared.markDeathProcessed("kills", death), "first death credit should be recorded");
        helper.assertFalse(shared.markDeathProcessed("kills", death), "same death must not be recorded twice");
        party.addSharedQuest(shared);

        PartyInvitation invitation = new PartyInvitation(
                UUID.randomUUID(), leader, rejectedPlayer, party.id(), now, now + 200L);
        data.putInvitation(invitation);
        CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
        PartySavedData loaded = PartySavedData.load(saved, level.registryAccess());
        PartyRecord restored = loaded.party(party.id()).orElseThrow(
                () -> new GameTestAssertException("serialized party did not load"));

        helper.assertValueEqual(saved.getInt("Version"), 7, "party serialization version");
        helper.assertValueEqual(restored.combatMode(), PartyCombatMode.KILL_ON_SIGHT,
                "global combat-mode persistence");
        helper.assertValueEqual(restored.attackMode(), PartyAttackMode.HOSTILES,
                "global attack-mode persistence");
        helper.assertFalse(restored.sharedVillagerInventories(), "shared-inventory policy persistence");
        helper.assertValueEqual(restored.villager(villagers.getFirst()).combatMode(),
                PartyCombatMode.ATTACK_WITH_PARTY,
                "individual combat-mode persistence");
        helper.assertValueEqual(restored.villager(villagers.getFirst()).attackMode(), PartyAttackMode.PLAYERS,
                "individual attack-mode persistence");
        helper.assertValueEqual(restored.villager(villagers.getFirst()).dropCollectionMode(),
                PartyDropCollectionMode.SLAIN_ENTITIES,
                "individual drop-collection setting persistence");
        helper.assertFalse(restored.villager(villagers.getFirst()).quickCommandsEnabled(),
                "individual quick-command participation persistence");
        CompoundTag legacyQuickCommandVillager = customized.save();
        legacyQuickCommandVillager.remove("QuickCommandsEnabled");
        helper.assertTrue(PartyVillagerRecord.load(legacyQuickCommandVillager).quickCommandsEnabled(),
                "legacy villagers default to quick-command participation");
        helper.assertValueEqual(restored.playerIds(), List.of(leader, second, third, fourth),
                "player roster order with leader first");
        helper.assertValueEqual(restored.villagers().stream().map(PartyVillagerRecord::villagerId).toList(), villagers,
                "villager recruitment order");
        helper.assertValueEqual(restored.totalMembers(), PartyService.MAX_VISIBLE_MEMBERS, "visible 8/8 member total");
        helper.assertValueEqual(restored.villager(villagers.get(1)).commandMode(), PartyCommandMode.STAY,
                "stay mode persistence");
        helper.assertValueEqual(restored.villager(villagers.get(1)).stayPosition(), new BlockPos(12, 65, -4),
                "stay position persistence");
        helper.assertValueEqual(restored.villager(villagers.getFirst()).lastKnownPosition(), new BlockPos(0, 64, 0),
                "last-known villager position persistence");
        helper.assertValueEqual(loaded.partyForPlayer(fourth).map(PartyRecord::id).orElse(null), party.id(),
                "player membership index persistence");
        helper.assertValueEqual(loaded.partyForVillager(villagers.getLast()).map(PartyRecord::id).orElse(null), party.id(),
                "villager membership index persistence");
        helper.assertTrue(loaded.invitation(invitation.id()).isPresent(), "pending invitation persistence");
        helper.assertValueEqual(restored.sharedQuests().getFirst().instanceId(), shared.instanceId(),
                "stable shared quest instance persistence");
        helper.assertValueEqual(restored.sharedQuests().getFirst().objectiveCounter("kills"), 2,
                "shared objective progress persistence");

        CompoundTag legacySaved = saved.copy();
        CompoundTag legacyParty = (CompoundTag) legacySaved
                .getList("Parties", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .get(0);
        legacyParty.remove("PartyCombatMode");
        legacyParty.putBoolean("KillOnSight", false);
        legacyParty.putBoolean("AttackWithParty", false);
        CompoundTag legacyVillager = (CompoundTag) legacyParty
                .getList("Villagers", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .get(0);
        legacyVillager.remove("PartyCombatMode");
        legacyVillager.putBoolean("KillOnSight", true);
        PartyRecord migrated = PartySavedData.load(legacySaved, level.registryAccess())
                .party(party.id())
                .orElseThrow();
        helper.assertValueEqual(migrated.combatMode(), PartyCombatMode.SELF_DEFENSE,
                "legacy disabled attack policy migrates to self defense");
        helper.assertValueEqual(migrated.villager(villagers.getFirst()).combatMode(),
                PartyCombatMode.KILL_ON_SIGHT,
                "legacy KOS setting migrates to kill-on-sight combat mode");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void invitationAcceptanceRevalidatesTheFinalPlayerSlot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer leader = helper.makeMockServerPlayerInLevel();
        ServerPlayer second = fakePlayer(level, uniqueName("party_second"));
        ServerPlayer third = fakePlayer(level, uniqueName("party_third"));
        ServerPlayer fourth = fakePlayer(level, uniqueName("party_fourth"));
        ServerPlayer racedOut = fakePlayer(level, uniqueName("party_raced_out"));
        UUID partyId = null;
        try {
            PartyService.PartyResult firstInvite = PartyService.sendInvitation(leader, second);
            helper.assertTrue(firstInvite.success(), "leader should send first invitation");
            helper.assertTrue(PartyService.getPartyForPlayer(level, leader.getUUID()).isEmpty(),
                    "sending an invitation must not create an empty party");
            PartyService.PartyResult firstAccept = PartyService.acceptInvitation(second, firstInvite.invitationId());
            helper.assertTrue(firstAccept.success(), "target should explicitly accept first invitation");
            partyId = firstAccept.partyId();

            PartyService.PartyResult secondInvite = PartyService.sendInvitation(leader, third);
            helper.assertTrue(PartyService.acceptInvitation(third, secondInvite.invitationId()).success(),
                    "third player should fill the third roster position");
            PartyService.PartyResult finalSlotInvite = PartyService.sendInvitation(leader, fourth);
            PartyService.PartyResult racingInvite = PartyService.sendInvitation(leader, racedOut);
            helper.assertTrue(finalSlotInvite.success() && racingInvite.success(),
                    "pending invitations must not reserve the last slot");
            helper.assertTrue(PartyService.acceptInvitation(fourth, finalSlotInvite.invitationId()).success(),
                    "first final-slot acceptance should succeed");
            PartyService.PartyResult losingAcceptance =
                    PartyService.acceptInvitation(racedOut, racingInvite.invitationId());
            helper.assertFalse(losingAcceptance.success(), "second final-slot acceptance must fail cleanly");
            helper.assertValueEqual(losingAcceptance.messageKey(), "villagerretaliation.party.error.player_limit",
                    "final-slot race failure message");

            PartyRecord party = PartyService.getParty(level, partyId).orElseThrow();
            helper.assertValueEqual(party.playerIds().size(), PartyService.MAX_PLAYERS, "authoritative player cap");
            helper.assertTrue(PartyService.getPartyForPlayer(level, racedOut.getUUID()).isEmpty(),
                    "losing target must remain outside the party");
            helper.assertFalse(PartyService.leaveParty(leader).success(), "leader must disband instead of leaving");
            helper.assertTrue(PartyService.leaveParty(second).success(), "non-leader should be able to leave");
            helper.assertTrue(PartyService.getPartyForPlayer(level, second.getUUID()).isEmpty(),
                    "leave must clear the membership index");
        } finally {
            if (partyId != null) {
                PartyService.deleteParty(level, partyId);
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void recruitmentPaymentExtensionCommandsInventoryAndDismissalAreAuthoritative(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer leader = fakePlayer(level, uniqueName("party_contract_leader"));
        ServerPlayer member = fakePlayer(level, uniqueName("party_contract_member"));
        ServerPlayer outsider = fakePlayer(level, uniqueName("party_contract_outsider"));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        movePlayer(helper, leader, new BlockPos(1, 2, 2));
        UUID partyId = null;
        try {
            int walletBeforeRecruitment = VillagerWalletService.getCurrentEmeralds(villager);
            leader.getInventory().add(new ItemStack(Items.EMERALD, 31));
            PartyVillagerContractService.ContractResult insufficient =
                    PartyVillagerContractService.recruit(leader, villager);
            helper.assertFalse(insufficient.success(), "recruitment without 32 emeralds must fail");
            helper.assertValueEqual(VillagerCurrencyPayment.count(leader), 31, "failed recruitment payment rollback");
            helper.assertTrue(PartyService.getPartyForPlayer(level, leader.getUUID()).isEmpty(),
                    "failed recruitment must not create a party");
            helper.assertTrue(PartyService.getPartyForVillager(level, villager.getUUID()).isEmpty(),
                    "failed recruitment must not add the villager");
            helper.assertValueEqual(VillagerWalletService.getCurrentEmeralds(villager), walletBeforeRecruitment,
                    "failed recruitment must not credit the villager wallet");

            leader.getInventory().add(new ItemStack(Items.EMERALD));
            PartyVillagerContractService.ContractResult recruited =
                    PartyVillagerContractService.recruit(leader, villager);
            helper.assertTrue(recruited.success(), "32 emerald recruitment should succeed");
            partyId = recruited.partyId();
            PartyRecord party = PartyService.getParty(level, partyId).orElseThrow();
            PartyVillagerRecord record = party.villager(villager.getUUID());
            helper.assertValueEqual(VillagerCurrencyPayment.count(leader), 0, "exact initial recruitment cost");
            helper.assertValueEqual(record.commandMode(), PartyCommandMode.FOLLOW, "default follow command");
            helper.assertTrue(record.quickCommandsEnabled(), "new recruits default to quick-command participation");
            PartyQuickCommandService.handle(leader, new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                    PartyQuickCommand.STAND_GUARD));
            helper.assertTrue(PartyQuickCommandService.isStandGuardActive(party),
                    "stand guard quick command activates the runtime guard state");
            helper.assertFalse(PartyQuickCommandService.overridesRecruitmentMovement(villager),
                    "stand guard remains a stance and does not override follow movement");
            PartyQuickCommandService.handle(leader, new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                    PartyQuickCommand.STAY_HERE,
                    com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload.NO_ENTITY,
                    villager.blockPosition()));
            helper.assertValueEqual(record.commandMode(), PartyCommandMode.STAY,
                    "stay-here quick command changes the underlying guard position behavior");
            helper.assertTrue(PartyQuickCommandService.isStandGuardActive(party),
                    "stay-here quick command retains stand guard stance");
            PartyQuickCommandService.handle(leader, new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                    PartyQuickCommand.REGROUP));
            helper.assertValueEqual(record.commandMode(), PartyCommandMode.FOLLOW,
                    "follow quick command restores movement while guarding");
            helper.assertTrue(PartyQuickCommandService.isStandGuardActive(party),
                    "follow quick command retains stand guard stance");
            PartyQuickCommandService.handle(leader, new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                    PartyQuickCommand.STAND_GUARD));
            helper.assertFalse(PartyQuickCommandService.isStandGuardActive(party),
                    "repeating stand guard lowers shields and clears the runtime guard state");
            BlockPos quickMoveTarget = villager.blockPosition().offset(1, 0, 0);
            PartyQuickCommandService.handle(leader, new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                    PartyQuickCommand.MOVE_TO,
                    com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload.NO_ENTITY,
                    quickMoveTarget));
            helper.assertTrue(PartyQuickCommandService.overridesRecruitmentMovement(villager),
                    "move-to quick command overrides ordinary follow-distance movement");
            BlockPos outlinedMoveTarget = PartyQuickCommandService.moveTarget(party);
            helper.assertTrue(outlinedMoveTarget != null
                            && !level.getBlockState(outlinedMoveTarget)
                            .getCollisionShape(level, outlinedMoveTarget)
                            .isEmpty(),
                    "move-to quick command exposes the solid destination block beneath its stand position");
            PartyQuickCommandService.handle(leader, new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                    PartyQuickCommand.REGROUP));
            helper.assertTrue(PartyQuickCommandService.overridesRecruitmentMovement(villager),
                    "regroup should own movement while the villager rushes to the leader");
            leader.moveTo(villager.getX(), villager.getY(), villager.getZ());
            PartyQuickCommandService.onVillagerTickPost(villager);
            helper.assertFalse(PartyQuickCommandService.overridesRecruitmentMovement(villager),
                    "regroup should release its movement override within 2.5 blocks");
            helper.assertValueEqual(record.contractEndGameTime() - record.contractStartGameTime(),
                    VillagerContractTime.DAY_TICKS, "one paid contract day");
            helper.assertValueEqual(record.emeraldsPaid(), 32, "per-villager prepaid amount");
            helper.assertValueEqual(VillagerWalletService.getCurrentEmeralds(villager), walletBeforeRecruitment + 32,
                    "initial party payment must credit the villager wallet");
            helper.assertValueEqual(VillagerInteractionService.openTrading(leader, villager, false), InteractionResult.FAIL,
                    "party villagers must not trade even with their leader");
            VillagerInventoryMenu partyInventoryMenu = new VillagerInventoryMenu(
                    1,
                    leader.getInventory(),
                    villager,
                    VillagerInventoryMenu.ViewMode.JOB,
                    true,
                    true);
            helper.assertValueEqual(partyInventoryMenu.viewMode(), VillagerInventoryMenu.ViewMode.PARTY,
                    "active party contracts must select the party inventory view");
            helper.assertValueEqual(partyInventoryMenu.workInventoryViewMode(), VillagerInventoryMenu.ViewMode.PARTY,
                    "personal-to-work tab switching must retain party mode");
            helper.assertTrue(partyInventoryMenu.getSlot(HiredJobInventory.FILTER_SLOT - 1).container
                            == HiredJobInventory.getJobInventory(villager),
                    "party inventory must include the final hotbar slot");
            helper.assertTrue(partyInventoryMenu.getSlot(HiredJobInventory.FILTER_SLOT).container == leader.getInventory(),
                    "party inventory must omit the job filter slot");
            partyInventoryMenu.removed(leader);

            PartySavedData.get(level).addPlayer(party, member.getUUID());
            helper.assertTrue(PartyVillagerContractService.canAccessJobInventory(level, villager, leader),
                    "leader job-inventory access");
            helper.assertTrue(PartyVillagerContractService.canAccessJobInventory(level, villager, member),
                    "party members share villager inventories by default");
            helper.assertFalse(PartyVillagerContractService.canAccessJobInventory(level, villager, outsider),
                    "unrelated player job-inventory denial");
            helper.assertTrue(PartyService.setPolicies(leader, null, null, false).success(),
                    "leader should disable shared villager inventories");
            helper.assertFalse(PartyVillagerContractService.canAccessJobInventory(level, villager, member),
                    "disabled sharing denies ordinary party members");
            helper.assertTrue(PartyVillagerContractService.canAccessJobInventory(level, villager, leader),
                    "inventory sharing policy never locks out the leader");
            helper.assertTrue(PartyService.setPolicies(leader, null, null, true).success(),
                    "leader should restore shared villager inventories");
            helper.assertFalse(PartyService.setPolicies(
                    member, PartyCombatMode.SELF_DEFENSE, null, false).success(),
                    "ordinary members cannot change party policies");
            helper.assertFalse(PartyVillagerContractService.setStaying(member, villager).success(),
                    "non-leader follow/stay command denial");
            helper.assertTrue(PartyVillagerContractService.cycleCombatMode(leader, villager).success(),
                    "leader may customize one villager's combat mode");
            helper.assertValueEqual(record.combatMode(), PartyCombatMode.SELF_DEFENSE,
                    "individual combat mode cycles to self defense");
            helper.assertFalse(PartyVillagerContractService.cycleCombatMode(member, villager).success(),
                    "ordinary members cannot customize villager combat policies");
            helper.assertTrue(PartyVillagerContractService.cycleDropCollectionMode(leader, villager).success(),
                    "leader may configure individual drop collection");
            helper.assertValueEqual(record.dropCollectionMode(), PartyDropCollectionMode.SLAIN_ENTITIES,
                    "first drop collection mode is slain entities");
            helper.assertTrue(PartyVillagerContractService.cycleDropCollectionMode(leader, villager).success(),
                    "drop collection mode cycles again");
            helper.assertValueEqual(record.dropCollectionMode(), PartyDropCollectionMode.ALL_DROPS,
                    "second drop collection mode is all drops");
            ItemEntity groundDrop = new ItemEntity(
                    level, villager.getX(), villager.getY(), villager.getZ(), new ItemStack(Items.BONE, 2));
            helper.assertTrue(PartyVillagerDropCollection.capturePickup(level, villager, groundDrop),
                    "all-drops mode captures a nearby ground item");
            helper.assertTrue(groundDrop.isRemoved(), "fully collected ground item is removed");
            helper.assertTrue(HiredJobInventory.getJobInventory(villager).hasOutput(stack -> stack.is(Items.BONE)),
                    "collected drops are routed to the party inventory output slots");
            helper.assertTrue(PartyService.setPolicies(
                    leader, PartyCombatMode.ATTACK_WITH_PARTY, null, null).success(),
                    "global combat mode should bulk update villagers");
            helper.assertValueEqual(record.combatMode(), PartyCombatMode.ATTACK_WITH_PARTY,
                    "bulk combat mode overwrites individual setting");

            helper.assertTrue(PartyVillagerContractService.setStaying(leader, villager).success(),
                    "leader stay command");
            helper.assertValueEqual(record.commandMode(), PartyCommandMode.STAY, "persistent stay command");
            helper.assertValueEqual(record.stayPosition(), villager.blockPosition(), "stay anchor");
            helper.assertTrue(PartyVillagerContractService.setFollowing(leader, villager).success(),
                    "leader follow command");
            helper.assertValueEqual(record.commandMode(), PartyCommandMode.FOLLOW, "follow command restored");
            helper.assertTrue(record.stayPosition() == null, "follow clears stale stay position");

            leader.getInventory().add(new ItemStack(Items.EMERALD, 96));
            long beforeExtension = record.contractEndGameTime();
            int walletBeforeExtension = VillagerWalletService.getCurrentEmeralds(villager);
            PartyVillagerContractService.ContractResult extension =
                    PartyVillagerContractService.extend(leader, villager, 3);
            helper.assertTrue(extension.success(), "three-day extension should succeed");
            helper.assertValueEqual(extension.emeraldCost(), 96, "three-day extension cost");
            helper.assertValueEqual(record.contractEndGameTime(), beforeExtension + 3L * VillagerContractTime.DAY_TICKS,
                    "exact three-day duration extension");
            helper.assertValueEqual(VillagerCurrencyPayment.count(leader), 0, "successful extension payment");
            helper.assertValueEqual(VillagerWalletService.getCurrentEmeralds(villager), walletBeforeExtension + 96,
                    "party extension payment must credit the villager wallet");

            member.getInventory().add(new ItemStack(Items.EMERALD, 32));
            long beforeMemberExtension = record.contractEndGameTime();
            helper.assertTrue(PartyVillagerContractService.extend(member, villager, 1).success(),
                    "ordinary party members may prolong villager contracts");
            helper.assertValueEqual(record.contractEndGameTime(), beforeMemberExtension + VillagerContractTime.DAY_TICKS,
                    "member-paid contract extension duration");
            helper.assertValueEqual(VillagerCurrencyPayment.count(member), 0,
                    "member-paid extension consumes the member's emeralds");
            outsider.getInventory().add(new ItemStack(Items.EMERALD, 32));
            helper.assertFalse(PartyVillagerContractService.extend(outsider, villager, 1).success(),
                    "unrelated players cannot prolong party contracts");
            helper.assertValueEqual(VillagerCurrencyPayment.count(outsider), 32,
                    "rejected outsider extension does not consume emeralds");

            leader.getInventory().add(new ItemStack(Items.EMERALD, 64));
            long beforeFailedExtension = record.contractEndGameTime();
            helper.assertFalse(PartyVillagerContractService.extend(leader, villager, 3).success(),
                    "insufficient extension payment must fail");
            helper.assertValueEqual(record.contractEndGameTime(), beforeFailedExtension,
                    "failed extension duration rollback");
            helper.assertValueEqual(VillagerCurrencyPayment.count(leader), 64,
                    "failed extension payment rollback");
            helper.assertFalse(PartyVillagerContractService.extend(leader, villager, 30).success(),
                    "extension beyond shared maximum must fail");

            int balanceBeforeDismissal = VillagerCurrencyPayment.count(leader);
            helper.assertTrue(PartyVillagerContractService.dismiss(leader, villager).success(),
                    "leader dismissal should succeed");
            helper.assertTrue(PartyService.getPartyForVillager(level, villager.getUUID()).isEmpty(),
                    "dismissal frees villager slot immediately");
            helper.assertFalse(PartyVillagerContractService.canAccessJobInventory(level, villager, leader),
                    "dismissal revokes job-inventory access");
            helper.assertValueEqual(VillagerCurrencyPayment.count(leader), balanceBeforeDismissal,
                    "dismissal must not refund prepaid time");
        } finally {
            if (partyId != null) {
                PartyService.deleteParty(level, partyId);
            }
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void partyRecruitmentRequiresNeutralOrHigherReputation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fakePlayer(level, uniqueName("party_reputation_gate"));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        movePlayer(helper, player, new BlockPos(1, 2, 2));
        player.getInventory().add(new ItemStack(Items.EMERALD, 64));
        try {
            VillagerReputationManager.setReputation(
                    level,
                    villager,
                    player.getUUID(),
                    VillagerRetaliationConfig.SUSPICIOUS_THRESHOLD.get());
            PartyVillagerContractService.ContractResult suspicious =
                    PartyVillagerContractService.recruit(player, villager);
            helper.assertFalse(suspicious.success(), "suspicious villagers must reject party recruitment");
            helper.assertValueEqual(VillagerCurrencyPayment.count(player), 64,
                    "rejected recruitment must not remove emeralds");
            helper.assertTrue(PartyService.getPartyForVillager(level, villager.getUUID()).isEmpty(),
                    "rejected recruitment must not add the villager");

            VillagerReputationManager.setReputation(level, villager, player.getUUID(), 0);
            PartyVillagerContractService.ContractResult neutral =
                    PartyVillagerContractService.recruit(player, villager);
            helper.assertTrue(neutral.success(), "neutral villagers must remain eligible for party recruitment");
        } finally {
            PartyService.getPartyForVillager(level, villager.getUUID())
                    .ifPresent(party -> PartyService.deleteParty(level, party.id()));
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void normallyHiredVillagerCannotAlsoJoinAParty(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fakePlayer(level, uniqueName("party_hired_conflict"));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        movePlayer(helper, player, new BlockPos(1, 2, 2));
        try {
            HiredVillagerContractService.startHireContract(level, villager, player, 1, 0);
            player.getInventory().add(new ItemStack(Items.EMERALD, 32));
            PartyVillagerContractService.ContractResult result = PartyVillagerContractService.recruit(player, villager);
            helper.assertFalse(result.success(), "normally hired villager must reject party recruitment");
            helper.assertValueEqual(result.messageKey(), "villagerretaliation.party.error.villager_already_hired",
                    "incompatible contract failure message");
            helper.assertValueEqual(VillagerCurrencyPayment.count(player), 32,
                    "incompatible contract must not remove emeralds");
            helper.assertTrue(PartyService.getPartyForVillager(level, villager.getUUID()).isEmpty(),
                    "hired villager must not gain party membership");
        } finally {
            HiredVillagerContractService.endHireContract(level, villager, player);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void sharedFetchSubmissionConsumesPlayerAndVillagerStacksOnlyOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        long now = level.getServer().overworld().getGameTime();
        PartySavedData data = PartySavedData.get(level);
        PartyRecord party = data.createParty(player.getUUID(), now);
        try {
            helper.assertTrue(data.addVillager(party, villagerRecord(villager.getUUID(), player.getUUID(), 0, now)),
                    "fixture villager party membership");
            PartySharedQuestRecord shared = new PartySharedQuestRecord(
                    VillagerRetaliation.id("party_fetch_fixture"), villager.getUUID(), now);
            shared.enroll(player.getUUID(), false);
            party.addSharedQuest(shared);
            data.changed();

            player.getInventory().setItem(0, new ItemStack(Items.BONE, 2));
            HiredJobInventory jobInventory = HiredJobInventory.getJobInventory(villager);
            jobInventory.setItem(0, new ItemStack(Items.BONE, 3));
            QuestDefinition.Objective objective = itemObjective("submit_bones", Items.BONE.builtInRegistryHolder().key().location(), 5);
            PartyQuestService.SubmissionPlan plan = PartyQuestService.planSharedItemSubmission(
                            player,
                            shared,
                            List.of(objective),
                            (candidate, stack) -> stack.is(Items.BONE))
                    .orElseThrow(() -> new GameTestAssertException("shared player/villager stacks were not aggregated"));
            helper.assertValueEqual(plan.submittedStacks().stream().mapToInt(ItemStack::getCount).sum(), 5,
                    "shared submitted count");
            helper.assertTrue(plan.remove(), "validated shared submission should remove its exact source stacks");
            helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "player source stack consumed exactly once");
            helper.assertTrue(jobInventory.getItem(0).isEmpty(), "villager job source stack consumed exactly once");
            helper.assertTrue(PartyQuestService.planSharedItemSubmission(
                            player,
                            shared,
                            List.of(objective),
                            (candidate, stack) -> stack.is(Items.BONE)).isEmpty(),
                    "transferring or resubmitting consumed stacks cannot duplicate progress");
            helper.assertFalse(plan.remove(), "same submission plan cannot consume the same stacks twice");
        } finally {
            PartyService.deleteParty(level, party.id());
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void unloadedVillagersPersistUntilAuthoritativeExpiryAndFreeOnlyExpiredSlots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID leader = UUID.randomUUID();
        long now = level.getServer().overworld().getGameTime();
        PartyRecord party = PartySavedData.get(level).createParty(leader, now);
        UUID futureVillager = UUID.randomUUID();
        UUID expiredVillager = UUID.randomUUID();
        PartyVillagerRecord future = villagerRecord(futureVillager, leader, 0, now);
        PartyVillagerRecord expired = new PartyVillagerRecord(
                expiredVillager,
                leader,
                UUID.randomUUID(),
                1,
                PartyCommandMode.FOLLOW,
                null,
                null,
                now,
                now,
                1,
                32,
                "Expired",
                "minecraft:farmer",
                Level.OVERWORLD.location(),
                new BlockPos(7, 70, -9));
        try {
            PartySavedData.get(level).addVillager(party, future);
            PartySavedData.get(level).addVillager(party, expired);
            PartyVillagerContractService.clearRuntimeState();
            PartyVillagerContractService.onServerTick(level.getServer());
            helper.assertTrue(PartyService.getPartyForVillager(level, futureVillager).isPresent(),
                    "temporarily unresolved future contract must remain in roster");
            helper.assertTrue(PartyService.getPartyForVillager(level, expiredVillager).isEmpty(),
                    "authoritatively expired unresolved contract must be removed");
            helper.assertValueEqual(party.villagers().size(), 1, "expired villager frees exactly one slot");
        } finally {
            PartyService.deleteParty(level, party.id());
            PartyVillagerContractService.clearRuntimeState();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void permanentlyDiscardedVillagerIsRemovedWithoutWaitingForContractExpiry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        UUID leader = UUID.randomUUID();
        long now = level.getServer().overworld().getGameTime();
        PartyRecord party = PartySavedData.get(level).createParty(leader, now);
        PartySavedData.get(level).addVillager(party, villagerRecord(villager.getUUID(), leader, 0, now));
        villager.discard();
        helper.assertTrue(PartyService.getPartyForVillager(level, villager.getUUID()).isEmpty(),
                "permanent entity removal must free the party slot immediately");
        PartyService.deleteParty(level, party.id());
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void partyRelationshipsProtectAlliesAndKeepNearbyPartiesSeparate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer firstLeader = fakePlayer(level, uniqueName("party_ally_first"));
        ServerPlayer secondLeader = fakePlayer(level, uniqueName("party_ally_second"));
        Villager firstVillager = spawnVillager(helper, new BlockPos(2, 2, 2));
        Villager secondVillager = spawnVillager(helper, new BlockPos(3, 2, 2));
        long now = level.getServer().overworld().getGameTime();
        PartySavedData data = PartySavedData.get(level);
        PartyRecord firstParty = data.createParty(firstLeader.getUUID(), now);
        PartyRecord secondParty = data.createParty(secondLeader.getUUID(), now);
        try {
            data.addVillager(firstParty, villagerRecord(firstVillager.getUUID(), firstLeader.getUUID(), 0, now));
            data.addVillager(secondParty, villagerRecord(secondVillager.getUUID(), secondLeader.getUUID(), 0, now));
            helper.assertTrue(PartyService.areInSameParty(firstLeader, firstVillager),
                    "player and recruited villager ally lookup");
            helper.assertFalse(PartyService.areInSameParty(firstVillager, secondLeader),
                    "nearby second party player must remain unrelated");
            helper.assertFalse(PartyService.areInSameParty(firstVillager, secondVillager),
                    "nearby recruited villagers from different parties must remain unrelated");

            Map<UUID, VillagerRetaliationRetaliationUtil.AngerTarget> anger = new LinkedHashMap<>();
            helper.assertFalse(VillagerRetaliationRetaliationUtil.tryAnger(
                            firstVillager, firstLeader, anger, "PartyGameTestAnger"),
                    "recruited villager must never acquire a party ally retaliation target");
            helper.assertTrue(anger.isEmpty(), "ally retaliation must not persist target state");
            helper.assertTrue(VillagerRetaliationRetaliationUtil.tryAnger(
                            firstVillager, secondLeader, anger, "PartyGameTestAnger"),
                    "different-party player remains a valid retaliation relationship");
        } finally {
            PartyService.deleteParty(level, firstParty.id());
            PartyService.deleteParty(level, secondParty.id());
            firstVillager.discard();
            secondVillager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 180)
    public static void killOnSightAcquiresConfiguredTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        ServerPlayer firstLeader = fakePlayer(level, uniqueName("party_kos_first"));
        movePlayer(helper, firstLeader, new BlockPos(12, 2, 12));
        Villager attacker = spawnVillager(helper, new BlockPos(2, 2, 2));
        attacker.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
        var target = helper.spawn(EntityType.COW, new BlockPos(4, 2, 2));
        target.setNoAi(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024.0D);
        target.setHealth(1024.0F);
        long now = level.getServer().overworld().getGameTime();
        PartySavedData data = PartySavedData.get(level);
        PartyRecord firstParty = data.createParty(firstLeader.getUUID(), now);
        PartyVillagerRecord attackerRecord = villagerRecord(attacker.getUUID(), firstLeader.getUUID(), 0, now);
        data.addVillager(firstParty, attackerRecord);
        attackerRecord.setAttackMode(PartyAttackMode.ANIMALS);
        attackerRecord.setCombatMode(PartyCombatMode.KILL_ON_SIGHT);
        data.changed();

        helper.runAfterDelay(140, () -> {
            helper.assertTrue(VillagerRetaliationHandler.hasRetaliationTarget(attacker, target),
                    "KOS should retain a nearby retaliation target; attacker="
                            + attacker.blockPosition() + ", target=" + target.blockPosition()
                            + ", distance=" + attacker.distanceToSqr(target)
                            + ", canAttack=" + attacker.canAttack(target)
                            + ", lineOfSight=" + attacker.hasLineOfSight(target)
                            + ", transientTarget=" + attacker.getTarget()
                            + ", mode=" + attackerRecord.combatMode() + "/" + attackerRecord.attackMode());
            PartyService.deleteParty(level, firstParty.id());
            attacker.discard();
            target.discard();
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void partyAttackerWakesSleepingVillagerTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        ServerPlayer leader = fakePlayer(level, uniqueName("party_sleeping_target"));
        movePlayer(helper, leader, new BlockPos(1, 2, 2));
        Villager attacker = spawnVillager(helper, new BlockPos(2, 2, 2));
        Villager target = spawnVillager(helper, new BlockPos(4, 2, 2));
        target.setNoAi(true);
        target.startSleeping(target.blockPosition());
        long now = level.getServer().overworld().getGameTime();
        PartyRecord party = PartySavedData.get(level).createParty(leader.getUUID(), now);
        PartySavedData.get(level).addVillager(
                party, villagerRecord(attacker.getUUID(), leader.getUUID(), 0, now));
        leader.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());

        PartyQuickCommandService.handle(
                leader,
                new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                        PartyQuickCommand.ATTACK,
                        target.getId(),
                        null));
        helper.assertTrue(VillagerRetaliationHandler.hasRetaliationTarget(attacker, target),
                "attack command should establish the sleeping villager as the party target");
        helper.assertTrue(target.isSleeping(), "regression setup should begin with the target in bed");

        helper.runAfterDelay(5, () -> {
            helper.assertFalse(target.isSleeping(),
                    "a villager targeted by a party attacker should be pulled out of bed");
            PartyService.deleteParty(level, party.id());
            PartyQuickCommandService.clearRuntimeState();
            attacker.discard();
            target.discard();
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void attackAtCrosshairUsesModeFilteredRaycastAndStopsAtBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 9; x++) {
            helper.setBlock(new BlockPos(x, 1, 2), Blocks.STONE);
        }
        ServerPlayer leader = fakePlayer(level, uniqueName("party_crosshair_attack"));
        movePlayer(helper, leader, new BlockPos(1, 2, 2));
        Villager attacker = spawnVillager(helper, new BlockPos(2, 2, 2));
        Villager closerInvalidTarget = spawnVillager(helper, new BlockPos(4, 2, 2));
        Zombie fartherValidTarget = helper.spawn(EntityType.ZOMBIE, new BlockPos(7, 2, 2));
        closerInvalidTarget.setNoAi(true);
        fartherValidTarget.setNoAi(true);

        long now = level.getGameTime();
        PartyRecord party = PartySavedData.get(level).createParty(leader.getUUID(), now);
        PartyVillagerRecord attackerRecord = villagerRecord(attacker.getUUID(), leader.getUUID(), 0, now);
        PartySavedData.get(level).addVillager(party, attackerRecord);
        attackerRecord.setAttackMode(PartyAttackMode.HOSTILES);
        leader.lookAt(EntityAnchorArgument.Anchor.EYES, fartherValidTarget.getEyePosition());

        PartyQuickCommandService.handle(
                leader,
                new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                        PartyQuickCommand.ATTACK,
                        closerInvalidTarget.getId(),
                        null));
        helper.assertTrue(VillagerRetaliationHandler.hasRetaliationTarget(attacker, fartherValidTarget),
                "crosshair attack should skip a closer entity rejected by HOSTILES mode");
        helper.assertFalse(VillagerRetaliationHandler.hasRetaliationTarget(attacker, closerInvalidTarget),
                "crosshair attack must not attach to a mode-invalid entity");

        VillagerRetaliationHandler.clearCustomTarget(attacker);
        PartyQuickCommandService.clearRuntimeState();
        helper.setBlock(new BlockPos(6, 3, 2), Blocks.STONE);
        PartyQuickCommandService.handle(
                leader,
                new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                        PartyQuickCommand.ATTACK,
                        fartherValidTarget.getId(),
                        null));
        helper.assertFalse(VillagerRetaliationHandler.hasRetaliationTarget(attacker, fartherValidTarget),
                "crosshair attack must not use a transmitted entity through a blocking wall");

        PartyService.deleteParty(level, party.id());
        PartyQuickCommandService.clearRuntimeState();
        attacker.discard();
        closerInvalidTarget.discard();
        fartherValidTarget.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void attackAtCrosshairAllowsForgivingNearMiss(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 9; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        ServerPlayer leader = fakePlayer(level, uniqueName("party_crosshair_near_miss"));
        movePlayer(helper, leader, new BlockPos(1, 2, 2));
        Villager attacker = spawnVillager(helper, new BlockPos(2, 2, 2));
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(7, 2, 2));
        target.setNoAi(true);

        long now = level.getGameTime();
        PartyRecord party = PartySavedData.get(level).createParty(leader.getUUID(), now);
        PartyVillagerRecord attackerRecord = villagerRecord(attacker.getUUID(), leader.getUUID(), 0, now);
        attackerRecord.setAttackMode(PartyAttackMode.HOSTILES);
        PartySavedData.get(level).addVillager(party, attackerRecord);
        leader.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition().add(0.0D, 0.0D, 0.7D));

        PartyQuickCommandService.handle(
                leader,
                new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                        PartyQuickCommand.ATTACK,
                        target.getId(),
                        null));
        helper.assertTrue(VillagerRetaliationHandler.hasRetaliationTarget(attacker, target),
                "attack command should accept a visible target just outside its exact hitbox");

        PartyService.deleteParty(level, party.id());
        PartyQuickCommandService.clearRuntimeState();
        attacker.discard();
        target.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void moveToUsesSharedNodeRouteNavigation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 14; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        ServerPlayer leader = fakePlayer(level, uniqueName("party_shared_move_route"));
        movePlayer(helper, leader, new BlockPos(1, 2, 2));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        BlockPos target = helper.absolutePos(new BlockPos(13, 2, 2));
        long now = level.getServer().overworld().getGameTime();
        PartyRecord party = PartySavedData.get(level).createParty(leader.getUUID(), now);
        PartySavedData.get(level).addVillager(
                party, villagerRecord(villager.getUUID(), leader.getUUID(), 0, now));

        PartyQuickCommandService.handle(
                leader,
                new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                        PartyQuickCommand.MOVE_TO,
                        com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload.NO_ENTITY,
                        target));
        helper.startSequence()
                .thenExecuteFor(20, () -> {
                    if (villager.getNavigation().isDone()) {
                        PartyQuickCommandService.onVillagerTickPost(villager);
                    }
                })
                .thenExecute(() -> {
                    helper.assertFalse(villager.getNavigation().isDone(),
                            "move-to should start the shared node-route path");
                    helper.assertTrue(VillagerTaskNavigationUtil.isHiredWalkTarget(villager),
                            "move-to should use the same guarded walk-target pipeline as node jobs");
                    PartyService.deleteParty(level, party.id());
                    PartyQuickCommandService.clearRuntimeState();
                    villager.discard();
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 180)
    public static void sharedMoveRouteCrossesDirtPathGrassEdge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(
                        new BlockPos(x, 1, z),
                        x <= 3 ? Blocks.DIRT_PATH : Blocks.GRASS_BLOCK);
            }
        }
        ServerPlayer leader = fakePlayer(level, uniqueName("party_path_grass_edge"));
        movePlayer(helper, leader, new BlockPos(1, 2, 2));
        Villager villager = spawnVillager(helper, new BlockPos(3, 2, 2));
        BlockPos pathSide = helper.absolutePos(new BlockPos(3, 2, 2));
        BlockPos grassSide = helper.absolutePos(new BlockPos(4, 2, 2));
        double routeX = grassSide.getX() - pathSide.getX();
        double routeZ = grassSide.getZ() - pathSide.getZ();
        villager.moveTo(
                (pathSide.getX() + grassSide.getX() + 1.0D) * 0.5D - routeX * 0.05D,
                pathSide.getY() - 0.0625D,
                (pathSide.getZ() + grassSide.getZ() + 1.0D) * 0.5D - routeZ * 0.05D,
                0.0F,
                0.0F);
        villager.setOnGround(true);
        BlockPos target = helper.absolutePos(new BlockPos(10, 2, 2));
        long now = level.getServer().overworld().getGameTime();
        PartyRecord party = PartySavedData.get(level).createParty(leader.getUUID(), now);
        PartySavedData.get(level).addVillager(
                party, villagerRecord(villager.getUUID(), leader.getUUID(), 0, now));

        PartyQuickCommandService.handle(
                leader,
                new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                        PartyQuickCommand.MOVE_TO,
                        com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload.NO_ENTITY,
                        target));
        PartyQuickCommandService.onVillagerTickPost(villager);
        villager.horizontalCollision = true;
        villager.setOnGround(true);
        helper.assertTrue(VillagerTaskNavigationUtil.tickHiredPathStepAssist(level, villager),
                "shared route should recognize the dirt-path/grass collision seam as a safe step assist");
        helper.startSequence()
                .thenWaitUntil(() -> {
                    PartyQuickCommandService.onVillagerTickPost(villager);
                    helper.assertTrue(villager.distanceToSqr(target.getCenter()) <= 4.0D,
                            "shared route has not crossed the 1/16-block dirt-path/grass seam yet; pos="
                                    + villager.position() + ", nav=" + villager.getNavigation().getTargetPos());
                })
                .thenExecute(() -> {
                    PartyService.deleteParty(level, party.id());
                    PartyQuickCommandService.clearRuntimeState();
                    villager.discard();
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void submergedVillagerAlwaysFloatsAndKeepsBreathing(GameTestHelper helper) {
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                for (int y = 2; y <= 4; y++) {
                    boolean wall = x == 0 || x == 4 || z == 0 || z == 4;
                    helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : Blocks.WATER);
                }
            }
        }
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        villager.setAirSupply(1);
        villager.setDeltaMovement(0.0D, -0.1D, 0.0D);
        VillagerTaskNavigationUtil.tickVillagerWaterSafety(helper.getLevel(), villager);

        helper.assertValueEqual(villager.getAirSupply(), villager.getMaxAirSupply(),
                "water safety should prevent drowning even without an active movement order");
        helper.assertTrue(villager.getDeltaMovement().y > 0.0D,
                "a submerged idle villager should always receive upward swimming motion");

        BlockPos current = villager.blockPosition();
        BlockPos swimTarget = current.east(2);
        net.minecraft.world.level.pathfinder.Path swimPath = new net.minecraft.world.level.pathfinder.Path(
                List.of(
                        new net.minecraft.world.level.pathfinder.Node(current.getX(), current.getY(), current.getZ()),
                        new net.minecraft.world.level.pathfinder.Node(
                                swimTarget.getX(), swimTarget.getY(), swimTarget.getZ())),
                swimTarget,
                true);
        helper.assertTrue(villager.getNavigation().moveTo(swimPath, 0.5D),
                "regression setup should install an active water path");
        VillagerTaskNavigationUtil.tickVillagerWaterSafety(helper.getLevel(), villager);
        helper.assertTrue(villager.getMoveControl().hasWanted()
                        && villager.getMoveControl().getWantedX() > villager.getX(),
                "a submerged villager with a path should swim toward its upcoming route node");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void partyMemberInteractionInterruptsCombatWithoutOpeningTrade(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer leader = fakePlayer(level, uniqueName("party_interaction_combat"));
        movePlayer(helper, leader, new BlockPos(1, 2, 2));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        Zombie target = EntityType.ZOMBIE.create(level);
        if (target == null) {
            throw new GameTestAssertException("Could not create interaction combat target");
        }
        BlockPos targetPos = helper.absolutePos(new BlockPos(4, 2, 2));
        target.moveTo(targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D, 0.0F, 0.0F);
        target.setNoAi(true);
        level.addFreshEntity(target);
        long now = level.getGameTime();
        PartyRecord party = PartySavedData.get(level).createParty(leader.getUUID(), now);
        PartySavedData.get(level).addVillager(
                party, villagerRecord(villager.getUUID(), leader.getUUID(), 0, now));
        villager.setTarget(target);
        villager.setLastHurtByMob(target);

        helper.assertTrue(
                VillagerInteractionService.shouldHandleInteraction(villager, leader, net.minecraft.world.InteractionHand.MAIN_HAND),
                "the custom interaction system should exclusively intercept a party-member click");
        helper.assertTrue(VillagerInteractionService.canUseInteractionSystem(leader, villager),
                "a party member should be allowed to interrupt its villager's active combat");
        helper.assertValueEqual(
                VillagerInteractionService.handleVillagerRightClick(villager, leader),
                InteractionResult.CONSUME,
                "party-member interaction should open the custom menu");
        helper.assertFalse(villager.isTrading(),
                "opening the party-member interaction menu must not also start vanilla trading");
        helper.assertTrue(VillagerConversationService.isConversing(leader),
                "the party-member interaction should establish a custom conversation session");
        helper.assertTrue(villager.getTarget() == null && villager.getLastHurtByMob() == null,
                "opening the interaction menu should suspend the party villager's combat state");

        VillagerConversationService.endForPlayer(leader, false);
        PartyService.deleteParty(level, party.id());
        target.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void villagerInteractionRequiresAtLeastOneEmptyPlayerHand(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper.getLevel(), uniqueName("interaction_empty_hand"));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        helper.assertFalse(VillagerInteractionService.hasEmptyHandForVillagerInteraction(player),
                "a main-hand item plus an off-hand shield should block villager interaction");

        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(VillagerInteractionService.hasEmptyHandForVillagerInteraction(player),
                "an empty main hand should permit villager interaction");

        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
        helper.assertTrue(VillagerInteractionService.hasEmptyHandForVillagerInteraction(player),
                "an empty off hand should permit villager interaction");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = "crossbow_combat", setupTicks = 20, timeoutTicks = 180)
    public static void rangedPartyVillagerLoadsAndFiresCrossbowLikePillager(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer leader = fakePlayer(level, uniqueName("party_crossbow_cycle"));
        movePlayer(helper, leader, new BlockPos(1, 2, 2));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        Zombie target = EntityType.HUSK.create(level);
        if (target == null) {
            throw new GameTestAssertException("Could not create crossbow target");
        }
        BlockPos targetPos = helper.absolutePos(new BlockPos(7, 2, 2));
        target.moveTo(targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D, 0.0F, 0.0F);
        target.setNoAi(true);
        level.addFreshEntity(target);
        long now = level.getGameTime();
        PartyRecord party = PartySavedData.get(level).createParty(leader.getUUID(), now);
        PartyVillagerRecord record = villagerRecord(villager.getUUID(), leader.getUUID(), 0, now);
        record.setWeaponPreference(PartyWeaponPreference.RANGED);
        PartySavedData.get(level).addVillager(
                party, record);
        leader.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        HiredJobInventory jobInventory = HiredJobInventory.getJobInventory(villager);
        jobInventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_SWORD));
        int crossbowSlot = HiredJobInventory.HOTBAR_START;
        int arrowSlot = crossbowSlot + 1;
        jobInventory.setItem(crossbowSlot, new ItemStack(Items.CROSSBOW));
        jobInventory.markPlayerPlacedSupply(crossbowSlot);
        jobInventory.setItem(arrowSlot, new ItemStack(Items.ARROW));
        jobInventory.markPlayerPlacedSupply(arrowSlot);

        VillagerCombatLoadoutService.applyPreference(villager, PartyWeaponPreference.RANGED);
        helper.assertTrue(villager.getMainHandItem().getItem() instanceof CrossbowItem,
                "range mode should equip the available crossbow");
        PartyQuickCommandService.handle(
                leader,
                new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                        PartyQuickCommand.ATTACK,
                        target.getId(),
                        null));
        helper.assertTrue(VillagerRetaliationHandler.hasRetaliationTarget(villager, target),
                "party attack command should establish the crossbow target");

        helper.startSequence()
                .thenWaitUntil(() -> {
                    level.tickNonPassenger(villager);
                    helper.assertTrue(
                            jobInventory.getItem(arrowSlot).isEmpty(),
                            "ranged party villager has not loaded its crossbow: "
                                    + crossbowCycleState(level, villager, target, jobInventory, arrowSlot));
                })
                .thenWaitUntil(() -> {
                    level.tickNonPassenger(villager);
                    helper.assertTrue(
                            crossbowShotObserved(level, villager, target),
                            "ranged party villager has not completed the crossbow load-and-fire cycle: "
                                    + crossbowCycleState(level, villager, target, jobInventory, arrowSlot));
                })
                .thenExecute(() -> {
                    helper.assertTrue(jobInventory.getItem(arrowSlot).isEmpty(),
                            "loading the crossbow should consume exactly the required arrow");
                    helper.assertFalse(villager.isUsingItem(),
                            "the crossbow loading animation must end after the shot is prepared");
                    helper.assertFalse(CrossbowItem.isCharged(findCrossbow(villager, jobInventory)),
                            "the crossbow should discharge after firing");
                    VillagerRetaliationHandler.clearCustomTarget(villager);
                    PartyService.deleteParty(level, party.id());
                    PartyQuickCommandService.clearRuntimeState();
                    target.discard();
                    villager.discard();
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void pickUpDropsRecoversOwnConsumedArrowButNotMultishotCopy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer leader = fakePlayer(level, uniqueName("party_arrow_pickup"));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        movePlayer(helper, leader, new BlockPos(1, 2, 2));
        long now = level.getGameTime();
        PartyRecord party = PartySavedData.get(level).createParty(leader.getUUID(), now);
        PartySavedData.get(level).addVillager(
                party, villagerRecord(villager.getUUID(), leader.getUUID(), 0, now));

        ItemStack consumedAmmo = new ItemStack(Items.ARROW);
        HiredRangedAmmo.markConsumedCrossbowProjectile(consumedAmmo);
        consumedAmmo.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
        Arrow consumedArrow = new Arrow(
                level, villager, consumedAmmo, new ItemStack(Items.CROSSBOW));
        consumedArrow.moveTo(villager.getX() + 0.75D, villager.getY(), villager.getZ());
        ((AbstractArrowAccessor) consumedArrow).villagerretaliation$setInGround(true);
        PartyVillagerDropCollection.onArrowEntityLoaded(consumedArrow);
        helper.assertValueEqual(consumedArrow.pickup, AbstractArrow.Pickup.ALLOWED,
                "a consumed arrow shot by a party villager should become recoverable");
        level.addFreshEntity(consumedArrow);

        ItemStack multishotAmmo = new ItemStack(Items.ARROW);
        multishotAmmo.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
        Arrow multishotCopy = new Arrow(
                level, villager, multishotAmmo, new ItemStack(Items.CROSSBOW));
        multishotCopy.moveTo(villager.getX() + 0.25D, villager.getY(), villager.getZ());
        ((AbstractArrowAccessor) multishotCopy).villagerretaliation$setInGround(true);
        PartyVillagerDropCollection.onArrowEntityLoaded(multishotCopy);
        helper.assertValueEqual(multishotCopy.pickup, AbstractArrow.Pickup.CREATIVE_ONLY,
                "an intangible Multishot copy should retain vanilla non-survival pickup eligibility");
        level.addFreshEntity(multishotCopy);

        PartyQuickCommandService.handle(
                leader,
                new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                        PartyQuickCommand.PICK_UP_DROPS));
        helper.runAfterDelay(1, () -> {
            PartyQuickCommandService.onVillagerTickPost(villager);
            helper.assertFalse(consumedArrow.isAlive(),
                    "pick-up-drops mode should collect the party villager's recoverable arrow");
            helper.assertTrue(multishotCopy.isAlive(),
                    "pick-up-drops mode should ignore a closer Multishot copy");
            helper.assertValueEqual(HiredJobInventory.getJobInventory(villager).countItem(Items.ARROW), 1,
                    "the recovered arrow should return to party supplies");

            PartyService.deleteParty(level, party.id());
            PartyQuickCommandService.clearRuntimeState();
            multishotCopy.discard();
            villager.discard();
            helper.succeed();
        });
    }

    private static ItemStack findCrossbow(Villager villager, HiredJobInventory jobInventory) {
        if (villager.getMainHandItem().getItem() instanceof CrossbowItem) {
            return villager.getMainHandItem();
        }
        if (villager.getOffhandItem().getItem() instanceof CrossbowItem) {
            return villager.getOffhandItem();
        }
        for (int slot = 0; slot < jobInventory.getContainerSize(); slot++) {
            ItemStack stack = jobInventory.getItem(slot);
            if (stack.getItem() instanceof CrossbowItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean crossbowShotObserved(ServerLevel level, Villager villager, Zombie target) {
        return target.getHealth() < target.getMaxHealth()
                || !ownedCrossbowArrows(level, villager).isEmpty();
    }

    private static String crossbowCycleState(
            ServerLevel level,
            Villager villager,
            Zombie target,
            HiredJobInventory jobInventory,
            int arrowSlot) {
        ItemStack crossbow = findCrossbow(villager, jobInventory);
        return "target=" + VillagerRetaliationHandler.hasRetaliationTarget(villager, target)
                + ", mobTarget=" + (villager.getTarget() == target)
                + ", aggressive=" + villager.isAggressive()
                + ", noAi=" + villager.isNoAi()
                + ", sleeping=" + villager.isSleeping()
                + ", tickCount=" + villager.tickCount
                + ", targetTickCount=" + target.tickCount
                + ", using=" + villager.isUsingItem()
                + ", useTicks=" + villager.getTicksUsingItem()
                + ", charged=" + CrossbowItem.isCharged(crossbow)
                + ", crossbowDamage=" + crossbow.getDamageValue()
                + ", ownedArrows=" + ownedCrossbowArrows(level, villager).size()
                + ", arrows=" + jobInventory.getItem(arrowSlot).getCount()
                + ", mainHand=" + villager.getMainHandItem()
                + ", preference=" + VillagerCombatLoadoutService.preference(villager)
                + ", lineOfSight=" + villager.hasLineOfSight(target)
                + ", distanceSqr=" + villager.distanceToSqr(target)
                + ", villagerPos=" + villager.position();
    }

    private static List<AbstractArrow> ownedCrossbowArrows(ServerLevel level, Villager villager) {
        return level.getEntitiesOfClass(
                AbstractArrow.class,
                villager.getBoundingBox().inflate(20.0D),
                arrow -> arrow.getOwner() == villager);
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void killOnSightDoesNotTargetVillagerReleasedByContractExpiry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer leader = fakePlayer(level, uniqueName("party_kos_expired_contract"));
        movePlayer(helper, leader, new BlockPos(12, 2, 12));
        Villager attacker = spawnVillager(helper, new BlockPos(2, 2, 2));
        Villager released = spawnVillager(helper, new BlockPos(4, 2, 2));
        long now = level.getServer().overworld().getGameTime();
        PartySavedData data = PartySavedData.get(level);
        PartyRecord party = data.createParty(leader.getUUID(), now);
        PartyVillagerRecord attackerRecord = villagerRecord(attacker.getUUID(), leader.getUUID(), 0, now);
        PartyVillagerRecord expiredRecord = new PartyVillagerRecord(
                released.getUUID(),
                leader.getUUID(),
                UUID.randomUUID(),
                1,
                PartyCommandMode.FOLLOW,
                null,
                null,
                now,
                now,
                1,
                32,
                "Released",
                "minecraft:farmer",
                Level.OVERWORLD.location(),
                released.blockPosition());
        data.addVillager(party, attackerRecord);
        data.addVillager(party, expiredRecord);
        attackerRecord.setAttackMode(PartyAttackMode.ALL);
        attackerRecord.setCombatMode(PartyCombatMode.KILL_ON_SIGHT);
        data.changed();
        PartyVillagerContractService.clearRuntimeState();
        PartyVillagerContractService.onServerTick(level.getServer());

        helper.assertTrue(PartyService.getPartyForVillager(level, released.getUUID()).isEmpty(),
                "expired contract should release the villager from the active roster");
        helper.assertTrue(PartyVillagerContractService.hasExpiredContractWithParty(released, party.id()),
                "released villager should remember the party whose contract expired");
        helper.runAfterDelay(30, () -> {
            helper.assertFalse(attacker.getTarget() == released,
                    "KOS must not turn on a villager released by this party's contract expiry");
            PartyService.deleteParty(level, party.id());
            PartyVillagerContractService.clearRuntimeState();
            attacker.discard();
            released.discard();
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void fallBackCancelsActiveMoveToPath(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer leader = fakePlayer(level, uniqueName("party_fall_back_move_to"));
        movePlayer(helper, leader, new BlockPos(1, 2, 2));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        long now = level.getServer().overworld().getGameTime();
        PartyRecord party = PartySavedData.get(level).createParty(leader.getUUID(), now);
        PartySavedData.get(level).addVillager(party, villagerRecord(villager.getUUID(), leader.getUUID(), 0, now));
        BlockPos moveTarget = helper.absolutePos(new BlockPos(6, 2, 2));

        PartyQuickCommandService.handle(leader, new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                PartyQuickCommand.MOVE_TO,
                com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload.NO_ENTITY,
                moveTarget));
        PartyQuickCommandService.onVillagerTickPost(villager);
        helper.assertTrue(PartyQuickCommandService.overridesRecruitmentMovement(villager)
                        && PartyQuickCommandService.moveTarget(party) != null,
                "move-to should establish an active targeted order for the regression setup");
        villager.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(moveTarget), 0.72F, 0));
        helper.assertTrue(villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "regression setup should retain movement intent toward the move-to target");

        PartyQuickCommandService.handle(leader, new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                PartyQuickCommand.REGROUP));
        helper.assertTrue(villager.getNavigation().isDone(),
                "fall-back should immediately stop the active move-to path");
        helper.assertFalse(villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "fall-back should clear movement memory for the old move-to target");
        helper.assertTrue(PartyQuickCommandService.moveTarget(party) == null,
                "fall-back should clear the party move-to marker");
        PartyQuickCommandService.onVillagerTickPost(villager);
        helper.assertFalse(PartyQuickCommandService.overridesRecruitmentMovement(villager),
                "nearby villagers should finish fall-back without resuming the old move-to order");
        helper.assertTrue(villager.getNavigation().isDone(),
                "completed fall-back should leave the old move-to navigation stopped");

        PartyService.deleteParty(level, party.id());
        PartyQuickCommandService.clearRuntimeState();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void partyGatherDropsCollectsItemsAndMoveToOverridesIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer leader = fakePlayer(level, uniqueName("party_gather_drops"));
        movePlayer(helper, leader, new BlockPos(1, 2, 2));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        long now = level.getServer().overworld().getGameTime();
        PartyRecord party = PartySavedData.get(level).createParty(leader.getUUID(), now);
        PartySavedData.get(level).addVillager(
                party,
                villagerRecord(villager.getUUID(), leader.getUUID(), 0, now));

        ItemEntity drop = new ItemEntity(
                level,
                villager.getX(),
                villager.getY(),
                villager.getZ(),
                new ItemStack(Items.DIAMOND, 3));
        drop.setNoPickUpDelay();
        level.addFreshEntity(drop);
        PartyQuickCommandService.handle(
                leader,
                new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                        PartyQuickCommand.PICK_UP_DROPS));
        Zombie gatherTarget = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 2));
        VillagerRetaliationHandler.forceAnger(villager, gatherTarget);
        helper.assertTrue(
                PartyQuickCommandService.suppressesPartyTargetAcquisition(villager),
                "the gather-drops order should suppress party target acquisition");
        PartyQuickCommandService.onVillagerTickPost(villager);
        helper.assertFalse(
                VillagerRetaliationHandler.hasActiveRetaliationTarget(villager),
                "the gather-drops order should clear an active combat target");
        helper.assertValueEqual(
                HiredJobInventory.getJobInventory(villager).countItem(Items.DIAMOND),
                3,
                "the gather-drops order should collect every reachable ground stack");

        BlockPos moveTarget = helper.absolutePos(new BlockPos(6, 2, 2));
        PartyQuickCommandService.handle(
                leader,
                new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                        PartyQuickCommand.PICK_UP_DROPS));
        PartyQuickCommandService.handle(
                leader,
                new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                        PartyQuickCommand.MOVE_TO,
                        com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload.NO_ENTITY,
                        moveTarget));
        helper.assertTrue(
                PartyQuickCommandService.moveTarget(party) != null,
                "move-to should replace an active gather-drops order");

        PartyQuickCommandService.handle(
                leader,
                new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                        PartyQuickCommand.PICK_UP_DROPS));
        PartyQuickCommandService.handle(
                leader,
                new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                        PartyQuickCommand.REGROUP));
        PartyQuickCommandService.onVillagerTickPost(villager);
        helper.assertFalse(
                PartyQuickCommandService.overridesRecruitmentMovement(villager),
                "regroup should replace a gather-drops order and finish once the villager reaches the leader");

        PartyService.deleteParty(level, party.id());
        PartyQuickCommandService.clearRuntimeState();
        villager.discard();
        drop.discard();
        gatherTarget.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void partyLootContainersEmptiesContainersNearPing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer leader = fakePlayer(level, uniqueName("party_loot_containers"));
        movePlayer(helper, leader, new BlockPos(1, 2, 2));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        long now = level.getServer().overworld().getGameTime();
        PartyRecord party = PartySavedData.get(level).createParty(leader.getUUID(), now);
        PartySavedData.get(level).addVillager(
                party,
                villagerRecord(villager.getUUID(), leader.getUUID(), 0, now));
        BlockPos chestPos = helper.absolutePos(new BlockPos(3, 2, 2));
        level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        if (!(level.getBlockEntity(chestPos) instanceof Container chest)) {
            throw new GameTestAssertException("Could not create loot-command chest");
        }
        chest.setItem(0, new ItemStack(Items.EMERALD, 5));

        PartyQuickCommandService.handle(
                leader,
                new com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload(
                        PartyQuickCommand.LOOT_CONTAINERS,
                        com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload.NO_ENTITY,
                        chestPos));
        Zombie lootTarget = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 2));
        VillagerRetaliationHandler.forceAnger(villager, lootTarget);
        helper.assertTrue(
                PartyQuickCommandService.suppressesPartyTargetAcquisition(villager),
                "the loot-container order should suppress party target acquisition");
        PartyQuickCommandService.onVillagerTickPost(villager);
        helper.assertFalse(
                VillagerRetaliationHandler.hasActiveRetaliationTarget(villager),
                "the loot-container order should clear an active combat target");
        helper.assertTrue(chest.isEmpty(),
                "the loot-container order should remove items from a reachable container near the ping");
        helper.assertValueEqual(
                HiredJobInventory.getJobInventory(villager).countItem(Items.EMERALD),
                5,
                "looted container items should enter the villager's party/job inventory");

        PartyService.deleteParty(level, party.id());
        PartyQuickCommandService.clearRuntimeState();
        villager.discard();
        lootTarget.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void civilianCandidateGateDefersVillagersButStillBlocksTradersAndGolems(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        WanderingTrader wanderingTrader = helper.spawn(EntityType.WANDERING_TRADER, new BlockPos(3, 2, 2));
        IronGolem ironGolem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 2));
        var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 2));
        try {
            helper.assertTrue(PartyService.canRecruitedVillagersAssistAgainst(villager),
                    "villager candidates now defer to the centralized allegiance policy");
            helper.assertFalse(PartyService.canRecruitedVillagersAssistAgainst(wanderingTrader),
                    "party aggression must not target wandering traders");
            helper.assertFalse(PartyService.canRecruitedVillagersAssistAgainst(ironGolem),
                    "party aggression must not target village iron golems");
            helper.assertTrue(PartyService.canRecruitedVillagersAssistAgainst(zombie),
                    "party aggression must still target ordinary mobs");
        } finally {
            villager.discard();
            wanderingTrader.discard();
            ironGolem.discard();
            zombie.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void directPlayerHitStillDamagesOwnRecruitAndDirectReputation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fakePlayer(level, uniqueName("party_direct_recruit_hit"));
        Villager recruited = spawnVillager(helper, new BlockPos(2, 2, 2));
        long now = level.getServer().overworld().getGameTime();
        PartyRecord party = PartySavedData.get(level).createParty(player.getUUID(), now);
        try {
            PartySavedData.get(level).addVillager(
                    party,
                    villagerRecord(recruited.getUUID(), player.getUUID(), 0, now));
            float healthBefore = recruited.getHealth();
            int reputationBefore = VillagerReputationManager.getReputation(level, recruited, player.getUUID());

            helper.assertTrue(recruited.hurt(level.damageSources().playerAttack(player), 2.0F),
                    "own recruit must accept direct player damage");
            helper.assertTrue(recruited.getHealth() < healthBefore,
                    "own recruit health must decrease after the direct hit");
            helper.assertTrue(
                    VillagerReputationManager.getReputation(level, recruited, player.getUUID()) < reputationBefore,
                    "own recruit direct reputation must decrease after the direct hit");
        } finally {
            PartyService.deleteParty(level, party.id());
            recruited.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void thirdAbuseIncidentCommitsOneNonlethalDisciplinaryAttempt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fakePlayer(level, uniqueName("party_discipline"));
        movePlayer(helper, player, new BlockPos(3, 2, 2));
        Villager recruited = spawnVillager(helper, new BlockPos(2, 2, 2));
        long now = level.getServer().overworld().getGameTime();
        PartyRecord party = PartySavedData.get(level).createParty(player.getUUID(), now);
        try {
            PartySavedData.get(level).addVillager(
                    party,
                    villagerRecord(recruited.getUUID(), player.getUUID(), 0, now));
            helper.assertValueEqual(VillagerDisciplineService.recordQualifyingHit(level, recruited, player), 1,
                    "first warning count");
            helper.assertFalse(VillagerDisciplineService.hasIncident(recruited.getUUID()),
                    "first warning has no attack incident");
            helper.assertValueEqual(VillagerDisciplineService.recordQualifyingHit(level, recruited, player), 2,
                    "second warning count");
            helper.assertFalse(VillagerDisciplineService.hasIncident(recruited.getUUID()),
                    "second warning has no attack incident");
            helper.assertValueEqual(VillagerDisciplineService.recordQualifyingHit(level, recruited, player), 3,
                    "third warning count");
            helper.assertTrue(VillagerDisciplineService.hasIncident(recruited.getUUID()),
                    "third warning creates a bounded attack incident");

            player.setHealth(1.5F);
            helper.assertTrue(VillagerDisciplineService.tickVillager(recruited),
                    "disciplinary incident should be handled");
            helper.assertTrue(player.getHealth() >= 1.0F, "disciplinary damage must be nonlethal");
            helper.assertFalse(VillagerDisciplineService.hasIncident(recruited.getUUID()),
                    "committed attempt consumes the incident");
            helper.assertTrue(recruited.getTarget() == null, "disciplinary target clears after one attempt");

            VillagerAbuseSavedData serialized = new VillagerAbuseSavedData();
            serialized.recordHit(recruited.getUUID(), player.getUUID(), 10L);
            serialized.recordHit(recruited.getUUID(), player.getUUID(), 20L);
            CompoundTag saved = serialized.save(new CompoundTag(), level.registryAccess());
            VillagerAbuseSavedData loaded = VillagerAbuseSavedData.load(saved, level.registryAccess());
            helper.assertValueEqual(loaded.record(recruited.getUUID(), player.getUUID()).hits(), 2,
                    "abuse warnings survive SavedData serialization");
        } finally {
            VillagerDisciplineService.clearRuntimeState();
            PartyService.deleteParty(level, party.id());
            recruited.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void partyMembershipNoLongerBlanketSuppressesIndirectReputation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fakePlayer(level, uniqueName("party_reputation_exemption"));
        Villager recruited = spawnVillager(helper, new BlockPos(2, 2, 2));
        long now = level.getServer().overworld().getGameTime();
        PartyRecord party = PartySavedData.get(level).createParty(player.getUUID(), now);
        try {
            PartySavedData.get(level).addVillager(
                    party,
                    villagerRecord(recruited.getUUID(), player.getUUID(), 0, now));
            VillagerReputationManager.addWitnessedReputation(
                    level, recruited, player.getUUID(), -10, recruited.blockPosition());
            VillagerReputationManager.addGossipReputation(
                    level, recruited, player.getUUID(), -5, UUID.randomUUID());
            helper.assertValueEqual(
                    VillagerReputationManager.getReputation(level, recruited, player.getUUID()),
                    -15,
                    "party membership alone must not suppress witnessed and gossip consequences");

            VillagerReputationManager.addDirectReputation(level, recruited, player.getUUID(), -4);
            helper.assertValueEqual(
                    VillagerReputationManager.getReputation(level, recruited, player.getUUID()),
                    -19,
                    "directly attacking the recruited villager must still damage its reputation");
        } finally {
            PartyService.deleteParty(level, party.id());
            recruited.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void socialVillageKeyIsPhysicalAndSurvivesUnresolvedRelocation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        VillagerSocialGraphSavedData socialGraph = new VillagerSocialGraphSavedData();
        Villager relocated = null;
        try {
            villager.getBrain().setMemory(
                    MemoryModuleType.HOME,
                    GlobalPos.of(level.dimension(), villager.blockPosition()));
            VillageMembership.clearCache();
            socialGraph.ensureProfile(level, villager);
            String originalVillage = socialGraph.knownVillage(villager.getUUID()).orElse("");
            helper.assertTrue(VillageScopeKeys.isVillageKey(originalVillage),
                    "resident profile must capture a physical village key");

            // GameTests are packed into a shared grid, so move well beyond neighboring test villages.
            BlockPos movedPos = villager.blockPosition().offset(16384, 0, 0);
            level.getChunk(movedPos.getX() >> 4, movedPos.getZ() >> 4);
            UUID villagerId = villager.getUUID();
            villager.discard();
            relocated = EntityType.VILLAGER.create(level);
            if (relocated == null) {
                throw new GameTestAssertException("Could not create relocated villager");
            }
            relocated.setUUID(villagerId);
            relocated.moveTo(movedPos.getX() + 0.5D, movedPos.getY(), movedPos.getZ() + 0.5D, 0.0F, 0.0F);
            relocated.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), movedPos));
            helper.assertTrue(level.addFreshEntity(relocated), "relocated villager must load at its new position");
            VillageMembership.clearCache();
            helper.assertTrue(VillageMembership.resolve(level, relocated).isEmpty(),
                    "isolated relocation must have no currently resolvable physical village");
            socialGraph.ensureProfile(level, relocated);
            String refreshedVillage = socialGraph.knownVillage(villagerId).orElse("");

            helper.assertTrue(VillageScopeKeys.isVillageKey(refreshedVillage),
                    "relocated profile must retain its last physical village key");
            helper.assertValueEqual(refreshedVillage, originalVillage,
                    "social profile preserves the last nonblank village when current membership is unresolved");
        } finally {
            VillageMembership.clearCache();
            villager.discard();
            if (relocated != null) {
                relocated.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void questInstanceAndPendingRewardMarkersSurviveSaveReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID playerId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        ResourceLocation questId = VillagerRetaliation.id("party_reward_persistence_fixture");
        VillagerQuestSavedData data = new VillagerQuestSavedData();
        VillagerQuestSavedData.QuestProgress progress = data.getOrCreate(playerId, questId);
        progress.start(providerId, Level.OVERWORLD, BlockPos.ZERO, 10L);
        progress.linkPartyQuest(instanceId);
        progress.complete(20L, false);
        progress.markPendingPartyReward();

        CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
        VillagerQuestSavedData loaded = VillagerQuestSavedData.load(saved, level.registryAccess());
        VillagerQuestSavedData.QuestProgress restored = loaded.get(playerId, questId);
        helper.assertValueEqual(restored.partyQuestInstanceId(), instanceId, "stable personal shared-instance link");
        helper.assertTrue(restored.pendingPartyReward(), "offline pending reward persistence");
        restored.markPartyRewardClaimed();
        CompoundTag claimedSaved = loaded.save(new CompoundTag(), level.registryAccess());
        VillagerQuestSavedData claimedLoaded = VillagerQuestSavedData.load(claimedSaved, level.registryAccess());
        helper.assertTrue(claimedLoaded.get(playerId, questId).partyRewardClaimed(),
                "at-most-once reward claim persistence");
        helper.assertFalse(claimedLoaded.get(playerId, questId).pendingPartyReward(),
                "claimed reward must clear pending delivery");
        helper.succeed();
    }

    private static PartyVillagerRecord villagerRecord(UUID villagerId, UUID leaderId, int order, long now) {
        return new PartyVillagerRecord(
                villagerId,
                leaderId,
                UUID.randomUUID(),
                order,
                PartyCommandMode.FOLLOW,
                null,
                null,
                now,
                VillagerContractTime.endAfterDays(now, 1),
                1,
                32,
                "Villager " + order,
                "minecraft:farmer",
                Level.OVERWORLD.location(),
                new BlockPos(order, 64, -order));
    }

    private static QuestDefinition.Objective itemObjective(String id, ResourceLocation item, int count) {
        return new QuestDefinition.Objective(
                id,
                QuestDefinition.ObjectiveType.ITEM_CHECK,
                false,
                null,
                Level.OVERWORLD,
                null,
                8,
                List.of(),
                16,
                8,
                item,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                null,
                null,
                QuestFactScope.PLAYER,
                null,
                Set.of(),
                "",
                Set.of(),
                null,
                null,
                count,
                true,
                QuestDefinition.ItemRequirements.EMPTY,
                List.of(),
                QuestDefinition.ObjectiveTracker.EMPTY);
    }

    private static Villager spawnVillager(GameTestHelper helper, BlockPos relativePos) {
        ServerLevel level = helper.getLevel();
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            throw new GameTestAssertException("Could not create villager");
        }
        BlockPos pos = helper.absolutePos(relativePos);
        villager.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        if (!level.addFreshEntity(villager)) {
            throw new GameTestAssertException("Could not add villager to level");
        }
        level.tickNonPassenger(villager);
        return villager;
    }

    private static ServerPlayer fakePlayer(ServerLevel level, String name) {
        UUID id = UUID.nameUUIDFromBytes(("villagerretaliation:" + name).getBytes(StandardCharsets.UTF_8));
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(id, name));
        BlockPos spawn = level.getSharedSpawnPos();
        player.moveTo(spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D, 0.0F, 0.0F);
        return player;
    }

    private static String uniqueName(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static void movePlayer(GameTestHelper helper, ServerPlayer player, BlockPos relativePos) {
        BlockPos pos = helper.absolutePos(relativePos);
        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
    }

    private static void configureGameTestStructures() {
        String configured = System.getProperty("villagerretaliation.gameteststructures");
        if (configured != null && !configured.isBlank()) {
            StructureUtils.testStructuresDir = configured;
            return;
        }
        List<Path> candidates = List.of(
                Path.of("src/main/gameteststructures"),
                Path.of("../src/main/gameteststructures"),
                Path.of("neoforge/src/main/gameteststructures"));
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                StructureUtils.testStructuresDir = candidate.toAbsolutePath().normalize().toString();
                return;
            }
        }
    }
}

package com.jvn.villagerretaliation.gametest;

import com.jvn.villagerretaliation.debug.HiredDebugPreviewService;
import com.jvn.villagerretaliation.combat.WanderingTraderRetaliationHandler;
import com.jvn.villagerretaliation.combat.downed.VillagerDeathProtectionResolver;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceService;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerIndex;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.interaction.VillagerRecruitmentService;
import com.jvn.villagerretaliation.interaction.VillagerWalletService;
import com.jvn.villagerretaliation.item.HiredStorageClipboardItem;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.network.ClipboardWorkAreaActionPayload;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerRules;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.ReactToBell;
import net.minecraft.world.entity.ai.behavior.SetHiddenState;
import net.minecraft.world.entity.ai.behavior.VillagerPanicTrigger;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerGameplayGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    static {
        configureGameTestStructures();
    }

    private VillagerGameplayGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void unprotectedVillagerStillDiesFromLethalDamage(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));

        villager.hurt(helper.getLevel().damageSources().generic(), 1000.0F);

        helper.assertTrue(villager.isDeadOrDying() || villager.isRemoved(), "unprotected villager should die");
        helper.assertFalse(VillagerDownedService.isDowned(villager), "unprotected villager should not be downed");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void essentialVillagerDownsOnceAndRejectsRepeatedDamage(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        villager.addTag(VillagerDeathProtectionResolver.ESSENTIAL_ENTITY_TAG);
        float standingWidth = villager.getBbWidth();
        float standingHeight = villager.getBbHeight();

        villager.hurt(helper.getLevel().damageSources().generic(), 1000.0F);
        helper.assertTrue(VillagerDownedService.isDowned(villager), "essential villager should be downed");
        helper.assertTrue(villager.isAlive(), "downed villager should remain alive");
        helper.assertTrue(villager.getHealth() >= 1.0F, "downed villager should retain at least one health");
        helper.assertTrue(villager.getBbWidth() > standingWidth, "downed hitbox should widen for the grounded pose");
        helper.assertTrue(villager.getBbHeight() < standingHeight, "downed hitbox should lower for the grounded pose");
        float healthAfterLethalHit = villager.getHealth();

        villager.invulnerableTime = 0;
        villager.hurt(helper.getLevel().damageSources().generic(), 5.0F);
        helper.assertValueEqual(villager.getHealth(), healthAfterLethalHit, "repeated damage should not reduce health");
        helper.assertTrue(VillagerDownedService.isDowned(villager), "repeated damage should keep the same downed state");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void activePartyVillagerDownsWithoutLosingContract(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer leader = fakePlayer(level, "VrDownedParty");
        leader.getInventory().add(new ItemStack(Items.EMERALD, 32));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        leader.moveTo(villager.getX(), villager.getY(), villager.getZ(), 0.0F, 0.0F);
        PartyVillagerContractService.ContractResult recruited = PartyVillagerContractService.recruit(leader, villager);
        helper.assertTrue(recruited.success(), "party villager fixture should recruit");

        villager.hurt(level.damageSources().generic(), 1000.0F);

        helper.assertTrue(VillagerDownedService.isDowned(villager), "active party villager should be downed");
        helper.assertTrue(villager.isAlive(), "active party villager should remain alive");
        helper.assertTrue(
                PartyVillagerContractService.isActivePartyVillager(level, villager),
                "downed transition should preserve the party contract");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void downedStateSurvivesEntitySerializationAndRestoresPriorFlags(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager original = spawnVillager(helper, new BlockPos(1, 2, 1));
        original.setCanPickUpLoot(true);
        VillagerDownedService.enterDowned(
                level,
                original,
                new VillagerDeathProtectionResolver.ProtectionResult(true, List.of("test")));
        CompoundTag saved = new CompoundTag();
        original.saveWithoutId(saved);

        Villager loaded = EntityType.VILLAGER.create(level);
        if (loaded == null) {
            throw new GameTestAssertException("Could not create serialized villager");
        }
        loaded.load(saved);
        VillagerDownedService.onVillagerLoaded(loaded);
        helper.assertTrue(VillagerDownedService.isDowned(loaded), "serialized villager should remain downed");
        helper.assertTrue(loaded.isNoAi(), "loaded downed villager should remain incapacitated");
        float downedWidth = loaded.getBbWidth();
        float downedHeight = loaded.getBbHeight();

        VillagerDownedService.recover(loaded);
        helper.assertFalse(VillagerDownedService.isDowned(loaded), "recovery should clear persisted state");
        helper.assertFalse(loaded.isNoAi(), "recovery should restore the prior AI flag");
        helper.assertTrue(loaded.canPickUpLoot(), "recovery should restore the prior pickup flag");
        helper.assertTrue(loaded.getBbWidth() < downedWidth, "recovery should restore the standing hitbox width");
        helper.assertTrue(loaded.getBbHeight() > downedHeight, "recovery should restore the standing hitbox height");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void operatorKillBypassesEssentialProtection(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        villager.addTag(VillagerDeathProtectionResolver.ESSENTIAL_ENTITY_TAG);

        villager.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);

        helper.assertTrue(villager.isDeadOrDying() || villager.isRemoved(), "generic kill should bypass protection");
        helper.assertFalse(VillagerDownedService.isDowned(villager), "generic kill should not enter the downed state");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void downedVillagerSuspendsInteractionAndClearsRetargeting(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fakePlayer(level, "VrDownedInteraction");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        Zombie zombie = spawnZombie(helper, new BlockPos(4, 2, 2));
        VillagerDownedService.enterDowned(
                level,
                villager,
                new VillagerDeathProtectionResolver.ProtectionResult(true, List.of("test")));

        helper.assertFalse(
                VillagerInteractionService.canUseInteractionSystem(player, villager),
                "downed villager should reject server-side interaction validation");
        helper.assertValueEqual(
                VillagerInteractionService.handleVillagerRightClick(villager, player),
                net.minecraft.world.InteractionResult.FAIL,
                "downed right-click result");

        zombie.setTarget(villager);
        zombie.setNoAi(true);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(zombie.getTarget() == null, "periodic fallback should clear hostile retargeting");
            helper.assertTrue(villager.isNoAi(), "downed villager should remain AI-suspended");
            helper.assertFalse(villager.canPickUpLoot(), "downed villager should not pick up items");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void hiredContractIndexesClipboardWorkforce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HiredVillagerIndex.clearRuntimeState();

        ServerPlayer player = fakePlayer(level, "VrWorkerIndex");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));

        HiredVillagerContractService.startHireContract(level, villager, player, 1, 8);
        helper.assertTrue(HiredVillagerContractService.isHiredBy(level, villager, player), "contract should be active");
        helper.assertTrue(HiredVillagerIndex.find(player, villager.getUUID()).isPresent(), "hired villager should be indexed");

        ClipboardWorkforceSnapshot snapshot = ClipboardWorkforceService.snapshot(player);
        helper.assertValueEqual(snapshot.totalHired(), 1, "clipboard workforce total");
        helper.assertValueEqual(snapshot.workers().size(), 1, "clipboard worker rows");
        helper.assertValueEqual(snapshot.workers().getFirst().villagerId(), villager.getUUID(), "clipboard worker id");

        HiredVillagerContractService.endHireContract(level, villager, player);
        helper.assertTrue(HiredVillagerIndex.find(player, villager.getUUID()).isEmpty(), "ended contract should leave index");
        helper.assertValueEqual(ClipboardWorkforceService.snapshot(player).totalHired(), 0, "clipboard total after end");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void clipboardPreviewPacketRequiresHeldClipboard(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HiredDebugPreviewService.clearRuntimeState();

        ServerPlayer player = fakePlayer(level, "VrPreviewGuard");
        HiredDebugPreviewService.DebugPreviewSummary rejected =
                HiredDebugPreviewService.setClipboardPreviewEnabled(player, true);
        helper.assertFalse(rejected.enabled(), "preview should reject players without a held clipboard");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VillagerRetaliationItems.CLIPBOARD.get()));
        HiredDebugPreviewService.DebugPreviewSummary accepted =
                HiredDebugPreviewService.setClipboardPreviewEnabled(player, true);
        helper.assertTrue(accepted.enabled(), "preview should accept a held clipboard");

        HiredDebugPreviewService.DebugPreviewSummary repeated =
                HiredDebugPreviewService.setClipboardPreviewEnabled(player, true);
        helper.assertTrue(repeated.enabled(), "repeated enable should stay enabled");

        HiredDebugPreviewService.setClipboardPreviewEnabled(player, false);
        HiredDebugPreviewService.clearRuntimeState();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void hiredContractPaymentStaysConservedWhenEndedEarly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "VrHireEscrow");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        int payment = 20;
        int walletBefore = VillagerWalletService.getCurrentEmeralds(villager);

        HiredVillagerContractService.startHireContract(level, villager, hirer, 10, payment);
        helper.assertValueEqual(
                VillagerWalletService.getCurrentEmeralds(villager),
                walletBefore,
                "unearned hire payment should remain in escrow");

        int refund = HiredVillagerContractService.endHireContract(level, villager, hirer);
        int walletIncrease = VillagerWalletService.getCurrentEmeralds(villager) - walletBefore;
        helper.assertValueEqual(
                walletIncrease + refund,
                payment,
                "early cancellation should settle the original payment exactly once");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void hiredContractsAndFollowCommandsDoNotOverwriteEachOther(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer followerOwner = fakePlayer(level, "VrFollowOwner");
        ServerPlayer otherPlayer = fakePlayer(level, "VrFollowOther");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));

        helper.assertTrue(
                VillagerRecruitmentService.startFollowing(level, villager, followerOwner),
                "uncommitted villager should accept a follow command");
        helper.assertFalse(
                VillagerRecruitmentService.stopFollowing(level, villager, otherPlayer),
                "another player should not clear the owner's follow state");
        helper.assertTrue(
                VillagerRecruitmentService.isFollowing(villager, followerOwner),
                "rejected stop command should preserve the original follower owner");

        HiredVillagerContractService.startHireContract(level, villager, followerOwner, 1, 8);
        helper.assertFalse(
                VillagerRecruitmentService.isFollowingAnyPlayer(villager),
                "hiring should clear the previous follow state");
        helper.assertFalse(
                VillagerRecruitmentService.startFollowing(level, villager, otherPlayer),
                "a hired worker should reject new follow commands");

        HiredVillagerContractService.endHireContract(level, villager, followerOwner);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void clipboardWorkAreaPacketsRequireOwnerAndHeldClipboard(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HiredVillagerIndex.clearRuntimeState();

        ServerPlayer hirer = fakePlayer(level, "VrWorkAreaOwner");
        ServerPlayer otherPlayer = fakePlayer(level, "VrWorkAreaOther");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));

        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8);
        HiredVillagerWorkService.initializeWorkArea(level, villager);
        HiredWorkArea original = HiredVillagerWorkService.workArea(level, villager);

        otherPlayer.setItemInHand(InteractionHand.MAIN_HAND, clipboard());
        VillagerInteractionService.handleClipboardWorkAreaAction(
                otherPlayer,
                villager.getUUID(),
                ClipboardWorkAreaActionPayload.Action.SET_CENTER_HERE,
                5);
        assertWorkAreaUnchanged(helper, level, villager, original, "non-hirer packet");

        VillagerInteractionService.handleClipboardWorkAreaAction(
                hirer,
                villager.getUUID(),
                ClipboardWorkAreaActionPayload.Action.SET_CENTER_HERE,
                5);
        assertWorkAreaUnchanged(helper, level, villager, original, "missing clipboard packet");

        BlockPos requestedCenter = helper.absolutePos(new BlockPos(5, 2, 5));
        hirer.moveTo(
                requestedCenter.getX() + 0.5D,
                requestedCenter.getY(),
                requestedCenter.getZ() + 0.5D,
                0.0F,
                0.0F);
        hirer.setItemInHand(InteractionHand.MAIN_HAND, clipboard());
        VillagerInteractionService.handleClipboardWorkAreaAction(
                hirer,
                villager.getUUID(),
                ClipboardWorkAreaActionPayload.Action.SET_CENTER_HERE,
                5);
        helper.assertValueEqual(
                HiredVillagerWorkService.workArea(level, villager).center(),
                requestedCenter,
                "owner with held clipboard should be allowed to manage the work area");

        HiredVillagerContractService.endHireContract(level, villager, hirer);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void clipboardWorkforceActionAppliesHeldDraft(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HiredVillagerIndex.clearRuntimeState();

        ServerPlayer hirer = fakePlayer(level, "VrWorkAreaDraft");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 8);
        HiredVillagerWorkService.initializeWorkArea(level, villager);

        ItemStack clipboard = clipboard();
        HiredStorageClipboardItem.cycleMode(clipboard, 1);
        HiredStorageClipboardItem.cycleMode(clipboard, 1);
        HiredStorageClipboardItem.cycleMode(clipboard, 1);
        helper.assertValueEqual(
                HiredStorageClipboardItem.mode(clipboard),
                HiredStorageClipboardItem.ClipboardMode.SET_WORK_AREA,
                "clipboard mode");
        hirer.setItemInHand(InteractionHand.MAIN_HAND, clipboard);

        BlockPos first = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos second = helper.absolutePos(new BlockPos(6, 4, 6));
        HiredStorageClipboardItem.handleLeftClickBlock(level, hirer, clipboard, first);
        HiredStorageClipboardItem.handleRightClickBlock(level, hirer, clipboard, second);

        VillagerInteractionService.handleClipboardWorkAreaAction(
                hirer,
                villager.getUUID(),
                ClipboardWorkAreaActionPayload.Action.APPLY_HELD_DRAFT,
                1);

        HiredWorkArea applied = HiredVillagerWorkService.workArea(level, villager);
        helper.assertValueEqual(applied.min(), HiredWorkArea.minPos(first, second), "applied draft min");
        helper.assertValueEqual(applied.max(), HiredWorkArea.maxPos(first, second), "applied draft max");
        helper.assertTrue(applied.explicitlyAssigned(), "applied draft should become the explicit work site");
        helper.assertTrue(HiredStorageClipboardItem.selectedWorkArea(clipboard).first() == null, "applied draft should clear held clipboard draft");

        HiredVillagerContractService.endHireContract(level, villager, hirer);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void panicMixinKeepsArmedVillagerOutOfVanillaPanic(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        ItemStack weapon = new ItemStack(Items.IRON_SWORD);

        VillagerRetaliationVillagerEquipment.setPickedUpMainHand(villager, weapon.copy());
        helper.assertTrue(VillagerRetaliationVillagerRules.shouldSuppressVanillaFleeBehavior(villager), "armed villager fixture should suppress vanilla fleeing");
        Villager mate = spawnVillager(helper, new BlockPos(2, 2, 1));
        Zombie hostile = spawnZombie(helper, new BlockPos(4, 2, 1));
        villager.getBrain().setMemory(MemoryModuleType.NEAREST_HOSTILE, hostile);
        villager.getBrain().setMemory(MemoryModuleType.BREED_TARGET, mate);

        new VillagerPanicTrigger().tryStart(level, villager, level.getGameTime());

        helper.assertFalse(villager.getBrain().isActive(Activity.PANIC), "armed villager should not enter vanilla panic");
        helper.assertFalse(villager.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE), "panic trigger should clear threat memory after suppression");
        helper.assertTrue(villager.getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET), "suppressed panic should preserve breeding target memory");
        helper.assertTrue(ItemStack.isSameItemSameComponents(villager.getMainHandItem(), weapon), "suppressed panic should preserve main hand weapon");

        hostile.discard();
        mate.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void hideMixinsKeepArmedVillagerOutOfBellAndHiddenState(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        VillagerRetaliationVillagerEquipment.setPickedUpMainHand(villager, new ItemStack(Items.IRON_SWORD));
        helper.assertTrue(VillagerRetaliationVillagerRules.shouldSuppressVanillaFleeBehavior(villager), "armed villager fixture should suppress vanilla fleeing");

        villager.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, level.getGameTime());
        BehaviorControl<LivingEntity> bellReaction = ReactToBell.create();
        bellReaction.tryStart(level, villager, level.getGameTime());

        helper.assertFalse(villager.getBrain().isActive(Activity.HIDE), "armed villager should not enter vanilla hide after bell");
        helper.assertFalse(villager.getBrain().hasMemoryValue(MemoryModuleType.HEARD_BELL_TIME), "suppressed bell hide should clear bell memory");

        villager.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, level.getGameTime());
        villager.getBrain().setMemory(MemoryModuleType.HIDING_PLACE, GlobalPos.of(level.dimension(), villager.blockPosition()));
        BehaviorControl<LivingEntity> hiddenState = SetHiddenState.create(15, 3);
        hiddenState.tryStart(level, villager, level.getGameTime());

        helper.assertFalse(villager.getBrain().isActive(Activity.HIDE), "armed villager should not remain in vanilla hidden state");
        helper.assertFalse(villager.getBrain().hasMemoryValue(MemoryModuleType.HIDING_PLACE), "suppressed hidden state should clear hiding place");
        helper.assertFalse(villager.getBrain().hasMemoryValue(MemoryModuleType.HEARD_BELL_TIME), "suppressed hidden state should clear bell memory");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void combatTargetSuppressesVanillaBrainTick(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        Zombie hostile = spawnZombie(helper, new BlockPos(4, 2, 1));

        helper.assertFalse(
                VillagerRetaliationVillagerBrainUtil.shouldSuppressVanillaBrainTickForCombat(villager),
                "idle villager should keep vanilla brain tick"
        );
        villager.setTarget(hostile);
        helper.assertTrue(
                VillagerRetaliationVillagerBrainUtil.shouldSuppressVanillaBrainTickForCombat(villager),
                "active combat target should suppress vanilla brain tick"
        );
        villager.setTarget(null);
        helper.assertFalse(
                VillagerRetaliationVillagerBrainUtil.shouldSuppressVanillaBrainTickForCombat(villager),
                "villager without combat target should resume vanilla brain tick"
        );

        hostile.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void traderAvoidanceMixinsStopVanillaPanicAndAvoidGoals(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WanderingTrader trader = spawnWanderingTrader(helper, new BlockPos(1, 2, 1));
        Zombie hostile = spawnZombie(helper, new BlockPos(4, 2, 1));

        invokeTraderAnger(trader, hostile);
        helper.assertTrue(WanderingTraderRetaliationHandler.shouldSuppressVanillaAvoidance(trader), "angered trader should suppress vanilla avoidance");

        AvoidEntityGoal<Zombie> avoidGoal = new AvoidEntityGoal<>(trader, Zombie.class, 8.0F, 0.5D, 0.5D);
        helper.assertFalse(avoidGoal.canUse(), "angered trader should not start vanilla avoid goal");

        trader.hurt(level.damageSources().mobAttack(hostile), 1.0F);
        PanicGoal panicGoal = new PanicGoal(trader, 0.5D);
        helper.assertFalse(panicGoal.canUse(), "angered trader should not start vanilla panic goal");

        hostile.discard();
        trader.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void repeatableServerRequestsAreBoundedPerPlayerAndKind(GameTestHelper helper) {
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        ResourceLocation profile = ResourceLocation.fromNamespaceAndPath("villagerretaliation", "profile_test");
        ResourceLocation reputation = ResourceLocation.fromNamespaceAndPath("villagerretaliation", "reputation_test");
        ServerboundRequestLimiter.clearRuntimeState();

        helper.assertTrue(ServerboundRequestLimiter.tryAcquire(firstPlayer, profile, 100L, 5L),
                "the first request should be accepted");
        helper.assertFalse(ServerboundRequestLimiter.tryAcquire(firstPlayer, profile, 104L, 5L),
                "a repeated request inside the interval should be rejected");
        helper.assertTrue(ServerboundRequestLimiter.tryAcquire(firstPlayer, profile, 105L, 5L),
                "the request should be accepted at the interval boundary");
        helper.assertTrue(ServerboundRequestLimiter.tryAcquire(firstPlayer, reputation, 104L, 5L),
                "different request kinds should be independent");
        helper.assertTrue(ServerboundRequestLimiter.tryAcquire(secondPlayer, profile, 104L, 5L),
                "different players should be independent");

        ServerboundRequestLimiter.clear(firstPlayer);
        helper.assertTrue(ServerboundRequestLimiter.tryAcquire(firstPlayer, profile, 104L, 5L),
                "disconnect cleanup should release the player's request state");
        ServerboundRequestLimiter.clearRuntimeState();
        helper.succeed();
    }

    private static ServerPlayer fakePlayer(ServerLevel level, String name) {
        UUID id = UUID.nameUUIDFromBytes(("villagerretaliation:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(id, name));
        BlockPos spawn = level.getSharedSpawnPos();
        player.moveTo(spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D, 0.0F, 0.0F);
        return player;
    }

    private static ItemStack clipboard() {
        return new ItemStack(VillagerRetaliationItems.CLIPBOARD.get());
    }

    private static void assertWorkAreaUnchanged(
            GameTestHelper helper,
            ServerLevel level,
            Villager villager,
            HiredWorkArea expected,
            String label) {
        HiredWorkArea actual = HiredVillagerWorkService.workArea(level, villager);
        helper.assertValueEqual(actual.center(), expected.center(), label + " center");
        helper.assertValueEqual(actual.min(), expected.min(), label + " min");
        helper.assertValueEqual(actual.max(), expected.max(), label + " max");
        helper.assertValueEqual(actual.horizontalRadius(), expected.horizontalRadius(), label + " horizontal radius");
        helper.assertValueEqual(actual.verticalRadius(), expected.verticalRadius(), label + " vertical radius");
        helper.assertValueEqual(actual.explicitlyAssigned(), expected.explicitlyAssigned(), label + " assigned flag");
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
        return villager;
    }

    private static WanderingTrader spawnWanderingTrader(GameTestHelper helper, BlockPos relativePos) {
        ServerLevel level = helper.getLevel();
        WanderingTrader trader = EntityType.WANDERING_TRADER.create(level);
        if (trader == null) {
            throw new GameTestAssertException("Could not create wandering trader");
        }
        BlockPos pos = helper.absolutePos(relativePos);
        trader.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        if (!level.addFreshEntity(trader)) {
            throw new GameTestAssertException("Could not add wandering trader to level");
        }
        return trader;
    }

    private static Zombie spawnZombie(GameTestHelper helper, BlockPos relativePos) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            throw new GameTestAssertException("Could not create zombie");
        }
        BlockPos pos = helper.absolutePos(relativePos);
        zombie.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        if (!level.addFreshEntity(zombie)) {
            throw new GameTestAssertException("Could not add zombie to level");
        }
        return zombie;
    }

    private static void invokeTraderAnger(WanderingTrader trader, LivingEntity attacker) {
        try {
            Method method = WanderingTraderRetaliationHandler.class.getDeclaredMethod(
                    "anger",
                    WanderingTrader.class,
                    LivingEntity.class
            );
            method.setAccessible(true);
            method.invoke(null, trader, attacker);
        } catch (ReflectiveOperationException exception) {
            throw new GameTestAssertException("Could not invoke WanderingTraderRetaliationHandler.anger: " + exception);
        }
    }

    private static void configureGameTestStructures() {
        String configured = System.getProperty("villagerretaliation.gameteststructures");
        if (configured != null && !configured.isBlank()) {
            StructureUtils.testStructuresDir = configured;
            return;
        }

        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of("src/main/gameteststructures"));
        candidates.add(Path.of("../src/main/gameteststructures"));
        candidates.add(Path.of("neoforge/src/main/gameteststructures"));
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                StructureUtils.testStructuresDir = candidate.toAbsolutePath().normalize().toString();
                return;
            }
        }
    }
}

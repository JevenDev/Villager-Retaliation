package com.jvn.villagerretaliation.gametest;

import com.jvn.villagerretaliation.debug.HiredDebugPreviewService;
import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.combat.WanderingTraderRetaliationHandler;
import com.jvn.villagerretaliation.combat.downed.VillagerDeathProtectionResolver;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedPose;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceService;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerIndex;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.interaction.VillagerRecruitmentService;
import com.jvn.villagerretaliation.interaction.VillagerWalletService;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.item.HiredStorageClipboardItem;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.network.ClipboardWorkAreaActionPayload;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.reputation.VillagerAggressionPolicy;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.reputation.VillagerReputationTradePricing;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerRules;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
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
    public static void villagerMendingArmorAttractsAndConsumesExperience(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        ItemStack armor = new ItemStack(Items.IRON_CHESTPLATE);
        var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        armor.enchant(enchantments.getOrThrow(Enchantments.MENDING), 1);
        armor.setDamageValue(20);
        villager.setItemSlot(EquipmentSlot.CHEST, armor);

        ExperienceOrb orb = helper.spawn(EntityType.EXPERIENCE_ORB, 2, 2, 1);
        orb.value = 5;
        orb.setDeltaMovement(Vec3.ZERO);
        orb.tickCount = 1;
        orb.tick();
        Vec3 offset = villager.position().subtract(orb.position());
        boolean attracted = orb.getDeltaMovement().x * offset.x
                + orb.getDeltaMovement().z * offset.z > 0.0D;
        helper.assertTrue(attracted, "damaged villager Mending armor should attract nearby experience orbs");

        orb.setPos(villager.position());
        orb.setDeltaMovement(Vec3.ZERO);
        orb.tick();
        helper.assertValueEqual(armor.getDamageValue(), 10, "five XP should repair ten armor durability");
        helper.assertTrue(orb.isRemoved(), "the villager should consume the experience orb after repairing armor");
        helper.succeed();
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
    public static void downedHitboxMovesAwayFromAdjacentBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos villagerPos = helper.absolutePos(new BlockPos(2, 2, 2));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        villager.moveTo(villagerPos.getX() + 0.65D, villagerPos.getY(), villagerPos.getZ() + 0.5D);
        level.setBlock(villagerPos.east(), Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(level.noCollision(villager), "standing villager fixture should not intersect the wall");
        double standingX = villager.getX();

        VillagerDownedService.enterDowned(
                level,
                villager,
                new VillagerDeathProtectionResolver.ProtectionResult(true, List.of("test")));

        helper.assertTrue(level.noCollision(villager), "expanded downed hitbox should remain outside the wall");
        helper.assertTrue(villager.getX() < standingX, "downed resize should move the villager away from the wall");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void activePartyVillagerDownsInsteadOfConvertingFromZombieAttack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer leader = fakePlayer(level, "VrZombieProofParty");
        leader.getInventory().add(new ItemStack(Items.EMERALD, 32));
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        Zombie zombie = spawnZombie(helper, new BlockPos(3, 2, 2));
        leader.moveTo(villager.getX(), villager.getY(), villager.getZ(), 0.0F, 0.0F);
        PartyVillagerContractService.ContractResult recruited = PartyVillagerContractService.recruit(leader, villager);
        helper.assertTrue(recruited.success(), "party villager fixture should recruit");

        zombie.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1000.0D);
        helper.assertTrue(zombie.doHurtTarget(villager), "lethal zombie attack should land");

        helper.assertTrue(VillagerDownedService.isDowned(villager), "party villager should enter the downed state");
        helper.assertTrue(villager.isAlive(), "party villager should survive the lethal zombie attack");
        helper.assertFalse(villager.isRemoved(), "party villager should not be replaced by a zombie villager");
        helper.assertTrue(
                PartyVillagerContractService.isActivePartyVillager(level, villager),
                "zombie attack should preserve the party contract");
        villager.discard();
        zombie.discard();
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
        VillagerDownedPose selectedPose = VillagerDownedService.pose(original);
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
        helper.assertValueEqual(VillagerDownedService.pose(loaded), selectedPose,
                "serialized villager should preserve its selected downed pose");
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
    public static void villagerOnlyEatsBelowHalfHungerOutsideCombat(GameTestHelper helper) {
        Villager halfHunger = spawnVillager(helper, new BlockPos(1, 2, 1));
        setRecoveryState(halfHunger, 10, 0.0F);
        VillagerInventoryAccess.addItem(halfHunger, new ItemStack(Items.BREAD));

        helper.assertFalse(
                VillagerRecoveryService.onVillagerTickPost(halfHunger),
                "a healthy villager at half hunger should not begin recovery");
        helper.assertTrue(
                VillagerInventoryAccess.hasCarriedItem(halfHunger, stack -> stack.is(Items.BREAD)),
                "food should remain stored at exactly half hunger");
        helper.assertFalse(halfHunger.isUsingItem(), "villager should not eat at exactly half hunger");

        Villager combatant = spawnVillager(helper, new BlockPos(3, 2, 1));
        Zombie target = spawnZombie(helper, new BlockPos(5, 2, 1));
        setRecoveryState(combatant, 9, 0.0F);
        VillagerInventoryAccess.addItem(combatant, new ItemStack(Items.BREAD));
        combatant.setTarget(target);

        helper.assertFalse(
                VillagerRecoveryService.onVillagerTickPost(combatant),
                "a healthy villager should not leave combat to eat");
        helper.assertTrue(
                VillagerInventoryAccess.hasCarriedItem(combatant, stack -> stack.is(Items.BREAD)),
                "combat should preserve the stored food");
        helper.assertFalse(combatant.isUsingItem(), "villager should not eat during combat");

        VillagerRecoveryService.onVillagerUnloaded(halfHunger);
        VillagerRecoveryService.onVillagerUnloaded(combatant);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void villagerFoodUseLastsForItemAnimation(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        ItemStack bread = new ItemStack(Items.BREAD);
        int useTicks = Math.max(2, bread.getUseDuration(villager)) - 1;
        setRecoveryState(villager, 9, 0.0F);
        VillagerInventoryAccess.addItem(villager, bread.copy());

        helper.assertTrue(
                VillagerRecoveryService.onVillagerTickPost(villager),
                "starting a meal should reserve the tick from loadout maintenance");
        helper.assertTrue(villager.isUsingItem(), "villager should begin the eating animation");
        helper.assertTrue(villager.getMainHandItem().is(Items.BREAD), "food should remain visible while eating");
        helper.assertValueEqual(VillagerRecoveryService.foodLevel(villager), 9, "food is not applied immediately");

        for (int tick = 1; tick < useTicks; tick++) {
            VillagerRecoveryService.onVillagerTickPost(villager);
        }
        helper.assertTrue(villager.isUsingItem(), "eating animation should remain active until its final tick");
        helper.assertValueEqual(VillagerRecoveryService.foodLevel(villager), 9, "food waits for the full use duration");

        VillagerRecoveryService.onVillagerTickPost(villager);
        helper.assertFalse(villager.isUsingItem(), "eating animation should stop after its full duration");
        helper.assertValueEqual(VillagerRecoveryService.foodLevel(villager), 14, "bread nutrition applies after eating");

        VillagerRecoveryService.onVillagerUnloaded(villager);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void villagerFoodUseRecoversInterruptedVisualState(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        setRecoveryState(villager, 9, 0.0F);
        VillagerInventoryAccess.addItem(villager, new ItemStack(Items.BREAD));
        VillagerRecoveryService.onVillagerTickPost(villager);

        VillagerRetaliationVillagerEquipment.setVisualMainHand(villager, new ItemStack(Items.IRON_SWORD));
        villager.stopUsingItem();
        VillagerRecoveryService.onVillagerTickPost(villager);

        helper.assertTrue(villager.isUsingItem(), "an interrupted eating animation should resume");
        helper.assertTrue(villager.getMainHandItem().is(Items.BREAD), "food visual should survive equipment maintenance");
        helper.assertValueEqual(VillagerRecoveryService.foodLevel(villager), 9, "resuming should not consume food early");

        VillagerRecoveryService.onVillagerUnloaded(villager);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void repeatableServerRequestsAreBoundedPerPlayerAndKind(GameTestHelper helper) {
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        ResourceLocation profile = ResourceLocation.fromNamespaceAndPath("villagerretaliation", "profile_test");
        ResourceLocation reputation = ResourceLocation.fromNamespaceAndPath("villagerretaliation", "reputation_test");
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
        ServerboundRequestLimiter.clear(firstPlayer);
        ServerboundRequestLimiter.clear(secondPlayer);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void invisibleEntitiesCannotBecomeRetaliationTargets(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper.getLevel(), "VrInvisibleTarget");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        player.setInvisible(true);

        helper.assertFalse(
                VillagerRetaliationHandler.engageCustomTarget(villager, player, false),
                "an invisible entity should be rejected as a custom retaliation target");
        VillagerRetaliationHandler.forceAngerSilently(villager, player);
        helper.assertFalse(
                VillagerRetaliationHandler.hasActiveRetaliationTarget(villager),
                "direct anger should not acquire an invisible entity");

        player.setInvisible(false);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void invisibilityClearsExistingRetaliation(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper.getLevel(), "VrInvisibleEscape");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        player.setInvisible(false);
        VillagerRetaliationHandler.forceAngerSilently(villager, player);
        helper.assertTrue(
                VillagerRetaliationHandler.hasActiveRetaliationTarget(villager),
                "the visible player should be acquired before becoming invisible");

        player.setInvisible(true);
        helper.assertFalse(
                VillagerRetaliationHandler.isHostileTowards(villager, player),
                "an invisible player should not remain hostile");
        helper.assertFalse(
                VillagerRetaliationHandler.hasActiveRetaliationTarget(villager),
                "checking hostility should clear the concealed retaliation target");

        player.setInvisible(false);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void visibleArmorAllowsRetaliationAgainstInvisiblePlayer(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper.getLevel(), "VrArmoredInvisibleTarget");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        player.setInvisible(true);

        VillagerRetaliationHandler.forceAngerSilently(villager, player);

        helper.assertTrue(
                VillagerRetaliationHandler.hasActiveRetaliationTarget(villager),
                "visible armor should let a villager retaliate against its invisible wearer");
        helper.assertTrue(
                VillagerRetaliationHandler.isHostileTowards(villager, player),
                "an armored invisible attacker should remain hostile while retaliation is active");
        VillagerRetaliationHandler.clearCustomTarget(villager);
        player.setInvisible(false);
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void invisibleTradingUsesAnonymousPrices(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper.getLevel(), "VrInvisiblePrices");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 10),
                new ItemStack(Items.BREAD),
                12,
                2,
                0.05F
        );
        offer.addToSpecialPriceDiff(-5);
        villager.getOffers().add(offer);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        player.setInvisible(true);

        VillagerReputationTradePricing.refreshPricesForPlayer(helper.getLevel(), villager, player);

        helper.assertValueEqual(
                offer.getSpecialPriceDiff(),
                0,
                "anonymous trading should remove every player-specific price adjustment");
        player.setInvisible(false);
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void damageAttributionDoesNotUseStaleKillCredit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fakePlayer(level, "VrDamageAttribution");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        player.moveTo(villager.getX(), villager.getY(), villager.getZ() + 1.0D, 0.0F, 0.0F);

        helper.assertTrue(
                villager.hurt(level.damageSources().playerAttack(player), 1.0F),
                "player hit should seed vanilla kill credit");
        helper.assertTrue(villager.getKillCredit() == player, "fixture should retain player kill credit");
        int reputationAfterPlayerHit = VillagerReputationManager.getReputation(level, villager, player.getUUID());
        helper.assertTrue(reputationAfterPlayerHit < 0, "player hit should apply the direct reputation penalty");
        helper.assertTrue(
                VillagerRetaliationVillagerCombatUtil.resolveDamageAttacker(
                                villager, level.damageSources().generic())
                        .isEmpty(),
                "attackerless damage must not inherit stale kill credit");
        helper.assertTrue(
                VillagerRetaliationVillagerCombatUtil.resolveDeathAttacker(
                                villager, level.damageSources().generic())
                        .orElse(null) == player,
                "death attribution should retain vanilla kill-credit fallback");

        villager.invulnerableTime = 0;
        helper.assertTrue(
                villager.hurt(level.damageSources().generic(), 1.0F),
                "attackerless follow-up damage should be applied");
        helper.assertValueEqual(
                VillagerReputationManager.getReputation(level, villager, player.getUUID()),
                reputationAfterPlayerHit,
                "attackerless follow-up damage must not penalize the stale credited player");

        VillagerReputationManager.setReputation(level, villager, player.getUUID(), 0);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void despisedKillOnSightToggleControlsGolemAggressionPolicy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fakePlayer(level, "VrGolemAggressionToggle");
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        boolean previousReputationEnabled = VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get();
        boolean previousKillOnSightEnabled = VillagerRetaliationConfig.ENABLE_DESPISED_KILL_ON_SIGHT.get();

        try {
            VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.set(true);
            VillagerReputationManager.setReputation(
                    level,
                    villager,
                    player.getUUID(),
                    VillagerRetaliationConfig.DESPISED_THRESHOLD.get());

            VillagerRetaliationConfig.ENABLE_DESPISED_KILL_ON_SIGHT.set(false);
            helper.assertFalse(
                    VillagerAggressionPolicy.shouldIronGolemsTargetNegativeReputationPlayer(villager, player),
                    "disabled kill on sight should suppress reputation-driven golem aggression");

            VillagerRetaliationConfig.ENABLE_DESPISED_KILL_ON_SIGHT.set(true);
            helper.assertTrue(
                    VillagerAggressionPolicy.shouldIronGolemsTargetNegativeReputationPlayer(villager, player),
                    "enabled kill on sight should allow golems to target a despised player");
        } finally {
            VillagerReputationManager.setReputation(level, villager, player.getUUID(), 0);
            VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.set(previousReputationEnabled);
            VillagerRetaliationConfig.ENABLE_DESPISED_KILL_ON_SIGHT.set(previousKillOnSightEnabled);
            villager.discard();
        }

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

    private static void setRecoveryState(Villager villager, int food, float saturation) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Food", food);
        tag.putFloat("Saturation", saturation);
        tag.putFloat("Exhaustion", 0.0F);
        tag.putInt("HealTimer", 0);
        villager.getPersistentData().put("VillagerRetaliationRecovery", tag);
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

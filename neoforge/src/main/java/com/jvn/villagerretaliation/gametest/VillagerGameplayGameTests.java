package com.jvn.villagerretaliation.gametest;

import com.jvn.villagerretaliation.debug.HiredDebugPreviewService;
import com.jvn.villagerretaliation.entity.VillagerFishingHook;
import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.combat.WanderingTraderRetaliationHandler;
import com.jvn.villagerretaliation.combat.downed.VillagerDeathProtectionResolver;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedPose;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionSavedData;
import com.jvn.villagerretaliation.event.VillagerDeathMessageFactory;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceService;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerIndex;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.interaction.VillagerRecruitmentService;
import com.jvn.villagerretaliation.interaction.RecruitmentPolicy;
import com.jvn.villagerretaliation.interaction.work.HiredRoleWorkerRegistry;
import com.jvn.villagerretaliation.interaction.VillagerAssignmentCommand;
import com.jvn.villagerretaliation.interaction.VillagerAssignmentService;
import com.jvn.villagerretaliation.interaction.VillagerAssignmentStore;
import com.jvn.villagerretaliation.interaction.VillagerAssignmentState;
import com.jvn.villagerretaliation.interaction.VillagerWalletService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.item.HiredStorageClipboardItem;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.network.ClipboardWorkAreaActionPayload;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.network.VillagerGiftRequestPayload;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.reputation.VillagerAggressionPolicy;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.reputation.VillagerReputationTradePricing;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerArmor;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerRules;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
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
import net.minecraft.world.entity.item.ItemEntity;
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
    public static void villagerLavaDeathReflectsRecentPlayerCombat(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        villager.setCustomName(Component.literal("Ember"));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(
                villager.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0F),
                "player damage should enter the villager in combat");

        var lava = helper.getLevel().damageSources().lava();
        villager.getCombatTracker().recordDamage(lava, 1.0F);
        Component message = VillagerDeathMessageFactory.create(villager, lava);
        TranslatableContents translation = translated(message, helper);

        helper.assertValueEqual(translation.getKey(), "death.attack.lava.player", "contextual lava death key");
        helper.assertValueEqual(((Component) translation.getArgs()[0]).getString(), "Ember", "villager display name");
        helper.assertValueEqual(
                ((Component) translation.getArgs()[1]).getString(), player.getDisplayName().getString(),
                "recent combat opponent");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void villagerFallDeathUsesVanillaAssistanceContext(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        villager.setCustomName(Component.literal("Cliff"));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        villager.getCombatTracker().recordDamage(helper.getLevel().damageSources().playerAttack(player), 1.0F);
        villager.fallDistance = 8.0F;

        var fall = helper.getLevel().damageSources().fall();
        villager.getCombatTracker().recordDamage(fall, 20.0F);
        TranslatableContents translation = translated(VillagerDeathMessageFactory.create(villager, fall), helper);

        helper.assertValueEqual(translation.getKey(), "death.fell.assist", "assisted-fall death key");
        helper.assertValueEqual(((Component) translation.getArgs()[0]).getString(), "Cliff", "villager display name");
        helper.assertValueEqual(
                ((Component) translation.getArgs()[1]).getString(), player.getDisplayName().getString(),
                "fall assister");
        helper.succeed();
    }

    private static TranslatableContents translated(Component component, GameTestHelper helper) {
        helper.assertTrue(component.getContents() instanceof TranslatableContents, "death message should remain translatable");
        return (TranslatableContents) component.getContents();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void droppedWeaponUpgradesPreferMeleeAndQuality(GameTestHelper helper) {
        helper.assertTrue(
                VillagerRetaliationVillagerWeapons.isBetterWeaponChoice(
                        new ItemStack(Items.IRON_SWORD), new ItemStack(Items.BOW)),
                "a melee weapon should be preferred over a ranged weapon");
        helper.assertFalse(
                VillagerRetaliationVillagerWeapons.isBetterWeaponChoice(
                        new ItemStack(Items.BOW), new ItemStack(Items.IRON_SWORD)),
                "a ranged weapon should not replace a usable melee weapon");
        helper.assertTrue(
                VillagerRetaliationVillagerWeapons.isBetterWeaponChoice(
                        new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.IRON_SWORD)),
                "a higher-tier melee weapon should replace a lower-tier melee weapon");

        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        ItemEntity nearbyBow = new ItemEntity(
                helper.getLevel(), villager.getX() + 1.0D, villager.getY(), villager.getZ(),
                new ItemStack(Items.BOW));
        ItemEntity fartherSword = new ItemEntity(
                helper.getLevel(), villager.getX() + 3.0D, villager.getY(), villager.getZ(),
                new ItemStack(Items.IRON_SWORD));
        helper.getLevel().addFreshEntity(nearbyBow);
        helper.getLevel().addFreshEntity(fartherSword);
        helper.assertTrue(
                VillagerRetaliationVillagerWeapons.findNearestWeapon(villager).orElse(null) == fartherSword,
                "ground-weapon selection should prefer melee quality before proximity");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void villagerEquipsDroppedArmorUpgradeAndStoresOldPiece(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        villager.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        ItemEntity upgrade = new ItemEntity(
                helper.getLevel(), villager.getX(), villager.getY(), villager.getZ(),
                new ItemStack(Items.IRON_CHESTPLATE));
        upgrade.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(upgrade);

        helper.assertTrue(
                VillagerRetaliationVillagerArmor.equipGroundUpgrade(villager, upgrade),
                "the villager should equip a valid dropped armor upgrade");
        helper.assertTrue(
                villager.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE),
                "the stronger chestplate should be equipped");
        helper.assertTrue(
                villager.getInventory().hasAnyMatching(stack -> stack.is(Items.LEATHER_CHESTPLATE)),
                "the displaced armor should be preserved in the villager inventory");
        helper.assertTrue(upgrade.isRemoved(), "the consumed ground stack should be discarded");
        helper.assertFalse(
                VillagerRetaliationVillagerArmor.isBetterArmor(
                        new ItemStack(Items.LEATHER_CHESTPLATE), villager.getItemBySlot(EquipmentSlot.CHEST)),
                "weaker armor should not replace the equipped upgrade");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void partyEquipmentCannotBeDuplicatedByGroundUpgrades(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        HiredJobInventory partyInventory = HiredJobInventory.getJobInventory(villager);
        partyInventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_SWORD));
        partyInventory.setItem(1, new ItemStack(Items.IRON_CHESTPLATE));

        ItemEntity weaponUpgrade = new ItemEntity(
                helper.getLevel(), villager.getX(), villager.getY(), villager.getZ(),
                new ItemStack(Items.DIAMOND_SWORD));
        ItemEntity armorUpgrade = new ItemEntity(
                helper.getLevel(), villager.getX(), villager.getY(), villager.getZ(),
                new ItemStack(Items.DIAMOND_CHESTPLATE));
        weaponUpgrade.setNoPickUpDelay();
        armorUpgrade.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(weaponUpgrade);
        helper.getLevel().addFreshEntity(armorUpgrade);

        helper.assertFalse(
                VillagerRetaliationVillagerWeapons.shouldPathfindForWeapon(villager, weaponUpgrade.getItem()),
                "party main-hand authority should reject ground weapon upgrades");
        helper.assertFalse(
                VillagerRetaliationVillagerArmor.shouldPathfindForUpgrade(villager, armorUpgrade.getItem()),
                "party armor authority should reject ground armor upgrades");

        // Revalidate at pickup time too: a path selected before party equipment was
        // assigned must not copy the live equipment mirror into personal storage.
        VillagerRetaliationVillagerWeapons.equipGroundWeapon(villager, weaponUpgrade);
        helper.assertFalse(
                VillagerRetaliationVillagerArmor.equipGroundUpgrade(villager, armorUpgrade),
                "stale armor pickup should yield to party equipment authority");
        HiredJobInventory.maintainEquipmentSlots(villager);

        helper.assertTrue(villager.getMainHandItem().is(Items.IRON_SWORD),
                "party weapon should remain equipped");
        helper.assertTrue(villager.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE),
                "party armor should remain equipped");
        helper.assertTrue(partyInventory.getItem(HiredJobInventory.MAINHAND_SLOT).is(Items.IRON_SWORD)
                        && partyInventory.getItem(HiredJobInventory.MAINHAND_SLOT).getCount() == 1,
                "party inventory should retain exactly one weapon");
        helper.assertTrue(partyInventory.getItem(1).is(Items.IRON_CHESTPLATE)
                        && partyInventory.getItem(1).getCount() == 1,
                "party inventory should retain exactly one armor piece");
        helper.assertValueEqual(villager.getInventory().countItem(Items.IRON_SWORD), 0,
                "party weapon must not be copied into personal inventory");
        helper.assertValueEqual(villager.getInventory().countItem(Items.IRON_CHESTPLATE), 0,
                "party armor must not be copied into personal inventory");
        helper.assertTrue(weaponUpgrade.isAlive() && armorUpgrade.isAlive(),
                "rejected upgrades should remain on the ground");
        helper.succeed();
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
    public static void villagerHeldMendingFishingRodConsumesExperience(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        ItemStack fishingRod = new ItemStack(Items.FISHING_ROD);
        var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        fishingRod.enchant(enchantments.getOrThrow(Enchantments.MENDING), 1);
        fishingRod.setDamageValue(20);
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(
                villager,
                EquipmentSlot.MAINHAND,
                fishingRod
        );
        ItemStack equippedRod = villager.getMainHandItem();

        ExperienceOrb orb = helper.spawn(EntityType.EXPERIENCE_ORB, 1, 2, 1);
        orb.value = 5;
        orb.setPos(villager.position());
        orb.tickCount = 1;
        orb.tick();
        orb.setPos(villager.position());
        orb.setDeltaMovement(Vec3.ZERO);
        orb.tick();

        helper.assertValueEqual(equippedRod.getDamageValue(), 10,
                "five XP should repair ten held fishing-rod durability");
        helper.assertTrue(orb.isRemoved(),
                "the villager should consume the experience orb after repairing a held fishing rod");
        helper.assertTrue(VillagerRetaliationVillagerEquipment.maintainPlayerManagedMainHand(villager),
                "equipment maintenance should retain the repaired held tool");
        helper.assertValueEqual(villager.getMainHandItem().getDamageValue(), 10,
                "equipment maintenance should not restore the fishing rod's old damage");
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
    public static void operatorKillFinishesAlreadyDownedVillager(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        villager.addTag(VillagerDeathProtectionResolver.ESSENTIAL_ENTITY_TAG);
        villager.hurt(helper.getLevel().damageSources().generic(), 1000.0F);
        helper.assertTrue(VillagerDownedService.isDowned(villager),
                "essential villager should be downed before the bypass hit");

        villager.invulnerableTime = 0;
        villager.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);

        helper.assertTrue(villager.isDeadOrDying() || villager.isRemoved(),
                "generic kill should finish an already downed villager");
        helper.assertFalse(VillagerDownedService.isDowned(villager),
                "lethal bypass damage should release the downed state");
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

        HiredVillagerIndex.clearRuntimeState();
        helper.assertTrue(HiredVillagerIndex.find(player, villager.getUUID()).isEmpty(), "test should simulate a stale runtime index");
        ClipboardWorkforceSnapshot repairedSnapshot = ClipboardWorkforceService.snapshot(player);
        helper.assertValueEqual(repairedSnapshot.totalHired(), 1, "clipboard should recover a loaded hired villager");
        helper.assertTrue(HiredVillagerIndex.find(player, villager.getUUID()).isPresent(), "clipboard snapshot should repair the runtime index");

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

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        HiredDebugPreviewService.onPlayerTick(player);
        HiredDebugPreviewService.DebugPreviewSummary persisted =
                HiredDebugPreviewService.setClipboardPreviewEnabled(player, true);
        helper.assertTrue(persisted.enabled(), "enabled clipboard preview should persist after putting the clipboard away");

        HiredDebugPreviewService.setClipboardPreviewEnabled(player, false);
        HiredDebugPreviewService.clearRuntimeState();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void hitboxDebugPreviewPacketRequiresOperatorPermission(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HiredDebugPreviewService.clearRuntimeState();

        ServerPlayer player = fakePlayer(level, "VrHitboxGuard");
        HiredDebugPreviewService.DebugPreviewSummary rejected =
                HiredDebugPreviewService.setHitboxDebugPreviewEnabled(player, true);
        helper.assertFalse(rejected.enabled(), "hitbox preview should reject a non-operator packet sender");

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
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));

        VillagerReputationManager.setReputation(level, villager, followerOwner.getUUID(), 0);
        helper.assertTrue(
                VillagerRecruitmentService.startFollowing(level, villager, followerOwner),
                "a neutral villager should accept a follow request without a hire contract");
        helper.assertValueEqual(
                RecruitmentPolicy.mayCommand(level, villager, followerOwner, VillagerAssignmentCommand.FOLLOW).reason(),
                RecruitmentPolicy.DenialReason.NONE,
                "policy should allow neutral villagers to follow without a hire contract");
        helper.assertTrue(
                VillagerRecruitmentService.stopFollowing(level, villager, followerOwner),
                "a player should be able to stop their own unpaid follower");
        helper.assertTrue(
                HiredRoleWorkerRegistry.get(HiredVillagerRole.COMBAT) != null
                        && HiredRoleWorkerRegistry.get(HiredVillagerRole.FARMING) != null,
                "guard and farming work are concrete role behaviors");

        helper.assertValueEqual(
                VillagerAssignmentService.snapshot(villager).state(),
                VillagerAssignmentState.UNASSIGNED,
                "new villager assignment state");

        HiredVillagerContractService.startHireContract(
                level, villager, followerOwner, 1, 8, HiredVillagerRole.FARMING);
        followerOwner.moveTo(villager.getX(), villager.getY(), villager.getZ() + 1.0D, 0.0F, 0.0F);
        helper.assertValueEqual(
                VillagerAssignmentService.snapshot(villager).command(),
                VillagerAssignmentCommand.WORK,
                "a new hire starts in work command state");
        helper.assertTrue(
                VillagerRecruitmentService.startFollowing(level, villager, followerOwner),
                "the contract owner should be able to tell their hired worker to follow");
        helper.assertValueEqual(
                villager.getPersistentData().getCompound("VillagerRetaliationHiredWork").getString("Status"),
                "interaction.work.status.paused_for_command",
                "hired work should pause before follower navigation begins");
        helper.assertFalse(
                VillagerRecruitmentService.startFollowing(level, villager, otherPlayer),
                "a hired worker should reject follow commands from another player");
        helper.assertValueEqual(
                RecruitmentPolicy.mayCommand(level, villager, otherPlayer, VillagerAssignmentCommand.FOLLOW).reason(),
                RecruitmentPolicy.DenialReason.OWNED_BY_ANOTHER_PLAYER,
                "policy should distinguish foreign ownership from an absent contract");
        helper.assertTrue(
                VillagerRecruitmentService.isFollowing(villager, followerOwner),
                "the rejected command should preserve the contract owner's follow state");
        VillagerRecruitmentService.onVillagerTickPre(villager);
        VillagerRecruitmentService.onVillagerTickPost(villager);
        HiredVillagerWorkService.onVillagerTickPost(villager);
        helper.assertTrue(
                VillagerRecruitmentService.isFollowing(villager, followerOwner),
                "hired work ticks should yield to and preserve the contract owner's follow command");
        followerOwner.moveTo(villager.getX() + 6.0D, villager.getY(), villager.getZ(), 0.0F, 0.0F);
        VillagerRecruitmentService.onVillagerTickPost(villager);
        helper.assertFalse(
                villager.getNavigation().isDone(),
                "a hired villager ordered to follow should begin navigating toward their hirer");

        VillagerReputationManager.setReputation(level, villager, followerOwner.getUUID(), 100);
        helper.assertTrue(
                VillagerRecruitmentService.stayHere(level, villager, followerOwner),
                "the hirer should be able to change follow to stay");
        helper.assertValueEqual(
                VillagerAssignmentService.snapshot(villager).command(),
                VillagerAssignmentCommand.STAY,
                "stay transition should be persisted canonically");
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.WEAPONSMITH));
        helper.assertTrue(
                HiredVillagerContractService.setActiveRole(level, villager, HiredVillagerRole.COMBAT),
                "hirer should be able to change role");
        helper.assertValueEqual(
                VillagerAssignmentService.snapshot(villager).command(),
                VillagerAssignmentCommand.GUARD,
                "combat role should select guard command");
        HiredVillagerContractService.endHireContract(level, villager, followerOwner);
        helper.assertValueEqual(
                VillagerAssignmentService.snapshot(villager).state(),
                VillagerAssignmentState.UNASSIGNED,
                "firing should end the assignment lifecycle");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void assignmentSchemaMigratesLegacyFollowStateAndSurvivesReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = fakePlayer(level, "VrAssignmentReload");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        helper.assertTrue(
                HiredVillagerContractService.startHireContract(
                        level, villager, owner, 2, 8, HiredVillagerRole.FARMING),
                "farmer fixture should accept a farming contract before migration");

        CompoundTag assignment = villager.getPersistentData().getCompound("VillagerRetaliationAssignment");
        assignment.putInt("SchemaVersion", 1);
        assignment.putString("Command", VillagerAssignmentCommand.WORK.name());
        villager.getPersistentData().put("VillagerRetaliationAssignment", assignment);
        villager.getPersistentData().putUUID("VillagerRetaliationFollowingPlayer", owner.getUUID());
        villager.getPersistentData().putString("VillagerRetaliationFollowMode", "follow");
        villager.getPersistentData().putFloat("VillagerRetaliationFollowStartHealth", 20.0F);
        villager.getPersistentData().putFloat("VillagerRetaliationFollowMinHealth", 14.0F);
        villager.getPersistentData().putInt("VillagerRetaliationFollowStartX", villager.blockPosition().getX());
        villager.getPersistentData().putInt("VillagerRetaliationFollowStartY", villager.blockPosition().getY());
        villager.getPersistentData().putInt("VillagerRetaliationFollowStartZ", villager.blockPosition().getZ());
        villager.getPersistentData().putString("VillagerRetaliationFollowStartBiome", "plains");
        villager.getPersistentData().putInt("VillagerRetaliationFollowMaxDistance", 17);
        villager.getPersistentData().putBoolean("VillagerRetaliationFollowUsedBoat", true);

        var migrated = VillagerAssignmentStore.snapshot(villager);
        var journey = VillagerAssignmentStore.journey(villager);
        helper.assertValueEqual(migrated.schemaVersion(), 2, "legacy assignment should migrate to schema v2");
        helper.assertValueEqual(migrated.command(), VillagerAssignmentCommand.FOLLOW, "legacy follow command");
        helper.assertValueEqual(journey.startBiome(), "plains", "legacy journey biome");
        helper.assertValueEqual(journey.distanceBlocks(), 17, "legacy journey distance");
        helper.assertTrue(journey.usedBoat(), "legacy boat trip flag");
        helper.assertFalse(
                villager.getPersistentData().contains("VillagerRetaliationFollowingPlayer"),
                "migration should remove old top-level owner key");
        helper.assertTrue(
                villager.getPersistentData().getCompound("VillagerRetaliationAssignment")
                        .contains("Journey", net.minecraft.nbt.Tag.TAG_COMPOUND),
                "journey state should live inside the versioned assignment");

        CompoundTag saved = new CompoundTag();
        villager.saveWithoutId(saved);
        Villager reloaded = EntityType.VILLAGER.create(level);
        if (reloaded == null) throw new GameTestAssertException("Could not create reload fixture");
        reloaded.load(saved);
        var restored = VillagerAssignmentStore.snapshot(reloaded);
        helper.assertTrue(restored.ownedBy(owner.getUUID()), "owner should survive entity serialization");
        helper.assertValueEqual(restored.command(), VillagerAssignmentCommand.FOLLOW, "follow should survive reload");
        helper.assertValueEqual(VillagerAssignmentStore.journey(reloaded), journey, "journey should survive reload");

        VillagerReputationManager.setReputation(level, reloaded, owner.getUUID(), 100);
        helper.assertTrue(VillagerRecruitmentService.stayHere(level, reloaded, owner), "owner can switch to stay");
        CompoundTag stayed = new CompoundTag();
        reloaded.saveWithoutId(stayed);
        Villager stayedReloaded = EntityType.VILLAGER.create(level);
        if (stayedReloaded == null) throw new GameTestAssertException("Could not create stay reload fixture");
        stayedReloaded.load(stayed);
        helper.assertValueEqual(
                VillagerAssignmentStore.snapshot(stayedReloaded).command(),
                VillagerAssignmentCommand.STAY,
                "stay should survive reload");
        helper.assertValueEqual(
                VillagerAssignmentStore.stayAnchor(stayedReloaded),
                reloaded.blockPosition(),
                "stay anchor should survive reload");

        villager.discard();
        reloaded.discard();
        stayedReloaded.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void activeAssignmentCannotBeOverwrittenBySecondHirer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer first = fakePlayer(level, "VrFirstHirer");
        ServerPlayer second = fakePlayer(level, "VrSecondHirer");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));

        helper.assertTrue(
                HiredVillagerContractService.startHireContract(level, villager, first, 2, 8),
                "first hire should win");
        helper.assertFalse(
                HiredVillagerContractService.startHireContract(level, villager, second, 2, 8),
                "second hire must not overwrite an active assignment");
        helper.assertTrue(
                VillagerAssignmentStore.snapshot(villager).ownedBy(first.getUUID()),
                "the first owner remains authoritative");
        helper.assertValueEqual(
                HiredVillagerContractService.currentContractHirer(villager).orElseThrow(),
                first.getUUID(),
                "contract and assignment owners must agree");

        HiredVillagerContractService.endHireContract(level, villager, first);
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
        helper.assertValueEqual(
                HiredStorageClipboardItem.mode(clipboard),
                HiredStorageClipboardItem.ClipboardMode.ASSIGN_STORAGE,
                "applying a draft should reset the held clipboard mode");

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
    public static void woundedVillagerWithoutRecoverySuppliesKeepsFighting(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        Zombie target = spawnZombie(helper, new BlockPos(3, 2, 1));
        setRecoveryState(villager, 20, 5.0F);
        villager.setHealth(villager.getMaxHealth() * 0.4F);
        villager.setTarget(target);

        helper.assertFalse(
                VillagerRecoveryService.onVillagerTickPost(villager),
                EMPTY_TEMPLATE);
        helper.assertTrue(villager.getTarget() == target, EMPTY_TEMPLATE);
        helper.assertFalse(VillagerRecoveryService.isForcingRecovery(villager), EMPTY_TEMPLATE);

        VillagerRecoveryService.onVillagerUnloaded(villager);
        target.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void woundedVillagerWithFoodRetreatsToRecover(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        Zombie target = spawnZombie(helper, new BlockPos(3, 2, 1));
        setRecoveryState(villager, 9, 0.0F);
        villager.setHealth(villager.getMaxHealth() * 0.4F);
        VillagerInventoryAccess.addItem(villager, new ItemStack(Items.BREAD));
        villager.setTarget(target);

        helper.assertTrue(
                VillagerRecoveryService.onVillagerTickPost(villager),
                EMPTY_TEMPLATE);
        helper.assertTrue(villager.getTarget() == null, EMPTY_TEMPLATE);
        helper.assertTrue(VillagerRecoveryService.isForcingRecovery(villager), EMPTY_TEMPLATE);
        helper.assertTrue(villager.getMainHandItem().is(Items.BREAD), EMPTY_TEMPLATE);

        VillagerRecoveryService.onVillagerUnloaded(villager);
        target.discard();
        villager.discard();
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
        helper.assertFalse(villager.isUsingItem(),
                "food should synchronize to the client before item use begins");
        VillagerRecoveryService.onVillagerTickPost(villager);
        helper.assertTrue(villager.isUsingItem(), "villager should begin the eating animation");
        helper.assertTrue(villager.getMainHandItem().is(Items.BREAD), "food should remain visible while eating");
        helper.assertValueEqual(VillagerRecoveryService.foodLevel(villager), 9, "food is not applied immediately");

        for (int tick = 1; tick < useTicks - 1; tick++) {
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
    public static void hiredFishingExperienceRequiresMendingRod(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        VillagerFishingHook.CatchResult catchResult = new VillagerFishingHook.CatchResult(List.of(), 1, 5);
        ItemStack ordinaryRod = new ItemStack(Items.FISHING_ROD);

        catchResult.spawnExperience(helper.getLevel(), villager, ordinaryRod);
        helper.assertTrue(
                helper.getLevel().getEntitiesOfClass(
                        ExperienceOrb.class,
                        villager.getBoundingBox().inflate(2.0D)).isEmpty(),
                "hired fishing should not generate experience for an ordinary rod");

        ItemStack mendingRod = new ItemStack(Items.FISHING_ROD);
        var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        mendingRod.enchant(enchantments.getOrThrow(Enchantments.MENDING), 1);
        catchResult.spawnExperience(helper.getLevel(), villager, mendingRod);

        List<ExperienceOrb> experience = helper.getLevel().getEntitiesOfClass(
                ExperienceOrb.class,
                villager.getBoundingBox().inflate(2.0D));
        helper.assertValueEqual(experience.size(), 1,
                "hired fishing should generate one experience orb for a Mending rod");
        helper.assertValueEqual(experience.getFirst().value, 5,
                "hired fishing should preserve the catch experience value");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void recoveryOwnsMainHandUntilEatingFinishes(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        HiredJobInventory jobInventory = HiredJobInventory.getJobInventory(villager);
        jobInventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_SWORD));
        setRecoveryState(villager, 9, 0.0F);
        VillagerInventoryAccess.addItem(villager, new ItemStack(Items.BREAD));

        VillagerRecoveryService.onVillagerTickPost(villager);
        HiredJobInventory.maintainEquipmentSlots(villager);

        helper.assertFalse(villager.isUsingItem(),
                "recovery should synchronize its food hand before starting item use");
        helper.assertTrue(villager.getMainHandItem().is(Items.BREAD),
                "recovery should expose food for one synchronization tick before eating");

        VillagerRecoveryService.onVillagerTickPost(villager);
        HiredJobInventory.maintainEquipmentSlots(villager);

        helper.assertTrue(villager.isUsingItem() && villager.getUseItem().is(Items.BREAD),
                "equipment authority must not interrupt the eating use state");
        helper.assertTrue(villager.getMainHandItem().is(Items.BREAD),
                "equipment authority must not flicker the weapon over the food visual");
        helper.assertTrue(jobInventory.getItem(HiredJobInventory.MAINHAND_SLOT).is(Items.IRON_SWORD)
                        && jobInventory.getItem(HiredJobInventory.MAINHAND_SLOT).getCount() == 1,
                "recovery hand swaps must preserve exactly one authoritative job weapon stack");

        VillagerRecoveryService.onVillagerUnloaded(villager);
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void recoveryDefersBorrowedWeaponReturnWithoutDuplication(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        VillagerInventoryAccess.addItem(villager, new ItemStack(Items.IRON_SWORD));
        VillagerInventoryAccess.addItem(villager, new ItemStack(Items.BREAD));
        helper.assertTrue(
                VillagerInventoryAccess.tryBorrowCombatWeapon(villager, stack -> stack.is(Items.IRON_SWORD)),
                "test setup should borrow the only stored weapon");
        setRecoveryState(villager, 9, 0.0F);

        VillagerRecoveryService.onVillagerTickPost(villager);
        VillagerInventoryAccess.returnBorrowedCombatWeapon(villager);

        VillagerRecoveryService.onVillagerTickPost(villager);
        VillagerInventoryAccess.returnBorrowedCombatWeapon(villager);

        helper.assertTrue(VillagerInventoryAccess.hasBorrowedCombatWeapon(villager),
                "borrowed weapon return must wait until the recovery visual releases the main hand");
        helper.assertTrue(villager.isUsingItem() && villager.getUseItem().is(Items.BREAD),
                "deferred weapon return must preserve the eating use state");

        VillagerRecoveryService.onVillagerUnloaded(villager);
        VillagerInventoryAccess.returnBorrowedCombatWeapon(villager);
        ItemStack returnedWeapon = VillagerInventoryAccess.takeCarriedItem(
                villager, stack -> stack.is(Items.IRON_SWORD));
        ItemStack duplicateWeapon = VillagerInventoryAccess.takeCarriedItem(
                villager, stack -> stack.is(Items.IRON_SWORD));
        helper.assertTrue(returnedWeapon.is(Items.IRON_SWORD) && returnedWeapon.getCount() == 1,
                "the borrowed weapon should return exactly once after recovery");
        helper.assertTrue(duplicateWeapon.isEmpty(),
                "healing hand swaps must not duplicate the borrowed weapon");

        villager.discard();
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
    public static void giftRequestCooldownIsPerPlayerAndHonorsBoundary(GameTestHelper helper) {
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        ResourceLocation giftRequest = VillagerGiftRequestPayload.TYPE.id();

        helper.assertTrue(ServerboundRequestLimiter.tryAcquire(firstPlayer, giftRequest, 100L, 10L),
                "the first gift request should be accepted");
        helper.assertFalse(ServerboundRequestLimiter.tryAcquire(firstPlayer, giftRequest, 109L, 10L),
                "a gift request inside the cooldown should be rejected");
        helper.assertTrue(ServerboundRequestLimiter.tryAcquire(firstPlayer, giftRequest, 110L, 10L),
                "a gift request at the cooldown boundary should be accepted");
        helper.assertTrue(ServerboundRequestLimiter.tryAcquire(secondPlayer, giftRequest, 109L, 10L),
                "gift request cooldowns should be isolated by player");

        ServerboundRequestLimiter.clear(firstPlayer);
        ServerboundRequestLimiter.clear(secondPlayer);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void dailyGiftReputationLimitsRepeatStacksAndRelationshipTotals(GameTestHelper helper) {
        VillagerInteractionSavedData data = new VillagerInteractionSavedData();
        UUID villager = UUID.randomUUID();
        UUID otherVillager = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        UUID otherPlayer = UUID.randomUUID();

        helper.assertValueEqual(data.limitPositiveGiftReputation(
                villager, player, 4L, "minecraft:emerald", 57, 0.10D, 120), 57,
                "the first positive stack should receive its full value");
        helper.assertValueEqual(data.limitPositiveGiftReputation(
                villager, player, 4L, "minecraft:emerald", 57, 0.10D, 120), 5,
                "a repeated stack should receive ten percent rounded down");
        helper.assertValueEqual(data.limitPositiveGiftReputation(
                villager, player, 4L, "minecraft:diamond", 100, 0.10D, 120), 58,
                "a different item should be truncated to the remaining daily allowance");
        helper.assertValueEqual(data.limitPositiveGiftReputation(
                villager, player, 4L, "minecraft:gold_ingot", 20, 0.10D, 120), 0,
                "positive gift reputation should stop at the daily cap");
        helper.assertValueEqual(data.limitPositiveGiftReputation(
                villager, player, 4L, "minecraft:rotten_flesh", -20, 0.10D, 120), -20,
                "negative gift reputation should remain unchanged");
        helper.assertValueEqual(data.limitPositiveGiftReputation(
                otherVillager, player, 4L, "minecraft:emerald", 57, 0.10D, 120), 57,
                "a different villager should have an independent allowance");
        helper.assertValueEqual(data.limitPositiveGiftReputation(
                villager, otherPlayer, 4L, "minecraft:emerald", 57, 0.10D, 120), 57,
                "a different player should have an independent allowance");
        helper.assertValueEqual(data.limitPositiveGiftReputation(
                villager, player, 5L, "minecraft:emerald", 57, 0.10D, 120), 57,
                "the ledger should reset on the next Minecraft day");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void dailyGiftReputationLedgerSurvivesSaveAndLegacyLoad(GameTestHelper helper) {
        UUID villager = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        VillagerInteractionSavedData data = new VillagerInteractionSavedData();
        helper.assertValueEqual(data.limitPositiveGiftReputation(
                villager, player, 8L, "minecraft:emerald", 40, 0.10D, 120), 40,
                "initial gift reputation");

        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        VillagerInteractionSavedData loaded = VillagerInteractionSavedData.load(
                saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(loaded.limitPositiveGiftReputation(
                villager, player, 8L, "minecraft:emerald", 40, 0.10D, 120), 4,
                "the repeated-item ledger should survive save and load");
        helper.assertValueEqual(loaded.limitPositiveGiftReputation(
                villager, player, 8L, "minecraft:diamond", 100, 0.10D, 120), 76,
                "the persisted daily total should constrain remaining reputation");

        VillagerInteractionSavedData legacy = VillagerInteractionSavedData.load(
                new CompoundTag(), helper.getLevel().registryAccess());
        helper.assertValueEqual(legacy.limitPositiveGiftReputation(
                villager, player, 8L, "minecraft:emerald", 40, 0.10D, 120), 40,
                "legacy data should start with an empty gift ledger");
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

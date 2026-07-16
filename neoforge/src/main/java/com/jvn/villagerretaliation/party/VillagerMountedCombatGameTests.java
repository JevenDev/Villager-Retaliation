package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.compat.rideon.VillagerRideOnCompat;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.VillagerContractTime;
import com.jvn.villagerretaliation.mixin.ProjectileCanHitAccessor;
import com.jvn.villagerretaliation.mount.VillagerMountedCombatPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerMountedCombatGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    static {
        configureGameTestStructures();
    }

    private VillagerMountedCombatGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = "mount_combat_villager")
    public static void frontVillagerRejectsMeleeAndLetsProjectilesContinue(GameTestHelper helper) {
        if (!VillagerRideOnCompat.available()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        Villager rear = helper.spawn(EntityType.VILLAGER, 2, 1, 2);
        Villager front = helper.spawn(EntityType.VILLAGER, 3, 1, 2);
        AbstractHorse horse = helper.spawn(EntityType.HORSE, 4, 1, 2);
        mountRearBehind(helper, horse, front, rear);
        helper.assertTrue(VillagerMountedCombatPolicy.isProtectedFrontRider(rear, front),
                "A front-seat villager must be protected from its rear villager");

        float health = front.getHealth();
        front.hurt(level.damageSources().mobAttack(rear), 4.0F);
        helper.assertValueEqual(front.getHealth(), health,
                "Rear-seat melee must not damage a front-seat villager");

        Arrow arrow = arrow(level, rear);
        Vec3 movement = new Vec3(0.0D, 0.0D, 1.5D);
        arrow.setDeltaMovement(movement);
        helper.assertFalse(((ProjectileCanHitAccessor) arrow).villagerretaliation$canHitEntity(front),
                "An allied front rider must retain vanilla same-vehicle projectile rejection");
        helper.assertTrue(EventHooks.onProjectileImpact(arrow, new EntityHitResult(front)),
                "The allied front-rider impact must be canceled");
        helper.assertTrue(arrow.isAlive() && arrow.getDeltaMovement().equals(movement),
                "Canceling the allied impact must leave the projectile flying");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = "mount_combat_party")
    public static void partyPlayerIsProtectedFromRearSeatMelee(GameTestHelper helper) {
        if (!VillagerRideOnCompat.available()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        ServerPlayer driver = helper.makeMockServerPlayerInLevel();
        Villager rear = helper.spawn(EntityType.VILLAGER, 2, 1, 2);
        AbstractHorse horse = helper.spawn(EntityType.HORSE, 4, 1, 2);
        long now = level.getGameTime();
        PartyRecord party = PartySavedData.get(level).createParty(driver.getUUID(), now);
        helper.assertTrue(PartySavedData.get(level).addVillager(
                        party, partyVillager(rear, driver, now)),
                "The party fixture must recruit its rear villager");
        mountRearBehind(helper, horse, driver, rear);

        float health = driver.getHealth();
        driver.hurt(level.damageSources().mobAttack(rear), 4.0F);
        helper.assertValueEqual(driver.getHealth(), health,
                "A party player in front must not take rear-seat melee damage");
        PartyService.deleteParty(level, party.id());
        driver.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = "mount_combat_hirer")
    public static void activeHirerIsProtectedFromAttributedAreaDamage(GameTestHelper helper) {
        if (!VillagerRideOnCompat.available()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        Villager rear = helper.spawn(EntityType.VILLAGER, 2, 1, 2);
        AbstractHorse horse = helper.spawn(EntityType.HORSE, 4, 1, 2);
        HiredVillagerContractService.startHireContract(level, rear, hirer, 1, 0);
        mountRearBehind(helper, horse, hirer, rear);

        AreaEffectCloud cloud = new AreaEffectCloud(level, rear.getX(), rear.getY(), rear.getZ());
        cloud.setOwner(rear);
        DamageSource areaDamage = level.damageSources().indirectMagic(cloud, rear);
        helper.assertTrue(VillagerMountedCombatPolicy.shouldCancelDamage(hirer, areaDamage),
                "Attributed area damage must resolve back to the hired rear villager");
        float health = hirer.getHealth();
        hirer.hurt(areaDamage, 4.0F);
        helper.assertValueEqual(hirer.getHealth(), health,
                "An active hirer in front must not take rear-seat area damage");
        HiredVillagerContractService.endHireContract(level, rear, hirer);
        hirer.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = "mount_combat_hostile")
    public static void retaliationTargetInFrontCanStillBeShot(GameTestHelper helper) {
        if (!VillagerRideOnCompat.available()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        Zombie driver = helper.spawn(EntityType.ZOMBIE, 3, 1, 2);
        Villager rear = helper.spawn(EntityType.VILLAGER, 2, 1, 2);
        AbstractHorse horse = helper.spawn(EntityType.HORSE, 4, 1, 2);
        mountRearBehind(helper, horse, driver, rear);

        Arrow arrow = arrow(level, rear);
        DamageSource arrowDamage = level.damageSources().arrow(arrow, rear);
        helper.assertFalse(VillagerMountedCombatPolicy.shouldCancelDamage(driver, arrowDamage),
                "A neutral front rider must not receive general seat immunity");
        helper.assertFalse(((ProjectileCanHitAccessor) arrow).villagerretaliation$canHitEntity(driver),
                "Vanilla must initially reject the same-vehicle projectile");

        VillagerRetaliationHandler.forceAngerSilently(rear, driver);
        helper.assertTrue(VillagerRetaliationHandler.hasRetaliationTarget(rear, driver),
                "The front driver must become the rear villager's retaliation target");
        helper.assertTrue(VillagerMountedCombatPolicy.isExplicitlyHostileFrontRider(rear, driver),
                "Retaliation must take precedence over friendly-seat rules");
        helper.assertTrue(((ProjectileCanHitAccessor) arrow).villagerretaliation$canHitEntity(driver),
                "The hostile front rider must override vanilla same-vehicle rejection");
        float health = driver.getHealth();
        driver.hurt(arrowDamage, 4.0F);
        helper.assertTrue(driver.getHealth() < health,
                "The retaliation target in front must take the rear villager's projectile damage");
        helper.succeed();
    }

    private static void mountRearBehind(
            GameTestHelper helper,
            AbstractHorse horse,
            Entity driver,
            Villager rear) {
        horse.setTamed(true);
        if (driver.isPassenger()) {
            driver.stopRiding();
        }
        helper.assertTrue(driver.startRiding(horse, true),
                "The fixture must mount its living driver in front");
        helper.assertTrue(rear.startRiding(horse, true),
                "The fixture must mount its villager in the rear seat");
        helper.assertValueEqual(VillagerRideOnCompat.occupant(horse, false), driver,
                "The expected entity must occupy the front seat");
        helper.assertValueEqual(VillagerRideOnCompat.occupant(horse, true), rear,
                "The villager must occupy the rear seat");
    }

    private static Arrow arrow(ServerLevel level, Villager owner) {
        return new Arrow(level, owner, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
    }

    private static PartyVillagerRecord partyVillager(Villager villager, ServerPlayer leader, long now) {
        return new PartyVillagerRecord(
                villager.getUUID(),
                leader.getUUID(),
                UUID.randomUUID(),
                0,
                PartyCommandMode.FOLLOW,
                null,
                null,
                now,
                VillagerContractTime.endAfterDays(now, 1),
                1,
                0,
                villager.getName().getString(),
                "minecraft:none",
                Level.OVERWORLD.location(),
                villager.blockPosition());
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

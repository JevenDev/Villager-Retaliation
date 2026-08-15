package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.interaction.VillagerContractTime;
import com.jvn.villagerretaliation.mount.VillagerMountPassengers;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
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

    @GameTest(template = EMPTY_TEMPLATE, batch = "mount_friendly_fire_villager_passenger")
    public static void partyVillagerPassengerCannotDamagePlayerDriver(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer driver = helper.makeMockServerPlayerInLevel();
        Villager passenger = helper.spawn(EntityType.VILLAGER, 2, 1, 2);
        AbstractHorse horse = helper.spawn(EntityType.HORSE, 4, 1, 2);
        PartyRecord party = recruit(level, driver, passenger);
        mount(helper, horse, driver, passenger);

        helper.assertTrue(VillagerMountedCombatPolicy.isProtectedPair(passenger, driver),
                "The party passenger and driver must be protected co-riders");
        float health = driver.getHealth();
        driver.hurt(level.damageSources().mobAttack(passenger), 4.0F);
        helper.assertValueEqual(driver.getHealth(), health,
                "Rear-seat villager melee must not damage its party player driver");

        Arrow arrow = new Arrow(level, passenger, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
        Vec3 movement = new Vec3(0.0D, 0.0D, 1.5D);
        arrow.setDeltaMovement(movement);
        helper.assertTrue(EventHooks.onProjectileImpact(arrow, new EntityHitResult(driver)),
                "An allied rear-passenger projectile impact must be canceled");
        helper.assertTrue(arrow.isAlive() && arrow.getDeltaMovement().equals(movement),
                "The canceled allied impact must leave the projectile flying");

        PartyService.deleteParty(level, party.id());
        helper.assertFalse(VillagerMountedCombatPolicy.isProtectedPair(passenger, driver),
                "Deleting the party must remove seat-specific friendly-fire protection");
        driver.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = "mount_friendly_fire_player_passenger")
    public static void partyPlayerPassengerCannotDamageVillagerDriver(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer passenger = helper.makeMockServerPlayerInLevel();
        Villager driver = helper.spawn(EntityType.VILLAGER, 2, 1, 2);
        AbstractHorse horse = helper.spawn(EntityType.HORSE, 4, 1, 2);
        PartyRecord party = recruit(level, passenger, driver);
        horse.setTamed(true);
        helper.assertTrue(VillagerMountPassengers.tryMountAvailableSeat(horse, driver),
                "The villager must mount in the driver seat");
        helper.assertTrue(VillagerMountPassengers.tryMountAvailableSeat(horse, passenger),
                "The player must mount in the rear seat");
        helper.assertValueEqual(VillagerMountPassengers.occupant(horse, true), passenger,
                "The player must be the rear passenger");

        helper.assertTrue(VillagerMountedCombatPolicy.isProtectedPair(passenger, driver),
                "The party player passenger and villager driver must be protected co-riders");
        float health = driver.getHealth();
        driver.hurt(level.damageSources().playerAttack(passenger), 4.0F);
        helper.assertValueEqual(driver.getHealth(), health,
                "Rear-seat player damage must not hurt its party villager driver");

        PartyService.deleteParty(level, party.id());
        passenger.discard();
        helper.succeed();
    }

    private static PartyRecord recruit(ServerLevel level, ServerPlayer leader, Villager villager) {
        long now = level.getGameTime();
        PartyRecord party = PartySavedData.get(level).createParty(leader.getUUID(), now);
        if (!PartySavedData.get(level).addVillager(party, partyVillager(villager, leader, now))) {
            throw new IllegalStateException("Failed to construct mounted friendly-fire party fixture");
        }
        return party;
    }

    private static void mount(
            GameTestHelper helper,
            AbstractHorse horse,
            ServerPlayer driver,
            Villager passenger) {
        horse.setTamed(true);
        helper.assertTrue(driver.startRiding(horse, true),
                "The player must mount in the driver seat");
        helper.assertTrue(VillagerMountPassengers.tryMountAvailableSeat(horse, passenger),
                "The villager must mount in the rear seat");
        helper.assertValueEqual(VillagerMountPassengers.occupant(horse, false), driver,
                "The player must be the driver");
        helper.assertValueEqual(VillagerMountPassengers.occupant(horse, true), passenger,
                "The villager must be the rear passenger");
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

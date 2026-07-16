package com.jvn.villagerretaliation.mount;

import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerMountGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    static {
        configureGameTestStructures();
    }

    private VillagerMountGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void allFiveVanillaMountTypesUseTheInternalHorseAdapter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<AbstractHorse> mounts = List.of(
                helper.spawn(EntityType.HORSE, 1, 1, 1),
                helper.spawn(EntityType.DONKEY, 2, 1, 1),
                helper.spawn(EntityType.MULE, 3, 1, 1),
                helper.spawn(EntityType.LLAMA, 4, 1, 1),
                helper.spawn(EntityType.CAMEL, 5, 1, 1)
        );
        for (AbstractHorse mount : mounts) {
            mount.setAge(0);
            if (mount.getType() != EntityType.CAMEL) {
                mount.setTamed(true);
            }
            helper.assertTrue(mount.getType().is(VillagerMountTags.ASSIGNABLE_MOUNTS),
                    BuiltInRegistries.ENTITY_TYPE.getKey(mount.getType()) + " must be assignable");
            helper.assertTrue(VillagerMountAssignmentService.structurallyEligible(level, mount),
                    BuiltInRegistries.ENTITY_TYPE.getKey(mount.getType()) + " must pass adapter eligibility");
        }

        AbstractHorse untamedHorse = helper.spawn(EntityType.HORSE, 6, 1, 1);
        helper.assertFalse(VillagerMountAssignmentService.structurallyEligible(level, untamedHorse),
                "An untamed horse must be rejected");
        mounts.getLast().setAge(-24000);
        helper.assertFalse(VillagerMountAssignmentService.structurallyEligible(level, mounts.getLast()),
                "A baby camel must be rejected");
        Entity skeletonHorse = helper.spawn(EntityType.SKELETON_HORSE, 7, 1, 1);
        helper.assertFalse(VillagerMountAssignmentService.structurallyEligible(level, skeletonHorse),
                "Skeleton horses must remain outside the public assignable tag");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void assignmentDataPersistsBothIndexesLocationsAndParking(GameTestHelper helper) {
        VillagerMountAssignmentSavedData data = new VillagerMountAssignmentSavedData();
        UUID villagerId = UUID.randomUUID();
        UUID mountId = UUID.randomUUID();
        VillagerMountAssignment assignment = new VillagerMountAssignment(
                villagerId,
                mountId,
                ResourceLocation.withDefaultNamespace("horse"),
                Level.OVERWORLD.location(),
                new BlockPos(4, 70, -8),
                Level.OVERWORLD.location(),
                new BlockPos(3, 70, -7),
                42L
        );
        helper.assertTrue(data.assign(assignment), "The first one-to-one assignment must be accepted");
        helper.assertFalse(data.assign(new VillagerMountAssignment(
                        villagerId, UUID.randomUUID(), assignment.mountType(), assignment.mountDimension(),
                        BlockPos.ZERO, null, null, 43L)),
                "A villager must not receive a second mount");
        helper.assertFalse(data.assign(new VillagerMountAssignment(
                        UUID.randomUUID(), mountId, assignment.mountType(), assignment.mountDimension(),
                        BlockPos.ZERO, null, null, 43L)),
                "A mount must not be shared by a second villager");

        ResourceLocation nether = Level.NETHER.location();
        BlockPos moved = new BlockPos(18, 64, 22);
        helper.assertTrue(data.updateMountLocation(mountId, nether, moved),
                "Mount location updates must mutate both indexes");
        helper.assertTrue(data.setParkingAnchor(villagerId, nether, moved.offset(1, 0, 1)),
                "Parking anchors must be persisted independently of the last position");

        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        VillagerMountAssignmentSavedData loaded =
                VillagerMountAssignmentSavedData.load(saved, helper.getLevel().registryAccess());
        VillagerMountAssignment restored = loaded.forVillager(villagerId).orElseThrow();
        helper.assertValueEqual(loaded.forMount(mountId).orElseThrow(), restored,
                "Villager and mount indexes must resolve the same restored record");
        helper.assertValueEqual(restored.mountDimension(), nether, "restored mount dimension");
        helper.assertValueEqual(restored.lastMountPosition(), moved, "restored mount position");
        helper.assertValueEqual(restored.parkingPosition(), moved.offset(1, 0, 1), "restored parking anchor");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void roleWorkerMountedTravelTogglePersistsWithTheContract(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        Villager villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 0);
        helper.assertTrue(HiredVillagerContractService.isMountedTravelEnabled(level, villager),
                "New role contracts must opt into mounted travel");
        helper.assertFalse(HiredVillagerContractService.toggleMountedTravel(level, villager),
                "The first toggle must disable mounted travel");

        CompoundTag savedVillager = new CompoundTag();
        villager.saveWithoutId(savedVillager);
        Villager restored = EntityType.VILLAGER.create(level);
        helper.assertTrue(restored != null, "The restored villager fixture must be created");
        restored.load(savedVillager);
        helper.assertFalse(HiredVillagerContractService.isMountedTravelEnabled(level, restored),
                "Mounted travel must survive an entity save/load cycle");
        helper.assertTrue(HiredVillagerContractService.toggleMountedTravel(level, restored),
                "The second toggle must re-enable mounted travel");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void compatibleRuntimeAssignsEverySupportedMountWithoutOwnershipOrSaddles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        List<AbstractHorse> mounts = List.of(
                helper.spawn(EntityType.HORSE, 1, 1, 1),
                helper.spawn(EntityType.DONKEY, 2, 1, 1),
                helper.spawn(EntityType.MULE, 3, 1, 1),
                helper.spawn(EntityType.LLAMA, 4, 1, 1),
                helper.spawn(EntityType.CAMEL, 5, 1, 1)
        );
        Villager firstVillager = null;
        for (int index = 0; index < mounts.size(); index++) {
            AbstractHorse mount = mounts.get(index);
            mount.setAge(0);
            if (mount.getType() != EntityType.CAMEL) {
                mount.setTamed(true);
            }
            Villager villager = helper.spawn(EntityType.VILLAGER, index + 1, 1, 3);
            HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 0);
            if (firstVillager == null) {
                firstVillager = villager;
            }
            VillagerMountAssignmentService.AssignmentResult result =
                    VillagerMountAssignmentService.assign(hirer, villager, mount);
            if (!VillagerMountAssignmentService.featureAvailable()) {
                helper.assertValueEqual(result, VillagerMountAssignmentService.AssignmentResult.UNAVAILABLE,
                        "Standalone assignment must remain dormant without Ride On API v2");
                helper.succeed();
                return;
            }
            helper.assertValueEqual(result, VillagerMountAssignmentService.AssignmentResult.SUCCESS,
                    BuiltInRegistries.ENTITY_TYPE.getKey(mount.getType()) + " assignment result");
        }
        helper.assertTrue(firstVillager != null, "The fixture must create an initial villager");
        Villager duplicateVillager = helper.spawn(EntityType.VILLAGER, 7, 1, 3);
        HiredVillagerContractService.startHireContract(level, duplicateVillager, hirer, 1, 0);
        helper.assertValueEqual(
                VillagerMountAssignmentService.assign(hirer, duplicateVillager, mounts.getFirst()),
                VillagerMountAssignmentService.AssignmentResult.MOUNT_ALREADY_ASSIGNED,
                "A second villager must not share an assigned mount");
        helper.assertValueEqual(
                VillagerMountAssignmentService.assign(hirer, firstVillager, mounts.get(1)),
                VillagerMountAssignmentService.AssignmentResult.VILLAGER_ALREADY_ASSIGNED,
                "A villager must not replace its assignment implicitly");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void targetAndLeashAssignmentFlowsAreServerAuthoritative(GameTestHelper helper) {
        if (!VillagerMountAssignmentService.featureAvailable()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        Villager targetVillager = helper.spawn(EntityType.VILLAGER, 1, 1, 3);
        HiredVillagerContractService.startHireContract(level, targetVillager, hirer, 1, 0);
        AbstractHorse targetHorse = helper.spawn(EntityType.HORSE, 1, 1, 1);
        targetHorse.setTamed(true);

        helper.assertValueEqual(
                VillagerMountAssignmentService.startTargeting(hirer, targetVillager),
                VillagerMountAssignmentService.AssignmentResult.SUCCESS,
                "Dialogue assignment must enter target mode");
        PlayerInteractEvent.EntityInteract targetClick =
                new PlayerInteractEvent.EntityInteract(hirer, InteractionHand.MAIN_HAND, targetHorse);
        helper.assertTrue(VillagerMountAssignmentService.handleEntityInteract(targetClick) && targetClick.isCanceled(),
                "The next valid mount click must be consumed server-side");
        helper.assertTrue(VillagerMountAssignmentService.hasAssignment(level, targetVillager.getUUID()),
                "Target mode must create the assignment");

        Villager leashedVillager = helper.spawn(EntityType.VILLAGER, 4, 1, 3);
        HiredVillagerContractService.startHireContract(level, leashedVillager, hirer, 1, 0);
        AbstractHorse leashedDonkey = helper.spawn(EntityType.DONKEY, 4, 1, 1);
        leashedDonkey.setTamed(true);
        leashedDonkey.setLeashedTo(hirer, true);
        helper.assertTrue(leashedDonkey.isLeashed(), "The donkey fixture must be leashed");
        helper.assertValueEqual(leashedDonkey.getLeashHolder(), hirer,
                "The mock hirer must be the donkey's leash holder");
        helper.assertTrue(VillagerMountAssignmentService.isEligibleCandidate(level, leashedDonkey),
                "The leashed donkey fixture must be an eligible unassigned mount");
        int leadsBefore = hirer.getInventory().countItem(Items.LEAD);
        PlayerInteractEvent.EntityInteract villagerClick =
                new PlayerInteractEvent.EntityInteract(hirer, InteractionHand.MAIN_HAND, leashedVillager);
        helper.assertTrue(VillagerMountAssignmentService.handleEntityInteract(villagerClick) && villagerClick.isCanceled(),
                "Exactly one eligible leashed mount must be assigned from the villager click");
        helper.assertFalse(leashedDonkey.isLeashed(), "Assignment must remove the leash");
        helper.assertValueEqual(hirer.getInventory().countItem(Items.LEAD), leadsBefore + 1,
                "Assignment must return the lead");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void cancellingTargetModeLeavesTheMountUnassigned(GameTestHelper helper) {
        if (!VillagerMountAssignmentService.featureAvailable()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        Villager villager = helper.spawn(EntityType.VILLAGER, 1, 1, 3);
        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 0);
        AbstractHorse horse = helper.spawn(EntityType.HORSE, 1, 1, 1);
        horse.setTamed(true);
        VillagerMountAssignmentService.startTargeting(hirer, villager);
        VillagerMountAssignmentService.cancelTargeting(hirer);
        PlayerInteractEvent.EntityInteract click =
                new PlayerInteractEvent.EntityInteract(hirer, InteractionHand.MAIN_HAND, horse);
        helper.assertFalse(VillagerMountAssignmentService.handleEntityInteract(click),
                "A mount click after cancellation must fall through to normal interaction");
        helper.assertFalse(VillagerMountAssignmentService.hasAssignment(level, villager.getUUID()),
                "Cancellation must not create an assignment");
        helper.succeed();
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

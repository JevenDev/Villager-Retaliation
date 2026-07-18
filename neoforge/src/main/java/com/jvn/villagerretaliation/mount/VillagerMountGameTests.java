package com.jvn.villagerretaliation.mount;

import com.jvn.villagerretaliation.compat.rideon.VillagerRideOnCompat;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.mojang.authlib.GameProfile;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

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
        UUID secondVillagerId = UUID.randomUUID();
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
        helper.assertTrue(data.assign(assignment), "The first villager assignment must be accepted");
        helper.assertFalse(data.assign(new VillagerMountAssignment(
                        villagerId, UUID.randomUUID(), assignment.mountType(), assignment.mountDimension(),
                        BlockPos.ZERO, null, null, 43L)),
                "A villager must not receive a second mount");
        helper.assertTrue(data.assign(new VillagerMountAssignment(
                        secondVillagerId, mountId, assignment.mountType(), assignment.mountDimension(),
                        BlockPos.ZERO, null, null, 43L)),
                "Persistence must retain multiple villagers for a multi-seat mount");

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
                "The mount index must retain the first restored record");
        helper.assertValueEqual(loaded.assignmentsForMount(mountId).size(), 2,
                "The mount index must restore every remembered rider");
        helper.assertValueEqual(loaded.forVillager(secondVillagerId).orElseThrow().lastMountPosition(), moved,
                "Location updates must reach every assignment sharing a mount");
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
    public static void mountOwnershipDialogueOnlyAppearsForTheOtherHirersActiveRider(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        ServerPlayer owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "MountOwner"));
        Villager villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        AbstractHorse horse = helper.spawn(EntityType.HORSE, 1, 1, 2);
        horse.setTamed(true);
        horse.setOwnerUUID(owner.getUUID());
        horse.setCustomName(Component.literal("Chestnut"));
        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 0);
        VillagerMountAssignmentSavedData.get(level).assign(new VillagerMountAssignment(
                villager.getUUID(),
                horse.getUUID(),
                BuiltInRegistries.ENTITY_TYPE.getKey(horse.getType()),
                level.dimension().location(),
                horse.blockPosition(),
                level.dimension().location(),
                horse.blockPosition(),
                level.getServer().overworld().getGameTime()));
        villager.startRiding(horse, true);

        helper.assertTrue(VillagerMountOwnershipDialogue.isAvailable(level, owner, villager),
                "The mount owner must be able to challenge another player's hired rider");
        List<DialogueOptionDefinition> options =
                VillagerMountOwnershipDialogue.addAvailableOption(level, owner, villager, List.of());
        helper.assertTrue(options.size() == 1 && options.getFirst().label().equals("That's my Chestnut"),
                "The challenge option must identify the player's mounted animal by name");
        helper.assertFalse(VillagerMountOwnershipDialogue.isAvailable(level, hirer, villager),
                "A villager must not challenge the player who hired them");
        villager.stopRiding();
        helper.assertFalse(VillagerMountOwnershipDialogue.isAvailable(level, owner, villager),
                "The option must disappear when the villager is no longer on the mount");
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
                VillagerMountAssignmentService.AssignmentResult.SUCCESS,
                "A normal horse must remember a villager for each of its two Ride On seats");
        Villager thirdHorseVillager = helper.spawn(EntityType.VILLAGER, 8, 1, 3);
        HiredVillagerContractService.startHireContract(level, thirdHorseVillager, hirer, 1, 0);
        helper.assertValueEqual(
                VillagerMountAssignmentService.assign(hirer, thirdHorseVillager, mounts.getFirst()),
                VillagerMountAssignmentService.AssignmentResult.MOUNT_ALREADY_ASSIGNED,
                "A normal horse must reject a third remembered villager");
        Villager secondDonkeyVillager = helper.spawn(EntityType.VILLAGER, 9, 1, 3);
        HiredVillagerContractService.startHireContract(level, secondDonkeyVillager, hirer, 1, 0);
        helper.assertValueEqual(
                VillagerMountAssignmentService.assign(hirer, secondDonkeyVillager, mounts.get(1)),
                VillagerMountAssignmentService.AssignmentResult.MOUNT_ALREADY_ASSIGNED,
                "A Ride On single-seat mount must still remember only one villager");
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

    @GameTest(template = EMPTY_TEMPLATE, batch = "mount_shared_seats")
    public static void twoAssignedVillagersFillBothHorseSeatsAtRaiderHeight(GameTestHelper helper) {
        if (!VillagerMountAssignmentService.featureAvailable()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        Villager driver = helper.spawn(EntityType.VILLAGER, 1, 1, 2);
        Villager passenger = helper.spawn(EntityType.VILLAGER, 2, 1, 2);
        AbstractHorse horse = helper.spawn(EntityType.HORSE, 1, 1, 2);
        horse.setTamed(true);
        horse.setOnGround(true);
        HiredVillagerContractService.startHireContract(level, driver, hirer, 1, 0);
        HiredVillagerContractService.startHireContract(level, passenger, hirer, 1, 0);
        helper.assertValueEqual(
                VillagerMountAssignmentService.assign(hirer, driver, horse),
                VillagerMountAssignmentService.AssignmentResult.SUCCESS,
                "The horse must accept its driver assignment");
        helper.assertValueEqual(
                VillagerMountAssignmentService.assign(hirer, passenger, horse),
                VillagerMountAssignmentService.AssignmentResult.SUCCESS,
                "The horse must accept its rear-seat assignment");

        BlockPos target = driver.blockPosition().offset(20, 0, 0);
        layTravelFloor(level, driver.blockPosition(), target);
        driver.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(target), 0.8F, 0));
        passenger.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(target), 0.8F, 0));
        VillagerMountTravelService.onVillagerTickPost(driver);
        VillagerMountTravelService.onVillagerTickPost(passenger);

        helper.assertValueEqual(driver.getVehicle(), horse, "The first villager must take the driver seat");
        helper.assertValueEqual(passenger.getVehicle(), horse, "The second villager must take the rear seat");
        helper.assertValueEqual(horse.getPassengers().size(), 2, "The horse must carry both assigned villagers");
        helper.assertValueEqual(horse.getPassengers().getFirst(), driver, "The first villager must remain the driver");
        helper.assertValueEqual(horse.getPassengers().get(1), passenger, "The second villager must occupy the rear seat");

        horse.positionRider(driver);
        horse.positionRider(passenger);
        helper.assertTrue(driver.getY() - horse.getY() > 0.7D && driver.getY() - horse.getY() < 1.0D,
                "The driver must use the vanilla raider-style 0.6-block riding offset");
        helper.assertTrue(passenger.getY() - horse.getY() > 0.7D && passenger.getY() - horse.getY() < 1.0D,
                "The rear villager must use the same grounded riding offset");
        double driverHorizontalOffset = driver.position().multiply(1.0D, 0.0D, 1.0D)
                .distanceTo(horse.position().multiply(1.0D, 0.0D, 1.0D));
        helper.assertTrue(driverHorizontalOffset < 0.05D,
                "A two-villager driver must remain centered on the saddle; offset="
                        + driverHorizontalOffset);
        double horizontalSeatDistance = driver.position().multiply(1.0D, 0.0D, 1.0D)
                .distanceTo(passenger.position().multiply(1.0D, 0.0D, 1.0D));
        helper.assertTrue(horizontalSeatDistance > 1.1D && horizontalSeatDistance < 1.25D,
                "Two villager models must have extra front-to-rear clearance; distance="
                        + horizontalSeatDistance);

        helper.assertTrue(VillagerRideOnCompat.tryDismount(horse, passenger),
                "The rear villager must dismount for offset cleanup coverage");
        horse.positionRider(driver);
        double loneDriverHorizontalOffset = driver.position().multiply(1.0D, 0.0D, 1.0D)
                .distanceTo(horse.position().multiply(1.0D, 0.0D, 1.0D));
        helper.assertTrue(loneDriverHorizontalOffset < 0.05D,
                "A lone villager driver must return to the normal seat; offset="
                        + loneDriverHorizontalOffset);

        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120, batch = "mount_driven_route")
    public static void mountedVillagerActuallyDrivesTheHorseAlongItsRoute(GameTestHelper helper) {
        if (!VillagerMountAssignmentService.featureAvailable()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        Villager villager = helper.spawn(EntityType.VILLAGER, 1, 1, 2);
        AbstractHorse horse = helper.spawn(EntityType.HORSE, 2, 1, 2);
        horse.setTamed(true);
        horse.setOnGround(true);
        hirer.moveTo(horse.getX(), horse.getY(), horse.getZ(), 0.0F, 0.0F);
        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 0);
        helper.assertValueEqual(
                VillagerMountAssignmentService.assign(hirer, villager, horse),
                VillagerMountAssignmentService.AssignmentResult.SUCCESS,
                "The route fixture must create its assignment");

        BlockPos target = villager.blockPosition().offset(20, 0, 0);
        layTravelFloor(level, villager.blockPosition(), target);
        ChunkPos startChunk = new ChunkPos(villager.blockPosition());
        ChunkPos targetChunk = new ChunkPos(target);
        for (int chunkX = Math.min(startChunk.x, targetChunk.x); chunkX <= Math.max(startChunk.x, targetChunk.x); chunkX++) {
            level.setChunkForced(chunkX, startChunk.z, true);
        }
        double startingX = horse.getX();
        // Keep unrelated hired-work handlers from issuing their own stop requests while this
        // fixture isolates NeoForge's rider-to-vehicle navigation delegation.
        villager.setTradingPlayer(hirer);
        villager.setNoAi(true);
        villager.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(target), 0.8F, 0));
        VillagerMountTravelService.onVillagerTickPost(villager);
        helper.assertValueEqual(villager.getVehicle(), horse,
                "The route fixture must mount the villager before measuring movement");
        helper.assertValueEqual(villager.getNavigation(), horse.getNavigation(),
                "The mounted villager must use the controlled horse's native navigator");
        helper.assertTrue(villager.getNavigation().moveTo(
                        target.getX() + 0.5D,
                        target.getY(),
                        target.getZ() + 0.5D,
                        0.8D),
                "The rider's delegated navigator must accept the travel route");
        helper.startSequence()
                .thenExecuteAfter(60, () -> {
                    for (int chunkX = Math.min(startChunk.x, targetChunk.x); chunkX <= Math.max(startChunk.x, targetChunk.x); chunkX++) {
                        level.setChunkForced(chunkX, startChunk.z, false);
                    }
                    helper.assertTrue(horse.getX() - startingX > 2.0D,
                            "The horse navigator must carry its villager driver toward the requested destination; delta="
                                    + (horse.getX() - startingX)
                                    + ", entityTicks=" + horse.tickCount
                                    + ", navTarget=" + horse.getNavigation().getTargetPos());
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = "mount_travel_remount")
    public static void mountDesireSurvivesUnloadAndRetriesWhenTheMountReturns(GameTestHelper helper) {
        if (!VillagerMountAssignmentService.featureAvailable()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        Villager villager = helper.spawn(EntityType.VILLAGER, 1, 1, 2);
        AbstractHorse horse = helper.spawn(EntityType.HORSE, 7, 1, 2);
        horse.setTamed(true);
        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 0);
        helper.assertValueEqual(
                VillagerMountAssignmentService.assign(hirer, villager, horse),
                VillagerMountAssignmentService.AssignmentResult.SUCCESS,
                "The travel fixture must create an assignment");
        BlockPos travelTarget = villager.blockPosition().offset(16, 0, 0);
        layTravelFloor(level, villager.blockPosition(), travelTarget);
        horse.setOnGround(true);
        villager.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(travelTarget), 0.8F, 0));

        VillagerMountTravelService.onVillagerTickPost(villager);
        helper.assertFalse(villager.isPassenger(),
                "A villager must continue on foot while its assigned mount is out of boarding range");
        helper.assertTrue(VillagerMountAssignmentService.hasAssignment(level, villager.getUUID()),
                "A temporarily unreachable mount must not clear mount desire");

        horse.moveTo(villager.getX() + 1.0D, villager.getY(), villager.getZ(), 0.0F, 0.0F);
        horse.setOnGround(true);
        VillagerMountTravelService.onVillagerTickPost(villager);
        helper.assertValueEqual(villager.getVehicle(), horse,
                "The villager must board when the assigned mount returns within three blocks");
        helper.assertValueEqual(villager.getNavigation(), horse.getNavigation(),
                "A driver mob's native navigation must delegate to its controlled horse");
        horse.getNavigation().stop();
        helper.assertTrue(horse.getNavigation().isDone(),
                "The delegation fixture must begin with a stopped mount navigator");
        VillagerMountTravelService.onVillagerTickPost(villager);
        helper.assertTrue(horse.getNavigation().isDone(),
                "The mount coordinator must not inject a competing route for the rider's AI");
        helper.assertTrue(villager.getNavigation().moveTo(
                        travelTarget.getX() + 0.5D,
                        travelTarget.getY(),
                        travelTarget.getZ() + 0.5D,
                        0.8D),
                "The mounted villager's normal navigator must accept the route through its horse");
        helper.assertFalse(horse.getNavigation().isDone(),
                "The rider's delegated navigation request must start the horse navigator");
        helper.assertTrue(VillagerMountAssignmentSavedData.get(level)
                        .forVillager(villager.getUUID()).orElseThrow().parkingPosition() == null,
                "Boarding must release the persisted parking anchor");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = "mount_travel_commute")
    public static void roleCommuteDismountsForPreciseWorkAndParksTheHorse(GameTestHelper helper) {
        if (!VillagerMountAssignmentService.featureAvailable()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        Villager villager = helper.spawn(EntityType.VILLAGER, 1, 1, 2);
        AbstractHorse horse = helper.spawn(EntityType.HORSE, 2, 1, 2);
        horse.setTamed(true);
        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 0);
        helper.assertValueEqual(
                VillagerMountAssignmentService.assign(hirer, villager, horse),
                VillagerMountAssignmentService.AssignmentResult.SUCCESS,
                "The commute fixture must create an assignment");

        BlockPos farTarget = villager.blockPosition().offset(16, 0, 0);
        layTravelFloor(level, villager.blockPosition(), farTarget);
        horse.setOnGround(true);
        villager.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(farTarget), 0.8F, 0));
        helper.assertTrue(HiredVillagerContractService.isHired(level, villager),
                "The commute fixture must retain its active contract");
        helper.assertTrue(HiredVillagerContractService.isMountedTravelEnabled(level, villager),
                "The commute fixture must have mounted travel enabled");
        VillagerMountTravelService.onVillagerTickPost(villager);
        helper.assertValueEqual(villager.getVehicle(), horse,
                "A role worker must mount for a reachable travel leg of at least sixteen blocks");

        villager.getNavigation().stop();
        BlockPos preciseTarget = horse.blockPosition().offset(4, 0, 0);
        villager.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(preciseTarget), 0.5F, 0));
        VillagerMountTravelService.onVillagerTickPost(villager);
        helper.assertFalse(villager.isPassenger(),
                "A role worker must dismount within eight blocks of precise work");
        VillagerMountAssignment parked = VillagerMountAssignmentSavedData.get(level)
                .forVillager(villager.getUUID()).orElseThrow();
        helper.assertValueEqual(parked.parkingPosition(), horse.blockPosition(),
                "Dismounting must persist a parking anchor at the horse");
        helper.assertTrue(horse.hasRestriction() && horse.getRestrictRadius() == 8.0F,
                "A parked horse must receive the eight-block restriction");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = "mount_travel_parking")
    public static void parkedMountReturnsAfterYieldingToRidersAndLeashes(GameTestHelper helper) {
        if (!VillagerMountAssignmentService.featureAvailable()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        Villager villager = helper.spawn(EntityType.VILLAGER, 1, 1, 2);
        AbstractHorse horse = helper.spawn(EntityType.HORSE, 2, 1, 2);
        horse.setTamed(true);
        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 0);
        helper.assertValueEqual(
                VillagerMountAssignmentService.assign(hirer, villager, horse),
                VillagerMountAssignmentService.AssignmentResult.SUCCESS,
                "The parking fixture must create an assignment");
        helper.assertFalse(HiredVillagerContractService.toggleMountedTravel(level, villager),
                "The parking fixture must leave its villager on foot");
        BlockPos anchor = horse.blockPosition().immutable();
        VillagerMountAssignmentSavedData.get(level)
                .setParkingAnchor(villager.getUUID(), level.dimension().location(), anchor);

        horse.setLeashedTo(hirer, true);
        VillagerMountTravelService.maintainParking(level.getServer());
        helper.assertFalse(horse.hasRestriction(), "Parking must yield while the mount is leashed");
        horse.dropLeash(true, false);
        layTravelFloor(level, anchor, anchor.offset(12, 0, 0));
        horse.moveTo(anchor.getX() + 12.5D, anchor.getY(), anchor.getZ() + 0.5D, 0.0F, 0.0F);
        horse.setOnGround(true);
        VillagerMountTravelService.maintainParking(level.getServer());
        helper.assertTrue(horse.hasRestriction(), "Parking must resume after the leash is removed");
        helper.assertTrue(horse.getNavigation().getTargetPos() != null
                        && horse.getNavigation().getTargetPos().distSqr(anchor) <= 1.0D,
                "A mount beyond ten blocks must navigate back to its parking anchor");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = "mount_travel_lifecycle")
    public static void terminalLifecycleEventsClearAssignmentsButChunkUnloadDoesNot(GameTestHelper helper) {
        if (!VillagerMountAssignmentService.featureAvailable()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = helper.makeMockServerPlayerInLevel();
        Villager villager = helper.spawn(EntityType.VILLAGER, 1, 1, 2);
        AbstractHorse horse = helper.spawn(EntityType.HORSE, 2, 1, 2);
        horse.setTamed(true);
        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 0);
        helper.assertValueEqual(
                VillagerMountAssignmentService.assign(hirer, villager, horse),
                VillagerMountAssignmentService.AssignmentResult.SUCCESS,
                "The lifecycle fixture must create an assignment");

        horse.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        helper.assertTrue(VillagerMountAssignmentService.hasAssignment(level, villager.getUUID()),
                "Chunk unload must preserve the assignment");
        VillagerMountAssignmentService.onEntityPermanentlyRemoved(horse);
        helper.assertFalse(VillagerMountAssignmentService.hasAssignment(level, villager.getUUID()),
                "Permanent mount removal must clear both assignment indexes");

        AbstractHorse replacement = helper.spawn(EntityType.HORSE, 3, 1, 2);
        replacement.setTamed(true);
        helper.assertValueEqual(
                VillagerMountAssignmentService.assign(hirer, villager, replacement),
                VillagerMountAssignmentService.AssignmentResult.SUCCESS,
                "The replacement mount must be assignable after cleanup");
        HiredVillagerContractService.endHireContract(level, villager, hirer);
        helper.assertFalse(VillagerMountAssignmentService.hasAssignment(level, villager.getUUID()),
                "Contract end must release the replacement mount and clear the assignment");
        helper.assertFalse(replacement.hasRestriction(),
                "Terminal cleanup must release any parking restriction");
        helper.succeed();
    }

    private static void layTravelFloor(ServerLevel level, BlockPos from, BlockPos to) {
        int direction = Integer.compare(to.getX(), from.getX());
        if (direction == 0) {
            for (int z = from.getZ() - 1; z <= from.getZ() + 1; z++) {
                level.setBlockAndUpdate(new BlockPos(from.getX(), from.getY() - 1, z),
                        Blocks.STONE.defaultBlockState());
            }
            return;
        }
        for (int x = from.getX(); x != to.getX() + direction; x += direction) {
            for (int z = from.getZ() - 1; z <= from.getZ() + 1; z++) {
                level.setBlockAndUpdate(new BlockPos(x, from.getY() - 1, z),
                        Blocks.STONE.defaultBlockState());
            }
        }
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

package com.jvn.villagerretaliation.social;

import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.VillagerRecruitmentService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerBreedingGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    static {
        configureGameTestStructures();
    }

    private VillagerBreedingGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void policyTracksHireFollowAndStayLifecycle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fakePlayer(level, "breeding_policy");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));

        helper.assertValueEqual(
                VillagerBreedingPolicy.evaluateParent(level, villager).reason(),
                BreedingBlockReason.NONE,
                "ordinary adult eligibility");

        HiredVillagerContractService.startHireContract(level, villager, player, 1, 0);
        helper.assertValueEqual(
                VillagerBreedingPolicy.evaluateParent(level, villager).reason(),
                BreedingBlockReason.HIRED,
                "active hire blocker");
        HiredVillagerContractService.endHireContract(level, villager, player);
        helper.assertTrue(VillagerBreedingPolicy.canBreed(level, villager), "dismissal restores eligibility");

        HiredVillagerContractService.startHireContract(level, villager, player, 1, 0);
        helper.assertTrue(VillagerRecruitmentService.startFollowing(level, villager, player), "follow command fixture");
        helper.assertTrue(VillagerRecruitmentService.isFollowing(villager, player), "follow command state");
        helper.assertValueEqual(
                VillagerBreedingPolicy.evaluateParent(level, villager).reason(),
                BreedingBlockReason.HIRED,
                "hired follow remains blocked from breeding");

        VillagerReputationManager.setReputation(level, villager, player.getUUID(), 100);
        helper.assertTrue(VillagerRecruitmentService.stayHere(level, villager, player), "stay command fixture");
        helper.assertTrue(VillagerRecruitmentService.isStayingHere(villager, player), "stay command state");
        helper.assertValueEqual(
                VillagerBreedingPolicy.evaluateParent(level, villager).reason(),
                BreedingBlockReason.HIRED,
                "hired stay remains blocked from breeding");
        HiredVillagerContractService.endHireContract(level, villager, player);
        helper.assertTrue(VillagerBreedingPolicy.canBreed(level, villager), "dismissal clears commands and restores eligibility");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void authoritativePartyMembershipIgnoresStaleEntityReference(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fakePlayer(level, "breeding_party");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        player.moveTo(villager.getX(), villager.getY(), villager.getZ() + 1.0D, 0.0F, 0.0F);
        player.getInventory().add(new ItemStack(Items.EMERALD, PartyVillagerContractService.DAILY_EMERALD_COST));
        VillagerReputationManager.setReputation(level, villager, player.getUUID(), 0);

        PartyVillagerContractService.ContractResult result = PartyVillagerContractService.recruit(player, villager);
        helper.assertTrue(result.success(), "party recruitment fixture: " + result.messageKey());
        helper.assertValueEqual(
                VillagerBreedingPolicy.evaluateParent(level, villager).reason(),
                BreedingBlockReason.PARTY_MEMBER,
                "authoritative party blocker");

        helper.assertTrue(
                PartyVillagerContractService.dismiss(player, villager).success(),
                "authoritative party dismissal");
        villager.getPersistentData().putUUID("VillagerRetaliationPartyId", UUID.randomUUID());
        VillagerRecruitmentService.stopFollowing(villager);
        helper.assertTrue(
                VillagerBreedingPolicy.canBreed(level, villager),
                "stale entity party reference must not block after authoritative removal");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void cancellationClearsOnlyPartnerOwnedCourtshipMemories(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager first = spawnVillager(helper, new BlockPos(1, 2, 1));
        Villager second = spawnVillager(helper, new BlockPos(3, 2, 1));
        GlobalPos home = GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(1, 2, 3)));
        GlobalPos jobSite = GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(2, 2, 3)));
        GlobalPos meeting = GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(3, 2, 3)));

        seedCourtship(first, second);
        seedCourtship(second, first);
        first.getBrain().setMemory(MemoryModuleType.HOME, home);
        first.getBrain().setMemory(MemoryModuleType.JOB_SITE, jobSite);
        first.getBrain().setMemory(MemoryModuleType.MEETING_POINT, meeting);

        VillagerBreedingPolicy.cancelActiveAttempt(level, first);
        VillagerBreedingPolicy.cancelActiveAttempt(level, first);

        assertCourtshipCleared(helper, first, "first parent");
        assertCourtshipCleared(helper, second, "second parent");
        helper.assertValueEqual(first.getBrain().getMemory(MemoryModuleType.HOME).orElse(null), home, "home memory");
        helper.assertValueEqual(first.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null), jobSite, "job-site memory");
        helper.assertValueEqual(first.getBrain().getMemory(MemoryModuleType.MEETING_POINT).orElse(null), meeting, "meeting memory");

        first.discard();
        second.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void finalBirthGuardRejectsHiredParent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fakePlayer(level, "breeding_event");
        Villager first = spawnVillager(helper, new BlockPos(1, 2, 1));
        Villager second = spawnVillager(helper, new BlockPos(3, 2, 1));
        Villager child = first.getBreedOffspring(level, second);
        if (child == null) throw new GameTestAssertException("Could not create proposed child");

        HiredVillagerContractService.startHireContract(level, first, player, 1, 0);
        BabyEntitySpawnEvent event = new BabyEntitySpawnEvent(first, second, child);
        VillagerBirthService.onBabyEntitySpawn(event);
        helper.assertTrue(event.isCanceled(), "hired parent must cancel final birth event");
        helper.assertFalse(child.isAddedToLevel(), "canceled proposed child must remain unspawned");

        HiredVillagerContractService.endHireContract(level, first, player);
        first.discard();
        second.discard();
        child.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void newbornSanitizationPreservesVanillaIdentity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager first = spawnVillager(helper, new BlockPos(1, 2, 1));
        Villager second = spawnVillager(helper, new BlockPos(3, 2, 1));
        Villager child = first.getBreedOffspring(level, second);
        if (child == null) throw new GameTestAssertException("Could not create newborn fixture");
        child.setAge(AgeableMob.BABY_START_AGE);
        var expectedType = child.getVillagerData().getType();

        child.getPersistentData().put("VillagerRetaliationHireContract", new CompoundTag());
        child.getPersistentData().putUUID("VillagerRetaliationPartyId", UUID.randomUUID());
        child.getPersistentData().putUUID("VillagerRetaliationFollowingPlayer", UUID.randomUUID());
        child.getPersistentData().put("VillagerRetaliationHiredWork", new CompoundTag());
        child.getPersistentData().put("VillagerRetaliationJobInventory", new CompoundTag());
        CompoundTag downed = new CompoundTag();
        downed.putBoolean("Downed", true);
        child.getPersistentData().put("VillagerRetaliationDownedState", downed);

        VillagerBirthService.initializeNewborn(level, first, second, child, null);
        helper.assertFalse(child.getPersistentData().contains("VillagerRetaliationHireContract"), "hire state");
        helper.assertFalse(child.getPersistentData().contains("VillagerRetaliationPartyId"), "party state");
        helper.assertFalse(child.getPersistentData().contains("VillagerRetaliationFollowingPlayer"), "follow state");
        helper.assertFalse(child.getPersistentData().contains("VillagerRetaliationHiredWork"), "work state");
        helper.assertFalse(child.getPersistentData().contains("VillagerRetaliationJobInventory"), "job inventory");
        helper.assertFalse(child.getPersistentData().contains("VillagerRetaliationDownedState"), "downed state");
        helper.assertTrue(child.isBaby(), "baby age must survive sanitization");
        helper.assertValueEqual(child.getVillagerData().getType(), expectedType, "villager type");

        first.discard();
        second.discard();
        child.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void jobInventoryFoodCannotSupplyVanillaWillingness(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fakePlayer(level, "breeding_food");
        Villager hired = spawnVillager(helper, new BlockPos(1, 2, 1));
        Villager ordinary = spawnVillager(helper, new BlockPos(3, 2, 1));

        HiredVillagerContractService.startHireContract(level, hired, player, 1, 0);
        HiredJobInventory.getJobInventory(hired).insertPlainSupply(new ItemStack(Items.BREAD, 64));
        helper.assertFalse(hired.hasExcessFood(), "job inventory bread must not count as vanilla breeding food");
        helper.assertValueEqual(hired.getInventory().countItem(Items.BREAD), 0, "managed personal bread count");

        ordinary.getInventory().setItem(0, new ItemStack(Items.BREAD, 6));
        helper.assertTrue(ordinary.hasExcessFood(), "ordinary personal food retains vanilla behavior");

        HiredVillagerContractService.endHireContract(level, hired, player);
        hired.discard();
        ordinary.discard();
        helper.succeed();
    }

    private static void seedCourtship(Villager villager, Villager partner) {
        EntityTracker tracker = new EntityTracker(partner, true);
        villager.getBrain().setMemory(MemoryModuleType.BREED_TARGET, partner);
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, tracker);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(tracker, 0.5F, 2));
    }

    private static void assertCourtshipCleared(GameTestHelper helper, Villager villager, String label) {
        helper.assertFalse(villager.getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET), label + " breed target");
        helper.assertFalse(villager.getBrain().hasMemoryValue(MemoryModuleType.LOOK_TARGET), label + " look target");
        helper.assertFalse(villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET), label + " walk target");
    }

    private static Villager spawnVillager(GameTestHelper helper, BlockPos relativePos) {
        ServerLevel level = helper.getLevel();
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) throw new GameTestAssertException("Could not create villager");
        BlockPos pos = helper.absolutePos(relativePos);
        villager.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        if (!level.addFreshEntity(villager)) throw new GameTestAssertException("Could not add villager");
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        return villager;
    }

    private static ServerPlayer fakePlayer(ServerLevel level, String name) {
        UUID id = UUID.nameUUIDFromBytes(("villagerretaliation:" + name).getBytes(StandardCharsets.UTF_8));
        return FakePlayerFactory.get(level, new GameProfile(id, name));
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

package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class HiredEntityClaimGameTests {
    private HiredEntityClaimGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "entity_claims")
    public static void huntersCoordinateMovingTargetsAndReleaseStoppedPursuits(GameTestHelper helper) {
        var level = helper.getLevel();
        var first = worker(helper, HiredVillagerRole.HUNTING);
        var second = worker(helper, HiredVillagerRole.HUNTING);
        var cow = cow(helper, new BlockPos(3, 2, 4));
        try {
            var firstSession = HiredWorkSession.active(level, first);
            var secondSession = HiredWorkSession.active(level, second);
            var targets = new HiredHuntingTargets.Selection(true, false, false);
            helper.assertTrue(HuntingWorker.tryAcquireTarget(level, first, firstSession.context(), targets), "the first hunter acquires the cow");
            cow.setPos(cow.position().add(1, 0, 0));
            helper.assertFalse(HuntingWorker.tryAcquireTarget(level, second, secondSession.context(), targets), "movement does not invalidate the first hunter's claim");
            firstSession.worker().stop(level, first, firstSession.context());
            helper.assertTrue(first.getTarget() == null, "stopping the worker ends its work pursuit");
            secondSession.state().remove("HuntingNextTargetScanGameTime");
            helper.assertTrue(HuntingWorker.tryAcquireTarget(level, second, secondSession.context(), targets), "another hunter can acquire a released target");
            helper.assertTrue(cow.isAlive(), "claim changes do not invent combat damage");
        } finally {
            HiredPathMemory.clear(first);
            HiredPathMemory.clear(second);
            first.discard();
            second.discard();
            cow.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "entity_claims")
    public static void pairClaimsAreAtomicAndExpireWhenTheirWorkerDies(GameTestHelper helper) {
        var level = helper.getLevel();
        var first = worker(helper, HiredVillagerRole.ANIMAL_HANDLING);
        var second = worker(helper, HiredVillagerRole.ANIMAL_HANDLING);
        var one = cow(helper, new BlockPos(3, 2, 4));
        var two = cow(helper, new BlockPos(4, 2, 4));
        try {
            helper.assertTrue(HiredEntityClaims.reserve(level, first, one), "one animal is claimed");
            helper.assertFalse(HiredEntityClaims.reserve(level, second, two, one), "a breeding pair is reserved together");
            helper.assertTrue(HiredEntityClaims.available(level, first, two), "a failed pair claim cannot retain its other animal");
            first.setHealth(0);
            helper.assertTrue(HiredEntityClaims.reserve(level, second, one, two), "a dead worker releases the pair immediately");
            HiredWorkSession.active(level, second).worker().pause(level, second, HiredWorkSession.active(level, second).context());
            helper.assertTrue(HiredEntityClaims.available(level, first, one), "pausing releases entity claims through the normal worker lifecycle");
        } finally {
            HiredPathMemory.clear(first);
            HiredPathMemory.clear(second);
            first.discard();
            second.discard();
            one.discard();
            two.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "entity_tasks")
    public static void selectedPursuitsRespectBoundsAndCollectEarnedLoot(GameTestHelper helper) {
        var level = helper.getLevel();
        var hunter = worker(helper, HiredVillagerRole.HUNTING);
        var selected = cow(helper, new BlockPos(3, 2, 5));
        var other = cow(helper, new BlockPos(3, 2, 4));
        try {
            var state = HiredWorkStateStore.state(hunter);
            state.putBoolean(HiredHuntingTargets.HUNT_ANIMALS_TAG, true);
            var task = entityTask(hunter, selected, HiredTemporaryTask.Kind.HUNT);
            helper.assertTrue(com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.submit(owner(hunter), hunter, task, false).isEmpty(),
                    "a personal hunter accepts an eligible selected target");
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
            try {
                task.write(buffer);
                helper.assertValueEqual(HiredTemporaryTask.read(buffer), task, "the selected entity survives the wire");
                helper.assertValueEqual(HiredTemporaryTask.load(task.save()), task, "the selected entity survives reload");
            } finally {
                buffer.release();
            }
            var session = HiredWorkSession.active(level, hunter);
            helper.assertTrue(HuntingWorker.tryAcquireTarget(level, hunter, session.context(), HiredHuntingTargets.fromState(state)), "the selected target can be acquired");
            helper.assertValueEqual(HuntingWorker.activeTarget(hunter).orElseThrow(), selected, "a nearer animal does not replace the selected target");
            selected.setPos(helper.absolutePos(new BlockPos(9, 2, 5)).getBottomCenter());
            helper.assertFalse(com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.ready(level, hunter, HiredWorkSession.active(level, hunter)),
                    "pursuit stops when the target leaves personal bounds");
            helper.assertTrue(hunter.getTarget() == null && !com.jvn.villagerretaliation.combat.VillagerRetaliationHandler.hasRetaliationTarget(hunter, selected),
                    "the old combat target and pending pursuit are cleared");
            selected.setPos(helper.absolutePos(new BlockPos(3, 2, 4)).getBottomCenter());
            selected.setHealth(0);
            var drop = new net.minecraft.world.entity.item.ItemEntity(level, hunter.getX(), hunter.getY(), hunter.getZ(), new ItemStack(Items.BEEF, 2));
            level.addFreshEntity(drop);
            tick(helper, hunter);
            tick(helper, hunter);
            helper.assertTrue(com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.queue(state) == null, "pursuit completes after collecting loot");
            helper.assertValueEqual(count(hunter, Items.BEEF), 2, "earned loot is collected once");
            helper.assertTrue(other.isAlive() && hunter.getTarget() == null, "completion does not acquire another animal");
        } finally {
            HiredPathMemory.clear(hunter);
            hunter.discard();
            selected.discard();
            other.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "entity_tasks")
    public static void selectedMilkingWaitsForSuppliesAndNeverBreedsInstead(GameTestHelper helper) {
        var level = helper.getLevel();
        var worker = worker(helper, HiredVillagerRole.ANIMAL_HANDLING);
        var selected = cow(helper, new BlockPos(3, 2, 4));
        var other = cow(helper, new BlockPos(4, 2, 4));
        try {
            var inventory = HiredJobInventory.getJobInventory(worker);
            inventory.insertSupply(new ItemStack(Items.WHEAT, 8));
            helper.assertTrue(com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.submit(owner(worker), worker,
                    entityTask(worker, selected, HiredTemporaryTask.Kind.MILK), false).isEmpty(), "the personal milking task starts");
            for (int i = 0; i < 5; i++) tick(helper, worker);
            helper.assertValueEqual(count(worker, Items.MILK_BUCKET), 0, "missing buckets block output");
            helper.assertFalse(selected.isInLove() || other.isInLove(), "missing buckets do not trigger unrelated breeding");
            inventory.insertSupply(new ItemStack(Items.BUCKET, 2));
            for (int slot = HiredJobInventory.MAIN_GRID_START; slot < HiredJobInventory.FILTER_SLOT; slot++) {
                if (inventory.getItem(slot).isEmpty()) inventory.setItem(slot, new ItemStack(Items.STONE, 64));
            }
            for (int i = 0; i < 5; i++) tick(helper, worker);
            helper.assertValueEqual(count(worker, Items.BUCKET), 2, "full output never consumes the empty bucket");
            for (int slot = HiredJobInventory.MAIN_GRID_START; slot < HiredJobInventory.FILTER_SLOT; slot++) {
                if (inventory.getItem(slot).is(Items.STONE)) inventory.setItem(slot, ItemStack.EMPTY);
            }
            for (int i = 0; i < 12 && HiredTemporaryEntities.current(HiredWorkStateStore.state(worker)) != null; i++) tick(helper, worker);
            helper.assertValueEqual(count(worker, Items.MILK_BUCKET), 1, "one selected task produces one milk bucket");
            helper.assertValueEqual(count(worker, Items.BUCKET), 1, "only one empty bucket is consumed");
            helper.assertValueEqual(count(worker, Items.WHEAT), 8, "breeding food remains untouched");
            helper.assertTrue(HiredTemporaryEntities.current(HiredWorkStateStore.state(worker)) == null, "the milking task finishes");
        } finally {
            HiredPathMemory.clear(worker);
            worker.discard();
            selected.discard();
            other.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "entity_tasks")
    public static void selectedBreedingUsesOnePairAndPreservesCooldown(GameTestHelper helper) {
        var level = helper.getLevel();
        var worker = worker(helper, HiredVillagerRole.ANIMAL_HANDLING);
        var selected = cow(helper, new BlockPos(3, 2, 5));
        var first = cow(helper, new BlockPos(3, 2, 4));
        var second = cow(helper, new BlockPos(4, 2, 4));
        try {
            HiredJobInventory.getJobInventory(worker).insertSupply(new ItemStack(Items.WHEAT, 4));
            helper.assertTrue(com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.submit(owner(worker), worker,
                    entityTask(worker, selected, HiredTemporaryTask.Kind.BREED), false).isEmpty(), "the selected breeding task starts");
            for (int i = 0; i < 12 && HiredTemporaryEntities.current(HiredWorkStateStore.state(worker)) != null; i++) tick(helper, worker);
            helper.assertTrue(selected.isInLove(), "the selected animal participates in the pair");
            helper.assertTrue(first.isInLove() != second.isInLove(), "exactly one partner is fed");
            helper.assertValueEqual(count(worker, Items.WHEAT), 2, "one pair consumes two food items");
            helper.assertTrue(AnimalBreedingWorker.isHandledAnimalOnCooldown(HiredWorkStateStore.state(worker), selected, level.getGameTime()),
                    "the normal breeding cooldown is retained");
            com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.submit(owner(worker), worker,
                    entityTask(worker, selected, HiredTemporaryTask.Kind.BREED), false);
            for (int i = 0; i < 5; i++) tick(helper, worker);
            helper.assertValueEqual(count(worker, Items.WHEAT), 2, "reissuing a command cannot bypass cooldown");
        } finally {
            HiredPathMemory.clear(worker);
            worker.discard();
            selected.discard();
            first.discard();
            second.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "entity_tasks")
    public static void selectedShearingAndCollectionKeepTargetAndOutputLimits(GameTestHelper helper) {
        var level = helper.getLevel();
        var worker = worker(helper, HiredVillagerRole.ANIMAL_HANDLING);
        var sheep = EntityType.SHEEP.create(level);
        sheep.setPos(helper.absolutePos(new BlockPos(3, 2, 4)).getBottomCenter());
        sheep.setNoAi(true);
        level.addFreshEntity(sheep);
        var other = EntityType.SHEEP.create(level);
        other.setPos(helper.absolutePos(new BlockPos(4, 2, 3)).getBottomCenter());
        other.setNoAi(true);
        level.addFreshEntity(other);
        try {
            var inventory = HiredJobInventory.getJobInventory(worker);
            inventory.insertSupply(new ItemStack(Items.SHEARS));
            helper.assertTrue(com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.submit(owner(worker), worker,
                    entityTask(worker, sheep, HiredTemporaryTask.Kind.SHEAR), false).isEmpty(), "selected shearing starts");
            for (int i = 0; i < 12 && HiredTemporaryEntities.current(HiredWorkStateStore.state(worker)) != null; i++) tick(helper, worker);
            helper.assertTrue(sheep.isSheared() && !other.isSheared(), "only the selected sheep is sheared");
            int wool = count(worker, Items.WHITE_WOOL);
            helper.assertTrue(wool >= 1 && wool <= 3, "native wool quantities are preserved");
            var home = HiredWorkArea.fromState(HiredWorkStateStore.state(worker), worker.blockPosition());
            var task = new HiredTemporaryTask(UUID.randomUUID(), HiredTemporaryTask.Kind.COLLECT, home.min(), home.max(), 1);
            var eggs = new net.minecraft.world.entity.item.ItemEntity(level, worker.getX(), worker.getY(), worker.getZ(), new ItemStack(Items.EGG, 3));
            level.addFreshEntity(eggs);
            com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.submit(owner(worker), worker, task, false);
            HiredPathMemory.isAvoided(level, worker, eggs.blockPosition());
            for (int i = 0; i < 5; i++) HiredPathMemory.recordFailure(level, worker, eggs.blockPosition());
            tick(helper, worker);
            helper.assertTrue(HiredTemporaryEntities.current(HiredWorkStateStore.state(worker)) != null, "unreachable products cannot falsely complete collection");
            HiredPathMemory.clear(worker);
            tick(helper, worker);
            tick(helper, worker);
            helper.assertValueEqual(count(worker, Items.EGG), 3, "collection transfers actual dropped eggs");
            helper.assertTrue(HiredTemporaryEntities.current(HiredWorkStateStore.state(worker)) == null, "collection finishes after all available products are collected");
        } finally {
            HiredPathMemory.clear(worker);
            worker.discard();
            sheep.discard();
            other.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "entity_tasks")
    public static void selectedCullingPreservesProtectedAnimalsAndHerdCaps(GameTestHelper helper) {
        var worker = worker(helper, HiredVillagerRole.ANIMAL_HANDLING);
        var selected = cow(helper, new BlockPos(3, 2, 4));
        var other = cow(helper, new BlockPos(4, 2, 4));
        Cow excess = null;
        try {
            var level = helper.getLevel();
            var state = HiredWorkStateStore.state(worker);
            HiredAnimalCullSettings.setCap(state, 2);
            selected.setCustomName(net.minecraft.network.chat.Component.literal("Protected"));
            helper.assertFalse(com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.submit(owner(worker), worker,
                    entityTask(worker, selected, HiredTemporaryTask.Kind.CULL), false).isEmpty(), "named animals remain protected");
            selected.setCustomName(null);
            helper.assertTrue(com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.submit(owner(worker), worker,
                    entityTask(worker, selected, HiredTemporaryTask.Kind.CULL), false).isEmpty(), "a culling task can wait for an excess animal");
            for (int i = 0; i < 5; i++) tick(helper, worker);
            helper.assertTrue(selected.isAlive() && other.isAlive(), "the herd cap prevents culling");
            excess = cow(helper, new BlockPos(4, 2, 5));
            state.remove("NextAnimalBreedingScanGameTime");
            for (int i = 0; i < 8 && selected.isAlive(); i++) tick(helper, worker);
            helper.assertFalse(selected.isAlive(), "only the selected excess animal is culled");
            helper.assertTrue(other.isAlive() && excess.isAlive(), "the remaining herd is preserved");
            helper.assertTrue(HiredTemporaryEntities.handled(state) && state.getBoolean("AnimalCullCleanupPending"), "native cleanup remains pending after the kill");
            com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.submit(owner(worker), worker, null, false);
            helper.assertTrue(HiredTemporaryEntities.current(state) == null && other.isAlive(), "cancellation cannot cause a second cull");
        } finally {
            if (excess != null) excess.discard();
            HiredPathMemory.clear(worker);
            worker.discard();
            selected.discard();
            other.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "entity_tasks")
    public static void clipboardTargetsStayPersonalAndMissingEntitiesRemainBlocked(GameTestHelper helper) {
        var level = helper.getLevel();
        var worker = worker(helper, HiredVillagerRole.ANIMAL_HANDLING);
        var target = cow(helper, new BlockPos(3, 2, 4));
        var owner = owner(worker);
        var clipboard = new ItemStack(com.jvn.villagerretaliation.item.VillagerRetaliationItems.CLIPBOARD.get());
        try {
            owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, clipboard);
            owner.setShiftKeyDown(true);
            clipboard.getItem().interactLivingEntity(clipboard, owner, target, net.minecraft.world.InteractionHand.MAIN_HAND);
            owner.setShiftKeyDown(false);
            com.jvn.villagerretaliation.item.HiredStorageClipboardItem.assignHeldTemporaryTask(owner, level, worker, false, 1, HiredTemporaryTask.Kind.MILK);
            var state = HiredWorkStateStore.state(worker);
            helper.assertValueEqual(HiredTemporaryEntities.current(state).entity(), target.getUUID(), "Clipboard selection starts the personal task");
            helper.assertTrue(com.jvn.villagerretaliation.settlement.SettlementWorkforceService.duty(worker).isEmpty(), "Clipboard tasks do not adopt a settlement role");
            target.discard();
            helper.assertFalse(com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.ready(level, worker, HiredWorkSession.active(level, worker)),
                    "a disappeared target blocks the task");
            helper.assertTrue(HiredTemporaryEntities.current(state) != null, "disappearance cannot award completion");
            var saved = state.copy();
            saved.putString("TemporaryEntityPosition", "damaged");
            helper.assertTrue(com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.queue(saved) == null
                    && saved.getBoolean("TemporaryTaskInvalid"), "damaged entity progress is rejected");
            com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.submit(owner, worker, null, false);
            helper.assertTrue(HiredTemporaryEntities.current(state) == null, "a missing target can be cancelled");
            com.jvn.villagerretaliation.item.HiredStorageClipboardItem.clearSelection(clipboard);
            helper.assertFalse(clipboard.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getCompound("VillagerRetaliationClipboard").contains("EntitySelection"),
                    "clearing the Clipboard also clears its selected animal");
        } finally {
            owner.setShiftKeyDown(false);
            owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            HiredPathMemory.clear(worker);
            worker.discard();
            target.discard();
        }
        helper.succeed();
    }

    private static HiredTemporaryTask entityTask(Villager worker, net.minecraft.world.entity.LivingEntity target, HiredTemporaryTask.Kind kind) {
        var home = HiredWorkArea.fromState(HiredWorkStateStore.state(worker), worker.blockPosition());
        return new HiredTemporaryTask(UUID.randomUUID(), kind, home.min(), home.max(), 1, null, target.getUUID());
    }

    private static net.minecraft.server.level.ServerPlayer owner(Villager worker) {
        var level = (net.minecraft.server.level.ServerLevel) worker.level();
        return FakePlayerFactory.get(level, new GameProfile(HiredVillagerContractService.getHirer(level, worker).orElseThrow(), "EntityTaskOwner"));
    }

    private static void tick(GameTestHelper helper, Villager worker) {
        var level = helper.getLevel();
        var session = HiredWorkSession.active(level, worker);
        if (com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.ready(level, worker, session)) {
            var result = session.worker().tick(level, worker, owner(worker), session.context());
            com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.completed(level, worker, session, result);
        }
    }

    private static int count(Villager worker, net.minecraft.world.item.Item item) {
        var inventory = HiredJobInventory.getJobInventory(worker);
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) count += inventory.getItem(slot).getCount();
        }
        return count;
    }

    private static Villager worker(GameTestHelper helper, HiredVillagerRole role) {
        var level = helper.getLevel();
        for (var pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(7, 1, 7))) helper.setBlock(pos, Blocks.STONE);
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "EntityTaskOwner"));
        var villager = EntityType.VILLAGER.create(level);
        villager.setPos(helper.absolutePos(new BlockPos(3, 2, 3)).getBottomCenter());
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(
                role == HiredVillagerRole.HUNTING ? VillagerProfession.FLETCHER : VillagerProfession.SHEPHERD));
        level.addFreshEntity(villager);
        helper.assertTrue(HiredVillagerContractService.startHireContract(level, villager, owner, 1, 32, role), "the personal contract starts");
        HiredWorkArea.fromBounds(helper.absolutePos(new BlockPos(0, 2, 0)), helper.absolutePos(new BlockPos(7, 5, 7)), true)
                .save(HiredWorkStateStore.state(villager));
        HiredJobInventory.getJobInventory(villager).setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_SWORD));
        HiredWorkStateStore.state(villager).putBoolean("AutoDepositOutputs", false);
        HiredWorkStateStore.state(villager).putBoolean("UseAssignedStorageForSupplies", false);
        HiredWorkSession.invalidate(villager);
        return villager;
    }

    private static Cow cow(GameTestHelper helper, BlockPos position) {
        var cow = EntityType.COW.create(helper.getLevel());
        cow.setPos(helper.absolutePos(position).getBottomCenter());
        cow.setNoAi(true);
        helper.getLevel().addFreshEntity(cow);
        return cow;
    }
}

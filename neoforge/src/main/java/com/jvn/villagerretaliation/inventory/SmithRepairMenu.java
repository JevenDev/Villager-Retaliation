package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.repair.SmithRepairPrices;
import com.jvn.villagerretaliation.repair.SmithRepairService;
import com.jvn.villagerretaliation.repair.SmithRepairAnimationState;
import com.jvn.villagerretaliation.repair.SmithRepairWorkstation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class SmithRepairMenu extends AbstractContainerMenu {
    public enum Status {
        EMPTY, INVALID, FULL, WRONG_MATERIAL, FUNDS, UNAVAILABLE, READY, NO_WORKSTATION, WORKING, RETURNING, APPROACHING
    }

    private static final int PLAYER_START = 3;
    private static final int PLAYER_END = 39;
    private final SimpleContainer input = new SimpleContainer(2);
    private final SimpleContainer preview = new SimpleContainer(1);
    private final ContainerData data = new SimpleContainerData(3);
    private final Player owner;
    private final Villager villager;
    private final int entityId;
    private final ResourceLocation currencyIcon;
    private ItemStack checkedItem = ItemStack.EMPTY;
    private boolean repairable;
    private int checkedGeneration = -1;
    private BlockPos workstation;
    private Vec3 standingPosition;
    private Vec3 interactionOrigin;
    private long approachStarted;
    private long returnStarted;
    private long lastStrike = -1;
    private int quotedCost;
    private ItemStack workingItem = ItemStack.EMPTY;
    private ItemStack workingMaterial = ItemStack.EMPTY;
    private ItemStack workingResult = ItemStack.EMPTY;
    private boolean completed;
    private BlockPos nearbyStation;
    private long nextStationCheck;

    public SmithRepairMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, null, buffer.readVarInt(), buffer.readResourceLocation());
    }

    public SmithRepairMenu(int containerId, Inventory inventory, Villager villager) {
        this(containerId, inventory, villager, villager.getId(),
                VillagerCurrencyResources.text(villager.level().getServer()).iconSprite());
        villager.goalSelector.getAvailableGoals().forEach(goal -> goal.stop());
        villager.targetSelector.getAvailableGoals().forEach(goal -> goal.stop());
        villager.getBrain().stopAll((ServerLevel) villager.level(), villager);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.PATH);
        villager.getNavigation().stop();
        villager.setTradingPlayer(inventory.player);
        refresh();
    }

    private SmithRepairMenu(int containerId, Inventory inventory, Villager villager, int entityId, ResourceLocation currencyIcon) {
        super(VillagerRetaliationMenus.SMITH_REPAIR.get(), containerId);
        this.owner = inventory.player;
        this.villager = villager;
        this.entityId = entityId;
        this.currencyIcon = currencyIcon;
        addSlot(new Slot(input, 0, 27, 47) {
            @Override
            public int getMaxStackSize() { return 1; }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.isDamaged() && SmithRepairService.repairable(stack);
            }
        });
        addSlot(new Slot(input, 1, 76, 47) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return SmithRepairService.validMaterial(input.getItem(0), stack);
            }
        });
        addSlot(new Slot(preview, 0, 134, 47) {
            @Override
            public boolean mayPlace(ItemStack stack) { return false; }

            @Override
            public boolean mayPickup(Player player) { return false; }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, 105 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 163));
        addDataSlots(data);
    }

    public static SmithRepairMenu controlling(Villager villager) {
        if (villager.getTradingPlayer() instanceof ServerPlayer player
                && player.containerMenu instanceof SmithRepairMenu menu && menu.villager == villager
                && menu.stillValid(player) && !villager.isOnFire() && !villager.isPassenger()
                && !villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY_ENTITY)) return menu;
        return null;
    }

    public int entityId() { return entityId; }
    public ResourceLocation currencyIcon() { return currencyIcon; }
    public int cost() { return (data.get(0) & 0xFFFF) | ((data.get(1) & 0xFFFF) << 16); }
    public Status status() { return Status.values()[data.get(2)]; }
    public boolean isBusy() { return status() == Status.APPROACHING || status() == Status.WORKING || status() == Status.RETURNING; }

    @Override
    public boolean stillValid(Player player) {
        return player == owner && (villager == null ? player.level().isClientSide
                : player instanceof ServerPlayer serverPlayer && villager.getTradingPlayer() == player
                    && VillagerInteractionService.canUseSmithRepair(serverPlayer, villager,
                            workstation == null ? villager.position() : interactionOrigin));
    }

    private void refresh() {
        if (!(owner instanceof ServerPlayer player) || villager == null) return;
        ItemStack item = input.getItem(0);
        ItemStack material = input.getItem(1);
        int generation = SmithRepairPrices.generation();
        if (checkedGeneration != generation || !ItemStack.matches(item, checkedItem)) {
            checkedItem = item.copy();
            checkedGeneration = generation;
            repairable = SmithRepairService.repairable(item);
        }
        if (workstation != null) {
            data.set(0, quotedCost & 0xFFFF);
            data.set(1, quotedCost >>> 16);
            data.set(2, (completed ? Status.RETURNING
                    : ((SmithRepairAnimationState) villager).villagerretaliation$repairStarted() == SmithRepairAnimationState.APPROACHING
                        ? Status.APPROACHING : Status.WORKING).ordinal());
            return;
        }
        long now = villager.level().getGameTime();
        if (now >= nextStationCheck || nearbyStation != null && !SmithRepairWorkstation.available(villager, nearbyStation)) {
            nearbyStation = SmithRepairWorkstation.find(villager);
            nextStationCheck = now + 20;
        }
        Status status;
        int cost = 0;
        ItemStack result = ItemStack.EMPTY;
        if (!stillValid(player)) status = Status.UNAVAILABLE;
        else if (item.isEmpty()) status = Status.EMPTY;
        else if (!repairable || item.getCount() != 1) status = Status.INVALID;
        else if (!item.isDamaged()) status = Status.FULL;
        else if (!material.isEmpty() && !SmithRepairService.validMaterial(item, material)) status = Status.WRONG_MATERIAL;
        else {
            boolean specialized = SmithRepairService.specialized(villager.getVillagerData().getProfession(), item);
            cost = SmithRepairService.cost(SmithRepairPrices.basePrice(player.getServer(), item), specialized, !material.isEmpty());
            result = SmithRepairService.preview(item, specialized);
            status = VillagerCurrencyPayment.count(player) < cost ? Status.FUNDS
                    : nearbyStation == null ? Status.NO_WORKSTATION : Status.READY;
        }
        preview.setItem(0, result);
        data.set(0, cost & 0xFFFF);
        data.set(1, cost >>> 16);
        data.set(2, status.ordinal());
    }

    @Override
    public void broadcastChanges() {
        tickVillager();
        refresh();
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != 0 || player != owner || !(player instanceof ServerPlayer)) return false;
        nextStationCheck = 0;
        refresh();
        if (status() != Status.READY || SmithRepairAnimationState.isActive(villager)) return false;
        BlockPos station = SmithRepairWorkstation.find(villager);
        Vec3 standing = station == null ? null : SmithRepairWorkstation.standingPosition(villager, station);
        if (standing == null) {
            data.set(2, Status.NO_WORKSTATION.ordinal());
            return false;
        }
        interactionOrigin = villager.position();
        workstation = station;
        standingPosition = standing;
        approachStarted = villager.level().getGameTime();
        quotedCost = cost();
        workingItem = input.getItem(0).copy();
        workingMaterial = input.getItem(1).copy();
        workingResult = preview.getItem(0).copy();
        completed = false;
        lastStrike = -1;
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        villager.getNavigation().stop();
        ((SmithRepairAnimationState) villager).villagerretaliation$repairAnimation(station, workingItem, SmithRepairAnimationState.APPROACHING);
        broadcastChanges();
        return true;
    }

    public void tickVillager() {
        if (villager == null || !(owner instanceof ServerPlayer player)) return;
        if (workstation != null) {
            tickRepair();
        } else if (stillValid(player) && !villager.isOnFire() && !villager.isPassenger()
                && !villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY_ENTITY)) {
            approachPlayer(player, villager.level().getGameTime(), 0.5);
        }
    }

    private void tickRepair() {
        if (workstation == null || !(owner instanceof ServerPlayer player)) return;
        var animation = (SmithRepairAnimationState) villager;
        if (!stillValid(player) || !completed && !SmithRepairWorkstation.available(villager, workstation)
                || villager.isOnFire() || villager.isPassenger()
                || villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY_ENTITY)
                || !completed && (!ItemStack.matches(workingItem, input.getItem(0))
                    || !ItemStack.matches(workingMaterial, input.getItem(1)))) {
            stopRepair();
            return;
        }
        long now = villager.level().getGameTime();
        if (completed) {
            returnToPlayer(player, now);
            return;
        }
        long started = animation.villagerretaliation$repairStarted();
        if (started == SmithRepairAnimationState.APPROACHING) {
            Vec3 item = SmithRepairWorkstation.itemPosition(villager, workstation);
            villager.getLookControl().setLookAt(item.x, item.y, item.z, 30, 30);
            if (villager.onGround() && villager.position().distanceToSqr(standingPosition) <= 0.04
                    && !villager.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(workstation))) {
                villager.getNavigation().stop();
                face(item);
                animation.villagerretaliation$repairAnimation(workstation, workingItem, now);
            } else if (now - approachStarted >= SmithRepairWorkstation.APPROACH_TIMEOUT_TICKS) {
                stopRepair();
            } else if (villager.position().distanceToSqr(standingPosition) < 1
                    && villager.level().noCollision(villager, villager.getBoundingBox()
                            .expandTowards(standingPosition.subtract(villager.position()).multiply(1, 0, 1)).deflate(1.0E-5))) {
                villager.getNavigation().stop();
                villager.getMoveControl().setWantedPosition(standingPosition.x, standingPosition.y, standingPosition.z, 0.35);
            } else if (villager.getNavigation().isDone() || now % 20 == 0) {
                villager.getNavigation().moveTo(standingPosition.x, standingPosition.y, standingPosition.z, 0, 0.6);
            }
            return;
        }
        if (villager.position().distanceToSqr(standingPosition) > 0.16) {
            approachStarted = now;
            lastStrike = -1;
            animation.villagerretaliation$repairAnimation(workstation, workingItem, SmithRepairAnimationState.APPROACHING);
            return;
        }
        villager.getNavigation().stop();
        long elapsed = now - started;
        if (elapsed >= SmithRepairWorkstation.WORK_TICKS) {
            if (!VillagerCurrencyPayment.tryRemove(player, quotedCost)) {
                stopRepair();
                return;
            }
            input.getItem(1).shrink(1);
            input.setItem(0, workingResult.copy());
            completed = true;
            returnStarted = now;
            animation.villagerretaliation$repairAnimation(workstation, workingResult, SmithRepairAnimationState.RETURNING);
            returnToPlayer(player, now);
        } else {
            Vec3 item = SmithRepairWorkstation.itemPosition(villager, workstation);
            face(item);
            if (elapsed >= 10 && (elapsed - 10) % 16 == 0 && lastStrike != elapsed) {
                lastStrike = elapsed;
                villager.level().playSound(null, workstation, net.minecraft.sounds.SoundEvents.ANVIL_USE,
                        net.minecraft.sounds.SoundSource.NEUTRAL, 0.35F, 1.1F + villager.getRandom().nextFloat() * 0.15F);
                if (villager.level() instanceof net.minecraft.server.level.ServerLevel level)
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, item.x, item.y + 0.03, item.z, 4, 0.06, 0.03, 0.06, 0.08);
            }
        }
    }

    private void returnToPlayer(ServerPlayer player, long now) {
        if (villager.distanceToSqr(player) <= 1.75 * 1.75 || now - returnStarted >= SmithRepairWorkstation.RETURN_TIMEOUT_TICKS) {
            stopRepair();
            face(player.getEyePosition());
        } else {
            approachPlayer(player, now, 0.6);
        }
    }

    private void approachPlayer(ServerPlayer player, long now, double speed) {
        villager.getLookControl().setLookAt(player, 30, 30);
        if (villager.distanceToSqr(player) <= 1.75 * 1.75) {
            villager.getNavigation().stop();
        } else if (villager.getNavigation().isDone() || now % 20 == 0) {
            villager.getNavigation().moveTo(player.getX(), player.getY(), player.getZ(), 0, speed);
        }
    }

    private void face(Vec3 target) {
        Vec3 direction = target.subtract(villager.position());
        float yaw = (float) (Mth.atan2(direction.z, direction.x) * 180 / Math.PI) - 90;
        villager.setYRot(Mth.rotLerp(0.35F, villager.getYRot(), yaw));
        villager.yBodyRot = villager.getYRot();
        villager.setYHeadRot(Mth.rotLerp(0.35F, villager.getYHeadRot(), yaw));
        float pitch = (float) (Mth.atan2(villager.getEyeY() - target.y, direction.horizontalDistance()) * Mth.RAD_TO_DEG);
        villager.setXRot(Mth.lerp(0.35F, villager.getXRot(), pitch));
        villager.getLookControl().setLookAt(target.x, target.y, target.z, 30, 30);
    }

    private void stopRepair() {
        if (workstation == null) return;
        ((SmithRepairAnimationState) villager).villagerretaliation$repairAnimation(null, ItemStack.EMPTY, SmithRepairAnimationState.APPROACHING);
        villager.getNavigation().stop();
        workstation = null;
        workingItem = ItemStack.EMPTY;
        workingMaterial = ItemStack.EMPTY;
        workingResult = ItemStack.EMPTY;
        nearbyStation = null;
        nextStationCheck = 0;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == 2 || isBusy() || !stillValid(player)) return;
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (isBusy() || !stillValid(player) || index < 0 || index >= slots.size() || index == 2) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < PLAYER_START) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) return ItemStack.EMPTY;
        } else if (slots.get(0).mayPlace(stack) && !slots.get(0).hasItem()) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (slots.get(1).mayPlace(stack)) {
            if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
        } else if (index < 30) {
            if (!moveItemStackTo(stack, 30, PLAYER_END, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, PLAYER_START, 30, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player == owner && !player.level().isClientSide) {
            stopRepair();
            clearContainer(player, input);
            if (villager != null && villager.getTradingPlayer() == player) {
                villager.getNavigation().stop();
                villager.getMoveControl().setWantedPosition(villager.getX(), villager.getY(), villager.getZ(), 0);
                villager.setTradingPlayer(null);
            }
        }
    }
}

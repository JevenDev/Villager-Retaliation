package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.repair.SmithRepairPrices;
import com.jvn.villagerretaliation.repair.SmithRepairService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
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
        EMPTY, INVALID, FULL, WRONG_MATERIAL, FUNDS, UNAVAILABLE, READY
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

    public SmithRepairMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, null, buffer.readVarInt(), buffer.readResourceLocation());
    }

    public SmithRepairMenu(int containerId, Inventory inventory, Villager villager) {
        this(containerId, inventory, villager, villager.getId(),
                VillagerCurrencyResources.text(villager.level().getServer()).iconSprite());
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

    public int entityId() { return entityId; }
    public ResourceLocation currencyIcon() { return currencyIcon; }
    public int cost() { return (data.get(0) & 0xFFFF) | ((data.get(1) & 0xFFFF) << 16); }
    public Status status() { return Status.values()[data.get(2)]; }

    @Override
    public boolean stillValid(Player player) {
        return player == owner && (villager == null ? player.level().isClientSide
                : player instanceof ServerPlayer serverPlayer && villager.getTradingPlayer() == player
                    && VillagerInteractionService.canUseSmithRepair(serverPlayer, villager));
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
            status = VillagerCurrencyPayment.count(player) >= cost ? Status.READY : Status.FUNDS;
        }
        preview.setItem(0, result);
        data.set(0, cost & 0xFFFF);
        data.set(1, cost >>> 16);
        data.set(2, status.ordinal());
    }

    @Override
    public void broadcastChanges() {
        refresh();
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != 0 || player != owner || !(player instanceof ServerPlayer serverPlayer)) return false;
        refresh();
        if (status() != Status.READY || !VillagerCurrencyPayment.tryRemove(serverPlayer, cost())) return false;
        ItemStack result = preview.getItem(0).copy();
        input.getItem(1).shrink(1);
        input.setItem(0, result);
        broadcastChanges();
        player.level().playSound(null, villager.blockPosition(), net.minecraft.sounds.SoundEvents.ANVIL_USE,
                net.minecraft.sounds.SoundSource.NEUTRAL, 0.5F, 1.0F);
        return true;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == 2 || !stillValid(player)) return;
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size() || index == 2) return ItemStack.EMPTY;
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
            clearContainer(player, input);
            if (villager != null && villager.getTradingPlayer() == player) villager.setTradingPlayer(null);
        }
    }
}

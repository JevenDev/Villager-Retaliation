package com.jvn.villagerretaliation.item;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.inventory.AssignedStorageService.AssignSummary;
import com.jvn.villagerretaliation.inventory.AssignedStorageService.StoragePosition;
import com.jvn.villagerretaliation.network.ClipboardAssignedStorageSyncPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public final class HiredStorageClipboardItem extends Item {
    private static final String TAG = "VillagerRetaliationClipboard";
    private static final String SELECTED_TAG = "Selected";
    private static final String DIMENSION_TAG = "Dimension";
    private static final String POS_TAG = "Pos";
    private static final int MAX_SELECTIONS = 8;

    public HiredStorageClipboardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        return selectContainer(serverLevel, serverPlayer, context.getItemInHand(), context.getClickedPos());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && player.isShiftKeyDown()) {
            clearSelection(stack);
            serverPlayer.displayClientMessage(Component.literal("Clipboard selection cleared."), true);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand usedHand) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(target instanceof Villager villager)) {
            return InteractionResult.PASS;
        }

        ServerLevel level = serverPlayer.serverLevel();
        List<StoragePosition> selected = selectedContainers(stack);
        if (!selected.isEmpty()) {
            AssignSummary summary = AssignedStorageService.assign(serverPlayer, villager, selected, "general");
            if (summary.assigned() > 0) {
                clearSelection(stack);
            }
            serverPlayer.displayClientMessage(AssignedStorageService.assignmentSummary(summary), true);
            return InteractionResult.SUCCESS;
        }

        if (AssignedStorageService.hasAssignedStorage(level, villager)) {
            if (serverPlayer.isShiftKeyDown()) {
                int removed = AssignedStorageService.removeAssignedStorage(level, villager);
                serverPlayer.displayClientMessage(Component.literal("Removed " + removed + " assigned container" + (removed == 1 ? "" : "s") + "."), true);
            } else {
                List<AssignedContainerRecord> assigned = AssignedStorageService.assignedStorage(level, villager);
                sendAssignedStorageOutlines(serverPlayer, assigned);
                int count = assigned.size();
                serverPlayer.displayClientMessage(Component.literal("Assigned containers: " + count + "."), true);
            }
            return InteractionResult.SUCCESS;
        }

        serverPlayer.displayClientMessage(Component.literal("Select containers, then use the clipboard on a villager."), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int count = selectedContainers(stack).size();
        if (count > 0) {
            tooltip.add(Component.literal(count + " selected container" + (count == 1 ? "" : "s")));
        }
    }

    public static List<StoragePosition> selectedContainers(ItemStack stack) {
        List<StoragePosition> positions = new ArrayList<>();
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(TAG)) {
            return positions;
        }
        ListTag selectedTag = customData.copyTag().getCompound(TAG).getList(SELECTED_TAG, Tag.TAG_COMPOUND);
        for (Tag rawEntry : selectedTag) {
            if (!(rawEntry instanceof CompoundTag entryTag) || !entryTag.contains(POS_TAG, Tag.TAG_LONG)) {
                continue;
            }
            ResourceLocation dimensionId = ResourceLocation.tryParse(entryTag.getString(DIMENSION_TAG));
            if (dimensionId == null) {
                continue;
            }
            positions.add(new StoragePosition(
                    ResourceKey.create(Registries.DIMENSION, dimensionId),
                    BlockPos.of(entryTag.getLong(POS_TAG))
            ));
        }
        return positions;
    }

    public static InteractionResult selectContainer(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos pos) {
        if (player.isShiftKeyDown()) {
            clearSelection(stack);
            player.displayClientMessage(Component.literal("Clipboard selection cleared."), true);
            return InteractionResult.SUCCESS;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof net.minecraft.world.Container)) {
            player.displayClientMessage(Component.literal("Select a valid container."), true);
            return InteractionResult.FAIL;
        }

        SelectionAddResult result = addSelection(stack, level.dimension(), pos);
        Component message = switch (result) {
            case ADDED -> Component.literal("Container selected.");
            case DUPLICATE -> Component.literal("That container is already selected.");
            case DIFFERENT_DIMENSION -> Component.literal("Clipboard selections must stay in one dimension.");
            case FULL -> Component.literal("Clipboard selection is full.");
        };
        player.displayClientMessage(message, true);
        return InteractionResult.SUCCESS;
    }

    public static void sendAssignedStorageOutlines(ServerPlayer player, List<AssignedContainerRecord> records) {
        List<ClipboardAssignedStorageSyncPayload.Entry> entries = records.stream()
                .map(record -> new ClipboardAssignedStorageSyncPayload.Entry(record.dimension().location(), record.pos()))
                .toList();
        PacketDistributor.sendToPlayer(player, new ClipboardAssignedStorageSyncPayload(entries, 200));
    }

    private static SelectionAddResult addSelection(ItemStack stack, ResourceKey<Level> dimension, BlockPos pos) {
        List<StoragePosition> selected = selectedContainers(stack);
        Optional<StoragePosition> first = selected.stream().findFirst();
        if (first.isPresent() && !first.get().dimension().equals(dimension)) {
            return SelectionAddResult.DIFFERENT_DIMENSION;
        }
        for (StoragePosition position : selected) {
            if (position.dimension().equals(dimension) && position.pos().equals(pos)) {
                return SelectionAddResult.DUPLICATE;
            }
        }
        if (selected.size() >= MAX_SELECTIONS) {
            return SelectionAddResult.FULL;
        }
        selected.add(new StoragePosition(dimension, pos.immutable()));
        saveSelection(stack, selected);
        return SelectionAddResult.ADDED;
    }

    public static void clearSelection(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(TAG)) {
            return;
        }
        CompoundTag tag = customData.copyTag();
        tag.remove(TAG);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    private static void saveSelection(ItemStack stack, List<StoragePosition> selected) {
        CompoundTag clipboardTag = new CompoundTag();
        ListTag selectedTag = new ListTag();
        for (StoragePosition position : selected) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString(DIMENSION_TAG, position.dimension().location().toString());
            entryTag.putLong(POS_TAG, position.pos().asLong());
            selectedTag.add(entryTag);
        }
        clipboardTag.put(SELECTED_TAG, selectedTag);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(TAG, clipboardTag));
    }

    private enum SelectionAddResult {
        ADDED,
        DUPLICATE,
        DIFFERENT_DIMENSION,
        FULL
    }
}

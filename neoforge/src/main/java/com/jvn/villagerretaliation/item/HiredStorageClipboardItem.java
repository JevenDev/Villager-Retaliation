package com.jvn.villagerretaliation.item;

import com.jvn.villagerretaliation.dialogue.VillagerDialogueResources;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.inventory.AssignedStorageService.AssignmentSummaryMessage;
import com.jvn.villagerretaliation.inventory.AssignedStorageService.AssignSummary;
import com.jvn.villagerretaliation.inventory.AssignedStorageService.StoragePosition;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceService;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.network.ClipboardAssignedStorageSyncPayload;
import com.jvn.villagerretaliation.network.ClipboardWorkAreaDraftPayload;
import com.jvn.villagerretaliation.network.ClipboardWorkAreaSyncPayload;
import com.jvn.villagerretaliation.util.VillagerLocale;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.inventory.Slot;
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
    private static final String WORK_AREA_SELECTION_TAG = "WorkAreaSelection";
    private static final String DIMENSION_TAG = "Dimension";
    private static final String POS_TAG = "Pos";
    private static final String FIRST_POS_TAG = "FirstPos";
    private static final String SECOND_POS_TAG = "SecondPos";
    private static final String MODE_TAG = "Mode";
    private static final int MAX_SELECTIONS = 8;
    private static final int DEFAULT_WORK_AREA_HORIZONTAL_RADIUS = 4;
    private static final int DEFAULT_WORK_AREA_VERTICAL_RADIUS = 2;
    private static final int MAX_WORK_AREA_DRAFT_RADIUS = 64;

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
        ItemStack stack = context.getItemInHand();
        return handleRightClickBlock(serverLevel, serverPlayer, stack, context.getClickedPos());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && player.isShiftKeyDown()) {
            clearSelection(stack);
            serverPlayer.displayClientMessage(Component.literal("Clipboard selection cleared."), true);
            return InteractionResultHolder.success(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            ClipboardWorkforceService.openClipboard(serverPlayer);
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
        ClipboardMode clipboardMode = mode(stack);
        if (clipboardMode == ClipboardMode.WORK_AREA) {
            if (!HiredVillagerWorkService.canManageWork(level, villager, serverPlayer)) {
                serverPlayer.displayClientMessage(Component.literal("Only the hiring player can inspect this work area."), true);
                return InteractionResult.SUCCESS;
            }
            sendWorkAreaOutline(serverPlayer, level, villager);
            return InteractionResult.SUCCESS;
        }
        if (clipboardMode == ClipboardMode.SET_WORK_AREA) {
            assignSelectedWorkArea(serverPlayer, level, villager, stack);
            return InteractionResult.SUCCESS;
        }

        if (clipboardMode == ClipboardMode.ASSIGN_PAYMENT) {
            List<StoragePosition> selected = selectedContainers(stack);
            if (!selected.isEmpty()) {
                if (!HiredVillagerContractService.isHiredBy(level, villager, serverPlayer)) {
                    serverPlayer.displayClientMessage(Component.literal("Hire this villager before assigning payment storage."), true);
                    return InteractionResult.SUCCESS;
                }
                AssignSummary summary = AssignedStorageService.assign(serverPlayer, villager, selected, AssignedStorageService.PAYMENT_PURPOSE);
                if (summary.assigned() > 0) {
                    clearSelection(stack);
                    HiredVillagerContractService.setAutoPaymentEnabled(villager, true);
                }
                displayAssignmentSummary(serverPlayer, summary);
                return InteractionResult.SUCCESS;
            }
            List<AssignedContainerRecord> assigned = AssignedStorageService.assignedPaymentStorage(level, villager);
            if (!assigned.isEmpty()) {
                sendAssignedStorageOutlines(serverPlayer, assigned);
                serverPlayer.displayClientMessage(Component.literal("Payment containers: " + assigned.size() + "."), true);
                return InteractionResult.SUCCESS;
            }
            serverPlayer.displayClientMessage(Component.literal("Select a payment container, then use the clipboard on a hired villager."), true);
            return InteractionResult.SUCCESS;
        }

        List<StoragePosition> selected = selectedContainers(stack);
        if (!selected.isEmpty()) {
            AssignSummary summary = AssignedStorageService.assign(serverPlayer, villager, selected, AssignedStorageService.GENERAL_PURPOSE);
            if (summary.assigned() > 0) {
                clearSelection(stack);
            }
            displayAssignmentSummary(serverPlayer, summary);
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
        ClipboardMode currentMode = mode(stack);
        tooltip.add(Component.translatable("item.villagerretaliation.clipboard.mode").withStyle(ChatFormatting.GRAY)
                .append(currentMode.labelComponent()));

        int count = selectedContainers(stack).size();
        if (count > 0) {
            tooltip.add(Component.literal(count + " selected container" + (count == 1 ? "" : "s")).withStyle(ChatFormatting.AQUA));
        }
        WorkAreaDraft draft = selectedWorkArea(stack);
        if (draft.first() != null || draft.second() != null) {
            tooltip.add(Component.literal(draft.complete()
                    ? "Job site draft: " + dimensions(draft.min(), draft.max())
                    : "Job site corners: " + (draft.first() == null ? "0" : draft.second() == null ? "1" : "2") + "/2")
                    .withStyle(ChatFormatting.GOLD));
        }
        if (!TooltipKeyState.hasShiftDown()) {
            tooltip.add(Component.translatable("item.villagerretaliation.tooltip.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        tooltip.add(Component.translatable("item.villagerretaliation.clipboard.controls.change_mode").withStyle(ChatFormatting.DARK_GRAY));
        switch (currentMode) {
            case ASSIGN_STORAGE -> tooltip.add(Component.translatable("item.villagerretaliation.clipboard.controls.assign_storage").withStyle(ChatFormatting.GRAY));
            case ASSIGN_PAYMENT -> tooltip.add(Component.translatable("item.villagerretaliation.clipboard.controls.assign_payment").withStyle(ChatFormatting.GRAY));
            case WORK_AREA -> tooltip.add(Component.translatable("item.villagerretaliation.clipboard.controls.preview_work_area").withStyle(ChatFormatting.GRAY));
            case SET_WORK_AREA -> {
                tooltip.add(Component.translatable("item.villagerretaliation.clipboard.controls.set_work_area").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("item.villagerretaliation.clipboard.controls.work_area_move").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("item.villagerretaliation.clipboard.controls.work_area_resize").withStyle(ChatFormatting.GRAY));
            }
        }
        tooltip.add(Component.translatable(currentMode == ClipboardMode.SET_WORK_AREA
                ? "item.villagerretaliation.clipboard.controls.clear_work_area"
                : "item.villagerretaliation.clipboard.controls.clear").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static ClipboardMode mode(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(TAG)) {
            return ClipboardMode.ASSIGN_STORAGE;
        }
        String id = customData.copyTag().getCompound(TAG).getString(MODE_TAG);
        return ClipboardMode.byId(id);
    }

    public static ClipboardMode cycleMode(ItemStack stack, int delta) {
        ClipboardMode next = mode(stack).cycle(delta);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag clipboardTag = tag.contains(TAG, Tag.TAG_COMPOUND)
                    ? tag.getCompound(TAG)
                    : new CompoundTag();
            clipboardTag.putString(MODE_TAG, next.id);
            tag.put(TAG, clipboardTag);
        });
        return next;
    }

    public static void changeHeldClipboardMode(ServerPlayer player, int delta) {
        changeClipboardMode(player, delta, -1);
    }

    public static void changeClipboardMode(ServerPlayer player, int delta, int menuSlotIndex) {
        Slot slot = menuSlot(player, menuSlotIndex);
        if (menuSlotIndex >= 0 && slot == null) {
            return;
        }
        ItemStack clipboard = slot == null ? heldClipboard(player) : slot.getItem();
        if (clipboard.isEmpty()) {
            return;
        }
        ClipboardMode next = cycleMode(clipboard, delta);
        if (slot != null) {
            slot.setChanged();
            player.containerMenu.broadcastChanges();
        }
        player.displayClientMessage(Component.literal("Clipboard mode: ")
                .append(next.labelComponent()), true);
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

    private static void displayAssignmentSummary(ServerPlayer player, AssignSummary summary) {
        AssignmentSummaryMessage message = AssignedStorageService.assignmentSummaryMessage(summary);
        String text = VillagerDialogueResources.globalMessage(
                player.getServer(),
                player.getRandom(),
                message.key(),
                VillagerLocale.locale(player),
                message.replacements()
        ).orElse(message.key());
        player.displayClientMessage(Component.literal(text), true);
    }

    public static WorkAreaDraft selectedWorkArea(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(TAG)) {
            return WorkAreaDraft.empty();
        }
        CompoundTag clipboardTag = customData.copyTag().getCompound(TAG);
        if (!clipboardTag.contains(WORK_AREA_SELECTION_TAG, Tag.TAG_COMPOUND)) {
            return WorkAreaDraft.empty();
        }
        CompoundTag selectionTag = clipboardTag.getCompound(WORK_AREA_SELECTION_TAG);
        ResourceLocation dimensionId = ResourceLocation.tryParse(selectionTag.getString(DIMENSION_TAG));
        if (dimensionId == null) {
            return WorkAreaDraft.empty();
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        BlockPos first = selectionTag.contains(FIRST_POS_TAG, Tag.TAG_LONG)
                ? BlockPos.of(selectionTag.getLong(FIRST_POS_TAG))
                : null;
        BlockPos second = selectionTag.contains(SECOND_POS_TAG, Tag.TAG_LONG)
                ? BlockPos.of(selectionTag.getLong(SECOND_POS_TAG))
                : null;
        return new WorkAreaDraft(dimension, first, second);
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
        ClipboardMode clipboardMode = mode(stack);
        if (!AssignedStorageService.isValidContainerForPurpose(
                level,
                pos,
                clipboardMode == ClipboardMode.ASSIGN_PAYMENT
                        ? AssignedStorageService.PAYMENT_PURPOSE
                        : AssignedStorageService.GENERAL_PURPOSE)) {
            player.displayClientMessage(Component.literal(clipboardMode == ClipboardMode.ASSIGN_PAYMENT
                    ? "Select a payment box."
                    : "Payment boxes can only be assigned in payment mode."), true);
            return InteractionResult.FAIL;
        }

        SelectionAddResult result = addSelection(stack, level.dimension(), pos);
        Component message = switch (result) {
            case ADDED -> Component.literal(clipboardMode == ClipboardMode.ASSIGN_PAYMENT ? "Payment box selected." : "Container selected.");
            case DUPLICATE -> Component.literal("That container is already selected.");
            case DIFFERENT_DIMENSION -> Component.literal("Clipboard selections must stay in one dimension.");
            case FULL -> Component.literal("Clipboard selection is full.");
        };
        player.displayClientMessage(message, true);
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult handleRightClickBlock(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos pos) {
        return switch (mode(stack)) {
            case ASSIGN_STORAGE, ASSIGN_PAYMENT -> selectContainer(level, player, stack, pos);
            case WORK_AREA -> {
                player.displayClientMessage(Component.literal("Preview Job Site mode: use the clipboard on a hired villager."), true);
                yield InteractionResult.SUCCESS;
            }
            case SET_WORK_AREA -> {
                WorkAreaDraft draft = selectedWorkArea(stack);
                if (draft.first() != null && draft.second() == null && level.dimension().equals(draft.dimension())) {
                    yield selectWorkAreaPosition(level, player, stack, pos, WorkAreaPosition.SECOND);
                }
                yield centerWorkAreaDraft(level, player, stack, pos);
            }
        };
    }

    public static InteractionResult handleLeftClickBlock(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos pos) {
        if (mode(stack) != ClipboardMode.SET_WORK_AREA) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            clearWorkAreaSelection(stack);
            player.displayClientMessage(Component.literal("Work area selection cleared."), true);
            return InteractionResult.SUCCESS;
        }
        return selectWorkAreaPosition(level, player, stack, pos, WorkAreaPosition.FIRST);
    }

    private static InteractionResult selectWorkAreaPosition(
            ServerLevel level,
            ServerPlayer player,
            ItemStack stack,
            BlockPos pos,
            WorkAreaPosition position) {
        WorkAreaDraft draft = selectedWorkArea(stack);
        if (draft.dimension() != null && !draft.dimension().equals(level.dimension())) {
            clearWorkAreaSelection(stack);
            draft = WorkAreaDraft.empty();
        }

        BlockPos first = position == WorkAreaPosition.FIRST ? pos.immutable() : draft.first();
        BlockPos second = position == WorkAreaPosition.SECOND ? pos.immutable() : draft.second();

        saveWorkAreaSelection(stack, level.dimension(), first, second);
        if (first != null && second != null) {
            sendSelectedWorkAreaOutline(player, level, first, second);
        }
        player.displayClientMessage(Component.literal(workAreaPositionMessage(position, first, second)), true);
        return InteractionResult.SUCCESS;
    }

    private static String workAreaPositionMessage(WorkAreaPosition position, BlockPos first, BlockPos second) {
        if (position == WorkAreaPosition.FIRST) {
            return second == null
                    ? "Corner 1 set. Right-click the opposite corner, then use the clipboard on the hired villager."
                    : "Corner 1 set. Use the clipboard on the hired villager to apply this custom work box.";
        }
        return first == null
                ? "Corner 2 set. Left-click the opposite corner, then use the clipboard on the hired villager."
                : "Corner 2 set. Use the clipboard on the hired villager to apply this custom work box.";
    }

    private static InteractionResult centerWorkAreaDraft(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos center) {
        WorkAreaDraft draft = selectedWorkArea(stack);
        BlockPos first;
        BlockPos second;
        if (draft.complete() && level.dimension().equals(draft.dimension())) {
            BlockPos min = draft.min();
            BlockPos max = draft.max();
            BlockPos oldCenter = draft.center();
            first = new BlockPos(
                    center.getX() - (oldCenter.getX() - min.getX()),
                    center.getY() - (oldCenter.getY() - min.getY()),
                    center.getZ() - (oldCenter.getZ() - min.getZ()));
            second = new BlockPos(
                    center.getX() + (max.getX() - oldCenter.getX()),
                    center.getY() + (max.getY() - oldCenter.getY()),
                    center.getZ() + (max.getZ() - oldCenter.getZ()));
        } else {
            HiredWorkArea area = HiredWorkArea.fromCenter(center, DEFAULT_WORK_AREA_HORIZONTAL_RADIUS, DEFAULT_WORK_AREA_VERTICAL_RADIUS, true);
            first = area.min();
            second = area.max();
        }
        saveWorkAreaSelection(stack, level.dimension(), first, second);
        sendSelectedWorkAreaOutline(player, level, first, second);
        player.displayClientMessage(Component.literal("Job site draft: " + dimensions(first, second)
                + " centered at " + positionDescription(center) + "."), true);
        return InteractionResult.SUCCESS;
    }

    public static void handleWorkAreaDraftAction(ServerPlayer player, ClipboardWorkAreaDraftPayload.Action action, int steps) {
        if (action == null) {
            return;
        }
        ItemStack stack = heldClipboard(player);
        if (stack.isEmpty() || mode(stack) != ClipboardMode.SET_WORK_AREA) {
            return;
        }
        ServerLevel level = player.serverLevel();
        WorkAreaDraft draft = selectedWorkArea(stack);
        if (!draft.complete() || !level.dimension().equals(draft.dimension())) {
            centerWorkAreaDraft(level, player, stack, player.blockPosition());
            return;
        }

        BlockPos min = draft.min();
        BlockPos max = draft.max();
        BlockPos center = draft.center();
        int safeSteps = Math.max(1, Math.min(8, steps));
        switch (action) {
            case MOVE_NORTH -> {
                min = min.offset(0, 0, -safeSteps);
                max = max.offset(0, 0, -safeSteps);
            }
            case MOVE_EAST -> {
                min = min.offset(safeSteps, 0, 0);
                max = max.offset(safeSteps, 0, 0);
            }
            case MOVE_SOUTH -> {
                min = min.offset(0, 0, safeSteps);
                max = max.offset(0, 0, safeSteps);
            }
            case MOVE_WEST -> {
                min = min.offset(-safeSteps, 0, 0);
                max = max.offset(-safeSteps, 0, 0);
            }
            case MOVE_UP -> {
                min = min.offset(0, safeSteps, 0);
                max = max.offset(0, safeSteps, 0);
            }
            case MOVE_DOWN -> {
                min = min.offset(0, -safeSteps, 0);
                max = max.offset(0, -safeSteps, 0);
            }
            case EXPAND_HORIZONTAL -> {
                min = new BlockPos(
                        Math.max(min.getX() - safeSteps, center.getX() - MAX_WORK_AREA_DRAFT_RADIUS),
                        min.getY(),
                        Math.max(min.getZ() - safeSteps, center.getZ() - MAX_WORK_AREA_DRAFT_RADIUS));
                max = new BlockPos(
                        Math.min(max.getX() + safeSteps, center.getX() + MAX_WORK_AREA_DRAFT_RADIUS),
                        max.getY(),
                        Math.min(max.getZ() + safeSteps, center.getZ() + MAX_WORK_AREA_DRAFT_RADIUS));
            }
            case CONTRACT_HORIZONTAL -> {
                if (max.getX() - min.getX() > 2) {
                    min = new BlockPos(Math.min(min.getX() + safeSteps, center.getX() - 1), min.getY(), min.getZ());
                    max = new BlockPos(Math.max(max.getX() - safeSteps, center.getX() + 1), max.getY(), max.getZ());
                }
                if (max.getZ() - min.getZ() > 2) {
                    min = new BlockPos(min.getX(), min.getY(), Math.min(min.getZ() + safeSteps, center.getZ() - 1));
                    max = new BlockPos(max.getX(), max.getY(), Math.max(max.getZ() - safeSteps, center.getZ() + 1));
                }
            }
            case EXPAND_VERTICAL -> {
                min = new BlockPos(min.getX(), Math.max(min.getY() - safeSteps, center.getY() - MAX_WORK_AREA_DRAFT_RADIUS), min.getZ());
                max = new BlockPos(max.getX(), Math.min(max.getY() + safeSteps, center.getY() + MAX_WORK_AREA_DRAFT_RADIUS), max.getZ());
            }
            case CONTRACT_VERTICAL -> {
                if (max.getY() - min.getY() > 0) {
                    min = new BlockPos(min.getX(), Math.min(min.getY() + safeSteps, center.getY()), min.getZ());
                    max = new BlockPos(max.getX(), Math.max(max.getY() - safeSteps, center.getY()), max.getZ());
                }
            }
        }

        saveWorkAreaSelection(stack, level.dimension(), min, max);
        sendSelectedWorkAreaOutline(player, level, min, max);
        WorkAreaDraft updated = selectedWorkArea(stack);
        player.displayClientMessage(Component.literal("Job site draft: " + dimensions(updated.min(), updated.max())
                + " centered at " + positionDescription(updated.center()) + "."), true);
    }

    public static void sendAssignedStorageOutlines(ServerPlayer player, List<AssignedContainerRecord> records) {
        List<ClipboardAssignedStorageSyncPayload.Entry> entries = records.stream()
                .map(record -> new ClipboardAssignedStorageSyncPayload.Entry(
                        record.dimension().location(),
                        record.pos(),
                        AssignedStorageService.PAYMENT_PURPOSE.equals(record.purpose())))
                .toList();
        PacketDistributor.sendToPlayer(player, new ClipboardAssignedStorageSyncPayload(entries, 200));
    }

    public static void sendWorkAreaOutline(ServerPlayer player, ServerLevel level, Villager villager) {
        HiredWorkArea area = HiredVillagerWorkService.workArea(level, villager);
        if (!area.usable()) {
            player.displayClientMessage(Component.literal("No work area assigned."), true);
            return;
        }
        PacketDistributor.sendToPlayer(player, ClipboardWorkAreaSyncPayload.assigned(level.dimension().location(), area.min(), area.max(), area.center(), 200));
        List<AssignedContainerRecord> assigned = AssignedStorageService.assignedStorage(level, villager);
        if (!assigned.isEmpty()) {
            sendAssignedStorageOutlines(player, assigned);
        }
        player.displayClientMessage(Component.literal("Showing job site: " + area.rangeDescription() + "."), true);
    }

    private static void sendSelectedWorkAreaOutline(ServerPlayer player, ServerLevel level, BlockPos first, BlockPos second) {
        PacketDistributor.sendToPlayer(player, ClipboardWorkAreaSyncPayload.selection(level.dimension().location(), first, second, 120));
    }

    private static void assignSelectedWorkArea(ServerPlayer player, ServerLevel level, Villager villager, ItemStack stack) {
        if (!HiredVillagerWorkService.canManageWork(level, villager, player)) {
            player.displayClientMessage(Component.literal("Only the hiring player can assign this work area."), true);
            return;
        }

        WorkAreaDraft draft = selectedWorkArea(stack);
        if (draft.first() == null || draft.second() == null) {
            player.displayClientMessage(Component.literal("Select two work area corners first."), true);
            return;
        }
        if (!level.dimension().equals(draft.dimension())) {
            player.displayClientMessage(Component.literal("Work area corners must be in this dimension."), true);
            return;
        }

        if (HiredVillagerWorkService.setWorkArea(player, level, villager, draft.first(), draft.second())) {
            clearWorkAreaSelection(stack);
            sendWorkAreaOutline(player, level, villager);
        }
    }

    private static ItemStack heldClipboard(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (VillagerRetaliationItems.isClipboard(mainHand)) {
            return mainHand;
        }
        ItemStack offhand = player.getOffhandItem();
        return VillagerRetaliationItems.isClipboard(offhand) ? offhand : ItemStack.EMPTY;
    }

    private static Slot menuSlot(ServerPlayer player, int menuSlotIndex) {
        if (menuSlotIndex < 0 || menuSlotIndex >= player.containerMenu.slots.size()) {
            return null;
        }
        Slot slot = player.containerMenu.slots.get(menuSlotIndex);
        return VillagerRetaliationItems.isClipboard(slot.getItem()) ? slot : null;
    }

    private static String dimensions(BlockPos first, BlockPos second) {
        BlockPos min = HiredWorkArea.minPos(first, second);
        BlockPos max = HiredWorkArea.maxPos(first, second);
        return (max.getX() - min.getX() + 1)
                + "x" + (max.getY() - min.getY() + 1)
                + "x" + (max.getZ() - min.getZ() + 1);
    }

    private static String positionDescription(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
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
        CompoundTag clipboardTag = tag.getCompound(TAG);
        clipboardTag.remove(SELECTED_TAG);
        clipboardTag.remove(WORK_AREA_SELECTION_TAG);
        if (clipboardTag.isEmpty()) {
            tag.remove(TAG);
        } else {
            tag.put(TAG, clipboardTag);
        }
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    private static void saveSelection(ItemStack stack, List<StoragePosition> selected) {
        ListTag selectedTag = new ListTag();
        for (StoragePosition position : selected) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString(DIMENSION_TAG, position.dimension().location().toString());
            entryTag.putLong(POS_TAG, position.pos().asLong());
            selectedTag.add(entryTag);
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag clipboardTag = tag.contains(TAG, Tag.TAG_COMPOUND)
                    ? tag.getCompound(TAG)
                    : new CompoundTag();
            clipboardTag.put(SELECTED_TAG, selectedTag);
            tag.put(TAG, clipboardTag);
        });
    }

    private static void saveWorkAreaSelection(
            ItemStack stack,
            ResourceKey<Level> dimension,
            BlockPos first,
            BlockPos second) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag clipboardTag = tag.contains(TAG, Tag.TAG_COMPOUND)
                    ? tag.getCompound(TAG)
                    : new CompoundTag();
            CompoundTag selectionTag = new CompoundTag();
            selectionTag.putString(DIMENSION_TAG, dimension.location().toString());
            if (first != null) {
                selectionTag.putLong(FIRST_POS_TAG, first.asLong());
            }
            if (second != null) {
                selectionTag.putLong(SECOND_POS_TAG, second.asLong());
            }
            clipboardTag.put(WORK_AREA_SELECTION_TAG, selectionTag);
            tag.put(TAG, clipboardTag);
        });
    }

    private static void clearWorkAreaSelection(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(TAG)) {
            return;
        }
        CompoundTag tag = customData.copyTag();
        CompoundTag clipboardTag = tag.getCompound(TAG);
        clipboardTag.remove(WORK_AREA_SELECTION_TAG);
        if (clipboardTag.isEmpty()) {
            tag.remove(TAG);
        } else {
            tag.put(TAG, clipboardTag);
        }
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    private enum SelectionAddResult {
        ADDED,
        DUPLICATE,
        DIFFERENT_DIMENSION,
        FULL
    }

    private enum WorkAreaPosition {
        FIRST,
        SECOND
    }

    public enum ClipboardMode {
        ASSIGN_STORAGE("assign_storage", "Assign Output Storage"),
        ASSIGN_PAYMENT("assign_payment", "Assign Payment Box"),
        WORK_AREA("work_area", "Preview Job Site"),
        SET_WORK_AREA("set_work_area", "Edit Job Site");

        private static final ClipboardMode[] VALUES = values();
        private final String id;
        private final String label;

        ClipboardMode(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String label() {
            return this.label;
        }

        public Component labelComponent() {
            return Component.literal(this.label).withStyle(color());
        }

        public ChatFormatting color() {
            return switch (this) {
                case ASSIGN_PAYMENT -> ChatFormatting.GREEN;
                case ASSIGN_STORAGE -> ChatFormatting.BLUE;
                case WORK_AREA -> ChatFormatting.YELLOW;
                case SET_WORK_AREA -> ChatFormatting.GOLD;
            };
        }

        private ClipboardMode cycle(int delta) {
            if (delta == 0) {
                return this;
            }
            int index = Math.floorMod(this.ordinal() + Integer.compare(delta, 0), VALUES.length);
            return VALUES[index];
        }

        private static ClipboardMode byId(String id) {
            for (ClipboardMode mode : VALUES) {
                if (mode.id.equals(id)) {
                    return mode;
                }
            }
            return ASSIGN_STORAGE;
        }
    }

    public record WorkAreaDraft(ResourceKey<Level> dimension, BlockPos first, BlockPos second) {
        private static WorkAreaDraft empty() {
            return new WorkAreaDraft(null, null, null);
        }

        public boolean complete() {
            return this.dimension != null && this.first != null && this.second != null;
        }

        public BlockPos min() {
            return complete() ? HiredWorkArea.minPos(this.first, this.second) : null;
        }

        public BlockPos max() {
            return complete() ? HiredWorkArea.maxPos(this.first, this.second) : null;
        }

        public BlockPos center() {
            return complete() ? HiredWorkArea.centerPos(min(), max()) : null;
        }
    }
}

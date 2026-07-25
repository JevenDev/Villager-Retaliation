package com.jvn.villagerretaliation.item;

import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.inventory.AssignedStorageService.AssignmentSummaryMessage;
import com.jvn.villagerretaliation.inventory.AssignedStorageService.AssignSummary;
import com.jvn.villagerretaliation.inventory.AssignedStorageService.StoragePosition;
import com.jvn.villagerretaliation.interaction.ClipboardWorkforceService;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredRoute;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.network.ClipboardAssignedStorageSyncPayload;
import com.jvn.villagerretaliation.network.ClipboardRouteSyncPayload;
import com.jvn.villagerretaliation.network.ClipboardWorkAreaDraftPayload;
import com.jvn.villagerretaliation.network.ClipboardWorkAreaSyncPayload;
import com.jvn.villagerretaliation.util.VillagerLocale;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final String ROUTE_SELECTION_TAG = "RouteSelection";
    private static final String DIMENSION_TAG = "Dimension";
    private static final String POS_TAG = "Pos";
    private static final String PURPOSE_TAG = "Purpose";
    private static final String FIRST_POS_TAG = "FirstPos";
    private static final String SECOND_POS_TAG = "SecondPos";
    private static final String NODES_TAG = "Nodes";
    private static final String LOOP_TAG = "Loop";
    private static final String MODE_TAG = "Mode";
    private static final String LAST_STORAGE_MODE_TAG = "LastStorageMode";
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
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && player.isShiftKeyDown()
                && mode(stack) != ClipboardMode.NONE) {
            clearSelection(serverPlayer, stack);
            serverPlayer.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.selection_cleared"), true);
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
        if (clipboardMode == ClipboardMode.NONE) {
            return InteractionResult.SUCCESS;
        }
        if (clipboardMode == ClipboardMode.WORK_AREA) {
            if (!HiredVillagerWorkService.canManageWork(level, villager, serverPlayer)) {
                serverPlayer.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.inspect_owner_only"), true);
                return InteractionResult.SUCCESS;
            }
            sendWorkAreaOutline(serverPlayer, level, villager);
            return InteractionResult.SUCCESS;
        }
        if (clipboardMode == ClipboardMode.SET_WORK_AREA) {
            assignSelectedWorkArea(serverPlayer, level, villager, stack);
            return InteractionResult.SUCCESS;
        }
        if (clipboardMode == ClipboardMode.ROUTE) {
            handleRouteVillager(serverPlayer, level, villager, stack);
            return InteractionResult.SUCCESS;
        }

        List<SelectedStoragePosition> selected = selectedStoragePositions(stack, clipboardMode.assignmentPurpose());
        if (!selected.isEmpty()) {
            assignSelectedStorage(serverPlayer, level, villager, stack, selected)
                    .ifPresent(message -> displayAssignmentSummary(serverPlayer, message));
            return InteractionResult.SUCCESS;
        }

        if (clipboardMode == ClipboardMode.ASSIGN_PAYMENT) {
            if (!HiredVillagerContractService.isHiredBy(level, villager, serverPlayer)) {
                VillagerInteractionService.sendVillagerNotice(serverPlayer, villager, "interaction.payment_storage.requires_hire");
                return InteractionResult.SUCCESS;
            }
            List<AssignedContainerRecord> assigned = AssignedStorageService.assignedPaymentStorage(level, villager);
            if (!assigned.isEmpty()) {
                sendAssignedStorageOutlines(serverPlayer, assigned);
                serverPlayer.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.payment_containers", assigned.size()), true);
                return InteractionResult.SUCCESS;
            }
            serverPlayer.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.select_payment_container"), true);
            return InteractionResult.SUCCESS;
        }

        if (AssignedStorageService.hasAssignedStorage(level, villager)) {
            if (serverPlayer.isShiftKeyDown()) {
                if (!VillagerInteractionService.canManageAssignedStorage(level, villager, serverPlayer)) {
                    VillagerInteractionService.sendVillagerNotice(serverPlayer, villager, "interaction.storage.remove_requires_access");
                    return InteractionResult.SUCCESS;
                }
                int removed = AssignedStorageService.removeAssignedStorage(level, villager);
                String key = removed == 1 ? "removed_container" : "removed_containers";
                serverPlayer.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message." + key, removed), true);
            } else {
                if (!VillagerInteractionService.canManageAssignedStorage(level, villager, serverPlayer)) {
                    VillagerInteractionService.sendVillagerNotice(serverPlayer, villager, "interaction.storage.inspect_requires_access");
                    return InteractionResult.SUCCESS;
                }
                List<AssignedContainerRecord> assigned = AssignedStorageService.assignedStorage(level, villager);
                sendAssignedStorageOutlines(serverPlayer, assigned);
                int count = assigned.size();
                serverPlayer.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.assigned_containers", count), true);
            }
            return InteractionResult.SUCCESS;
        }

        serverPlayer.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.select_containers"), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ClipboardMode currentMode = mode(stack);
        tooltip.add(Component.translatable("item.villagerretaliation.clipboard.mode").withStyle(ChatFormatting.GRAY)
                .append(currentMode.labelComponent()));

        int count = currentMode == ClipboardMode.ROUTE ? 0 : selectedContainers(stack).size();
        if (count > 0) {
            String key = count == 1 ? "selected_container" : "selected_containers";
            tooltip.add(Component.translatable("item.villagerretaliation.clipboard." + key, count).withStyle(ChatFormatting.AQUA));
        }
        WorkAreaDraft draft = selectedWorkArea(stack);
        if (draft.first() != null || draft.second() != null) {
            Component draftText = draft.complete()
                    ? Component.translatable("item.villagerretaliation.clipboard.job_site_draft", dimensions(draft.min(), draft.max()))
                    : Component.translatable("item.villagerretaliation.clipboard.job_site_corners",
                            draft.first() == null ? 0 : draft.second() == null ? 1 : 2);
            tooltip.add(draftText.copy().withStyle(ChatFormatting.GOLD));
        }
        RouteDraft routeDraft = selectedRoute(stack);
        if (!routeDraft.isEmpty()) {
            tooltip.add(Component.translatable("item.villagerretaliation.clipboard.route_draft", routeDescription(routeDraft.route()))
                    .withStyle(ChatFormatting.AQUA));
        }
        if (!TooltipKeyState.hasShiftDown()) {
            tooltip.add(Component.translatable("item.villagerretaliation.tooltip.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        tooltip.add(Component.translatable("item.villagerretaliation.clipboard.controls.change_mode").withStyle(ChatFormatting.GRAY));
        switch (currentMode) {
            case NONE -> tooltip.add(Component.translatable("item.villagerretaliation.clipboard.controls.none").withStyle(ChatFormatting.GRAY));
            case ASSIGN_STORAGE, ASSIGN_TOOL_STORAGE, ASSIGN_INPUT_STORAGE, ASSIGN_OUTPUT_STORAGE -> {
                tooltip.add(Component.translatable("item.villagerretaliation.clipboard.controls.assign_storage").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("item.villagerretaliation.clipboard.controls.assign_storage_variant").withStyle(ChatFormatting.GRAY));
            }
            case ASSIGN_PAYMENT -> tooltip.add(Component.translatable("item.villagerretaliation.clipboard.controls.assign_payment").withStyle(ChatFormatting.GRAY));
            case WORK_AREA -> tooltip.add(Component.translatable("item.villagerretaliation.clipboard.controls.preview_work_area").withStyle(ChatFormatting.GRAY));
            case SET_WORK_AREA -> {
                tooltip.add(Component.translatable("item.villagerretaliation.clipboard.controls.set_work_area").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("item.villagerretaliation.clipboard.controls.work_area_move").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("item.villagerretaliation.clipboard.controls.work_area_resize").withStyle(ChatFormatting.GRAY));
            }
            case ROUTE -> tooltip.add(Component.translatable("item.villagerretaliation.clipboard.controls.route").withStyle(ChatFormatting.GRAY));
        }
        if (currentMode == ClipboardMode.NONE) {
            return;
        }
        String clearKey = currentMode == ClipboardMode.SET_WORK_AREA
                ? "item.villagerretaliation.clipboard.controls.clear_work_area"
                : currentMode == ClipboardMode.ROUTE
                ? "item.villagerretaliation.clipboard.controls.clear_route"
                : "item.villagerretaliation.clipboard.controls.clear";
        tooltip.add(Component.translatable(clearKey).withStyle(ChatFormatting.GRAY));
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
        return cycleMode(stack, delta, false);
    }

    public static ClipboardMode cycleMode(ItemStack stack, int delta, boolean storageVariantOnly) {
        ClipboardMode current = mode(stack);
        ClipboardMode rememberedStorageMode = lastStorageMode(stack);
        ClipboardMode next = storageVariantOnly
                ? current.cycleStorageVariant(delta)
                : current.cycleInventoryMode(delta, rememberedStorageMode);
        ClipboardMode nextLastStorageMode = next.isStorageAssignmentMode() ? next : rememberedStorageMode;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag clipboardTag = tag.contains(TAG, Tag.TAG_COMPOUND)
                    ? tag.getCompound(TAG)
                    : new CompoundTag();
            clipboardTag.putString(MODE_TAG, next.id);
            clipboardTag.putString(LAST_STORAGE_MODE_TAG, nextLastStorageMode.id);
            tag.put(TAG, clipboardTag);
        });
        return next;
    }

    private static ClipboardMode lastStorageMode(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(TAG)) {
            return ClipboardMode.ASSIGN_STORAGE;
        }
        CompoundTag clipboardTag = customData.copyTag().getCompound(TAG);
        if (clipboardTag.contains(LAST_STORAGE_MODE_TAG, Tag.TAG_STRING)) {
            ClipboardMode remembered = ClipboardMode.byId(clipboardTag.getString(LAST_STORAGE_MODE_TAG));
            if (remembered.isStorageAssignmentMode()) {
                return remembered;
            }
        }
        ClipboardMode current = ClipboardMode.byId(clipboardTag.getString(MODE_TAG));
        return current.isStorageAssignmentMode() ? current : ClipboardMode.ASSIGN_STORAGE;
    }

    public static void changeHeldClipboardMode(ServerPlayer player, int delta) {
        changeClipboardMode(player, delta, -1, false);
    }

    public static void changeClipboardMode(ServerPlayer player, int delta, int menuSlotIndex) {
        changeClipboardMode(player, delta, menuSlotIndex, false);
    }

    public static void changeClipboardMode(ServerPlayer player, int delta, int menuSlotIndex, boolean storageVariantOnly) {
        Slot slot = menuSlot(player, menuSlotIndex);
        if (menuSlotIndex >= 0 && slot == null) {
            return;
        }
        ItemStack clipboard = slot == null ? heldClipboard(player) : slot.getItem();
        if (clipboard.isEmpty()) {
            return;
        }
        ClipboardMode next = cycleMode(clipboard, delta, storageVariantOnly);
        if (slot != null) {
            slot.setChanged();
        }
        syncClipboardStack(player);
        player.displayClientMessage(Component.translatable(
                "villagerretaliation.clipboard.message.mode", next.labelComponent()), true);
    }

    public static List<StoragePosition> selectedContainers(ItemStack stack) {
        return selectedStoragePositions(stack, mode(stack).assignmentPurpose()).stream()
                .map(SelectedStoragePosition::position)
                .toList();
    }

    public static List<SelectedStoragePosition> selectedStoragePositions(ItemStack stack, String fallbackPurpose) {
        List<SelectedStoragePosition> selected = new ArrayList<>();
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(TAG)) {
            return selected;
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
            StoragePosition position = new StoragePosition(
                    ResourceKey.create(Registries.DIMENSION, dimensionId),
                    BlockPos.of(entryTag.getLong(POS_TAG))
            );
            String purpose = entryTag.contains(PURPOSE_TAG, Tag.TAG_STRING)
                    ? entryTag.getString(PURPOSE_TAG)
                    : fallbackPurpose;
            selected.add(new SelectedStoragePosition(position, AssignedStorageService.normalizePurpose(purpose)));
        }
        return selected;
    }

    private static void displayAssignmentSummary(ServerPlayer player, AssignSummary summary) {
        displayAssignmentSummary(player, AssignedStorageService.assignmentSummaryMessage(summary));
    }

    private static void displayAssignmentSummary(ServerPlayer player, AssignmentSummaryMessage message) {
        String text = VillagerDialogueResources.globalMessage(
                player.getServer(),
                player.getRandom(),
                message.key(),
                VillagerLocale.locale(player),
                message.replacements()
        ).orElse(message.key());
        player.displayClientMessage(Component.literal(text), true);
    }

    public static Optional<AssignmentSummaryMessage> assignSelectedStorage(
            ServerPlayer player,
            ServerLevel level,
            Villager villager,
            ItemStack stack,
            List<SelectedStoragePosition> selected) {
        if (selected.stream().anyMatch(SelectedStoragePosition::paymentPurpose)
                && !HiredVillagerContractService.isHiredBy(level, villager, player)) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.payment_storage.requires_hire");
            return Optional.empty();
        }
        if (selected.stream().anyMatch(position -> !position.paymentPurpose())
                && !VillagerInteractionService.canManageAssignedStorage(level, villager, player)) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.storage.assign_requires_access");
            return Optional.empty();
        }

        Map<String, List<StoragePosition>> byPurpose = new LinkedHashMap<>();
        for (SelectedStoragePosition selectedPosition : selected) {
            byPurpose.computeIfAbsent(selectedPosition.purpose(), ignored -> new ArrayList<>())
                    .add(selectedPosition.position());
        }

        int assigned = 0;
        int alreadyAssigned = 0;
        int invalid = 0;
        Map<String, Integer> assignedByPurpose = new LinkedHashMap<>();
        for (Map.Entry<String, List<StoragePosition>> entry : byPurpose.entrySet()) {
            AssignSummary summary = AssignedStorageService.assign(player, villager, entry.getValue(), entry.getKey());
            assigned += summary.assigned();
            alreadyAssigned += summary.alreadyAssigned();
            invalid += summary.invalid();
            if (summary.assigned() > 0) {
                assignedByPurpose.merge(entry.getKey(), summary.assigned(), Integer::sum);
            }
        }

        if (assigned > 0) {
            clearSelection(stack);
            syncClipboardStack(player);
        }
        return Optional.of(assignmentSummaryMessage(assigned, alreadyAssigned, invalid, assignedByPurpose));
    }

    private static AssignmentSummaryMessage assignmentSummaryMessage(
            int assigned,
            int alreadyAssigned,
            int invalid,
            Map<String, Integer> assignedByPurpose) {
        if (assigned > 0) {
            return new AssignmentSummaryMessage(
                    "interaction.storage.assign_result.assigned_types",
                    Map.of(
                            "count", Integer.toString(assigned),
                            "plural", assigned == 1 ? "" : "s",
                            "types", purposeSummary(assignedByPurpose)
                    )
            );
        }
        return AssignedStorageService.assignmentSummaryMessage(new AssignSummary(assigned, alreadyAssigned, invalid));
    }

    private static String purposeSummary(Map<String, Integer> counts) {
        List<String> parts = new ArrayList<>();
        List<String> knownPurposes = List.of(
                AssignedStorageService.GENERAL_PURPOSE,
                AssignedStorageService.TOOL_PURPOSE,
                AssignedStorageService.INPUT_PURPOSE,
                AssignedStorageService.OUTPUT_PURPOSE,
                AssignedStorageService.PAYMENT_PURPOSE);
        for (String purpose : knownPurposes) {
            int count = counts.getOrDefault(purpose, 0);
            if (count > 0) {
                parts.add(count + " " + purposeLabel(purpose));
            }
        }
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > 0 && !knownPurposes.contains(entry.getKey())) {
                parts.add(entry.getValue() + " " + purposeLabel(entry.getKey()));
            }
        }
        return String.join(", ", parts);
    }

    private static String purposeLabel(String purpose) {
        return switch (AssignedStorageService.normalizePurpose(purpose)) {
            case AssignedStorageService.TOOL_PURPOSE -> "tool";
            case AssignedStorageService.INPUT_PURPOSE -> "input";
            case AssignedStorageService.OUTPUT_PURPOSE -> "output";
            case AssignedStorageService.PAYMENT_PURPOSE -> "payment";
            default -> "global";
        };
    }

    private static Component purposeLabelComponent(String purpose) {
        String normalized = AssignedStorageService.normalizePurpose(purpose);
        return Component.translatable("villagerretaliation.clipboard.purpose." + normalized);
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

    public static RouteDraft selectedRoute(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(TAG)) {
            return RouteDraft.empty();
        }
        CompoundTag clipboardTag = customData.copyTag().getCompound(TAG);
        if (!clipboardTag.contains(ROUTE_SELECTION_TAG, Tag.TAG_COMPOUND)) {
            return RouteDraft.empty();
        }
        CompoundTag selectionTag = clipboardTag.getCompound(ROUTE_SELECTION_TAG);
        ResourceLocation dimensionId = ResourceLocation.tryParse(selectionTag.getString(DIMENSION_TAG));
        if (dimensionId == null) {
            return RouteDraft.empty();
        }
        List<BlockPos> nodes = new ArrayList<>();
        ListTag nodeTags = selectionTag.getList(NODES_TAG, Tag.TAG_LONG);
        for (Tag rawNode : nodeTags) {
            if (rawNode instanceof net.minecraft.nbt.LongTag nodeTag && nodes.size() < HiredRoute.MAX_NODES) {
                nodes.add(BlockPos.of(nodeTag.getAsLong()));
            }
        }
        if (nodes.isEmpty()) {
            return RouteDraft.empty();
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        return new RouteDraft(dimension, new HiredRoute(nodes, selectionTag.getBoolean(LOOP_TAG)).validatedChain());
    }

    public static InteractionResult selectContainer(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos pos) {
        if (player.isShiftKeyDown()) {
            clearSelection(player, stack);
            player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.selection_cleared"), true);
            return InteractionResult.SUCCESS;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof net.minecraft.world.Container)) {
            player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.select_valid_container"), true);
            return InteractionResult.FAIL;
        }
        ClipboardMode clipboardMode = mode(stack);
        if (!AssignedStorageService.isValidContainerForPurpose(
                level,
                pos,
                clipboardMode.assignmentPurpose())) {
            String key = clipboardMode == ClipboardMode.ASSIGN_PAYMENT ? "select_payment_box" : "payment_box_wrong_mode";
            player.displayClientMessage(Component.translatable(
                    "villagerretaliation.clipboard.message." + key), true);
            return InteractionResult.FAIL;
        }

        SelectionAddResult result = addSelection(stack, level.dimension(), pos, clipboardMode.assignmentPurpose());
        Component message = switch (result) {
            case ADDED -> clipboardMode == ClipboardMode.ASSIGN_PAYMENT
                    ? Component.translatable("villagerretaliation.clipboard.message.payment_box_selected")
                    : Component.translatable("villagerretaliation.clipboard.message.container_selected",
                            purposeLabelComponent(clipboardMode.assignmentPurpose()));
            case DUPLICATE -> Component.translatable("villagerretaliation.clipboard.message.container_duplicate");
            case DIFFERENT_DIMENSION -> Component.translatable("villagerretaliation.clipboard.message.selection_wrong_dimension");
            case FULL -> Component.translatable("villagerretaliation.clipboard.message.selection_full");
        };
        if (result == SelectionAddResult.ADDED) {
            syncClipboardStack(player);
        }
        player.displayClientMessage(message, true);
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult handleRightClickBlock(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos pos) {
        return switch (mode(stack)) {
            case NONE -> {
                ClipboardWorkforceService.openClipboard(player);
                yield InteractionResult.SUCCESS;
            }
            case ASSIGN_STORAGE, ASSIGN_INPUT_STORAGE, ASSIGN_OUTPUT_STORAGE, ASSIGN_TOOL_STORAGE, ASSIGN_PAYMENT -> selectContainer(level, player, stack, pos);
            case WORK_AREA -> {
                player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.preview_job_site_mode"), true);
                yield InteractionResult.SUCCESS;
            }
            case SET_WORK_AREA -> {
                WorkAreaDraft draft = selectedWorkArea(stack);
                if (draft.first() != null && draft.second() == null && level.dimension().equals(draft.dimension())) {
                    yield selectWorkAreaPosition(level, player, stack, pos, WorkAreaPosition.SECOND);
                }
                yield centerWorkAreaDraft(level, player, stack, pos);
            }
            case ROUTE -> editRoute(level, player, stack, pos);
        };
    }

    public static InteractionResult handleLeftClickBlock(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos pos) {
        if (mode(stack) != ClipboardMode.SET_WORK_AREA) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            clearWorkAreaSelection(stack);
            syncClipboardStack(player);
            clearWorkAreaOutline(player);
            player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.work_area_cleared"), true);
            return InteractionResult.SUCCESS;
        }
        return selectWorkAreaPosition(level, player, stack, pos, WorkAreaPosition.FIRST);
    }

    private static InteractionResult editRoute(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos pos) {
        if (player.isShiftKeyDown()) {
            return removeRouteNode(level, player, stack, pos);
        }

        RouteDraft draft = selectedRoute(stack);
        if (!draft.isEmpty() && !level.dimension().equals(draft.dimension())) {
            player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.route_single_dimension"), true);
            return InteractionResult.FAIL;
        }

        HiredRoute route = draft.isEmpty() ? HiredRoute.empty() : draft.route();
        List<BlockPos> nodes = new ArrayList<>(route.nodes());
        BlockPos node = pos.immutable();
        if (nodes.isEmpty()) {
            HiredRoute updated = new HiredRoute(List.of(node), false);
            saveRouteSelection(stack, level.dimension(), updated);
            syncClipboardStack(player);
            sendSelectedRouteOutline(player, level, updated);
            player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.route_node_added", 1, HiredRoute.MAX_NODES), true);
            return InteractionResult.SUCCESS;
        }

        if (node.equals(nodes.getFirst()) && nodes.size() >= 2) {
            if (route.loop()) {
                HiredRoute updated = new HiredRoute(nodes, false);
                saveRouteSelection(stack, level.dimension(), updated);
                syncClipboardStack(player);
                sendSelectedRouteOutline(player, level, updated);
                player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.route_loop_opened"), true);
                return InteractionResult.SUCCESS;
            }
            if (!HiredRoute.canConnect(nodes.getLast(), nodes.getFirst())) {
                player.displayClientMessage(Component.translatable(
                        "villagerretaliation.clipboard.message.route_loop_too_far", HiredRoute.MAX_NODE_DISTANCE), true);
                return InteractionResult.FAIL;
            }
            HiredRoute updated = new HiredRoute(nodes, true);
            saveRouteSelection(stack, level.dimension(), updated);
            syncClipboardStack(player);
            sendSelectedRouteOutline(player, level, updated);
            player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.route_loop_closed"), true);
            return InteractionResult.SUCCESS;
        }

        if (route.contains(node)) {
            player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.route_node_duplicate"), true);
            return InteractionResult.SUCCESS;
        }
        if (route.loop()) {
            player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.route_open_before_adding"), true);
            return InteractionResult.FAIL;
        }
        if (nodes.size() >= HiredRoute.MAX_NODES) {
            player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.route_full", HiredRoute.MAX_NODES), true);
            return InteractionResult.FAIL;
        }
        if (!HiredRoute.canConnect(nodes.getLast(), node)) {
            player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.route_nodes_too_far", HiredRoute.MAX_NODE_DISTANCE), true);
            return InteractionResult.FAIL;
        }

        nodes.add(node);
        HiredRoute updated = new HiredRoute(nodes, false);
        saveRouteSelection(stack, level.dimension(), updated);
        syncClipboardStack(player);
        sendSelectedRouteOutline(player, level, updated);
        player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.route_node_added", updated.nodes().size(), HiredRoute.MAX_NODES), true);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult removeRouteNode(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos pos) {
        RouteDraft draft = selectedRoute(stack);
        if (draft.isEmpty() || !level.dimension().equals(draft.dimension())) {
            player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.route_node_missing"), true);
            return InteractionResult.SUCCESS;
        }
        int removedIndex = draft.route().indexOf(pos);
        if (removedIndex < 0) {
            player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.route_node_missing"), true);
            return InteractionResult.SUCCESS;
        }

        List<BlockPos> nodes = new ArrayList<>(draft.route().nodes());
        nodes.remove(removedIndex);
        HiredRoute updated = new HiredRoute(nodes, false).validatedChain();
        boolean truncated = updated.nodes().size() < nodes.size();
        if (updated.isEmpty()) {
            clearRouteSelection(stack);
        } else {
            saveRouteSelection(stack, level.dimension(), updated);
        }
        syncClipboardStack(player);
        sendSelectedRouteOutline(player, level, updated);
        String key = draft.route().loop()
                ? truncated ? "route_node_removed_loop_downstream" : "route_node_removed_loop"
                : truncated ? "route_node_removed_downstream" : "route_node_removed";
        player.displayClientMessage(Component.translatable(
                "villagerretaliation.clipboard.message." + key), true);
        return InteractionResult.SUCCESS;
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
        syncClipboardStack(player);
        if (first != null && second != null) {
            sendSelectedWorkAreaOutline(player, level, first, second);
        }
        player.displayClientMessage(workAreaPositionMessage(position, first, second), true);
        return InteractionResult.SUCCESS;
    }

    private static Component workAreaPositionMessage(WorkAreaPosition position, BlockPos first, BlockPos second) {
        if (position == WorkAreaPosition.FIRST) {
            String key = second == null ? "corner_one_next" : "corner_one_ready";
            return Component.translatable("villagerretaliation.clipboard.message." + key);
        }
        String key = first == null ? "corner_two_next" : "corner_two_ready";
        return Component.translatable("villagerretaliation.clipboard.message." + key);
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
        syncClipboardStack(player);
        sendSelectedWorkAreaOutline(player, level, first, second);
        player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.job_site_draft_centered",
                dimensions(first, second), positionDescription(center)), true);
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
        syncClipboardStack(player);
        sendSelectedWorkAreaOutline(player, level, min, max);
        WorkAreaDraft updated = selectedWorkArea(stack);
        player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.job_site_draft_centered",
                dimensions(updated.min(), updated.max()), positionDescription(updated.center())), true);
    }

    public static void sendAssignedStorageOutlines(ServerPlayer player, List<AssignedContainerRecord> records) {
        sendAssignedStorageOutlines(player, records, "");
    }

    private static void sendAssignedStorageOutlines(
            ServerPlayer player,
            List<AssignedContainerRecord> records,
            String ownerName) {
        List<ClipboardAssignedStorageSyncPayload.Entry> entries = records.stream()
                .map(record -> new ClipboardAssignedStorageSyncPayload.Entry(
                        record.dimension().location(),
                        record.pos(),
                        AssignedStorageService.PAYMENT_PURPOSE.equals(AssignedStorageService.normalizePurpose(record.purpose())),
                        ownerName,
                        storagePurposeLabel(record.purpose())))
                .toList();
        PacketDistributor.sendToPlayer(player, new ClipboardAssignedStorageSyncPayload(entries, 200));
    }

    public static void sendWorkAreaOutline(ServerPlayer player, ServerLevel level, Villager villager) {
        HiredWorkArea area = HiredVillagerWorkService.workArea(level, villager);
        if (!area.usable()) {
            player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.no_work_area"), true);
            return;
        }
        String ownerName = VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
        String jobName = HiredVillagerContractService.activeRole(level, villager).label();
        PacketDistributor.sendToPlayer(player, ClipboardWorkAreaSyncPayload.assigned(
                level.dimension().location(),
                area.min(),
                area.max(),
                area.center(),
                ownerName,
                jobName,
                200));
        List<AssignedContainerRecord> assigned = AssignedStorageService.allAssignedStorage(level, villager);
        if (!assigned.isEmpty()) {
            sendAssignedStorageOutlines(player, assigned, ownerName);
        }
        player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.showing_job_site", area.rangeDescription()), true);
    }

    private static String storagePurposeLabel(String purpose) {
        String normalized = AssignedStorageService.normalizePurpose(purpose);
        if (AssignedStorageService.PAYMENT_PURPOSE.equals(normalized)) {
            return "Payment";
        }
        if (AssignedStorageService.TOOL_PURPOSE.equals(normalized)) {
            return "Tool";
        }
        if (AssignedStorageService.INPUT_PURPOSE.equals(normalized)) {
            return "Input";
        }
        if (AssignedStorageService.OUTPUT_PURPOSE.equals(normalized)) {
            return "Output";
        }
        return "Storage";
    }

    private static void sendSelectedWorkAreaOutline(ServerPlayer player, ServerLevel level, BlockPos first, BlockPos second) {
        PacketDistributor.sendToPlayer(player, ClipboardWorkAreaSyncPayload.selection(level.dimension().location(), first, second, 120));
    }

    private static void sendRouteOutline(ServerPlayer player, ServerLevel level, HiredRoute route) {
        PacketDistributor.sendToPlayer(player, ClipboardRouteSyncPayload.single(level.dimension().location(), route, route == null || route.isEmpty() ? 0 : 200));
    }

    private static void sendSelectedRouteOutline(ServerPlayer player, ServerLevel level, HiredRoute route) {
        PacketDistributor.sendToPlayer(player, ClipboardRouteSyncPayload.single(level.dimension().location(), route, route == null || route.isEmpty() ? 0 : 160));
    }

    private static void clearClipboardOutlines(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ClipboardAssignedStorageSyncPayload(List.of(), 0));
        clearWorkAreaOutline(player);
        clearRouteOutline(player);
    }

    private static void clearWorkAreaOutline(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ClipboardWorkAreaSyncPayload(List.of(), 0));
    }

    private static void clearRouteOutline(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ClipboardRouteSyncPayload(List.of(), 0));
    }

    public static void assignHeldWorkAreaDraft(ServerPlayer player, ServerLevel level, Villager villager) {
        ItemStack stack = heldClipboard(player);
        if (stack.isEmpty()) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.clipboard.work_area.hold_clipboard");
            return;
        }
        assignSelectedWorkArea(player, level, villager, stack);
    }

    private static void assignSelectedWorkArea(ServerPlayer player, ServerLevel level, Villager villager, ItemStack stack) {
        if (!HiredVillagerWorkService.canManageWork(level, villager, player)) {
            player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.assign_work_area_owner_only"), true);
            return;
        }

        WorkAreaDraft draft = selectedWorkArea(stack);
        if (draft.first() == null || draft.second() == null) {
            player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.select_two_corners"), true);
            return;
        }
        if (!level.dimension().equals(draft.dimension())) {
            player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.work_area_wrong_dimension"), true);
            return;
        }

        if (HiredVillagerWorkService.setWorkArea(player, level, villager, draft.first(), draft.second())) {
            clearWorkAreaSelection(stack);
            syncClipboardStack(player);
            sendWorkAreaOutline(player, level, villager);
        }
    }

    private static void handleRouteVillager(ServerPlayer player, ServerLevel level, Villager villager, ItemStack stack) {
        if (!HiredVillagerWorkService.canManageWork(level, villager, player)) {
            player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.assign_route_owner_only"), true);
            return;
        }
        if (player.isShiftKeyDown()) {
            if (HiredVillagerWorkService.clearRoute(player, level, villager)) {
                sendRouteOutline(player, level, HiredRoute.empty());
                player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.route_cleared"), true);
            }
            return;
        }

        RouteDraft draft = selectedRoute(stack);
        if (!draft.isEmpty()) {
            if (!level.dimension().equals(draft.dimension())) {
                player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.route_wrong_dimension"), true);
                return;
            }
            HiredRoute route = draft.route().validatedChain();
            if (HiredVillagerWorkService.setRoute(player, level, villager, route)) {
                clearRouteSelection(stack);
                syncClipboardStack(player);
                sendRouteOutline(player, level, route);
                player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.route_assigned", routeDescription(route)), true);
            }
            return;
        }

        HiredRoute assigned = HiredVillagerWorkService.route(level, villager);
        if (!assigned.isEmpty()) {
            saveRouteSelection(stack, level.dimension(), assigned);
            syncClipboardStack(player);
            sendRouteOutline(player, level, assigned);
            player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.route_loaded", routeDescription(assigned)), true);
            return;
        }

        player.displayClientMessage(Component.translatable("villagerretaliation.clipboard.message.route_add_instructions"), true);
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

    private static void syncClipboardStack(ServerPlayer player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
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

    private static String routeDescription(HiredRoute route) {
        int count = route == null ? 0 : route.nodes().size();
        return count + " node" + (count == 1 ? "" : "s") + (route != null && route.loop() ? ", loop" : ", back-and-forth");
    }

    private static SelectionAddResult addSelection(
            ItemStack stack,
            ResourceKey<Level> dimension,
            BlockPos pos,
            String purpose) {
        List<SelectedStoragePosition> selected = selectedStoragePositions(stack, mode(stack).assignmentPurpose());
        Optional<SelectedStoragePosition> first = selected.stream().findFirst();
        if (first.isPresent() && !first.get().position().dimension().equals(dimension)) {
            return SelectionAddResult.DIFFERENT_DIMENSION;
        }
        for (SelectedStoragePosition position : selected) {
            if (position.position().dimension().equals(dimension)
                    && position.position().pos().equals(pos)
                    && position.purpose().equals(AssignedStorageService.normalizePurpose(purpose))) {
                return SelectionAddResult.DUPLICATE;
            }
        }
        if (selected.size() >= MAX_SELECTIONS) {
            return SelectionAddResult.FULL;
        }
        selected.add(new SelectedStoragePosition(
                new StoragePosition(dimension, pos.immutable()),
                AssignedStorageService.normalizePurpose(purpose)));
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
        clipboardTag.remove(ROUTE_SELECTION_TAG);
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

    public static void clearSelection(ServerPlayer player, ItemStack stack) {
        clearSelection(stack);
        syncClipboardStack(player);
        clearClipboardOutlines(player);
    }

    private static void saveSelection(ItemStack stack, List<SelectedStoragePosition> selected) {
        ListTag selectedTag = new ListTag();
        for (SelectedStoragePosition selectedPosition : selected) {
            StoragePosition position = selectedPosition.position();
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString(DIMENSION_TAG, position.dimension().location().toString());
            entryTag.putLong(POS_TAG, position.pos().asLong());
            entryTag.putString(PURPOSE_TAG, selectedPosition.purpose());
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

    private static void saveRouteSelection(ItemStack stack, ResourceKey<Level> dimension, HiredRoute route) {
        if (route == null || route.isEmpty()) {
            clearRouteSelection(stack);
            return;
        }
        HiredRoute safeRoute = route.validatedChain();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag clipboardTag = tag.contains(TAG, Tag.TAG_COMPOUND)
                    ? tag.getCompound(TAG)
                    : new CompoundTag();
            CompoundTag selectionTag = new CompoundTag();
            ListTag nodeTags = new ListTag();
            for (BlockPos node : safeRoute.nodes()) {
                nodeTags.add(net.minecraft.nbt.LongTag.valueOf(node.asLong()));
            }
            selectionTag.putString(DIMENSION_TAG, dimension.location().toString());
            selectionTag.put(NODES_TAG, nodeTags);
            selectionTag.putBoolean(LOOP_TAG, safeRoute.loop());
            clipboardTag.put(ROUTE_SELECTION_TAG, selectionTag);
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

    private static void clearRouteSelection(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(TAG)) {
            return;
        }
        CompoundTag tag = customData.copyTag();
        CompoundTag clipboardTag = tag.getCompound(TAG);
        clipboardTag.remove(ROUTE_SELECTION_TAG);
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
        NONE("none", "None"),
        ASSIGN_STORAGE("assign_storage", "Assign Global Storage"),
        ASSIGN_TOOL_STORAGE("assign_tool_storage", "Assign Tool Storage"),
        ASSIGN_INPUT_STORAGE("assign_input_storage", "Assign Input Storage"),
        ASSIGN_OUTPUT_STORAGE("assign_output_storage", "Assign Output Storage"),
        ASSIGN_PAYMENT("assign_payment", "Assign Payment Box"),
        WORK_AREA("work_area", "Preview Job Site"),
        SET_WORK_AREA("set_work_area", "Edit Job Site"),
        ROUTE("route", "Route Mode");

        private static final ClipboardMode[] VALUES = values();
        private static final ClipboardMode[] STORAGE_VALUES = {
                ASSIGN_STORAGE,
                ASSIGN_TOOL_STORAGE,
                ASSIGN_INPUT_STORAGE,
                ASSIGN_OUTPUT_STORAGE
        };
        private static final ClipboardMode[] INVENTORY_CYCLE_VALUES = {
                NONE,
                ASSIGN_STORAGE,
                ASSIGN_PAYMENT,
                WORK_AREA,
                SET_WORK_AREA,
                ROUTE
        };
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
            case NONE -> ChatFormatting.GRAY;
            case ASSIGN_PAYMENT -> ChatFormatting.GREEN;
            case ASSIGN_STORAGE, ASSIGN_TOOL_STORAGE, ASSIGN_INPUT_STORAGE, ASSIGN_OUTPUT_STORAGE -> ChatFormatting.BLUE;
            case WORK_AREA -> ChatFormatting.YELLOW;
            case SET_WORK_AREA -> ChatFormatting.GOLD;
            case ROUTE -> ChatFormatting.AQUA;
            };
        }

        public boolean isStorageAssignmentMode() {
            return switch (this) {
                case ASSIGN_STORAGE, ASSIGN_TOOL_STORAGE, ASSIGN_INPUT_STORAGE, ASSIGN_OUTPUT_STORAGE -> true;
                default -> false;
            };
        }

        public boolean opensClipboardAssignmentMenu() {
            return isStorageAssignmentMode() || this == ASSIGN_PAYMENT;
        }

        public String assignmentPurpose() {
            return this == ASSIGN_PAYMENT ? AssignedStorageService.PAYMENT_PURPOSE : storagePurpose();
        }

        public String storagePurpose() {
            return switch (this) {
                case ASSIGN_TOOL_STORAGE -> AssignedStorageService.TOOL_PURPOSE;
                case ASSIGN_INPUT_STORAGE -> AssignedStorageService.INPUT_PURPOSE;
                case ASSIGN_OUTPUT_STORAGE -> AssignedStorageService.OUTPUT_PURPOSE;
                default -> AssignedStorageService.GENERAL_PURPOSE;
            };
        }

        private ClipboardMode cycleInventoryMode(int delta, ClipboardMode lastStorageMode) {
            if (delta == 0) {
                return this;
            }
            ClipboardMode inventoryMode = isStorageAssignmentMode() ? ASSIGN_STORAGE : this;
            for (int i = 0; i < INVENTORY_CYCLE_VALUES.length; i++) {
                if (INVENTORY_CYCLE_VALUES[i] == inventoryMode) {
                    int index = Math.floorMod(i + Integer.compare(delta, 0), INVENTORY_CYCLE_VALUES.length);
                    ClipboardMode next = INVENTORY_CYCLE_VALUES[index];
                    return next == ASSIGN_STORAGE ? lastStorageMode : next;
                }
            }
            return lastStorageMode;
        }

        private ClipboardMode cycleStorageVariant(int delta) {
            if (delta == 0 || !isStorageAssignmentMode()) {
                return this;
            }
            for (int i = 0; i < STORAGE_VALUES.length; i++) {
                if (STORAGE_VALUES[i] == this) {
                    int index = Math.floorMod(i + Integer.compare(delta, 0), STORAGE_VALUES.length);
                    return STORAGE_VALUES[index];
                }
            }
            return ASSIGN_STORAGE;
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

    public record RouteDraft(ResourceKey<Level> dimension, HiredRoute route) {
        public RouteDraft {
            route = route == null ? HiredRoute.empty() : route.validatedChain();
            if (route.isEmpty()) {
                dimension = null;
            }
        }

        private static RouteDraft empty() {
            return new RouteDraft(null, HiredRoute.empty());
        }

        public boolean isEmpty() {
            return this.route.isEmpty();
        }
    }

    public record SelectedStoragePosition(StoragePosition position, String purpose) {
        public SelectedStoragePosition {
            purpose = AssignedStorageService.normalizePurpose(purpose);
        }

        public boolean paymentPurpose() {
            return AssignedStorageService.PAYMENT_PURPOSE.equals(this.purpose);
        }
    }
}

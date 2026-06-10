package com.jvn.villagerretaliation.item;

import com.jvn.villagerretaliation.interaction.work.BuilderStructureScanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

public final class ConstructionBlueprintItem extends Item {
    private static final String TAG = "VillagerRetaliationConstructionBlueprint";
    private static final String JOB_ID_TAG = "JobId";
    private static final String VILLAGER_ID_TAG = "VillagerId";
    private static final String STRUCTURE_ID_TAG = "StructureId";
    private static final String STRUCTURE_LABEL_TAG = "StructureLabel";
    private static final String DIMENSION_TAG = "Dimension";
    private static final String ORIGIN_TAG = "Origin";
    private static final String ROTATION_TAG = "Rotation";
    private static final String LOCAL_MIN_TAG = "LocalMin";
    private static final String LOCAL_MAX_TAG = "LocalMax";
    private static final String TOTAL_BLOCKS_TAG = "TotalBlocks";
    private static final String PAID_CURRENCY_TAG = "PaidCurrency";
    private static final String JOB_COST_TAG = "JobCost";
    private static final String MATERIAL_SUMMARY_TAG = "MaterialSummary";
    private static final String MISSING_MATERIALS_TAG = "MissingMaterials";
    private static final String BUILDER_NAME_TAG = "BuilderName";
    private static final String STARTED_GAME_TIME_TAG = "StartedGameTime";
    private static final String STARTED_TAG = "Started";
    private static final String EXPIRED_TAG = "Expired";
    private static final String COMPLETED_TAG = "Completed";
    private static final String PLACEMENT_LOCKED_TAG = "PlacementLocked";
    private static final String BLOCKS_TAG = "Blocks";
    private static final String LOCAL_POS_TAG = "LocalPos";
    private static final String STATE_TAG = "State";

    public ConstructionBlueprintItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(
            ServerLevel level,
            Villager villager,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            UUID jobId,
            int paidCurrency,
            int jobCost,
            String missingMaterials,
            long startedGameTime) {
        ItemStack stack = new ItemStack(VillagerRetaliationItems.CONSTRUCTION_BLUEPRINT.get());
        CompoundTag blueprintTag = new CompoundTag();
        blueprintTag.putString(JOB_ID_TAG, jobId.toString());
        if (villager != null) {
            blueprintTag.putUUID(VILLAGER_ID_TAG, villager.getUUID());
        }
        blueprintTag.putString(STRUCTURE_ID_TAG, plan.entry().id().toString());
        blueprintTag.putString(STRUCTURE_LABEL_TAG, plan.entry().menuLabel());
        blueprintTag.putString(DIMENSION_TAG, level.dimension().location().toString());
        blueprintTag.putLong(ORIGIN_TAG, origin.asLong());
        blueprintTag.putString(ROTATION_TAG, plan.rotation().name());
        blueprintTag.putLong(LOCAL_MIN_TAG, plan.localMin().asLong());
        blueprintTag.putLong(LOCAL_MAX_TAG, plan.localMax().asLong());
        blueprintTag.putInt(TOTAL_BLOCKS_TAG, plan.blocks().size());
        blueprintTag.putInt(PAID_CURRENCY_TAG, Math.max(0, paidCurrency));
        blueprintTag.putInt(JOB_COST_TAG, Math.max(0, jobCost));
        blueprintTag.putString(MATERIAL_SUMMARY_TAG, plan.materialSummary(8));
        blueprintTag.putString(MISSING_MATERIALS_TAG, missingMaterials == null ? "" : missingMaterials);
        blueprintTag.putString(BUILDER_NAME_TAG, villager == null ? "" : villager.getName().getString());
        blueprintTag.putLong(STARTED_GAME_TIME_TAG, startedGameTime);
        blueprintTag.putBoolean(STARTED_TAG, startedGameTime > 0L);
        blueprintTag.putBoolean(EXPIRED_TAG, false);
        blueprintTag.putBoolean(COMPLETED_TAG, false);
        blueprintTag.putBoolean(PLACEMENT_LOCKED_TAG, false);
        blueprintTag.put(BLOCKS_TAG, previewBlocks(plan));

        CompoundTag root = new CompoundTag();
        root.put(TAG, blueprintTag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        return stack;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        Optional<PreviewData> preview = previewData(stack);
        if (preview.isEmpty() || preview.get().locked() || preview.get().placementLocked()) {
            return InteractionResult.FAIL;
        }
        BlockPos target = context.getClickedPos();
        if (!level.getBlockState(target).canBeReplaced()) {
            target = target.relative(context.getClickedFace());
        }
        com.jvn.villagerretaliation.interaction.VillagerInteractionService.handleConstructionBlueprintDeploy(player, stack, target);
        return InteractionResult.SUCCESS;
    }

    public static boolean isBlueprint(ItemStack stack) {
        return stack != null && stack.is(VillagerRetaliationItems.CONSTRUCTION_BLUEPRINT.get()) && blueprintTag(stack).isPresent();
    }

    public static void expireMatchingBlueprints(ServerPlayer player, UUID jobId) {
        if (player == null || jobId == null) {
            return;
        }
        expireMatchingBlueprints(player.getInventory().items, jobId);
        expireMatchingBlueprints(player.getInventory().offhand, jobId);
    }

    public static void completeMatchingBlueprints(ServerLevel level, UUID jobId) {
        if (level == null || level.getServer() == null || jobId == null) {
            return;
        }
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            completeMatchingBlueprints(player.getInventory().items, jobId);
            completeMatchingBlueprints(player.getInventory().offhand, jobId);
        }
    }

    public static Optional<PreviewData> previewData(ItemStack stack) {
        Optional<CompoundTag> optionalTag = blueprintTag(stack);
        if (optionalTag.isEmpty()) {
            return Optional.empty();
        }
        CompoundTag tag = optionalTag.get();
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString(DIMENSION_TAG));
        ResourceLocation structureId = ResourceLocation.tryParse(tag.getString(STRUCTURE_ID_TAG));
        Optional<UUID> jobId = parseJobId(tag);
        if (dimensionId == null || structureId == null || jobId.isEmpty() || !tag.contains(ORIGIN_TAG, Tag.TAG_LONG)) {
            return Optional.empty();
        }
        List<PreviewBlock> blocks = new ArrayList<>();
        ListTag blockTags = tag.getList(BLOCKS_TAG, Tag.TAG_COMPOUND);
        for (Tag rawBlockTag : blockTags) {
            if (!(rawBlockTag instanceof CompoundTag blockTag)
                    || !blockTag.contains(LOCAL_POS_TAG, Tag.TAG_LONG)
                    || !blockTag.contains(STATE_TAG, Tag.TAG_COMPOUND)) {
                continue;
            }
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), blockTag.getCompound(STATE_TAG));
            if (state.isAir()) {
                continue;
            }
            blocks.add(new PreviewBlock(BlockPos.of(blockTag.getLong(LOCAL_POS_TAG)), state));
        }
        return Optional.of(new PreviewData(
                jobId.get(),
                tag.hasUUID(VILLAGER_ID_TAG) ? tag.getUUID(VILLAGER_ID_TAG) : null,
                ResourceKey.create(Registries.DIMENSION, dimensionId),
                structureId,
                tag.getString(STRUCTURE_LABEL_TAG),
                BlockPos.of(tag.getLong(ORIGIN_TAG)),
                readRotation(tag),
                tag.contains(LOCAL_MIN_TAG, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(LOCAL_MIN_TAG)) : BlockPos.ZERO,
                tag.contains(LOCAL_MAX_TAG, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(LOCAL_MAX_TAG)) : BlockPos.ZERO,
                Math.max(0, tag.getInt(TOTAL_BLOCKS_TAG)),
                Math.max(0, tag.getInt(PAID_CURRENCY_TAG)),
                tag.contains(JOB_COST_TAG, Tag.TAG_INT)
                        ? Math.max(0, tag.getInt(JOB_COST_TAG))
                        : Math.max(0, tag.getInt(PAID_CURRENCY_TAG)),
                tag.getString(MATERIAL_SUMMARY_TAG),
                tag.getString(MISSING_MATERIALS_TAG),
                tag.getString(BUILDER_NAME_TAG),
                tag.getBoolean(STARTED_TAG) || tag.getLong(STARTED_GAME_TIME_TAG) > 0L,
                tag.getBoolean(EXPIRED_TAG),
                tag.getBoolean(COMPLETED_TAG),
                tag.getBoolean(PLACEMENT_LOCKED_TAG),
                blocks
        ));
    }

    public static void updatePlacement(
            ItemStack stack,
            ServerLevel level,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            Rotation rotation) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            CompoundTag blueprintTag = root.contains(TAG, Tag.TAG_COMPOUND)
                    ? root.getCompound(TAG)
                    : new CompoundTag();
            blueprintTag.putString(DIMENSION_TAG, level.dimension().location().toString());
            blueprintTag.putLong(ORIGIN_TAG, origin.asLong());
            blueprintTag.putString(ROTATION_TAG, (rotation == null ? Rotation.NONE : rotation).name());
            blueprintTag.putLong(LOCAL_MIN_TAG, plan.localMin().asLong());
            blueprintTag.putLong(LOCAL_MAX_TAG, plan.localMax().asLong());
            blueprintTag.putInt(TOTAL_BLOCKS_TAG, plan.blocks().size());
            blueprintTag.putString(MATERIAL_SUMMARY_TAG, plan.materialSummary(8));
            blueprintTag.put(BLOCKS_TAG, previewBlocks(plan));
            root.put(TAG, blueprintTag);
        });
    }

    public static void markStarted(ItemStack stack, Villager villager, int paidCurrency, long startedGameTime) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            CompoundTag blueprintTag = root.contains(TAG, Tag.TAG_COMPOUND)
                    ? root.getCompound(TAG)
                    : new CompoundTag();
            if (villager != null) {
                blueprintTag.putUUID(VILLAGER_ID_TAG, villager.getUUID());
                blueprintTag.putString(BUILDER_NAME_TAG, villager.getName().getString());
            }
            blueprintTag.putInt(PAID_CURRENCY_TAG, Math.max(0, paidCurrency));
            blueprintTag.putLong(STARTED_GAME_TIME_TAG, Math.max(0L, startedGameTime));
            blueprintTag.putBoolean(STARTED_TAG, true);
            blueprintTag.putBoolean(EXPIRED_TAG, false);
            blueprintTag.putBoolean(COMPLETED_TAG, false);
            root.put(TAG, blueprintTag);
        });
        stack.remove(DataComponents.ITEM_NAME);
    }

    public static Optional<Boolean> togglePlacementLocked(ItemStack stack) {
        Optional<PreviewData> preview = previewData(stack);
        if (preview.isEmpty() || preview.get().locked()) {
            return Optional.empty();
        }
        boolean locked = !preview.get().placementLocked();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            CompoundTag blueprintTag = root.contains(TAG, Tag.TAG_COMPOUND)
                    ? root.getCompound(TAG)
                    : new CompoundTag();
            blueprintTag.putBoolean(PLACEMENT_LOCKED_TAG, locked);
            root.put(TAG, blueprintTag);
        });
        return Optional.of(locked);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Optional<PreviewData> data = previewData(stack);
        if (data.isEmpty()) {
            tooltip.add(Component.translatable("item.villagerretaliation.construction_blueprint.invalid").withStyle(ChatFormatting.RED));
            return;
        }
        PreviewData preview = data.get();
        if (preview.expired()) {
            tooltip.add(Component.translatable("item.villagerretaliation.construction_blueprint.expired").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        } else if (preview.completed()) {
            tooltip.add(Component.translatable("item.villagerretaliation.construction_blueprint.completed").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        } else if (preview.started()) {
            tooltip.add(Component.translatable("item.villagerretaliation.construction_blueprint.active").withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.translatable("item.villagerretaliation.construction_blueprint.pending").withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(label("item.villagerretaliation.construction_blueprint.structure", preview.structureLabel(), ChatFormatting.AQUA));
        if (!preview.builderName().isBlank()) {
            tooltip.add(label("item.villagerretaliation.construction_blueprint.builder", preview.builderName(), ChatFormatting.YELLOW));
        }
        tooltip.add(label("item.villagerretaliation.construction_blueprint.site", formatPos(preview.origin()), ChatFormatting.GOLD));
        tooltip.add(label("item.villagerretaliation.construction_blueprint.rotation", preview.rotation().name(), ChatFormatting.LIGHT_PURPLE));
        tooltip.add(label("item.villagerretaliation.construction_blueprint.size", preview.sizeText(), ChatFormatting.GRAY));
        tooltip.add(label("item.villagerretaliation.construction_blueprint.blocks", Integer.toString(preview.totalBlocks()), ChatFormatting.GRAY));
        tooltip.add(label("item.villagerretaliation.construction_blueprint.payment", Integer.toString(preview.paidCurrency()), ChatFormatting.GREEN));
        tooltip.add(label("item.villagerretaliation.construction_blueprint.job_cost", Integer.toString(preview.jobCost()), ChatFormatting.GREEN));
        if (!preview.locked()) {
            tooltip.add(Component.translatable(preview.placementLocked()
                    ? "item.villagerretaliation.construction_blueprint.placement_locked"
                    : "item.villagerretaliation.construction_blueprint.placement_unlocked").withStyle(preview.placementLocked()
                    ? ChatFormatting.YELLOW
                    : ChatFormatting.AQUA));
        }
        if (!preview.materialSummary().isBlank()) {
            tooltip.add(label("item.villagerretaliation.construction_blueprint.materials", preview.materialSummary(), ChatFormatting.WHITE));
        }
        if (!preview.missingMaterials().isBlank()) {
            tooltip.add(label("item.villagerretaliation.construction_blueprint.missing", preview.missingMaterials(), ChatFormatting.RED));
        }
        if (!preview.locked()) {
            tooltip.add(Component.translatable("item.villagerretaliation.construction_blueprint.controls.deploy").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable(preview.placementLocked()
                    ? "item.villagerretaliation.construction_blueprint.controls.locked"
                    : "item.villagerretaliation.construction_blueprint.controls.move").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("item.villagerretaliation.construction_blueprint.controls.toggle_lock").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static ListTag previewBlocks(BuilderStructureScanner.StructurePlan plan) {
        ListTag blocks = new ListTag();
        for (BuilderStructureScanner.BuildBlock block : plan.blocks()) {
            CompoundTag blockTag = new CompoundTag();
            blockTag.putLong(LOCAL_POS_TAG, block.localPos().asLong());
            blockTag.put(STATE_TAG, NbtUtils.writeBlockState(block.state()));
            blocks.add(blockTag);
        }
        return blocks;
    }

    private static void expireMatchingBlueprints(List<ItemStack> stacks, UUID jobId) {
        for (ItemStack stack : stacks) {
            if (jobId(stack).filter(jobId::equals).isPresent()) {
                expire(stack);
            }
        }
    }

    private static void completeMatchingBlueprints(List<ItemStack> stacks, UUID jobId) {
        for (ItemStack stack : stacks) {
            if (jobId(stack).filter(jobId::equals).isPresent()) {
                complete(stack);
            }
        }
    }

    private static void expire(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            CompoundTag blueprintTag = root.contains(TAG, Tag.TAG_COMPOUND)
                    ? root.getCompound(TAG)
                    : new CompoundTag();
            blueprintTag.putBoolean(EXPIRED_TAG, true);
            root.put(TAG, blueprintTag);
        });
        stack.set(
                DataComponents.ITEM_NAME,
                Component.translatable("item.villagerretaliation.construction_blueprint.expired_name")
                        .withStyle(ChatFormatting.RED));
    }

    private static void complete(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            CompoundTag blueprintTag = root.contains(TAG, Tag.TAG_COMPOUND)
                    ? root.getCompound(TAG)
                    : new CompoundTag();
            blueprintTag.putBoolean(COMPLETED_TAG, true);
            blueprintTag.putBoolean(EXPIRED_TAG, false);
            root.put(TAG, blueprintTag);
        });
        stack.set(
                DataComponents.ITEM_NAME,
                Component.translatable("item.villagerretaliation.construction_blueprint.completed_name")
                        .withStyle(ChatFormatting.GREEN));
    }

    private static Optional<UUID> jobId(ItemStack stack) {
        return blueprintTag(stack).flatMap(ConstructionBlueprintItem::parseJobId);
    }

    private static Optional<UUID> parseJobId(CompoundTag tag) {
        if (!tag.contains(JOB_ID_TAG, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(tag.getString(JOB_ID_TAG)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static Rotation readRotation(CompoundTag tag) {
        if (!tag.contains(ROTATION_TAG, Tag.TAG_STRING)) {
            return Rotation.NONE;
        }
        try {
            return Rotation.valueOf(tag.getString(ROTATION_TAG));
        } catch (IllegalArgumentException ignored) {
            return Rotation.NONE;
        }
    }

    private static Optional<CompoundTag> blueprintTag(ItemStack stack) {
        if (stack == null || !stack.is(VillagerRetaliationItems.CONSTRUCTION_BLUEPRINT.get())) {
            return Optional.empty();
        }
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(TAG)) {
            return Optional.empty();
        }
        CompoundTag root = customData.copyTag();
        if (!root.contains(TAG, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        return Optional.of(root.getCompound(TAG));
    }

    private static Component label(String key, String value, ChatFormatting valueColor) {
        return Component.translatable(key)
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(valueColor));
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    public record PreviewData(
            UUID jobId,
            UUID villagerId,
            ResourceKey<Level> dimension,
            ResourceLocation structureId,
            String structureLabel,
            BlockPos origin,
            Rotation rotation,
            BlockPos localMin,
            BlockPos localMax,
            int totalBlocks,
            int paidCurrency,
            int jobCost,
            String materialSummary,
            String missingMaterials,
            String builderName,
            boolean started,
            boolean expired,
            boolean completed,
            boolean placementLocked,
            List<PreviewBlock> blocks) {
        public BlockPos localCenter() {
            return new BlockPos(
                    Math.floorDiv(this.localMin.getX() + this.localMax.getX(), 2),
                    Math.floorDiv(this.localMin.getY() + this.localMax.getY(), 2),
                    Math.floorDiv(this.localMin.getZ() + this.localMax.getZ(), 2));
        }

        public BlockPos worldCenter() {
            return this.origin.offset(localCenter());
        }

        public BlockPos worldMin() {
            return this.origin.offset(this.localMin);
        }

        public BlockPos worldMax() {
            return this.origin.offset(this.localMax);
        }

        public String sizeText() {
            int width = Math.max(0, this.localMax.getX() - this.localMin.getX() + 1);
            int height = Math.max(0, this.localMax.getY() - this.localMin.getY() + 1);
            int depth = Math.max(0, this.localMax.getZ() - this.localMin.getZ() + 1);
            return width + " x " + height + " x " + depth;
        }

        public boolean locked() {
            return this.expired || this.completed || this.started;
        }
    }

    public record PreviewBlock(BlockPos localPos, BlockState state) {
        public BlockPos worldPos(BlockPos origin) {
            return origin.offset(this.localPos);
        }
    }
}

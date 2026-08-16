package com.jvn.villagerretaliation.item;

import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.inventory.ConnectedContainerResolver;
import com.jvn.villagerretaliation.network.BlueprintChecklistSyncPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BlueprintChecklistItem extends Item {
    private static final String TAG = "VillagerRetaliationBlueprintChecklist";
    private static final String TITLE_TAG = "Title";
    private static final String ENTRIES_TAG = "Entries";
    private static final String ITEM_TAG = "Item";
    private static final String REQUIRED_TAG = "Required";
    private static final String OBSERVED_TAG = "Observed";
    private static final String CHECKED_TAG = "Checked";
    private static final String AUTO_SEEN_TAG = "AutoSeen";
    public static final int MAX_ENTRIES = 2048;

    public BlueprintChecklistItem(Properties properties) {
        super(properties);
    }

    public static ItemStack createFromBlueprint(ItemStack blueprint) {
        List<BuilderStructureScanner.MaterialRequirement> requirements =
                ConstructionBlueprintItem.materialRequirements(blueprint);
        if (requirements.isEmpty()) {
            return ItemStack.EMPTY;
        }
        String title = ConstructionBlueprintItem.previewData(blueprint)
                .map(ConstructionBlueprintItem.PreviewData::structureLabel)
                .filter(label -> !label.isBlank())
                .orElse(Component.translatable("item.villagerretaliation.blueprint_checklist").getString());
        return create(title, requirements);
    }

    public static ItemStack create(
            String title,
            List<BuilderStructureScanner.MaterialRequirement> requirements) {
        ItemStack result = new ItemStack(VillagerRetaliationItems.BLUEPRINT_CHECKLIST.get());
        List<Entry> entries = new ArrayList<>();
        if (requirements != null) {
            for (BuilderStructureScanner.MaterialRequirement requirement : requirements) {
                if (entries.size() >= MAX_ENTRIES || requirement == null
                        || requirement.item().isEmpty() || requirement.count() <= 0) {
                    continue;
                }
                entries.add(new Entry(requirement.item().copyWithCount(1), requirement.count(), 0, false, false));
            }
        }
        if (entries.isEmpty()) {
            return ItemStack.EMPTY;
        }
        write(result, title, entries);
        result.set(DataComponents.ITEM_NAME,
                Component.translatable("item.villagerretaliation.blueprint_checklist"));
        return result;
    }

    public static boolean isChecklist(ItemStack stack) {
        return stack != null && stack.is(VillagerRetaliationItems.BLUEPRINT_CHECKLIST.get()) && dataTag(stack) != null;
    }

    public static ChecklistData data(ItemStack stack) {
        CompoundTag data = dataTag(stack);
        if (data == null) {
            return new ChecklistData("", List.of());
        }
        List<Entry> entries = new ArrayList<>();
        ListTag entryTags = data.getList(ENTRIES_TAG, Tag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(entryTags.size(), MAX_ENTRIES); index++) {
            CompoundTag entryTag = entryTags.getCompound(index);
            ResourceLocation itemId = ResourceLocation.tryParse(entryTag.getString(ITEM_TAG));
            if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                continue;
            }
            ItemStack item = new ItemStack(BuiltInRegistries.ITEM.get(itemId));
            if (item.isEmpty()) {
                continue;
            }
            int required = Math.max(1, entryTag.getInt(REQUIRED_TAG));
            entries.add(new Entry(
                    item,
                    required,
                    Math.max(0, entryTag.getInt(OBSERVED_TAG)),
                    entryTag.getBoolean(CHECKED_TAG),
                    entryTag.getBoolean(AUTO_SEEN_TAG)));
        }
        return new ChecklistData(data.getString(TITLE_TAG), List.copyOf(entries));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            updateAgainst(stack, serverPlayer.getInventory(), false);
            syncStack(serverPlayer);
            sendView(serverPlayer, hand, stack, true);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getLevel() instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        Container container = ConnectedContainerResolver.resolve(level, context.getClickedPos());
        if (container == null) {
            return InteractionResult.PASS;
        }
        int updated = updateAgainst(context.getItemInHand(), container, true);
        syncStack(serverPlayer);
        showUpdatedActionBar(serverPlayer, updated);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!(level instanceof ServerLevel) || !(entity instanceof ServerPlayer player)
                || Math.floorMod(level.getGameTime() + slotId, 20L) != 0L) {
            return;
        }
        if (updateAgainst(stack, player.getInventory(), false) > 0) {
            syncStack(player);
            if (player.getMainHandItem() == stack) {
                sendView(player, InteractionHand.MAIN_HAND, stack, false);
            } else if (player.getOffhandItem() == stack) {
                sendView(player, InteractionHand.OFF_HAND, stack, false);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ChecklistData data = data(stack);
        if (data.entries().isEmpty()) {
            tooltip.add(Component.translatable("item.villagerretaliation.blueprint_checklist.invalid")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (!data.title().isBlank()) {
            tooltip.add(Component.literal(data.title()).withStyle(ChatFormatting.GRAY));
        }
        long checked = data.entries().stream().filter(Entry::checked).count();
        tooltip.add(Component.translatable(
                        "item.villagerretaliation.blueprint_checklist.progress", checked, data.entries().size())
                .withStyle(checked == data.entries().size() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.villagerretaliation.blueprint_checklist.controls.open")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.villagerretaliation.blueprint_checklist.controls.container")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    public static void handleToggle(ServerPlayer player, InteractionHand hand, int index) {
        if (player == null || hand == null || index < 0 || index >= MAX_ENTRIES) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!toggle(stack, index)) {
            return;
        }
        syncStack(player);
        sendView(player, hand, stack, false);
    }

    static boolean toggle(ItemStack stack, int index) {
        ChecklistData data = data(stack);
        if (!isChecklist(stack) || index < 0 || index >= data.entries().size()) {
            return false;
        }
        List<Entry> entries = new ArrayList<>(data.entries());
        Entry current = entries.get(index);
        boolean checked = !current.checked();
        entries.set(index, new Entry(
                current.item(), current.required(), checked ? current.required() : 0, checked, current.autoSeen()));
        write(stack, data.title(), entries);
        return true;
    }

    static int updateAgainst(ItemStack stack, Container inventory, boolean updateObserved) {
        ChecklistData data = data(stack);
        if (data.entries().isEmpty() || inventory == null) {
            return 0;
        }
        int changedEntries = 0;
        List<Entry> updatedEntries = new ArrayList<>(data.entries().size());
        for (Entry entry : data.entries()) {
            if (entry.checked()) {
                updatedEntries.add(entry);
                continue;
            }
            int count = countMatching(inventory, entry.item());
            int observed = updateObserved ? count : entry.observed();
            boolean autoSeen = entry.autoSeen();
            boolean checked = false;
            if (count >= entry.required()) {
                autoSeen = true;
                checked = true;
                observed = count;
            }
            Entry updated = new Entry(entry.item(), entry.required(), observed, checked, autoSeen);
            if (!updated.equals(entry)) {
                changedEntries++;
            }
            updatedEntries.add(updated);
        }
        if (changedEntries > 0) {
            write(stack, data.title(), updatedEntries);
        }
        return changedEntries;
    }

    private static int countMatching(Container inventory, ItemStack required) {
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (BuilderStructureScanner.sameMaterial(candidate, required)) {
                total += candidate.getCount();
            }
        }
        return total;
    }

    private static void sendView(ServerPlayer player, InteractionHand hand, ItemStack stack, boolean openScreen) {
        ChecklistData data = data(stack);
        List<BlueprintChecklistSyncPayload.EntryView> entries = data.entries().stream()
                .map(entry -> new BlueprintChecklistSyncPayload.EntryView(
                        entry.item(), entry.required(), entry.observed(), entry.checked()))
                .toList();
        PacketDistributor.sendToPlayer(player,
                new BlueprintChecklistSyncPayload(hand, data.title(), entries, openScreen));
    }

    private static void showUpdatedActionBar(ServerPlayer player, int updated) {
        player.displayClientMessage(Component.translatable(
                updated == 1
                        ? "villagerretaliation.blueprint_checklist.subtitle.updated_one"
                        : "villagerretaliation.blueprint_checklist.subtitle.updated_many",
                updated), true);
    }

    private static void syncStack(ServerPlayer player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
    }

    private static CompoundTag dataTag(ItemStack stack) {
        if (stack == null || !stack.is(VillagerRetaliationItems.BLUEPRINT_CHECKLIST.get())) {
            return null;
        }
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty()) {
            return null;
        }
        CompoundTag root = customData.copyTag();
        return root.contains(TAG, Tag.TAG_COMPOUND) ? root.getCompound(TAG) : null;
    }

    private static void write(ItemStack stack, String title, List<Entry> entries) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            CompoundTag data = new CompoundTag();
            data.putString(TITLE_TAG, title == null ? "" : title);
            ListTag entryTags = new ListTag();
            for (Entry entry : entries) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(entry.item().getItem());
                if (id == null || entryTags.size() >= MAX_ENTRIES) {
                    continue;
                }
                CompoundTag entryTag = new CompoundTag();
                entryTag.putString(ITEM_TAG, id.toString());
                entryTag.putInt(REQUIRED_TAG, Math.max(1, entry.required()));
                entryTag.putInt(OBSERVED_TAG, Math.max(0, entry.observed()));
                entryTag.putBoolean(CHECKED_TAG, entry.checked());
                entryTag.putBoolean(AUTO_SEEN_TAG, entry.autoSeen());
                entryTags.add(entryTag);
            }
            data.put(ENTRIES_TAG, entryTags);
            root.put(TAG, data);
        });
    }

    public record ChecklistData(String title, List<Entry> entries) {
    }

    public record Entry(ItemStack item, int required, int observed, boolean checked, boolean autoSeen) {
    }
}

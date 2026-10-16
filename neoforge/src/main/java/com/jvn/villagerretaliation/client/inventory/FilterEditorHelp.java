package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.item.VillagerAttributeFilterData;
import com.jvn.villagerretaliation.item.VillagerFilterMatcher;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerRecipeFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Read-only explanations shared by the three physical filter editors. */
final class FilterEditorHelp {
    private static final String KEY = "villagerretaliation.gui.filter_editor.";

    private FilterEditorHelp() {
    }

    static Component summary(ItemStack filter) {
        VillagerFilterPolicy.Policy policy = VillagerFilterPolicy.read(filter);
        if (!policy.valid()) {
            return Component.translatable(KEY + "invalid");
        }
        int count = entryCount(filter);
        if (count == 0 && VillagerRetaliationItems.isRecipeFilter(filter)) {
            return Component.translatable(KEY + "empty_recipe");
        }
        if (count == 0) {
            return Component.translatable(KEY + "empty." + policy.listMode().id());
        }
        return Component.translatable(KEY + "summary", count,
                Component.translatable("villagerretaliation.gui.recipe_filter.mode." + policy.listMode().id()),
                Component.translatable(KEY + "combination." + policy.combinationMode().id()));
    }

    static Tooltip rules(ItemStack filter) {
        VillagerFilterPolicy.Policy policy = VillagerFilterPolicy.read(filter);
        MutableComponent text = Component.translatable(KEY + "rules").withStyle(ChatFormatting.YELLOW)
                .append("\n").append(summary(filter).copy().withStyle(ChatFormatting.WHITE));
        if (policy.combinationMode() == VillagerFilterPolicy.CombinationMode.LEGACY) {
            text.append("\n").append(Component.translatable(KEY + "legacy_matching").withStyle(ChatFormatting.GRAY));
        }
        text.append("\n\n").append(Component.translatable(KEY + "worker").withStyle(ChatFormatting.AQUA));
        text.append("\n").append(Component.translatable(KEY + (VillagerRetaliationItems.isRecipeFilter(filter)
                ? "worker_recipe" : "worker_items")).withStyle(ChatFormatting.GRAY));
        if (VillagerRetaliationItems.isRecipeFilter(filter) && entryCount(filter) == 0) {
            text.append("\n").append(Component.translatable(KEY + "empty_recipe_worker").withStyle(ChatFormatting.YELLOW));
        }
        text.append("\n\n").append(Component.translatable(KEY + "preview_hint").withStyle(ChatFormatting.GRAY));
        return Tooltip.create(text);
    }

    static Tooltip containerToggle(boolean showingContainer) {
        return Tooltip.create(Component.translatable(KEY + (showingContainer ? "show_rules" : "show_container"))
                .append("\n").append(Component.translatable(KEY + "container_only"))
                .append("\n").append(Component.translatable(KEY + "container_conflicts")));
    }

    static Tooltip policyTooltip(ItemStack filter, Component description) {
        MutableComponent text = description.copy();
        if (VillagerRetaliationItems.isItemFilter(filter)
                && VillagerFilterPolicy.read(filter).state() == VillagerFilterPolicy.PolicyState.LEGACY) {
            text.append("\n\n").append(Component.translatable(KEY + "legacy_rules_edit").withStyle(ChatFormatting.GRAY));
        }
        return Tooltip.create(text);
    }

    static Tooltip containerTooltip(ItemStack filter, Component description) {
        MutableComponent text = Component.translatable(KEY + "container").withStyle(ChatFormatting.AQUA)
                .append("\n").append(Component.translatable(KEY + "container_only").withStyle(ChatFormatting.GRAY))
                .append("\n").append(description.copy().withStyle(ChatFormatting.WHITE));
        VillagerFilterPolicy.Policy policy = VillagerFilterPolicy.read(filter);
        if (VillagerRetaliationItems.isItemFilter(filter)
                && policy.state() == VillagerFilterPolicy.PolicyState.LEGACY) {
            text.append("\n\n").append(Component.translatable(KEY + "legacy_container_edit").withStyle(ChatFormatting.YELLOW));
            if (policy.combinationMode() == VillagerFilterPolicy.CombinationMode.LEGACY) {
                text.append("\n").append(Component.translatable(KEY + "legacy_combination_convert").withStyle(ChatFormatting.YELLOW));
            }
        }
        return Tooltip.create(text);
    }
    static void appendPreview(List<Component> tooltip, Level level, ItemStack filter, ItemStack sample) {
        if (level == null || sample.isEmpty()) {
            return;
        }
        VillagerFilterMatcher.RawMatchResult result = VillagerFilterMatcher.rawMatchResult(level, filter, sample);
        if (!VillagerFilterPolicy.read(filter).valid() || !result.valid()) {
            tooltip.add(Component.translatable(KEY + "preview.invalid").withStyle(ChatFormatting.RED));
            return;
        }
        boolean allowed = VillagerFilterMatcher.matches(level, filter, sample);
        tooltip.add(Component.translatable(KEY + (allowed ? "preview.allowed" : "preview.denied"))
                .withStyle(allowed ? ChatFormatting.GREEN : ChatFormatting.RED));
        tooltip.add(Component.translatable(KEY + (VillagerRetaliationItems.isRecipeFilter(filter)
                ? "preview.recipe_scope" : "preview.scope")).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static int entryCount(ItemStack filter) {
        if (VillagerRetaliationItems.isItemFilter(filter)) {
            return (int) VillagerItemFilterData.entries(filter).stream().filter(entry -> !entry.isEmpty()).count();
        }
        if (VillagerRetaliationItems.isAttributeFilter(filter)) {
            return VillagerAttributeFilterData.read(filter).attributes().size();
        }
        return VillagerRecipeFilterData.read(filter).recipeId() == null ? 0 : 1;
    }
}

package com.jvn.villagerretaliation.authoring.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

final class EditorRegistryResources {
    static boolean supports(String kind) { return List.of("item", "block", "entity", "sound").contains(kind); }

    static List<EditorResourceCatalog.Entry> entries(String kind) {
        return switch (kind) {
            case "item" -> BuiltInRegistries.ITEM.entrySet().stream().map(entry ->
                    new EditorResourceCatalog.Entry(entry.getKey().location().toString(), entry.getValue().getDescription().getString())).toList();
            case "block" -> BuiltInRegistries.BLOCK.entrySet().stream().map(entry ->
                    new EditorResourceCatalog.Entry(entry.getKey().location().toString(), entry.getValue().getName().getString())).toList();
            case "entity" -> BuiltInRegistries.ENTITY_TYPE.entrySet().stream().map(entry ->
                    new EditorResourceCatalog.Entry(entry.getKey().location().toString(), entry.getValue().getDescription().getString())).toList();
            case "sound" -> Minecraft.getInstance().getSoundManager().getAvailableSounds().stream().map(id -> {
                var event = Minecraft.getInstance().getSoundManager().getSoundEvent(id);
                String title = event != null && event.getSubtitle() != null ? event.getSubtitle().getString() : id.getPath().replace('.', ' ').replace('_', ' ');
                return new EditorResourceCatalog.Entry(id.toString(), title);
            }).toList();
            default -> List.of();
        };
    }

    static ItemStack icon(String kind, String value) {
        var id = ResourceLocation.tryParse(value);
        if (id == null) return ItemStack.EMPTY;
        return switch (kind) {
            case "item" -> BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.get(id).getDefaultInstance() : ItemStack.EMPTY;
            case "block" -> BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.get(id).asItem().getDefaultInstance() : ItemStack.EMPTY;
            default -> ItemStack.EMPTY;
        };
    }

    static String shortcut(String kind) {
        var minecraft = Minecraft.getInstance();
        if (kind.equals("item")) {
            return minecraft.player == null || minecraft.player.getMainHandItem().isEmpty() ? ""
                    : BuiltInRegistries.ITEM.getKey(minecraft.player.getMainHandItem().getItem()).toString();
        }
        if (kind.equals("block") && minecraft.level != null && minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK)
            return BuiltInRegistries.BLOCK.getKey(minecraft.level.getBlockState(hit.getBlockPos()).getBlock()).toString();
        if (kind.equals("entity") && minecraft.hitResult instanceof EntityHitResult hit && !hit.getEntity().isRemoved())
            return BuiltInRegistries.ENTITY_TYPE.getKey(hit.getEntity().getType()).toString();
        return "";
    }

    static String shortcutLabel(String kind) {
        return switch (kind) {
            case "item" -> "Use held item";
            case "block" -> "Use target block";
            case "entity" -> "Use target entity";
            default -> "";
        };
    }
}

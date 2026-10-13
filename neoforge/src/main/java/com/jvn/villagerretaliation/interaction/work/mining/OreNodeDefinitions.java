package com.jvn.villagerretaliation.interaction.work.mining;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.state.BlockState;

public final class OreNodeDefinitions {
    private static volatile Lookup lookup = new Lookup(List.of());

    private OreNodeDefinitions() { }

    public static void reload(ResourceManager resources) {
        var loaded = new ArrayList<Definition>();
        for (var entry : resources.listResources("ore_nodes", path -> path.getPath().endsWith(".json")).entrySet()) {
            try (var reader = entry.getValue().openAsReader()) {
                loaded.add(parse(entry.getKey(), GsonHelper.parse(reader)));
            } catch (IOException | IllegalArgumentException | com.google.gson.JsonParseException exception) {
                throw new IllegalStateException("Invalid ore node definition " + entry.getKey(), exception);
            }
        }
        loaded.sort(Comparator.comparingInt(Definition::priority).reversed().thenComparing(Definition::id));
        lookup = new Lookup(List.copyOf(loaded));
    }

    static Definition parse(ResourceLocation id, JsonObject json) {
        int extractions;
        try {
            extractions = GsonHelper.getAsBigDecimal(json, "extractions").intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Ore extractions must be a whole number", exception);
        }
        if (extractions < 1 || extractions > 100_000) throw new IllegalArgumentException("Ore extractions must be between 1 and 100000");
        var blocks = new ArrayList<ResourceLocation>();
        var tags = new ArrayList<TagKey<net.minecraft.world.level.block.Block>>();
        if (json.has("blocks")) for (var value : GsonHelper.getAsJsonArray(json, "blocks"))
            blocks.add(ResourceLocation.parse(GsonHelper.convertToString(value, "block")));
        if (json.has("tags")) for (var value : GsonHelper.getAsJsonArray(json, "tags"))
            tags.add(TagKey.create(Registries.BLOCK, ResourceLocation.parse(GsonHelper.convertToString(value, "tag"))));
        if (blocks.isEmpty() && tags.isEmpty()) throw new IllegalArgumentException("Ore nodes require blocks or tags");
        return new Definition(id, List.copyOf(blocks), List.copyOf(tags), extractions, GsonHelper.getAsInt(json, "priority", 0));
    }

    public static int extractions(BlockState state) {
        if (state.isAir() || state.hasBlockEntity()) return 0;
        var current = lookup;
        return current.counts.computeIfAbsent(state, block -> {
            for (var definition : current.definitions) if (definition.matches(block)) return definition.extractions();
            return 0;
        });
    }

    public static void onTagsUpdated(net.neoforged.neoforge.event.TagsUpdatedEvent event) {
        if (!event.shouldUpdateStaticData()) return;
        lookup = new Lookup(lookup.definitions);
        HiredOreBlockTracker.clearRuntimeState();
    }

    private record Lookup(List<Definition> definitions, java.util.concurrent.ConcurrentMap<BlockState, Integer> counts) {
        Lookup(List<Definition> definitions) { this(definitions, new java.util.concurrent.ConcurrentHashMap<>()); }
    }

    record Definition(ResourceLocation id, List<ResourceLocation> blocks, List<TagKey<net.minecraft.world.level.block.Block>> tags,
                      int extractions, int priority) {
        boolean matches(BlockState state) {
            return blocks.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock())) || tags.stream().anyMatch(state::is);
        }
    }
}

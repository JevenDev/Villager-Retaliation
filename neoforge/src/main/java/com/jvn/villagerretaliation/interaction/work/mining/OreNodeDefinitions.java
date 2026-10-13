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
    private static volatile List<Definition> definitions = List.of();

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
        definitions = List.copyOf(loaded);
    }

    static Definition parse(ResourceLocation id, JsonObject json) {
        int extractions = GsonHelper.getAsInt(json, "extractions");
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
        for (var definition : definitions) if (definition.matches(state)) return definition.extractions();
        return 0;
    }

    record Definition(ResourceLocation id, List<ResourceLocation> blocks, List<TagKey<net.minecraft.world.level.block.Block>> tags,
                      int extractions, int priority) {
        boolean matches(BlockState state) {
            return blocks.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock())) || tags.stream().anyMatch(state::is);
        }
    }
}

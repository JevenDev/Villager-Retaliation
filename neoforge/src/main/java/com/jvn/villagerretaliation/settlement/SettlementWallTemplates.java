package com.jvn.villagerretaliation.settlement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class SettlementWallTemplates {
    public record Cell(BlockPos position, BlockState state) {}
    public record Piece(BlockPos size, List<Cell> cells) {}
    private static final Map<String, Piece> CACHE = new HashMap<>();

    private SettlementWallTemplates() {}

    public static synchronized Piece get(String name) {
        if (!List.of("stone_tower_low", "stone_tower_tall", "stone_span", "timber_span", "palisade_post", "palisade_span", "plank_post", "plank_span").contains(name))
            throw new IllegalArgumentException("Unknown wall template");
        return CACHE.computeIfAbsent(name, SettlementWallTemplates::read);
    }

    private static Piece read(String name) {
        String resource = "/data/villagerretaliation/structure/walls/" + name + ".nbt";
        try (var stream = SettlementWallTemplates.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("Missing wall template " + name);
            var root = NbtIo.readCompressed(stream, NbtAccounter.create(1_048_576));
            var dimensions = root.getList("size", Tag.TAG_INT);
            var size = new BlockPos(dimensions.getInt(0), dimensions.getInt(1), dimensions.getInt(2));
            var palette = new ArrayList<BlockState>();
            for (var value : root.getList("palette", Tag.TAG_COMPOUND)) {
                var tag = (net.minecraft.nbt.CompoundTag) value;
                var id = ResourceLocation.parse(tag.getString("Name"));
                if (!BuiltInRegistries.BLOCK.containsKey(id)) {
                    if (!id.toString().equals("supplementaries:gravel_bricks"))
                        throw new IllegalStateException("Missing wall block " + id);
                    palette.add(Blocks.ANDESITE.defaultBlockState());
                } else palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag));
            }
            var cells = new ArrayList<Cell>();
            for (var value : root.getList("blocks", Tag.TAG_COMPOUND)) {
                var tag = (net.minecraft.nbt.CompoundTag) value;
                var pos = tag.getList("pos", Tag.TAG_INT);
                cells.add(new Cell(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)), palette.get(tag.getInt("state"))));
            }
            return new Piece(size, List.copyOf(cells));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read wall template " + name, exception);
        }
    }
}

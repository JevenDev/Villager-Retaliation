package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CampaignStructureProcessor extends StructureProcessor {
    private static final Codec<BlockPos> POSITION = BlockPos.CODEC.validate(position ->
            Math.abs((long) position.getX()) <= 32 && Math.abs((long) position.getY()) <= 32 && Math.abs((long) position.getZ()) <= 32
                    ? DataResult.success(position) : DataResult.error(() -> "Campaign structure coordinates must be between -32 and 32."));
    private static final Codec<List<Cell>> CELLS = Cell.CODEC.listOf().validate(cells -> {
        var ids = new HashSet<String>();
        var positions = new HashSet<BlockPos>();
        return !cells.isEmpty() && cells.size() <= 256 && cells.stream().allMatch(cell -> ids.add(cell.id()) && positions.add(cell.position()))
                ? DataResult.success(List.copyOf(cells)) : DataResult.error(() -> "Campaign structures require 1 to 256 unique cells and positions.");
    });
    public static final MapCodec<CampaignStructureProcessor> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("layout").forGetter(value -> value.layout),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("revision").forGetter(value -> value.revision),
            POSITION.fieldOf("anchor").forGetter(value -> value.anchor),
            CELLS.fieldOf("cells").forGetter(value -> value.cells)
    ).apply(instance, CampaignStructureProcessor::new));
    private static final DeferredRegister<StructureProcessorType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, VillagerRetaliation.MOD_ID);
    private static final Supplier<StructureProcessorType<CampaignStructureProcessor>> TYPE =
            TYPES.register("campaign_owned_blocks", () -> () -> CODEC);

    private final ResourceLocation layout;
    private final int revision;
    private final BlockPos anchor;
    private final List<Cell> cells;

    private CampaignStructureProcessor(ResourceLocation layout, int revision, BlockPos anchor, List<Cell> cells) {
        this.layout = layout;
        this.revision = revision;
        this.anchor = anchor.immutable();
        this.cells = List.copyOf(cells);
    }

    static void register(IEventBus bus) { TYPES.register(bus); }
    @Override protected StructureProcessorType<?> getType() { return TYPE.get(); }

    @Override
    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(ServerLevelAccessor serverLevel, BlockPos offset, BlockPos pos,
            List<StructureTemplate.StructureBlockInfo> original, List<StructureTemplate.StructureBlockInfo> processed, StructurePlaceSettings settings) {
        if (!(serverLevel instanceof WorldGenRegion region)) return processed;
        BlockPos origin = StructureTemplate.calculateRelativePosition(settings, anchor).offset(offset);
        var eligible = processed.stream().filter(block -> writable(region, block.pos(), settings)).toList();
        Map<BlockPos, StructureTemplate.StructureBlockInfo> blocks = eligible.stream()
                .collect(Collectors.toMap(StructureTemplate.StructureBlockInfo::pos, value -> value, (first, last) -> last));
        for (var cell : cells) {
            BlockPos position = StructureTemplate.calculateRelativePosition(settings, cell.position()).offset(offset);
            var block = blocks.get(position);
            if (block == null) continue;
            var chunk = region.getChunk(position);
            var expected = block.state().mirror(settings.getMirror()).rotate(settings.getRotation());
            var candidate = new CampaignGeneratedBlock(layout, region.getLevel().dimension().location(), revision, origin, settings.getRotation(), settings.getMirror(),
                    cell.id(), position, expected, CampaignGeneratedBlock.State.PENDING, "");
            synchronized (chunk) {
                chunk.getData(CampaignGeneratedBlocks.GENERATION).record(candidate);
                chunk.setUnsaved(true);
            }
        }
        return eligible;
    }

    @Override
    public StructureTemplate.StructureEntityInfo processEntity(net.minecraft.world.level.LevelReader world, BlockPos offset,
            StructureTemplate.StructureEntityInfo original, StructureTemplate.StructureEntityInfo processed,
            StructurePlaceSettings settings, StructureTemplate template) {
        return world instanceof WorldGenRegion region && (!writable(region, processed.blockPos, settings)
                || !writable(region, BlockPos.containing(processed.pos), settings)) ? null : processed;
    }

    private static boolean writable(WorldGenRegion region, BlockPos position, StructurePlaceSettings settings) {
        if (settings.getBoundingBox() != null && !settings.getBoundingBox().isInside(position)
                || region.isOutsideBuildHeight(position) || !region.ensureCanWrite(position)) return false;
        var chunk = region.getChunk(position);
        return chunk instanceof ProtoChunk && !(chunk instanceof ImposterProtoChunk) && !chunk.isUpgrading();
    }

    private record Cell(String id, BlockPos position) {
        private static final Codec<Cell> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.validate(id -> id.matches(CampaignOutcomeAction.ID_PATTERN) ? DataResult.success(id) : DataResult.error(() -> "Campaign cell requires a stable ID."))
                        .fieldOf("id").forGetter(Cell::id),
                POSITION.fieldOf("position").forGetter(Cell::position)
        ).apply(instance, Cell::new));
    }
}

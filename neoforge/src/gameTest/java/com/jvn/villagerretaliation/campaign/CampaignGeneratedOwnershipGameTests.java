package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignGeneratedOwnershipGameTests {
    private CampaignGeneratedOwnershipGameTests() { }

    @GameTest(template = "empty", batch = "campaign_generated_ownership")
    public static void processorUsesNativeDataCodecAndRejectsAmbiguousCells(GameTestHelper helper) throws java.io.IOException {
        var raw = definition();
        helper.assertTrue(StructureProcessorType.SINGLE_CODEC.parse(JsonOps.INSTANCE, raw).result().isPresent(), "native processor definition loads");
        for (var change : List.<java.util.function.Consumer<JsonObject>>of(
                value -> value.addProperty("revision", 0),
                value -> value.getAsJsonArray("anchor").set(0, new com.google.gson.JsonPrimitive(33)),
                value -> value.getAsJsonArray("cells").get(0).getAsJsonObject().addProperty("id", "not a stable id"),
                value -> value.getAsJsonArray("cells").add(value.getAsJsonArray("cells").get(0).deepCopy()),
                value -> value.add("cells", new com.google.gson.JsonArray()))) {
            var invalid = raw.deepCopy(); change.accept(invalid);
            helper.assertTrue(StructureProcessorType.SINGLE_CODEC.parse(JsonOps.INSTANCE, invalid).error().isPresent(), "invalid structure ownership fails at the native authoring boundary");
        }
        var fixture = CampaignCharacterTestFixture.resource("worldgen/processor_list/owned_waystation.json");
        helper.assertTrue(StructureProcessorType.DIRECT_CODEC.parse(JsonOps.INSTANCE, fixture).result().isPresent(), "packaged ownership processor uses the native list codec");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_generated_ownership")
    public static void generatedClaimsBecomeOwnershipDuringNativeChunkPromotion(GameTestHelper helper) throws Exception {
        var fixture = new Fixture(helper);
        fixture.place(new StructurePlaceSettings());
        helper.assertValueEqual(CampaignGeneratedBlocks.cells(fixture.center).size(), 3, "processor records its bounded authored cells");
        helper.assertTrue(CampaignGeneratedBlocks.cells(fixture.center).values().stream().allMatch(cell -> cell.state() == CampaignGeneratedBlock.State.PENDING),
                "generation only records intent before template placement is complete");
        var full = fixture.promote(true);
        for (var cell : CampaignGeneratedBlocks.cells(full).values()) {
            helper.assertValueEqual(cell.state(), CampaignGeneratedBlock.State.OWNED, "new chunk establishes ownership before players can edit");
            var receipt = CampaignBlockOwnership.receipt(full, cell.position());
            helper.assertTrue(receipt != null && receipt.owner().equals(cell.site()) && receipt.operation().equals(cell.operation()),
                    "native block ownership uses the generated site and stable cell identity");
            helper.assertValueEqual(receipt.original(), receipt.placed(), "generation captures a baseline without changing terrain");
        }
        var saved = ChunkSerializer.write(helper.getLevel(), full);
        var info = new RegionStorageInfo("generated-campaign-test", helper.getLevel().dimension(), "chunk");
        var restored = ((ImposterProtoChunk) ChunkSerializer.read(helper.getLevel(), helper.getLevel().getPoiManager(), info, full.getPos(), saved)).getWrapped();
        restored.runPostLoad();
        CampaignGeneratedBlocks.onChunkLoad(new ChunkEvent.Load(restored, false));
        helper.assertValueEqual(CampaignGeneratedBlocks.cells(restored), CampaignGeneratedBlocks.cells(full), "native reload preserves generation identity");
        for (var cell : CampaignGeneratedBlocks.cells(restored).values())
            helper.assertValueEqual(CampaignBlockOwnership.receipt(restored, cell.position()), CampaignBlockOwnership.receipt(full, cell.position()),
                    "block and container snapshots survive native reload with the generation claim");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_generated_ownership")
    public static void rotationAndMirroringPreserveAuthoredCellIdentities(GameTestHelper helper) throws Exception {
        var fixture = new Fixture(helper);
        var settings = new StructurePlaceSettings().setRotation(Rotation.CLOCKWISE_90).setMirror(Mirror.FRONT_BACK);
        fixture.place(settings);
        var full = fixture.promote(true);
        for (var cell : CampaignGeneratedBlocks.cells(full).values()) {
            helper.assertValueEqual(cell.rotation(), Rotation.CLOCKWISE_90, "saved site retains its orientation");
            helper.assertValueEqual(cell.mirror(), Mirror.FRONT_BACK, "saved site retains its mirror");
            helper.assertValueEqual(full.getBlockState(cell.position()), cell.expected(), "claimed state matches native rotated placement");
            helper.assertValueEqual(cell.state(), CampaignGeneratedBlock.State.OWNED, "transformed cell is owned");
        }
        helper.assertValueEqual(CampaignGeneratedBlocks.cells(full).size(), 3, "all transformed cells retain identity");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_generated_ownership")
    public static void alreadyExploredChunksCannotAcquirePendingGenerationOwnership(GameTestHelper helper) throws Exception {
        var fixture = new Fixture(helper);
        fixture.place(new StructurePlaceSettings());
        var full = fixture.promote(false);
        helper.assertTrue(CampaignGeneratedBlocks.cells(full).values().stream().allMatch(cell -> cell.state() == CampaignGeneratedBlock.State.CONFLICT),
                "pending claims cannot be adopted after first load");
        helper.assertFalse(full.hasData(CampaignBlockOwnership.OWNERSHIP), "explored terrain receives no fabricated block ownership");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_generated_ownership")
    public static void laterGenerationChangesAndOverlappingLayoutsRemainConflicts(GameTestHelper helper) throws Exception {
        var fixture = new Fixture(helper);
        fixture.place(new StructurePlaceSettings());
        var cells = CampaignGeneratedBlocks.cells(fixture.center).values().stream().toList();
        var changed = cells.getFirst();
        fixture.center.setBlockState(changed.position(), Blocks.DIAMOND_BLOCK.defaultBlockState(), false);
        var overlap = cells.get(1);
        fixture.center.getData(CampaignGeneratedBlocks.GENERATION).record(new CampaignGeneratedBlock(
                net.minecraft.resources.ResourceLocation.parse("campaign_fixture:competing"), overlap.dimension(), overlap.revision(), overlap.origin(),
                overlap.rotation(), overlap.mirror(), overlap.cell(), overlap.position(), overlap.expected(), CampaignGeneratedBlock.State.PENDING, ""));
        var full = fixture.promote(true);
        helper.assertValueEqual(CampaignGeneratedBlocks.cells(full).get(changed.position()).state(), CampaignGeneratedBlock.State.CONFLICT, "changed generation is diagnosed");
        helper.assertValueEqual(CampaignGeneratedBlocks.cells(full).get(overlap.position()).state(), CampaignGeneratedBlock.State.CONFLICT, "overlapping layouts cannot claim the same position");
        helper.assertTrue(CampaignBlockOwnership.receipt(full, changed.position()) == null && CampaignBlockOwnership.receipt(full, overlap.position()) == null,
                "conflicts never gain native ownership");
        helper.assertTrue(full.getBlockState(changed.position()).is(Blocks.DIAMOND_BLOCK), "ownership processing preserves conflicting terrain");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_generated_ownership")
    public static void playerReplacementAfterGenerationCannotBeReclaimedOnReload(GameTestHelper helper) throws Exception {
        var fixture = new Fixture(helper);
        fixture.place(new StructurePlaceSettings());
        var full = fixture.promote(true);
        var cell = CampaignGeneratedBlocks.cells(full).values().stream().filter(value -> value.cell().equals("glass")).findFirst().orElseThrow();
        full.setBlockState(cell.position(), Blocks.AIR.defaultBlockState(), false);
        full.setBlockState(cell.position(), cell.expected(), false);
        CampaignGeneratedBlocks.onChunkLoad(new ChunkEvent.Load(full, false));
        helper.assertValueEqual(CampaignBlockOwnership.receipt(full, cell.position()).state(), CampaignBlockOwnershipLedger.State.CONFLICT,
                "reloading a generated site cannot restore ownership lost to player edits");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_generated_ownership")
    public static void generationProcessorLeavesExploredNeighborBlocksAndEntitiesUntouched(GameTestHelper helper) throws Exception {
        var fixture = new Fixture(helper);
        var neighborPosition = new ChunkPos(fixture.center.getPos().x + 1, fixture.center.getPos().z);
        var neighbor = new LevelChunk(helper.getLevel(), (ProtoChunk) fixture.chunks.get(neighborPosition), null);
        fixture.chunks.put(neighborPosition, new ImposterProtoChunk(neighbor, true));
        var target = new BlockPos(neighborPosition.getMinBlockX(), fixture.origin.getY(), fixture.origin.getZ());
        neighbor.setBlockState(target, Blocks.DIAMOND_BLOCK.defaultBlockState(), false);
        var origin = new BlockPos(target.getX() - 1, target.getY(), target.getZ());
        fixture.place(origin, new StructurePlaceSettings());
        helper.assertTrue(neighbor.getBlockState(target).is(Blocks.DIAMOND_BLOCK), "structure generation cannot replace explored neighboring terrain");
        helper.assertFalse(neighbor.hasData(CampaignGeneratedBlocks.GENERATION), "explored neighbor acquires no new claim");
        var processor = processor();
        var entity = new StructureTemplate.StructureEntityInfo(net.minecraft.world.phys.Vec3.atCenterOf(target), target, new CompoundTag());
        helper.assertTrue(processor.processEntity(fixture.region, origin, entity, entity, new StructurePlaceSettings(), fixture.template()) == null,
                "generation cannot introduce structure entities into an explored neighbor");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_generated_ownership")
    public static void manualPlacementAndUnreadableGenerationDataCannotMintOwnership(GameTestHelper helper) throws Exception {
        var fixture = new Fixture(helper);
        var processor = processor();
        var blocks = List.of(new StructureTemplate.StructureBlockInfo(fixture.origin, Blocks.GLASS.defaultBlockState(), null));
        helper.assertValueEqual(processor.finalizeProcessing(helper.getLevel(), fixture.origin, fixture.origin, blocks, blocks, new StructurePlaceSettings()), blocks,
                "manual placement retains its ordinary behavior");
        helper.assertFalse(helper.getLevel().getChunkAt(fixture.origin).hasData(CampaignGeneratedBlocks.GENERATION), "manual placement creates no generation proof");
        fixture.place(new StructurePlaceSettings());
        var raw = fixture.center.getData(CampaignGeneratedBlocks.GENERATION).save(); raw.putInt("DataVersion", 99);
        fixture.center.setData(CampaignGeneratedBlocks.GENERATION, CampaignGenerationLedger.load(raw));
        var full = fixture.promote(true);
        helper.assertFalse(CampaignGeneratedBlocks.diagnostic(full).isBlank(), "future generation data remains unavailable");
        helper.assertValueEqual(full.getData(CampaignGeneratedBlocks.GENERATION).save(), raw, "future generation data is preserved exactly");
        helper.assertFalse(full.hasData(CampaignBlockOwnership.OWNERSHIP), "unreadable proof does not create ownership");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_generated_ownership")
    public static void packagedWaystationGeneratesItsOwnedCellsBeforePlayerAccess(GameTestHelper helper) throws Exception {
        var fixture = new Fixture(helper);
        var settings = new StructurePlaceSettings().setKnownShape(true).setIgnoreEntities(false);
        var processors = StructureProcessorType.DIRECT_CODEC.parse(JsonOps.INSTANCE,
                CampaignCharacterTestFixture.resource("worldgen/processor_list/owned_waystation.json")).getOrThrow();
        processors.list().forEach(settings::addProcessor);
        var template = new StructureTemplate();
        try (var stream = CampaignGeneratedOwnershipGameTests.class.getResourceAsStream("/campaign_fixture/data/campaign_fixture/structure/waystation.nbt")) {
            if (stream == null) throw new java.io.IOException("Missing optional waystation template");
            template.load(BuiltInRegistries.BLOCK.asLookup(), net.minecraft.nbt.NbtIo.readCompressed(stream, net.minecraft.nbt.NbtAccounter.unlimitedHeap()));
        }
        helper.assertTrue(template.placeInWorld(fixture.region, fixture.origin, fixture.origin, settings, RandomSource.create(7L), 3),
                "packaged waystation places through native world generation");
        var full = fixture.promote(true);
        full.postProcessGeneration();
        var cells = CampaignGeneratedBlocks.cells(full).values();
        helper.assertValueEqual(cells.stream().map(CampaignGeneratedBlock::cell).collect(java.util.stream.Collectors.toSet()), java.util.Set.of("floor", "entry"),
                "packaged processor selects the authored floor and doorway");
        for (var cell : cells) {
            helper.assertValueEqual(cell.origin(), fixture.origin.offset(2, 1, 2), "ownership is anchored at the persistent destination marker");
            helper.assertValueEqual(cell.state(), CampaignGeneratedBlock.State.OWNED, "packaged cells acquire generation ownership");
            helper.assertValueEqual(CampaignBlockOwnership.receipt(full, cell.position()).state(), CampaignBlockOwnershipLedger.State.APPLIED,
                    "native postprocessing retains the packaged baseline");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_generated_ownership")
    public static void malformedGenerationReceiptsRetainTheirOriginalEvidence(GameTestHelper helper) throws Exception {
        var fixture = new Fixture(helper);
        fixture.place(new StructurePlaceSettings());
        var original = fixture.center.getData(CampaignGeneratedBlocks.GENERATION).save();
        var extra = original.copy(); extra.putString("FutureField", "retain");
        var invalidPosition = original.copy(); invalidPosition.getList("Cells", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).putString("Position", "unreadable");
        var duplicate = original.copy();
        duplicate.getList("Cells", net.minecraft.nbt.Tag.TAG_COMPOUND).add(duplicate.getList("Cells", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).copy());
        var unknownBlock = original.copy();
        unknownBlock.getList("Cells", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).getCompound("Expected").putString("Name", "missing:block");
        for (var raw : List.of(extra, invalidPosition, duplicate, unknownBlock)) {
            var recovered = CampaignGenerationLedger.load(raw);
            helper.assertFalse(recovered.diagnostic().isBlank(), "malformed generation proof is diagnosed");
            helper.assertValueEqual(recovered.save(), raw, "malformed proof is preserved exactly");
            recovered.record(CampaignGeneratedBlocks.cells(fixture.center).values().iterator().next());
            helper.assertValueEqual(recovered.save(), raw, "later generation cannot overwrite unreadable evidence");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_generated_ownership")
    public static void concurrentProcessorsRetainEveryCellAndCoherentSaveSnapshots(GameTestHelper helper) {
        var fixture = new Fixture(helper);
        var blocks = java.util.stream.IntStream.range(0, 4).mapToObj(index ->
                new StructureTemplate.StructureBlockInfo(fixture.origin.offset(index, 0, 0), Blocks.GLASS.defaultBlockState(), null)).toList();
        var start = new java.util.concurrent.CyclicBarrier(4);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var futures = java.util.stream.IntStream.range(0, 4).mapToObj(index -> workers.submit(() -> {
                var raw = definition();
                var cells = new com.google.gson.JsonArray();
                var cell = new JsonObject();
                cell.addProperty("id", "cell_" + index);
                cell.add("position", JsonParser.parseString("[" + index + ",0,0]"));
                cells.add(cell); raw.add("cells", cells);
                var processor = StructureProcessorType.SINGLE_CODEC.parse(JsonOps.INSTANCE, raw).getOrThrow();
                start.await();
                for (int attempt = 0; attempt < 50; attempt++) {
                    processor.finalizeProcessing(fixture.region, fixture.origin, fixture.origin, blocks, blocks, new StructurePlaceSettings());
                    var snapshot = fixture.center.getData(CampaignGeneratedBlocks.GENERATION).save();
                    if (!CampaignGenerationLedger.load(snapshot).diagnostic().isBlank())
                        throw new IllegalStateException("Concurrent generation produced an unreadable ownership snapshot");
                }
                return null;
            })).toList();
            for (var future : futures) {
                try { future.get(); }
                catch (java.util.concurrent.ExecutionException exception) { throw new AssertionError("Concurrent ownership failed", exception.getCause()); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError("Concurrent ownership was interrupted", exception); }
            }
        }
        helper.assertValueEqual(CampaignGeneratedBlocks.cells(fixture.center).values().stream().map(CampaignGeneratedBlock::cell)
                .collect(java.util.stream.Collectors.toSet()), java.util.Set.of("cell_0", "cell_1", "cell_2", "cell_3"),
                "simultaneous processors retain every stable cell in one attachment");
        helper.succeed();
    }

    private static JsonObject definition() {
        return JsonParser.parseString("""
                {"processor_type":"villagerretaliation:campaign_owned_blocks","layout":"campaign_fixture:owned_test","revision":1,
                 "anchor":[1,0,1],"cells":[{"id":"stairs","position":[0,0,0]},{"id":"glass","position":[1,0,0]},{"id":"chest","position":[2,0,0]}]}
                """).getAsJsonObject();
    }

    private static StructureProcessor processor() { return StructureProcessorType.SINGLE_CODEC.parse(JsonOps.INSTANCE, definition()).getOrThrow(); }

    private static final class Fixture {
        final GameTestHelper helper;
        final Map<ChunkPos, ChunkAccess> chunks = new HashMap<>();
        final ProtoChunk center;
        final BlockPos origin;
        final WorldGenRegion region;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            var centerPosition = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
            var cache = StaticCache2D.<GenerationChunkHolder>create(centerPosition.x, centerPosition.z, 2, (x, z) -> {
                var position = new ChunkPos(x, z);
                chunks.put(position, new ProtoChunk(position, UpgradeData.EMPTY, helper.getLevel(),
                        helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME), null));
                return new GenerationChunkHolder(position) {
                    @Override public int getTicketLevel() { return 0; }
                    @Override public int getQueueLevel() { return 0; }
                    @Override public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) { return chunks.get(position); }
                };
            });
            center = (ProtoChunk) chunks.get(centerPosition);
            origin = new BlockPos(centerPosition.getMinBlockX() + 5, helper.getLevel().getMinBuildHeight() + 4, centerPosition.getMinBlockZ() + 5);
            region = new WorldGenRegion(helper.getLevel(), cache, ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES), center);
        }

        StructureTemplate template() throws Exception {
            var template = new StructureTemplate();
            template.load(BuiltInRegistries.BLOCK.asLookup(), TagParser.parseTag("""
                    {size:[3,1,1],palette:[{Name:"minecraft:oak_stairs",Properties:{facing:"east",half:"bottom",shape:"straight",waterlogged:"false"}},
                     {Name:"minecraft:glass"},{Name:"minecraft:chest",Properties:{facing:"north",type:"single",waterlogged:"false"}}],
                     blocks:[{pos:[0,0,0],state:0},{pos:[1,0,0],state:1},{pos:[2,0,0],state:2,nbt:{id:"minecraft:chest",Items:[]}}],entities:[]}
                    """));
            return template;
        }

        void place(StructurePlaceSettings settings) throws Exception { place(origin, settings); }

        void place(BlockPos at, StructurePlaceSettings settings) throws Exception {
            settings.addProcessor(processor()).setKnownShape(true).setIgnoreEntities(true);
            helper.assertTrue(template().placeInWorld(region, at, at, settings, RandomSource.create(13L), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS),
                    "native structure template places in the generation region");
        }

        LevelChunk promote(boolean fresh) {
            var chunk = new LevelChunk(helper.getLevel(), center, null);
            CampaignGeneratedBlocks.onChunkLoad(new ChunkEvent.Load(chunk, fresh));
            return chunk;
        }
    }
}

package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasurePieces;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("village_admission")
@PrefixGameTestTemplate(false)
public final class AutonomousVillageAdmissionGameTests {
    @GameTest(template = "empty")
    public static void generatedVillageAdmitsOneResidentAndPreservesExistingState(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            f.generatedAt(f.position);
            helper.assertValueEqual(f.inspect(), AutonomousVillageAdmission.Result.ELIGIBLE, "one generated resident qualifies");
            var village = f.registry.canonicalRecord(f.id).orElseThrow();
            var town = f.data.establishAutonomous(f.level, village).orElseThrow();
            helper.assertValueEqual(town.id(), f.id, "identity retained");
            helper.assertValueEqual(town.autonomous().sections, village.footprintSections(), "exact footprint retained");
            helper.assertTrue(town.economy().balance().isZero(), "no free funds");
            helper.assertTrue(f.data.establishAutonomous(f.level, village).isEmpty(), "repeat admission is idempotent");
            f.generatedAt(f.position.offset(128, 0, 0));
            f.resident.discard();
            helper.assertValueEqual(f.inspect(), AutonomousVillageAdmission.Result.EXISTING_SETTLEMENT, "existing towns are grandfathered");
            var restored = SettlementSavedData.load(f.data.save(new CompoundTag(), f.level.registryAccess()), f.level.registryAccess());
            helper.assertTrue(restored.get(f.id).orElseThrow().autonomous() != null, "saved autonomous town remains autonomous without evidence");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void relocatedAndPlayerCreatedCommunitiesRequireManualEstablishment(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            helper.assertValueEqual(f.inspect(), AutonomousVillageAdmission.Result.NO_GENERATED_VILLAGE, "POI village alone is insufficient");
            helper.assertTrue(f.data.establishAutonomous(f.level, f.registry.canonicalRecord(f.id).orElseThrow()).isEmpty(),
                    "creation cannot bypass generated evidence");
            f.generatedAt(f.position.offset(32, 0, 0));
            helper.assertValueEqual(f.inspect(), AutonomousVillageAdmission.Result.NO_GENERATED_VILLAGE, "nearby generated structure is insufficient");
            f.generatedAt(f.position);
            f.resident.setPos(f.position.offset(256, 0, 0).getBottomCenter());
            helper.assertValueEqual(f.inspect(), AutonomousVillageAdmission.Result.NO_LOCAL_RESIDENT, "travelling resident cannot admit old home");
            f.resident.setPos(f.position.getBottomCenter());
            var other = f.registry.create(0, f.level.dimension().location(), f.position, "");
            VillageAllegianceApi.assignKnown(f.level, f.resident, other, AllegianceAssignmentSource.ADMIN);
            helper.assertValueEqual(f.inspect(), AutonomousVillageAdmission.Result.NO_LOCAL_RESIDENT, "foreign visitor cannot admit village");
            var manualPosition = f.position.offset(512, 0, 0);
            var manualId = f.registry.create(0, f.level.dimension().location(), manualPosition, "");
            var manual = f.data.establish(f.registry, manualId, UUID.randomUUID(), manualPosition, 0).orElseThrow();
            helper.assertTrue(manual.autonomous() == null, "manual establishment remains available without generated structures");
            f.registry.releaseSettlement(manual.id());
            f.registry.removeVillage(manual.id());
            f.registry.removeVillage(other);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unloadedReferencesAndWrongStructuresNeverGrantAdmissionOrLoadChunks(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var chunk = f.level.getChunkAt(f.position);
            var remote = new ChunkPos(chunk.getPos().x + 4096, chunk.getPos().z + 4096);
            chunk.addReferenceForStructure(f.structure, remote.toLong());
            int before = f.level.getChunkSource().getLoadedChunksCount();
            helper.assertValueEqual(f.inspect(), AutonomousVillageAdmission.Result.EVIDENCE_UNLOADED, "unloaded reference is deferred");
            helper.assertValueEqual(f.level.getChunkSource().getLoadedChunksCount(), before, "inspection never loads chunks");
            helper.assertTrue(f.level.getChunkSource().getChunkNow(remote.x, remote.z) == null, "reference remains unloaded");
            chunk.getAllReferences().get(f.structure).remove(remote.toLong());
            var wrong = f.level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                    .get(ResourceLocation.withDefaultNamespace("buried_treasure"));
            var previous = chunk.getStartForStructure(wrong);
            try {
                chunk.setStartForStructure(wrong, new StructureStart(wrong, chunk.getPos(), 0,
                        new PiecesContainer(List.of(new BuriedTreasurePieces.BuriedTreasurePiece(f.position)))));
                helper.assertValueEqual(f.inspect(), AutonomousVillageAdmission.Result.NO_GENERATED_VILLAGE, "unrelated structure is insufficient");
            } finally {
                chunk.setStartForStructure(wrong, previous == null ? StructureStart.INVALID_START : previous);
            }
            f.generatedAt(f.position);
            helper.assertValueEqual(f.inspect(), AutonomousVillageAdmission.Result.ELIGIBLE, "retry succeeds when actual evidence is available");
        }
        helper.succeed();
    }

    static final class Fixture implements AutoCloseable {
        final ServerLevel level;
        final VillageAllegianceRegistrySavedData registry;
        final SettlementSavedData data;
        final BlockPos position;
        final VillageAllegianceId id;
        final Villager resident;
        final Structure structure;
        private final SettlementSavedData previousData;
        private final VillageAllegianceRegistrySavedData previousRegistry;
        private final StructureStart previousStart;
        private final BlockState previousBlock;
        private final BlockState previousSupport;

        Fixture(GameTestHelper helper) {
            this.level = helper.getLevel();
            this.position = helper.absolutePos(new BlockPos(2, 2, 2));
            this.previousRegistry = VillageAllegianceRegistrySavedData.get(this.level);
            this.registry = new VillageAllegianceRegistrySavedData();
            this.level.getServer().overworld().getDataStorage().set("villagerretaliation_village_allegiances", this.registry);
            this.previousData = SettlementSavedData.get(this.level);
            this.data = new SettlementSavedData();
            this.level.getServer().overworld().getDataStorage().set("villagerretaliation_settlements", this.data);
            var chunk = this.level.getChunkAt(this.position);
            this.structure = this.level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                    .get(ResourceLocation.withDefaultNamespace("village_plains"));
            this.previousStart = chunk.getStartForStructure(this.structure);
            this.previousBlock = this.level.getBlockState(this.position);
            this.previousSupport = this.level.getBlockState(this.position.below());
            this.level.setBlock(this.position.below(), Blocks.STONE.defaultBlockState(), 2);
            this.level.setBlock(this.position, Blocks.RED_BED.defaultBlockState()
                    .setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD), 2);
            var manager = this.level.getPoiManager();
            manager.add(this.position, this.level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE)
                    .getHolderOrThrow(PoiTypes.HOME));
            helper.assertTrue(manager.take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(this.position),
                    this.position, 1).isPresent(), "occupied source bed");
            this.id = this.registry.discoverAt(this.level, this.position).orElseThrow();
            this.resident = EntityType.VILLAGER.create(this.level);
            this.resident.setPos(this.position.getBottomCenter());
            this.resident.setNoAi(true);
            VillageAllegianceApi.assignKnown(this.level, this.resident, this.id, AllegianceAssignmentSource.NATURAL_SPAWN);
            this.level.addFreshEntity(this.resident);
        }

        void generatedAt(BlockPos anchor) {
            var chunk = this.level.getChunkAt(this.position);
            chunk.setStartForStructure(this.structure, new StructureStart(this.structure, chunk.getPos(), 0,
                    new PiecesContainer(List.of(new BuriedTreasurePieces.BuriedTreasurePiece(anchor)))));
        }

        AutonomousVillageAdmission.Result inspect() {
            return AutonomousVillageAdmission.inspect(this.level, this.id);
        }

        @Override
        public void close() {
            this.resident.discard();
            this.level.getChunkAt(this.position).setStartForStructure(this.structure,
                    this.previousStart == null ? StructureStart.INVALID_START : this.previousStart);
            this.level.getPoiManager().remove(this.position);
            this.level.setBlock(this.position, this.previousBlock, 2);
            this.level.setBlock(this.position.below(), this.previousSupport, 2);
            this.registry.releaseSettlement(this.id);
            this.registry.removeVillage(this.id);
            this.level.getServer().overworld().getDataStorage().set("villagerretaliation_settlements", this.previousData);
            this.level.getServer().overworld().getDataStorage().set("villagerretaliation_village_allegiances", this.previousRegistry);
        }
    }
}

package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignDefinitionParser;
import com.jvn.villagerretaliation.campaign.content.CampaignLocationDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignSchema;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignStructureMarkerGameTests {
    private static final ResourceLocation MARKER = ResourceLocation.parse("campaign_fixture:waystation_entry");
    private static final ResourceLocation STRUCTURE = ResourceLocation.parse("minecraft:desert_pyramid");

    private CampaignStructureMarkerGameTests() { }

    @GameTest(template = "empty", batch = "campaign_structure_markers")
    public static void structureMarkersRequireARegisteredStructureSource(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignCharacterTestFixture(helper, "structures", "structure_greeting")) {
            var raw = fixture.raw();
            var location = raw.getAsJsonArray("locations").get(0).getAsJsonObject();
            location.addProperty("structure_marker", MARKER.toString());
            helper.assertFalse(CampaignSchema.validate(fixture.campaignId, raw).isEmpty(), "coordinate markers cannot silently turn into entity searches");
            location.addProperty("source", "village");
            helper.assertFalse(CampaignDefinitionParser.parse(fixture.campaignId, raw).valid(), "village locations cannot search for structure markers");
            location.addProperty("source", "structure"); location.addProperty("structure", STRUCTURE.toString());
            helper.assertTrue(CampaignDefinitionParser.parse(fixture.campaignId, raw).valid(), "registered structure marker definition compiles");
            for (String value : List.of("missing namespace", "#campaign_fixture:waystation", "")) {
                location.addProperty("structure_marker", value);
                helper.assertFalse(CampaignDefinitionParser.parse(fixture.campaignId, raw).valid(), "marker tag needs a stable namespaced identity");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_structure_markers")
    public static void bindingUsesTheTaggedMarkerInsteadOfTheStructureCenter(GameTestHelper helper) {
        try (var fixture = new StructureFixture(helper)) {
            var marker = fixture.marker(fixture.origin.offset(1, 1, 1), MARKER.toString());
            helper.getLevel().setBlockAndUpdate(fixture.origin, Blocks.GOLD_BLOCK.defaultBlockState());
            int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
            var found = CampaignLocationService.find(helper.getLevel(), fixture.declaration(MARKER, 64));
            helper.assertTrue(found != null && found.identity().endsWith("/marker:" + marker.getUUID()), "binding records the exact persistent marker identity");
            helper.assertValueEqual(found.position(), marker.blockPosition(), "destination uses the authored doorway rather than the structure center");
            helper.assertFalse(found.position().equals(fixture.start.getBoundingBox().getCenter()), "fixture distinguishes marker and geometric center");
            helper.assertValueEqual(helper.getLevel().getBlockState(fixture.origin), Blocks.GOLD_BLOCK.defaultBlockState(), "resolution preserves player construction");
            helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "marker lookup never generates or loads chunks");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_structure_markers")
    public static void unrelatedMarkersCannotSubstituteForTheAuthoredDestination(GameTestHelper helper) {
        try (var fixture = new StructureFixture(helper)) {
            var wrongTag = fixture.marker(fixture.origin.offset(1, 1, 1), "campaign_fixture:other");
            var outside = fixture.marker(fixture.origin.offset(-1, 1, -1), MARKER.toString());
            var unrelated = helper.spawn(EntityType.ARMOR_STAND, 2, 1, 2); unrelated.addTag(MARKER.toString());
            var declaration = fixture.declaration(MARKER, 64);
            helper.assertTrue(CampaignLocationService.find(helper.getLevel(), declaration) == null, "tag, entity type and containing structure must all match");
            var correct = fixture.marker(fixture.origin.offset(2, 1, 2), MARKER.toString());
            helper.assertValueEqual(CampaignLocationService.find(helper.getLevel(), declaration).position(), correct.blockPosition(), "only the authored marker is eligible");
            helper.assertTrue(CampaignLocationService.find(helper.getLevel(), fixture.declaration(MARKER, 1)) == null, "matching marker outside the authored radius remains unavailable");
            helper.assertFalse(wrongTag.isRemoved() || outside.isRemoved(), "resolution does not delete unrelated markers");
            unrelated.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_structure_markers")
    public static void unloadedMarkersRemainUnavailableUntilTheirOriginalEntityReturns(GameTestHelper helper) {
        try (var fixture = new StructureFixture(helper)) {
            var marker = fixture.marker(fixture.origin.offset(1, 1, 1), MARKER.toString());
            var id = marker.getUUID();
            var snapshot = marker.saveWithoutId(new CompoundTag());
            marker.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(CampaignLocationService.find(helper.getLevel(), fixture.declaration(MARKER, 64)) == null, "structure center cannot stand in for an unloaded marker");
            var restored = EntityType.MARKER.create(helper.getLevel()); restored.load(snapshot);
            helper.assertTrue(helper.getLevel().addFreshEntity(restored), "marker reloads with its saved UUID and tag");
            fixture.markers.add(restored);
            helper.assertTrue(CampaignLocationService.find(helper.getLevel(), fixture.declaration(MARKER, 64)).identity().endsWith("/marker:" + id), "recovered marker retains its identity");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_structure_markers")
    public static void savedMarkerDestinationsSurviveMovementRemovalAndLedgerReload(GameTestHelper helper) throws IOException {
        try (var world = new StructureFixture(helper); var fixture = new CampaignCharacterTestFixture(helper, "structures", "structure_greeting")) {
            var marker = world.marker(world.origin.offset(1, 1, 1), MARKER.toString());
            var raw = fixture.raw();
            var location = raw.getAsJsonArray("locations").get(0).getAsJsonObject();
            location.addProperty("source", "structure"); location.addProperty("structure", STRUCTURE.toString());
            location.addProperty("structure_marker", MARKER.toString()); location.addProperty("search_radius", 64);
            fixture.install(raw);
            var first = CampaignLocationService.resolve(fixture.server, fixture.campaignId, "home");
            helper.assertTrue(first.available(), first.diagnostic());
            var boundPosition = marker.blockPosition();
            marker.setPos(marker.getX() + 8, marker.getY(), marker.getZ());
            marker.discard();
            world.marker(world.origin.offset(2, 1, 1), MARKER.toString());
            var storage = fixture.server.overworld().getDataStorage();
            storage.set(CampaignLocationSavedData.DATA_NAME, CampaignLocationSavedData.load(fixture.disk.read(CampaignLocationSavedData.DATA_NAME), fixture.disk.root));
            var restored = CampaignLocationService.resolve(fixture.server, fixture.campaignId, "home");
            helper.assertValueEqual(restored.binding(), first.binding(), "saved destination is never moved or rebound to another marker");
            helper.assertValueEqual(restored.binding().position(), boundPosition, "original doorway remains the shared destination");
            helper.getLevel().setBlockAndUpdate(boundPosition, Blocks.DIAMOND_BLOCK.defaultBlockState());
            CampaignLocationService.resolve(fixture.server, fixture.campaignId, "home");
            helper.assertValueEqual(helper.getLevel().getBlockState(boundPosition), Blocks.DIAMOND_BLOCK.defaultBlockState(), "repeated lookup does not regenerate a structure over player edits");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_structure_markers")
    public static void optionalWorldgenFixtureUsesNativeCodecsAndAValidPersistentMarkerTemplate(GameTestHelper helper) throws IOException {
        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        var pool = CampaignCharacterTestFixture.resource("worldgen/template_pool/waystation.json");
        var element = pool.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("element");
        helper.assertValueEqual(element.get("processors").getAsString(), "campaign_fixture:owned_waystation", "pool references its authored ownership processor");
        element.add("processors", CampaignCharacterTestFixture.resource("worldgen/processor_list/owned_waystation.json"));
        helper.assertTrue(StructureTemplatePool.DIRECT_CODEC.parse(ops, pool).result().isPresent(), "optional template pool uses the native worldgen codec");
        var structure = CampaignCharacterTestFixture.resource("worldgen/structure/waystation.json");
        helper.assertValueEqual(structure.get("start_pool").getAsString(), "campaign_fixture:waystation", "structure references the packaged pool");
        structure.add("start_pool", pool);
        var parsedStructure = Structure.DIRECT_CODEC.parse(ops, structure);
        helper.assertTrue(parsedStructure.result().isPresent(), "optional structure uses the native worldgen codec: " + parsedStructure.error());
        var set = CampaignCharacterTestFixture.resource("worldgen/structure_set/waystations.json");
        helper.assertValueEqual(set.getAsJsonArray("structures").get(0).getAsJsonObject().get("structure").getAsString(), "campaign_fixture:waystation", "placement references the packaged structure");
        set.getAsJsonArray("structures").get(0).getAsJsonObject().add("structure", structure);
        var parsedSet = StructureSet.DIRECT_CODEC.parse(ops, set);
        helper.assertTrue(parsedSet.result().isPresent(), "optional placement uses the native worldgen codec: " + parsedSet.error());
        var template = new StructureTemplate();
        try (var stream = CampaignStructureMarkerGameTests.class.getResourceAsStream("/campaign_fixture/data/campaign_fixture/structure/waystation.nbt")) {
            if (stream == null) throw new IOException("Missing optional waystation template");
            template.load(BuiltInRegistries.BLOCK.asLookup(), NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap()));
        }
        var origin = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.assertTrue(template.placeInWorld(helper.getLevel(), origin, origin, new StructurePlaceSettings().setIgnoreEntities(false), RandomSource.create(7L), 3), "native template placement creates the fixture");
        var markers = helper.getLevel().getEntitiesOfClass(Marker.class, new AABB(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 5, origin.getY() + 5, origin.getZ() + 5), entity -> entity.getTags().contains(MARKER.toString()));
        try {
            helper.assertValueEqual(markers.size(), 1, "template places one persistent destination marker");
            var marker = markers.getFirst();
            helper.assertValueEqual(marker.blockPosition(), origin.offset(2, 1, 2), "marker is at the usable interior destination");
            var saved = marker.saveWithoutId(new CompoundTag());
            var restored = EntityType.MARKER.create(helper.getLevel()); restored.load(saved);
            helper.assertValueEqual(restored.getUUID(), marker.getUUID(), "native entity save retains marker identity");
            helper.assertTrue(restored.getTags().contains(MARKER.toString()), "native entity save retains the authored marker tag");
        } finally { markers.forEach(Entity::discard); }
        helper.succeed();
    }

    static final class StructureFixture implements AutoCloseable {
        final GameTestHelper helper;
        final BlockPos origin;
        final LevelChunk chunk;
        final Map<Structure, StructureStart> previous;
        final StructureStart start;
        final List<Marker> markers = new ArrayList<>();

        StructureFixture(GameTestHelper helper) {
            this.helper = helper;
            origin = helper.absolutePos(new BlockPos(1, 1, 1));
            chunk = helper.getLevel().getChunkSource().getChunkNow(origin.getX() >> 4, origin.getZ() >> 4);
            previous = Map.copyOf(chunk.getAllStarts());
            var structure = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE).get(STRUCTURE);
            var piece = new DesertPyramidPiece(RandomSource.create(1L), origin.getX(), origin.getZ());
            piece.move(0, origin.getY() - piece.getBoundingBox().minY(), 0);
            start = new StructureStart(structure, chunk.getPos(), 0, new PiecesContainer(List.of(piece)));
            chunk.setStartForStructure(structure, start);
        }

        CampaignLocationDefinition declaration(ResourceLocation marker, int radius) {
            return new CampaignLocationDefinition("home", "test.title", CampaignLocationDefinition.Source.STRUCTURE,
                    helper.getLevel().dimension().location(), origin, radius, STRUCTURE, marker, "");
        }

        Marker marker(BlockPos position, String tag) {
            var marker = EntityType.MARKER.create(helper.getLevel());
            marker.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
            marker.addTag(tag);
            helper.assertTrue(helper.getLevel().addFreshEntity(marker), "fixture marker enters the world");
            markers.add(marker);
            return marker;
        }

        @Override public void close() {
            markers.forEach(Entity::discard);
            chunk.setAllStarts(previous);
        }
    }
}

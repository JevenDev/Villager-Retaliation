package com.jvn.villagerretaliation.party;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.*;
import java.util.*;

@GameTestHolder("companion_ambient")
@PrefixGameTestTemplate(false)
public final class CompanionAmbientGameTests {
    @GameTest(template="empty",batch="companion_ambient_start")
    public static void loadedStartInspectionRevalidatesGeometryAndIdentity(GameTestHelper h) {
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,5));
        for(int x=(origin.getX()>>4)-2;x<=(origin.getX()>>4)+2;x++)for(int z=(origin.getZ()>>4)-2;z<=(origin.getZ()>>4)+2;z++)level.getChunk(x,z);
        var bounds=new BoundingBox(origin.getX(),origin.getY(),origin.getZ(),origin.getX()+4,origin.getY()+4,origin.getZ());
        var id=ResourceLocation.withDefaultNamespace("stronghold");
        var structure=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).get(id);
        var chunk=level.getChunkAt(origin);var prior=chunk.getAllStarts().get(structure);
        var start=new net.minecraft.world.level.levelgen.structure.StructureStart(structure,chunk.getPos(),0,
                new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(List.of(new ProofPiece(bounds))));
        chunk.setStartForStructure(structure,start);
        var v=h.spawn(EntityType.VILLAGER,4,3,1);v.setNoAi(true);v.setNoGravity(true);
        for(var cell:CompanionStructurePerception.shapes().get("stronghold").cells())level.setBlock(origin.offset(cell.x(),cell.y(),cell.z()),Blocks.STONE_BRICKS.defaultBlockState(),2);
        try {
            h.assertTrue(CompanionStructurePerception.starts(level,v.blockPosition()).stream().anyMatch(e -> e.getKey().equals(id) && e.getValue()==start),"loaded reference traversal finds the installed real start identity");
            var samples=CompanionStructurePerception.geometry(level,origin,"stronghold",0,bounds);
            h.assertValueEqual(samples.size(),16,"test architecture complete");
            h.assertTrue(samples.stream().allMatch(p -> CompanionStructurePerception.visible(level,v,p,48)),"fixture observer sees all diagnostic geometry");
            CompanionStructurePerception.Evidence evidence=null;
            for(int cursor=0;cursor<128 && evidence==null;cursor++)evidence=CompanionStructurePerception.inspect(level,v,id,start,cursor).orElse(null);
            h.assertTrue(evidence!=null,"bounded scans eventually perceive exposed architecture");
            h.assertTrue(CompanionStructurePerception.fresh(level,v,evidence),"current start and exposed architecture validate");
            level.setBlock(evidence.samples().getFirst(),Blocks.AIR.defaultBlockState(),2);
            h.assertFalse(CompanionStructurePerception.fresh(level,v,evidence),"queued evidence cancels on demolition");
            level.setBlock(evidence.samples().getFirst(),Blocks.STONE_BRICKS.defaultBlockState(),2);
            for(var p:BlockPos.betweenClosed(origin.offset(-2,-1,-2),origin.offset(6,6,-2)))level.setBlock(p,Blocks.STONE.defaultBlockState(),2);
            h.assertFalse(CompanionStructurePerception.fresh(level,v,evidence),"new intervening wall cancels stale queued evidence");
            h.assertTrue(CompanionStructurePerception.inspect(level,v,ResourceLocation.parse("example:unregistered"),start,0).isEmpty(),"unknown registry identity cannot borrow recognizable vanilla geometry");
        } finally {
            chunk.setStartForStructure(structure,prior==null?net.minecraft.world.level.levelgen.structure.StructureStart.INVALID_START:prior);
        }
        h.succeed();
    }
    private static final class ProofPiece extends net.minecraft.world.level.levelgen.structure.StructurePiece {
        ProofPiece(BoundingBox bounds){super(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType.STRONGHOLD_STRAIGHT,0,bounds);}
        @Override protected void addAdditionalSaveData(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext c,CompoundTag tag) { }
        @Override public void postProcess(net.minecraft.world.level.WorldGenLevel level,net.minecraft.world.level.StructureManager manager,net.minecraft.world.level.chunk.ChunkGenerator generator,net.minecraft.util.RandomSource random,BoundingBox box,net.minecraft.world.level.ChunkPos chunk,BlockPos position) { }
    }
    @GameTest(template="empty",batch="companion_ambient_vanilla",timeoutTicks=400)
    public static void vanillaTemplatesContainTheRecognizedArchitecture(GameTestHelper h) {
        var fixtures=List.of(
                new Vanilla("igloo","igloo/top",new BlockPos(2,0,0),0),
                new Vanilla("pillager_outpost","pillager_outpost/watchtower",new BlockPos(4,6,3),0),
                new Vanilla("woodland_mansion","woodland_mansion/wall_window",BlockPos.ZERO,1),
                new Vanilla("ruined_portal","ruined_portal/portal_1",new BlockPos(3,3,1),1),
                new Vanilla("shipwreck","shipwreck/rightsideup_fronthalf",new BlockPos(0,2,5),0),
                new Vanilla("ocean_ruins","underwater_ruin/warm_1",new BlockPos(2,1,0),0),
                new Vanilla("end_city","end_city/base_floor",new BlockPos(1,0,1),0),
                new Vanilla("nether_fossil","nether_fossils/fossil_1",BlockPos.ZERO,0),
                new Vanilla("ancient_city","ancient_city/city_center/city_center_1",new BlockPos(13,17,10),0),
                new Vanilla("trial_chambers","trial_chambers/corridor/addon/wall",BlockPos.ZERO,0),
                new Vanilla("trail_ruins","trail_ruins/tower/tower_1",new BlockPos(0,2,0),0),
                new Vanilla("bastion_remnant","bastion/bridge/starting_pieces/entrance_face",new BlockPos(4,4,8),0),
                new Vanilla("village","village/plains/houses/plains_small_house_1",new BlockPos(1,1,1),0));
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(0,160,0));
        for(int x=(origin.getX()>>4)-3;x<=(origin.getX()>>4)+5;x++)for(int z=(origin.getZ()>>4)-3;z<=(origin.getZ()>>4)+5;z++)level.getChunk(x,z);
        var bounds=new BoundingBox(origin.getX()-1,origin.getY()-1,origin.getZ()-1,origin.getX()+50,origin.getY()+40,origin.getZ()+50);
        var hidden=new ArrayList<String>();
        for(var fixture:fixtures) {
            var template=level.getStructureManager().get(ResourceLocation.withDefaultNamespace(fixture.template())).orElseThrow();
            h.assertTrue(template.placeInWorld(level,origin,origin,new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setIgnoreEntities(true),level.random,2),"real vanilla template placed "+fixture.template());
            var samples=CompanionStructurePerception.geometry(level,origin.offset(fixture.offset()),fixture.recognizer(),fixture.rotation(),bounds);
            h.assertFalse(samples.isEmpty(),"current vanilla blocks satisfy reviewed geometry: "+fixture.recognizer());
            var observer=h.spawn(EntityType.VILLAGER,1,3,1);observer.setNoAi(true);observer.setNoGravity(true);
            boolean exposed=false;
            var center=new Vec3(samples.stream().mapToDouble(p -> p.getX()+.5).average().orElseThrow(),samples.stream().mapToDouble(p -> p.getY()+.5).average().orElseThrow(),samples.stream().mapToDouble(p -> p.getZ()+.5).average().orElseThrow());
            outer: for(int distance:new int[]{4,8,16,24})for(int height:new int[]{-4,-2,0,2,6,12})for(int angle=0;angle<8;angle++) {
                observer.setPos(center.x+Math.cos(angle*Math.PI/4)*distance,center.y+height,center.z+Math.sin(angle*Math.PI/4)*distance);
                int count=(int)samples.stream().filter(sample -> CompanionStructurePerception.visible(level,observer,sample,48)).count();
                if(count==samples.size()) {exposed=true;break outer;}
            }
            observer.discard();
            if(!exposed)hidden.add(fixture.recognizer());
            level.setBlock(samples.getFirst(),Blocks.AIR.defaultBlockState(),2);
            h.assertTrue(CompanionStructurePerception.geometry(level,origin.offset(fixture.offset()),fixture.recognizer(),fixture.rotation(),bounds).isEmpty(),"demolition invalidates vanilla evidence "+fixture.recognizer());
            for(var pos:BlockPos.betweenClosed(origin,origin.offset(template.getSize()).offset(1,1,1)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
        }
        h.assertTrue(hidden.isEmpty(),"actual vanilla architecture has a coherent visible viewpoint: "+hidden);
        h.succeed();
    }
    private record Vanilla(String recognizer,String template,BlockPos offset,int rotation) { }

    @GameTest(template="empty",batch="companion_ambient_home",timeoutTicks=400)
    public static void homecomingRequiresRealSharedAbsenceAndConsumesFarmerStoryEncounter(GameTestHelper h) throws Exception {
        var level=h.getLevel();var p=h.makeMockServerPlayerInLevel();var v=h.spawn(EntityType.VILLAGER,2,3,2);v.setNoAi(true);v.setNoGravity(true);
        var home=h.absolutePos(new BlockPos(2,3,2));
        for(var pos:BlockPos.betweenClosed(home.offset(-4,-1,-4),home.offset(4,-1,4)))level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
        level.setBlock(home,Blocks.RED_BED.defaultBlockState(),2);
        v.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME,GlobalPos.of(level.dimension(),home));
        v.setPos(home.getX()+2.5,home.getY(),home.getZ()+.5);p.setPos(v.getX()+1,v.getY(),v.getZ());
        var state=CompanionSavedData.get(level).getOrCreate(v.getUUID());state.putUUID("Owner",p.getUUID());state.putBoolean("HomeBed",true);
        state.putString("HomeDimension",level.dimension().location().toString());state.putLong("HomePosition",home.asLong());state.putString("OriginProfession","minecraft:farmer");
        CompanionRelationshipService.experience(level,v.getUUID(),p.getUUID(),"villagerretaliation:returned_home",2);
        var data=PartySavedData.get(level);var party=data.createParty(p.getUUID(),level.getGameTime());
        data.addVillager(party,new PartyVillagerRecord(v.getUUID(),p.getUUID(),UUID.randomUUID(),0,PartyCommandMode.FOLLOW,null,null,
                0,24000,1,0,"Companion","farmer",level.dimension().location(),home));
        var root=new CompoundTag();root.putInt("Version",1);root.putUUID("World",UUID.randomUUID());
        var file=java.nio.file.Files.createTempDirectory("vr-home-test-").resolve("ledger.dat");
        var ledger=new CompanionAmbientLedger(file,root);
        try {
            for(int i=0;i<7;i++)homeTick(v,ledger);
            var journey=ledger.section("Journeys").getCompound(v.getUUID().toString());
            h.assertValueEqual(journey.getString("State"),"HOME","registered bed requires settled home presence");
            v.setPos(home.getX()+70,home.getY(),home.getZ());p.setPos(v.getX()+1,v.getY(),v.getZ());homeTick(v,ledger);
            v.setPos(home.getX()+63,home.getY(),home.getZ());p.setPos(v.getX()+1,v.getY(),v.getZ());homeTick(v,ledger);
            h.assertValueEqual(journey.getString("State"),"HOME","brief border excursion resets departure");
            for(int i=0;i<310;i++) {
                v.setPos(home.getX()+70+i,home.getY(),home.getZ());p.setPos(v.getX()+1,v.getY(),v.getZ());homeTick(v,ledger);
            }
            h.assertValueEqual(journey.getString("State"),"AWAY_QUALIFIED","loaded shared walking and five-minute absence qualify");
            v.setPos(home.getX()+2.5,home.getY(),home.getZ()+.5);p.setPos(v.getX()+1,v.getY(),v.getZ());
            for(int i=0;i<6;i++)homeTick(v,ledger);
            h.assertValueEqual(journey.getString("State"),"CONSUMED","first qualified return is consumed after dwell");
            h.assertValueEqual(journey.getString("Reason"),"story_priority","existing farmer homecoming owns the encounter");
            h.assertTrue(ledger.section("Events").isEmpty(),"ambient path neither duplicates story speech nor grants a reward");
            h.assertValueEqual(ledger.section("JourneyHistory").size(),1,"individual journey receipt persists");
            h.assertValueEqual(CompanionAmbientLedger.read(file).getCompound("Journeys").getCompound(v.getUUID().toString()).getString("State"),"CONSUMED","suppression survives actual disk reload");
            h.assertTrue(state.getCompound("GrowthChoices").isEmpty(),"ambient suppression never chooses a story or grants growth");
            state.putString("OriginProfession","minecraft:cartographer");
            for(int i=0;i<7;i++)homeTick(v,ledger);
            for(int i=0;i<310;i++) {
                v.setPos(home.getX()+70+i,home.getY(),home.getZ());p.setPos(v.getX()+1,v.getY(),v.getZ());homeTick(v,ledger);
            }
            v.setPos(home.getX()+2.5,home.getY(),home.getZ()+.5);p.setPos(v.getX()+1,v.getY(),v.getZ());
            for(int i=0;i<6;i++)homeTick(v,ledger);
            h.assertValueEqual(ledger.section("Events").size(),1,"a different qualifying trip without a ready story produces one ambient homecoming");
            for(int i=0;i<12;i++)homeTick(v,ledger);
            h.assertValueEqual(ledger.section("Events").size(),1,"remaining at home cannot replay the encounter");
            h.assertValueEqual(ledger.section("JourneyHistory").size(),2,"both independent journeys retained");
        } finally {data.removeParty(party.id());}
        h.succeed();
    }
    private static void homeTick(net.minecraft.world.entity.npc.Villager v,CompanionAmbientLedger ledger) {
        for(int tick=0;tick<20;tick++)ledger.tick();CompanionAmbientService.home(v,ledger);
    }

    @GameTest(template="empty",batch="companion_ambient")
    public static void sharedTravelCannotCountIdlePlayersTeleportsOrOfflineGaps(GameTestHelper h) {
        var p=h.makeMockServerPlayerInLevel();var trip=new CompoundTag();
        p.setPos(0,100,0);CompanionAmbientService.accumulateParticipant(trip,p,20,false,0);
        p.setPos(2,100,0);CompanionAmbientService.accumulateParticipant(trip,p,40,true,2);
        h.assertValueEqual(trip.getDouble("Travel"),2.0,"shared walking counts");
        CompanionAmbientService.accumulateParticipant(trip,p,60,true,8);
        h.assertValueEqual(trip.getDouble("Travel"),2.0,"companion-only movement does not credit listener");
        p.setPos(200,100,0);CompanionAmbientService.accumulateParticipant(trip,p,80,true,198);
        h.assertValueEqual(trip.getDouble("Travel"),2.0,"teleport cannot satisfy travel distance");
        int ticks=trip.getInt("Ticks");p.setPos(202,100,0);CompanionAmbientService.accumulateParticipant(trip,p,200,true,2);
        h.assertValueEqual(trip.getInt("Ticks"),ticks,"offline/unloaded gap earns no absence ticks");
        h.assertValueEqual(trip.getDouble("Travel"),2.0,"gap earns no travel");h.succeed();
    }

    @GameTest(template="empty",batch="companion_ambient")
    public static void ledgerMissingOrMismatchedBackupFailsClosed(GameTestHelper h) throws Exception {
        var file=java.nio.file.Files.createTempDirectory("vr-ambient-identity-").resolve("ledger.dat");
        var root=new CompoundTag();root.putInt("Version",1);root.putUUID("World",UUID.randomUUID());
        var ledger=new CompanionAmbientLedger(file,root);h.assertTrue(ledger.commit(),"initial identity receipt durable");
        var old=java.nio.file.Files.readAllBytes(file);h.assertTrue(ledger.commit(),"next revision durable");
        java.nio.file.Files.write(file,old);
        boolean mismatch=false;try {CompanionAmbientLedger.read(file);}catch(java.io.IOException expected){mismatch=true;}
        h.assertTrue(mismatch,"restoring only older ledger cannot silently forget delivery");
        java.nio.file.Files.delete(file);boolean missing=false;
        try {CompanionAmbientLedger.read(file);}catch(java.io.IOException expected){missing=true;}
        h.assertTrue(missing,"lost ledger beside receipt cannot initialize fresh discovery history");h.succeed();
    }
    @GameTest(template="empty",batch="companion_ambient")
    public static void directionsAreRecipientRelativeAndConservative(GameTestHelper h) {
        h.assertValueEqual(CompanionAmbientService.direction(0,0,Vec3.ZERO,new Vec3(0,0,10)),"ahead of you","south facing south");
        h.assertValueEqual(CompanionAmbientService.direction(180,0,Vec3.ZERO,new Vec3(0,0,10)),"behind you","opposite headings");
        h.assertValueEqual(CompanionAmbientService.direction(0,0,Vec3.ZERO,new Vec3(-10,0,0)),"on your right","Minecraft yaw right is west");
        h.assertValueEqual(CompanionAmbientService.direction(0,0,Vec3.ZERO,new Vec3(10,0,0)),"on your left","east is left");
        h.assertValueEqual(CompanionAmbientService.direction(0,0,Vec3.ZERO,new Vec3(-10,0,10)),"on your right","45 boundary");
        h.assertValueEqual(CompanionAmbientService.direction(0,0,Vec3.ZERO,new Vec3(-10,0,-10)),"behind you","135 boundary");
        h.assertValueEqual(CompanionAmbientService.direction(0,0,Vec3.ZERO,new Vec3(10,0,-10)),"on your left","-135 boundary");
        h.assertValueEqual(CompanionAmbientService.direction(0,0,Vec3.ZERO,new Vec3(10,0,10)),"ahead of you","-45 boundary");
        h.assertValueEqual(CompanionAmbientService.direction(0,89,Vec3.ZERO,new Vec3(0,0,10)),"nearby","vertical gaze");
        h.assertValueEqual(CompanionAmbientService.direction(Float.NaN,0,Vec3.ZERO,new Vec3(0,0,10)),"nearby","invalid heading");
        h.succeed();
    }

    @GameTest(template="empty",batch="companion_ambient")
    public static void allGeometryAdaptersRequireTheirCompleteComposition(GameTestHelper h) {
        var level=h.getLevel(); var origin=h.absolutePos(new BlockPos(1,2,1));
        var bounds=new BoundingBox(origin.getX()-10,origin.getY()-10,origin.getZ()-10,origin.getX()+20,origin.getY()+20,origin.getZ()+20);
        var materials=Map.ofEntries(Map.entry("wood_hull","oak_planks"),Map.entry("trail_masonry","mud_bricks"),Map.entry("sandstone","sandstone"),Map.entry("cobble","cobblestone"),Map.entry("spruce","spruce_planks"),
                Map.entry("planks","oak_planks"),Map.entry("masonry","stone_bricks"),Map.entry("mine_support","oak_fence"),Map.entry("tuff","tuff_bricks"),
                Map.entry("copper","copper_block"),Map.entry("blackstone","polished_blackstone_bricks"),Map.entry("purpur","purpur_block"),
                Map.entry("village_wall","oak_log"),Map.entry("village_roof","oak_planks"),Map.entry("built","stone_bricks"),Map.entry("terracotta","terracotta"));
        for(var entry:CompanionStructurePerception.shapes().entrySet()) {
            for(var cell:entry.getValue().cells()) {
                var block=BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(materials.getOrDefault(cell.material(),cell.material())));
                h.assertTrue(block!=Blocks.AIR || cell.material().equals("air"),"fixture material exists "+cell.material());
                level.setBlockAndUpdate(origin.offset(cell.x(),cell.y(),cell.z()),block.defaultBlockState());
            }
            h.assertTrue(!CompanionStructurePerception.geometry(level,origin,entry.getKey(),0,bounds).isEmpty(),"complete connected geometry "+entry.getKey());
            var removed=entry.getValue().cells().getFirst();level.setBlockAndUpdate(origin.offset(removed.x(),removed.y(),removed.z()),Blocks.AIR.defaultBlockState());
            h.assertTrue(CompanionStructurePerception.geometry(level,origin,entry.getKey(),0,bounds).isEmpty(),"missing diagnostic block fails "+entry.getKey());
            for(var cell:entry.getValue().cells())level.setBlockAndUpdate(origin.offset(cell.x(),cell.y(),cell.z()),Blocks.AIR.defaultBlockState());
        }
        h.assertFalse(CompanionStructurePerception.supports("unknown_mod_classifier"),"unknown recognizer disabled");h.succeed();
    }

    @GameTest(template="empty",batch="companion_ambient")
    public static void wallsDemolitionRangeAndUnknownRegistryFailClosed(GameTestHelper h) {
        var level=h.getLevel();var v=h.spawn(EntityType.VILLAGER,1,2,1);v.setNoAi(true);
        var target=h.absolutePos(new BlockPos(1,3,7));level.setBlockAndUpdate(target,Blocks.STONE.defaultBlockState());
        h.assertTrue(CompanionStructurePerception.visible(level,v,target,16),"nearby exposed sample visible");
        var wall=h.absolutePos(new BlockPos(1,3,4));level.setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());
        h.assertFalse(CompanionStructurePerception.visible(level,v,target,16),"solid wall occludes sample");
        h.assertFalse(CompanionStructurePerception.visible(level,v,target,3),"beyond visual range");
        var unloaded=new BlockPos(10000000,100,10000000);
        h.assertTrue(level.getChunkSource().getChunkNow(unloaded.getX()>>4,unloaded.getZ()>>4)==null,"fixture chunk is absent");
        h.assertTrue(CompanionStructurePerception.loadedView(level).getBlockState(unloaded).is(Blocks.BARRIER),"unknown ray voxel occludes without a world read");
        h.assertTrue(level.getChunkSource().getChunkNow(unloaded.getX()>>4,unloaded.getZ()>>4)==null,"ray view never loads missing chunks");
        h.assertTrue(CompanionStructurePerception.profile(level,ResourceLocation.parse("example:unknown"))==null,"unknown registry identity skipped");
        h.assertTrue(CompanionStructurePerception.profile(level,ResourceLocation.withDefaultNamespace("buried_treasure"))==null,"buried treasure opt-out");
        var ruin=List.of(target.offset(3,0,0),target.offset(3,1,0),target.offset(3,2,0));
        for(var sample:ruin)level.setBlock(sample,Blocks.CUT_SANDSTONE.defaultBlockState(),2);
        h.assertFalse(CompanionStructurePerception.waterExterior(level,ruin),"dry masonry is not underwater evidence");
        for(var sample:ruin)level.setBlock(sample.north(),Blocks.WATER.defaultBlockState(),2);
        h.assertTrue(CompanionStructurePerception.waterExterior(level,ruin),"water beside solid architecture qualifies without probing inside masonry");
        for(var sample:ruin)level.setBlock(sample.north(),Blocks.AIR.defaultBlockState(),2);
        h.assertFalse(CompanionStructurePerception.waterExterior(level,ruin),"draining the exposed ruin invalidates queued underwater context");h.succeed();
    }

    @GameTest(template="empty",batch="companion_ambient")
    public static void reservationSurvivesDiskReloadAndPreventsOverlappingAttempts(GameTestHelper h) throws Exception {
        var file=java.nio.file.Files.createTempDirectory("vr-ambient-ledger-").resolve("ledger.dat");
        var root=new CompoundTag();root.putInt("Version",1);root.putUUID("World",UUID.randomUUID());
        var ledger=new CompanionAmbientLedger(file,root);UUID speaker=UUID.randomUUID(),listener=UUID.randomUUID();
        String key=ledger.instance("minecraft:overworld","minecraft:stronghold",1,2);
        h.assertTrue(ledger.reserve(key,speaker,List.of(listener),"line1","1 2 3",2400),"durable first reservation");
        var restored=new CompanionAmbientLedger(file,CompanionAmbientLedger.read(file));
        h.assertFalse(restored.reserve(key,speaker,List.of(UUID.randomUUID()),"line2","1 2 3",2400),"old speaker cannot rediscover for new listener");
        h.assertFalse(restored.reserve(key,UUID.randomUUID(),List.of(listener),"line2","1 2 3",2400),"new companion cannot repeat to aware listener");
        h.assertFalse(restored.reserve("other",UUID.randomUUID(),List.of(listener),"line2","1 2 3",2400),"shared recipient cooldown crosses instances");
        h.assertFalse(key.equals(restored.instance("minecraft:the_nether","minecraft:stronghold",1,2)),"dimension identity");
        h.assertFalse(key.equals(restored.instance("minecraft:overworld","minecraft:stronghold",2,2)),"distinct start identity");
        UUID party=UUID.randomUUID();
        h.assertTrue(restored.reserve("party-a",UUID.randomUUID(),List.of(UUID.randomUUID()),"line","anchor",2400,party),"first party attempt reserves shared cooldown");
        var reopened=new CompanionAmbientLedger(file,CompanionAmbientLedger.read(file));
        h.assertFalse(reopened.reserve("party-b",UUID.randomUUID(),List.of(UUID.randomUUID()),"line","anchor",2400,party),"disjoint listeners and different speaker still obey persistent party cooldown");
        h.succeed();
    }

    @GameTest(template="empty",batch="companion_ambient")
    public static void persistenceFailureSuppressesDelivery(GameTestHelper h) throws Exception {
        var parent=java.nio.file.Files.createTempFile("vr-ambient-invalid-parent-",".tmp");
        var root=new CompoundTag();root.putUUID("World",UUID.randomUUID());
        var ledger=new CompanionAmbientLedger(parent.resolve("not-a-directory.dat"),root);
        h.assertFalse(ledger.reserve("x",UUID.randomUUID(),List.of(UUID.randomUUID()),"line","anchor",2400),"failed persistence never authorizes send");
        h.assertFalse(ledger.available(),"failure stays closed");h.succeed();
    }
}

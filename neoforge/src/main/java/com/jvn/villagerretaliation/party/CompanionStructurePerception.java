package com.jvn.villagerretaliation.party;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.phys.*;

/** Bounded, loaded-only architecture sampling. A start is identity, never perception. */
public final class CompanionStructurePerception {
    public record Evidence(String instance, String pool, String recognizer, BlockPos anchor, List<BlockPos> samples,
            List<String> materials, ResourceLocation structure, long startChunk, ResourceLocation dimension) { }
    record Cell(int x, int y, int z, String material) { }
    record Shape(List<Cell> cells) { }
    private static final Map<String, Shape> SHAPES = shapes();
    private static final Set<ResourceLocation> warnedMappings=new HashSet<>();
    static void mappingsReloaded(){warnedMappings.clear();}
    private CompanionStructurePerception() { }
    public static boolean supports(String id) { return SHAPES.containsKey(id); }

    static Map<String, Shape> shapes() {
        var shapes = new LinkedHashMap<String, Shape>();
        // Cross sections require connected, non-collinear architecture, not three arbitrary blocks.
        shapes.put("desert_pyramid", plane("SOS", "SOS", "OSO", "SOS", "OSO", "OOO", "SSS", 'S', "sandstone", 'O', "orange_terracotta"));
        var temple=new ArrayList<Cell>();
        for(int x=0;x<10;x++) {temple.add(new Cell(x,0,0,"cobble"));temple.add(new Cell(x,3,0,"cobble"));}
        for(int x:new int[]{0,9})for(int y=1;y<3;y++)temple.add(new Cell(x,y,0,"cobble"));
        for(int x=2;x<8;x++)temple.add(new Cell(x,4,2,"cobble"));
        for(int x=3;x<7;x++)temple.add(new Cell(x,5,3,"cobble"));
        shapes.put("jungle_temple",new Shape(List.copyOf(temple)));
        var igloo=new ArrayList<>(plane("NNN", "N.N", "N.N", ".N.", 'N', "snow_block").cells());
        for(int x=0;x<3;x++)igloo.add(new Cell(x,4,3,"snow_block"));
        igloo.add(new Cell(-2,2,3,"snow_block"));igloo.add(new Cell(4,2,3,"snow_block"));
        shapes.put("igloo",new Shape(List.copyOf(igloo)));
        var portal=new ArrayList<>(plane("O..", "O..", "O..", "OOO", 'O', "obsidian").cells());
        portal.add(new Cell(-1,2,0,"stone_bricks"));
        shapes.put("ruined_portal",new Shape(List.copyOf(portal)));
        shapes.put("pillager_outpost", plane("LP...PL", "LP...PL", "LPPSPPL", "LPPLPPL", "LPLPLPL", "CPPLPPC", 'C', "cobble", 'L', "dark_oak_log", 'P', "dark_oak_planks", 'S', "dark_oak_slab"));
        shapes.put("woodland_mansion", plane("CCCCCCCL", "PPPPPPPP", "PLGGGLPP", "PLGGGLPP", "PLGGGLPP", "PLGGGLPP", "PLLLLLPP", "PPPPPPPP", 'C', "cobble", 'L', "dark_oak_log", 'P', "dark_oak_planks", 'G', "glass_pane"));
        shapes.put("swamp_hut", plane("L...L", "LPPPL", "LPPPL", "LPFPL", "PPPPP", 'L', "oak_log", 'P', "spruce", 'F', "oak_fence"));
        shapes.put("shipwreck", new Shape(List.of(new Cell(4,0,0,"wood_hull"), new Cell(3,1,0,"wood_hull"), new Cell(2,2,0,"wood_hull"), new Cell(4,0,1,"wood_hull"), new Cell(3,1,1,"wood_hull"), new Cell(2,2,1,"wood_hull"), new Cell(1,3,1,"wood_hull"), new Cell(1,4,1,"wood_hull"), new Cell(3,0,2,"wood_hull"), new Cell(2,1,2,"wood_hull"), new Cell(1,2,2,"wood_hull"), new Cell(1,3,2,"wood_hull"), new Cell(1,4,2,"wood_hull"), new Cell(2,0,3,"wood_hull"), new Cell(1,1,3,"wood_hull"), new Cell(1,2,3,"wood_hull"), new Cell(0,3,3,"wood_hull"), new Cell(0,4,3,"wood_hull"), new Cell(1,0,4,"wood_hull"), new Cell(1,1,4,"wood_hull"), new Cell(0,2,4,"wood_hull"), new Cell(0,3,4,"wood_hull"), new Cell(0,4,4,"wood_hull"), new Cell(1,0,5,"wood_hull"), new Cell(1,1,5,"wood_hull"), new Cell(0,2,5,"wood_hull"), new Cell(0,3,5,"wood_hull"))));
        var ruin=new ArrayList<>(plane("MM", "GG", "MM", "MM", 'M', "masonry", 'G', "polished_granite").cells());
        for(int x=0;x<2;x++){ruin.add(new Cell(x,4,1,"masonry"));ruin.add(new Cell(x,5,2,"masonry"));}
        shapes.put("ocean_ruins",new Shape(List.copyOf(ruin)));
        shapes.put("ocean_monument", plane("PPPDDPPP", "PPDPPDPP", "PDPPPPDP", "DPPDDPPD", "PPDPPDPP", 'P', "prismarine", 'D', "dark_prismarine"));
        var mine = new ArrayList<Cell>();
        for (int z : new int[]{0, 5}) { for (int y = 0; y < 2; y++) { mine.add(new Cell(0,y,z,"mine_support")); mine.add(new Cell(2,y,z,"mine_support")); }
            for (int x = 0; x < 3; x++) mine.add(new Cell(x,2,z,"planks")); }
        shapes.put("mineshaft", new Shape(List.copyOf(mine)));
        shapes.put("stronghold", plane("SSSSS", "S...S", "S...S", "S...S", "SSSSS", 'S', "stone_bricks"));
        shapes.put("ancient_city", new Shape(List.of(new Cell(0,0,0,"reinforced_deepslate"), new Cell(0,0,1,"reinforced_deepslate"), new Cell(0,0,2,"reinforced_deepslate"), new Cell(0,0,3,"reinforced_deepslate"), new Cell(0,0,4,"reinforced_deepslate"), new Cell(0,0,5,"reinforced_deepslate"), new Cell(0,0,6,"reinforced_deepslate"), new Cell(0,1,0,"reinforced_deepslate"), new Cell(0,2,0,"reinforced_deepslate"), new Cell(0,3,0,"reinforced_deepslate"), new Cell(0,4,0,"reinforced_deepslate"), new Cell(0,5,0,"reinforced_deepslate"), new Cell(0,6,0,"reinforced_deepslate"), new Cell(0,7,0,"reinforced_deepslate"), new Cell(0,7,1,"reinforced_deepslate"), new Cell(0,7,2,"reinforced_deepslate"), new Cell(0,7,3,"reinforced_deepslate"), new Cell(0,7,4,"reinforced_deepslate"), new Cell(0,7,5,"reinforced_deepslate"), new Cell(0,7,6,"reinforced_deepslate"))));
        shapes.put("trail_ruins", new Shape(List.of(new Cell(0,0,0,"trail_masonry"), new Cell(1,0,0,"gravel"), new Cell(2,0,0,"gravel"), new Cell(3,0,0,"gravel"), new Cell(4,0,0,"trail_masonry"), new Cell(0,1,0,"terracotta"), new Cell(1,1,0,"gravel"), new Cell(2,1,0,"gravel"), new Cell(3,1,0,"gravel"), new Cell(4,1,0,"terracotta"), new Cell(0,2,0,"trail_masonry"), new Cell(1,2,0,"gravel"), new Cell(3,2,0,"gravel"), new Cell(4,2,0,"trail_masonry"), new Cell(0,3,0,"trail_masonry"), new Cell(1,3,0,"trail_masonry"), new Cell(2,3,0,"trail_masonry"), new Cell(3,3,0,"trail_masonry"), new Cell(4,3,0,"trail_masonry"), new Cell(0,4,0,"trail_masonry"), new Cell(1,4,0,"trail_masonry"), new Cell(2,4,0,"trail_masonry"), new Cell(3,4,0,"trail_masonry"), new Cell(4,4,0,"trail_masonry"), new Cell(0,5,0,"terracotta"), new Cell(1,5,0,"terracotta"), new Cell(2,5,0,"trail_masonry"), new Cell(3,5,0,"terracotta"), new Cell(4,5,0,"terracotta"), new Cell(0,6,0,"terracotta"), new Cell(1,6,0,"terracotta"), new Cell(3,6,0,"terracotta"), new Cell(4,6,0,"terracotta"), new Cell(2,6,0,"trail_masonry"))));
        shapes.put("trial_chambers", new Shape(List.of(new Cell(0,0,0,"tuff"), new Cell(1,0,0,"tuff"), new Cell(2,0,0,"tuff"), new Cell(3,0,0,"tuff"), new Cell(4,0,0,"tuff"), new Cell(5,0,0,"tuff"), new Cell(6,0,0,"tuff"), new Cell(7,0,0,"tuff"), new Cell(8,0,0,"tuff"), new Cell(0,1,0,"copper"), new Cell(1,1,0,"tuff"), new Cell(2,1,0,"tuff"), new Cell(3,1,0,"tuff"), new Cell(4,1,0,"tuff"), new Cell(5,1,0,"tuff"), new Cell(6,1,0,"tuff"), new Cell(7,1,0,"tuff"), new Cell(8,1,0,"copper"), new Cell(1,2,0,"tuff"), new Cell(2,2,0,"tuff"), new Cell(3,2,0,"tuff"), new Cell(4,2,0,"tuff"), new Cell(5,2,0,"tuff"), new Cell(6,2,0,"tuff"), new Cell(7,2,0,"tuff"), new Cell(1,3,0,"tuff"), new Cell(2,3,0,"tuff"), new Cell(3,3,0,"tuff"), new Cell(4,3,0,"tuff"), new Cell(5,3,0,"tuff"), new Cell(6,3,0,"tuff"), new Cell(7,3,0,"tuff"), new Cell(1,4,0,"tuff"), new Cell(2,4,0,"tuff"), new Cell(3,4,0,"tuff"), new Cell(4,4,0,"tuff"), new Cell(5,4,0,"tuff"), new Cell(6,4,0,"tuff"), new Cell(7,4,0,"tuff"), new Cell(1,5,0,"tuff"), new Cell(2,5,0,"tuff"), new Cell(3,5,0,"tuff"), new Cell(4,5,0,"tuff"), new Cell(5,5,0,"tuff"), new Cell(6,5,0,"tuff"), new Cell(7,5,0,"tuff"))));
        shapes.put("nether_fortress", plane("BFBBBB.", "BFBBFBB", "BFBBFBB", "BBBBBBB", 'B', "nether_bricks", 'F', "nether_brick_fence"));
        shapes.put("bastion_remnant", new Shape(List.of(new Cell(0,0,0,"blackstone"), new Cell(0,0,1,"blackstone"), new Cell(0,0,2,"blackstone"), new Cell(0,0,3,"blackstone"), new Cell(0,0,4,"blackstone"), new Cell(0,0,5,"blackstone"), new Cell(0,0,6,"blackstone"), new Cell(0,0,7,"blackstone"), new Cell(0,0,8,"blackstone"), new Cell(0,0,9,"blackstone"), new Cell(0,0,10,"blackstone"), new Cell(0,1,0,"blackstone"), new Cell(0,1,1,"blackstone"), new Cell(0,1,2,"blackstone"), new Cell(0,1,3,"blackstone"), new Cell(0,1,4,"blackstone"), new Cell(0,1,5,"blackstone"), new Cell(0,1,6,"blackstone"), new Cell(0,1,7,"blackstone"), new Cell(0,1,8,"blackstone"), new Cell(0,2,0,"blackstone"), new Cell(0,2,1,"blackstone"), new Cell(0,2,2,"blackstone"), new Cell(0,2,6,"blackstone"), new Cell(0,2,7,"blackstone"), new Cell(0,3,0,"basalt"), new Cell(0,3,1,"blackstone"), new Cell(0,3,2,"blackstone"), new Cell(0,3,6,"blackstone"), new Cell(0,3,7,"blackstone"), new Cell(0,3,8,"blackstone"), new Cell(0,4,0,"basalt"), new Cell(0,4,1,"blackstone"), new Cell(0,4,2,"blackstone"), new Cell(0,4,6,"blackstone"), new Cell(0,4,7,"blackstone"), new Cell(0,4,8,"blackstone"), new Cell(0,4,9,"blackstone"), new Cell(0,5,0,"basalt"), new Cell(0,5,1,"blackstone"), new Cell(0,5,2,"blackstone"), new Cell(0,5,6,"blackstone"), new Cell(0,5,7,"blackstone"), new Cell(0,5,8,"blackstone"), new Cell(0,5,9,"blackstone"), new Cell(0,5,10,"blackstone"), new Cell(0,6,0,"basalt"), new Cell(0,6,1,"blackstone"), new Cell(0,6,2,"blackstone"), new Cell(0,6,3,"blackstone"), new Cell(0,6,4,"blackstone"), new Cell(0,6,5,"blackstone"), new Cell(0,6,6,"blackstone"), new Cell(0,6,7,"blackstone"), new Cell(0,6,8,"blackstone"), new Cell(0,6,9,"blackstone"), new Cell(0,6,10,"blackstone"), new Cell(0,7,0,"basalt"), new Cell(0,7,1,"basalt"), new Cell(0,7,2,"basalt"), new Cell(0,7,3,"blackstone"), new Cell(0,7,4,"blackstone"), new Cell(0,7,5,"blackstone"), new Cell(0,7,6,"blackstone"), new Cell(0,7,7,"blackstone"), new Cell(0,7,8,"blackstone"), new Cell(0,7,9,"blackstone"), new Cell(0,7,10,"blackstone"))));
        shapes.put("nether_fossil", new Shape(List.of(new Cell(0,0,0,"bone_block"), new Cell(0,0,2,"bone_block"), new Cell(0,0,4,"bone_block"), new Cell(3,0,0,"bone_block"), new Cell(3,0,2,"bone_block"), new Cell(0,1,0,"bone_block"), new Cell(0,1,2,"bone_block"), new Cell(3,1,0,"bone_block"), new Cell(0,2,0,"bone_block"), new Cell(1,3,0,"bone_block"))));
        shapes.put("end_city", plane("PPPPPPPP", "PEEEEEEP", "PEGEEGEP", "PEGEEGEP", 'P', "purpur", 'E', "end_stone_bricks", 'G', "magenta_stained_glass"));
        // Village recognizer separately requires two exposed, distinct generated building pieces.
        var house=new ArrayList<>(plane("W...W", "W...W", "W...W", 'W', "village_wall").cells());
        for(int x=0;x<5;x++)house.add(new Cell(x,3,-1,"village_roof"));
        shapes.put("village",new Shape(List.copyOf(house)));
        shapes.put("generic_built", plane("MMMMM", "M...M", "M...M", "MMMMM", 'M', "built"));
        shapes.put("generic_ruined_masonry", plane("MMMMM", "MAAAM", "MMAAA", 'M', "masonry", 'A', "air"));
        shapes.put("generic_wooden_wreck", plane("PPPPP", "PAAAP", "APAAA", 'P', "planks", 'A', "air"));
        return Map.copyOf(shapes);
    }

    private static Shape plane(Object... args) {
        var rows = new ArrayList<String>(); var materials = new HashMap<Character,String>(); int i = 0;
        while (i < args.length && args[i] instanceof String) rows.add((String) args[i++]);
        while (i < args.length) { materials.put((Character) args[i++], (String) args[i++]); }
        var cells = new ArrayList<Cell>();
        for (int y = 0; y < rows.size(); y++) for (int x = 0; x < rows.get(y).length(); x++) {
            char ch = rows.get(y).charAt(x); if (ch != '.') cells.add(new Cell(x,y,0,materials.get(ch)));
        }
        return new Shape(List.copyOf(cells));
    }

    static boolean material(BlockState state, String type) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return switch (type) {
            case "air" -> state.isAir();
            case "cobble" -> id.equals("cobblestone") || id.equals("mossy_cobblestone");
            case "masonry" -> id.contains("bricks") || id.contains("sandstone") || id.equals("cobblestone") || id.equals("mossy_cobblestone");
            case "built" -> id.contains("bricks") || id.contains("planks") || id.contains("cut_sandstone");
            case "wood_hull" -> state.is(net.minecraft.tags.BlockTags.PLANKS) || state.is(net.minecraft.tags.BlockTags.LOGS) || state.is(net.minecraft.tags.BlockTags.WOODEN_STAIRS);
            case "trail_masonry" -> id.contains("mud_brick") || id.equals("packed_mud");
            case "prismarine" -> id.equals("prismarine") || id.equals("prismarine_bricks");
            case "obsidian" -> id.equals("obsidian") || id.equals("crying_obsidian");
            case "mine_support" -> id.endsWith("_fence") || id.endsWith("_log") || id.endsWith("_planks");
            case "village_wall" -> id.contains("cobblestone") || id.contains("planks") || id.contains("log") || id.contains("sandstone") || id.equals("snow_block");
            case "village_roof" -> id.contains("log") || id.endsWith("stairs") || id.endsWith("slab") || id.contains("planks") || id.contains("sandstone") || id.equals("snow_block");
            default -> id.contains(type);
        };
    }

    /** Every accessed start chunk was returned by getChunkNow. References are never resolved by loading. */
    public static List<Map.Entry<ResourceLocation, StructureStart>> starts(ServerLevel level, BlockPos position) {
        var starts = new TreeMap<String, Map.Entry<ResourceLocation, StructureStart>>();
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (int x = (position.getX() >> 4) - 4; x <= (position.getX() >> 4) + 4; x++)
            for (int z = (position.getZ() >> 4) - 4; z <= (position.getZ() >> 4) + 4; z++) {
                var chunk = level.getChunkSource().getChunkNow(x,z); if (chunk == null) continue;
                var chunks = new HashSet<Long>(); chunks.add(chunk.getPos().toLong());
                chunk.getAllReferences().values().forEach(refs -> { for (long ref : refs) { if (chunks.size() >= 64) break; chunks.add(ref); } });
                for (long key : chunks) {
                    var pos = new net.minecraft.world.level.ChunkPos(key); var startChunk = level.getChunkSource().getChunkNow(pos.x,pos.z);
                    if (startChunk == null) continue;
                    for (var entry : startChunk.getAllStarts().entrySet()) {
                        var start = entry.getValue(); var id = registry.getKey(entry.getKey());
                        if (id == null || !start.isValid()) continue;
                        String identity = id + "/" + start.getChunkPos().toLong();
                        starts.putIfAbsent(identity, Map.entry(id, start));
                    }
                }
            }
        return List.copyOf(starts.values());
    }

    public static CompanionAmbientResources.Profile profile(ServerLevel level, ResourceLocation id) {
        var holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, id)).orElse(null);
        if (holder == null || id.equals(ResourceLocation.withDefaultNamespace("buried_treasure"))) return null;
        var all = CompanionAmbientResources.get().profiles();
        var matches = all.stream().filter(p -> p.structures().contains(id.toString()) || p.tags().stream().anyMatch(t -> holder.is(TagKey.create(Registries.STRUCTURE, ResourceLocation.parse(t))))).toList();
        if (matches.stream().anyMatch(p -> !p.enabled())) return null;
        var exact = matches.stream().filter(p -> p.structures().contains(id.toString())).toList();
        var chosen = exact.isEmpty() ? matches : exact;
        if((chosen.size()!=1 || !supports(chosen.getFirst().recognizer())) && warnedMappings.add(id))
            com.mojang.logging.LogUtils.getLogger().warn("Skipping ambient structure {}: mapping is missing, ambiguous or uses an unsupported recognizer",id);
        return chosen.size() == 1 && supports(chosen.getFirst().recognizer()) ? chosen.getFirst() : null;
    }

    static List<BlockPos> geometry(ServerLevel level, BlockPos origin, String recognizer, int rotation, BoundingBox bounds) {
        var shape = SHAPES.get(recognizer); if (shape == null) return List.of();
        var positions = new ArrayList<BlockPos>();
        for (Cell cell : shape.cells()) {
            int x = cell.x(), z = cell.z();
            for (int turn = 0; turn < rotation; turn++) { int old = x; x = -z; z = old; }
            var pos = origin.offset(x,cell.y(),z);
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4,pos.getZ() >> 4);
            if (chunk == null || !bounds.isInside(pos) || !material(chunk.getBlockState(pos),cell.material())) return List.of();
            positions.add(pos);
        }
        return List.copyOf(positions);
    }

    public static boolean visible(ServerLevel level, LivingEntity observer, BlockPos position, int range) {
        if (observer.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS) || observer.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS)) return false;
        Vec3 eyes = observer.getEyePosition();
        // Aim at the exposed face. Center rays falsely hit neighboring facade blocks at oblique angles.
        Vec3 target = new Vec3(position.getX()+net.minecraft.util.Mth.clamp(eyes.x-position.getX(),.05,.95),
                position.getY()+net.minecraft.util.Mth.clamp(eyes.y-position.getY(),.05,.95),
                position.getZ()+net.minecraft.util.Mth.clamp(eyes.z-position.getZ(),.05,.95));
        return visibleRay(level,observer,position,range,eyes,target) || visibleRay(level,observer,position,range,eyes,Vec3.atCenterOf(position));
    }

    private static boolean visibleRay(ServerLevel level,LivingEntity observer,BlockPos position,int range,Vec3 eyes,Vec3 target) {
        double distance = eyes.distanceTo(target);
        if (distance > range || distance < .1 || level.getChunkSource().getChunkNow(position.getX()>>4,position.getZ()>>4)==null) return false;
        if (level.isRaining() && level.canSeeSky(position) && distance>24) return false;
        int water = 0;
        int steps = (int) Math.ceil(distance * 2);
        for (int i = 0; i <= steps; i++) {
            var pos = BlockPos.containing(eyes.lerp(target, i / (double) steps));
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4,pos.getZ() >> 4); if (chunk == null) return false;
            var fluid = chunk.getFluidState(pos);
            if (!fluid.isEmpty()) { if (!fluid.is(net.minecraft.tags.FluidTags.WATER)) return false; water++; }
        }
        if (water > 0 && (distance > 12 || water > 16)) return false;
        if (level.getMaxLocalRawBrightness(position) < 4 && distance > 8) return false;
        var hit = level.clip(new ClipContext(eyes,target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,observer));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(position);
    }

    static boolean fresh(ServerLevel level,LivingEntity speaker,Evidence e) {
        if (!level.dimension().location().equals(e.dimension())) return false;
        var profile=profile(level,e.structure());
        if(profile==null || !profile.pool().equals(e.pool()) || !profile.recognizer().equals(e.recognizer())) return false;
        var chunkPos=new net.minecraft.world.level.ChunkPos(e.startChunk());
        var chunk=level.getChunkSource().getChunkNow(chunkPos.x,chunkPos.z);if(chunk==null)return false;
        var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(e.structure());
        var start=chunk.getAllStarts().get(structure);
        if(start==null || !start.isValid() || e.samples().size()!=e.materials().size())return false;
        for(int i=0;i<e.samples().size();i++) {
            var pos=e.samples().get(i);var sampleChunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
            if(sampleChunk==null || !start.getBoundingBox().isInside(pos) || !material(sampleChunk.getBlockState(pos),e.materials().get(i))
                    || !visible(level,speaker,pos,CompanionAmbientResources.get().range()))return false;
        }
        return true;
    }

    /** Samples visible surfaces first, without asking the world to load any ray segment. */
    private static List<BlockPos> surfaceOrigins(ServerLevel level,LivingEntity speaker,Shape shape,int range,long cursor) {
        var origins=new LinkedHashSet<BlockPos>();var eyes=speaker.getEyePosition();
        for(int ray=0;ray<24 && origins.size()<128;ray++) {
            double yaw=Math.toRadians((ray%8)*45+(cursor%3)*15);
            double pitch=Math.toRadians((ray/8-1)*25);
            Vec3 target=eyes.add(Math.sin(yaw)*Math.cos(pitch)*range,Math.sin(pitch)*range,Math.cos(yaw)*Math.cos(pitch)*range);
            boolean loaded=true;
            for(int step=0;step<=range*2;step++) {
                var pos=BlockPos.containing(eyes.lerp(target,step/(double)(range*2)));
                if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null) {loaded=false;break;}
            }
            if(!loaded)continue;
            var hit=level.clip(new ClipContext(eyes,target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,speaker));
            if(hit.getType()!=HitResult.Type.BLOCK)continue;
            for(int i=0;i<4 && origins.size()<128;i++) {
                var cell=shape.cells().get((int)Math.floorMod(cursor*4+i,shape.cells().size()));
                for(int turn=0;turn<4;turn++) {
                    int x=cell.x(),z=cell.z();for(int t=0;t<turn;t++){int old=x;x=-z;z=old;}
                    origins.add(hit.getBlockPos().offset(-x,-cell.y(),-z));
                }
            }
        }
        return List.copyOf(origins);
    }

    public static Optional<Evidence> inspect(ServerLevel level, LivingEntity speaker, ResourceLocation id, StructureStart start, long cursor) {
        var profile = profile(level,id); if (profile == null) return Optional.empty();
        var bounds = start.getBoundingBox(); int range = CompanionAmbientResources.get().range();
        int minX = Math.max(bounds.minX(), speaker.getBlockX()-range), maxX = Math.min(bounds.maxX(),speaker.getBlockX()+range);
        int minY = Math.max(bounds.minY(), speaker.getBlockY()-range), maxY = Math.min(bounds.maxY(),speaker.getBlockY()+range);
        int minZ = Math.max(bounds.minZ(), speaker.getBlockZ()-range), maxZ = Math.min(bounds.maxZ(),speaker.getBlockZ()+range);
        if (minX>maxX || minY>maxY || minZ>maxZ) return Optional.empty();
        long volume = (long)(maxX-minX+1)*(maxY-minY+1)*(maxZ-minZ+1);
        var origins=new ArrayList<>(surfaceOrigins(level,speaker,SHAPES.get(profile.recognizer()),range,cursor));
        for (int n=0;n<32;n++) {
            long index = Math.floorMod(cursor*128+n,volume);
            int x=minX+(int)(index%(maxX-minX+1)); index/=maxX-minX+1;
            int z=minZ+(int)(index%(maxZ-minZ+1)); int y=minY+(int)(index/(maxZ-minZ+1));
            origins.add(new BlockPos(x,y,z));
        }
        for(var origin:origins) {
            for (int rotation=0;rotation<4;rotation++) {
                var samples=geometry(level,origin,profile.recognizer(),rotation,bounds);
                if (samples.isEmpty() || !samples.stream().allMatch(p -> visible(level,speaker,p,range))) continue;
                var materials=new ArrayList<>(SHAPES.get(profile.recognizer()).cells().stream().map(Cell::material).toList());
                if (profile.recognizer().equals("village")) {
                    var firstSamples=samples;
                    var first=start.getPieces().stream().filter(p -> p.getBoundingBox().getYSpan()>=5 && firstSamples.stream().allMatch(p.getBoundingBox()::isInside)).findFirst().orElse(null);
                    if(first==null)continue;
                    List<BlockPos> second=List.of();
                    for(var other:origins) {
                        for(int r=0;r<4;r++) {
                            var proof=geometry(level,other,"village",r,bounds);
                            if(proof.isEmpty() || !proof.stream().allMatch(p -> visible(level,speaker,p,range)))continue;
                            if(start.getPieces().stream().anyMatch(p -> p!=first && p.getBoundingBox().getYSpan()>=5
                                    && !p.getBoundingBox().intersects(first.getBoundingBox()) && proof.stream().allMatch(p.getBoundingBox()::isInside))) {second=proof;break;}
                        }
                        if(!second.isEmpty())break;
                    }
                    if(second.isEmpty())continue;
                    var both=new ArrayList<>(samples);both.addAll(second);samples=List.copyOf(both);
                    materials.addAll(SHAPES.get("village").cells().stream().map(Cell::material).toList());
                }
                if (profile.recognizer().equals("ocean_ruins") && !level.getFluidState(origin.above()).is(net.minecraft.tags.FluidTags.WATER)) continue;
                String instance=CompanionAmbientLedger.get(level.getServer()).instance(level.dimension().location().toString(),id.toString(),start.getChunkPos().x,start.getChunkPos().z);
                return Optional.of(new Evidence(instance,profile.pool(),profile.recognizer(),samples.getFirst(),samples,List.copyOf(materials),
                        id,start.getChunkPos().toLong(),level.dimension().location()));
            }
        }
        return Optional.empty();
    }
}

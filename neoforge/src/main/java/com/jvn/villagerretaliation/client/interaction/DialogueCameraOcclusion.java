package com.jvn.villagerretaliation.client.interaction;

import com.mojang.logging.LogUtils;
import com.jvn.villagerretaliation.dialogue.DialogueCamera;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

public final class DialogueCameraOcclusion {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_HIDDEN_BLOCKS = 4096;
    private static final long REMOVAL_DELAY = 250_000_000L;
    private static volatile Snapshot snapshot = new Snapshot(null, 0, Set.of());
    private static final Map<BlockPos, Long> retained = new HashMap<>();
    private static final ConcurrentLinkedQueue<Build> builds = new ConcurrentLinkedQueue<>();
    // keep each worker's mask until its mesh has actually reached the renderer
    private static final ThreadLocal<Build> meshBuild = new ThreadLocal<>();
    private static final Map<Object, Build> sodiumOutputs = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static Set<UUID> hiddenEntities = Set.of();
    private static Vec3 lastPosition;
    private static long nextScan;
    private static Method shaderQuery;
    private static Object shaderApi;
    private static boolean checkedShaderApi;
    private static boolean shaderApiUnavailable;

    private DialogueCameraOcclusion() { }

    public record Snapshot(Level level, long version, Set<BlockPos> blocks) {
        public Snapshot { blocks = Set.copyOf(blocks); }
        public boolean hides(BlockPos position) { return blocks.contains(position); }
    }
    private record Build(SectionPos section, Snapshot snapshot) { }
    private record Sight(Entity entity, Vec3 point) { }

    public static Snapshot snapshot(Level level) {
        var current = snapshot;
        return current.level() == level ? current : new Snapshot(level, current.version(), Set.of());
    }

    public static void built(SectionPos section, Snapshot used) {
        if (!used.blocks().isEmpty() || !snapshot.blocks().isEmpty() || used.version() != snapshot.version())
            builds.add(new Build(section, used));
    }
    public static void beginBuild() { meshBuild.remove(); }
    public static void captureBuild(SectionPos section, Snapshot used) { meshBuild.set(new Build(section, used)); }
    public static void finishBuild(java.util.concurrent.CompletableFuture<?> completion) {
        var build = meshBuild.get();
        meshBuild.remove();
        if (build != null) completion.thenRun(() -> built(build.section(), build.snapshot()));
    }
    public static void finishSodiumBuild(Object output) {
        var build = meshBuild.get();
        meshBuild.remove();
        if (build != null && output != null) sodiumOutputs.put(output, build);
    }
    public static void uploadedSodium(java.util.Collection<?> outputs) {
        for (var output : outputs) {
            var build = sodiumOutputs.remove(output);
            if (build != null) built(build.section(), build.snapshot());
        }
    }

    public static boolean hides(BlockPos position) {
        var minecraft = Minecraft.getInstance();
        return snapshot.level() == minecraft.level && snapshot.hides(position);
    }

    public static boolean hides(Entity entity) { return hiddenEntities.contains(entity.getUUID()); }

    public static Vec3 resolve(Vec3 desired, Vec3 pivot, List<Entity> subjects, List<Entity> participants, float partialTick) {
        Vec3 position = placement(desired, pivot, subjects, partialTick);
        return position == null ? null : track(position, pivot, subjects, participants, partialTick);
    }

    public static Vec3 resolveFocus(Vec3 desired, Vec3 pivot, Vec3 point, Entity subject,
            BlockPos block, List<Entity> participants, float partialTick) {
        var subjects = subject == null ? List.<Entity>of() : List.of(subject);
        var points = List.of(new Sight(subject, point));
        Vec3 position = placement(desired, pivot, subjects, partialTick, false, points, block);
        return position == null ? null : track(position, pivot, subjects, participants, partialTick, false, points, block);
    }

    public static Vec3 placement(Vec3 desired, Vec3 pivot, List<Entity> subjects, float partialTick) {
        return placement(desired, pivot, subjects, partialTick, false);
    }

    public static Vec3 placement(Vec3 desired, Vec3 pivot, List<Entity> subjects, float partialTick, boolean ignoreEntityBlockers) {
        return placement(desired, pivot, subjects, partialTick, ignoreEntityBlockers,
                subjects.stream().flatMap(entity -> targets(entity, partialTick).stream().map(point -> new Sight(entity, point))).toList(), null);
    }

    private static Vec3 placement(Vec3 desired, Vec3 pivot, List<Entity> subjects, float partialTick,
            boolean ignoreEntityBlockers, List<Sight> points, BlockPos protectedBlock) {
        var minecraft = Minecraft.getInstance();
        boolean masking = supportsMasking();
        var candidates = new java.util.ArrayList<Vec3>();
        candidates.add(desired);
        if (lastPosition != null && lastPosition.distanceToSqr(desired) <= 2.25)
            candidates.add(DialogueCamera.clamp(lastPosition, minecraft.player.getPosition(partialTick)));
        for (var offset : List.of(new Vec3(0, 0.5, 0), new Vec3(0.5, 0, 0), new Vec3(-0.5, 0, 0),
                new Vec3(0, 0, 0.5), new Vec3(0, 0, -0.5), new Vec3(0, -0.5, 0), new Vec3(0, 1, 0),
                new Vec3(1, 0, 0), new Vec3(-1, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 0, -1)))
            candidates.add(DialogueCamera.clamp(desired.add(offset), minecraft.player.getPosition(partialTick)));
        candidates.add(pivot.lerp(desired, 0.5));
        Vec3 best = desired;
        double score = -1;
        for (var candidate : candidates) {
            double visibility = score(candidate, subjects, ignoreEntityBlockers, points, protectedBlock);
            if (visibility > score + 0.001) { best = candidate; score = visibility; }
            if (visibility >= points.size() + 1) break;
        }
        if (!masking) {
            publish(Set.of());
            hiddenEntities = Set.of();
            if (!free(best, subjects, ignoreEntityBlockers)) best = pivot;
            if (!free(best, subjects, ignoreEntityBlockers) || score(best, subjects, ignoreEntityBlockers, points, protectedBlock) <= 1) return null;
            lastPosition = best;
            return best;
        }
        lastPosition = best;
        return best;
    }

    public static Vec3 track(Vec3 best, Vec3 pivot, List<Entity> subjects, List<Entity> participants, float partialTick) {
        return track(best, pivot, subjects, participants, partialTick, false);
    }

    public static Vec3 track(Vec3 best, Vec3 pivot, List<Entity> subjects, List<Entity> participants, float partialTick, boolean ignoreEntityBlockers) {
        return track(best, pivot, subjects, participants, partialTick, ignoreEntityBlockers,
                subjects.stream().flatMap(entity -> targets(entity, partialTick).stream().map(point -> new Sight(entity, point))).toList(), null);
    }

    private static Vec3 track(Vec3 best, Vec3 pivot, List<Entity> subjects, List<Entity> participants, float partialTick,
            boolean ignoreEntityBlockers, List<Sight> points, BlockPos protectedBlock) {
        var minecraft = Minecraft.getInstance();
        if (!supportsMasking()) {
            publish(Set.of());
            hiddenEntities = Set.of();
            return free(best, subjects, ignoreEntityBlockers) && score(best, subjects, ignoreEntityBlockers, points, protectedBlock) > 1 ? best : null;
        }
        long now = System.nanoTime();
        if (protectedBlock != null && retained.remove(protectedBlock) != null) publish(retained.keySet());
        if (now < nextScan) return best;
        nextScan = now + 50_000_000L;
        var hidden = new HashSet<BlockPos>();
        var near = new AABB(best, best).inflate(0.35);
        for (var position : BlockPos.betweenClosed(BlockPos.containing(near.minX, near.minY, near.minZ),
                BlockPos.containing(near.maxX, near.maxY, near.maxZ))) {
            if (!minecraft.level.getBlockState(position).isAir()) hidden.add(position.immutable());
        }
        var entities = new HashSet<UUID>();
        var protectedEntities = new HashSet<UUID>();
        subjects.forEach(entity -> protectedEntities.add(entity.getUUID()));
        participants.forEach(entity -> protectedEntities.add(entity.getUUID()));
        protectedEntities.add(minecraft.player.getUUID());
        for (var sight : points) {
            Vec3 eye = sight.point();
            if (eye.distanceToSqr(best) > 64 * 64) continue;
            collect(best, eye, hidden);
            for (var entity : minecraft.level.getEntities(null, new AABB(best, eye).inflate(0.5))) {
                if (!protectedEntities.contains(entity.getUUID())
                        && (entity.getBoundingBox().inflate(0.1).clip(best, eye).isPresent()
                            || entity.getBoundingBox().intersects(near))) entities.add(entity.getUUID());
            }
        }
        hidden.remove(protectedBlock);
        if (hidden.size() > MAX_HIDDEN_BLOCKS) {
            publish(Set.of());
            hiddenEntities = Set.of();
            return free(pivot) ? pivot : null;
        }
        hidden.forEach(position -> retained.put(position, now + REMOVAL_DELAY));
        retained.entrySet().removeIf(entry -> entry.getValue() < now);
        if (retained.size() > MAX_HIDDEN_BLOCKS) {
            retained.clear();
            publish(Set.of());
            hiddenEntities = Set.of();
            return free(pivot) ? pivot : null;
        }
        publish(retained.keySet());
        hiddenEntities = Set.copyOf(entities);
        return best;
    }

    private static List<Vec3> targets(Entity entity, float partialTick) {
        Vec3 eye = entity.getEyePosition(partialTick);
        return List.of(eye, eye.add(entity.getBbWidth() * 0.35, -entity.getBbHeight() * 0.25, 0),
                eye.add(-entity.getBbWidth() * 0.35, -entity.getBbHeight() * 0.25, 0));
    }

    private static double score(Vec3 camera, List<Entity> subjects, boolean ignoreEntityBlockers, List<Sight> points, BlockPos protectedBlock) {
        var minecraft = Minecraft.getInstance();
        double score = free(camera, subjects, ignoreEntityBlockers) ? 1 : -100;
        for (var sight : points) {
            Vec3 target = sight.point();
            var hidden = new HashSet<BlockPos>();
            collect(camera, target, hidden);
            hidden.remove(protectedBlock);
            // first-person shots can pull back through the player's hidden body
            boolean entityBlocker = !ignoreEntityBlockers && minecraft.level.getEntities(sight.entity(), new AABB(camera, target)).stream()
                    .anyMatch(other -> (other != minecraft.player || DialogueCameraController.detached())
                            && !other.getBoundingBox().contains(camera)
                            && other.getBoundingBox().clip(camera, target).isPresent());
            if (hidden.isEmpty() && !entityBlocker) score++;
        }
        return score;
    }

    private static boolean free(Vec3 position) {
        return free(position, List.of(), false);
    }

    private static boolean free(Vec3 position, List<Entity> subjects, boolean ignoreEntityBlockers) {
        var level = Minecraft.getInstance().level;
        var box = new AABB(position, position).inflate(0.15);
        if (!level.getEntities(Minecraft.getInstance().player, box, entity -> entity.isAlive() && !entity.isInvisible()
                && (!ignoreEntityBlockers || subjects.contains(entity))).isEmpty())
            return false;
        for (var block : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            var shape = level.getBlockState(block).getVisualShape(level, block, net.minecraft.world.phys.shapes.CollisionContext.empty());
            if (!shape.isEmpty() && shape.bounds().move(block).intersects(box)) return false;
        }
        return true;
    }

    private static void collect(Vec3 camera, Vec3 target, Set<BlockPos> hidden) {
        var level = Minecraft.getInstance().level;
        if (camera.distanceToSqr(target) > 64 * 64) return;
        BlockGetter.traverseBlocks(camera, target, hidden, (blocks, position) -> {
            var state = level.getBlockState(position);
            if (!state.isAir()) {
                var shape = state.getShape(level, position, net.minecraft.world.phys.shapes.CollisionContext.empty());
                if (!shape.isEmpty() && (shape.clip(camera, target, position) != null
                        || shape.bounds().move(position).contains(camera))) blocks.add(position.immutable());
            }
            return null;
        }, blocks -> null);
    }

    private static void publish(Set<BlockPos> hidden) {
        var minecraft = Minecraft.getInstance();
        var previous = snapshot;
        if (previous.level() == minecraft.level && previous.blocks().equals(hidden)) return;
        snapshot = new Snapshot(minecraft.level, previous.version() + 1, hidden);
        var changed = new HashSet<BlockPos>();
        if (previous.level() == minecraft.level) changed.addAll(previous.blocks());
        for (var block : hidden) if (!changed.add(block)) changed.remove(block);
        dirty(changed);
    }

    private static void dirty(Set<BlockPos> changed) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        var sections = new HashSet<SectionPos>();
        for (var position : changed) {
            for (var nearby : BlockPos.betweenClosed(position.offset(-1, -1, -1), position.offset(1, 1, 1)))
                sections.add(SectionPos.of(nearby));
        }
        for (var section : sections)
            minecraft.levelRenderer.setSectionDirty(section.x(), section.y(), section.z());
    }

    public static void tick() {
        for (Build build; (build = builds.poll()) != null;) {
            if (build.snapshot().level() != Minecraft.getInstance().level || build.snapshot().version() == snapshot.version()) continue;
            var changed = new HashSet<BlockPos>();
            for (var position : build.snapshot().blocks())
                if (affects(build.section(), position) && !snapshot.blocks().contains(position)) changed.add(position);
            for (var position : snapshot.blocks())
                if (affects(build.section(), position) && !build.snapshot().blocks().contains(position)) changed.add(position);
            dirty(changed);
        }
    }

    private static boolean affects(SectionPos section, BlockPos position) {
        return position.getX() >= section.minBlockX() - 1 && position.getX() <= section.maxBlockX() + 1
                && position.getY() >= section.minBlockY() - 1 && position.getY() <= section.maxBlockY() + 1
                && position.getZ() >= section.minBlockZ() - 1 && position.getZ() <= section.maxBlockZ() + 1;
    }

    public static void clear() {
        publish(Set.of());
        retained.clear();
        hiddenEntities = Set.of();
        lastPosition = null;
        nextScan = 0;
    }

    public static boolean supportsMasking() {
        if (ModList.get().isLoaded("sodium")
                && !com.jvn.villagerretaliation.compat.VillagerRetaliationMixinPlugin.supportsSodiumDialogueCamera()) return false;
        for (var renderer : List.of("embeddium", "vulkanmod", "canvas", "nvidium"))
            if (ModList.get().isLoaded(renderer)) return false;
        if (!ModList.get().isLoaded("iris") && !ModList.get().isLoaded("oculus")) return true;
        if (!checkedShaderApi) {
            checkedShaderApi = true;
            String className = ModList.get().isLoaded("iris") ? "net.irisshaders.iris.api.v0.IrisApi" : "net.coderbot.iris.api.v0.IrisApi";
            try {
                Class<?> type = Class.forName(className);
                shaderApi = type.getMethod("getInstance").invoke(null);
                shaderQuery = type.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException exception) {
                shaderApiUnavailable = true;
                LOGGER.warn("Dialogue blocker hiding is unavailable with this shader renderer", exception);
            }
        }
        if (shaderApiUnavailable) return false;
        try {
            return !((Boolean) shaderQuery.invoke(shaderApi));
        } catch (ReflectiveOperationException exception) {
            shaderApiUnavailable = true;
            LOGGER.warn("Could not query shader-pack state for dialogue cameras", exception);
            return false;
        }
    }
}

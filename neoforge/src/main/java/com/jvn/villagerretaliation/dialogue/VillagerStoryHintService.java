package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.dialogue.resources.BiomeStoryResources;
import com.jvn.villagerretaliation.dialogue.resources.DangerousStructureStoryResources;
import com.jvn.villagerretaliation.dialogue.resources.DialogueTuningResources;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.VillagerDialogueService;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.util.VillagerWorldTargetCache;
import com.jvn.toucanlib.util.ToucanRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class VillagerStoryHintService {
    private static final int MIN_STRUCTURE_DISTANCE = 96;
    private static final long CARTOGRAPHER_MAP_COOLDOWN_TICKS = 20L * 60L * 20L * 2L;
    private static final long CARTOGRAPHER_MAP_DISCOVERY_TICKS = 20L * 60L * 60L * 6L;
    private static final long STORY_HINT_DISCOVERY_TICKS = 20L * 60L * 60L * 6L;
    private static final int BIOME_HINT_DISCOVERY_RADIUS = 256;
    private static final int STRUCTURE_HINT_DISCOVERY_RADIUS = 128;
    private static final Map<CartographerMapGiftKey, Long> CARTOGRAPHER_MAP_GIFTS = new HashMap<>();
    private static MinecraftServer mapGiftServer;

    private VillagerStoryHintService() {
    }

    public static void clearRuntimeState() {
        CARTOGRAPHER_MAP_GIFTS.clear();
        mapGiftServer = null;
    }

    public static Optional<VillagerDialogueService.DialogueResult> select(DialogueContext context) {
        HintQuality quality = HintQuality.forReputation(context.reputationLevel());
        if (quality == HintQuality.NONE || !DialogueTuningResources.passes(
                context,
                "story_hint." + quality.name().toLowerCase(Locale.ROOT) + "_chance",
                quality.chancePercent / 100.0D)) {
            return Optional.empty();
        }

        Optional<WorldHint> hint = selectWorldHint(context, quality);
        return hint.map(worldHint -> new VillagerDialogueService.DialogueResult(
                "story_hint_" + worldHint.kind().name().toLowerCase(Locale.ROOT),
                worldHint.text()
        ));
    }

    public static Optional<VillagerDialogueService.DialogueResult> selectCartographerMapReport(DialogueContext context) {
        return VillagerInteractionTracker.claimUnreportedCartographerMapDiscovery(context.level(), context.villager(), context.player())
                .map(report -> {
                    String targetName = report.targetName() == null || report.targetName().isBlank()
                            ? VillagerInteractionTextUtil.resourcePathName(report.structureId())
                            : report.targetName();
                    String category = structureReportCategory(report.structureId());
                    Map<String, String> replacements = Map.of(
                            "target", targetName,
                            "target_article", withArticle(targetName)
                    );
                    String text = VillagerDialogueResources
                            .message(context, "cartographer_map_report.structure." + category, replacements)
                            .or(() -> VillagerDialogueResources.message(context, "cartographer_map_report.structure.generic", replacements))
                            .orElse("");
                    return new VillagerDialogueService.DialogueResult(
                            "cartographer_map_report_" + category + "_" + report.structureId().getPath(),
                            text
                    );
                });
    }

    public static Optional<VillagerDialogueService.DialogueResult> selectStoryHintReport(DialogueContext context) {
        return VillagerInteractionTracker.claimUnreportedStoryHintDiscovery(context.level(), context.villager(), context.player())
                .map(report -> {
                    String targetName = report.targetName() == null || report.targetName().isBlank()
                            ? VillagerInteractionTextUtil.resourcePathName(report.targetId())
                            : report.targetName();
                    Map<String, String> replacements = Map.of(
                            "target", targetName,
                            "target_article", withArticle(targetName)
                    );
                    String key = report.kind() == VillagerInteractionTracker.StoryHintKind.BIOME
                            ? "story_hint_report.biome"
                            : "story_hint_report.structure." + structureReportCategory(report.targetId());
                    String fallbackKey = report.kind() == VillagerInteractionTracker.StoryHintKind.BIOME
                            ? "story_hint_report.generic"
                            : "story_hint_report.structure.generic";
                    String text = VillagerDialogueResources
                            .message(context, key, replacements)
                            .or(() -> VillagerDialogueResources.message(context, fallbackKey, replacements))
                            .orElse("");
                    return new VillagerDialogueService.DialogueResult(
                            "story_hint_report_" + report.kind().name().toLowerCase(Locale.ROOT) + "_" + report.targetId().getPath(),
                            text
                    );
                });
    }

    public static Optional<VillagerDialogueService.DialogueResult> selectSharedStory(
            DialogueContext context,
            DialogueOptionDefinition option,
            List<String> recentDialogueIds) {
        return VillagerInteractionTracker.claimShareableStory(context.level(), context.villager(), context.player())
                .map(report -> {
                    String targetName = report.targetName() == null || report.targetName().isBlank()
                            ? VillagerInteractionTextUtil.resourcePathName(report.targetId())
                            : report.targetName();
                    VillagerDialogueService.DialogueResult result = VillagerDialogueService.select(context, option, recentDialogueIds);
                    Map<String, String> replacements = Map.of(
                            "target", targetName,
                            "target_article", withArticle(targetName)
                    );
                    return new VillagerDialogueService.DialogueResult(
                            "share_story_" + report.targetId().getPath() + "_" + result.lineId(),
                            VillagerDialogueResources.resolveTemplate(result.text(), replacements)
                    );
                });
    }

    private static Optional<WorldHint> selectWorldHint(DialogueContext context, HintQuality quality) {
        boolean tryStructureFirst = quality.canRevealStructures && context.random().nextBoolean();
        if (tryStructureFirst) {
            Optional<WorldHint> structureHint = findStructureHint(context, quality);
            if (structureHint.isPresent()) {
                return structureHint;
            }
        }

        Optional<WorldHint> biomeHint = findBiomeHint(context, quality);
        if (biomeHint.isPresent()) {
            return biomeHint;
        }

        return quality.canRevealStructures ? findStructureHint(context, quality) : Optional.empty();
    }

    private static Optional<WorldHint> findBiomeHint(DialogueContext context, HintQuality quality) {
        ServerLevel level = context.level();
        BlockPos origin = context.villager().blockPosition();
        Holder<Biome> currentBiome = level.getBiome(origin);
        List<CachedTarget> targets = locateBiomeTargets(context, quality, currentBiome);
        if (targets.isEmpty()) {
            return Optional.empty();
        }

        CachedTarget target = targets.get(context.random().nextInt(targets.size()));
        String name = biomeTargetName(level, target.id());
        HintPlacement placement = HintPlacement.from(origin, target.pos(), quality);
        rememberStoryHint(context, target, name);
        return Optional.of(new WorldHint(HintKind.BIOME, biomeText(context, name, placement, quality)));
    }

    private static List<CachedTarget> locateBiomeTargets(DialogueContext context, HintQuality quality, Holder<Biome> currentBiome) {
        ServerLevel level = context.level();
        BlockPos origin = context.villager().blockPosition();
        Map<ResourceLocation, BiomeStoryResources.Entry> storyBiomes =
                BiomeStoryResources.entriesByBiome(level.getServer());
        if (storyBiomes.isEmpty()) {
            return List.of();
        }

        int minRadius = Math.max(160, quality.biomeMinRadius);
        int maxRadius = Math.max(minRadius, quality.biomeRadius);
        int attempts = Math.max(48, quality.biomePoolSize * 24);
        ResourceLocation currentBiomeId = keyLocation(currentBiome).orElse(null);
        List<CachedTarget> targets = new ArrayList<>();
        for (VillagerWorldTargetCache.LocatedBiome located : VillagerWorldTargetCache.findBiomeSamples(
                level,
                origin,
                storyBiomes.keySet(),
                currentBiomeId,
                minRadius,
                maxRadius,
                quality.biomePoolSize,
                attempts,
                context.random())) {
            targets.add(new CachedTarget(HintKind.BIOME, located.id(), located.pos()));
        }
        return targets;
    }

    private static Optional<WorldHint> findStructureHint(DialogueContext context, HintQuality quality) {
        ServerLevel level = context.level();
        BlockPos origin = context.villager().blockPosition();
        List<CachedTarget> targets = locateStructureTargets(context, quality);
        if (targets.isEmpty()) {
            return Optional.empty();
        }

        CachedTarget target = targets.get(context.random().nextInt(targets.size()));
        String name = structureTargetName(level, target.id());
        HintPlacement placement = HintPlacement.fromStructure(origin, target.pos(), target.id(), quality);
        boolean gaveMap = maybeGiveCartographerMap(context, target, name);
        if (!gaveMap) {
            rememberStoryHint(context, target, name);
        }
        return Optional.of(new WorldHint(
                gaveMap ? HintKind.MAP : HintKind.STRUCTURE,
                gaveMap
                        ? cartographerMapText(context, name, placement)
                        : structureText(context, name, placement, quality)
        ));
    }

    private static List<CachedTarget> locateStructureTargets(DialogueContext context, HintQuality quality) {
        ServerLevel level = context.level();
        if (!level.getServer().getWorldData().worldGenOptions().generateStructures()) {
            return List.of();
        }
        BlockPos origin = context.villager().blockPosition();
        List<ResourceLocation> structures = DangerousStructureStoryResources.entries(level.getServer()).stream()
                .map(DangerousStructureStoryResources.Entry::structureId)
                .toList();
        if (structures.isEmpty()) {
            return List.of();
        }

        return VillagerWorldTargetCache.findNearestStructure(level, origin, structures, quality.structureRadius)
                .filter(target -> HintPlacement.fromStructure(origin, target.pos(), target.structureId(), quality).horizontalDistance
                        >= MIN_STRUCTURE_DISTANCE)
                .map(target -> List.of(new CachedTarget(HintKind.STRUCTURE, target.structureId(), target.pos())))
                .orElseGet(List::of);
    }

    private static void rememberStoryHint(DialogueContext context, CachedTarget target, String targetName) {
        VillagerInteractionTracker.StoryHintKind kind = switch (target.kind()) {
            case BIOME -> VillagerInteractionTracker.StoryHintKind.BIOME;
            case STRUCTURE -> VillagerInteractionTracker.StoryHintKind.STRUCTURE;
            case MAP -> null;
        };
        if (kind == null) {
            return;
        }
        VillagerInteractionTracker.rememberStoryHint(
                context.level(),
                context.villager(),
                context.player(),
                kind,
                target.id(),
                targetName,
                target.pos(),
                context.level().getGameTime() + STORY_HINT_DISCOVERY_TICKS,
                discoveryRadiusFor(context, target)
        );
    }

    private static int discoveryRadiusFor(DialogueContext context, CachedTarget target) {
        if (target.kind() == HintKind.BIOME) {
            return BIOME_HINT_DISCOVERY_RADIUS;
        }
        return DangerousStructureStoryResources.entries(context.level().getServer())
                .stream()
                .filter(entry -> VillagerWorldTargetCache.sameStructureId(entry.structureId(), target.id()))
                .findFirst()
                .map(DangerousStructureStoryResources.Entry::radius)
                .orElse(STRUCTURE_HINT_DISCOVERY_RADIUS);
    }

    private static boolean maybeGiveCartographerMap(DialogueContext context, CachedTarget target, String targetName) {
        if (context.profession() != VillagerProfession.CARTOGRAPHER
                || target.kind() != HintKind.STRUCTURE
                || !DialogueTuningResources.passes(context, cartographerMapChance(context))) {
            return false;
        }

        ensureMapGiftServer(context.level().getServer());
        CartographerMapGiftKey giftKey = new CartographerMapGiftKey(context.player().getUUID(), context.villager().getUUID());
        long gameTime = context.level().getGameTime();
        Long nextGiftTime = CARTOGRAPHER_MAP_GIFTS.get(giftKey);
        if (nextGiftTime != null && nextGiftTime > gameTime) {
            return false;
        }

        ItemStack map = createExplorerMap(context.level(), target, targetName);
        ItemStack remainder = map.copy();
        if (!context.player().addItem(remainder) && !remainder.isEmpty()) {
            context.player().drop(remainder, false);
        }
        VillagerInteractionTracker.rememberCartographerMap(
                context.level(),
                context.villager(),
                context.player(),
                target.id(),
                targetName,
                target.pos(),
                gameTime + CARTOGRAPHER_MAP_DISCOVERY_TICKS
        );
        VillagerInteractionService.sendReceivedItemNotice(context.player(), context.villager(), map);

        CARTOGRAPHER_MAP_GIFTS.put(giftKey, gameTime + CARTOGRAPHER_MAP_COOLDOWN_TICKS);
        context.villager().playSound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.8F, 0.9F + context.random().nextFloat() * 0.2F);
        pruneMapGiftCooldowns(gameTime);
        return true;
    }

    public static boolean maybeGiveQuestTargetMap(
            DialogueContext context,
            ResourceLocation structureId,
            ResourceKey<Level> targetDimension,
            BlockPos targetPos,
            String targetName) {
        if (context == null
                || structureId == null
                || targetPos == null
                || context.profession() != VillagerProfession.CARTOGRAPHER
                || !DialogueTuningResources.passes(context, cartographerMapChance(context))) {
            return false;
        }

        ensureMapGiftServer(context.level().getServer());
        CartographerMapGiftKey giftKey = new CartographerMapGiftKey(context.player().getUUID(), context.villager().getUUID());
        long gameTime = context.level().getGameTime();
        Long nextGiftTime = CARTOGRAPHER_MAP_GIFTS.get(giftKey);
        if (nextGiftTime != null && nextGiftTime > gameTime) {
            return false;
        }

        ServerLevel mapLevel = targetDimension == null
                ? context.level()
                : context.level().getServer().getLevel(targetDimension);
        if (mapLevel == null) {
            return false;
        }

        String name = targetName == null || targetName.isBlank()
                ? VillagerInteractionTextUtil.resourcePathName(structureId)
                : targetName;
        ItemStack map = createExplorerMap(mapLevel, new CachedTarget(HintKind.STRUCTURE, structureId, targetPos), name);
        ItemStack remainder = map.copy();
        if (!context.player().addItem(remainder) && !remainder.isEmpty()) {
            context.player().drop(remainder, false);
        }
        VillagerInteractionService.sendReceivedItemNotice(context.player(), context.villager(), map);

        CARTOGRAPHER_MAP_GIFTS.put(giftKey, gameTime + CARTOGRAPHER_MAP_COOLDOWN_TICKS);
        context.villager().playSound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.8F, 0.9F + context.random().nextFloat() * 0.2F);
        pruneMapGiftCooldowns(gameTime);
        return true;
    }

    private static ItemStack createExplorerMap(ServerLevel level, CachedTarget target, String targetName) {
        ItemStack map = MapItem.create(level, target.pos().getX(), target.pos().getZ(), (byte) 2, true, true);
        MapItemSavedData.addTargetDecoration(map, target.pos(), "+", MapDecorationTypes.RED_X);
        map.set(DataComponents.ITEM_NAME, Component.translatable("item.villagerretaliation.story_map", targetName));
        MapItem.renderBiomePreviewMap(level, map);
        return map;
    }

    private static void pruneMapGiftCooldowns(long gameTime) {
        if (CARTOGRAPHER_MAP_GIFTS.size() <= 256) {
            return;
        }
        CARTOGRAPHER_MAP_GIFTS.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
    }

    private static void ensureMapGiftServer(MinecraftServer server) {
        if (mapGiftServer == server) {
            return;
        }
        CARTOGRAPHER_MAP_GIFTS.clear();
        mapGiftServer = server;
    }

    private static Optional<ResourceLocation> keyLocation(Holder<?> holder) {
        return holder.unwrapKey().map(ResourceKey::location);
    }

    private static double cartographerMapChance(DialogueContext context) {
        String level = context.reputationLevel().name().toLowerCase(Locale.ROOT);
        double fallback = switch (context.reputationLevel()) {
            case ROYALTY -> 0.09D;
            case REVERED -> 0.06D;
            case RESPECTED -> 0.03D;
            default -> 0.0D;
        };
        return DialogueTuningResources.value(context, "cartographer_map." + level + "_chance", fallback);
    }

    private static String biomeText(DialogueContext context, String name, HintPlacement placement, HintQuality quality) {
        Map<String, String> replacements = hintReplacements(name, placement);
        if (!quality.namesTargets) {
            return VillagerDialogueResources.message(context, "story_hint.biome.vague", replacements).orElse("");
        }

        if (!quality.namesDistances) {
            return VillagerDialogueResources.message(context, "story_hint.biome.named", replacements).orElse("");
        }

        return VillagerDialogueResources.message(context, "story_hint.biome.precise", replacements).orElse("");
    }

    private static String structureText(DialogueContext context, String name, HintPlacement placement, HintQuality quality) {
        Map<String, String> replacements = hintReplacements(name, placement);
        if (!quality.namesDistances) {
            return VillagerDialogueResources.message(context, "story_hint.structure.rumor", replacements).orElse("");
        }

        return VillagerDialogueResources.message(context, "story_hint.structure.precise", replacements).orElse("");
    }

    private static String cartographerMapText(DialogueContext context, String name, HintPlacement placement) {
        return VillagerDialogueResources.message(context, "story_hint.map", hintReplacements(name, placement)).orElse("");
    }

    private static String biomeTargetName(ServerLevel level, ResourceLocation biomeId) {
        BiomeStoryResources.Entry entry = BiomeStoryResources.entriesByBiome(level.getServer()).get(biomeId);
        return entry == null ? VillagerInteractionTextUtil.resourcePathName(biomeId) : entry.targetName();
    }

    private static String structureTargetName(ServerLevel level, ResourceLocation structureId) {
        return DangerousStructureStoryResources.entries(level.getServer()).stream()
                .filter(entry -> VillagerWorldTargetCache.sameStructureId(entry.structureId(), structureId))
                .findFirst()
                .map(DangerousStructureStoryResources.Entry::targetName)
                .orElseGet(() -> VillagerInteractionTextUtil.resourcePathName(structureId));
    }

    private static String structureReportCategory(ResourceLocation structureId) {
        String path = structureId.getPath();
        if (path.startsWith("village")) {
            return "village";
        }
        if (path.contains("pyramid") || path.contains("temple") || path.equals("igloo")) {
            return "temple";
        }
        if (path.contains("mansion")) {
            return "mansion";
        }
        if (path.contains("monument")) {
            return "monument";
        }
        if (path.contains("mineshaft")) {
            return "mineshaft";
        }
        if (path.contains("ancient_city")) {
            return "ancient_city";
        }
        if (path.contains("stronghold")) {
            return "stronghold";
        }
        if (path.contains("fortress") || path.contains("bastion")) {
            return "nether";
        }
        if (path.contains("outpost")) {
            return "outpost";
        }
        if (path.contains("shipwreck") || path.contains("ruin")) {
            return "ruins";
        }
        if (path.contains("trial_chambers")) {
            return "trial_chambers";
        }
        return "generic";
    }

    private static Map<String, String> hintReplacements(String name, HintPlacement placement) {
        return Map.of(
                "target", name,
                "target_article", withArticle(name),
                "direction", placement.direction(),
                "vague_direction", placement.vagueDirectionPhrase(),
                "distance", placement.distancePhrase(),
                "vertical", placement.verticalPhrase()
        );
    }

    private static String withArticle(String name) {
        return VillagerInteractionTextUtil.withIndefiniteArticle(name);
    }

    private enum HintKind {
        BIOME,
        STRUCTURE,
        MAP
    }

    private enum HintQuality {
        NONE(0, 0, 0, 0, 0, 0, 0, false, false, false),
        VAGUE(12, 700, 160, 0, 1, 0, 0, false, false, false),
        BIOME_NAME(24, 1100, 220, 0, 2, 0, 0, true, false, false),
        PRECISE_BIOME(34, 1800, 360, 0, 3, 0, 50, true, true, false),
        STRUCTURE_RUMOR(46, 2400, 480, 96, 3, 2, 100, true, false, true),
        PRECISE_STRUCTURE(58, 3600, 720, 160, 4, 3, 50, true, true, true);

        private final int chancePercent;
        private final int biomeRadius;
        private final int biomeMinRadius;
        private final int structureRadius;
        private final int biomePoolSize;
        private final int structurePoolSize;
        private final int distanceRoundBlocks;
        private final boolean namesTargets;
        private final boolean namesDistances;
        private final boolean canRevealStructures;

        HintQuality(
                int chancePercent,
                int biomeRadius,
                int biomeMinRadius,
                int structureRadius,
                int biomePoolSize,
                int structurePoolSize,
                int distanceRoundBlocks,
                boolean namesTargets,
                boolean namesDistances,
                boolean canRevealStructures) {
            this.chancePercent = chancePercent;
            this.biomeRadius = biomeRadius;
            this.biomeMinRadius = biomeMinRadius;
            this.structureRadius = structureRadius;
            this.biomePoolSize = biomePoolSize;
            this.structurePoolSize = structurePoolSize;
            this.distanceRoundBlocks = distanceRoundBlocks;
            this.namesTargets = namesTargets;
            this.namesDistances = namesDistances;
            this.canRevealStructures = canRevealStructures;
        }

        private static HintQuality forReputation(VillagerReputationLevel reputationLevel) {
            return switch (reputationLevel) {
                case ROYALTY, REVERED -> PRECISE_STRUCTURE;
                case RESPECTED -> STRUCTURE_RUMOR;
                case TRUSTED -> PRECISE_BIOME;
                case NEUTRAL -> BIOME_NAME;
                case SUSPICIOUS -> VAGUE;
                case HOSTILE, DESPISED, FEARED -> NONE;
            };
        }
    }

    private record HintPlacement(String direction, int horizontalDistance, int yDelta, HintQuality quality, boolean reliableVertical) {
        private static HintPlacement from(BlockPos origin, BlockPos target, HintQuality quality) {
            return from(origin, target, quality, true);
        }

        private static HintPlacement fromStructure(BlockPos origin, BlockPos target, ResourceLocation structureId, HintQuality quality) {
            return from(origin, target, quality, hasReliableStructureVertical(structureId, target));
        }

        private static HintPlacement from(BlockPos origin, BlockPos target, HintQuality quality, boolean reliableVertical) {
            int dx = target.getX() - origin.getX();
            int dz = target.getZ() - origin.getZ();
            int horizontalDistance = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
            return new HintPlacement(direction(dx, dz), horizontalDistance, target.getY() - origin.getY(), quality, reliableVertical);
        }

        private String vagueDirectionPhrase() {
            return switch (this.direction) {
                case "north", "south", "east", "west" -> "to the " + this.direction;
                default -> this.direction;
            };
        }

        private String distancePhrase() {
            int step = Math.max(1, this.quality.distanceRoundBlocks);
            int rounded = Math.max(step, Math.round((float) this.horizontalDistance / step) * step);
            return rounded + " blocks";
        }

        private String verticalPhrase() {
            if (!this.reliableVertical) {
                return "";
            }
            if (this.yDelta < -36) {
                return ", deep underground";
            }
            if (this.yDelta < -16) {
                return ", below the surface";
            }
            if (this.yDelta > 48) {
                return ", high above the usual paths";
            }
            return "";
        }

        private static boolean hasReliableStructureVertical(ResourceLocation structureId, BlockPos target) {
            if (target.getY() == 0) {
                return false;
            }
            return !"village".equals(structureReportCategory(structureId));
        }

        private static String direction(int dx, int dz) {
            double angle = Math.atan2(dz, dx);
            double eighth = Math.PI / 8.0D;
            if (angle >= -eighth && angle < eighth) {
                return "east";
            }
            if (angle >= eighth && angle < 3.0D * eighth) {
                return "southeast";
            }
            if (angle >= 3.0D * eighth && angle < 5.0D * eighth) {
                return "south";
            }
            if (angle >= 5.0D * eighth && angle < 7.0D * eighth) {
                return "southwest";
            }
            if (angle >= 7.0D * eighth || angle < -7.0D * eighth) {
                return "west";
            }
            if (angle >= -7.0D * eighth && angle < -5.0D * eighth) {
                return "northwest";
            }
            if (angle >= -5.0D * eighth && angle < -3.0D * eighth) {
                return "north";
            }
            return "northeast";
        }
    }

    private record CachedTarget(HintKind kind, ResourceLocation id, BlockPos pos) {
    }

    private record CartographerMapGiftKey(UUID playerId, UUID villagerId) {
    }

    private record WorldHint(HintKind kind, String text) {
    }
}

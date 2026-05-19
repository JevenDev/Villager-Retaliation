package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class VillagerStoryHintService {
    private static final int MIN_STRUCTURE_DISTANCE = 96;
    private static final int CACHE_REGION_SHIFT = 8;
    private static final long POSITIVE_CACHE_TICKS = 20L * 60L * 10L;
    private static final long NEGATIVE_CACHE_TICKS = 20L * 30L;
    private static final long CARTOGRAPHER_MAP_COOLDOWN_TICKS = 20L * 60L * 20L * 2L;
    private static final Map<HintCacheKey, CachedLookup> CACHE = new HashMap<>();
    private static final Map<CartographerMapGiftKey, Long> CARTOGRAPHER_MAP_GIFTS = new HashMap<>();

    private VillagerStoryHintService() {
    }

    public static Optional<VillagerDialogueService.DialogueResult> select(DialogueContext context) {
        HintQuality quality = HintQuality.forReputation(context.reputationLevel());
        if (quality == HintQuality.NONE || context.random().nextInt(100) >= quality.chancePercent) {
            return Optional.empty();
        }

        Optional<WorldHint> hint = selectWorldHint(context, quality);
        return hint.map(worldHint -> new VillagerDialogueService.DialogueResult(
                "story_hint_" + worldHint.kind().name().toLowerCase(Locale.ROOT),
                worldHint.text()
        ));
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
        HintCacheKey cacheKey = HintCacheKey.create(level, origin, quality, HintKind.BIOME, keyLocation(currentBiome).orElse(null));
        Optional<CachedLookup> cached = getCached(level, cacheKey);
        if (cached.isPresent()) {
            return cached.get().nextTarget(context.random()).map(target -> {
                String name = humanName(target.id());
                HintPlacement placement = HintPlacement.from(origin, target.pos(), quality);
                return new WorldHint(HintKind.BIOME, biomeText(context.random(), name, placement, quality));
            });
        }

        List<CachedTarget> targets = locateBiomeTargets(context, quality, currentBiome);
        cache(level, cacheKey, targets);
        if (targets.isEmpty()) {
            return Optional.empty();
        }

        CachedTarget target = targets.get(context.random().nextInt(targets.size()));
        String name = humanName(target.id());
        HintPlacement placement = HintPlacement.from(origin, target.pos(), quality);
        return Optional.of(new WorldHint(HintKind.BIOME, biomeText(context.random(), name, placement, quality)));
    }

    private static List<CachedTarget> locateBiomeTargets(DialogueContext context, HintQuality quality, Holder<Biome> currentBiome) {
        ServerLevel level = context.level();
        BlockPos origin = context.villager().blockPosition();
        List<CachedTarget> targets = new ArrayList<>();
        int samplesPerRing = 12;
        int ringStep = Math.max(160, quality.biomeRadius / 5);
        double angleOffset = context.random().nextDouble() * Math.PI * 2.0D;
        for (int radius = Math.max(160, quality.biomeMinRadius); radius <= quality.biomeRadius && targets.size() < quality.biomePoolSize; radius += ringStep) {
            for (int sample = 0; sample < samplesPerRing && targets.size() < quality.biomePoolSize; sample++) {
                double angle = angleOffset + (Math.PI * 2.0D * sample / samplesPerRing);
                int x = origin.getX() + (int) Math.round(Math.cos(angle) * radius);
                int z = origin.getZ() + (int) Math.round(Math.sin(angle) * radius);
                Holder<Biome> biome = level.getUncachedNoiseBiome(
                        QuartPos.fromBlock(x),
                        QuartPos.fromBlock(origin.getY()),
                        QuartPos.fromBlock(z)
                );
                ResourceLocation biomeId = keyLocation(biome).orElse(null);
                if (biomeId != null && !biome.is(currentBiome) && isNewTarget(targets, biomeId, x, z)) {
                    targets.add(new CachedTarget(HintKind.BIOME, biomeId, new BlockPos(x, origin.getY(), z)));
                }
            }
        }
        return targets;
    }

    private static Optional<WorldHint> findStructureHint(DialogueContext context, HintQuality quality) {
        ServerLevel level = context.level();
        BlockPos origin = context.villager().blockPosition();
        HintCacheKey cacheKey = HintCacheKey.create(level, origin, quality, HintKind.STRUCTURE, null);
        Optional<CachedLookup> cached = getCached(level, cacheKey);
        if (cached.isPresent()) {
            return cached.get().nextTarget(context.random()).map(target -> {
                String name = humanName(target.id());
                HintPlacement placement = HintPlacement.from(origin, target.pos(), quality);
                maybeGiveCartographerMap(context, target, name);
                return new WorldHint(HintKind.STRUCTURE, structureText(context.random(), name, placement, quality));
            });
        }

        List<CachedTarget> targets = locateStructureTargets(context, quality);
        cache(level, cacheKey, targets);
        if (targets.isEmpty()) {
            return Optional.empty();
        }

        CachedTarget target = targets.get(context.random().nextInt(targets.size()));
        String name = humanName(target.id());
        HintPlacement placement = HintPlacement.from(origin, target.pos(), quality);
        maybeGiveCartographerMap(context, target, name);
        return Optional.of(new WorldHint(
                HintKind.STRUCTURE,
                structureText(context.random(), name, placement, quality)
        ));
    }

    private static List<CachedTarget> locateStructureTargets(DialogueContext context, HintQuality quality) {
        ServerLevel level = context.level();
        BlockPos origin = context.villager().blockPosition();
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<Holder.Reference<Structure>> structures = new ArrayList<>(registry.holders().toList());
        if (structures.isEmpty()) {
            return List.of();
        }

        List<CachedTarget> targets = new ArrayList<>();
        List<Holder.Reference<Structure>> remaining = new ArrayList<>(structures);
        for (int index = 0; index < quality.structurePoolSize && !remaining.isEmpty(); index++) {
            Pair<BlockPos, Holder<Structure>> nearest = level.getChunkSource().getGenerator().findNearestMapStructure(
                    level,
                    HolderSet.direct(remaining),
                    origin,
                    quality.structureRadius,
                    false
            );
            if (nearest == null) {
                break;
            }

            HintPlacement placement = HintPlacement.from(origin, nearest.getFirst(), quality);
            ResourceLocation structureId = keyLocation(nearest.getSecond()).orElse(null);
            if (placement.horizontalDistance >= MIN_STRUCTURE_DISTANCE
                    && structureId != null
                    && isNewTarget(targets, structureId, nearest.getFirst().getX(), nearest.getFirst().getZ())) {
                targets.add(new CachedTarget(HintKind.STRUCTURE, structureId, nearest.getFirst()));
            }
            ResourceLocation foundStructureId = structureId;
            remaining.removeIf(structure -> foundStructureId != null && keyLocation(structure).map(foundStructureId::equals).orElse(false));
        }
        return targets;
    }

    private static void maybeGiveCartographerMap(DialogueContext context, CachedTarget target, String targetName) {
        if (context.profession() != VillagerProfession.CARTOGRAPHER
                || target.kind() != HintKind.STRUCTURE
                || context.random().nextInt(100) >= cartographerMapChancePercent(context.reputationLevel())) {
            return;
        }

        CartographerMapGiftKey giftKey = new CartographerMapGiftKey(context.player().getUUID(), context.villager().getUUID());
        long gameTime = context.level().getGameTime();
        Long nextGiftTime = CARTOGRAPHER_MAP_GIFTS.get(giftKey);
        if (nextGiftTime != null && nextGiftTime > gameTime) {
            return;
        }

        ItemStack map = createExplorerMap(context.level(), target, targetName);
        ItemStack remainder = map.copy();
        if (!context.player().addItem(remainder) && !remainder.isEmpty()) {
            context.player().drop(remainder, false);
        }

        CARTOGRAPHER_MAP_GIFTS.put(giftKey, gameTime + CARTOGRAPHER_MAP_COOLDOWN_TICKS);
        context.villager().playSound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.8F, 0.9F + context.random().nextFloat() * 0.2F);
        pruneMapGiftCooldowns(gameTime);
    }

    private static ItemStack createExplorerMap(ServerLevel level, CachedTarget target, String targetName) {
        ItemStack map = MapItem.create(level, target.pos().getX(), target.pos().getZ(), (byte) 2, true, true);
        MapItemSavedData.addTargetDecoration(map, target.pos(), "+", MapDecorationTypes.RED_X);
        map.set(DataComponents.ITEM_NAME, Component.literal("Map to " + targetName));
        MapItem.renderBiomePreviewMap(level, map);
        return map;
    }

    private static void pruneMapGiftCooldowns(long gameTime) {
        if (CARTOGRAPHER_MAP_GIFTS.size() <= 256) {
            return;
        }
        CARTOGRAPHER_MAP_GIFTS.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
    }

    private static Optional<CachedLookup> getCached(ServerLevel level, HintCacheKey cacheKey) {
        long gameTime = level.getGameTime();
        CachedLookup cached = CACHE.get(cacheKey);
        if (cached == null) {
            return Optional.empty();
        }
        if (cached.expiresAt() <= gameTime) {
            CACHE.remove(cacheKey);
            return Optional.empty();
        }
        return Optional.of(cached);
    }

    private static void cache(ServerLevel level, HintCacheKey cacheKey, List<CachedTarget> targets) {
        long cacheTicks = targets.isEmpty() ? NEGATIVE_CACHE_TICKS : POSITIVE_CACHE_TICKS;
        CACHE.put(cacheKey, new CachedLookup(List.copyOf(targets), level.getGameTime() + cacheTicks));
        if (CACHE.size() > 256) {
            pruneExpiredCache(level.getGameTime());
        }
    }

    private static void pruneExpiredCache(long gameTime) {
        CACHE.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= gameTime);
        if (CACHE.size() > 256) {
            CACHE.clear();
        }
    }

    private static Optional<ResourceLocation> keyLocation(Holder<?> holder) {
        return holder.unwrapKey().map(ResourceKey::location);
    }

    private static boolean isNewTarget(List<CachedTarget> targets, ResourceLocation id, int x, int z) {
        return targets.stream().noneMatch(target ->
                target.id().equals(id) || target.pos().distSqr(new BlockPos(x, target.pos().getY(), z)) < 96.0D * 96.0D
        );
    }

    private static int cartographerMapChancePercent(VillagerReputationLevel reputationLevel) {
        return switch (reputationLevel) {
            case ROYALTY -> 9;
            case REVERED -> 6;
            case RESPECTED -> 3;
            default -> 0;
        };
    }

    private static String biomeText(RandomSource random, String name, HintPlacement placement, HintQuality quality) {
        if (!quality.namesTargets) {
            return pick(random,
                    "I caught wind of different land " + placement.vagueDirectionPhrase() + ". Could be worth a walk.",
                    "Travelers keep arriving with mud on their boots from " + placement.vagueDirectionPhrase() + ".",
                    "There is a change in the air " + placement.vagueDirectionPhrase() + ". Different trees, different ground.",
                    "The paths feel busier " + placement.vagueDirectionPhrase() + ". Someone found another kind of country out there.",
                    "I heard talk of unfamiliar grass and stone " + placement.vagueDirectionPhrase() + "."
            );
        }

        if (!quality.namesDistances) {
            return pick(random,
                    "I caught wind of " + article(name) + " " + placement.vagueDirectionPhrase() + ".",
                    "Someone came through talking about " + article(name) + " " + placement.vagueDirectionPhrase() + ".",
                    "If you head " + placement.direction + ", you may find " + article(name) + " before too long.",
                    "The traders mention " + article(name) + " somewhere " + placement.vagueDirectionPhrase() + ".",
                    "I keep hearing about " + article(name) + " off " + placement.vagueDirectionPhrase() + "."
            );
        }

        return pick(random,
                "I caught wind of " + article(name) + " about " + placement.distancePhrase() + " " + placement.direction + ".",
                "A traveler swore there is " + article(name) + " roughly " + placement.distancePhrase() + " " + placement.direction + ".",
                "Head " + placement.direction + " for about " + placement.distancePhrase() + " and the land should turn into " + article(name) + ".",
                "The maps around here put " + article(name) + " near " + placement.distancePhrase() + " " + placement.direction + ".",
                "If the gossip is right, " + article(name) + " sits about " + placement.distancePhrase() + " " + placement.direction + ".",
                "A cart passed through with plants from " + article(name) + "; they said it was " + placement.distancePhrase() + " " + placement.direction + "."
        );
    }

    private static String structureText(RandomSource random, String name, HintPlacement placement, HintQuality quality) {
        String vertical = placement.verticalPhrase();
        if (!quality.namesDistances) {
            return pick(random,
                    "I keep hearing stories about " + article(name) + " somewhere " + placement.vagueDirectionPhrase() + vertical + ".",
                    "The road talk says there is " + article(name) + " out " + placement.vagueDirectionPhrase() + vertical + ".",
                    "Someone saw old stone " + placement.vagueDirectionPhrase() + vertical + ". Might have been " + article(name) + ".",
                    "There are rumors of " + article(name) + " toward the " + placement.direction + vertical + ".",
                    "A nervous miner mentioned " + article(name) + " off " + placement.vagueDirectionPhrase() + vertical + "."
            );
        }

        return pick(random,
                "I keep hearing about " + article(name) + " around " + placement.distancePhrase() + " " + placement.direction + vertical + ".",
                "A trader marked " + article(name) + " roughly " + placement.distancePhrase() + " " + placement.direction + vertical + ".",
                "If you head " + placement.direction + " for about " + placement.distancePhrase() + ", watch for " + article(name) + vertical + ".",
                "Someone heard strange echoes from " + article(name) + " nearly " + placement.distancePhrase() + " " + placement.direction + vertical + ".",
                "The last patrol saw signs of " + article(name) + " about " + placement.distancePhrase() + " " + placement.direction + vertical + ".",
                "I would not swear to it, but " + article(name) + " should be " + placement.distancePhrase() + " " + placement.direction + vertical + ".",
                "Old path stones point " + placement.direction + ". Follow them " + placement.distancePhrase() + " or so and you may find " + article(name) + vertical + "."
        );
    }

    private static String pick(RandomSource random, String... values) {
        return values[random.nextInt(values.length)];
    }

    private static String humanName(ResourceLocation id) {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < path.length()) {
            path = path.substring(slash + 1);
        }

        String[] words = path.replace('-', '_').split("_+");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1));
            }
        }
        return builder.isEmpty() ? id.toString() : builder.toString();
    }

    private static String article(String name) {
        if (name.isEmpty()) {
            return "somewhere";
        }
        char first = Character.toLowerCase(name.charAt(0));
        String article = first == 'a' || first == 'e' || first == 'i' || first == 'o' || first == 'u' ? "an " : "a ";
        return article + name;
    }

    private enum HintKind {
        BIOME,
        STRUCTURE
    }

    private enum HintQuality {
        NONE(0, 0, 0, 0, 0, 0, 0, false, false, false),
        VAGUE(12, 700, 160, 0, 1, 0, 0, false, false, false),
        BIOME_NAME(24, 1100, 220, 0, 2, 0, 0, true, false, false),
        PRECISE_BIOME(34, 1800, 360, 0, 3, 0, 64, true, true, false),
        STRUCTURE_RUMOR(46, 2400, 480, 96, 3, 2, 128, true, false, true),
        PRECISE_STRUCTURE(58, 3600, 720, 160, 4, 3, 64, true, true, true);

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

    private record HintPlacement(String direction, int horizontalDistance, int yDelta, HintQuality quality) {
        private static HintPlacement from(BlockPos origin, BlockPos target, HintQuality quality) {
            int dx = target.getX() - origin.getX();
            int dz = target.getZ() - origin.getZ();
            int horizontalDistance = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
            return new HintPlacement(direction(dx, dz), horizontalDistance, target.getY() - origin.getY(), quality);
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

    private record HintCacheKey(
            ResourceLocation dimension,
            int regionX,
            int regionZ,
            HintQuality quality,
            HintKind kind,
            ResourceLocation originType
    ) {
        private static HintCacheKey create(
                ServerLevel level,
                BlockPos origin,
                HintQuality quality,
                HintKind kind,
                ResourceLocation originType) {
            return new HintCacheKey(
                    level.dimension().location(),
                    origin.getX() >> CACHE_REGION_SHIFT,
                    origin.getZ() >> CACHE_REGION_SHIFT,
                    quality,
                    kind,
                    originType
            );
        }
    }

    private static final class CachedLookup {
        private final List<CachedTarget> targets;
        private final long expiresAt;
        private int nextIndex;

        private CachedLookup(List<CachedTarget> targets, long expiresAt) {
            this.targets = targets;
            this.expiresAt = expiresAt;
        }

        private Optional<CachedTarget> nextTarget(RandomSource random) {
            if (this.targets.isEmpty()) {
                return Optional.empty();
            }
            int index = this.nextIndex++ % this.targets.size();
            if (this.targets.size() > 2 && random.nextInt(100) < 20) {
                index = random.nextInt(this.targets.size());
            }
            return Optional.of(this.targets.get(index));
        }

        private long expiresAt() {
            return this.expiresAt;
        }
    }

    private record CachedTarget(HintKind kind, ResourceLocation id, BlockPos pos) {
    }

    private record CartographerMapGiftKey(UUID playerId, UUID villagerId) {
    }

    private record WorldHint(HintKind kind, String text) {
    }
}

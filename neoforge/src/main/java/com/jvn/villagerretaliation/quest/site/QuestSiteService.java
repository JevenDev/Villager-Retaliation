package com.jvn.villagerretaliation.quest.site;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.quest.follower.QuestFollowerService;
import com.jvn.villagerretaliation.quest.runtime.QuestRuntimes;
import com.jvn.villagerretaliation.quest.site.QuestSiteSavedData.Phase;
import com.jvn.villagerretaliation.quest.site.QuestSiteSavedData.Site;
import com.jvn.villagerretaliation.quest.tracking.VillagerQuestTargets;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = "villagerretaliation")
public final class QuestSiteService {
    private static final Gson GSON = new Gson();
    private QuestSiteService() { }

    static Site find(ServerLevel level, UUID player, ResourceLocation quest) {
        var progress = VillagerQuestSavedData.get(level).get(player, quest);
        var sites = QuestSiteSavedData.get(level).sites.values();
        if (progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE && progress.questRunId() != null) {
            var bound = sites.stream().filter(site -> site.quest.equals(quest) && progress.questRunId().equals(site.run))
                    .reduce((first, second) -> second).orElse(null);
            if (bound != null) return bound;
        }
        var party = com.jvn.villagerretaliation.party.PartyService.getPartyForPlayer(level, player).orElse(null);
        if (party != null && (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE)) {
            var shared = sites.stream().filter(site -> site.quest.equals(quest) && site.phase == Phase.ACTIVE && site.run != null
                    && party.sharedQuests().stream().anyMatch(record -> !record.completed() && record.instanceId().equals(site.run)))
                    .reduce((first, second) -> second).orElse(null);
            if (shared != null) return shared;
        }
        return sites.stream().filter(site -> site.quest.equals(quest) && site.player.equals(player))
                .reduce((first, second) -> second).orElse(null);
    }

    public static boolean offerAvailable(DialogueContext context, QuestDefinition quest) {
        if (quest.target().site() == null) return true;
        var site = ensure(context, quest);
        return site != null && (site.provider == null || context.villager() != null && site.provider.equals(context.villager().getUUID()))
                && (site.phase == Phase.PREPARING || site.phase == Phase.READY || site.phase == Phase.ACTIVE);
    }

    private static Site ensure(DialogueContext context, QuestDefinition quest) {
        var config = quest.target().site();
        if (quest.target().dimension() != null && !quest.target().dimension().equals(context.level().dimension())) return null;
        var data = QuestSiteSavedData.get(context.level());
        var site = find(context.level(), context.player().getUUID(), quest.id());
        if (site == null || site.phase == Phase.RETIRED) {
            long pending = data.sites.values().stream().filter(value -> value.phase != Phase.RETIRED && value.phase != Phase.UNAVAILABLE).count();
            if (pending >= 64) return null;
            site = new Site(UUID.randomUUID(), context.player().getUUID(), quest.id(),
                    context.villager() == null ? null : context.villager().getUUID(), context.level().dimension().location(), context.origin());
            site.config = GSON.toJson(config);
            site.requestedAt = context.level().getGameTime();
            data.add(site);
            data.setDirty();
        }
        return site;
    }

    public static Optional<VillagerQuestTargets.LocatedTarget> request(DialogueContext context, QuestDefinition quest) {
        var config = quest.target().site();
        if (config == null) return Optional.empty();
        if (quest.target().dimension() != null && !quest.target().dimension().equals(context.level().dimension())) return Optional.empty();
        var data = QuestSiteSavedData.get(context.level());
        var site = ensure(context, quest);
        if (site == null) return Optional.empty();
        if (site.provider != null && (context.villager() == null || !site.provider.equals(context.villager().getUUID()))) return Optional.empty();
        site.accepted = true;
        data.setDirty();
        if (site.phase == Phase.ACTIVE && site.position != null)
            return Optional.of(new VillagerQuestTargets.LocatedTarget(context.level().dimension(), site.position, ""));
        if (site.phase != Phase.READY || site.position == null) return Optional.empty();
        if (!QuestSitePlanner.loadForPreparation(context.level(), site, config)) return Optional.empty();
        if (site.dead || !QuestSitePlanner.usable(context.level(), site, config)) {
            unavailable(data, site, "The prepared quest site is no longer usable. No quest has been started.");
            return Optional.empty();
        }
        return Optional.of(new VillagerQuestTargets.LocatedTarget(context.level().dimension(), site.position, ""));
    }

    public static boolean preparing(DialogueContext context, QuestDefinition quest) {
        var site = find(context.level(), context.player().getUUID(), quest.id());
        return site != null && site.phase != Phase.UNAVAILABLE && site.phase != Phase.RETIRED;
    }

    public static String issue(DialogueContext context, QuestDefinition quest) {
        var site = find(context.level(), context.player().getUUID(), quest.id());
        return site == null ? "No safe quest site is available in this dimension."
                : site.phase == Phase.UNAVAILABLE ? site.diagnostic : "I am checking that lead. Stay nearby and I will mark your map when it is ready.";
    }

    public static void bind(DialogueContext context, QuestDefinition quest, UUID run) {
        var site = find(context.level(), context.player().getUUID(), quest.id());
        if (site != null && site.phase == Phase.ACTIVE && run.equals(site.run)) return;
        if (site == null || site.phase != Phase.READY) throw new IllegalStateException("Quest site is not prepared.");
        site.run = run;
        site.phase = Phase.ACTIVE;
        QuestSiteSavedData.get(context.level()).setDirty();
    }

    public static boolean discovered(ServerLevel level, UUID player, ResourceLocation quest) {
        var site = find(level, player, quest);
        return site == null || site.discovered;
    }

    public static BlockPos displayPosition(ServerLevel level, UUID player, QuestDefinition quest, BlockPos actual) {
        var config = quest.target().site();
        var site = config == null ? null : find(level, player, quest.id());
        if (site == null || site.discovered || config.mapStyle() != QuestSiteDefinition.MapStyle.SEARCH_CIRCLE) return actual;
        int radius = config.searchRadius();
        int dx = Math.floorMod(site.id.hashCode(), radius / 2 + 1) - radius / 4;
        int dz = Math.floorMod(site.id.hashCode() >>> 8, radius / 2 + 1) - radius / 4;
        return new BlockPos(site.position.getX() + dx, 0, site.position.getZ() + dz);
    }

    public static int displayRadius(ServerLevel level, UUID player, QuestDefinition quest) {
        return quest.target().site() != null && !discovered(level, player, quest.id())
                && quest.target().site().mapStyle() == QuestSiteDefinition.MapStyle.SEARCH_CIRCLE
                ? quest.target().site().searchRadius() : 0;
    }

    public static void tick(MinecraftServer server) {
        var data = QuestSiteSavedData.get(server.overworld());
        if (server.overworld().getGameTime() % 5 != 0) return;
        int preparing = 0;
        for (var site : data.tickingSites()) {
            if (site.phase == Phase.RETIRED) continue;
            if (!site.accepted && site.run == null && site.captive == null
                    && server.overworld().getGameTime() - site.requestedAt > 6000) {
                data.retire(site);
                site.position = null;
                site.pending = null;
                continue;
            }
            if (site.phase == Phase.UNAVAILABLE) {
                for (var player : server.getPlayerList().getPlayers()) {
                    if (owns(player.serverLevel(), site, player)) QuestRuntimes.forQuest(site.quest).fail(player, site.quest, site.run);
                }
                for (var candidate : server.getAllLevels()) {
                    var captive = site.captive == null ? null : candidate.getEntity(site.captive);
                    if (captive instanceof Mob mob) mob.setNoAi(false);
                }
                continue;
            }
            var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, site.dimension));
            var quest = VillagerQuestResources.quest(server, site.quest).orElse(null);
            if (level == null || quest == null || quest.target().site() == null) continue;
            var config = quest.target().site();
            if (!site.config.equals(GSON.toJson(config))) {
                unavailable(data, site, "Quest site requirements changed. The existing site has been preserved.");
                continue;
            }
            if (site.phase == Phase.ACTIVE) {
                maintain(level, data, site, quest);
                continue;
            }
            if (site.phase == Phase.PREPARING && !site.accepted) continue;
            if (site.phase != Phase.READY && ++preparing > 1) continue;
            if (site.pending == null) site.pendingSince = 0;
            else if (site.pendingSince == 0) site.pendingSince = level.getGameTime();
            else if (level.getGameTime() - site.pendingSince > 1200) {
                unavailable(data, site, "Quest site terrain took too long to load. No quest has been started.");
                continue;
            }
            try {
                switch (site.phase) {
                    case SEARCHING -> {
                        if (!QuestSitePlanner.next(level, site, config))
                            unavailable(data, site, "No suitable quest site could be found within the allowed travel distance.");
                        data.setDirty();
                    }
                    case CANDIDATE -> {
                        data.setDirty();
                        if (QuestSitePlanner.candidate(level, site, config)) {
                            site.phase = Phase.PREPARING;
                            data.setDirty();
                            var player = server.getPlayerList().getPlayer(site.player);
                            if (player != null) VillagerQuestService.refreshTracker(player);
                        }
                    }
                    case PREPARING -> prepare(level, data, site, config);
                    case READY -> resume(level, site);
                    default -> { }
                }
            } catch (java.util.concurrent.CompletionException exception) {
                com.mojang.logging.LogUtils.getLogger().error("Could not prepare quest site {}", site.id, exception);
                unavailable(data, site, "Quest site terrain could not be loaded. No quest has been started.");
            }
        }
    }

    static void prepare(ServerLevel level, QuestSiteSavedData data, Site site, QuestSiteDefinition config) {
        if (site.phase != Phase.PREPARING) return;
        if (!site.validated) {
            site.phase = Phase.CANDIDATE;
            site.pending = null;
            data.setDirty();
            return;
        }
        if (!QuestSitePlanner.loadForPreparation(level, site, config)) { data.setDirty(); return; }
        if (QuestSitePlanner.conflicts(level, site, site.position, config.buffer() + 16)) {
            unavailable(data, site, "The selected quest site is now occupied. No quest has been started.");
            return;
        }
        if (site.template != null) {
            var template = QuestSitePlanner.template(level, site).orElse(null);
            if (template == null || !QuestSitePlanner.clear(level, site.position, 8, 16, true)) {
                unavailable(data, site, "The quest site can no longer be placed safely.");
                return;
            }
            site.phase = Phase.PLACING;
            checkpoint(level, data);
            var origin = site.position.offset(-template.getSize().getX() / 2, -1, -template.getSize().getZ() / 2);
            var settings = new StructurePlaceSettings().setIgnoreEntities(true).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
            if (!template.placeInWorld(level, origin, origin, settings, level.random, 2)) {
                unavailable(data, site, "Quest structure placement failed. The site has been preserved.");
                return;
            }
        }
        if (config.rescue() != QuestSiteDefinition.Rescue.NONE) {
            site.captive = actorId(site, "captive");
            site.actorPosition = site.position;
            site.actorDimension = site.dimension;
            for (int i = 0; i < config.guards(); i++) site.guards.add(actorId(site, "guard/" + i));
            site.phase = Phase.SPAWNING;
            checkpoint(level, data);
            var captive = EntityType.VILLAGER.create(level);
            if (captive == null) { unavailable(data, site, "The captive could not be created."); return; }
            captive.setUUID(site.captive);
            captive.setNoAi(true);
            captive.setPersistenceRequired();
            captive.moveTo(site.position.getX() + .5, site.position.getY(), site.position.getZ() + .5, 0, 0);
            if (!level.noCollision(captive) || !level.addFreshEntity(captive)) {
                unavailable(data, site, "The captive's holding area is obstructed.");
                return;
            }
            for (int i = 0; i < site.guards.size(); i++) {
                var guard = EntityType.PILLAGER.create(level);
                if (guard == null) { unavailable(data, site, "A quest guard could not be created."); return; }
                guard.setUUID(site.guards.get(i));
                guard.setPersistenceRequired();
                guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.CROSSBOW));
                double angle = i * Math.PI * 2 / Math.max(1, site.guards.size());
                guard.moveTo(site.position.getX() + .5 + Math.cos(angle) * 3, site.position.getY(),
                        site.position.getZ() + .5 + Math.sin(angle) * 3, 0, 0);
                if (!level.noCollision(guard) || !level.addFreshEntity(guard)) {
                    unavailable(data, site, "A quest guard's position is obstructed.");
                    return;
                }
            }
        }
        site.phase = Phase.READY;
        checkpoint(level, data);
    }

    private static UUID actorId(Site site, String alias) {
        return UUID.nameUUIDFromBytes((site.id + "/" + alias).getBytes(StandardCharsets.UTF_8));
    }

    private static void checkpoint(ServerLevel level, QuestSiteSavedData data) {
        data.setDirty();
        level.getServer().overworld().getDataStorage().save();
    }

    private static void resume(ServerLevel level, Site site) {
        if (!site.accepted) return;
        var player = level.getServer().getPlayerList().getPlayer(site.player);
        if (player == null || player.level() != level) return;
        var provider = site.provider == null ? null : level.getEntity(site.provider);
        if (site.provider != null && (!(provider instanceof Villager) || !provider.isAlive()
                || provider.distanceToSqr(player) > 64 * 64)) return;
        var context = provider instanceof Villager villager
                ? VillagerInteractionService.createDialogueContext(level, player, villager) : DialogueContext.forPlayer(player);
        var result = VillagerQuestService.startDiscoveredQuest(context, site.quest);
        if (!result.status().equals("started") && !result.status().equals("locate_failed")
                && !result.status().equals("site_preparing")) unavailable(QuestSiteSavedData.get(level), site,
                "The quest can no longer be accepted. The prepared site has been preserved.");
    }

    private static boolean owns(ServerLevel level, Site site, ServerPlayer player) {
        var progress = VillagerQuestSavedData.get(level).get(player.getUUID(), site.quest);
        return progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                && site.run != null && site.run.equals(progress.questRunId());
    }

    private static void maintain(ServerLevel level, QuestSiteSavedData data, Site site, QuestDefinition quest) {
        var players = level.getServer().getPlayerList().getPlayers().stream().filter(player -> owns(level, site, player)).toList();
        maintain(level, data, site, quest, players);
    }

    static void maintain(ServerLevel level, QuestSiteSavedData data, Site site, QuestDefinition quest, List<ServerPlayer> players) {
        if (players.isEmpty()) {
            if (VillagerQuestSavedData.get(level).hasActiveRun(site.quest, site.run)) return;
            var captive = level.getEntity(site.captive == null ? new UUID(0, 0) : site.captive);
            if (captive instanceof Mob mob) mob.setNoAi(false);
            data.retire(site);
            return;
        }
        if (site.dead) {
            for (var player : players) QuestRuntimes.forQuest(site.quest).fail(player, site.quest, site.run);
            return;
        }
        var destination = quest.target().site().escortDestination() == null ? site.home : quest.target().site().escortDestination();
        var captive = site.captive == null ? null : actor(level.getServer(), site.captive);
        if (captive != null && (!captive.blockPosition().equals(site.actorPosition)
                || !captive.level().dimension().location().equals(site.actorDimension))) {
            site.actorPosition = captive.blockPosition().immutable();
            site.actorDimension = captive.level().dimension().location();
            data.setDirty();
        }
        var lastLevel = site.actorDimension == null ? level : level.getServer().getLevel(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, site.actorDimension));
        var lastPosition = site.actorPosition == null ? site.position : site.actorPosition;
        if (!site.rescued && site.captive != null && captive == null && lastLevel != null
                && lastLevel.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(lastPosition).toLong())) {
            if (site.activatedAt == 0) site.activatedAt = level.getGameTime();
            if (level.getGameTime() - site.activatedAt > 200) {
                site.diagnostic = "The captive is missing. Return to the quest giver to report the lost lead.";
                for (var player : players) QuestRuntimes.forQuest(site.quest).fail(player, site.quest, site.run);
            }
            return;
        }
        site.activatedAt = 0;
        if (!site.discovered && players.stream().anyMatch(player -> player.level() == level
                && player.distanceToSqr(site.position.getCenter()) <= 24 * 24)) {
            if (captive != null && !site.released && (!level.noCollision(captive)
                    || !level.getBlockState(captive.blockPosition().below()).isFaceSturdy(level,
                            captive.blockPosition().below(), net.minecraft.core.Direction.UP)
                    || !QuestSitePlanner.usable(level, site, quest.target().site()))) {
                site.diagnostic = "The holding area is no longer usable. Return to the quest giver.";
                for (var player : players) QuestRuntimes.forQuest(site.quest).fail(player, site.quest, site.run);
                return;
            }
            site.discovered = true;
            data.setDirty();
            for (var player : players) VillagerQuestService.refreshTracker(player);
        }
        if (site.released && !site.rescued && (quest.target().site().rescue() == QuestSiteDefinition.Rescue.FREE_AT_SITE
                || captive != null && captive.level() == level && captive.blockPosition().distSqr(destination) <= 12 * 12)) {
            site.rescued = true;
            if (site.captive != null) QuestFollowerService.release(level, site.captive);
            data.setDirty();
        }
        for (var player : players) {
            if (site.released && quest.target().site().rescue() == QuestSiteDefinition.Rescue.ESCORT_HOME) {
                var progress = VillagerQuestSavedData.get(level).get(player.getUUID(), site.quest);
                if (progress != null && !destination.equals(progress.targetPos())) {
                    progress.setTarget(progress.startedVillagerId(), level.dimension(), destination, "");
                    VillagerQuestSavedData.get(level).setDirty();
                    VillagerQuestService.refreshTracker(player);
                }
            }
            if (site.discovered) criterion(player, site, "site_discovered", captive);
            if (site.released) criterion(player, site, "captive_freed", captive);
            if (site.rescued) {
                criterion(player, site, "captive_rescued", captive);
                if (captive instanceof Mob mob) mob.setNoAi(false);
            }
            QuestSiteMaps.give(player, quest, site, false);
        }
    }

    private static void criterion(ServerPlayer player, Site site, String event, Entity actor) {
        com.jvn.villagerretaliation.api.QuestCriterionApi.trigger(player, ResourceLocation.fromNamespaceAndPath("villagerretaliation", event),
                Map.of("quest", site.quest.toString()), ItemStack.EMPTY, actor instanceof LivingEntity living ? living : null);
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        var data = QuestSiteSavedData.get(level);
        var site = data.sites.values().stream().filter(value -> value.phase == Phase.ACTIVE
                && !value.rescued && event.getTarget().getUUID().equals(value.captive)).findFirst().orElse(null);
        if (site == null) return;
        if (site.released && owns(level, site, player)) {
            var quest = VillagerQuestResources.quest(player.server, site.quest).orElse(null);
            if (quest != null && quest.target().site() != null
                    && quest.target().site().rescue() == QuestSiteDefinition.Rescue.ESCORT_HOME
                    && event.getTarget() instanceof Mob mob && !QuestFollowerService.retained(mob)) startEscort(player, site);
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!owns(level, site, player)) {
            player.displayClientMessage(Component.translatable("quest.villagerretaliation.rescue.reserved"), true);
            return;
        }
        if (site.released) return;
        if (!site.defeated.containsAll(site.guards)) {
            player.displayClientMessage(Component.translatable("quest.villagerretaliation.rescue.guards"), true);
            return;
        }
        var quest = VillagerQuestResources.quest(player.server, site.quest).orElse(null);
        if (quest == null || quest.target().site() == null) return;
        if (quest.target().site().rescue() == QuestSiteDefinition.Rescue.ESCORT_HOME) {
            if (!startEscort(player, site)) return;
        }
        site.released = true;
        if (event.getTarget() instanceof Mob mob) mob.setNoAi(false);
        data.setDirty();
        player.displayClientMessage(Component.translatable("quest.villagerretaliation.rescue.freed"), true);
    }

    private static boolean startEscort(ServerPlayer player, Site site) {
        var actor = new JsonObject();
        actor.addProperty("alias", "captive");
        actor.addProperty("type", "villagerretaliation:villager");
        actor.addProperty("binding_source", "uuid");
        actor.addProperty("binding", site.captive.toString());
        actor.addProperty("death_policy", "fail");
        actor.addProperty("missing_actor_policy", "block");
        var action = new JsonObject();
        action.addProperty("type", "follower");
        action.addProperty("quest", site.quest.toString());
        action.addProperty("command", "follow");
        action.add("actor", actor);
        var parsed = VillagerActionDefinition.readObjects(site.quest, "site rescue", List.of(action), site.quest);
        return parsed.size() == 1 && QuestFollowerService.execute(DialogueContext.forPlayer(player), parsed.getFirst()).legacyResult().ran();
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void interactSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        var interaction = new PlayerInteractEvent.EntityInteract(event.getEntity(), event.getHand(), event.getTarget());
        interact(interaction);
        if (interaction.isCanceled()) {
            event.setCanceled(true);
            event.setCancellationResult(interaction.getCancellationResult());
        }
    }

    public static boolean reserved(ServerLevel level, UUID actor) {
        return QuestSiteSavedData.get(level).sites.values().stream().anyMatch(site -> actor.equals(site.captive)
                && !site.released && (site.phase == Phase.ACTIVE || site.phase == Phase.READY));
    }

    private static Entity actor(MinecraftServer server, UUID id) {
        for (var level : server.getAllLevels()) {
            var actor = level.getEntity(id);
            if (actor != null) return actor;
        }
        return null;
    }

    @SubscribeEvent
    public static void joined(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Mob mob)
                || level.getServer().overworld() == null) return;
        for (var site : QuestSiteSavedData.get(level).sites.values()) {
            if (mob.getUUID().equals(site.captive) && (site.released || site.phase == Phase.RETIRED || site.phase == Phase.UNAVAILABLE))
                mob.setNoAi(false);
        }
    }

    @SubscribeEvent
    public static void left(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof net.minecraft.world.entity.monster.Pillager guard)
                || guard.getRemovalReason() != Entity.RemovalReason.DISCARDED
                || level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL) return;
        var data = QuestSiteSavedData.get(level);
        for (var site : data.sites.values()) {
            if (site.phase == Phase.RETIRED || site.phase == Phase.UNAVAILABLE
                    || !site.guards.contains(guard.getUUID()) || site.defeated.contains(guard.getUUID())) continue;
            site.defeated.add(guard.getUUID());
            data.setDirty();
        }
    }

    public static void died(LivingEntity actor) {
        if (!(actor.level() instanceof ServerLevel level)) return;
        var data = QuestSiteSavedData.get(level);
        for (var site : data.sites.values()) {
            if (site.phase == Phase.RETIRED || site.phase == Phase.UNAVAILABLE) continue;
            if (actor.getUUID().equals(site.captive) && !site.rescued) site.dead = true;
            else if (site.guards.contains(actor.getUUID()) && !site.defeated.contains(actor.getUUID())) site.defeated.add(actor.getUUID());
            else continue;
            data.setDirty();
        }
    }

    private static void unavailable(QuestSiteSavedData data, Site site, String diagnostic) {
        site.phase = Phase.UNAVAILABLE;
        site.diagnostic = diagnostic;
        data.setDirty();
    }

    public static com.jvn.villagerretaliation.network.QuestTrackerSyncPayload.Entry present(ServerPlayer player,
            QuestDefinition quest, com.jvn.villagerretaliation.network.QuestTrackerSyncPayload.Entry entry) {
        if (quest.target().site() == null || !entry.journal().waypoint().present()) return entry;
        var old = entry.journal().waypoint();
        var position = displayPosition(player.serverLevel(), player.getUUID(), quest, new BlockPos(old.x(), old.y(), old.z()));
        var waypoint = new com.jvn.villagerretaliation.network.QuestTrackerSyncPayload.Waypoint(old.dimension(),
                position.getX(), position.getY(), position.getZ(), displayRadius(player.serverLevel(), player.getUUID(), quest));
        return entry.withJournal(entry.journal().withRuntime(entry.journal().expiresAtGameTime(),
                entry.journal().completedGameTime(), waypoint));
    }

    public static void sanitize(Map<String, String> values, ServerPlayer player, QuestDefinition quest) {
        if (displayRadius(player.serverLevel(), player.getUUID(), quest) == 0) return;
        var site = find(player.serverLevel(), player.getUUID(), quest.id());
        var position = displayPosition(player.serverLevel(), player.getUUID(), quest, site.position);
        for (String prefix : List.of("target_", "objective_target_")) {
            values.put(prefix + "x", Integer.toString(position.getX()));
            values.put(prefix + "y", "unknown");
            values.put(prefix + "z", Integer.toString(position.getZ()));
        }
        values.put("distance", "unknown");
        values.put("direction", "within the marked search area");
    }

    public static void giveMap(DialogueContext context, QuestDefinition quest, boolean replacement) {
        var site = find(context.level(), context.player().getUUID(), quest.id());
        if (site != null && site.phase == Phase.ACTIVE) QuestSiteMaps.give(context.player(), quest, site, replacement);
    }
}

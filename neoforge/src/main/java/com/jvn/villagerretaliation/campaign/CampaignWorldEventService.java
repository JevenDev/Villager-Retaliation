package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

public final class CampaignWorldEventService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation DRAGON_DEFEATED = ResourceLocation.parse("minecraft:ender_dragon_defeated");
    private static final Map<ResourceLocation, String> REPORTED = new HashMap<>();

    private CampaignWorldEventService() {
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % 100L == 0L) reconcile(server);
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        REPORTED.clear();
    }

    public static void reconcile(MinecraftServer server) {
        if (CampaignWorldModeSavedData.get(server.overworld()).mode() != CampaignWorldMode.CAMPAIGN) return;
        var catalog = CampaignResources.current(server);
        var data = CampaignSavedData.get(server.overworld());
        var end = server.getLevel(Level.END);
        for (var id : data.instances().keySet()) {
            if (!catalog.campaigns().containsKey(id)) report(id, "Campaign content is missing. Restore its datapack to resume the saved campaign.");
        }
        for (var content : catalog.campaigns().values()) {
            ResourceLocation id = content.definition().id();
            String issue = data.compatibility(content.definition());
            if (!issue.isBlank()) {
                report(id, issue);
                continue;
            }
            try {
                reconcile(data, content, end, server.overworld().getGameTime());
                String characterIssue = reconcileCharacterLosses(server, data, content, server.overworld().getGameTime());
                if (characterIssue.isBlank()) REPORTED.remove(id);
                else report(id, characterIssue);
            } catch (IOException exception) {
                report(id, "Could not save campaign state: " + exception.getMessage() + ". The failed change remains unapplied. Check world storage before retrying.");
            }
        }
    }

    static void reconcile(CampaignSavedData data, CampaignContentCatalog.Entry content, ServerLevel end, long gameTime) throws IOException {
        var instance = data.open(content, gameTime);
        if (end == null || end.getDragonFight() == null || !end.getDragonFight().hasPreviouslyKilledDragon()) return;
        for (var milestone : content.definition().milestones().values()) {
            var observation = milestone.observation();
            if (observation == null || !observation.event().equals(DRAGON_DEFEATED) || instance.outcomes().containsKey(milestone.id())) continue;
            UUID operation = UUID.nameUUIDFromBytes((instance.instanceId() + "/" + milestone.id() + "/" + DRAGON_DEFEATED)
                    .getBytes(StandardCharsets.UTF_8));
            data.commitWorldOutcome(content, milestone.id(), observation.outcome(), operation, instance.worldRevision(),
                    CampaignInstance.Origin.WORLD_OBSERVATION, Set.of(), gameTime);
            instance = data.instances().get(content.definition().id());
        }
    }

    private static String reconcileCharacterLosses(MinecraftServer server, CampaignSavedData data, CampaignContentCatalog.Entry content, long gameTime) throws IOException {
        String issue = "";
        for (var milestone : content.definition().milestones().values()) {
            var observation = milestone.observation();
            var instance = data.instances().get(content.definition().id());
            if (observation == null || !observation.characterLost() || instance == null || instance.outcomes().containsKey(milestone.id())) continue;
            var character = CampaignBindingQueries.originalCharacter(server, content.definition().id(), observation.character());
            if (!character.diagnostic().isBlank()) {
                if (issue.isBlank()) issue = character.diagnostic();
                continue;
            }
            if (character.binding() == null || !character.binding().lost()) continue;
            UUID operation = UUID.nameUUIDFromBytes((instance.instanceId() + "/" + milestone.id() + "/" + observation.event()
                    + "/" + character.binding().entityId()).getBytes(StandardCharsets.UTF_8));
            var result = data.commitWorldOutcome(content, milestone.id(), observation.outcome(), operation, instance.worldRevision(),
                    CampaignInstance.Origin.WORLD_OBSERVATION, Set.of(), gameTime);
            if (result.status() != CampaignSavedData.Status.APPLIED && result.status() != CampaignSavedData.Status.ALREADY_COMMITTED
                    && issue.isBlank()) issue = result.diagnostic();
        }
        return issue;
    }

    private static void report(ResourceLocation campaign, String message) {
        if (!message.equals(REPORTED.put(campaign, message))) LOGGER.error("Campaign {}: {}", campaign, message);
    }
}

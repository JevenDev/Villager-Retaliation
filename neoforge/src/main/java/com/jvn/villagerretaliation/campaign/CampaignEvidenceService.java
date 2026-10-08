package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveQuery;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public final class CampaignEvidenceService {
    private CampaignEvidenceService() { }

    public static Resolution resolve(MinecraftServer server, ResourceLocation campaign, String evidenceId) {
        if (server == null || !server.isSameThread()) return Resolution.unavailable("Campaign evidence requires the server thread.");
        if (CampaignWorldModeSavedData.get(server.overworld()).mode() != CampaignWorldMode.CAMPAIGN)
            return Resolution.unavailable("This evidence requires a Campaign Mode world.");
        var content = CampaignResources.current(server).campaigns().get(campaign);
        if (content == null) return Resolution.unavailable("Campaign evidence content is missing. Restore the campaign datapack.");
        var definition = content.definition();
        var evidence = definition.evidence().get(evidenceId);
        if (evidence == null) return Resolution.unavailable("Unknown campaign evidence " + evidenceId + ". Restore its authored definition.");
        var campaigns = CampaignSavedData.get(server.overworld());
        String issue = campaigns.compatibility(definition);
        if (!issue.isBlank()) return Resolution.unavailable(issue);
        var data = CampaignEvidenceSavedData.get(server.overworld());
        if (!data.diagnostic().isBlank()) return Resolution.unavailable(data.diagnostic());
        var binding = data.bindings().get(new CampaignEvidenceBinding.Key(campaign, evidenceId));
        var instance = campaigns.instances().get(campaign);
        if (binding != null && (instance == null || !binding.instance().equals(instance.instanceId())
                || binding.definitionRevision() != definition.revision() || !binding.fingerprint().equals(definition.fingerprint())))
            return Resolution.unavailable("Saved evidence ownership is incompatible. Restore the matching campaign and evidence saves.");
        var location = CampaignLocationService.resolve(server, campaign, evidence.location());
        if (!location.available()) return Resolution.unavailable(location.diagnostic());
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, location.binding().dimension()));
        if (level == null) return Resolution.unavailable("The evidence dimension is unavailable.");
        if (binding != null) {
            if (!binding.dimension().equals(location.binding().dimension()))
                return Resolution.unavailable("The saved evidence dimension differs from its campaign location.");
            var entity = level.getEntity(binding.entity());
            if (!(entity instanceof Interaction interaction) || entity.isRemoved())
                return Resolution.unavailable("The original evidence source is unavailable or unloaded. Return to its saved location and retry.");
            if (!entity.getTags().contains(evidence.entityTag().toString()) || !entity.blockPosition().equals(binding.position()))
                return Resolution.unavailable("The original evidence source was changed or moved. Restore its authored identity and position.");
            return new Resolution(binding, interaction, "");
        }
        var origin = location.binding().position();
        double radius = evidence.searchRadius();
        var candidates = level.getEntitiesOfClass(Interaction.class, new AABB(origin).inflate(radius),
                entity -> !entity.isRemoved() && entity.getTags().contains(evidence.entityTag().toString()) && data.owner(entity.getUUID()) == null
                        && entity.blockPosition().distSqr(origin) <= radius * radius);
        var selected = candidates.stream().min(Comparator.<Interaction>comparingDouble(entity -> entity.blockPosition().distSqr(origin))
                .thenComparing(Entity::getUUID)).orElse(null);
        if (selected == null) return Resolution.unavailable("No loaded evidence source tagged " + evidence.entityTag()
                + " is available at campaign location " + evidence.location() + ". Visit its authored location and retry.");
        try {
            long time = level.getGameTime();
            instance = campaigns.open(content, time);
            binding = data.bind(new CampaignEvidenceBinding(campaign, instance.instanceId(), definition.revision(), definition.fingerprint(),
                    evidenceId, selected.getUUID(), level.dimension().location(), selected.blockPosition(), time));
            return new Resolution(binding, selected, "");
        } catch (IOException | IllegalArgumentException exception) {
            return Resolution.unavailable("Campaign evidence could not be bound: " + exception.getMessage());
        }
    }

    public static void onInteract(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (!event.isCanceled() && handle(event.getEntity(), event.getTarget())) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);
        }
    }

    public static boolean handle(Player player, Entity target) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(target instanceof Interaction)) return false;
        var result = investigate(serverPlayer, target);
        if (!result.matched()) return false;
        if (!result.diagnostic().isBlank()) player.displayClientMessage(Component.literal(result.diagnostic()), true);
        else player.sendSystemMessage(Component.literal(result.title() + "\n" + result.text()));
        return true;
    }

    public static Result investigate(ServerPlayer player, Entity target) {
        if (player == null || !(target instanceof Interaction) || !player.server.isSameThread() || target.level() != player.serverLevel()
                || target.isRemoved() || !player.isAlive() || player.isSpectator() || !player.canInteractWithEntity(target, 0.0)
                || !player.hasLineOfSight(target)) return Result.unmatched();
        if (CampaignWorldModeSavedData.get(player.serverLevel()).mode() != CampaignWorldMode.CAMPAIGN) return Result.unmatched();
        var quests = VillagerQuestSavedData.get(player.serverLevel());
        var catalog = QuestContentCatalogs.current(player.server);
        var campaigns = CampaignResources.current(player.server);
        var data = CampaignEvidenceSavedData.get(player.serverLevel());
        var observed = new ArrayList<CampaignInvestigationReceipt>();
        for (var entry : quests.activeProgress(player.getUUID())) {
            var quest = catalog.quest(entry.getKey()).orElse(null);
            var progress = entry.getValue();
            if (quest == null || quest.campaign() == null || progress.questRunId() == null) continue;
            var content = campaigns.campaigns().get(quest.campaign());
            if (content == null) continue;
            for (var objective : QuestObjectiveQuery.activeObjectives(quest, progress)) {
                if (!(objective.story() instanceof CampaignStoryObjective.Investigate investigation)) continue;
                var evidence = content.definition().evidence().get(investigation.evidence());
                if (evidence == null || !target.getTags().contains(evidence.entityTag().toString())) continue;
                String issue = CampaignQuestAccess.executionIssue(player.serverLevel(), player.getUUID(), quest, progress);
                if (!issue.isBlank()) return Result.unavailable(issue);
                var resolved = resolve(player.server, quest.campaign(), evidence.id());
                if (!resolved.available()) return Result.unavailable(resolved.diagnostic());
                if (!resolved.entity().getUUID().equals(target.getUUID())) continue;
                var binding = resolved.binding();
                var key = new CampaignInvestigationReceipt.Key(quest.campaign(), binding.instance(), binding.fingerprint(), player.getUUID(),
                        quest.id(), progress.questRunId(), objective.id(), evidence.id());
                String title = content.localization().plainText(player.clientInformation().language(), evidence.titleKey()).orElse(evidence.titleKey());
                String text = content.localization().plainText(player.clientInformation().language(), evidence.textKey()).orElse(evidence.textKey());
                observed.add(CampaignInvestigationReceipt.create(key, target.getUUID(), title, text, player.serverLevel().getGameTime()));
            }
        }
        if (observed.isEmpty()) return Result.unmatched();
        try {
            boolean applied = data.record(observed);
            var first = observed.getFirst();
            return new Result(true, applied, first.title(), first.text(), "");
        } catch (IOException | IllegalArgumentException exception) {
            return Result.unavailable("Investigation could not be saved. Restore writable campaign storage and interact again: " + exception.getMessage());
        }
    }

    public record Resolution(CampaignEvidenceBinding binding, Interaction entity, String diagnostic) {
        public boolean available() { return binding != null && entity != null; }
        static Resolution unavailable(String diagnostic) { return new Resolution(null, null, diagnostic); }
    }

    public record Result(boolean matched, boolean applied, String title, String text, String diagnostic) {
        static Result unmatched() { return new Result(false, false, "", "", ""); }
        static Result unavailable(String diagnostic) { return new Result(true, false, "", "", diagnostic); }
    }
}

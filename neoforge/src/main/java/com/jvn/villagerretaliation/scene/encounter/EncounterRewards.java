package com.jvn.villagerretaliation.scene.encounter;

import com.jvn.villagerretaliation.campaign.CampaignPlayerRewards;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerProfileSavedData;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import com.jvn.villagerretaliation.scene.runtime.SceneOperationReceipt;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

final class EncounterRewards {
    private EncounterRewards() { }

    static String grant(MinecraftServer server, SceneSavedData data, EncounterInstance encounter,
            EncounterTemplate template, List<EncounterService.RewardGrant> eligible) {
        var ledger = encounter.rewardLedger();
        if (!ledger.diagnostic().isBlank()) return ledger.diagnostic();
        String pending = EncounterPersistence.prepare(server, data, encounter);
        if (!pending.isBlank()) return pending;
        var scene = data.get(encounter.sceneId()).orElseThrow();
        String contentIssue = com.jvn.villagerretaliation.campaign.CampaignQuestAccess.contentIssue(server, scene.owningQuestId(), scene.owningCampaignId());
        if (!contentIssue.isBlank()) return contentIssue;
        var definition = com.jvn.villagerretaliation.scene.SceneResources.scene(server, scene.sceneId()).orElse(null);
        if (definition == null) return "The encounter's owning scene is unavailable. Restore its original content.";
        var reconciliation = com.jvn.villagerretaliation.scene.runtime.SceneDefinitionReconciler.reconcile(scene, definition);
        if (!reconciliation.safe()) return reconciliation.diagnostic();
        try {
            for (var batch : ledger.batches().values()) validate(template, batch);
            boolean changed = ledger.bind(template);
            for (var earned : eligible) {
                var batch = new EncounterRewardLedger.Batch(earned.trigger(), earned.reward(), 0, java.util.Map.of());
                if (ledger.batches().containsKey(batch.key())) continue;
                var grants = new LinkedHashMap<String, EncounterRewardLedger.Grant>();
                for (UUID participant : encounter.participants()) {
                    String key = "player/" + participant;
                    var grant = capture(scene, encounter, batch, key, participant);
                    if (grant != null) grants.put(key, grant);
                }
                ledger.record(new EncounterRewardLedger.Batch(batch.trigger(), batch.reward(), 0, grants));
                changed = true;
            }
            int guts = EncounterService.completionGutsReward(template);
            if (guts != 0 && encounter.completionRewardEligible() && !ledger.batches().containsKey("profile/guts")) {
                var batch = new EncounterRewardLedger.Batch("profile/guts", null, guts, java.util.Map.of());
                var grants = new LinkedHashMap<String, EncounterRewardLedger.Grant>();
                for (var ally : encounter.allies().values()) {
                    if (ally.state() == EncounterInstance.AllyState.DEAD || ally.state() == EncounterInstance.AllyState.REMOVED) continue;
                    var entity = find(server, ally.entityId());
                    if (entity != null && !(entity instanceof Villager) || entity == null
                            && !ally.entityType().equals(net.minecraft.resources.ResourceLocation.withDefaultNamespace("villager"))) continue;
                    if (entity instanceof Villager villager) VillagerProfileManager.getOrCreateProfile((net.minecraft.server.level.ServerLevel) villager.level(), villager);
                    var grant = capture(scene, encounter, batch, ally.key(), ally.entityId());
                    if (grant != null) grants.put(ally.key(), grant.ready(List.of()));
                }
                ledger.record(new EncounterRewardLedger.Batch(batch.trigger(), null, guts, grants));
                changed = true;
            }
            if (changed) {
                data.changed();
                pending = EncounterPersistence.checkpoint(server, data, encounter);
                if (!pending.isBlank()) return pending;
            }
            for (var batch : List.copyOf(ledger.batches().values())) {
                for (var original : batch.grants().values()) {
                    var grant = original;
                    if (batch.reward() == null) {
                        VillagerProfileSavedData.get(server.overworld()).deliverCampaignReward(server.overworld(), grant.operationId(),
                                grant.token(), grant.recipient(), batch.guts(), grant.state() == EncounterRewardLedger.State.DELIVERED);
                    } else {
                        var player = server.getPlayerList().getPlayer(grant.recipient());
                        if (player == null) continue;
                        if (grant.state() == EncounterRewardLedger.State.EARNED) {
                            if (batch.reward().lootTable() != null) {
                                var resolution = com.jvn.villagerretaliation.quest.content.reward.QuestRewardResolver.resolve(server, batch.reward().lootTable());
                                EncounterRewardLedger.require(resolution.resolved(), resolution.diagnostic());
                            } else EncounterRewardLedger.require(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(batch.reward().item()),
                                    "The earned encounter reward item is unavailable.");
                            var items = EncounterService.rewardItems(player, encounter, batch.reward(), batch.trigger()).stream()
                                    .filter(item -> !item.isEmpty()).map(item -> (net.minecraft.nbt.CompoundTag) item.save(player.registryAccess())).toList();
                            grant = grant.ready(items);
                            ledger.record(ledger.batches().get(batch.key()).replace(grant));
                            data.changed();
                            pending = EncounterPersistence.checkpoint(server, data, encounter);
                            if (!pending.isBlank()) return pending;
                        }
                        CampaignPlayerRewards.deliver(player, new CampaignPlayerRewards.Delivery(grant.operationId(), grant.token(),
                                grant.recipient(), 0, grant.items(), grant.state() == EncounterRewardLedger.State.DELIVERED));
                    }
                    if (grant.state() == EncounterRewardLedger.State.DELIVERED) continue;
                    ledger.record(ledger.batches().get(batch.key()).replace(grant.finish()));
                    var receipt = scene.prepareReceipt(grant.operation(), kind(batch), server.overworld().getGameTime());
                    receipt.applied(server.overworld().getGameTime(), "recipient=" + grant.recipient());
                    receipt.completed(server.overworld().getGameTime(), "reward saved with its recipient");
                    data.changed();
                    pending = EncounterPersistence.checkpoint(server, data, encounter);
                    if (!pending.isBlank()) return pending;
                }
            }
            return "";
        } catch (IOException | IllegalArgumentException exception) {
            return "Encounter reward delivery is waiting: " + exception.getMessage();
        }
    }

    private static EncounterRewardLedger.Grant capture(SceneInstance scene, EncounterInstance encounter,
            EncounterRewardLedger.Batch batch, String key, UUID recipient) {
        String operation = batch.operation(scene.id(), encounter.id(), key);
        var existing = scene.receipts().get(operation);
        if (existing != null) {
            EncounterRewardLedger.require(existing.state() == SceneOperationReceipt.ReceiptState.COMPLETED,
                    "A legacy encounter reward has no delivery proof. Restore or repair its original receipt before continuing.");
            return null;
        }
        return new EncounterRewardLedger.Grant(key, operation, recipient, UUID.randomUUID(), EncounterRewardLedger.State.EARNED, List.of());
    }

    private static void validate(EncounterTemplate template, EncounterRewardLedger.Batch batch) {
        if (batch.reward() == null) {
            EncounterRewardLedger.require(batch.guts() == EncounterService.completionGutsReward(template), "The encounter's villager reward has changed. Restore its original content.");
            return;
        }
        var policy = template.rewards();
        List<EncounterTemplate.Reward> candidates = policy == null ? List.of()
                : batch.trigger().equals("completion") ? policy.completion()
                : batch.trigger().startsWith("wave/") ? policy.waves() : policy.phases();
        EncounterRewardLedger.require(candidates.contains(batch.reward()), "Saved encounter reward " + batch.reward().id() + " no longer matches its authored content.");
        boolean trigger = batch.trigger().equals("completion") || batch.trigger().equals("wave/" + batch.reward().target())
                || batch.trigger().matches("phase/" + java.util.regex.Pattern.quote(batch.reward().target()) + "/[1-9][0-9]*");
        EncounterRewardLedger.require(trigger, "Saved encounter reward trigger is invalid.");
    }

    private static SceneOperationReceipt.Kind kind(EncounterRewardLedger.Batch batch) {
        return batch.reward() == null ? SceneOperationReceipt.Kind.PROFILE_ATTRIBUTE_CHANGE
                : batch.reward().item() == null ? SceneOperationReceipt.Kind.LOOT_GRANT : SceneOperationReceipt.Kind.ITEM_GRANT;
    }

    private static Entity find(MinecraftServer server, UUID id) {
        for (var level : server.getAllLevels()) {
            var entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }
}

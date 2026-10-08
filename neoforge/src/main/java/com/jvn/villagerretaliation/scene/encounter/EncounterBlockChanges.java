package com.jvn.villagerretaliation.scene.encounter;

import com.jvn.villagerretaliation.campaign.CampaignBlockOwnership;
import com.jvn.villagerretaliation.campaign.CampaignBlockOwnershipLedger;
import com.jvn.villagerretaliation.campaign.CampaignBlockSnapshot;
import com.jvn.villagerretaliation.scene.encounter.EncounterInstance.EnvironmentBlock;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

final class EncounterBlockChanges {
    private EncounterBlockChanges() { }

    static EncounterService.Result apply(MinecraftServer server, SceneSavedData data, EncounterInstance encounter, ServerLevel level, EnvironmentBlock owned) {
        if (!owned.ownership().issue().isBlank()) return EncounterService.Result.waiting(owned.ownership().issue());
        var request = request(encounter, level, owned);
        if (owned.ownership().state() == EncounterBlockOwnership.State.PENDING) {
            var reservation = CampaignBlockOwnership.reserve(level, request);
            if (reservation.status() != CampaignBlockOwnership.Status.READY)
                return waiting(server, data, encounter, owned, reservation.diagnostic());
            owned = owned.withOwnership(owned.ownership().ready());
            encounter.replaceEnvironmentBlock(owned);
            data.changed();
            String storageIssue = EncounterPersistence.checkpoint(server, data, encounter);
            if (!storageIssue.isBlank()) return EncounterService.Result.waiting(storageIssue);
        }
        var result = CampaignBlockOwnership.apply(level, request);
        if (result.status() != CampaignBlockOwnership.Status.APPLIED)
            return waiting(server, data, encounter, owned, result.diagnostic());
        return finish(server, data, encounter, owned.withOwnership(owned.ownership().ready()).applied());
    }

    static EncounterService.Result restore(MinecraftServer server, SceneSavedData data, EncounterInstance encounter, ServerLevel level, EnvironmentBlock owned) {
        var proof = owned.ownership();
        if (proof.state() == EncounterBlockOwnership.State.UNREADABLE) return EncounterService.Result.waiting(proof.issue());
        if (proof.state() == EncounterBlockOwnership.State.LEGACY)
            return preserve(server, data, encounter, level, owned, proof.issue());
        var chunk = level.getChunkSource().getChunkNow(owned.position().getX() >> 4, owned.position().getZ() >> 4);
        if (chunk == null) return EncounterService.Result.waiting("The owned encounter block chunk is unloaded.");
        String issue = CampaignBlockOwnership.diagnostic(chunk);
        if (!issue.isBlank()) return EncounterService.Result.waiting(issue);
        if (proof.state() == EncounterBlockOwnership.State.PENDING && CampaignBlockOwnership.receipt(chunk, owned.position()) == null)
            return preserve(server, data, encounter, level, owned, "Block ownership was never acknowledged. Existing terrain was preserved.");
        var result = CampaignBlockOwnership.restore(level, request(encounter, level, owned));
        return switch (result.status()) {
            case RESTORED -> finish(server, data, encounter, owned.withOwnership(proof.ready()).restored());
            case CONFLICT -> preserve(server, data, encounter, level, owned, result.diagnostic());
            default -> waiting(server, data, encounter, owned, result.diagnostic());
        };
    }

    private static CampaignBlockOwnershipLedger.Receipt request(EncounterInstance encounter, ServerLevel level, EnvironmentBlock owned) {
        return new CampaignBlockOwnershipLedger.Receipt(owned.position(), encounter.id(), owned.ownership().operation(),
                CampaignBlockSnapshot.defaultState(level, owned.position(), owned.original()),
                CampaignBlockSnapshot.defaultState(level, owned.position(), owned.placed()), CampaignBlockOwnershipLedger.State.RESERVED, "");
    }

    private static EncounterService.Result preserve(MinecraftServer server, SceneSavedData data, EncounterInstance encounter,
            ServerLevel level, EnvironmentBlock owned, String diagnostic) {
        String issue = EncounterPersistence.checkpointBlock(server, data, encounter, level, owned.position());
        if (!issue.isBlank()) return EncounterService.Result.waiting(issue);
        return finish(server, data, encounter, owned.withOwnership(owned.ownership().conflict(diagnostic)).preserved());
    }

    private static EncounterService.Result waiting(MinecraftServer server, SceneSavedData data, EncounterInstance encounter, EnvironmentBlock owned, String diagnostic) {
        encounter.replaceEnvironmentBlock(owned.withOwnership(owned.ownership().conflict(diagnostic)));
        data.changed();
        String issue = EncounterPersistence.checkpoint(server, data, encounter);
        return EncounterService.Result.waiting(issue.isBlank() ? diagnostic : issue);
    }

    private static EncounterService.Result finish(MinecraftServer server, SceneSavedData data, EncounterInstance encounter, EnvironmentBlock owned) {
        encounter.replaceEnvironmentBlock(owned);
        data.changed();
        String issue = EncounterPersistence.checkpoint(server, data, encounter);
        return issue.isBlank() ? EncounterService.Result.completed() : EncounterService.Result.waiting(issue);
    }
}

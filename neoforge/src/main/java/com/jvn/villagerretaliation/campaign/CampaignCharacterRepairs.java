package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignCharacterDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

public final class CampaignCharacterRepairs {
    private CampaignCharacterRepairs() { }

    public static Result repair(MinecraftServer server, Request request) {
        if (server == null || !server.isSameThread() || request == null) return Result.failed("Character repairs require a complete request on the server thread.");
        if (CampaignWorldModeSavedData.get(server.overworld()).mode() != CampaignWorldMode.CAMPAIGN)
            return Result.failed("Character repairs require Campaign Mode.");
        var content = CampaignResources.current(server).campaigns().get(request.campaign());
        if (content == null) return Result.failed("Restore the campaign's valid datapack before repairing its characters.");
        var definition = content.definition();
        var declared = definition.characters().get(request.role());
        if (declared == null) return Result.failed("Choose a declared campaign character role.");
        var campaigns = CampaignSavedData.get(server.overworld());
        String issue = campaigns.compatibility(definition);
        if (!issue.isBlank()) return Result.failed(issue);
        var data = CampaignCharacterSavedData.get(server.overworld());
        if (!data.diagnostic().isBlank()) return Result.failed(data.diagnostic());
        var key = new CampaignCharacterBinding.Key(request.campaign(), request.role());
        var current = data.bindings().get(key);
        var owner = campaigns.instances().get(request.campaign());
        if (current != null && (owner == null || !current.instance().equals(owner.instanceId())
                || current.definitionRevision() != definition.revision() || !current.fingerprint().equals(definition.fingerprint())
                || current.source() != declared.source() || current.lossPolicy() != declared.lossPolicy()))
            return Result.failed("Saved character ownership is incompatible. Restore matching campaign and character saves.");
        var receipt = data.repairs().get(request.operation());
        if (receipt != null) {
            boolean matches = receipt.after().key().equals(key) && receipt.after().entityId().equals(request.target())
                    && receipt.expected().equals(request.expected()) && receipt.operator().equals(request.operator()) && receipt.reason().equals(request.reason());
            return matches ? new Result(current, receipt, "") : Result.failed("Character repair operation already belongs to another request.");
        }
        if (!data.token(key).equals(request.expected())) return Result.failed("Character binding changed. Its current token is " + data.token(key) + ".");
        var successor = data.replacements().get(key);
        if (successor != null) return Result.failed("This role has an authored successor. Inspect and repair role " + successor.replacementRole() + " instead.");
        issue = CampaignCharacterLossService.diagnostic(server, current);
        if (!issue.isBlank()) return Result.failed(issue);
        Entity selected = loaded(server, request.target());
        if (!(selected instanceof Villager target) || !target.isAlive() || target.isRemoved() || target.isBaby() || VillagerDownedService.isDowned(target))
            return Result.failed("Choose a loaded, living adult villager who is not downed.");
        if (data.reserved(target.getUUID()) && data.known(key, target.getUUID()) == null)
            return Result.failed("This villager belongs to another current or historical campaign role.");
        var spawns = CampaignSpawnSavedData.get(server.overworld());
        if (!spawns.diagnostic().isBlank()) return Result.failed(spawns.diagnostic());
        CampaignCharacterBinding candidate;
        long time = server.overworld().getGameTime();
        if (declared.source() == CampaignCharacterDefinition.Source.OWNED_SPAWN) {
            var spawn = spawns.receipts().get(key);
            if (spawn == null || !spawn.committed() || owner == null || !spawn.binding().instance().equals(owner.instanceId())
                    || spawn.binding().definitionRevision() != definition.revision() || !spawn.binding().fingerprint().equals(definition.fingerprint())
                    || spawn.binding().source() != declared.source() || spawn.binding().lossPolicy() != declared.lossPolicy()
                    || !CampaignOwnedSpawns.owns(spawn, target))
                return Result.failed("Campaign-owned roles must recover their original committed spawn identity and ownership receipt.");
            candidate = spawn.binding();
        } else {
            if (spawns.forEntity(target.getUUID()) != null || CampaignOwnedSpawns.hasOwner(target))
                return Result.failed("A dedicated campaign spawn cannot be reassigned as an existing-villager role.");
            boolean same = current != null && current.entityId().equals(target.getUUID());
            if (same) candidate = current.withLoss(false);
            else {
                if (current != null && !current.lost() && loaded(server, current.entityId()) == null)
                    return Result.failed("The original character is unloaded. Load its saved identity before reassigning a living role.");
                if (declared.profession() != null && !declared.profession().equals(VillagerProfessionUtil.id(target.getVillagerData().getProfession())))
                    return Result.failed("The selected villager does not match the role's authored profession.");
                var location = CampaignBindingQueries.location(server, request.campaign(), declared.location());
                if (!location.diagnostic().isBlank()) return Result.failed(location.diagnostic());
                if (location.binding() == null || owner == null) return Result.failed("Bind the role's authored location before selecting its character.");
                if (!target.level().dimension().location().equals(location.binding().dimension())
                        || target.blockPosition().distSqr(location.binding().position()) > (double) declared.searchRadius() * declared.searchRadius())
                    return Result.failed("Choose an eligible villager within the role's authored location and casting radius.");
                candidate = new CampaignCharacterBinding(request.campaign(), owner.instanceId(), definition.revision(), definition.fingerprint(),
                        request.role(), declared.source(), declared.lossPolicy(), target.getUUID(), target.level().dimension().location(), target.blockPosition(), time, false);
            }
        }
        if (candidate.equals(current)) return new Result(current, null, "");
        issue = CampaignCharacterRepairScenes.preflight(server, data, candidate, request.operator());
        if (issue.isBlank()) issue = com.jvn.villagerretaliation.dialogue.conversation.ConversationService.characterRepairIssue(server, candidate);
        if (!issue.isBlank()) return Result.failed(issue);
        try {
            receipt = data.repair(request.operation(), request.expected(), candidate, request.operator(), request.reason(), time);
            return new Result(receipt.after(), receipt, "");
        } catch (IOException | IllegalArgumentException exception) {
            return Result.failed("Character repair could not be saved: " + exception.getMessage());
        }
    }

    private static Entity loaded(MinecraftServer server, UUID entity) {
        for (var level : server.getAllLevels()) {
            var found = level.getEntity(entity);
            if (found != null) return found;
        }
        return null;
    }

    public record Request(ResourceLocation campaign, String role, UUID target, UUID expected, UUID operation, UUID operator, String reason) {
        public Request {
            CampaignStateCodec.require(campaign != null && CampaignFactFields.validId(role) && target != null && expected != null && operation != null
                    && operator != null && reason != null && !reason.isBlank() && reason.length() <= 256,
                    "character repair requires a campaign, role, target identity, expected token, operation, operator, and reason");
        }
    }

    public record Result(CampaignCharacterBinding binding, CampaignCharacterRepair receipt, String diagnostic) {
        public boolean accepted() { return binding != null && diagnostic.isBlank(); }
        private static Result failed(String issue) { return new Result(null, null, issue); }
    }
}

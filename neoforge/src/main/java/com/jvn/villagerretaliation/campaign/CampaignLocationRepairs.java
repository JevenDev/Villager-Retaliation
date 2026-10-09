package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignLocationDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class CampaignLocationRepairs {
    private CampaignLocationRepairs() { }

    public static Result repair(MinecraftServer server, Request request) {
        if (server == null || !server.isSameThread() || request == null) return Result.failed("Location repairs require a complete request on the server thread.");
        if (CampaignWorldModeSavedData.get(server.overworld()).mode() != CampaignWorldMode.CAMPAIGN)
            return Result.failed("Location repairs require Campaign Mode.");
        var content = CampaignResources.current(server).campaigns().get(request.campaign());
        if (content == null) return Result.failed("Restore the campaign's valid datapack before repairing its bindings.");
        var campaigns = CampaignSavedData.get(server.overworld());
        String issue = campaigns.compatibility(content.definition());
        if (!issue.isBlank()) return Result.failed(issue);
        var definition = content.definition();
        var declared = definition.locations().get(request.alias());
        if (declared == null || !declared.dimension().equals(request.dimension()))
            return Result.failed("Choose a declared location in its authored dimension.");
        var data = CampaignLocationSavedData.get(server.overworld());
        if (!data.diagnostic().isBlank()) return Result.failed(data.diagnostic());
        var key = new CampaignLocationBinding.Key(request.campaign(), request.alias());
        var current = data.bindings().get(key);
        var owner = campaigns.instances().get(request.campaign());
        if (current != null && (owner == null || !current.instance().equals(owner.instanceId())
                || current.definitionRevision() != definition.revision() || !current.fingerprint().equals(definition.fingerprint())
                || current.source() != declared.source() || !current.dimension().equals(declared.dimension())))
            return Result.failed("Saved campaign location ownership is incompatible. Restore matching campaign and location saves.");
        var receipt = data.repairs().get(request.operation());
        if (receipt != null) {
            boolean matches = receipt.after().key().equals(key) && receipt.after().dimension().equals(request.dimension())
                    && receipt.after().position().equals(request.position()) && receipt.expected().equals(request.expected())
                    && receipt.operator().equals(request.operator()) && receipt.reason().equals(request.reason());
            return matches ? new Result(current, receipt, "") : Result.failed("Repair operation already belongs to another request.");
        }
        if (!data.token(key).equals(request.expected()))
            return Result.failed("Location binding changed. Its current token is " + data.token(key) + ".");
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, request.dimension()));
        if (level == null || level.isOutsideBuildHeight(request.position()) || !level.getWorldBorder().isWithinBounds(request.position())
                || !level.hasChunkAt(request.position()))
            return Result.failed("Choose a loaded destination inside the authored dimension and world bounds.");
        var selector = new CampaignLocationDefinition(declared.id(), declared.titleKey(), declared.source(), declared.dimension(),
                request.position(), declared.searchRadius(), declared.structure(), declared.structureMarker(), declared.pointer());
        var selected = CampaignLocationService.find(level, selector);
        if (selected == null || !selected.position().equals(request.position()))
            return Result.failed("The destination must match an existing village center, authored structure destination, or explicit marker position.");
        try {
            long time = server.overworld().getGameTime();
            var instance = campaigns.open(content, time);
            var candidate = new CampaignLocationBinding(request.campaign(), instance.instanceId(), definition.revision(), definition.fingerprint(),
                    request.alias(), declared.source(), selected.identity(), request.dimension(), selected.position(), time);
            if (current != null && current.sameOwner(candidate) && current.position().equals(candidate.position())
                    && current.targetIdentity().equals(candidate.targetIdentity())) return new Result(current, null, "");
            receipt = data.repair(request.operation(), request.expected(), candidate, request.operator(), request.reason(), time);
            return new Result(receipt.after(), receipt, "");
        } catch (IOException | IllegalArgumentException exception) {
            return Result.failed("Location repair could not be saved: " + exception.getMessage());
        }
    }

    public record Request(ResourceLocation campaign, String alias, ResourceLocation dimension, BlockPos position,
            UUID expected, UUID operation, UUID operator, String reason) {
        public Request {
            CampaignStateCodec.require(campaign != null && CampaignFactFields.validId(alias) && dimension != null && position != null
                    && expected != null && operation != null && operator != null && reason != null && !reason.isBlank() && reason.length() <= 256,
                    "location repair requires a campaign, location, destination, expected token, operation, operator, and reason");
            position = position.immutable();
        }
    }

    public record Result(CampaignLocationBinding binding, CampaignLocationRepair receipt, String diagnostic) {
        public boolean accepted() { return binding != null && diagnostic.isBlank(); }
        private static Result failed(String issue) { return new Result(null, null, issue); }
    }
}

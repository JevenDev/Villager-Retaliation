package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.campaign.content.CampaignLocationVariant;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class CampaignLocationVariants {
    private CampaignLocationVariants() { }

    public static String reconcile(MinecraftServer server, ResourceLocation campaign) {
        return reconcile(server, campaign, null);
    }

    static String reconcile(MinecraftServer server, ResourceLocation campaign, UUID throughOutcome) {
        if (!server.isSameThread()) return "Campaign location changes require the server thread.";
        if (CampaignWorldModeSavedData.get(server.overworld()).mode() != CampaignWorldMode.CAMPAIGN) return "Campaign location changes require Campaign Mode.";
        var content = CampaignResources.current(server).campaigns().get(campaign);
        if (content == null) return "Restore the campaign datapack to resume its location changes.";
        var campaigns = CampaignSavedData.get(server.overworld());
        String issue = campaigns.compatibility(content.definition());
        if (!issue.isBlank()) return issue;
        var instance = campaigns.instances().get(campaign);
        if (instance == null) return "The campaign instance has not started.";
        boolean changesLocations = false;
        for (var committed : instance.outcomes().values()) {
            var milestone = content.definition().milestones().get(committed.milestone());
            var outcome = milestone == null ? null : milestone.outcomes().get(committed.outcome());
            if (outcome == null) return "A saved outcome no longer matches its campaign content. Restore the original definition and state.";
            changesLocations |= !outcome.locationVariants().isEmpty();
        }
        if (!changesLocations) return throughOutcome == null || instance.outcomes().values().stream().anyMatch(outcome -> outcome.operationId().equals(throughOutcome))
                ? "" : "The saved campaign outcome is unavailable.";
        var data = CampaignVariantSavedData.get(server.overworld());
        if (!data.diagnostic().isBlank()) return data.diagnostic();
        for (var outcome : instance.outcomes().values().stream().sorted(Comparator.comparingLong(CampaignInstance.OutcomeCommit::revision)).toList()) {
            var milestone = content.definition().milestones().get(outcome.milestone());
            var definition = milestone.outcomes().get(outcome.outcome());
            for (var id : definition.locationVariants()) {
                var variant = content.definition().locationVariants().get(id);
                UUID operation = CampaignVariantReceipt.operation(instance.instanceId(), outcome.operationId(), id);
                var receipt = data.receipts().get(operation);
                try {
                    if (receipt == null) receipt = data.record(new CampaignVariantReceipt(campaign, instance.instanceId(), instance.definitionRevision(),
                            instance.fingerprint(), outcome.milestone(), outcome.operationId(), id, null, java.util.List.of(), 0, null, ""));
                    if (receipt.definitionRevision() != instance.definitionRevision() || !receipt.fingerprint().equals(instance.fingerprint())
                            || !receipt.campaign().equals(campaign) || !receipt.milestone().equals(outcome.milestone()))
                        return "Saved location change ownership is incompatible. Restore matching campaign and variant data.";
                    if (receipt.complete()) continue;
                    receipt = apply(server, data, receipt, variant);
                    if (!receipt.complete()) return receipt.diagnostic();
                } catch (IOException exception) {
                    return "Campaign location changes could not be saved. Check world storage before retrying.";
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    return "Campaign location change is unavailable: " + exception.getMessage();
                }
            }
            if (outcome.operationId().equals(throughOutcome)) return "";
        }
        return throughOutcome == null ? "" : "The saved campaign outcome is unavailable.";
    }

    private static CampaignVariantReceipt apply(MinecraftServer server, CampaignVariantSavedData data,
            CampaignVariantReceipt receipt, CampaignLocationVariant variant) throws IOException {
        var resolved = CampaignLocationService.resolve(server, receipt.campaign(), variant.location());
        if (!resolved.available()) return diagnose(data, receipt, resolved.diagnostic());
        var binding = resolved.binding();
        if (receipt.binding() != null && !receipt.binding().equals(binding)) {
            if (!receipt.binding().sameOwner(binding) || !CampaignLocationSavedData.get(server.overworld()).knows(receipt.binding()))
                return diagnose(data, receipt, "The saved location binding changed without a repair audit. Restore its original campaign location data.");
            binding = receipt.binding();
        }
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, binding.dimension()));
        if (level == null) return diagnose(data, receipt, "The campaign location dimension is unavailable.");
        if (receipt.binding() == null) {
            var generated = generated(level, binding, variant);
            var cells = new ArrayList<CampaignVariantReceipt.Cell>();
            for (var authored : variant.cells()) {
                var cell = generated.get(authored.id());
                if (cell == null) return diagnose(data, receipt, "Load the generated layout for cell " + authored.id() + ". Missing generation ownership cannot be inferred from terrain.");
                if (cell.state() != CampaignGeneratedBlock.State.OWNED) return diagnose(data, receipt, "Generated cell " + authored.id() + " has conflicting ownership.");
                var chunk = level.getChunkSource().getChunkNow(cell.position().getX() >> 4, cell.position().getZ() >> 4);
                if (chunk == null) return diagnose(data, receipt, "Load the campaign location before changing its variant.");
                var prior = CampaignBlockOwnership.receipt(chunk, cell.position());
                if (prior == null || !CampaignBlockOwnership.diagnostic(chunk).isBlank() || prior.state() != CampaignBlockOwnershipLedger.State.APPLIED
                        || !prior.owner().equals(cell.site()) || !knownPrior(data, cell, prior))
                    return diagnose(data, receipt, "Ownership of cell " + authored.id() + " is missing or was lost to a world edit. Inspect or preserve this change.");
                var expected = authored.expected().mirror(cell.mirror()).rotate(cell.rotation());
                if (!prior.placed().state().equals(expected) || !CampaignBlockSnapshot.capture(level, chunk, cell.position()).equals(prior.placed()))
                    return diagnose(data, receipt, "Cell " + authored.id() + " no longer matches its authored expected state. Existing terrain was preserved.");
                var placed = authored.placed().mirror(cell.mirror()).rotate(cell.rotation());
                var target = placed.equals(prior.placed().state()) ? prior.placed() : CampaignBlockSnapshot.defaultState(level, cell.position(), placed);
                var requested = new CampaignBlockOwnershipLedger.Receipt(cell.position(), cell.site(),
                        CampaignVariantReceipt.cellOperation(receipt.operation(), cell.cell()), prior.placed(), target, CampaignBlockOwnershipLedger.State.RESERVED, "");
                cells.add(new CampaignVariantReceipt.Cell(cell, prior, requested));
            }
            receipt = data.record(receipt.plan(binding, cells));
        }
        if (receipt.cells().size() != variant.cells().size()) return diagnose(data, receipt, "The saved location change does not match its authored cells.");
        for (int index = 0; index < receipt.cells().size(); index++) {
            var saved = receipt.cells().get(index);
            var authored = variant.cells().get(index);
            var generated = saved.generated();
            if (!authored.id().equals(generated.cell()) || !generated.layout().equals(variant.layout()) || generated.revision() != variant.layoutRevision()
                    || !saved.previous().placed().state().equals(authored.expected().mirror(generated.mirror()).rotate(generated.rotation()))
                    || !saved.requested().placed().state().equals(authored.placed().mirror(generated.mirror()).rotate(generated.rotation())))
                return diagnose(data, receipt, "The saved location change is incompatible with its authored layout or block states.");
        }
        while (!receipt.complete()) {
            var cell = receipt.cells().get(receipt.cursor());
            var position = cell.generated().position();
            var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
            if (chunk == null) return diagnose(data, receipt, "Load the campaign location to resume its saved cell changes.");
            if (!cell.generated().equals(CampaignGeneratedBlocks.cells(chunk).get(position)))
                return diagnose(data, receipt, "The saved generation evidence changed. Restore the original location ownership.");
            var reserved = CampaignBlockOwnership.advance(level, cell.previous(), cell.requested());
            if (reserved.status() != CampaignBlockOwnership.Status.READY) return diagnose(data, receipt, reserved.diagnostic());
            var applied = CampaignBlockOwnership.apply(level, cell.requested());
            if (applied.status() != CampaignBlockOwnership.Status.APPLIED) return diagnose(data, receipt, applied.diagnostic());
            receipt = data.record(receipt.advanced());
        }
        return receipt;
    }

    private static Map<String, CampaignGeneratedBlock> generated(ServerLevel level, CampaignLocationBinding binding, CampaignLocationVariant variant) {
        var cells = new LinkedHashMap<String, CampaignGeneratedBlock>();
        UUID site = null;
        var origin = binding.position();
        for (int x = (origin.getX() - 64) >> 4; x <= (origin.getX() + 64) >> 4; x++) {
            for (int z = (origin.getZ() - 64) >> 4; z <= (origin.getZ() + 64) >> 4; z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (var cell : CampaignGeneratedBlocks.cells(chunk).values()) {
                    if (!cell.layout().equals(variant.layout()) || cell.revision() != variant.layoutRevision()
                            || !cell.origin().equals(origin) || !cell.dimension().equals(binding.dimension())) continue;
                    if (site != null && !site.equals(cell.site()) || cells.putIfAbsent(cell.cell(), cell) != null)
                        throw new IllegalStateException("Multiple generated sites or cells share this location anchor.");
                    site = cell.site();
                }
            }
        }
        return cells;
    }

    private static boolean knownPrior(CampaignVariantSavedData data, CampaignGeneratedBlock cell, CampaignBlockOwnershipLedger.Receipt prior) {
        if (prior.operation().equals(cell.operation())) return prior.original().equals(prior.placed()) && prior.placed().state().equals(cell.expected());
        for (var receipt : data.receipts().values()) for (int index = 0; index < receipt.cursor(); index++) {
            if (receipt.cells().get(index).requested().sameIdentity(prior)) return true;
        }
        return false;
    }

    private static CampaignVariantReceipt diagnose(CampaignVariantSavedData data, CampaignVariantReceipt receipt, String issue) throws IOException {
        return data.record(receipt.diagnosed(issue.length() <= 512 ? issue : "The campaign location change is unavailable. Inspect its saved ownership and content."));
    }

    public static String preserve(MinecraftServer server, UUID operation, UUID operator, String reason) {
        if (!server.isSameThread()) return "Campaign location repairs require the server thread.";
        var data = CampaignVariantSavedData.get(server.overworld());
        if (!data.diagnostic().isBlank()) return data.diagnostic();
        var receipt = data.receipts().get(operation);
        if (receipt == null) return "No saved location change has that operation ID.";
        if (receipt.complete()) return "This location change is already complete.";
        try {
            var audit = new CampaignVariantReceipt.Audit(operator, reason, server.overworld().getGameTime());
            if (receipt.binding() != null) {
                var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, receipt.binding().dimension()));
                if (level == null) return "Load the campaign location dimension before preserving this change.";
                for (int index = receipt.cursor(); index < receipt.cells().size(); index++) {
                    var cell = receipt.cells().get(index);
                    var result = CampaignBlockOwnership.preserve(level, cell.generated().position(), cell.generated().site(), cell.requested().operation());
                    if (result.status() != CampaignBlockOwnership.Status.PRESERVED) return result.diagnostic();
                }
            }
            data.record(receipt.preserved(audit));
            return "";
        } catch (IOException exception) {
            return "The location repair could not be saved. Check world storage before retrying.";
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return exception.getMessage();
        }
    }

    public static ActionResult poll(MinecraftServer server, ActionResult waiting) {
        if (!waiting.suspended() || waiting.suspensionKind() != ActionResult.SuspensionKind.CAMPAIGN_OUTCOME) return waiting;
        if (server.overworld().getGameTime() % 20L != 0L) return waiting;
        for (var instance : CampaignSavedData.get(server.overworld()).instances().values()) {
            if (instance.outcomes().values().stream().noneMatch(outcome -> outcome.operationId().equals(waiting.suspensionId()))) continue;
            String issue = reconcile(server, instance.campaignId(), waiting.suspensionId());
            return issue.isBlank() ? ActionResult.alreadySatisfied("Campaign location changes are complete.", Set.of(), VillagerActionResult.EMPTY)
                    : ActionResult.suspendedOutcome(waiting.suspensionId(), issue, waiting.capabilities(), waiting.legacyResult());
        }
        return waiting;
    }
}

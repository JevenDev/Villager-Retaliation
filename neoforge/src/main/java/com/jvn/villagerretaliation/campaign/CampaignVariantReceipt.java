package com.jvn.villagerretaliation.campaign;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public record CampaignVariantReceipt(ResourceLocation campaign, UUID instance, int definitionRevision, String fingerprint,
        String milestone, UUID worldOperation, String variant, CampaignLocationBinding binding, List<Cell> cells,
        int cursor, Audit audit, String diagnostic) {
    public CampaignVariantReceipt {
        CampaignStateCodec.require(campaign != null && instance != null && definitionRevision > 0 && fingerprint != null
                && !fingerprint.isBlank() && fingerprint.length() <= 256 && valid(milestone) && worldOperation != null && valid(variant)
                && cells != null && cells.size() <= 256 && cursor >= 0 && cursor <= cells.size()
                && diagnostic != null && diagnostic.length() <= 512, "invalid campaign location change");
        cells = List.copyOf(cells);
        CampaignStateCodec.require(binding == null ? cells.isEmpty() && cursor == 0 : !cells.isEmpty()
                && binding.campaign().equals(campaign) && binding.instance().equals(instance)
                && binding.definitionRevision() == definitionRevision && binding.fingerprint().equals(fingerprint), "invalid location change binding");
        var ids = new HashSet<String>();
        var positions = new HashSet<net.minecraft.core.BlockPos>();
        UUID site = null;
        UUID operation = operation(instance, worldOperation, variant);
        for (var cell : cells) {
            var generated = cell.generated();
            CampaignStateCodec.require(binding != null && generated.dimension().equals(binding.dimension())
                    && generated.origin().equals(binding.position()) && (site == null || site.equals(generated.site()))
                    && ids.add(generated.cell()) && positions.add(generated.position())
                    && cell.requested().operation().equals(cellOperation(operation, generated.cell())), "conflicting location change cells");
            site = generated.site();
        }
    }

    public UUID operation() { return operation(instance, worldOperation, variant); }
    static UUID operation(UUID instance, UUID outcome, String variant) {
        return UUID.nameUUIDFromBytes((instance + "/location_variant/" + outcome + "/" + variant).getBytes(StandardCharsets.UTF_8));
    }
    static UUID cellOperation(UUID operation, String cell) {
        return UUID.nameUUIDFromBytes((operation + "/cell/" + cell).getBytes(StandardCharsets.UTF_8));
    }
    public boolean complete() { return audit != null || binding != null && cursor == cells.size(); }
    CampaignVariantReceipt plan(CampaignLocationBinding location, List<Cell> changes) {
        CampaignStateCodec.require(binding == null && !complete(), "location change is already planned");
        return new CampaignVariantReceipt(campaign, instance, definitionRevision, fingerprint, milestone, worldOperation, variant, location, changes, 0, null, "");
    }
    CampaignVariantReceipt advanced() {
        CampaignStateCodec.require(binding != null && !complete(), "location change has no pending cell");
        return new CampaignVariantReceipt(campaign, instance, definitionRevision, fingerprint, milestone, worldOperation, variant, binding, cells, cursor + 1, null, "");
    }
    CampaignVariantReceipt diagnosed(String issue) {
        return new CampaignVariantReceipt(campaign, instance, definitionRevision, fingerprint, milestone, worldOperation, variant, binding, cells, cursor, audit, issue);
    }
    CampaignVariantReceipt preserved(Audit reason) {
        return new CampaignVariantReceipt(campaign, instance, definitionRevision, fingerprint, milestone, worldOperation, variant, binding, cells, cursor, reason, "");
    }
    boolean sameIntent(CampaignVariantReceipt other) {
        return campaign.equals(other.campaign) && instance.equals(other.instance) && definitionRevision == other.definitionRevision
                && fingerprint.equals(other.fingerprint) && milestone.equals(other.milestone()) && worldOperation.equals(other.worldOperation()) && variant.equals(other.variant());
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("Campaign", campaign.toString());
        tag.putUUID("Instance", instance);
        tag.putInt("Revision", definitionRevision);
        tag.putString("Fingerprint", fingerprint);
        tag.putString("Milestone", milestone);
        tag.putUUID("WorldOperation", worldOperation);
        tag.putString("Variant", variant);
        tag.put("Binding", binding == null ? new CompoundTag() : binding.save());
        var entries = new ListTag();
        for (var cell : cells) {
            var value = new CompoundTag();
            value.put("Generated", cell.generated().save());
            value.put("Previous", cell.previous().save());
            value.put("Requested", cell.requested().save());
            entries.add(value);
        }
        tag.put("Cells", entries);
        tag.putInt("Cursor", cursor);
        var repair = new CompoundTag();
        if (audit != null) {
            repair.putUUID("Operator", audit.operator());
            repair.putString("Reason", audit.reason());
            repair.putLong("Time", audit.time());
        }
        tag.put("Audit", repair);
        tag.putString("Diagnostic", diagnostic);
        return tag;
    }

    static CampaignVariantReceipt load(CompoundTag tag) {
        CampaignStateCodec.require(tag.getAllKeys().equals(java.util.Set.of("Campaign", "Instance", "Revision", "Fingerprint", "Milestone",
                "WorldOperation", "Variant", "Binding", "Cells", "Cursor", "Audit", "Diagnostic")), "unknown location change fields");
        for (String field : List.of("Campaign", "Fingerprint", "Milestone", "Variant", "Diagnostic"))
            CampaignStateCodec.require(tag.contains(field, Tag.TAG_STRING), "invalid location change field " + field);
        CampaignStateCodec.require(tag.hasUUID("Instance") && tag.hasUUID("WorldOperation") && tag.contains("Revision", Tag.TAG_INT)
                && tag.contains("Cursor", Tag.TAG_INT) && tag.contains("Binding", Tag.TAG_COMPOUND) && tag.contains("Audit", Tag.TAG_COMPOUND),
                "incomplete location change identity");
        var cells = new java.util.ArrayList<Cell>();
        for (var raw : CampaignStateCodec.list(tag, "Cells", Tag.TAG_COMPOUND)) {
            var value = (CompoundTag) raw;
            CampaignStateCodec.require(value.getAllKeys().equals(java.util.Set.of("Generated", "Previous", "Requested"))
                    && value.contains("Generated", Tag.TAG_COMPOUND) && value.contains("Previous", Tag.TAG_COMPOUND)
                    && value.contains("Requested", Tag.TAG_COMPOUND), "invalid location change cell");
            cells.add(new Cell(CampaignGeneratedBlock.load(value.getCompound("Generated")),
                    CampaignBlockOwnershipLedger.Receipt.load(value.getCompound("Previous")), CampaignBlockOwnershipLedger.Receipt.load(value.getCompound("Requested"))));
        }
        var savedBinding = tag.getCompound("Binding");
        var binding = savedBinding.isEmpty() ? null : CampaignLocationBinding.load(savedBinding);
        CampaignStateCodec.require(binding == null || binding.save().equals(savedBinding), "unknown location binding fields");
        var repair = tag.getCompound("Audit");
        Audit audit = null;
        if (!repair.isEmpty()) {
            CampaignStateCodec.require(repair.getAllKeys().equals(java.util.Set.of("Operator", "Reason", "Time"))
                    && repair.hasUUID("Operator") && repair.contains("Reason", Tag.TAG_STRING) && repair.contains("Time", Tag.TAG_LONG), "invalid location repair audit");
            audit = new Audit(repair.getUUID("Operator"), repair.getString("Reason"), repair.getLong("Time"));
        }
        return new CampaignVariantReceipt(ResourceLocation.tryParse(tag.getString("Campaign")), tag.getUUID("Instance"), tag.getInt("Revision"),
                tag.getString("Fingerprint"), tag.getString("Milestone"), tag.getUUID("WorldOperation"), tag.getString("Variant"),
                binding, cells, tag.getInt("Cursor"), audit, tag.getString("Diagnostic"));
    }

    private static boolean valid(String id) { return id != null && id.matches(CampaignOutcomeAction.ID_PATTERN); }

    public record Cell(CampaignGeneratedBlock generated, CampaignBlockOwnershipLedger.Receipt previous, CampaignBlockOwnershipLedger.Receipt requested) {
        public Cell {
            CampaignStateCodec.require(generated != null && previous != null && requested != null
                    && generated.state() == CampaignGeneratedBlock.State.OWNED && generated.position().equals(previous.position())
                    && generated.site().equals(previous.owner()) && previous.state() == CampaignBlockOwnershipLedger.State.APPLIED
                    && previous.position().equals(requested.position()) && previous.owner().equals(requested.owner())
                    && previous.placed().equals(requested.original()) && !previous.operation().equals(requested.operation())
                    && requested.state() == CampaignBlockOwnershipLedger.State.RESERVED, "invalid planned location cell");
        }
    }

    public record Audit(UUID operator, String reason, long time) {
        public Audit {
            CampaignStateCodec.require(operator != null && reason != null && !reason.isBlank() && reason.length() <= 512 && time >= 0, "invalid location repair audit");
        }
    }
}

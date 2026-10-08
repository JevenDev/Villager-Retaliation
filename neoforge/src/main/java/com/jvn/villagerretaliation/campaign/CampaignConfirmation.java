package com.jvn.villagerretaliation.campaign;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public record CampaignConfirmation(UUID id, long createdAt, CampaignQuestConsent consent,
        Map<ResourceLocation, CompoundTag> snapshots, String text) {
    public CampaignConfirmation {
        Objects.requireNonNull(id);
        Objects.requireNonNull(consent);
        snapshots = copy(snapshots);
        require(text != null && !text.isBlank()
                && text.length() <= com.jvn.villagerretaliation.network.CampaignQuestConfirmationPayload.MAX_TEXT_LENGTH,
                "Campaign confirmation has no readable consequences.");
        require(!consent.quests().isEmpty() || !consent.decisions().isEmpty(), "Campaign confirmation has no consequences.");
        consent.quests().forEach((quest, entry) -> {
            require(id.equals(entry.acceptance().requestId()) && entry.acceptance().acceptedAt() == createdAt,
                    "Campaign confirmation acceptance belongs to another request.");
        });
        require(snapshots.keySet().containsAll(consent.quests().keySet())
                && consent.decisions().values().stream().map(CampaignOutcomeConsent::questId).allMatch(snapshots::containsKey),
                "Campaign confirmation is missing quest progress.");
        for (var entry : consent.quests().entrySet()) {
            require(entry.getValue().progress().equals(snapshots.get(entry.getKey())), "Campaign confirmation quest progress disagrees with its consent.");
        }
    }

    @Override public Map<ResourceLocation, CompoundTag> snapshots() { return copy(snapshots); }

    public CompoundTag acceptedConsent(long acceptedAt) {
        var quests = new LinkedHashMap<ResourceLocation, CampaignQuestConsent.Entry>();
        consent.quests().forEach((quest, entry) -> {
            var accepted = entry.acceptance();
            quests.put(quest, new CampaignQuestConsent.Entry(new CampaignQuestAcceptance(accepted.requestId(), accepted.instanceId(),
                    accepted.fingerprint(), accepted.worldRevision(), acceptedAt, accepted.fixedOutcomes()), entry.progress()));
        });
        return new CampaignQuestConsent(consent.playerId(), consent.providerId(), quests, consent.decisions()).save();
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putUUID("Id", id);
        tag.putLong("CreatedAt", createdAt);
        tag.put("Consent", consent.save());
        tag.putString("Text", text);
        var references = new ListTag();
        snapshots.forEach((quest, progress) -> {
            var entry = new CompoundTag();
            entry.putString("Quest", quest.toString());
            entry.put("Progress", progress.copy());
            references.add(entry);
        });
        tag.put("Snapshots", references);
        return tag;
    }

    public static CampaignConfirmation load(CompoundTag tag) {
        require(tag.contains("Version", Tag.TAG_INT) && tag.getInt("Version") == 1 && tag.hasUUID("Id")
                && tag.contains("CreatedAt", Tag.TAG_LONG) && tag.contains("Consent", Tag.TAG_COMPOUND)
                && tag.contains("Text", Tag.TAG_STRING), "Campaign confirmation is unreadable or uses an unsupported version.");
        var consent = CampaignQuestConsent.load(tag.getCompound("Consent"));
        require(consent.save().equals(tag.getCompound("Consent")), "Campaign confirmation consent is incomplete.");
        require(tag.get("Snapshots") instanceof ListTag, "Campaign confirmation quest snapshots are missing.");
        var raw = (ListTag) tag.get("Snapshots");
        require(raw.isEmpty() || raw.getElementType() == Tag.TAG_COMPOUND, "Campaign confirmation quest snapshots are unreadable.");
        var snapshots = new LinkedHashMap<ResourceLocation, CompoundTag>();
        for (var item : raw) {
            var entry = (CompoundTag) item;
            String key = entry.getString("Quest");
            var quest = ResourceLocation.tryParse(key);
            require(quest != null && key.contains(":") && entry.contains("Progress", Tag.TAG_COMPOUND), "Campaign confirmation has an invalid quest snapshot.");
            require(snapshots.putIfAbsent(quest, entry.getCompound("Progress")) == null, "Campaign confirmation has a duplicate quest snapshot.");
        }
        return new CampaignConfirmation(tag.getUUID("Id"), tag.getLong("CreatedAt"), consent, snapshots, tag.getString("Text"));
    }

    private static Map<ResourceLocation, CompoundTag> copy(Map<ResourceLocation, CompoundTag> snapshots) {
        var result = new LinkedHashMap<ResourceLocation, CompoundTag>();
        snapshots.forEach((id, tag) -> result.put(Objects.requireNonNull(id), tag.copy()));
        return Collections.unmodifiableMap(result);
    }

    private static void require(boolean valid, String diagnostic) {
        if (!valid) throw new IllegalArgumentException(diagnostic);
    }
}

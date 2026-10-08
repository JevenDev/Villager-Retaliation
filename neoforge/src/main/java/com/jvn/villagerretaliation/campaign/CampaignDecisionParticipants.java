package com.jvn.villagerretaliation.campaign;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public record CampaignDecisionParticipants(UUID conversation, UUID decision, ResourceLocation campaign,
        UUID instance, UUID player, Set<UUID> participants) {
    private static final ThreadLocal<CampaignDecisionParticipants> CURRENT = new ThreadLocal<>();

    public CampaignDecisionParticipants {
        if (conversation == null || decision == null || campaign == null || instance == null || player == null
                || participants == null || !participants.contains(player)) {
            throw new IllegalArgumentException("Campaign participation requires its decision, campaign, and deciding player.");
        }
        participants = Set.copyOf(participants);
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putUUID("Conversation", conversation);
        tag.putUUID("Decision", decision);
        tag.putString("Campaign", campaign.toString());
        tag.putUUID("Instance", instance);
        tag.putUUID("Player", player);
        var people = new ListTag();
        participants.stream().sorted().forEach(id -> {
            var person = new CompoundTag();
            person.putUUID("Player", id);
            people.add(person);
        });
        tag.put("Participants", people);
        return tag;
    }

    public static CampaignDecisionParticipants load(CompoundTag tag) {
        var campaign = ResourceLocation.tryParse(tag.getString("Campaign"));
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1
                || !tag.hasUUID("Conversation") || !tag.hasUUID("Decision") || !tag.hasUUID("Instance")
                || !tag.hasUUID("Player") || campaign == null || !tag.getString("Campaign").contains(":")
                || !(tag.get("Participants") instanceof ListTag people)
                || people.isEmpty() || people.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Saved campaign participation is unreadable. Restore compatible decision data.");
        }
        var participants = new LinkedHashSet<UUID>();
        for (var raw : people) {
            var person = (CompoundTag) raw;
            if (!person.hasUUID("Player") || !participants.add(person.getUUID("Player"))) {
                throw new IllegalArgumentException("Saved campaign participation contains an invalid or duplicate player.");
            }
        }
        return new CampaignDecisionParticipants(tag.getUUID("Conversation"), tag.getUUID("Decision"), campaign,
                tag.getUUID("Instance"), tag.getUUID("Player"), participants);
    }

    public static CompoundTag capture() {
        var current = CURRENT.get();
        return current == null ? new CompoundTag() : current.save();
    }

    public static String issue(CompoundTag tag) {
        if (tag.isEmpty()) return "";
        try { load(tag); return ""; }
        catch (IllegalArgumentException exception) { return exception.getMessage(); }
    }

    public static <T> T withParticipants(CompoundTag tag, Supplier<T> action) {
        var next = tag.isEmpty() ? null : load(tag);
        var previous = CURRENT.get();
        try {
            if (next == null) CURRENT.remove();
            else CURRENT.set(next);
            return action.get();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    static Set<UUID> forOutcome(UUID player, CampaignOutcomeConsent outcome) {
        var current = CURRENT.get();
        return current != null && current.player.equals(player) && current.campaign.equals(outcome.campaignId())
                && current.instance.equals(outcome.instanceId()) ? current.participants : Set.of(player);
    }
}

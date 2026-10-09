package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignStandingDefinition.Scope;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public final class CampaignStanding {
    private CampaignStanding() { }

    static void require(boolean value, String issue) { if (!value) throw new IllegalArgumentException(issue); }
    static boolean id(String value) { return value != null && value.length() <= 256 && CampaignFactFields.validId(value); }

    public record Key(ResourceLocation campaign, UUID instance, UUID player, Scope scope, String subject, UUID character) {
        public Key {
            require(campaign != null && instance != null && player != null && scope != null && id(subject)
                    && (scope == Scope.CHARACTER) == (character != null), "Invalid campaign standing identity");
        }
    }

    public record Subject(Key key, int definitionRevision, String fingerprint, String title, int minimum, int maximum, int initial) {
        public Subject {
            require(key != null && definitionRevision > 0 && fingerprint != null && fingerprint.matches("[0-9a-f]{64}")
                    && title != null && !title.isBlank() && minimum <= maximum && initial >= minimum && initial <= maximum,
                    "Invalid campaign standing definition snapshot");
        }

        boolean compatible(Subject other) {
            return key.equals(other.key) && definitionRevision == other.definitionRevision && fingerprint.equals(other.fingerprint)
                    && minimum == other.minimum && maximum == other.maximum && initial == other.initial;
        }

        int apply(int before, int amount) { return (int) Math.clamp((long) before + amount, minimum, maximum); }
    }

    public record Change(ResourceLocation campaign, UUID instance, UUID player, ResourceLocation quest, UUID questRun,
            Scope scope, String subject, int amount, String operationId) {
        public Change {
            require(campaign != null && instance != null && player != null && quest != null && questRun != null
                    && scope != null && id(subject) && id(operationId), "Invalid campaign standing change");
        }

        public UUID operation() {
            return UUID.nameUUIDFromBytes((instance + "|standing|" + player + "|" + quest + "|" + questRun + "|" + operationId)
                    .getBytes(StandardCharsets.UTF_8));
        }

        boolean owns(Key key) {
            return campaign.equals(key.campaign()) && instance.equals(key.instance()) && player.equals(key.player())
                    && scope == key.scope() && subject.equals(key.subject());
        }
    }

    public record Receipt(Change change, Subject subject, int before, int after, long revision, long committedAt) {
        public Receipt {
            require(change != null && subject != null && change.owns(subject.key()) && before >= subject.minimum()
                    && before <= subject.maximum() && after == subject.apply(before, change.amount()) && revision > 0 && committedAt >= 0,
                    "Invalid campaign standing receipt");
        }
        public UUID operation() { return change.operation(); }
    }

    public record Value(Subject subject, int amount, long revision) {
        public Value {
            require(subject != null && amount >= subject.minimum() && amount <= subject.maximum() && revision > 0,
                    "Invalid campaign standing value");
        }
    }

    public record Result(Receipt receipt, boolean applied) { }
}

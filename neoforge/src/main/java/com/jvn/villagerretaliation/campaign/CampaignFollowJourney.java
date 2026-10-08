package com.jvn.villagerretaliation.campaign;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record CampaignFollowJourney(Key key, ResourceLocation campaign, UUID instance, String fingerprint,
        ResourceLocation definition, String definitionHash, String role, String location, UUID actor,
        ResourceLocation dimension, BlockPos destination, double followDistance, double arrivalDistance,
        Vec3 start, Vec3 position, double distance, long startedAt, long observedAt, State state,
        Map<Participant, Trail> participants) {
    public CampaignFollowJourney {
        CampaignStateCodec.require(key != null && campaign != null && instance != null && text(fingerprint)
                && definition != null && text(definitionHash) && CampaignFactFields.validId(role) && CampaignFactFields.validId(location)
                && actor != null && dimension != null && destination != null && validVector(start) && validVector(position)
                && Double.isFinite(followDistance) && followDistance >= 2 && followDistance <= 32
                && Double.isFinite(arrivalDistance) && arrivalDistance >= 0.25 && arrivalDistance <= 4
                && Double.isFinite(distance) && distance >= 0 && distance + 1.0E-6 >= start.distanceTo(position) && startedAt >= 0 && observedAt >= startedAt
                && state != null && participants != null && !participants.isEmpty(), "invalid campaign follow journey");
        destination = destination.immutable();
        CampaignStateCodec.require(validVector(Vec3.atCenterOf(destination)), "follow destination is outside world bounds");
        participants = Collections.unmodifiableMap(new LinkedHashMap<>(participants));
        CampaignStateCodec.require(participants.keySet().stream().noneMatch(java.util.Objects::isNull)
                && participants.values().stream().noneMatch(java.util.Objects::isNull), "invalid follow participant");
        if (state == State.COMPLETED) {
            Vec3 target = Vec3.atCenterOf(destination);
            double arrival = arrivalDistance * arrivalDistance + 1.0E-6;
            CampaignStateCodec.require(distance > 0 && position.distanceToSqr(target) <= arrival
                    && distance + arrivalDistance + 1.0E-6 >= start.distanceTo(target)
                    && participants.values().stream().allMatch(trail -> trail.distance > 0 && trail.position.distanceToSqr(target) <= arrival),
                    "completed follow journey lacks accompanied arrival");
        }
    }

    public CampaignFollowJourney observed(Vec3 actorPosition, Map<Participant, Trail> witnesses, long time, boolean arrived) {
        return new CampaignFollowJourney(key, campaign, instance, fingerprint, definition, definitionHash, role, location, actor,
                dimension, destination, followDistance, arrivalDistance, start, actorPosition,
                distance + position.distanceTo(actorPosition), startedAt, time, arrived ? State.COMPLETED : State.ACTIVE, witnesses);
    }

    public CampaignFollowJourney cancelled() {
        return new CampaignFollowJourney(key, campaign, instance, fingerprint, definition, definitionHash, role, location, actor,
                dimension, destination, followDistance, arrivalDistance, start, position, distance, startedAt, observedAt, State.CANCELLED, participants);
    }

    public boolean sameIdentity(CampaignFollowJourney other) {
        return other != null && key.equals(other.key) && campaign.equals(other.campaign) && instance.equals(other.instance)
                && fingerprint.equals(other.fingerprint) && definition.equals(other.definition) && definitionHash.equals(other.definitionHash)
                && role.equals(other.role) && location.equals(other.location) && actor.equals(other.actor) && dimension.equals(other.dimension)
                && destination.equals(other.destination) && followDistance == other.followDistance && arrivalDistance == other.arrivalDistance
                && start.equals(other.start) && startedAt == other.startedAt && participants.keySet().equals(other.participants.keySet())
                && participants.entrySet().stream().allMatch(entry -> entry.getValue().start.equals(other.participants.get(entry.getKey()).start));
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("SceneInstance", key.sceneInstance); tag.putString("Step", key.step);
        tag.putString("Campaign", campaign.toString()); tag.putUUID("Instance", instance); tag.putString("Fingerprint", fingerprint);
        tag.putString("Definition", definition.toString()); tag.putString("DefinitionHash", definitionHash);
        tag.putString("Role", role); tag.putString("Location", location); tag.putUUID("Actor", actor);
        tag.putString("Dimension", dimension.toString());
        tag.putInt("X", destination.getX()); tag.putInt("Y", destination.getY()); tag.putInt("Z", destination.getZ());
        tag.putDouble("FollowDistance", followDistance); tag.putDouble("ArrivalDistance", arrivalDistance);
        tag.put("Start", vector(start)); tag.put("Position", vector(position)); tag.putDouble("Distance", distance);
        tag.putLong("StartedAt", startedAt); tag.putLong("ObservedAt", observedAt); tag.putString("State", state.name());
        var witnesses = new ListTag();
        participants.forEach((participant, trail) -> {
            var witness = new CompoundTag();
            witness.putUUID("Player", participant.player); witness.putString("Quest", participant.quest.toString());
            witness.putUUID("QuestRun", participant.questRun); witness.putString("Objective", participant.objective);
            witness.put("Start", vector(trail.start)); witness.put("Position", vector(trail.position)); witness.putDouble("Distance", trail.distance);
            witnesses.add(witness);
        });
        tag.put("Participants", witnesses);
        return tag;
    }

    static CampaignFollowJourney load(CompoundTag tag) {
        for (String key : new String[]{"SceneInstance", "Instance", "Actor"}) CampaignStateCodec.require(tag.hasUUID(key), "invalid follow identity " + key);
        for (String key : new String[]{"Step", "Campaign", "Fingerprint", "Definition", "DefinitionHash", "Role", "Location", "Dimension", "State"})
            CampaignStateCodec.require(tag.contains(key, Tag.TAG_STRING), "invalid follow text " + key);
        for (String key : new String[]{"X", "Y", "Z"}) CampaignStateCodec.require(tag.contains(key, Tag.TAG_INT), "invalid follow destination " + key);
        for (String key : new String[]{"StartedAt", "ObservedAt"}) CampaignStateCodec.require(tag.contains(key, Tag.TAG_LONG), "invalid follow time " + key);
        for (String key : new String[]{"FollowDistance", "ArrivalDistance", "Distance"}) CampaignStateCodec.require(tag.contains(key, Tag.TAG_DOUBLE), "invalid follow distance " + key);
        var participants = new LinkedHashMap<Participant, Trail>();
        for (var raw : CampaignStateCodec.list(tag, "Participants", Tag.TAG_COMPOUND)) {
            var value = (CompoundTag) raw;
            CampaignStateCodec.require(value.hasUUID("Player") && value.hasUUID("QuestRun") && value.contains("Quest", Tag.TAG_STRING)
                    && value.contains("Objective", Tag.TAG_STRING) && value.contains("Distance", Tag.TAG_DOUBLE), "invalid follow participant fields");
            var participant = new Participant(value.getUUID("Player"), ResourceLocation.tryParse(value.getString("Quest")),
                    value.getUUID("QuestRun"), value.getString("Objective"));
            var trail = new Trail(vector(value, "Start"), vector(value, "Position"), value.getDouble("Distance"));
            CampaignStateCodec.require(participants.putIfAbsent(participant, trail) == null, "duplicate follow participant");
        }
        return new CampaignFollowJourney(new Key(tag.getUUID("SceneInstance"), tag.getString("Step")),
                ResourceLocation.tryParse(tag.getString("Campaign")), tag.getUUID("Instance"), tag.getString("Fingerprint"),
                ResourceLocation.tryParse(tag.getString("Definition")), tag.getString("DefinitionHash"), tag.getString("Role"), tag.getString("Location"),
                tag.getUUID("Actor"), ResourceLocation.tryParse(tag.getString("Dimension")), new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")),
                tag.getDouble("FollowDistance"), tag.getDouble("ArrivalDistance"), vector(tag, "Start"), vector(tag, "Position"),
                tag.getDouble("Distance"), tag.getLong("StartedAt"), tag.getLong("ObservedAt"), State.valueOf(tag.getString("State")), participants);
    }

    private static boolean text(String value) { return value != null && !value.isBlank() && value.length() <= 256; }

    private static boolean validVector(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z)
                && Math.abs(value.x) <= 30_000_001 && Math.abs(value.y) <= 30_000_001 && Math.abs(value.z) <= 30_000_001;
    }

    private static CompoundTag vector(Vec3 value) {
        var tag = new CompoundTag();
        tag.putDouble("X", value.x); tag.putDouble("Y", value.y); tag.putDouble("Z", value.z);
        return tag;
    }

    private static Vec3 vector(CompoundTag parent, String key) {
        CampaignStateCodec.require(parent.contains(key, Tag.TAG_COMPOUND), "missing follow position " + key);
        var tag = parent.getCompound(key);
        CampaignStateCodec.require(tag.contains("X", Tag.TAG_DOUBLE) && tag.contains("Y", Tag.TAG_DOUBLE) && tag.contains("Z", Tag.TAG_DOUBLE),
                "invalid follow coordinates");
        return new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
    }

    public enum State { ACTIVE, COMPLETED, CANCELLED }

    public record Key(UUID sceneInstance, String step) {
        public Key { CampaignStateCodec.require(sceneInstance != null && CampaignFactFields.validId(step), "invalid follow operation"); }
    }

    public record Participant(UUID player, ResourceLocation quest, UUID questRun, String objective) {
        public Participant {
            CampaignStateCodec.require(player != null && quest != null && questRun != null && text(objective), "invalid follow participant ownership");
        }
    }

    public record Trail(Vec3 start, Vec3 position, double distance) {
        public Trail {
            CampaignStateCodec.require(validVector(start) && validVector(position) && Double.isFinite(distance)
                    && distance >= 0 && distance + 1.0E-6 >= start.distanceTo(position), "invalid followed path");
        }
        public Trail observed(Vec3 next) { return new Trail(start, next, distance + position.distanceTo(next)); }
    }
}

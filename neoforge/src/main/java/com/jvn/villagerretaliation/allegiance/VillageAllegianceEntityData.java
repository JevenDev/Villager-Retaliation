package com.jvn.villagerretaliation.allegiance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

public final class VillageAllegianceEntityData {
    public static final String ROOT_TAG = "VillagerRetaliationVillageAllegiance";
    private static final String VERSION = "DataVersion";
    private static final String STATE = "State";
    private static final String PRIMARY = "Primary";
    private static final String SOURCE = "Source";
    private static final String CONFIDENCE = "Confidence";
    private static final String ASSIGNED_TIME = "AssignedGameTime";
    private static final String ORIGIN_DIMENSION = "OriginDimension";
    private static final String ORIGIN_X = "OriginX";
    private static final String ORIGIN_Y = "OriginY";
    private static final String ORIGIN_Z = "OriginZ";
    private static final String PROTECTED_PARENTS = "ProtectedParents";
    private static final String ID = "Id";
    private static final String HISTORY = "History";
    private static final String PREVIOUS_STATE = "PreviousState";
    private static final String PREVIOUS_VILLAGE = "PreviousVillage";
    private static final String NEW_STATE = "NewState";
    private static final String NEW_VILLAGE = "NewVillage";
    private static final String GAME_TIME = "GameTime";
    private static final String RESPONSIBLE_PLAYER = "ResponsiblePlayer";
    private static final int MAX_HISTORY = 8;
    private static final String PENDING_ASSIGNMENT = "PendingAssignment";
    private static final String PENDING_DIMENSION = "Dimension";
    private static final String PENDING_ATTEMPTS = "Attempts";
    private static final String PENDING_NEXT_ATTEMPT = "NextAttemptGameTime";

    private VillageAllegianceEntityData() {
    }

    public static Optional<VillageAllegianceData> read(Entity entity) {
        if (entity == null || !entity.getPersistentData().contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        return Optional.of(readPayload(entity.getPersistentData().getCompound(ROOT_TAG), entity));
    }

    public static void write(Entity entity, VillageAllegianceData data) {
        if (entity == null || data == null) {
            return;
        }
        Optional<VillageAllegianceData> previous = read(entity);
        List<VillageAllegianceHistoryEntry> history = new ArrayList<>(readHistory(entity));
        if (previous.isEmpty() || allegianceChanged(previous.get(), data)) {
            history.add(new VillageAllegianceHistoryEntry(
                    previous.map(VillageAllegianceData::state).orElse(null),
                    previous.map(VillageAllegianceData::primary).orElse(null),
                    data.state(), data.primary(), data.assignmentSource(), data.assignedGameTime(), null));
            if (history.size() > MAX_HISTORY) {
                history = new ArrayList<>(history.subList(history.size() - MAX_HISTORY, history.size()));
            }
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION, VillageAllegianceData.CURRENT_VERSION);
        tag.putString(STATE, data.state().name());
        if (data.isKnown()) {
            tag.putUUID(PRIMARY, data.primary().value());
        }
        tag.putString(SOURCE, data.assignmentSource().name());
        tag.putString(CONFIDENCE, data.confidence().name());
        tag.putLong(ASSIGNED_TIME, data.assignedGameTime());
        if (data.originDimension() != null) {
            tag.putString(ORIGIN_DIMENSION, data.originDimension().toString());
        }
        tag.putInt(ORIGIN_X, data.originPosition().getX());
        tag.putInt(ORIGIN_Y, data.originPosition().getY());
        tag.putInt(ORIGIN_Z, data.originPosition().getZ());
        ListTag parents = new ListTag();
        for (VillageAllegianceId parent : data.protectedParents()) {
            CompoundTag parentTag = new CompoundTag();
            parentTag.putUUID(ID, parent.value());
            parents.add(parentTag);
        }
        tag.put(PROTECTED_PARENTS, parents);
        tag.put(HISTORY, writeHistory(history));
        entity.getPersistentData().put(ROOT_TAG, tag);
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
    }

    public static void copy(Entity source, Entity outcome, AllegianceAssignmentSource copySource) {
        Optional<VillageAllegianceData> sourceData = read(source);
        if (sourceData.isEmpty() || outcome == null) {
            return;
        }
        VillageAllegianceData data = sourceData.get();
        if (copySource != null && data.isKnown()) {
            data = VillageAllegianceData.known(
                    data.primary(), copySource, data.confidence(), data.assignedGameTime(),
                    data.originDimension(), data.originPosition(), data.protectedParents());
        }
        write(outcome, data);
        CompoundTag outcomeRoot = outcome.getPersistentData().getCompound(ROOT_TAG);
        outcomeRoot.put(HISTORY, writeHistory(readHistory(source)));
        outcome.getPersistentData().put(ROOT_TAG, outcomeRoot);
    }

    public static void clear(Entity entity) {
        if (entity != null) {
            entity.getPersistentData().remove(ROOT_TAG);
        }
    }

    public static void clearForRepair(Entity entity) {
        if (entity == null) {
            return;
        }
        List<VillageAllegianceHistoryEntry> history = readHistory(entity);
        entity.getPersistentData().remove(ROOT_TAG);
        if (!history.isEmpty()) {
            CompoundTag root = new CompoundTag();
            root.put(HISTORY, writeHistory(history));
            entity.getPersistentData().put(ROOT_TAG, root);
        }
    }

    public static void writePending(Entity entity, PendingAssignmentData pending) {
        if (entity == null || pending == null) {
            return;
        }
        CompoundTag root = entity.getPersistentData().getCompound(ROOT_TAG);
        CompoundTag tag = new CompoundTag();
        tag.putString(SOURCE, pending.source().name());
        if (pending.dimension() != null) {
            tag.putString(PENDING_DIMENSION, pending.dimension().toString());
        }
        tag.putInt(ORIGIN_X, pending.position().getX());
        tag.putInt(ORIGIN_Y, pending.position().getY());
        tag.putInt(ORIGIN_Z, pending.position().getZ());
        tag.putInt(PENDING_ATTEMPTS, Math.max(0, pending.attempts()));
        tag.putLong(PENDING_NEXT_ATTEMPT, pending.nextAttemptGameTime());
        root.put(PENDING_ASSIGNMENT, tag);
        entity.getPersistentData().put(ROOT_TAG, root);
    }

    public static Optional<PendingAssignmentData> readPending(Entity entity) {
        if (entity == null || !entity.getPersistentData().contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag root = entity.getPersistentData().getCompound(ROOT_TAG);
        if (!root.contains(PENDING_ASSIGNMENT, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag tag = root.getCompound(PENDING_ASSIGNMENT);
        AllegianceAssignmentSource source = enumValue(
                AllegianceAssignmentSource.class, tag.getString(SOURCE));
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(PENDING_DIMENSION));
        if (source == null || dimension == null) {
            return Optional.empty();
        }
        return Optional.of(new PendingAssignmentData(
                dimension,
                new BlockPos(tag.getInt(ORIGIN_X), tag.getInt(ORIGIN_Y), tag.getInt(ORIGIN_Z)),
                source,
                Math.max(0, tag.getInt(PENDING_ATTEMPTS)),
                tag.getLong(PENDING_NEXT_ATTEMPT)));
    }

    public static void clearPending(Entity entity) {
        if (entity == null || !entity.getPersistentData().contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag root = entity.getPersistentData().getCompound(ROOT_TAG);
        root.remove(PENDING_ASSIGNMENT);
        entity.getPersistentData().put(ROOT_TAG, root);
    }

    public static List<VillageAllegianceHistoryEntry> readHistory(Entity entity) {
        if (entity == null || !entity.getPersistentData().contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return List.of();
        }
        List<VillageAllegianceHistoryEntry> history = new ArrayList<>();
        for (Tag raw : entity.getPersistentData().getCompound(ROOT_TAG).getList(HISTORY, Tag.TAG_COMPOUND)) {
            if (!(raw instanceof CompoundTag tag)) {
                continue;
            }
            AllegianceState newState = enumValue(AllegianceState.class, tag.getString(NEW_STATE));
            AllegianceAssignmentSource source = enumValue(
                    AllegianceAssignmentSource.class, tag.getString(SOURCE));
            if (newState == null || source == null) {
                continue;
            }
            history.add(new VillageAllegianceHistoryEntry(
                    enumValue(AllegianceState.class, tag.getString(PREVIOUS_STATE)),
                    tag.hasUUID(PREVIOUS_VILLAGE)
                            ? new VillageAllegianceId(tag.getUUID(PREVIOUS_VILLAGE)) : null,
                    newState,
                    tag.hasUUID(NEW_VILLAGE) ? new VillageAllegianceId(tag.getUUID(NEW_VILLAGE)) : null,
                    source,
                    tag.getLong(GAME_TIME),
                    tag.hasUUID(RESPONSIBLE_PLAYER) ? tag.getUUID(RESPONSIBLE_PLAYER) : null));
        }
        return List.copyOf(history);
    }

    public static void annotateLatestHistoryActor(Entity entity, UUID playerId) {
        if (entity == null || playerId == null) {
            return;
        }
        List<VillageAllegianceHistoryEntry> history = new ArrayList<>(readHistory(entity));
        if (history.isEmpty()) {
            return;
        }
        VillageAllegianceHistoryEntry latest = history.getLast();
        history.set(history.size() - 1, new VillageAllegianceHistoryEntry(
                latest.previousState(), latest.previousVillage(), latest.newState(), latest.newVillage(),
                latest.source(), latest.gameTime(), playerId));
        CompoundTag root = entity.getPersistentData().getCompound(ROOT_TAG);
        root.put(HISTORY, writeHistory(history));
        entity.getPersistentData().put(ROOT_TAG, root);
    }

    private static boolean allegianceChanged(VillageAllegianceData previous, VillageAllegianceData current) {
        return previous.state() != current.state()
                || !java.util.Objects.equals(previous.primary(), current.primary());
    }

    private static ListTag writeHistory(List<VillageAllegianceHistoryEntry> history) {
        ListTag tags = new ListTag();
        for (VillageAllegianceHistoryEntry entry : history) {
            CompoundTag tag = new CompoundTag();
            if (entry.previousState() != null) {
                tag.putString(PREVIOUS_STATE, entry.previousState().name());
            }
            if (entry.previousVillage() != null) {
                tag.putUUID(PREVIOUS_VILLAGE, entry.previousVillage().value());
            }
            tag.putString(NEW_STATE, entry.newState().name());
            if (entry.newVillage() != null) {
                tag.putUUID(NEW_VILLAGE, entry.newVillage().value());
            }
            tag.putString(SOURCE, entry.source().name());
            tag.putLong(GAME_TIME, entry.gameTime());
            if (entry.responsiblePlayer() != null) {
                tag.putUUID(RESPONSIBLE_PLAYER, entry.responsiblePlayer());
            }
            tags.add(tag);
        }
        return tags;
    }

    private static VillageAllegianceData readPayload(CompoundTag tag, Entity entity) {
        int version = tag.getInt(VERSION);
        if (version <= 0 || version > VillageAllegianceData.CURRENT_VERSION) {
            return conservativeUnknown(entity);
        }
        AllegianceState state = enumValue(AllegianceState.class, tag.getString(STATE));
        AllegianceAssignmentSource source = enumValue(AllegianceAssignmentSource.class, tag.getString(SOURCE));
        AllegianceConfidence confidence = enumValue(AllegianceConfidence.class, tag.getString(CONFIDENCE));
        if (state == null || source == null || confidence == null) {
            return conservativeUnknown(entity);
        }
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(ORIGIN_DIMENSION));
        BlockPos position = new BlockPos(tag.getInt(ORIGIN_X), tag.getInt(ORIGIN_Y), tag.getInt(ORIGIN_Z));
        long assignedTime = tag.getLong(ASSIGNED_TIME);
        List<VillageAllegianceId> parents = new ArrayList<>();
        for (Tag raw : tag.getList(PROTECTED_PARENTS, Tag.TAG_COMPOUND)) {
            if (raw instanceof CompoundTag parentTag && parentTag.hasUUID(ID)) {
                parents.add(new VillageAllegianceId(parentTag.getUUID(ID)));
            }
        }
        if (state == AllegianceState.UNKNOWN) {
            return VillageAllegianceData.unknown(
                    source, confidence, assignedTime, dimension, position, parents);
        }
        if (state == AllegianceState.UNAFFILIATED) {
            return VillageAllegianceData.unaffiliated(source, assignedTime, dimension, position);
        }
        if (!tag.hasUUID(PRIMARY)) {
            return conservativeUnknown(entity);
        }
        try {
            return VillageAllegianceData.known(
                    new VillageAllegianceId(tag.getUUID(PRIMARY)), source, confidence,
                    assignedTime, dimension, position, parents);
        } catch (RuntimeException ignored) {
            return conservativeUnknown(entity);
        }
    }

    private static VillageAllegianceData conservativeUnknown(Entity entity) {
        ResourceLocation dimension = entity == null ? null : entity.level().dimension().location();
        BlockPos position = entity == null ? BlockPos.ZERO : entity.blockPosition();
        long gameTime = entity == null ? 0L : entity.level().getGameTime();
        return VillageAllegianceData.unknown(
                AllegianceAssignmentSource.MIGRATION,
                AllegianceConfidence.LEGACY_INFERRED,
                gameTime,
                dimension,
                position);
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record PendingAssignmentData(
            ResourceLocation dimension,
            BlockPos position,
            AllegianceAssignmentSource source,
            int attempts,
            long nextAttemptGameTime) {
        public PendingAssignmentData {
            position = position == null ? BlockPos.ZERO : position.immutable();
        }
    }
}

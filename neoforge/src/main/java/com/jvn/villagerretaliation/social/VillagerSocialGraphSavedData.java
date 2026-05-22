package com.jvn.villagerretaliation.social;

import com.jvn.villagerretaliation.village.VillageMembership;
import com.jvn.villagerretaliation.villager.VillagerGender;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.saveddata.SavedData;

public class VillagerSocialGraphSavedData extends SavedData {
    private static final int ANCESTOR_GENERATION_LIMIT = 10;
    private static final String DATA_NAME = "villagerretaliation_social_graph";
    private static final String TAG_PROFILES = "Profiles";
    private static final String TAG_RELATIONSHIPS = "Relationships";
    private static final String TAG_ID = "Id";
    private static final String TAG_NAME = "Name";
    private static final String TAG_GENDER = "Gender";
    private static final String TAG_PROFESSION = "Profession";
    private static final String TAG_BABY = "Baby";
    private static final String TAG_ALIVE = "Alive";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_VILLAGE = "Village";
    private static final String TAG_CREATED_GAME_TIME = "CreatedGameTime";
    private static final String TAG_LAST_SEEN_GAME_TIME = "LastSeenGameTime";
    private static final String TAG_LAST_POS = "LastKnownPosition";
    private static final String TAG_DEATH_GAME_TIME = "DeathGameTime";
    private static final String TAG_DEATH_DAY = "DeathDay";
    private static final String TAG_DEATH_CAUSE = "DeathCause";
    private static final String TAG_FROM = "From";
    private static final String TAG_TO = "To";
    private static final String TAG_TYPE = "Type";

    private final Map<UUID, VillagerProfile> profiles = new HashMap<>();
    private final Map<UUID, EnumMap<RelationshipType, Set<UUID>>> relationships = new HashMap<>();

    public static VillagerSocialGraphSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillagerSocialGraphSavedData::new, VillagerSocialGraphSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static VillagerSocialGraphSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        VillagerSocialGraphSavedData data = new VillagerSocialGraphSavedData();
        ListTag profilesTag = tag.getList(TAG_PROFILES, Tag.TAG_COMPOUND);
        for (Tag rawProfile : profilesTag) {
            if (!(rawProfile instanceof CompoundTag profileTag) || !profileTag.hasUUID(TAG_ID)) {
                continue;
            }
            VillagerProfile profile = VillagerProfile.load(profileTag);
            data.profiles.put(profile.id(), profile);
        }

        ListTag relationshipsTag = tag.getList(TAG_RELATIONSHIPS, Tag.TAG_COMPOUND);
        for (Tag rawRelationship : relationshipsTag) {
            if (!(rawRelationship instanceof CompoundTag relationshipTag)
                    || !relationshipTag.hasUUID(TAG_FROM)
                    || !relationshipTag.hasUUID(TAG_TO)) {
                continue;
            }
            RelationshipType type = RelationshipType.bySerializedName(relationshipTag.getString(TAG_TYPE));
            if (type == null) {
                continue;
            }
            data.addRelationshipRaw(relationshipTag.getUUID(TAG_FROM), type, relationshipTag.getUUID(TAG_TO));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag profilesTag = new ListTag();
        for (VillagerProfile profile : this.profiles.values()) {
            profilesTag.add(profile.save());
        }
        tag.put(TAG_PROFILES, profilesTag);

        ListTag relationshipsTag = new ListTag();
        for (Map.Entry<UUID, EnumMap<RelationshipType, Set<UUID>>> subjectEntry : this.relationships.entrySet()) {
            UUID from = subjectEntry.getKey();
            for (Map.Entry<RelationshipType, Set<UUID>> typeEntry : subjectEntry.getValue().entrySet()) {
                for (UUID to : typeEntry.getValue()) {
                    CompoundTag relationshipTag = new CompoundTag();
                    relationshipTag.putUUID(TAG_FROM, from);
                    relationshipTag.putString(TAG_TYPE, typeEntry.getKey().serializedName());
                    relationshipTag.putUUID(TAG_TO, to);
                    relationshipsTag.add(relationshipTag);
                }
            }
        }
        tag.put(TAG_RELATIONSHIPS, relationshipsTag);
        return tag;
    }

    public VillagerProfile ensureProfile(ServerLevel level, Villager villager) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(villager, "villager");

        UUID id = villager.getUUID();
        VillagerProfile profile = this.profiles.get(id);
        if (profile == null) {
            profile = VillagerProfile.create(id, level.getGameTime());
            this.profiles.put(id, profile);
        }

        if (profile.updateFrom(level, villager)) {
            setDirty();
        }
        return profile;
    }

    public void markDead(ServerLevel level, Villager villager, String deathCause) {
        VillagerProfile profile = ensureProfile(level, villager);
        if (profile.markDead(level, deathCause)) {
            setDirty();
        }
    }

    public boolean transferIdentity(UUID sourceId, UUID targetId) {
        if (sourceId == null || targetId == null || sourceId.equals(targetId)) {
            return false;
        }

        boolean changed = false;
        VillagerProfile sourceProfile = this.profiles.remove(sourceId);
        VillagerProfile targetProfile = this.profiles.get(targetId);
        if (sourceProfile != null) {
            if (targetProfile == null) {
                this.profiles.put(targetId, sourceProfile.copyFor(targetId));
            } else {
                targetProfile.mergeFrom(sourceProfile);
            }
            changed = true;
        }

        EnumMap<RelationshipType, Set<UUID>> sourceRelationships = this.relationships.remove(sourceId);
        if (sourceRelationships != null) {
            for (Map.Entry<RelationshipType, Set<UUID>> entry : sourceRelationships.entrySet()) {
                for (UUID target : entry.getValue()) {
                    if (!targetId.equals(target)) {
                        changed |= addRelationshipRaw(targetId, entry.getKey(), target);
                    }
                }
            }
            changed = true;
        }

        for (Map.Entry<UUID, EnumMap<RelationshipType, Set<UUID>>> relationshipEntry : this.relationships.entrySet()) {
            UUID subjectId = relationshipEntry.getKey();
            EnumMap<RelationshipType, Set<UUID>> byType = relationshipEntry.getValue();
            for (Set<UUID> targets : byType.values()) {
                if (targets.remove(sourceId)) {
                    if (!subjectId.equals(targetId)) {
                        targets.add(targetId);
                    }
                    changed = true;
                }
            }
        }

        if (changed) {
            setDirty();
        }
        return changed;
    }

    public void linkParentsAndChild(ServerLevel level, Villager parentA, Villager parentB, Villager child) {
        linkParentsAndChild(level, parentA, parentB, child, RelationshipType.BIRTH_PARENT, RelationshipType.BIRTH_CHILD);
    }

    private void linkParentsAndChild(
            ServerLevel level,
            Villager parentA,
            Villager parentB,
            Villager child,
            RelationshipType parentDetailType,
            RelationshipType childDetailType
    ) {
        ensureProfile(level, parentA);
        ensureProfile(level, parentB);
        ensureProfile(level, child);

        Set<UUID> existingSiblings = new HashSet<>();
        existingSiblings.addAll(relationships(parentA.getUUID(), RelationshipType.CHILD));
        existingSiblings.addAll(relationships(parentB.getUUID(), RelationshipType.CHILD));
        existingSiblings.remove(child.getUUID());

        boolean changed = false;
        changed |= addParentChild(parentA.getUUID(), child.getUUID());
        changed |= addParentChild(parentB.getUUID(), child.getUUID());
        changed |= addParentChildDetail(parentA.getUUID(), child.getUUID(), parentDetailType, childDetailType);
        changed |= addParentChildDetail(parentB.getUUID(), child.getUUID(), parentDetailType, childDetailType);
        for (UUID siblingId : existingSiblings) {
            changed |= addSymmetric(child.getUUID(), RelationshipType.SIBLING, siblingId);
        }

        if (changed) {
            setDirty();
        }
    }

    public VillagerFamilyTreeSnapshot familySnapshot(ServerLevel level, Villager villager) {
        ensureProfile(level, villager);
        UUID villagerId = villager.getUUID();
        return new VillagerFamilyTreeSnapshot(
                relationshipMembers(level, villagerId, RelationshipType.PARENT),
                relationshipMembers(level, villagerId, RelationshipType.BIRTH_PARENT),
                relationshipMembers(level, villagerId, RelationshipType.ADOPTIVE_PARENT),
                stepParents(level, villagerId),
                relationshipMembers(level, villagerId, RelationshipType.SIBLING),
                relationshipMembers(level, villagerId, RelationshipType.SPOUSE),
                relationshipMembers(level, villagerId, RelationshipType.CHILD),
                relationshipMembers(level, villagerId, RelationshipType.FRIEND),
                relationshipMembers(level, villagerId, RelationshipType.RIVAL),
                ancestorGenerations(level, villagerId)
        );
    }

    public Set<UUID> relationships(UUID subjectId, RelationshipType type) {
        EnumMap<RelationshipType, Set<UUID>> byType = this.relationships.get(subjectId);
        if (byType == null) {
            return Set.of();
        }
        Set<UUID> targets = byType.get(type);
        return targets == null || targets.isEmpty() ? Set.of() : Set.copyOf(targets);
    }

    public BreedingValidation validateBreedingPair(ServerLevel level, Villager first, Villager second) {
        VillagerProfile firstProfile = ensureProfile(level, first);
        VillagerProfile secondProfile = ensureProfile(level, second);
        if (first.getUUID().equals(second.getUUID())) {
            return BreedingValidation.blocked("That is the same villager.");
        }
        if (firstProfile.gender() == secondProfile.gender()) {
            return BreedingValidation.blocked("Villagers need opposite genders to have a baby.");
        }
        if (isTooCloselyRelated(first.getUUID(), second.getUUID())) {
            return BreedingValidation.blocked("Those villagers are too closely related.");
        }
        return BreedingValidation.success();
    }

    public BreedingValidation validateAdoptionParents(ServerLevel level, Villager first, Villager second) {
        ensureProfile(level, first);
        ensureProfile(level, second);
        if (first.getUUID().equals(second.getUUID())) {
            return BreedingValidation.blocked("That is the same villager.");
        }
        if (first.isBaby() || second.isBaby()) {
            return BreedingValidation.blocked("Adoption needs adult villagers.");
        }
        if (isTooCloselyRelated(first.getUUID(), second.getUUID())) {
            return BreedingValidation.blocked("Those villagers are too closely related to adopt together.");
        }
        return BreedingValidation.success();
    }

    public BreedingValidation validateAdoption(ServerLevel level, Villager first, Villager second, Villager child) {
        BreedingValidation parentValidation = validateAdoptionParents(level, first, second);
        if (!parentValidation.allowed()) {
            return parentValidation;
        }
        ensureProfile(level, child);
        if (!child.isBaby()) {
            return BreedingValidation.blocked("Adoption needs a baby villager.");
        }
        UUID childId = child.getUUID();
        if (first.getUUID().equals(childId) || second.getUUID().equals(childId)) {
            return BreedingValidation.blocked("Villagers cannot adopt themselves.");
        }
        if (hasLivingParents(level, childId)) {
            return BreedingValidation.blocked("That baby already has living parents.");
        }
        return BreedingValidation.success();
    }

    public void linkAdoptiveParentsAndChild(ServerLevel level, Villager parentA, Villager parentB, Villager child) {
        linkParentsAndChild(level, parentA, parentB, child, RelationshipType.ADOPTIVE_PARENT, RelationshipType.ADOPTIVE_CHILD);
        linkSpouses(level, parentA, parentB);
    }

    public void linkSpouses(ServerLevel level, Villager first, Villager second) {
        ensureProfile(level, first);
        ensureProfile(level, second);
        if (addSymmetric(first.getUUID(), RelationshipType.SPOUSE, second.getUUID())) {
            setDirty();
        }
    }

    private boolean isTooCloselyRelated(UUID firstId, UUID secondId) {
        if (firstId.equals(secondId)) {
            return true;
        }
        if (isAncestorOf(firstId, secondId, 6) || isAncestorOf(secondId, firstId, 6)) {
            return true;
        }
        Set<UUID> firstAncestors = ancestors(firstId, 4);
        Set<UUID> secondAncestors = ancestors(secondId, 4);
        for (UUID ancestorId : firstAncestors) {
            if (secondAncestors.contains(ancestorId)) {
                return true;
            }
        }
        return relationships(firstId, RelationshipType.SIBLING).contains(secondId)
                || relationships(secondId, RelationshipType.SIBLING).contains(firstId);
    }

    private boolean hasLivingParents(ServerLevel level, UUID childId) {
        for (UUID parentId : relationships(childId, RelationshipType.PARENT)) {
            if (isLivingVillager(level, parentId)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLivingVillager(ServerLevel level, UUID villagerId) {
        Entity entity = level.getEntity(villagerId);
        if (entity instanceof Villager villager) {
            return villager.isAlive();
        }
        VillagerProfile profile = this.profiles.get(villagerId);
        return profile != null && profile.alive();
    }

    private boolean isAncestorOf(UUID possibleAncestorId, UUID villagerId, int maxDepth) {
        return ancestors(villagerId, maxDepth).contains(possibleAncestorId);
    }

    private Set<UUID> ancestors(UUID villagerId, int maxDepth) {
        Set<UUID> ancestors = new HashSet<>();
        collectAncestors(villagerId, maxDepth, ancestors);
        return ancestors;
    }

    private void collectAncestors(UUID villagerId, int remainingDepth, Set<UUID> ancestors) {
        if (remainingDepth <= 0) {
            return;
        }
        for (UUID parentId : relationships(villagerId, RelationshipType.PARENT)) {
            if (ancestors.add(parentId)) {
                collectAncestors(parentId, remainingDepth - 1, ancestors);
            }
        }
    }

    private List<VillagerFamilyTreeSnapshot.FamilyMember> stepParents(ServerLevel level, UUID villagerId) {
        Set<UUID> directParents = relationships(villagerId, RelationshipType.PARENT);
        Set<UUID> stepParentIds = new HashSet<>();
        for (UUID parentId : directParents) {
            for (UUID spouseId : relationships(parentId, RelationshipType.SPOUSE)) {
                if (!directParents.contains(spouseId) && !villagerId.equals(spouseId)) {
                    stepParentIds.add(spouseId);
                }
            }
        }
        return membersForIds(level, stepParentIds);
    }

    private List<VillagerFamilyTreeSnapshot.AncestorGeneration> ancestorGenerations(ServerLevel level, UUID villagerId) {
        Map<Integer, Set<UUID>> ancestorsByGeneration = new HashMap<>();
        collectAncestorGenerations(villagerId, 1, ancestorsByGeneration, new HashSet<>());

        List<VillagerFamilyTreeSnapshot.AncestorGeneration> generations = new ArrayList<>();
        for (int generation = 2; generation <= ANCESTOR_GENERATION_LIMIT; generation++) {
            List<VillagerFamilyTreeSnapshot.FamilyMember> ancestors =
                    membersForIds(level, ancestorsByGeneration.getOrDefault(generation, Set.of()));
            if (!ancestors.isEmpty()) {
                generations.add(new VillagerFamilyTreeSnapshot.AncestorGeneration(generation, ancestors));
            }
        }
        return List.copyOf(generations);
    }

    private void collectAncestorGenerations(
            UUID villagerId,
            int parentGeneration,
            Map<Integer, Set<UUID>> ancestorsByGeneration,
            Set<UUID> path
    ) {
        if (parentGeneration > ANCESTOR_GENERATION_LIMIT) {
            return;
        }
        for (UUID parentId : relationships(villagerId, RelationshipType.PARENT)) {
            if (!path.add(parentId)) {
                continue;
            }
            if (parentGeneration >= 2) {
                ancestorsByGeneration
                        .computeIfAbsent(parentGeneration, ignored -> new HashSet<>())
                        .add(parentId);
            }
            collectAncestorGenerations(parentId, parentGeneration + 1, ancestorsByGeneration, path);
            path.remove(parentId);
        }
    }

    private List<VillagerFamilyTreeSnapshot.FamilyMember> relationshipMembers(ServerLevel level, UUID villagerId, RelationshipType type) {
        return membersForIds(level, relationships(villagerId, type));
    }

    private List<VillagerFamilyTreeSnapshot.FamilyMember> membersForIds(ServerLevel level, Set<UUID> memberIds) {
        List<VillagerFamilyTreeSnapshot.FamilyMember> members = new ArrayList<>();
        for (UUID memberId : memberIds) {
            VillagerFamilyTreeSnapshot.FamilyMember member = profileMember(level, memberId);
            if (member != null && !member.name().isBlank()) {
                members.add(member);
            }
        }
        members.sort((first, second) -> first.name().compareToIgnoreCase(second.name()));
        return List.copyOf(members);
    }

    private VillagerFamilyTreeSnapshot.FamilyMember profileMember(ServerLevel level, UUID relativeId) {
        Entity entity = level.getEntity(relativeId);
        if (entity instanceof Villager villager) {
            ensureProfile(level, villager);
            return new VillagerFamilyTreeSnapshot.FamilyMember(
                    VillagerPresetNameRegistry.resolveDisplayName(villager).getString(),
                    VillagerPresetNameRegistry.resolveGender(villager),
                    villager.isAlive()
            );
        }

        VillagerProfile profile = this.profiles.get(relativeId);
        if (profile == null || profile.name().isBlank()) {
            return null;
        }
        return new VillagerFamilyTreeSnapshot.FamilyMember(profile.name(), profile.gender(), profile.alive());
    }

    private boolean addParentChild(UUID parentId, UUID childId) {
        return addRelationshipRaw(childId, RelationshipType.PARENT, parentId)
                | addRelationshipRaw(parentId, RelationshipType.CHILD, childId);
    }

    private boolean addParentChildDetail(UUID parentId, UUID childId, RelationshipType parentType, RelationshipType childType) {
        return addRelationshipRaw(childId, parentType, parentId)
                | addRelationshipRaw(parentId, childType, childId);
    }

    private boolean addSymmetric(UUID subjectId, RelationshipType type, UUID targetId) {
        return addRelationshipRaw(subjectId, type, targetId)
                | addRelationshipRaw(targetId, type, subjectId);
    }

    private boolean addRelationshipRaw(UUID subjectId, RelationshipType type, UUID targetId) {
        if (subjectId == null || targetId == null || subjectId.equals(targetId)) {
            return false;
        }
        return this.relationships
                .computeIfAbsent(subjectId, ignored -> new EnumMap<>(RelationshipType.class))
                .computeIfAbsent(type, ignored -> new HashSet<>())
                .add(targetId);
    }

    public enum RelationshipType {
        PARENT("parent"),
        CHILD("child"),
        BIRTH_PARENT("birth_parent"),
        BIRTH_CHILD("birth_child"),
        ADOPTIVE_PARENT("adoptive_parent"),
        ADOPTIVE_CHILD("adoptive_child"),
        SIBLING("sibling"),
        SPOUSE("spouse"),
        FRIEND("friend"),
        RIVAL("rival");

        private final String serializedName;

        RelationshipType(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return this.serializedName;
        }

        static RelationshipType bySerializedName(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            for (RelationshipType type : values()) {
                if (type.serializedName.equals(value) || type.name().equalsIgnoreCase(value)) {
                    return type;
                }
            }
            return null;
        }
    }

    public record BreedingValidation(boolean allowed, String reason) {
        static BreedingValidation success() {
            return new BreedingValidation(true, "");
        }

        static BreedingValidation blocked(String reason) {
            return new BreedingValidation(false, reason);
        }
    }

    public static class VillagerProfile {
        private final UUID id;
        private String name = "";
        private VillagerGender gender = VillagerGender.MALE;
        private String profession = "";
        private boolean baby;
        private boolean alive = true;
        private String dimension = "";
        private String village = "";
        private long createdGameTime;
        private long lastSeenGameTime;
        private BlockPos lastKnownPosition = BlockPos.ZERO;
        private long deathGameTime = Long.MIN_VALUE;
        private long deathDay = Long.MIN_VALUE;
        private String deathCause = "";

        private VillagerProfile(UUID id) {
            this.id = id;
        }

        static VillagerProfile create(UUID id, long createdGameTime) {
            VillagerProfile profile = new VillagerProfile(id);
            profile.createdGameTime = createdGameTime;
            return profile;
        }

        static VillagerProfile load(CompoundTag tag) {
            VillagerProfile profile = new VillagerProfile(tag.getUUID(TAG_ID));
            profile.name = tag.getString(TAG_NAME);
            VillagerGender loadedGender = VillagerGender.bySerializedName(tag.getString(TAG_GENDER));
            profile.gender = loadedGender == null ? VillagerGender.MALE : loadedGender;
            profile.profession = tag.getString(TAG_PROFESSION);
            profile.baby = tag.getBoolean(TAG_BABY);
            profile.alive = !tag.contains(TAG_ALIVE, Tag.TAG_BYTE) || tag.getBoolean(TAG_ALIVE);
            profile.dimension = tag.getString(TAG_DIMENSION);
            profile.village = tag.getString(TAG_VILLAGE);
            profile.createdGameTime = tag.getLong(TAG_CREATED_GAME_TIME);
            profile.lastSeenGameTime = tag.getLong(TAG_LAST_SEEN_GAME_TIME);
            if (tag.contains(TAG_LAST_POS, Tag.TAG_COMPOUND)) {
                profile.lastKnownPosition = readBlockPos(tag.getCompound(TAG_LAST_POS));
            }
            profile.deathGameTime = tag.contains(TAG_DEATH_GAME_TIME, Tag.TAG_LONG)
                    ? tag.getLong(TAG_DEATH_GAME_TIME)
                    : Long.MIN_VALUE;
            profile.deathDay = tag.contains(TAG_DEATH_DAY, Tag.TAG_LONG)
                    ? tag.getLong(TAG_DEATH_DAY)
                    : Long.MIN_VALUE;
            profile.deathCause = tag.getString(TAG_DEATH_CAUSE);
            return profile;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(TAG_ID, this.id);
            tag.putString(TAG_NAME, this.name);
            tag.putString(TAG_GENDER, this.gender.serializedName());
            tag.putString(TAG_PROFESSION, this.profession);
            tag.putBoolean(TAG_BABY, this.baby);
            tag.putBoolean(TAG_ALIVE, this.alive);
            tag.putString(TAG_DIMENSION, this.dimension);
            tag.putString(TAG_VILLAGE, this.village);
            tag.putLong(TAG_CREATED_GAME_TIME, this.createdGameTime);
            tag.putLong(TAG_LAST_SEEN_GAME_TIME, this.lastSeenGameTime);
            tag.put(TAG_LAST_POS, writeBlockPos(this.lastKnownPosition));
            if (this.deathGameTime != Long.MIN_VALUE) {
                tag.putLong(TAG_DEATH_GAME_TIME, this.deathGameTime);
            }
            if (this.deathDay != Long.MIN_VALUE) {
                tag.putLong(TAG_DEATH_DAY, this.deathDay);
            }
            tag.putString(TAG_DEATH_CAUSE, this.deathCause);
            return tag;
        }

        boolean updateFrom(ServerLevel level, Villager villager) {
            VillagerPresetNameRegistry.ensurePresetNameAssigned(villager);
            String nextName = VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
            VillagerGender nextGender = VillagerPresetNameRegistry.resolveGender(villager);
            String nextProfession = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()).toString();
            String nextDimension = level.dimension().location().toString();
            String nextVillage = resolveVillageKey(level, villager);
            BlockPos nextPos = villager.blockPosition().immutable();

            boolean changed = false;
            changed |= setName(nextName);
            changed |= setGender(nextGender);
            changed |= setProfession(nextProfession);
            changed |= setBaby(villager.isBaby());
            changed |= setAlive(villager.isAlive());
            changed |= setDimension(nextDimension);
            changed |= setVillage(nextVillage);
            changed |= setLastSeenGameTime(level.getGameTime());
            changed |= setLastKnownPosition(nextPos);
            if (villager.isAlive()) {
                changed |= setDeath("", Long.MIN_VALUE, Long.MIN_VALUE);
            }
            return changed;
        }

        boolean markDead(ServerLevel level, String deathCause) {
            return setAlive(false)
                    | setDeath(deathCause == null ? "" : deathCause, level.getGameTime(), level.getDayTime() / 24000L)
                    | setLastSeenGameTime(level.getGameTime());
        }

        VillagerProfile copyFor(UUID newId) {
            VillagerProfile copy = new VillagerProfile(newId);
            copy.mergeFrom(this);
            return copy;
        }

        void mergeFrom(VillagerProfile source) {
            if (source == null) {
                return;
            }
            if (this.name.isBlank()) {
                this.name = source.name;
            }
            if (this.profession.isBlank()) {
                this.profession = source.profession;
            }
            this.gender = source.gender;
            this.baby = source.baby;
            this.alive = source.alive;
            if (this.dimension.isBlank()) {
                this.dimension = source.dimension;
            }
            if (this.village.isBlank()) {
                this.village = source.village;
            }
            if (this.createdGameTime == 0L || source.createdGameTime < this.createdGameTime) {
                this.createdGameTime = source.createdGameTime;
            }
            if (source.lastSeenGameTime > this.lastSeenGameTime) {
                this.lastSeenGameTime = source.lastSeenGameTime;
                this.lastKnownPosition = source.lastKnownPosition;
            }
            if (source.deathGameTime > this.deathGameTime) {
                this.deathGameTime = source.deathGameTime;
                this.deathDay = source.deathDay;
                this.deathCause = source.deathCause;
            }
        }

        public UUID id() {
            return this.id;
        }

        public String name() {
            return this.name;
        }

        public boolean alive() {
            return this.alive;
        }

        public VillagerGender gender() {
            return this.gender;
        }

        private boolean setName(String value) {
            String safeValue = value == null ? "" : value;
            if (this.name.equals(safeValue)) {
                return false;
            }
            this.name = safeValue;
            return true;
        }

        private boolean setProfession(String value) {
            String safeValue = value == null ? "" : value;
            if (this.profession.equals(safeValue)) {
                return false;
            }
            this.profession = safeValue;
            return true;
        }

        private boolean setGender(VillagerGender value) {
            VillagerGender safeValue = value == null ? VillagerGender.MALE : value;
            if (this.gender == safeValue) {
                return false;
            }
            this.gender = safeValue;
            return true;
        }

        private boolean setBaby(boolean value) {
            if (this.baby == value) {
                return false;
            }
            this.baby = value;
            return true;
        }

        private boolean setAlive(boolean value) {
            if (this.alive == value) {
                return false;
            }
            this.alive = value;
            return true;
        }

        private boolean setDimension(String value) {
            String safeValue = value == null ? "" : value;
            if (this.dimension.equals(safeValue)) {
                return false;
            }
            this.dimension = safeValue;
            return true;
        }

        private boolean setVillage(String value) {
            String safeValue = value == null ? "" : value;
            if (this.village.equals(safeValue)) {
                return false;
            }
            this.village = safeValue;
            return true;
        }

        private boolean setLastSeenGameTime(long value) {
            if (this.lastSeenGameTime == value) {
                return false;
            }
            this.lastSeenGameTime = value;
            return true;
        }

        private boolean setLastKnownPosition(BlockPos value) {
            BlockPos safeValue = value == null ? BlockPos.ZERO : value;
            if (this.lastKnownPosition.equals(safeValue)) {
                return false;
            }
            this.lastKnownPosition = safeValue;
            return true;
        }

        private boolean setDeath(String cause, long gameTime, long day) {
            String safeCause = cause == null ? "" : cause;
            if (this.deathCause.equals(safeCause) && this.deathGameTime == gameTime && this.deathDay == day) {
                return false;
            }
            this.deathCause = safeCause;
            this.deathGameTime = gameTime;
            this.deathDay = day;
            return true;
        }

        private static String resolveVillageKey(ServerLevel level, Villager villager) {
            return VillageMembership.resolve(level, villager)
                    .map(area -> level.dimension().location() + "@" + compactPos(area.centerBlock()))
                    .orElse("");
        }

        private static String compactPos(BlockPos pos) {
            return pos.getX() + "," + pos.getY() + "," + pos.getZ();
        }

        private static CompoundTag writeBlockPos(BlockPos pos) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("X", pos.getX());
            tag.putInt("Y", pos.getY());
            tag.putInt("Z", pos.getZ());
            return tag;
        }

        private static BlockPos readBlockPos(CompoundTag tag) {
            return new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
        }
    }
}

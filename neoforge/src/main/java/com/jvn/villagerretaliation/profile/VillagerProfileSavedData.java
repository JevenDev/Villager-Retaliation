package com.jvn.villagerretaliation.profile;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public class VillagerProfileSavedData extends SavedData {
    public static final String DATA_NAME = "villagerretaliation_villager_profiles";

    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_RETIRED_PROFILES = "RetiredProfiles";
    private static final String TAG_VILLAGER = "Villager";
    private static final String TAG_RETIRED_AT_GAME_TIME = "RetiredAtGameTime";
    private static final String TAG_RETIREMENT_REASON = "RetirementReason";

    private final Map<UUID, VillagerProfile> profiles = new HashMap<>();
    private final Map<UUID, RetiredProfile> retiredProfiles = new HashMap<>();
    private final Map<UUID, RewardReceipt> rewardReceipts = new HashMap<>();
    private Tag preservedRewards;
    private String rewardDiagnostic = "";

    public static VillagerProfileSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(() -> {
                    var data = new VillagerProfileSavedData();
                    if (Files.exists(rewardPath(level))) data.rewardDiagnostic = "Villager profiles could not be read. Restore their saved data before delivering campaign rewards.";
                    return data;
                }, VillagerProfileSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static VillagerProfileSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        VillagerProfileSavedData data = new VillagerProfileSavedData();
        ListTag entriesTag = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (Tag rawEntry : entriesTag) {
            if (!(rawEntry instanceof CompoundTag entryTag)) {
                continue;
            }
            VillagerProfile profile = VillagerProfile.load(entryTag);
            if (profile != null) {
                data.profiles.put(profile.villagerUuid(), profile);
            }
        }

        data.readRewards(tag.get("CampaignRewards"));
        ListTag retiredProfilesTag = tag.getList(TAG_RETIRED_PROFILES, Tag.TAG_COMPOUND);
        for (Tag rawEntry : retiredProfilesTag) {
            if (!(rawEntry instanceof CompoundTag entryTag) || !entryTag.hasUUID(TAG_VILLAGER)) {
                continue;
            }
            UUID villagerId = entryTag.getUUID(TAG_VILLAGER);
            RetirementReason reason = RetirementReason.fromSerializedName(entryTag.getString(TAG_RETIREMENT_REASON));
            if (!data.profiles.containsKey(villagerId) || reason == null) {
                continue;
            }
            data.retiredProfiles.put(
                    villagerId,
                    new RetiredProfile(Math.max(0L, entryTag.getLong(TAG_RETIRED_AT_GAME_TIME)), reason)
            );
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entriesTag = new ListTag();
        for (VillagerProfile profile : this.profiles.values()) {
            entriesTag.add(profile.save());
        }
        tag.put(TAG_ENTRIES, entriesTag);

        ListTag retiredProfilesTag = new ListTag();
        for (Map.Entry<UUID, RetiredProfile> entry : this.retiredProfiles.entrySet()) {
            CompoundTag retiredTag = new CompoundTag();
            retiredTag.putUUID(TAG_VILLAGER, entry.getKey());
            retiredTag.putLong(TAG_RETIRED_AT_GAME_TIME, entry.getValue().retiredAtGameTime());
            retiredTag.putString(TAG_RETIREMENT_REASON, entry.getValue().reason().serializedName());
            retiredProfilesTag.add(retiredTag);
        }
        tag.put(TAG_RETIRED_PROFILES, retiredProfilesTag);
        tag.put("CampaignRewards", preservedRewards == null ? writeRewards(rewardReceipts) : preservedRewards.copy());
        return tag;
    }

    public void deliverCampaignReward(ServerLevel level, UUID operation, UUID token, UUID villager, int amount, boolean delivered) throws IOException {
        requireReward(rewardDiagnostic.isBlank(), rewardDiagnostic);
        requireReward(operation != null && token != null && villager != null && (amount == 3 || amount == 5), "Invalid villager encounter reward.");
        var receipt = new RewardReceipt(token, villager, amount);
        var prior = rewardReceipts.get(operation);
        if (prior != null) {
            requireReward(prior.equals(receipt), "The villager reward belongs to a different grant. Restore matching scene and profile data.");
            return;
        }
        requireReward(!delivered, "Villager profiles are older than the delivered encounter reward. Restore matching profile data.");
        requireReward(rewardReceipts.values().stream().noneMatch(value -> value.token().equals(token)), "Duplicate villager reward token.");
        var profile = profiles.get(villager);
        requireReward(profile != null, "The rewarded villager profile is unavailable. Restore its original profile before continuing.");
        var candidate = profile.copyFor(villager);
        candidate.setSocialAttribute(VillagerSocialAttribute.GUTS, candidate.socialAttributes().get(VillagerSocialAttribute.GUTS) + amount, level.getGameTime());
        var receipts = new HashMap<>(rewardReceipts);
        receipts.put(operation, receipt);
        var snapshot = save(new CompoundTag(), level.registryAccess());
        var entries = snapshot.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++)
            if (entries.getCompound(index).hasUUID(TAG_VILLAGER) && entries.getCompound(index).getUUID(TAG_VILLAGER).equals(villager))
                entries.set(index, candidate.save());
        snapshot.put("CampaignRewards", writeRewards(receipts));
        var root = new CompoundTag();
        root.put("data", snapshot);
        net.minecraft.nbt.NbtUtils.addCurrentDataVersion(root);
        net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
        var path = rewardPath(level);
        Files.createDirectories(path.getParent());
        net.neoforged.neoforge.common.IOUtilities.writeNbtCompressed(root, path);
        profile.setSocialAttribute(VillagerSocialAttribute.GUTS, candidate.socialAttributes().get(VillagerSocialAttribute.GUTS), level.getGameTime());
        rewardReceipts.put(operation, receipt);
        setDirty(false);
    }

    private static java.nio.file.Path rewardPath(ServerLevel level) {
        return level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data").resolve(DATA_NAME + ".dat");
    }

    private void readRewards(Tag input) {
        if (input == null) return;
        try {
            requireReward(input instanceof CompoundTag, "Invalid villager reward history.");
            var tag = (CompoundTag) input;
            requireReward(tag.getAllKeys().equals(java.util.Set.of("Version", "Receipts")) && tag.contains("Version", Tag.TAG_INT)
                    && tag.getInt("Version") == 1 && tag.get("Receipts") instanceof ListTag, "Unsupported villager reward history.");
            var entries = (ListTag) tag.get("Receipts");
            requireReward(entries.isEmpty() || entries.getElementType() == Tag.TAG_COMPOUND, "Invalid villager reward receipts.");
            var tokens = new java.util.HashSet<UUID>();
            for (var raw : entries) {
                var entry = (CompoundTag) raw;
                requireReward(entry.hasUUID("Operation") && entry.hasUUID("Token") && entry.hasUUID("Villager")
                        && entry.contains("Amount", Tag.TAG_INT) && (entry.getInt("Amount") == 3 || entry.getInt("Amount") == 5), "Invalid villager reward.");
                requireReward(rewardReceipts.putIfAbsent(entry.getUUID("Operation"), new RewardReceipt(entry.getUUID("Token"),
                        entry.getUUID("Villager"), entry.getInt("Amount"))) == null && tokens.add(entry.getUUID("Token")), "Duplicate villager reward.");
            }
        } catch (IllegalArgumentException exception) {
            rewardReceipts.clear();
            preservedRewards = input.copy();
            rewardDiagnostic = "Villager reward history is unreadable. Restore compatible profile data before continuing.";
        }
    }

    private static CompoundTag writeRewards(Map<UUID, RewardReceipt> receipts) {
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        var entries = new ListTag();
        receipts.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(value -> {
            var entry = new CompoundTag();
            entry.putUUID("Operation", value.getKey());
            entry.putUUID("Token", value.getValue().token());
            entry.putUUID("Villager", value.getValue().villager());
            entry.putInt("Amount", value.getValue().amount());
            entries.add(entry);
        });
        tag.put("Receipts", entries);
        return tag;
    }

    private static void requireReward(boolean valid, String diagnostic) {
        if (!valid) throw new IllegalArgumentException(diagnostic);
    }

    private record RewardReceipt(UUID token, UUID villager, int amount) { }

    @Override
    public void save(java.io.File file, HolderLookup.Provider provider) {
        if (!rewardDiagnostic.isBlank() && preservedRewards == null) return;
        super.save(file, provider);
    }

    public VillagerProfile get(UUID villagerUuid) {
        return this.profiles.get(villagerUuid);
    }

    public boolean hasProfile(UUID villagerUuid) {
        return this.profiles.containsKey(villagerUuid);
    }

    public VillagerProfile put(VillagerProfile profile) {
        VillagerProfile previous = this.profiles.put(profile.villagerUuid(), profile);
        this.retiredProfiles.remove(profile.villagerUuid());
        setDirty();
        return previous;
    }

    public boolean transferVillagerProfile(UUID sourceVillagerId, UUID targetVillagerId) {
        if (sourceVillagerId == null || targetVillagerId == null || sourceVillagerId.equals(targetVillagerId)) {
            return false;
        }

        VillagerProfile source = this.profiles.remove(sourceVillagerId);
        boolean changed = this.retiredProfiles.remove(sourceVillagerId) != null;
        if (source == null) {
            changed |= this.retiredProfiles.remove(targetVillagerId) != null;
            if (changed) {
                setDirty();
            }
            return changed;
        }
        this.profiles.put(targetVillagerId, source.copyFor(targetVillagerId));
        this.retiredProfiles.remove(targetVillagerId);
        setDirty();
        return true;
    }

    public boolean reactivate(UUID villagerUuid) {
        if (this.retiredProfiles.remove(villagerUuid) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public boolean retire(UUID villagerUuid, long gameTime, RetirementReason reason) {
        if (!this.profiles.containsKey(villagerUuid)
                || reason == null
                || this.retiredProfiles.containsKey(villagerUuid)) {
            return false;
        }
        this.retiredProfiles.put(villagerUuid, new RetiredProfile(Math.max(0L, gameTime), reason));
        setDirty();
        return true;
    }

    public int pruneRetiredProfiles(long retiredOnOrBeforeGameTime) {
        int removed = 0;
        Iterator<Map.Entry<UUID, RetiredProfile>> iterator = this.retiredProfiles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, RetiredProfile> entry = iterator.next();
            if (entry.getValue().retiredAtGameTime() > retiredOnOrBeforeGameTime) {
                continue;
            }
            this.profiles.remove(entry.getKey());
            iterator.remove();
            removed++;
        }
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    public Collection<VillagerProfile> profiles() {
        return this.profiles.values();
    }

    public enum RetirementReason {
        DEATH("death"),
        NATURAL_DESPAWN("natural_despawn");

        private final String serializedName;

        RetirementReason(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return this.serializedName;
        }

        private static RetirementReason fromSerializedName(String value) {
            for (RetirementReason reason : values()) {
                if (reason.serializedName.equals(value)) {
                    return reason;
                }
            }
            return null;
        }
    }

    private record RetiredProfile(long retiredAtGameTime, RetirementReason reason) {
    }
}

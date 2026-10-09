package com.jvn.villagerretaliation.campaign;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;

public final class CampaignWorldModeSavedData extends SavedData {
    public static final int CURRENT_DATA_VERSION = 1;
    static final String DATA_NAME = "villagerretaliation_campaign_mode";
    private CampaignWorldMode mode = CampaignWorldMode.LEGACY;
    private String selectionSource = "existing_world";
    private long selectedAt = -1L;
    private boolean selectionCommitted;
    private boolean selectionPersisted;
    private String diagnostic = "";
    private CompoundTag preservedData;

    public static CampaignWorldModeSavedData get(ServerLevel level) {
        ServerLevel overworld = level.dimension().equals(Level.OVERWORLD) ? level : level.getServer().overworld();
        return get(overworld.getDataStorage(), level.getServer().getWorldPath(LevelResource.ROOT).resolve("data"));
    }

    static CampaignWorldModeSavedData get(DimensionDataStorage storage, Path directory) {
        return storage.computeIfAbsent(new SavedData.Factory<>(
                () -> missing(directory), CampaignWorldModeSavedData::load, DataFixTypes.LEVEL), DATA_NAME);
    }

    private static CampaignWorldModeSavedData missing(Path directory) {
        CampaignWorldModeSavedData data = new CampaignWorldModeSavedData();
        if (!Files.notExists(directory.resolve(DATA_NAME + ".dat"))) {
            data.mode = CampaignWorldMode.UNKNOWN;
            data.selectionCommitted = true;
            data.diagnostic = "existing campaign world-mode file could not be read";
        }
        return data;
    }

    public static CampaignWorldModeSavedData load(CompoundTag input, HolderLookup.Provider provider) {
        CampaignWorldModeSavedData data = new CampaignWorldModeSavedData();
        int version = input.contains("DataVersion", Tag.TAG_INT) ? input.getInt("DataVersion") : 0;
        data.selectionCommitted = true;
        data.selectionPersisted = true;
        if (input.contains("DataVersion") && !input.contains("DataVersion", Tag.TAG_INT)
                || input.contains("Mode") && !input.contains("Mode", Tag.TAG_STRING)) {
            data.preserve(input, "invalid campaign world mode data");
            return data;
        }
        if (version > CURRENT_DATA_VERSION || version < 0) {
            data.preserve(input, "unsupported campaign world data version " + version);
            return data;
        }
        CompoundTag migrated = input.copy();
        if (version == 0) {
            if (!migrated.contains("Mode", Tag.TAG_STRING)) migrated.putString("Mode", CampaignWorldMode.LEGACY.id());
            migrated.putInt("DataVersion", CURRENT_DATA_VERSION);
        }
        data.mode = CampaignWorldMode.byId(migrated.getString("Mode"));
        if (data.mode == CampaignWorldMode.UNKNOWN) {
            data.preserve(input, "unrecognized campaign world mode " + migrated.getString("Mode"));
            return data;
        }
        if (migrated.contains("SelectionSource", Tag.TAG_STRING)) data.selectionSource = migrated.getString("SelectionSource");
        if (migrated.contains("SelectedAt", Tag.TAG_LONG)) data.selectedAt = migrated.getLong("SelectedAt");
        return data;
    }

    private void preserve(CompoundTag input, String diagnostic) {
        this.mode = CampaignWorldMode.UNKNOWN;
        this.preservedData = input.copy();
        this.diagnostic = diagnostic;
    }

    public CampaignWorldMode mode() {
        return this.mode;
    }

    public String selectionSource() {
        return this.selectionSource;
    }

    public long selectedAt() {
        return this.selectedAt;
    }

    public String diagnostic() {
        return this.diagnostic;
    }

    boolean selectForNewWorld(CampaignWorldMode mode, String selectionSource, long gameTime) {
        if (this.selectionCommitted || this.preservedData != null) return false;
        if (mode != CampaignWorldMode.CAMPAIGN && mode != CampaignWorldMode.SANDBOX) {
            throw new IllegalArgumentException("new worlds require Campaign or Sandbox mode");
        }
        this.mode = mode;
        this.selectionSource = selectionSource;
        this.selectedAt = gameTime;
        this.selectionCommitted = true;
        setDirty();
        return true;
    }

    void freezeExistingMode() {
        if (this.selectionCommitted) return;
        this.selectionCommitted = true;
        setDirty();
    }

    boolean selectionPending() {
        return this.selectionCommitted && !this.selectionPersisted
                && (this.mode == CampaignWorldMode.CAMPAIGN || this.mode == CampaignWorldMode.SANDBOX);
    }

    void persist(ServerLevel level) throws IOException {
        persist(level.getServer().getWorldPath(LevelResource.ROOT).resolve("data"), level.registryAccess());
    }

    void persist(Path directory, HolderLookup.Provider provider) throws IOException {
        if (this.mode == CampaignWorldMode.UNKNOWN) throw new IOException(this.diagnostic);
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("data", save(new CompoundTag(), provider));
        NbtUtils.addCurrentDataVersion(wrapper);
        IOUtilities.waitUntilIOWorkerComplete();
        Files.createDirectories(directory);
        com.jvn.villagerretaliation.util.AtomicDataFiles.writeCompressed(wrapper, directory.resolve(DATA_NAME + ".dat"));
        this.selectionPersisted = true;
        setDirty(false);
    }

    @Override
    public void save(File file, HolderLookup.Provider provider) {
        if (this.mode != CampaignWorldMode.UNKNOWN) super.save(file, provider);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (this.preservedData != null) return this.preservedData.copy();
        tag.putInt("DataVersion", CURRENT_DATA_VERSION);
        tag.putString("Mode", this.mode.id());
        tag.putString("SelectionSource", this.selectionSource);
        tag.putLong("SelectedAt", this.selectedAt);
        return tag;
    }
}

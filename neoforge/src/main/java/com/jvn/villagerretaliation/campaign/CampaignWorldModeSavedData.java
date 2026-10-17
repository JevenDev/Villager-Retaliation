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
    public static final int CURRENT_DATA_VERSION = 2;
    public static final String DATA_NAME = "villagerretaliation_campaign_mode";
    private CampaignWorldMode mode = CampaignWorldMode.LEGACY;
    private net.minecraft.resources.ResourceLocation worldCampaign;
    private boolean includeDefaultContent;
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
        if (migrated.contains("WorldCampaign")) {
            if (!migrated.contains("WorldCampaign", Tag.TAG_STRING) || !migrated.contains("IncludeDefaultContent", Tag.TAG_BYTE)) {
                data.preserve(input, "invalid world campaign selection");
                return data;
            }
            data.worldCampaign = net.minecraft.resources.ResourceLocation.tryParse(migrated.getString("WorldCampaign"));
            if (data.worldCampaign == null) {
                data.preserve(input, "invalid world campaign ID");
                return data;
            }
            data.includeDefaultContent = migrated.getBoolean("IncludeDefaultContent");
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

    public net.minecraft.resources.ResourceLocation worldCampaign() {
        return this.worldCampaign;
    }

    public boolean includeDefaultContent() {
        return this.includeDefaultContent;
    }

    public static CampaignWorldModeSavedData readSelection(Path world, HolderLookup.Provider provider) throws IOException {
        Path file = world.resolve("data").resolve(DATA_NAME + ".dat");
        if (Files.notExists(file)) return new CampaignWorldModeSavedData();
        CompoundTag wrapper = net.minecraft.nbt.NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.create(1024 * 1024));
        if (!wrapper.contains("data", Tag.TAG_COMPOUND)) throw new IOException("Campaign selection data is missing its saved payload");
        return load(wrapper.getCompound("data"), provider);
    }

    public static void createSelection(Path world, com.jvn.villagerretaliation.campaign.selection.WorldCampaign campaign,
            String authority, HolderLookup.Provider provider) throws IOException {
        if (campaign.locked()) throw new IOException("This campaign is not available yet");
        if (!Files.notExists(world.resolve("data").resolve(DATA_NAME + ".dat"))) {
            throw new IOException("This world already has a permanent campaign selection");
        }
        CampaignWorldModeSavedData data = new CampaignWorldModeSavedData();
        data.selectForNewWorld(CampaignWorldMode.CAMPAIGN, authority, 0);
        data.worldCampaign = campaign.id();
        data.includeDefaultContent = campaign.includeDefaultContent();
        data.persist(world.resolve("data"), provider);
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
        if (this.worldCampaign != null) {
            tag.putString("WorldCampaign", this.worldCampaign.toString());
            tag.putBoolean("IncludeDefaultContent", this.includeDefaultContent);
        }
        tag.putString("SelectionSource", this.selectionSource);
        tag.putLong("SelectedAt", this.selectedAt);
        return tag;
    }
}

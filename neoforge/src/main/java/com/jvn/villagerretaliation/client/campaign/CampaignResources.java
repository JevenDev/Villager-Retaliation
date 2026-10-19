package com.jvn.villagerretaliation.client.campaign;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.neoforged.fml.loading.FMLEnvironment;

public final class CampaignResources {
    public static final String SOURCE_RESOURCES_PROPERTY = "villagerretaliation.campaignSourceResources";

    private CampaignResources() { }

    public static CloseableResourceManager open(PackRepository repository) {
        String configured = FMLEnvironment.production ? null : System.getProperty(SOURCE_RESOURCES_PROPERTY);
        Path directory = configured == null || configured.isBlank() ? null : Path.of(configured).toAbsolutePath().normalize();
        if (directory != null && !Files.isDirectory(directory)) {
            throw new IllegalStateException("Campaign source resources directory is missing: " + directory);
        }
        var packs = new ArrayList<>(repository.openAllSelected());
        if (directory != null) {
            for (int index = 0; index < packs.size(); index++) {
                var pack = packs.get(index);
                if (!pack.packId().equals("mod/villagerretaliation")) continue;
                // replace the built-in pack in place so selected datapacks keep their priority
                packs.set(index, new PathPackResources(pack.location(), directory));
                pack.close();
            }
        }
        return new MultiPackResourceManager(PackType.SERVER_DATA, packs);
    }
}

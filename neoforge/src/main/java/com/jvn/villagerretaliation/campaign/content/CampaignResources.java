package com.jvn.villagerretaliation.campaign.content;

import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog;
import com.jvn.villagerretaliation.quest.debug.QuestDiagnostic;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

public final class CampaignResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static MinecraftServer loadedServer;
    private static CampaignContentCatalog loaded;

    private CampaignResources() {
    }

    public static synchronized CampaignContentCatalog current(MinecraftServer server) {
        if (server == null) return new CampaignContentCatalog(QuestContentCatalog.empty(), Map.of(), List.of());
        QuestContentCatalog quests = QuestContentCatalogs.current(server);
        if (loadedServer == server && loaded != null && loaded.quests() == quests) return loaded;
        List<CampaignContentCatalog.Source> sources = new ArrayList<>();
        var resources = DatapackResourceLoader.jsonResources(server, "campaigns");
        for (var resource : resources) {
            ResourceLocation location = resource.location();
            if (!location.getPath().endsWith("/campaign.json")) continue;
            String directory = location.getPath().substring(0, location.getPath().length() - "campaign.json".length());
            QuestLocaleCatalog locales = QuestLocaleCatalog.empty();
            List<QuestDiagnostic> diagnostics = new ArrayList<>();
            for (var localeResource : resources) {
                ResourceLocation localeLocation = localeResource.location();
                String localePrefix = directory + "locales/";
                if (!localeLocation.getNamespace().equals(location.getNamespace())
                        || !localeLocation.getPath().startsWith(localePrefix)) continue;
                String locale = localeLocation.getPath().substring(localePrefix.length(), localeLocation.getPath().length() - 5);
                if (!locale.matches("[a-z0-9_-]+")) {
                    diagnostics.add(localeError(localeResource, "locale filename must be lowercase and directly inside locales/"));
                    continue;
                }
                var rawLocale = DatapackResourceLoader.readObject(localeLocation, "campaign locale", localeResource.resource());
                if (rawLocale.isEmpty()) {
                    diagnostics.add(localeError(localeResource, "campaign locale could not be read"));
                    continue;
                }
                var result = QuestLocaleCatalog.read(rawLocale.get());
                if (!result.valid()) diagnostics.add(localeError(localeResource, result.error()));
                else locales = locales.overlay(locale, result.messages());
            }
            var root = DatapackResourceLoader.readObject(location, "campaign", resource.resource());
            sources.add(new CampaignContentCatalog.Source(location, resource.resource().sourcePackId(),
                    root.orElse(null), locales, diagnostics));
        }
        CampaignContentCatalog candidate = CampaignContentCatalog.compile(quests, sources);
        for (QuestDiagnostic diagnostic : candidate.diagnostics()) {
            LOGGER.error("Campaign content [{}] {} {}: {}", diagnostic.sourcePack(), diagnostic.resourceId(),
                    diagnostic.jsonPointer(), diagnostic.message());
        }
        loadedServer = server;
        loaded = candidate;
        return candidate;
    }

    private static QuestDiagnostic localeError(DatapackResourceLoader.JsonResource resource, String message) {
        return CampaignContentCatalog.inPack(CampaignSchema.error(resource.location(), "", message), resource.resource().sourcePackId());
    }

    public static synchronized void clearCache() {
        loadedServer = null;
        loaded = null;
    }
}

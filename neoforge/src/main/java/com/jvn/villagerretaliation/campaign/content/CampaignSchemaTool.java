package com.jvn.villagerretaliation.campaign.content;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CampaignSchemaTool {
    private CampaignSchemaTool() { }

    public static void main(String[] args) throws IOException {
        Path output = args.length == 0 ? Path.of("tools", "datapack-builder", "campaign-v1.schema.json") : Path.of(args[0]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        var gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        Files.writeString(output, gson.toJson(CampaignSchema.export()) + System.lineSeparator(), StandardCharsets.UTF_8);
        System.out.println("Wrote campaign schema to " + output.toAbsolutePath());
        Path scenarios = output.resolveSibling("campaign-scenario-v1.schema.json");
        Files.writeString(scenarios, gson.toJson(CampaignScenarioSchema.export()) + System.lineSeparator(), StandardCharsets.UTF_8);
        System.out.println("Wrote campaign scenario schema to " + scenarios.toAbsolutePath());
    }
}

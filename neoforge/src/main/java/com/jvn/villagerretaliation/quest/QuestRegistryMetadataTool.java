package com.jvn.villagerretaliation.quest;

import java.nio.file.Path;

public final class QuestRegistryMetadataTool {
    private QuestRegistryMetadataTool() {
    }

    public static void main(String[] args) throws Exception {
        Path output = args.length > 0
                ? Path.of(args[0])
                : QuestRegistryMetadata.TOOLING_METADATA_PATH;
        QuestRegistryMetadata.write(output);
        java.nio.file.Files.writeString(output.resolveSibling("companion-story.schema.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(
                        com.jvn.villagerretaliation.party.CompanionGrowthService.schema()) + "\n");
        System.out.println("Wrote quest registry metadata to " + output.toAbsolutePath());
    }
}

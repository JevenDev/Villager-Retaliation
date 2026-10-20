package com.jvn.villagerretaliation.dialogue.conversation;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConversationSchemaTool {
    private ConversationSchemaTool() {
    }

    public static void main(String[] args) throws IOException {
        Path output = args.length == 0 ? Path.of("tools", "schemas", "conversation-v1.schema.json") : Path.of(args[0]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        var gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        Files.writeString(output, gson.toJson(ConversationSchema.export()) + System.lineSeparator(), StandardCharsets.UTF_8);
        Path progression = output.resolveSibling("social-progression-v1.schema.json");
        Files.writeString(progression, gson.toJson(com.jvn.villagerretaliation.skill.player.SocialProgressionSchema.export())
                + System.lineSeparator(), StandardCharsets.UTF_8);
        System.out.println("Wrote conversation schema to " + output.toAbsolutePath());
        System.out.println("Wrote social progression schema to " + progression.toAbsolutePath());
    }
}

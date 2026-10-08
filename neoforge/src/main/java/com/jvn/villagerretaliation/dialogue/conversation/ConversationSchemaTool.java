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
        Path output = args.length == 0 ? Path.of("tools", "datapack-builder", "conversation-v1.schema.json") : Path.of(args[0]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        var gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        Files.writeString(output, gson.toJson(ConversationSchema.export()) + System.lineSeparator(), StandardCharsets.UTF_8);
        System.out.println("Wrote conversation schema to " + output.toAbsolutePath());
    }
}

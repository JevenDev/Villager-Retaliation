package com.jvn.villagerretaliation.quest.schema.v2;

import java.nio.file.Path;

public final class QuestV2SchemaTool {
    private QuestV2SchemaTool() {
    }

    public static void main(String[] args) throws Exception {
        Path output = args.length > 0
                ? Path.of(args[0])
                : QuestV2Schema.TOOLING_SCHEMA_PATH;
        QuestV2Schema.write(output);
        System.out.println("Wrote quest module v2 schema to " + output.toAbsolutePath());
    }
}

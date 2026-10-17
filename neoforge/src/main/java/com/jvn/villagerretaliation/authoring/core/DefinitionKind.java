package com.jvn.villagerretaliation.authoring.core;

public enum DefinitionKind {
    TREE, CONVERSATION, FORCED, QUEST, WORLD_CAMPAIGN, RESOURCE;

    public static DefinitionKind fromPath(String path) {
        if (!path.startsWith("data/")) return RESOURCE;
        int namespaceEnd = path.indexOf('/', 5);
        if (namespaceEnd < 0) return RESOURCE;
        String resource = path.substring(namespaceEnd + 1);
        if (resource.startsWith("world_campaigns/")) {
            String[] parts = resource.split("/", 3);
            if (parts.length == 3 && parts[2].equals("campaign.json")) return WORLD_CAMPAIGN;
            if (parts.length == 3) resource = parts[2];
        }
        if (resource.startsWith("dialogue_trees/")) return TREE;
        if (resource.startsWith("forced_dialogue/")) return FORCED;
        if (resource.startsWith("quests/") && resource.contains("/conversations/")) return CONVERSATION;
        if (resource.startsWith("quests/") && resource.endsWith("/quest.json")) return QUEST;
        return RESOURCE;
    }

    public String path(String id) {
        String[] parts = id.split(":", -1);
        if (parts.length != 2 || !parts[0].matches("[a-z0-9_.-]+") || !parts[1].matches("[a-z0-9_./-]+")
                || java.util.Arrays.stream(parts[1].split("/", -1)).anyMatch(p -> p.isEmpty() || p.equals(".") || p.equals(".."))) {
            throw new IllegalArgumentException("Use a namespaced resource ID with a safe path");
        }
        String name = parts[1];
        return "data/" + parts[0] + "/" + switch (this) {
            case TREE -> "dialogue_trees/en_us/" + name + ".json";
            case FORCED -> "forced_dialogue/" + name + ".json";
            case CONVERSATION -> "quests/_shared/conversations/" + name.replace('/', '_') + ".json";
            case QUEST -> "quests/authored/" + name.replace('/', '_') + "/quest.json";
            case WORLD_CAMPAIGN -> "world_campaigns/" + name + "/campaign.json";
            case RESOURCE -> name + ".json";
        };
    }
}

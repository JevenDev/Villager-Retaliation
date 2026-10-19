package com.jvn.villagerretaliation.authoring.core;

public enum DefinitionKind {
    TREE, CONVERSATION, FORCED, QUEST, WORLD_CAMPAIGN, CAMPAIGN_ADDON, RESOURCE;

    public static DefinitionKind fromPath(String path) {
        if (!path.startsWith("data/")) return RESOURCE;
        int namespaceEnd = path.indexOf('/', 5);
        if (namespaceEnd < 0) return RESOURCE;
        String resource = path.substring(namespaceEnd + 1);
        if (resource.startsWith("world_campaigns/") || resource.startsWith("campaign_addons/")) {
            String[] parts = resource.split("/", 3);
            if (parts.length == 3 && parts[0].equals("world_campaigns") && parts[2].equals("campaign.json")) return WORLD_CAMPAIGN;
            if (parts.length == 3 && parts[0].equals("campaign_addons") && parts[2].equals("addon.json")) return CAMPAIGN_ADDON;
            if (parts.length == 3) resource = parts[2];
        }
        if (resource.startsWith("dialogue_trees/")) return TREE;
        if (resource.startsWith("forced_dialogue/")) return FORCED;
        if (resource.startsWith("quests/") && resource.contains("/conversations/")) return CONVERSATION;
        if (resource.startsWith("quests/") && resource.endsWith("/quest.json")) return QUEST;
        return RESOURCE;
    }

    public boolean campaignManifest() {
        return this == WORLD_CAMPAIGN || this == CAMPAIGN_ADDON;
    }

    public static String contentRoot(String path) {
        String[] parts = path.split("/", 5);
        int length = parts.length == 5 && (parts[2].equals("world_campaigns") || parts[2].equals("campaign_addons")) ? 4 : 2;
        return String.join("/", java.util.Arrays.copyOf(parts, Math.min(length, parts.length))) + "/";
    }

    public static String unscopedPath(String path) {
        String root = contentRoot(path);
        return root.split("/").length == 4 ? "data/" + path.split("/", 3)[1] + "/" + path.substring(root.length()) : path;
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
            case CAMPAIGN_ADDON -> "campaign_addons/" + name + "/addon.json";
            case RESOURCE -> name + ".json";
        };
    }
}

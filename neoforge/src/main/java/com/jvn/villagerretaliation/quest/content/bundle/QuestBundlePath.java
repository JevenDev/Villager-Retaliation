package com.jvn.villagerretaliation.quest.content.bundle;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Classifies the public quests/ bundle layout without deriving a persistent content ID from it. */
public record QuestBundlePath(
        ResourceLocation resource,
        Owner owner,
        Kind kind,
        String locale,
        String relativePath) {
    private static final String ROOT = "quests/";

    public QuestBundlePath {
        locale = locale == null ? "" : locale;
        relativePath = relativePath == null ? "" : relativePath;
    }

    public static Classification classify(ResourceLocation resource) {
        if (resource == null || !resource.getPath().startsWith(ROOT)) {
            return Classification.error("resource is outside data/<namespace>/quests/");
        }
        String[] parts = resource.getPath().substring(ROOT.length()).split("/");
        if (parts.length < 3) {
            return Classification.error("quest bundle path is incomplete");
        }
        String namespace = resource.getNamespace();
        if ("_shared".equals(parts[0])) {
            Owner owner = Owner.shared(namespace);
            if ("locales".equals(parts[1]) && parts.length == 3) {
                return locale(resource, owner, parts[2]);
            }
            Kind kind = Kind.sharedDirectory(parts[1]).orElse(null);
            if (kind == null || parts.length != 3 || !parts[2].endsWith(".json")) {
                return Classification.error("_shared accepts locales, pools, scenes, encounters, and rewards JSON");
            }
            return Classification.ok(new QuestBundlePath(
                    resource, owner, kind, "", String.join("/", Arrays.copyOfRange(parts, 2, parts.length))));
        }

        String questline = parts[0];
        String slug = parts[1];
        if (questline.isBlank() || slug.isBlank() || "_shared".equals(questline) || "_shared".equals(slug)) {
            return Classification.error("_shared is reserved and questline/slug must not be blank");
        }
        Owner owner = Owner.quest(namespace, questline, slug);
        if (parts.length == 3 && "quest.json".equals(parts[2])) {
            return Classification.ok(new QuestBundlePath(resource, owner, Kind.QUEST, "", "quest.json"));
        }
        if ("locales".equals(parts[2]) && parts.length == 4) {
            return locale(resource, owner, parts[3]);
        }
        Kind kind = Kind.privateDirectory(parts[2]).orElse(null);
        if (kind == null || parts.length != 4 || !parts[3].endsWith(".json")) {
            return Classification.error("quest bundle accepts quest.json, locales, scenes, encounters, and rewards");
        }
        return Classification.ok(new QuestBundlePath(
                resource, owner, kind, "", String.join("/", Arrays.copyOfRange(parts, 3, parts.length))));
    }

    private static Classification locale(ResourceLocation resource, Owner owner, String fileName) {
        if (!fileName.endsWith(".json")) {
            return Classification.error("locale resource must be a JSON file");
        }
        String locale = fileName.substring(0, fileName.length() - 5).toLowerCase(Locale.ROOT);
        if (locale.isBlank() || !locale.matches("[a-z0-9_-]+")
                || !fileName.equals(locale + ".json")) {
            return Classification.error("locale filename must contain only lowercase locale characters");
        }
        return Classification.ok(new QuestBundlePath(resource, owner, Kind.LOCALE, locale, fileName));
    }

    public enum Kind {
        QUEST, LOCALE, SCENE, ENCOUNTER, REWARD, POOL;

        private static Optional<Kind> sharedDirectory(String value) {
            return switch (value) {
                case "scenes" -> Optional.of(SCENE);
                case "encounters" -> Optional.of(ENCOUNTER);
                case "rewards" -> Optional.of(REWARD);
                case "pools" -> Optional.of(POOL);
                default -> Optional.empty();
            };
        }

        private static Optional<Kind> privateDirectory(String value) {
            return switch (value) {
                case "scenes" -> Optional.of(SCENE);
                case "encounters" -> Optional.of(ENCOUNTER);
                case "rewards" -> Optional.of(REWARD);
                default -> Optional.empty();
            };
        }
    }

    public record Owner(String namespace, String questline, String slug, boolean shared) {
        public Owner {
            namespace = namespace == null ? "" : namespace;
            questline = questline == null ? "" : questline;
            slug = slug == null ? "" : slug;
        }

        public static Owner shared(String namespace) {
            return new Owner(namespace, "_shared", "", true);
        }

        public static Owner quest(String namespace, String questline, String slug) {
            return new Owner(namespace, questline, slug, false);
        }

        public String key() {
            return this.namespace + ":" + (this.shared ? "_shared" : this.questline + "/" + this.slug);
        }
    }

    public record Classification(QuestBundlePath path, String error) {
        public Classification {
            error = error == null ? "" : error;
        }

        public static Classification ok(QuestBundlePath path) {
            return new Classification(path, "");
        }

        public static Classification error(String error) {
            return new Classification(null, error);
        }

        public boolean valid() {
            return this.path != null && this.error.isBlank();
        }
    }
}

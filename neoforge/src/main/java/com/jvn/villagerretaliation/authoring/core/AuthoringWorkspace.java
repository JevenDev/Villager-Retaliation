package com.jvn.villagerretaliation.authoring.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

public final class AuthoringWorkspace {
    private static final String FILE = "workspace.json";
    private static final String RECOVERY = "workspace.recovery.json";
    private List<AuthoringDocument> savedDocuments = List.of();
    private Map<String, byte[]> savedAttachments = Map.of();
    private JsonObject savedPack;
    private boolean recovered;
    private JsonObject recoveryBuffers = new JsonObject();
    private final List<JsonObject> deleted = new ArrayList<>();
    private int savedDeleted;
    private static final long MAX_FILE_BYTES = 32L * 1024 * 1024;
    private static final long MAX_PACK_BYTES = 256L * 1024 * 1024;
    private final Path directory;
    private final List<AuthoringDocument> documents = new ArrayList<>();
    private final Map<String, byte[]> attachments = new LinkedHashMap<>();
    private JsonObject packMetadata = JsonParser.parseString("""
            {"pack":{"pack_format":48,"description":"Villager Retaliation authored content"}}
            """).getAsJsonObject();

    private AuthoringWorkspace(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public static AuthoringWorkspace create(Path directory) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        rejectSymlinks(root);
        if (Files.exists(root)) {
            try (var paths = Files.list(root)) {
                if (paths.findAny().isPresent()) throw new IOException("Choose a new or empty workspace directory");
            }
        }
        Files.createDirectories(root);
        AuthoringWorkspace result = new AuthoringWorkspace(root);
        result.save();
        return result;
    }

    public static AuthoringWorkspace open(Path directory) throws IOException {
        return open(directory, FILE);
    }

    public static AuthoringWorkspace recover(Path directory) throws IOException {
        AuthoringWorkspace workspace = open(directory, RECOVERY);
        workspace.recovered = true;
        return workspace;
    }

    private static AuthoringWorkspace open(Path directory, String file) throws IOException {
        AuthoringWorkspace result = new AuthoringWorkspace(directory);
        rejectSymlinks(result.directory.resolve(file));
        try {
            JsonObject state = JsonParser.parseString(Files.readString(result.directory.resolve(file))).getAsJsonObject();
            if (!state.has("version") || !state.get("version").isJsonPrimitive()
                    || !state.get("version").getAsString().equals("1")) throw new IOException("Unsupported workspace version at /version (expected 1)");
            if (!state.has("documents") || !state.get("documents").isJsonArray())
                throw new IOException("Invalid authoring workspace: /documents must be an array");
            int index = 0;
            for (JsonElement element : state.getAsJsonArray("documents")) {
                if (!element.isJsonObject()) throw new IOException("Invalid authoring workspace: /documents/" + index + " must be an object");
                JsonObject value = element.getAsJsonObject();
                for (String field : List.of("kind", "path", "definition")) {
                    if (!value.has(field) || value.get(field).isJsonNull())
                        throw new IOException("Invalid authoring workspace: missing /documents/" + index + "/" + field);
                }
                for (String field : List.of("kind", "path")) {
                    if (!value.get(field).isJsonPrimitive() || !value.getAsJsonPrimitive(field).isString())
                        throw new IOException("Invalid authoring workspace: /documents/" + index + "/" + field + " must be a string");
                }
                if (!value.get("definition").isJsonObject())
                    throw new IOException("Invalid authoring workspace: /documents/" + index + "/definition must be an object");
                if (value.has("editor") && !value.get("editor").isJsonObject())
                    throw new IOException("Invalid authoring workspace: /documents/" + index + "/editor must be an object");
                AuthoringDocument document = new AuthoringDocument(DefinitionKind.valueOf(value.get("kind").getAsString()),
                        value.get("path").getAsString(), value.getAsJsonObject("definition"));
                document.loadMetadata(value.has("editor") ? value.getAsJsonObject("editor") : new JsonObject());
                document.markSaved();
                result.add(document);
                index++;
            }
            if (state.has("attachments")) {
                if (!state.get("attachments").isJsonObject()) throw new IOException("Invalid authoring workspace: /attachments must be an object");
                for (var entry : state.getAsJsonObject("attachments").entrySet()) {
                    String path = safeRelativePath(entry.getKey());
                    if (result.containsPath(path)) throw new IOException("Duplicate workspace resource " + path);
                    if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString())
                        throw new IOException("Invalid authoring workspace: /attachments/" + JsonPointers.escape(path) + " must be a base64 string");
                    result.attachments.put(path, Base64.getDecoder().decode(entry.getValue().getAsString()));
                }
            }
            if (state.has("pack")) {
                if (!state.get("pack").isJsonObject()) throw new IOException("Invalid authoring workspace: /pack must be an object");
                result.packMetadata = state.getAsJsonObject("pack").deepCopy();
            }
            if (state.has("deleted")) for (JsonElement entry : state.getAsJsonArray("deleted"))
                result.deleted.add(entry.getAsJsonObject().deepCopy());
            if (state.has("recovery_buffers")) result.recoveryBuffers = state.getAsJsonObject("recovery_buffers").deepCopy();
            result.markSaved();
            return result;
        } catch (com.google.gson.JsonParseException | IllegalArgumentException | IllegalStateException ex) {
            throw new IOException("Invalid authoring workspace: " + ex.getMessage(), ex);
        }
    }

    public Path directory() { return directory; }
    public List<AuthoringDocument> documents() { return List.copyOf(documents); }

    public void add(AuthoringDocument document) {
        if (containsPath(document.resourcePath())) throw new IllegalArgumentException("Resource already exists: " + document.resourcePath());
        documents.add(document);
    }

    public void importCampaignImage(AuthoringDocument campaign, Path source) throws IOException {
        if (campaign.kind() != DefinitionKind.WORLD_CAMPAIGN) throw new IllegalArgumentException("Select a campaign first");
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.size(source) > 4 * 1024 * 1024) {
            throw new IOException("Choose a PNG image no larger than 4 MiB");
        }
        byte[] bytes = Files.readAllBytes(source);
        if (bytes.length < 24 || !java.util.Arrays.equals(java.util.Arrays.copyOf(bytes, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10})) {
            throw new IOException("Campaign images must be PNG files");
        }
        String directory = campaign.resourcePath().substring(0, campaign.resourcePath().lastIndexOf('/') + 1);
        String target = directory + "image-" + com.google.common.hash.Hashing.sha256().hashBytes(bytes) + ".png";
        if (documents.stream().anyMatch(document -> document.resourcePath().equals(target))) throw new IOException("Image path is already occupied");
        attachments.put(target, bytes);
        String[] path = target.split("/", 3);
        campaign.edit("Set campaign image", root -> root.addProperty("image", path[1] + ":" + path[2]));
    }

    public byte[] campaignImage(AuthoringDocument campaign) throws IOException {
        String id = DialogueGraph.string(campaign.json(), "image");
        if (id.isBlank() || id.equals("villagerretaliation:mod_icon")) {
            try (var stream = AuthoringWorkspace.class.getResourceAsStream("/villager_retaliation_cover.png")) {
                return stream == null ? new byte[0] : stream.readAllBytes();
            }
        }
        String[] parts = id.split(":", 2);
        if (parts.length != 2) return new byte[0];
        byte[] bytes = attachments.get("data/" + parts[0] + "/" + parts[1]);
        return bytes == null ? new byte[0] : bytes.clone();
    }

    private boolean containsPath(String path) {
        return attachments.keySet().stream().anyMatch(key -> key.equalsIgnoreCase(path))
                || documents.stream().anyMatch(document -> document.resourcePath().equalsIgnoreCase(path));
    }

    public AuthoringDocument createDocument(DefinitionKind kind, String id) {
        String path = kind.path(id);
        if ((kind == DefinitionKind.QUEST || kind == DefinitionKind.CONVERSATION || kind == DefinitionKind.WORLD_CAMPAIGN) && id.substring(id.indexOf(':') + 1).contains("/")) {
            throw new IllegalArgumentException("Quest bundles and world campaigns require a single-segment resource ID");
        }
        if (containsPath(path)) throw new IllegalArgumentException("Resource already exists: " + path);
        if (kind == DefinitionKind.QUEST) {
            String localePath = path.substring(0, path.lastIndexOf('/')) + "/locales/en_us.json";
            if (containsPath(localePath)) throw new IllegalArgumentException("Resource already exists: " + localePath);
        }
        if (kind == DefinitionKind.CONVERSATION) {
            String localePath = "data/" + id.substring(0, id.indexOf(':')) + "/quests/_shared/locales/en_us.json";
            AuthoringDocument locale = documents.stream().filter(document -> document.resourcePath().equalsIgnoreCase(localePath)).findFirst().orElse(null);
            if (containsPath(localePath) && locale == null) throw new IllegalArgumentException("Locale is not an editable JSON object: " + localePath);
            if (locale != null) {
                JsonObject messages = locale.json();
                if (messages.has("messages")) {
                    if (!messages.get("messages").isJsonObject()) throw new IllegalArgumentException("Locale messages must be an object: " + localePath);
                    messages = messages.getAsJsonObject("messages");
                }
                for (String suffix : List.of(".title", ".topic", ".greeting", ".done")) {
                    if (messages.has(id + suffix)) throw new IllegalArgumentException("Conversation message already exists: " + id + suffix);
                }
            }
        }
        JsonObject root = new JsonObject();
        root.addProperty("id", id);
        switch (kind) {
            case TREE -> {
                JsonObject display = new JsonObject();
                display.addProperty("title", id);
                display.addProperty("description", "");
                root.add("display", display);
                root.add("nodes", new JsonObject());
                root.getAsJsonObject("nodes").add("start", DialogueGraph.defaultNode(kind, "start"));
                root.add("entries", JsonParser.parseString("[{\"id\":\"default\",\"label\":\"Talk\",\"start\":\"start\"}]"));
            }
            case FORCED -> {
                root = DialogueGraph.defaultNode(kind, id.substring(id.indexOf(':') + 1));
                root.addProperty("initiate_dialogue", true);
                root.add("options", JsonParser.parseString("[{\"id\":\"leave\",\"label\":\"Goodbye\",\"end_conversation\":true}]"));
            }
            case QUEST -> {
                root.addProperty("schema", "villagerretaliation:quest/v3");
                root.addProperty("localization_prefix", id.substring(0, id.indexOf(':')) + ".quest." + id.substring(id.indexOf(':') + 1));
                root.add("metadata", JsonParser.parseString("{\"questline\":\"authored\"}"));
                root.add("provider", JsonParser.parseString("{\"type\":\"villagerretaliation:villager\"}"));
                root.addProperty("entry_stage", "start");
                JsonArray stages = new JsonArray();
                stages.add(DialogueGraph.defaultNode(kind, "start"));
                root.add("stages", stages);
            }
            case CONVERSATION -> {
                root.addProperty("schema", "villagerretaliation:conversation/v1");
                root.addProperty("revision", 1);
                root.addProperty("character", "provider");
                root.addProperty("quest", id.substring(0, id.indexOf(':')) + ":authored_quest");
                root.add("title", localized(id + ".title"));
                JsonObject topic = new JsonObject();
                topic.addProperty("id", "default");
                topic.addProperty("start", "start");
                topic.add("label", localized(id + ".topic"));
                JsonArray topics = new JsonArray();
                topics.add(topic);
                root.add("topics", topics);
                JsonArray nodes = new JsonArray();
                JsonObject start = DialogueGraph.defaultNode(kind, "start");
                JsonObject exchange = JsonParser.parseString("{\"id\":\"greeting\",\"speaker\":{\"type\":\"role\",\"role\":\"provider\"}}").getAsJsonObject();
                exchange.add("text", localized(id + ".greeting"));
                start.getAsJsonArray("exchanges").add(exchange);
                nodes.add(start);
                root.add("nodes", nodes);
                JsonObject outcome = new JsonObject();
                outcome.addProperty("id", "done");
                outcome.add("label", localized(id + ".done"));
                JsonArray outcomes = new JsonArray();
                outcomes.add(outcome);
                root.add("outcomes", outcomes);
            }
            case WORLD_CAMPAIGN -> {
                root.addProperty("schema", com.jvn.villagerretaliation.campaign.selection.WorldCampaignSchema.ID);
                root.addProperty("name", "New campaign");
                root.addProperty("description", "");
                root.add("authors", new JsonArray());
                root.addProperty("image", "villagerretaliation:mod_icon");
                root.addProperty("locked", true);
                root.addProperty("include_default_content", false);
            }
            case RESOURCE -> { }
        }
        AuthoringDocument result = new AuthoringDocument(kind, path, root);
        add(result);
        if (kind == DefinitionKind.QUEST) {
            add(new AuthoringDocument(DefinitionKind.RESOURCE, path.substring(0, path.lastIndexOf('/')) + "/locales/en_us.json", new JsonObject()));
        }
        if (kind == DefinitionKind.CONVERSATION) {
            String localePath = "data/" + id.substring(0, id.indexOf(':')) + "/quests/_shared/locales/en_us.json";
            AuthoringDocument locale = documents.stream().filter(document -> document.resourcePath().equalsIgnoreCase(localePath)).findFirst().orElse(null);
            if (locale == null) {
                locale = new AuthoringDocument(DefinitionKind.RESOURCE, localePath, new JsonObject());
                add(locale);
            }
            locale.edit("Add conversation messages", messages -> {
                JsonObject values = messages.has("messages") ? messages.getAsJsonObject("messages") : messages;
                values.addProperty(id + ".title", "New conversation");
                values.addProperty(id + ".topic", "Talk");
                values.addProperty(id + ".greeting", "Hello.");
                values.addProperty(id + ".done", "Goodbye");
            });
        }
        return result;
    }

    private static JsonObject localized(String key) {
        JsonObject result = new JsonObject();
        result.addProperty("key", key);
        return result;
    }

    public AuthoringDocument duplicate(AuthoringDocument document, String id) {
        if (document.kind() == DefinitionKind.QUEST) return transferQuestBundle(document, id, true);
        String path = renamedPath(document, id);
        JsonObject definition = document.json();
        if (document.kind() != DefinitionKind.RESOURCE && document.kind() != DefinitionKind.FORCED) definition.addProperty("id", id);
        AuthoringDocument result = new AuthoringDocument(document.kind(), path, definition);
        result.loadMetadata(document.metadata());
        add(result);
        return result;
    }

    public void rename(AuthoringDocument document, String id) {
        if (document.kind() == DefinitionKind.QUEST) {
            transferQuestBundle(document, id, false);
            return;
        }
        if (!documents.contains(document)) throw new IllegalArgumentException("The document does not belong to this workspace");
        String path = renamedPath(document, id);
        if (!path.equals(document.resourcePath()) && containsPath(path)) throw new IllegalArgumentException("Resource already exists: " + path);
        String previousId = document.id();
        Map<AuthoringDocument, String> beforePaths = new LinkedHashMap<>();
        beforePaths.put(document, document.resourcePath());
        document.rename(path, id);
        if (document.kind() == DefinitionKind.CONVERSATION && !previousId.equals(id)) {
            for (AuthoringDocument other : documents) {
                if (other == document) continue;
                JsonObject rewritten = rewriteConversationReferences(other.json(), previousId, id).getAsJsonObject();
                if (!rewritten.equals(other.json())) {
                    beforePaths.put(other, other.resourcePath());
                    other.replaceJson(AuthoringDocument.JSON.toJson(rewritten));
                }
            }
        }
        Map<AuthoringDocument, String> afterPaths = new LinkedHashMap<>();
        beforePaths.keySet().forEach(changed -> afterPaths.put(changed, changed.resourcePath()));
        AuthoringDocument.groupLatestChanges(List.copyOf(beforePaths.keySet()),
                () -> restoreBundlePaths(beforePaths, "", "", Map.of()),
                () -> restoreBundlePaths(afterPaths, "", "", Map.of()));
    }

    private static String renamedPath(AuthoringDocument document, String id) {
        String defaultPath = document.kind().path(id);
        String[] identity = id.split(":", 2);
        if (document.kind() == DefinitionKind.TREE) {
            String[] source = document.resourcePath().split("/", 5);
            if (source.length != 5 || !source[2].equals("dialogue_trees")) throw new IllegalArgumentException("Invalid dialogue tree resource path");
            return safeRelativePath("data/" + identity[0] + "/dialogue_trees/" + source[3] + "/" + identity[1] + ".json");
        }
        if (document.kind() == DefinitionKind.CONVERSATION) {
            String[] source = document.resourcePath().split("/", 3);
            if (source.length != 3 || !source[1].equals(identity[0])) {
                throw new IllegalArgumentException("Move the owning quest bundle to change a conversation namespace and retain its localization");
            }
            String directory = document.resourcePath().substring(0, document.resourcePath().lastIndexOf('/') + 1);
            return safeRelativePath(directory + identity[1].replace('/', '_') + ".json");
        }
        return defaultPath;
    }

    private static JsonElement rewriteConversationReferences(JsonElement value, String previousId, String id) {
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            value.getAsJsonArray().forEach(child -> result.add(rewriteConversationReferences(child, previousId, id)));
            return result;
        }
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            for (var entry : value.getAsJsonObject().entrySet()) {
                JsonElement child = entry.getValue();
                if ((entry.getKey().equals("conversation") || entry.getKey().equals("conversation_id"))
                        && child.isJsonPrimitive() && child.getAsJsonPrimitive().isString() && child.getAsString().equals(previousId)) {
                    result.addProperty(entry.getKey(), id);
                } else result.add(entry.getKey(), rewriteConversationReferences(child, previousId, id));
            }
            return result;
        }
        return value.deepCopy();
    }

    public void remove(AuthoringDocument document) {
        if (!documents.contains(document)) throw new IllegalArgumentException("Select a file in this workspace");
        String bundle = document.kind() == DefinitionKind.QUEST
                ? document.resourcePath().substring(0, document.resourcePath().lastIndexOf('/') + 1) : null;
        JsonObject entry = new JsonObject();
        entry.addProperty("name", document.id());
        JsonArray values = new JsonArray();
        for (AuthoringDocument member : List.copyOf(documents)) {
            if (member == document || bundle != null && member.resourcePath().startsWith(bundle)) {
                values.add(documentState(member));
                documents.remove(member);
            }
        }
        JsonObject files = new JsonObject();
        for (String path : List.copyOf(attachments.keySet())) {
            if (bundle != null && path.startsWith(bundle)) files.addProperty(path, Base64.getEncoder().encodeToString(attachments.remove(path)));
        }
        entry.add("documents", values);
        entry.add("attachments", files);
        deleted.add(entry);
    }

    public List<String> deletedNames() { return deleted.stream().map(value -> value.get("name").getAsString()).toList(); }

    public void restoreDeleted(int index) {
        JsonObject entry = deleted.get(index);
        List<AuthoringDocument> restored = new ArrayList<>();
        for (JsonElement element : entry.getAsJsonArray("documents")) {
            JsonObject value = element.getAsJsonObject();
            String path = value.get("path").getAsString();
            if (containsPath(path)) throw new IllegalArgumentException("Restore would overwrite " + path);
            AuthoringDocument document = new AuthoringDocument(DefinitionKind.valueOf(value.get("kind").getAsString()), path, value.getAsJsonObject("definition"));
            document.loadMetadata(value.getAsJsonObject("editor"));
            restored.add(document);
        }
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (var file : entry.getAsJsonObject("attachments").entrySet()) {
            String path = safeRelativePath(file.getKey());
            if (containsPath(path)) throw new IllegalArgumentException("Restore would overwrite " + path);
            files.put(path, Base64.getDecoder().decode(file.getValue().getAsString()));
        }
        documents.addAll(restored);
        attachments.putAll(files);
        deleted.remove(index);
    }

    private AuthoringDocument transferQuestBundle(AuthoringDocument quest, String id, boolean copy) {
        DefinitionKind.QUEST.path(id);
        String[] identity = id.split(":", 2);
        if (identity[1].contains("/")) throw new IllegalArgumentException("Quest IDs require a single path segment");
        String source = quest.resourcePath().substring(0, quest.resourcePath().lastIndexOf('/') + 1);
        String[] originalPath = source.split("/");
        boolean bundled = originalPath[2].equals("world_campaigns");
        if (bundled && !identity[0].equals(originalPath[1])) throw new IllegalArgumentException("Keep a bundled quest in its campaign namespace");
        String destination = "data/" + identity[0] + (bundled ? "/world_campaigns/" + originalPath[3] : "")
                + "/quests/" + originalPath[bundled ? 5 : 3] + "/" + identity[1] + "/";
        List<AuthoringDocument> members = documents.stream().filter(value -> value.resourcePath().startsWith(source)).toList();
        List<String> binaryMembers = attachments.keySet().stream().filter(path -> path.startsWith(source)).toList();
        for (AuthoringDocument member : members) {
            String target = destination + member.resourcePath().substring(source.length());
            if (containsPath(target) && (copy || !member.resourcePath().equals(target))) {
                throw new IllegalArgumentException("Resource already exists: " + target);
            }
        }
        for (String binary : binaryMembers) {
            String target = destination + binary.substring(source.length());
            if (containsPath(target) && (copy || !binary.equals(target))) throw new IllegalArgumentException("Resource already exists: " + target);
        }
        String oldPrefix = DialogueGraph.string(quest.json(), "localization_prefix");
        String newPrefix = copy || !identity[0].equals(originalPath[1])
                ? identity[0] + ".quest." + identity[1] : oldPrefix;
        Map<String, String> ids = new LinkedHashMap<>();
        ids.put(quest.id(), id);
        for (AuthoringDocument member : members) {
            String memberId = DialogueGraph.string(member.json(), "id");
            if (member == quest || !memberId.contains(":")) continue;
            if (copy) ids.put(memberId, identity[0] + ":" + identity[1] + "__" + memberId.substring(memberId.indexOf(':') + 1).replace('/', '_'));
            else if (!identity[0].equals(originalPath[1])) ids.put(memberId, identity[0] + ":" + memberId.substring(memberId.indexOf(':') + 1));
        }
        AuthoringDocument result = quest;
        Map<AuthoringDocument, String> beforePaths = new LinkedHashMap<>();
        Map<String, byte[]> movedAttachments = new LinkedHashMap<>();
        binaryMembers.forEach(path -> movedAttachments.put(path.substring(source.length()), attachments.get(path).clone()));
        for (AuthoringDocument member : members) {
            JsonObject definition = rewriteReferences(member.json(), ids, oldPrefix, newPrefix).getAsJsonObject();
            if (member == quest) definition.addProperty("localization_prefix", newPrefix);
            String target = destination + member.resourcePath().substring(source.length());
            if (copy) {
                AuthoringDocument duplicated = new AuthoringDocument(member.kind(), target, definition);
                duplicated.loadMetadata(member.metadata());
                add(duplicated);
                if (member == quest) result = duplicated;
            } else {
                beforePaths.put(member, member.resourcePath());
                member.relocate(target, definition);
            }
        }
        for (String binary : binaryMembers) {
            byte[] bytes = attachments.get(binary);
            if (!copy) attachments.remove(binary);
            attachments.put(destination + binary.substring(source.length()), bytes.clone());
        }
        if (!copy) {
            for (AuthoringDocument other : documents) {
                if (members.contains(other)) continue;
                JsonObject rewritten = rewriteReferences(other.json(), ids, "", "").getAsJsonObject();
                if (!rewritten.equals(other.json())) {
                    beforePaths.put(other, other.resourcePath());
                    other.replaceJson(AuthoringDocument.JSON.toJson(rewritten));
                }
            }
            Map<AuthoringDocument, String> afterPaths = new LinkedHashMap<>();
            beforePaths.keySet().forEach(document -> afterPaths.put(document, document.resourcePath()));
            AuthoringDocument.groupLatestChanges(List.copyOf(beforePaths.keySet()),
                    () -> restoreBundlePaths(beforePaths, destination, source, movedAttachments),
                    () -> restoreBundlePaths(afterPaths, source, destination, movedAttachments));
        }
        return result;
    }

    private void restoreBundlePaths(Map<AuthoringDocument, String> restoredPaths, String source, String destination,
            Map<String, byte[]> movedAttachments) {
        for (var entry : restoredPaths.entrySet()) {
            if (!documents.contains(entry.getKey())) throw new IllegalStateException("An affected document was removed from the workspace");
            String target = entry.getValue();
            if (documents.stream().anyMatch(document -> !restoredPaths.containsKey(document) && document.resourcePath().equalsIgnoreCase(target))
                    || attachments.containsKey(target)) {
                throw new IllegalStateException("Another resource now occupies " + target);
            }
        }
        for (var entry : movedAttachments.entrySet()) {
            String from = source + entry.getKey();
            String to = destination + entry.getKey();
            if (!java.util.Arrays.equals(attachments.get(from), entry.getValue())) {
                throw new IllegalStateException("An affected bundle asset was modified: " + from);
            }
            if (!from.equals(to) && containsPath(to)) throw new IllegalStateException("Another resource now occupies " + to);
        }
        for (var entry : movedAttachments.entrySet()) attachments.remove(source + entry.getKey());
        for (var entry : movedAttachments.entrySet()) attachments.put(destination + entry.getKey(), entry.getValue().clone());
    }

    private static JsonElement rewriteReferences(JsonElement value, Map<String, String> ids, String oldPrefix, String newPrefix) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String text = value.getAsString();
            if (ids.containsKey(text)) return new com.google.gson.JsonPrimitive(ids.get(text));
            if (!oldPrefix.isEmpty() && (text.equals(oldPrefix) || text.startsWith(oldPrefix + "."))) {
                return new com.google.gson.JsonPrimitive(newPrefix + text.substring(oldPrefix.length()));
            }
            return value.deepCopy();
        }
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            value.getAsJsonArray().forEach(child -> result.add(rewriteReferences(child, ids, oldPrefix, newPrefix)));
            return result;
        }
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            for (var entry : value.getAsJsonObject().entrySet()) {
                String key = entry.getKey();
                if (!oldPrefix.isEmpty() && key.startsWith(oldPrefix + ".")) key = newPrefix + key.substring(oldPrefix.length());
                result.add(key, rewriteReferences(entry.getValue(), ids, oldPrefix, newPrefix));
            }
            return result;
        }
        return value.deepCopy();
    }

    public int importPack(Path pack) throws IOException {
        Map<String, byte[]> incoming = new LinkedHashMap<>();
        Path source = pack.toAbsolutePath().normalize();
        rejectSymlinks(source);
        if (Files.isDirectory(source)) {
            try (var files = Files.walk(source)) {
                for (Path file : files.filter(path -> !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)).sorted().toList()) {
                    rejectSymlinks(file);
                    String path = safeRelativePath(source.relativize(file).toString().replace('\\', '/'));
                    if (!importable(path)) continue;
                    if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("Pack file is too large: " + path);
                    incoming.put(path, Files.readAllBytes(file));
                    checkPackSize(incoming);
                }
            }
        } else {
            try (ZipFile zip = new ZipFile(source.toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (entry.isDirectory()) continue;
                    String path = safeRelativePath(entry.getName());
                    if (!importable(path)) continue;
                    if (incoming.containsKey(path)) throw new IOException("Duplicate archive path " + path);
                    try (var input = zip.getInputStream(entry)) {
                        byte[] bytes = input.readNBytes((int) MAX_FILE_BYTES + 1);
                        if (bytes.length > MAX_FILE_BYTES) throw new IOException("Pack file is too large: " + path);
                        incoming.put(path, bytes);
                        checkPackSize(incoming);
                    }
                }
            }
        }
        List<AuthoringDocument> imported = new ArrayList<>();
        Map<String, byte[]> binaries = new LinkedHashMap<>();
        JsonObject importedPack = null;
        for (var entry : incoming.entrySet()) {
            String path = entry.getKey();
            if (!path.equals("pack.mcmeta") && containsPath(path)) throw new IOException("Import would overwrite " + path);
            if (path.equals("pack.mcmeta") || (path.startsWith("data/") && path.endsWith(".json"))) {
                try {
                    JsonElement json = JsonParser.parseString(new String(entry.getValue(), StandardCharsets.UTF_8));
                    if (path.equals("pack.mcmeta")) importedPack = json.getAsJsonObject();
                    else if (json.isJsonObject()) imported.add(new AuthoringDocument(DefinitionKind.fromPath(path), path, json.getAsJsonObject()));
                    else if (DefinitionKind.fromPath(path) != DefinitionKind.RESOURCE) {
                        throw new IllegalArgumentException("Dialogue and quest definitions require an object at the JSON root");
                    } else binaries.put(path, entry.getValue());
                } catch (com.google.gson.JsonParseException | IllegalArgumentException | IllegalStateException ex) {
                    throw new IOException("Invalid JSON in " + path + ": " + ex.getMessage(), ex);
                }
            } else binaries.put(path, entry.getValue());
        }
        java.util.HashSet<String> paths = new java.util.HashSet<>();
        for (String path : incoming.keySet()) {
            if (!paths.add(path.toLowerCase(java.util.Locale.ROOT))) throw new IOException("Case-colliding pack path " + path);
        }
        imported.forEach(this::add);
        attachments.putAll(binaries);
        if (importedPack != null && documents.size() == imported.size()) packMetadata = importedPack;
        return imported.size();
    }

    private static boolean importable(String path) {
        return path.equals("pack.mcmeta") || path.equals("pack.png") || path.startsWith("data/") || path.startsWith("assets/");
    }

    private static void checkPackSize(Map<String, byte[]> files) throws IOException {
        if (files.values().stream().mapToLong(bytes -> bytes.length).sum() > MAX_PACK_BYTES) throw new IOException("Pack exceeds the 256 MiB authoring import limit");
    }

    public List<AuthoringDiagnostic> validate() {
        return AuthoringValidation.validate(documents());
    }

    public record TextLocation(AuthoringDocument document, String pointer) { }

    public TextLocation textLocation(AuthoringDocument owner, String key, String locale) {
        String bundle = bundleDirectory(owner.resourcePath());
        if (key.startsWith("#")) {
            String prefix = documents.stream().filter(candidate -> candidate.resourcePath().equals(bundle + "quest.json"))
                    .map(candidate -> DialogueGraph.string(candidate.json(), "localization_prefix")).findFirst().orElse("");
            key = prefix + "." + key.substring(1);
        }
        String namespace = owner.resourcePath().split("/", 3)[1];
        String resolvedKey = key;
        for (String language : List.of(locale == null ? "en_us" : locale, "en_us")) {
            var candidates = documents.stream().filter(candidate -> candidate.resourcePath().endsWith("/locales/" + language + ".json"))
                    .sorted(Comparator.comparingInt((AuthoringDocument candidate) -> candidate.resourcePath().startsWith(bundle) && !bundle.isEmpty() ? 0
                            : candidate.resourcePath().startsWith("data/" + namespace + "/quests/_shared/") ? 1
                            : candidate.resourcePath().startsWith("data/" + namespace + "/") ? 2 : 3)
                            .thenComparing(AuthoringDocument::resourcePath)).toList();
            for (var candidate : candidates) {
                JsonObject root = candidate.json();
                JsonObject messages = root.has("messages") && root.get("messages").isJsonObject() ? root.getAsJsonObject("messages") : root;
                if (messages.has(resolvedKey)) return new TextLocation(candidate,
                        (messages == root ? "" : "/messages") + "/" + JsonPointers.escape(resolvedKey));
            }
        }
        return null;
    }

    public void addDialogueResponse(AuthoringDocument document, String nodeId, String responseId, String text, String targetId) {
        if (document.kind() != DefinitionKind.CONVERSATION && document.kind() != DefinitionKind.QUEST) {
            DialogueGraph.addResponse(document, nodeId, responseId, text, targetId);
            return;
        }
        String prefix = document.kind() == DefinitionKind.QUEST
                ? DialogueGraph.string(document.json(), "localization_prefix") : document.id();
        if (prefix.isBlank()) throw new IllegalArgumentException("Set the quest localization prefix before adding responses.");
        String key = prefix + "." + nodeId + ".response." + responseId;
        AuthoringDocument draft = new AuthoringDocument(document.kind(), document.resourcePath(), document.json());
        DialogueGraph.addResponse(draft, nodeId, responseId, key, targetId);
        commitWithMessage(document, draft.json(), key, text);
    }

    public void addDialogueLine(AuthoringDocument document, String nodeId) {
        var node = DialogueGraph.nodes(document).stream().filter(candidate -> candidate.id().equals(nodeId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Select a dialogue node first."));
        if (document.kind() == DefinitionKind.QUEST || document.kind() == DefinitionKind.RESOURCE)
            throw new IllegalArgumentException("Select a dialogue definition.");
        JsonObject root = document.json();
        JsonObject selected = JsonPointers.get(root, node.pointer()).getAsJsonObject();
        String key = "";
        if (document.kind() == DefinitionKind.CONVERSATION || selected.has("exchanges")) {
            JsonArray exchanges = selected.has("exchanges") ? selected.getAsJsonArray("exchanges") : new JsonArray();
            String id = "line_" + (exchanges.size() + 1);
            while (containsId(exchanges, id)) id += "_new";
            JsonObject line = JsonParser.parseString("{\"speaker\":{\"type\":\"role\",\"role\":\"provider\"}}").getAsJsonObject();
            line.addProperty("id", id);
            if (document.kind() == DefinitionKind.CONVERSATION) {
                key = document.id() + "." + nodeId + "." + id;
                line.add("text", localized(key));
            } else line.addProperty("text", "Hello.");
            exchanges.add(line);
            selected.add("exchanges", exchanges);
        } else {
            String field = selected.has("variants") ? "variants" : "lines";
            if (!selected.has(field)) {
                JsonArray lines = new JsonArray();
                if (selected.has("line")) lines.add(selected.remove("line"));
                selected.add(field, lines);
            }
            selected.getAsJsonArray(field).add("Hello.");
        }
        if (key.isEmpty()) document.replaceJson(AuthoringDocument.JSON.toJson(root));
        else commitWithMessage(document, root, key, "Hello.");
    }

    private static boolean containsId(JsonArray values, String id) {
        for (JsonElement value : values)
            if (value.isJsonObject() && id.equals(DialogueGraph.string(value.getAsJsonObject(), "id"))) return true;
        return false;
    }

    private void commitWithMessage(AuthoringDocument document, JsonObject changed, String key, String text) {
        if (!documents.contains(document)) throw new IllegalArgumentException("Select a document in this workspace.");
        String localePath = bundleDirectory(document.resourcePath()) + "locales/en_us.json";
        AuthoringDocument locale = documents.stream().filter(candidate -> candidate.resourcePath().equals(localePath)).findFirst().orElse(null);
        if (locale == null && containsPath(localePath)) throw new IllegalArgumentException("Locale is not editable: " + localePath);
        if (locale == null) locale = new AuthoringDocument(DefinitionKind.RESOURCE, localePath, new JsonObject());
        JsonObject messages = locale.json();
        if (messages.has("messages")) messages = messages.getAsJsonObject("messages");
        if (messages.has(key)) throw new IllegalArgumentException("A translation already uses " + key + ". Choose another ID.");
        if (text.isBlank()) throw new IllegalArgumentException("Enter some dialogue text.");
        if (!documents.contains(locale)) add(locale);
        document.replaceJson(AuthoringDocument.JSON.toJson(changed));
        locale.edit("Add dialogue wording", root -> {
            JsonObject values = root.has("messages") ? root.getAsJsonObject("messages") : root;
            values.addProperty(key, text);
        });
        AuthoringDocument.groupLatestChanges(List.of(document, locale), () -> { }, () -> { });
    }

    public String resolveText(AuthoringDocument owner, JsonElement value, String locale) {
        return resolveText(owner, value, locale, 0, ignored -> true);
    }

    public String resolveText(AuthoringDocument owner, JsonElement value, String locale, int variant,
            java.util.function.Predicate<JsonObject> eligible) {
        return resolveText(owner, value, locale, variant, eligible, new java.util.HashSet<>());
    }

    private String resolveText(AuthoringDocument owner, JsonElement value, String locale, int variant,
            java.util.function.Predicate<JsonObject> eligible, java.util.Set<String> visited) {
        if (value == null || value.isJsonNull()) return "";
        if (value.isJsonPrimitive()) return value.getAsString();
        if (value.isJsonArray()) {
            List<JsonElement> choices = value.getAsJsonArray().asList().stream()
                    .filter(choice -> !choice.isJsonObject() || eligible.test(choice.getAsJsonObject().deepCopy())).toList();
            return choices.isEmpty() ? "" : resolveText(owner, choices.get(Math.floorMod(variant, choices.size())), locale, variant, eligible, visited);
        }
        JsonObject reference = value.getAsJsonObject();
        for (String field : List.of("text", "line", "lines", "variants")) {
            if (reference.has(field)) return resolveText(owner, reference.get(field), locale, variant, eligible, visited);
        }
        String key = DialogueGraph.string(reference, "key");
        String bundle = bundleDirectory(owner.resourcePath());
        if (key.startsWith("#")) {
            String questPath = bundle + "quest.json";
            String prefix = documents.stream().filter(document -> document.resourcePath().equals(questPath))
                    .map(document -> DialogueGraph.string(document.json(), "localization_prefix")).findFirst().orElse("");
            key = prefix + "." + key.substring(1);
        }
        if (key.isBlank()) return value.toString();
        if (!visited.add(key)) return key;
        String namespace = owner.resourcePath().startsWith("data/") ? owner.resourcePath().split("/", 3)[1] : "";
        List<AuthoringDocument> candidates = documents.stream().sorted(Comparator
                .comparingInt((AuthoringDocument document) -> !bundle.isEmpty() && document.resourcePath().startsWith(bundle) ? 0
                        : document.resourcePath().startsWith("data/" + namespace + "/quests/_shared/") ? 1
                        : document.resourcePath().startsWith("data/" + namespace + "/") ? 2 : 3)
                .thenComparing(AuthoringDocument::resourcePath)).toList();
        for (String language : List.of(locale == null ? "en_us" : locale, "en_us")) {
            for (AuthoringDocument document : candidates) {
                JsonObject root = document.json();
                if (document.resourcePath().endsWith("/locales/" + language + ".json")) {
                    JsonElement payload = QuestLocaleCatalog.read(root).messages().get(key);
                    if (payload != null) return resolveText(owner, payload, language, variant, eligible, visited);
                } else if (document.resourcePath().contains("/dialogue/" + language + "/")) {
                    List<JsonObject> messages = new ArrayList<>();
                    for (JsonElement message : DialogueGraph.array(root, "messages")) {
                        if (!message.isJsonObject()) continue;
                        JsonObject entry = message.getAsJsonObject();
                        if (key.equals(DialogueGraph.string(entry, "key")) && !isRemoval(entry) && eligible.test(entry.deepCopy())) messages.add(entry);
                    }
                    if (!messages.isEmpty()) {
                        return resolveText(owner, messagePayload(messages.get(Math.floorMod(variant, messages.size()))), language, variant, eligible, visited);
                    }
                    if (document.resourcePath().contains("/messages/") && key.equals(DialogueGraph.string(root, "key"))
                            && !isRemoval(root) && eligible.test(root.deepCopy())) {
                        return resolveText(owner, messagePayload(root), language, variant, eligible, visited);
                    }
                }
            }
        }
        return key;
    }

    private static JsonElement messagePayload(JsonObject message) {
        for (String key : List.of("variants", "lines", "line", "text")) if (message.has(key)) return message.get(key);
        return new com.google.gson.JsonPrimitive(DialogueGraph.string(message, "key"));
    }

    private static boolean isRemoval(JsonObject object) {
        return object.has("remove") && object.get("remove").isJsonPrimitive() && object.get("remove").getAsJsonPrimitive().isBoolean()
                && object.get("remove").getAsBoolean();
    }

    private static String bundleDirectory(String path) {
        String[] parts = path.split("/");
        int index = parts.length > 4 && parts[2].equals("world_campaigns") ? 4 : 2;
        if (parts.length < index + 3 || !parts[0].equals("data") || !parts[index].equals("quests")) return "";
        return String.join("/", java.util.Arrays.copyOf(parts, parts[index + 1].equals("_shared") ? index + 2 : index + 3)) + "/";
    }

    private JsonObject snapshot() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray values = new JsonArray();
        for (AuthoringDocument document : documents) {
            values.add(documentState(document));
        }
        root.add("documents", values);
        JsonObject files = new JsonObject();
        attachments.forEach((path, bytes) -> files.addProperty(path, Base64.getEncoder().encodeToString(bytes)));
        root.add("attachments", files);
        root.add("pack", packMetadata.deepCopy());
        JsonArray trash = new JsonArray();
        deleted.forEach(value -> trash.add(value.deepCopy()));
        root.add("deleted", trash);
        return root;
    }

    private static JsonObject documentState(AuthoringDocument document) {
        JsonObject entry = new JsonObject();
        entry.addProperty("kind", document.kind().name());
        entry.addProperty("path", document.resourcePath());
        entry.add("definition", document.json());
        entry.add("editor", document.metadata());
        return entry;
    }

    private void markSaved() {
        documents.forEach(AuthoringDocument::markSaved);
        savedDocuments = List.copyOf(documents);
        savedAttachments = Map.copyOf(attachments);
        savedPack = packMetadata.deepCopy();
        savedDeleted = deleted.size();
        recovered = false;
    }

    public boolean dirty() {
        return recovered || !documents.equals(savedDocuments) || documents.stream().anyMatch(AuthoringDocument::dirty)
                || !attachments.equals(savedAttachments) || !packMetadata.equals(savedPack) || deleted.size() != savedDeleted;
    }

    public boolean recoveryAvailable() { return Files.isRegularFile(directory.resolve(RECOVERY), LinkOption.NOFOLLOW_LINKS); }
    public JsonObject recoveryBuffers() { return recoveryBuffers.deepCopy(); }

    public void saveRecovery(JsonObject buffers) throws IOException {
        JsonObject root = snapshot();
        root.add("recovery_buffers", buffers.deepCopy());
        atomicWrite(directory.resolve(RECOVERY), AuthoringDocument.JSON.toJson(root).getBytes(StandardCharsets.UTF_8));
    }

    public void discardRecovery() throws IOException {
        rejectSymlinks(directory.resolve(RECOVERY));
        Files.deleteIfExists(directory.resolve(RECOVERY));
    }

    public void save() throws IOException {
        atomicWrite(directory.resolve(FILE), AuthoringDocument.JSON.toJson(snapshot()).getBytes(StandardCharsets.UTF_8));
        markSaved();
        discardRecovery();
    }

    public void exportPack(Path destination) throws IOException {
        List<AuthoringDiagnostic> errors = validate().stream().filter(value -> value.severity() == AuthoringDiagnostic.Severity.ERROR).toList();
        if (!errors.isEmpty()) {
            AuthoringDiagnostic first = errors.getFirst();
            throw new IOException("Export blocked by " + errors.size() + " validation error(s): "
                    + first.resourcePath() + first.pointer() + ": " + first.message());
        }
        Path target = destination.toAbsolutePath().normalize();
        rejectSymlinks(target);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Export destination already exists. Choose a new directory");
        Files.createDirectories(target.getParent());
        Path staging = Files.createTempDirectory(target.getParent(), ".vr-export-");
        try {
            atomicWrite(staging.resolve("pack.mcmeta"), AuthoringDocument.JSON.toJson(packMetadata).getBytes(StandardCharsets.UTF_8));
            for (AuthoringDocument document : documents) {
                atomicWrite(staging.resolve(safeRelativePath(document.resourcePath())),
                        AuthoringDocument.JSON.toJson(document.json()).getBytes(StandardCharsets.UTF_8));
            }
            for (var attachment : attachments.entrySet()) atomicWrite(staging.resolve(safeRelativePath(attachment.getKey())), attachment.getValue());
            moveExportWithRetry(staging, target);
        } finally {
            if (Files.exists(staging)) {
                try (var files = Files.walk(staging)) {
                    for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                }
            }
        }
    }

    private static void moveExportWithRetry(Path staging, Path target) throws IOException {
        for (int attempt = 0; ; attempt++) {
            try {
                Files.move(staging, target);
                return;
            } catch (AccessDeniedException exception) {
                if (attempt == 4) throw exception;
                try {
                    Thread.sleep(25L << attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    IOException cancelled = new IOException("Interrupted while exporting datapack", interrupted);
                    cancelled.addSuppressed(exception);
                    throw cancelled;
                }
            }
        }
    }

    public static String safeRelativePath(String value) {
        if (value == null || value.isBlank() || value.startsWith("/") || value.contains("\\") || value.contains(":")
                || java.util.Arrays.stream(value.split("/", -1)).anyMatch(part -> part.isBlank() || part.equals(".") || part.equals("..")
                || part.endsWith(".") || part.endsWith(" ") || part.matches("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?")
                || part.matches(".*[<>\\\"|?*\\p{Cntrl}].*"))) {
            throw new IllegalArgumentException("Unsafe relative resource path: " + value);
        }
        return value;
    }

    private static void rejectSymlinks(Path path) throws IOException {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) throw new IOException("Authoring paths cannot traverse symbolic links: " + current);
        }
    }

    private static void atomicWrite(Path destination, byte[] content) throws IOException {
        rejectSymlinks(destination);
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), ".vr-draft-", ".tmp");
        try {
            Files.write(temporary, content);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

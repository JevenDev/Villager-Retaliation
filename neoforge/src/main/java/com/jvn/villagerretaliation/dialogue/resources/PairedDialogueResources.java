package com.jvn.villagerretaliation.dialogue.resources;

import com.google.gson.*;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.normal.PairedDialogueService;
import com.jvn.villagerretaliation.dialogue.normal.PairedDialogueService.Pair;
import java.util.*;
import net.minecraft.server.MinecraftServer;

/** Atomically replaces a locale snapshot; no options, actions or quests are loaded here. */
public final class PairedDialogueResources {
    private static MinecraftServer owner;
    private static final Map<String, List<Pair>> CACHE = new HashMap<>();
    private static final Set<String> KEYS = Set.of("id", "option", "request", "audience", "reputation", "familiarity", "branch", "player", "npc", "owners", "tokens", "group");
    private PairedDialogueResources() { }
    public static synchronized void clearCache() { owner = null; CACHE.clear(); }
    public static synchronized List<Pair> pairs(DialogueContext c) {
        var server = c.level().getServer();
        if (server != owner) { clearCache(); owner = server; }
        return CACHE.computeIfAbsent(c.locale(), locale -> {
            var pairs = new LinkedHashMap<String, Pair>();
            read(server, "en_us", pairs);
            if (!locale.equals("en_us")) read(server, locale, pairs);
            return List.copyOf(pairs.values());
        });
    }
    private static void read(MinecraftServer server, String locale, Map<String, Pair> result) {
        var localeIds = new HashSet<String>();
        for (var entry : new TreeMap<>(server.getResourceManager().listResources("paired_dialogue/" + locale, id -> id.getPath().endsWith(".json"))).entrySet()) {
            try (var reader = entry.getValue().openAsReader()) {
                for (Pair p : parse(JsonParser.parseReader(reader).getAsJsonObject())) {
                    if (!localeIds.add(p.id())) throw new IllegalArgumentException("Duplicate locale pair " + p.id());
                    result.put(p.id(), p);
                }
            } catch (Exception e) { throw new IllegalStateException("Invalid paired dialogue " + entry.getKey(), e); }
        }
    }
    public static List<Pair> parse(JsonObject root) {
        if (!root.keySet().equals(Set.of("version", "pairs")) || root.get("version").getAsInt() != 1) throw new IllegalArgumentException("Unknown paired dialogue version/fields");
        var rows = new ArrayList<Pair>();
        var ids = new HashSet<String>();
        for (var raw : root.getAsJsonArray("pairs")) {
            var row = raw.getAsJsonObject();
            if (!row.keySet().equals(KEYS)) throw new IllegalArgumentException("Unknown/missing paired dialogue fields");
            Pair p = new Gson().fromJson(row, Pair.class);
            if (!PairedDialogueService.safe(p.id()) || !ids.add(p.id()) || !PairedDialogueService.OPTIONS.contains(p.option())
                    || !Set.of("social-child", "guarded-events", "dynamic-results", "inspection-orders").contains(p.group())
                    || !Set.of("adult", "child", "either").contains(p.audience()) || !Set.of("first", "familiar", "either").contains(p.familiarity())) throw new IllegalArgumentException("Invalid pair " + p.id());
            if (p.branch()==null || p.owners()==null || p.tokens()==null || p.player()==null || p.npc()==null
                    || p.tokens().stream().anyMatch(t -> t==null || !t.matches("[a-z_]+"))
                    || new HashSet<>(p.tokens()).size()!=p.tokens().size()
                    || p.owners().stream().anyMatch(t -> !PairedDialogueService.safe(t))) throw new IllegalArgumentException("Invalid binding " + p.id());
            com.jvn.villagerretaliation.reputation.VillagerReputationLevel.valueOf(p.reputation().toUpperCase(Locale.ROOT));
            com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType.valueOf(p.request().toUpperCase(Locale.ROOT));
            var values = new HashMap<String, String>();
            p.tokens().forEach(t -> values.put(t, "literal"));
            if (PairedDialogueService.renderTemplate(p.player(), values).isEmpty() || PairedDialogueService.renderTemplate(p.npc(), values).isEmpty()) throw new IllegalArgumentException("Invalid pair template " + p.id());
            rows.add(p);
        }
        return List.copyOf(rows);
    }
}

package com.jvn.villagerretaliation.quest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public record QuestFactReceipt(UUID operation, UUID token, List<QuestScopeKey> scopes, ResourceLocation tag, String key, String value) {
    public QuestFactReceipt {
        require(operation != null && token != null && scopes != null && !scopes.isEmpty() && scopes.size() <= 1024, "Invalid campaign fact identity.");
        scopes = List.copyOf(scopes);
        var unique = new HashSet<String>();
        for (var scope : scopes) require(scope != null
                && Set.of(QuestScopeKey.Kind.WORLD, QuestScopeKey.Kind.PLAYER, QuestScopeKey.Kind.QUEST).contains(scope.kind())
                && QuestScopeKey.parse(scope.asString()).filter(scope::equals).isPresent() && unique.add(scope.asString()), "Invalid campaign fact scope.");
        require(key != null && value != null && (tag == null) != key.isBlank()
                && (key.isEmpty() || key.matches("[a-zA-Z0-9_.:-]{1,128}")) && value.length() <= 128, "Invalid campaign fact change.");
        require(tag == null || key.isEmpty() && value.isEmpty(), "A fact tag cannot also assign a variable.");
    }

    public CompoundTag save() {
        var saved = new CompoundTag();
        saved.putUUID("Operation", operation);
        saved.putUUID("Token", token);
        var scopeTags = new ListTag();
        scopes.forEach(scope -> scopeTags.add(StringTag.valueOf(scope.asString())));
        saved.put("Scopes", scopeTags);
        saved.putString("Tag", tag == null ? "" : tag.toString());
        saved.putString("Key", key);
        saved.putString("Value", value);
        return saved;
    }

    public static QuestFactReceipt load(CompoundTag saved) {
        require(saved.getAllKeys().equals(Set.of("Operation", "Token", "Scopes", "Tag", "Key", "Value"))
                && saved.hasUUID("Operation") && saved.hasUUID("Token")
                && saved.contains("Tag", Tag.TAG_STRING) && saved.contains("Key", Tag.TAG_STRING)
                && saved.contains("Value", Tag.TAG_STRING) && saved.get("Scopes") instanceof ListTag, "Invalid saved campaign fact.");
        var scopeTags = (ListTag) saved.get("Scopes");
        require(!scopeTags.isEmpty() && scopeTags.size() <= 1024 && scopeTags.getElementType() == Tag.TAG_STRING, "Invalid saved fact scopes.");
        var scopes = scopeTags.stream().map(raw -> QuestScopeKey.parse(raw.getAsString())
                .filter(scope -> scope.asString().equals(raw.getAsString()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid saved fact scope."))).toList();
        String tagId = saved.getString("Tag");
        var tag = tagId.isEmpty() ? null : ResourceLocation.tryParse(tagId);
        require(tagId.isEmpty() || tag != null, "Invalid saved fact tag.");
        return new QuestFactReceipt(saved.getUUID("Operation"), saved.getUUID("Token"), scopes, tag, saved.getString("Key"), saved.getString("Value"));
    }

    static void require(boolean valid, String diagnostic) {
        if (!valid) throw new IllegalArgumentException(diagnostic);
    }
}

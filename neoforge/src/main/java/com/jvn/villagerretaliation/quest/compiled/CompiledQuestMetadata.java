package com.jvn.villagerretaliation.quest.compiled;

import com.jvn.villagerretaliation.dialogue.DialogueEntryMetadata;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public record CompiledQuestMetadata(
        String title,
        String description,
        String titleKey,
        String descriptionKey,
        String questline,
        Set<String> tags,
        ResourceLocation parent,
        DialogueEntryMetadata dialogue
) {
    public CompiledQuestMetadata {
        title = title == null ? "" : title;
        description = description == null ? "" : description;
        titleKey = titleKey == null ? "" : titleKey;
        descriptionKey = descriptionKey == null ? "" : descriptionKey;
        questline = questline == null ? "" : questline;
        tags = tags == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(tags));
        dialogue = dialogue == null ? DialogueEntryMetadata.EMPTY : dialogue;
    }
}

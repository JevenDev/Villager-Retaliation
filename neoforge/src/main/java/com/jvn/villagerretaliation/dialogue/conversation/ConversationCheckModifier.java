package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

public record ConversationCheckModifier(String id, String labelKey, String label, int amount, boolean applied) {
    public static final int MAX_COUNT = 16;
    public static final int MAX_AMOUNT = 20;

    public ConversationCheckModifier {
        if (id == null || id.length() > 256 || !id.matches("[a-z0-9][a-z0-9_./-]*")
                || labelKey == null || labelKey.isBlank() || label == null || label.isBlank()
                || amount < -MAX_AMOUNT || amount > MAX_AMOUNT)
            throw new IllegalArgumentException("Invalid saved conversation modifier");
    }

    static List<ConversationCheckModifier> capture(DialogueRuntimeContext context, ConversationDefinition.Check check) {
        var result = new ArrayList<ConversationCheckModifier>();
        for (var modifier : check.modifiers()) {
            boolean applied = true;
            for (var condition : modifier.conditions()) {
                String issue = condition.unavailableReason(context);
                ConversationService.require(issue.isBlank(), "Situational modifier " + modifier.id() + ": " + issue);
                if (!condition.matches(context)) applied = false;
            }
            result.add(new ConversationCheckModifier(modifier.id(), modifier.labelKey(),
                    ConversationService.text(context.player(), modifier.labelKey()), modifier.amount(), applied));
        }
        return List.copyOf(result);
    }

    public static int total(List<ConversationCheckModifier> modifiers) {
        if (modifiers.size() > MAX_COUNT) throw new IllegalArgumentException("Too many conversation modifiers");
        var ids = new HashSet<String>();
        int total = 0;
        for (var modifier : modifiers) {
            if (!ids.add(modifier.id())) throw new IllegalArgumentException("Duplicate conversation modifier");
            if (modifier.applied()) total += modifier.amount();
        }
        return total;
    }

    static ListTag save(List<ConversationCheckModifier> modifiers) {
        var list = new ListTag();
        for (var modifier : modifiers) {
            var tag = new CompoundTag();
            tag.putString("Id", modifier.id());
            tag.putString("LabelKey", modifier.labelKey());
            tag.putString("Label", modifier.label());
            tag.putInt("Amount", modifier.amount());
            tag.putBoolean("Applied", modifier.applied());
            list.add(tag);
        }
        return list;
    }

    static List<ConversationCheckModifier> load(CompoundTag check) {
        var result = new ArrayList<ConversationCheckModifier>();
        for (var raw : ConversationSessionCodec.list(check, "Modifiers")) {
            var tag = (CompoundTag) raw;
            result.add(new ConversationCheckModifier(ConversationSessionCodec.text(tag, "Id"),
                    ConversationSessionCodec.text(tag, "LabelKey"), ConversationSessionCodec.text(tag, "Label"),
                    ConversationSessionCodec.integer(tag, "Amount"), ConversationSessionCodec.bool(tag, "Applied")));
        }
        total(result);
        return List.copyOf(result);
    }
}

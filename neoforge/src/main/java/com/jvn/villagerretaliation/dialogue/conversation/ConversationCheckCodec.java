package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.skill.player.SocialCheckBreakdown;
import net.minecraft.nbt.CompoundTag;
import static com.jvn.villagerretaliation.dialogue.conversation.ConversationSessionCodec.*;

public final class ConversationCheckCodec {
    private ConversationCheckCodec() { }

    public static CompoundTag save(ConversationSession.Check check) {
        var value = new CompoundTag();
        value.putUUID("Request", check.request());
        value.putString("Id", check.id());
        value.putInt("Die", check.die());
        value.putInt("Difficulty", check.difficulty());
        value.putInt("Modifier", check.modifier());
        value.putInt("Total", check.total());
        value.putBoolean("Success", check.success());
        value.putString("Destination", check.destination());
        value.putUUID("Player", check.player());
        value.putLong("RolledAt", check.rolledAt());
        if (check.social() != null) value.put("Social", check.social().save());
        value.put("Modifiers", ConversationCheckModifier.save(check.modifiers()));
        return value;
    }

    public static ConversationSession.Check load(CompoundTag value) {
        return new ConversationSession.Check(uuid(value, "Request"), text(value, "Id"), integer(value, "Die"), integer(value, "Difficulty"),
                integer(value, "Modifier"), integer(value, "Total"), bool(value, "Success"), text(value, "Destination"), uuid(value, "Player"), number(value, "RolledAt"),
                value.contains("Social") ? SocialCheckBreakdown.load(compound(value, "Social")) : null, ConversationCheckModifier.load(value));
    }
}

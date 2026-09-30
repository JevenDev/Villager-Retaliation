package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.HiredEquipmentNeed;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public record SettlementEquipmentPlan(Configuration configuration, UUID authority, long revision, String blocker) {
    public static final int MAX_SELECTIONS = 16;
    public static final int MAX_SPARES = 4096;

    public SettlementEquipmentPlan {
        Objects.requireNonNull(configuration);
        Objects.requireNonNull(authority);
        if (revision < 0 || blocker == null || blocker.length() > 128)
            throw new IllegalArgumentException("Invalid equipment plan");
    }

    public boolean active() { return this.configuration.enabled() && this.blocker.isEmpty(); }

    boolean authorized(SettlementRecord town) {
        return active() && town.permits(this.authority, SettlementPermission.STAFF);
    }

    SettlementEquipmentPlan suspend() {
        return new SettlementEquipmentPlan(this.configuration, this.authority, Math.addExact(this.revision, 1), "policy_permission_changed");
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.put("Configuration", this.configuration.save());
        tag.putUUID("Authority", this.authority);
        tag.putLong("Revision", this.revision);
        tag.putString("Blocker", this.blocker);
        return tag;
    }

    static SettlementEquipmentPlan load(CompoundTag tag) {
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1
                || !tag.contains("Configuration", Tag.TAG_COMPOUND) || !tag.hasUUID("Authority")
                || !tag.contains("Revision", Tag.TAG_LONG) || !tag.contains("Blocker", Tag.TAG_STRING))
            throw new IllegalArgumentException("Invalid saved equipment plan");
        return new SettlementEquipmentPlan(Configuration.load(tag.getCompound("Configuration")), tag.getUUID("Authority"),
                tag.getLong("Revision"), tag.getString("Blocker"));
    }

    public record Configuration(List<Selection> selections, int priority, boolean enabled) {
        public Configuration {
            selections = List.copyOf(selections);
            var slots = new HashSet<net.minecraft.world.entity.EquipmentSlot>();
            if (selections.isEmpty() || selections.size() > MAX_SELECTIONS || priority < 0 || priority > 9
                    || selections.stream().map(Selection::kind).distinct().count() != selections.size()
                    || selections.stream().filter(Selection::equip).anyMatch(selection -> !slots.add(selection.kind().guardSlot())))
                throw new IllegalArgumentException("Invalid equipment selection or priority");
        }

        public static Configuration draft(List<HiredEquipmentNeed.Kind> kinds) {
            var selections = new ArrayList<Selection>();
            for (var kind : kinds) selections.add(new Selection(kind, kind == HiredEquipmentNeed.Kind.AMMUNITION ? 32 : 1,
                    kind.guardSlot() != null, false));
            if (kinds.stream().anyMatch(kind -> kind == HiredEquipmentNeed.Kind.BOW || kind == HiredEquipmentNeed.Kind.CROSSBOW)
                    && !kinds.contains(HiredEquipmentNeed.Kind.AMMUNITION))
                selections.add(new Selection(HiredEquipmentNeed.Kind.AMMUNITION, 32, false, false));
            return new Configuration(selections, 5, false);
        }

        public boolean validFor(HiredVillagerRole role) {
            return role != null && this.selections.stream().allMatch(selection -> selection.kind().reserveFor(role)
                    && (!selection.equip() || role == HiredVillagerRole.COMBAT));
        }

        public Selection selection(HiredEquipmentNeed.Kind kind) {
            return this.selections.stream().filter(selection -> selection.kind() == kind).findFirst().orElse(null);
        }

        public CompoundTag save() {
            var tag = new CompoundTag();
            tag.putInt("Version", 1);
            tag.putInt("Priority", this.priority);
            tag.putBoolean("Enabled", this.enabled);
            var selections = new ListTag();
            this.selections.forEach(selection -> {
                var entry = new CompoundTag();
                entry.putString("Kind", selection.kind().name());
                entry.putInt("Spares", selection.spares());
                entry.putBoolean("Equip", selection.equip());
                entry.putBoolean("Protected", selection.protectedMinimum());
                selections.add(entry);
            });
            tag.put("Selections", selections);
            return tag;
        }

        public static Configuration load(CompoundTag tag) {
            if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1 || !tag.contains("Priority", Tag.TAG_INT)
                    || !tag.contains("Enabled", Tag.TAG_BYTE) || !(tag.get("Selections") instanceof ListTag list)
                    || list.size() > MAX_SELECTIONS || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
                throw new IllegalArgumentException("Invalid saved equipment configuration");
            var selections = new ArrayList<Selection>();
            for (var raw : tag.getList("Selections", Tag.TAG_COMPOUND)) {
                var entry = (CompoundTag) raw;
                if (!entry.contains("Kind", Tag.TAG_STRING) || !entry.contains("Spares", Tag.TAG_INT)
                        || !entry.contains("Equip", Tag.TAG_BYTE) || !entry.contains("Protected", Tag.TAG_BYTE))
                    throw new IllegalArgumentException("Invalid saved equipment selection");
                selections.add(new Selection(HiredEquipmentNeed.Kind.valueOf(entry.getString("Kind")), entry.getInt("Spares"),
                        entry.getBoolean("Equip"), entry.getBoolean("Protected")));
            }
            return new Configuration(selections, tag.getInt("Priority"), tag.getBoolean("Enabled"));
        }
    }

    public record Selection(HiredEquipmentNeed.Kind kind, int spares, boolean equip, boolean protectedMinimum) {
        public Selection {
            Objects.requireNonNull(kind);
            if (!kind.reservable() || spares < 0 || spares > MAX_SPARES || !equip && spares == 0 || equip && kind.guardSlot() == null)
                throw new IllegalArgumentException("Invalid equipment reserve");
        }
    }
}

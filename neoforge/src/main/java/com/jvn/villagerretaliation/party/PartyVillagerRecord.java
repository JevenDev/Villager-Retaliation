package com.jvn.villagerretaliation.party;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public final class PartyVillagerRecord {
    private static final String TAG_VILLAGER = "Villager";
    private static final String TAG_RECRUITER = "Recruiter";
    private static final String TAG_CONTRACT = "Contract";
    private static final String TAG_ORDER = "Order";
    private static final String TAG_COMMAND = "Command";
    private static final String TAG_STAY_DIMENSION = "StayDimension";
    private static final String TAG_STAY_X = "StayX";
    private static final String TAG_STAY_Y = "StayY";
    private static final String TAG_STAY_Z = "StayZ";
    private static final String TAG_CONTRACT_START = "ContractStart";
    private static final String TAG_CONTRACT_END = "ContractEnd";
    private static final String TAG_DURATION_DAYS = "DurationDays";
    private static final String TAG_EMERALDS_PAID = "EmeraldsPaid";
    private static final String TAG_NAME = "Name";
    private static final String TAG_PROFESSION = "Profession";
    private static final String TAG_GENDER = "Gender";
    private static final String TAG_LAST_DIMENSION = "LastDimension";
    private static final String TAG_LAST_X = "LastX";
    private static final String TAG_LAST_Y = "LastY";
    private static final String TAG_LAST_Z = "LastZ";
    private static final String TAG_ATTACK_WITH_PARTY = "AttackWithParty";
    private static final String TAG_COMBAT_MODE = "PartyCombatMode";
    private static final String TAG_ATTACK_MODE = "AttackMode";
    private static final String TAG_KILL_ON_SIGHT = "KillOnSight";
    private static final String TAG_POLICY_OVERRIDES = "PolicyOverrides";
    private static final String TAG_COMBAT_MODE_OVERRIDE = "CombatModeOverride";
    private static final String TAG_ATTACK_MODE_OVERRIDE = "AttackModeOverride";
    private static final String TAG_DROP_COLLECTION = "DropCollection";
    private static final String TAG_QUICK_COMMANDS_ENABLED = "QuickCommandsEnabled";
    private static final String TAG_WEAPON_PREFERENCE = "WeaponPreference";
    private static final String TAG_WEAPONS_UNEQUIPPED = "WeaponsUnequipped";
    private static final String TAG_REGROUPING = "Regrouping";
    private static final String TAG_MOVE_TO_RETURN_COMMANDER = "MoveToReturnCommander";
    private static final String TAG_MOVE_TO_HOLDING = "MoveToHolding";

    private final UUID villagerId;
    private UUID recruiterId;
    private final UUID contractId;
    private final int recruitmentOrder;
    private PartyCommandMode commandMode;
    private ResourceLocation stayDimension;
    private BlockPos stayPosition;
    private long contractStartGameTime;
    private long contractEndGameTime;
    private int durationDays;
    private int emeraldsPaid;
    private String cachedName;
    private String cachedProfession;
    private String cachedGender = "";
    private ResourceLocation lastKnownDimension;
    private BlockPos lastKnownPosition;
    private PartyCombatMode combatModeOverride;
    private PartyAttackMode attackModeOverride;
    private PartyCombatMode partyCombatMode = PartyCombatMode.ATTACK_WITH_PARTY;
    private PartyAttackMode partyAttackMode = PartyAttackMode.ALL;
    private PartyDropCollectionMode dropCollectionMode = PartyDropCollectionMode.OFF;
    private boolean quickCommandsEnabled = true;
    private PartyWeaponPreference weaponPreference = PartyWeaponPreference.AUTO;
    private boolean weaponsUnequipped;
    private boolean regrouping;
    private UUID moveToReturnCommanderId;
    private boolean moveToHolding;

    PartyVillagerRecord(
            UUID villagerId,
            UUID recruiterId,
            UUID contractId,
            int recruitmentOrder,
            PartyCommandMode commandMode,
            ResourceLocation stayDimension,
            BlockPos stayPosition,
            long contractStartGameTime,
            long contractEndGameTime,
            int durationDays,
            int emeraldsPaid,
            String cachedName,
            String cachedProfession,
            ResourceLocation lastKnownDimension,
            BlockPos lastKnownPosition) {
        this.villagerId = villagerId;
        this.recruiterId = recruiterId;
        this.contractId = contractId;
        this.recruitmentOrder = Math.max(0, recruitmentOrder);
        this.commandMode = commandMode == null ? PartyCommandMode.FOLLOW : commandMode;
        this.stayDimension = stayDimension;
        this.stayPosition = stayPosition == null ? null : stayPosition.immutable();
        this.contractStartGameTime = Math.max(0L, contractStartGameTime);
        this.contractEndGameTime = Math.max(this.contractStartGameTime, contractEndGameTime);
        this.durationDays = Math.max(1, durationDays);
        this.emeraldsPaid = Math.max(0, emeraldsPaid);
        this.cachedName = safeText(cachedName, 128);
        this.cachedProfession = safeText(cachedProfession, 128);
        this.lastKnownDimension = lastKnownDimension;
        this.lastKnownPosition = lastKnownPosition == null ? null : lastKnownPosition.immutable();
    }

    public UUID villagerId() {
        return this.villagerId;
    }

    public UUID recruiterId() {
        return this.recruiterId;
    }

    void transferRecruiter(UUID recruiterId) {
        if (recruiterId != null) this.recruiterId = recruiterId;
    }

    public UUID contractId() {
        return this.contractId;
    }

    public int recruitmentOrder() {
        return this.recruitmentOrder;
    }

    public PartyCommandMode commandMode() {
        return this.commandMode;
    }

    public ResourceLocation stayDimension() {
        return this.stayDimension;
    }

    public BlockPos stayPosition() {
        return this.stayPosition;
    }

    public long contractStartGameTime() {
        return this.contractStartGameTime;
    }

    public long contractEndGameTime() {
        return this.contractEndGameTime;
    }

    public int emeraldsPaid() {
        return this.emeraldsPaid;
    }

    public String cachedName() {
        return this.cachedName;
    }

    public String cachedProfession() {
        return this.cachedProfession;
    }

    public String cachedGender() {
        return this.cachedGender;
    }

    void setCachedGender(String gender) {
        this.cachedGender = safeText(gender, 32);
    }

    public ResourceLocation lastKnownDimension() {
        return this.lastKnownDimension;
    }

    public BlockPos lastKnownPosition() {
        return this.lastKnownPosition;
    }

    public PartyCombatMode combatMode() {
        return this.combatModeOverride == null ? this.partyCombatMode : this.combatModeOverride;
    }

    public PartyAttackMode attackMode() {
        return this.attackModeOverride == null ? this.partyAttackMode : this.attackModeOverride;
    }

    public PartyDropCollectionMode dropCollectionMode() {
        return this.dropCollectionMode;
    }

    public boolean quickCommandsEnabled() {
        return this.quickCommandsEnabled;
    }

    public PartyWeaponPreference weaponPreference() {
        return this.weaponPreference;
    }

    public boolean weaponsUnequipped() {
        return this.weaponsUnequipped;
    }

    public boolean regrouping() {
        return this.regrouping;
    }

    public UUID moveToReturnCommanderId() {
        return this.moveToReturnCommanderId;
    }

    public boolean moveToHolding() {
        return this.moveToHolding;
    }

    void setCombatMode(PartyCombatMode mode) {
        PartyCombatMode resolved = mode == null ? this.partyCombatMode : mode;
        this.combatModeOverride = resolved == this.partyCombatMode ? null : resolved;
    }

    void setAttackMode(PartyAttackMode mode) {
        PartyAttackMode resolved = mode == null ? this.partyAttackMode : mode;
        this.attackModeOverride = resolved == this.partyAttackMode ? null : resolved;
    }

    void bindPartyPolicies(PartyCombatMode combatMode, PartyAttackMode attackMode) {
        this.partyCombatMode = combatMode == null ? PartyCombatMode.ATTACK_WITH_PARTY : combatMode;
        this.partyAttackMode = attackMode == null ? PartyAttackMode.ALL : attackMode;
        if (this.combatModeOverride == this.partyCombatMode) {
            this.combatModeOverride = null;
        }
        if (this.attackModeOverride == this.partyAttackMode) {
            this.attackModeOverride = null;
        }
    }

    void setDropCollectionMode(PartyDropCollectionMode mode) {
        this.dropCollectionMode = mode == null ? PartyDropCollectionMode.OFF : mode;
    }

    void setQuickCommandsEnabled(boolean enabled) {
        this.quickCommandsEnabled = enabled;
    }

    void setWeaponPreference(PartyWeaponPreference preference) {
        this.weaponPreference = preference == null ? PartyWeaponPreference.AUTO : preference;
    }

    void setWeaponsUnequipped(boolean weaponsUnequipped) {
        this.weaponsUnequipped = weaponsUnequipped;
    }

    void setRegrouping(boolean regrouping) {
        this.regrouping = regrouping;
    }

    public int remainingDays(long gameTime) {
        return com.jvn.villagerretaliation.interaction.VillagerContractTime.remainingDays(
                gameTime,
                this.contractEndGameTime);
    }

    public int availableExtensionDays(long gameTime, int requestedDays) {
        return com.jvn.villagerretaliation.interaction.VillagerContractTime.availableExtensionDays(
                gameTime,
                this.contractEndGameTime,
                requestedDays);
    }

    void setFollowing() {
        this.commandMode = PartyCommandMode.FOLLOW;
        this.stayDimension = null;
        this.stayPosition = null;
        this.regrouping = false;
        this.moveToReturnCommanderId = null;
        this.moveToHolding = false;
    }

    void setStaying(ResourceLocation dimension, BlockPos position) {
        this.commandMode = PartyCommandMode.STAY;
        this.stayDimension = dimension;
        this.stayPosition = position == null ? null : position.immutable();
        this.regrouping = false;
        this.moveToReturnCommanderId = null;
        this.moveToHolding = false;
    }

    void setMoveToReturnCommander(UUID commanderId) {
        this.moveToReturnCommanderId = commanderId;
        this.moveToHolding = false;
    }

    void setMoveToHolding(boolean holding) {
        this.moveToHolding = holding && this.moveToReturnCommanderId != null;
    }

    void clearMoveToReturnCommander() {
        this.moveToReturnCommanderId = null;
        this.moveToHolding = false;
    }

    void extend(long newEndGameTime, int additionalDays, int additionalEmeralds) {
        this.contractEndGameTime = Math.max(this.contractEndGameTime, newEndGameTime);
        this.durationDays += Math.max(0, additionalDays);
        this.emeraldsPaid += Math.max(0, additionalEmeralds);
    }

    boolean updateDisplay(
            String name,
            String profession,
            String gender,
            ResourceLocation dimension,
            BlockPos position) {
        String safeName = safeText(name, 128);
        String safeProfession = safeText(profession, 128);
        String safeGender = safeText(gender, 32);
        BlockPos immutablePosition = position == null ? null : position.immutable();
        boolean changed = !this.cachedName.equals(safeName)
                || !this.cachedProfession.equals(safeProfession)
                || !this.cachedGender.equals(safeGender)
                || !Objects.equals(this.lastKnownDimension, dimension)
                || !Objects.equals(this.lastKnownPosition, immutablePosition);
        this.cachedName = safeName;
        this.cachedProfession = safeProfession;
        this.cachedGender = safeGender;
        this.lastKnownDimension = dimension;
        this.lastKnownPosition = immutablePosition;
        return changed;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_VILLAGER, this.villagerId);
        tag.putUUID(TAG_RECRUITER, this.recruiterId);
        tag.putUUID(TAG_CONTRACT, this.contractId);
        tag.putInt(TAG_ORDER, this.recruitmentOrder);
        tag.putString(TAG_COMMAND, this.commandMode.name());
        if (this.stayDimension != null && this.stayPosition != null) {
            tag.putString(TAG_STAY_DIMENSION, this.stayDimension.toString());
            tag.putInt(TAG_STAY_X, this.stayPosition.getX());
            tag.putInt(TAG_STAY_Y, this.stayPosition.getY());
            tag.putInt(TAG_STAY_Z, this.stayPosition.getZ());
        }
        tag.putLong(TAG_CONTRACT_START, this.contractStartGameTime);
        tag.putLong(TAG_CONTRACT_END, this.contractEndGameTime);
        tag.putInt(TAG_DURATION_DAYS, this.durationDays);
        tag.putInt(TAG_EMERALDS_PAID, this.emeraldsPaid);
        tag.putString(TAG_NAME, this.cachedName);
        tag.putString(TAG_PROFESSION, this.cachedProfession);
        tag.putString(TAG_GENDER, this.cachedGender);
        if (this.lastKnownDimension != null) {
            tag.putString(TAG_LAST_DIMENSION, this.lastKnownDimension.toString());
        }
        if (this.lastKnownPosition != null) {
            tag.putInt(TAG_LAST_X, this.lastKnownPosition.getX());
            tag.putInt(TAG_LAST_Y, this.lastKnownPosition.getY());
            tag.putInt(TAG_LAST_Z, this.lastKnownPosition.getZ());
        }
        tag.putBoolean(TAG_POLICY_OVERRIDES, true);
        if (this.combatModeOverride != null) {
            tag.putString(TAG_COMBAT_MODE_OVERRIDE, this.combatModeOverride.name());
        }
        if (this.attackModeOverride != null) {
            tag.putString(TAG_ATTACK_MODE_OVERRIDE, this.attackModeOverride.name());
        }
        tag.putString(TAG_DROP_COLLECTION, this.dropCollectionMode.name());
        tag.putBoolean(TAG_QUICK_COMMANDS_ENABLED, this.quickCommandsEnabled);
        tag.putString(TAG_WEAPON_PREFERENCE, this.weaponPreference.name());
        tag.putBoolean(TAG_WEAPONS_UNEQUIPPED, this.weaponsUnequipped);
        tag.putBoolean(TAG_REGROUPING, this.regrouping);
        if (this.moveToReturnCommanderId != null) {
            tag.putUUID(TAG_MOVE_TO_RETURN_COMMANDER, this.moveToReturnCommanderId);
            tag.putBoolean(TAG_MOVE_TO_HOLDING, this.moveToHolding);
        }
        return tag;
    }

    static PartyVillagerRecord load(CompoundTag tag) {
        if (!tag.hasUUID(TAG_VILLAGER) || !tag.hasUUID(TAG_RECRUITER)) {
            return null;
        }
        UUID contractId = tag.hasUUID(TAG_CONTRACT) ? tag.getUUID(TAG_CONTRACT) : UUID.randomUUID();
        ResourceLocation stayDimension = ResourceLocation.tryParse(tag.getString(TAG_STAY_DIMENSION));
        BlockPos stayPosition = hasStayPosition(tag)
                ? new BlockPos(tag.getInt(TAG_STAY_X), tag.getInt(TAG_STAY_Y), tag.getInt(TAG_STAY_Z))
                : null;
        if (stayDimension == null || stayPosition == null) {
            stayDimension = null;
            stayPosition = null;
        }
        PartyVillagerRecord record = new PartyVillagerRecord(
                tag.getUUID(TAG_VILLAGER),
                tag.getUUID(TAG_RECRUITER),
                contractId,
                tag.getInt(TAG_ORDER),
                PartyCommandMode.byName(tag.getString(TAG_COMMAND)),
                stayDimension,
                stayPosition,
                tag.getLong(TAG_CONTRACT_START),
                tag.getLong(TAG_CONTRACT_END),
                Math.max(1, tag.getInt(TAG_DURATION_DAYS)),
                tag.getInt(TAG_EMERALDS_PAID),
                tag.getString(TAG_NAME),
                tag.getString(TAG_PROFESSION),
                ResourceLocation.tryParse(tag.getString(TAG_LAST_DIMENSION)),
                hasLastKnownPosition(tag)
                        ? new BlockPos(tag.getInt(TAG_LAST_X), tag.getInt(TAG_LAST_Y), tag.getInt(TAG_LAST_Z))
                        : null
        );
        if (tag.getBoolean(TAG_POLICY_OVERRIDES)) {
            if (tag.contains(TAG_COMBAT_MODE_OVERRIDE, Tag.TAG_STRING)) {
                record.combatModeOverride = PartyCombatMode.byName(tag.getString(TAG_COMBAT_MODE_OVERRIDE));
            }
            if (tag.contains(TAG_ATTACK_MODE_OVERRIDE, Tag.TAG_STRING)) {
                record.attackModeOverride = PartyAttackMode.byName(tag.getString(TAG_ATTACK_MODE_OVERRIDE));
            }
        } else {
            record.combatModeOverride = loadCombatMode(tag);
            record.attackModeOverride = PartyAttackMode.byName(tag.getString(TAG_ATTACK_MODE));
        }
        record.setDropCollectionMode(PartyDropCollectionMode.byName(tag.getString(TAG_DROP_COLLECTION)));
        record.setQuickCommandsEnabled(!tag.contains(TAG_QUICK_COMMANDS_ENABLED)
                || tag.getBoolean(TAG_QUICK_COMMANDS_ENABLED));
        record.setWeaponPreference(PartyWeaponPreference.byName(tag.getString(TAG_WEAPON_PREFERENCE)));
        record.setWeaponsUnequipped(tag.getBoolean(TAG_WEAPONS_UNEQUIPPED));
        record.setRegrouping(tag.getBoolean(TAG_REGROUPING));
        record.cachedGender = safeText(tag.getString(TAG_GENDER), 32);
        if (tag.hasUUID(TAG_MOVE_TO_RETURN_COMMANDER)) {
            record.setMoveToReturnCommander(tag.getUUID(TAG_MOVE_TO_RETURN_COMMANDER));
            record.setMoveToHolding(tag.getBoolean(TAG_MOVE_TO_HOLDING));
        }
        return record;
    }

    private static boolean hasStayPosition(CompoundTag tag) {
        return tag.contains(TAG_STAY_X, Tag.TAG_INT)
                && tag.contains(TAG_STAY_Y, Tag.TAG_INT)
                && tag.contains(TAG_STAY_Z, Tag.TAG_INT);
    }

    private static PartyCombatMode loadCombatMode(CompoundTag tag) {
        if (tag.contains(TAG_COMBAT_MODE, Tag.TAG_STRING)) {
            return PartyCombatMode.byName(tag.getString(TAG_COMBAT_MODE));
        }
        if (tag.getBoolean(TAG_KILL_ON_SIGHT)) {
            return PartyCombatMode.KILL_ON_SIGHT;
        }
        return !tag.contains(TAG_ATTACK_WITH_PARTY) || tag.getBoolean(TAG_ATTACK_WITH_PARTY)
                ? PartyCombatMode.ATTACK_WITH_PARTY
                : PartyCombatMode.SELF_DEFENSE;
    }

    private static boolean hasLastKnownPosition(CompoundTag tag) {
        return tag.contains(TAG_LAST_X, Tag.TAG_INT)
                && tag.contains(TAG_LAST_Y, Tag.TAG_INT)
                && tag.contains(TAG_LAST_Z, Tag.TAG_INT);
    }

    private static String safeText(String value, int maximumLength) {
        String safe = value == null ? "" : value;
        return safe.length() <= maximumLength ? safe : safe.substring(0, maximumLength);
    }
}

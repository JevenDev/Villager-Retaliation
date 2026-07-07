package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.dialogue.normal.DialogueDisposition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.social.VillagerFamilyTreeSnapshot;
import com.jvn.villagerretaliation.social.VillagerRelationshipSnapshot;
import com.jvn.villagerretaliation.social.VillagerRelationshipStage;
import com.jvn.villagerretaliation.villager.VillagerGender;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenVillagerInteractionPayload(
        int entityId,
        String villagerNameKey,
        String villagerNameFallback,
        String professionName,
        String genderName,
        boolean baby,
        int reputation,
        VillagerReputationLevel reputationLevel,
        DialogueDisposition mood,
        VillagerMood primaryMood,
        boolean followingPlayer,
        boolean stayingHere,
        boolean forcedDialogue,
        boolean clipboardMenu,
        boolean hiredByPlayer,
        boolean hiredByOtherPlayer,
        int hiredRemainingDays,
        int walletEmeralds,
        int maxWalletEmeralds,
        int lifetimeWalletEarned,
        int lifetimeWalletDeposited,
        String walletCurrencyName,
        String walletCurrencyPluralName,
        String walletCurrencyLabel,
        ResourceLocation walletCurrencyIconSprite,
        int walletCurrencyTextColor,
        boolean forceCameraTowardsVillager,
        List<HiredVillagerRole> availableHiredRoles,
        HiredVillagerRole activeHiredRole,
        boolean activeBrewingOrder,
        boolean activeBuilderTask,
        List<String> selectedLoggingFilters,
        boolean loggingStripLogs,
        boolean loggingHarvestLeaves,
        boolean loggingBonemealSaplings,
        boolean loggingPlantSaplings,
        boolean loggingPickUpDecayDrops,
        List<String> selectedAnimalBreedingTargets,
        List<DialogueOptionDefinition> dialogueOptions,
        List<String> knownLikedGiftNames,
        List<String> knownDislikedGiftNames,
        VillagerFamilyTreeSnapshot familyTree,
        VillagerRelationshipSnapshot relationships)
        implements CustomPacketPayload {
    private static final int MAX_HIRED_ROLES = 16;
    private static final int MAX_FAMILY_MEMBERS = 64;
    private static final int MAX_FAMILY_GENERATIONS = 16;
    private static final int MAX_ROMANTIC_BONDS = 32;
    private static final int VILLAGER_NAME_KEY_LENGTH = 256;
    private static final int VILLAGER_NAME_FALLBACK_LENGTH = 128;
    private static final int PROFESSION_NAME_LENGTH = 128;
    private static final int GENDER_NAME_LENGTH = 32;
    private static final int CURRENCY_LABEL_LENGTH = 64;
    public static final Type<OpenVillagerInteractionPayload> TYPE = VillagerPayloads.type("open_villager_interaction");
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenVillagerInteractionPayload> STREAM_CODEC =
            VillagerPayloads.codec(OpenVillagerInteractionPayload::encode, OpenVillagerInteractionPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, OpenVillagerInteractionPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUtf(payload.villagerNameKey(), VILLAGER_NAME_KEY_LENGTH);
        buffer.writeUtf(payload.villagerNameFallback(), VILLAGER_NAME_FALLBACK_LENGTH);
        buffer.writeUtf(payload.professionName(), PROFESSION_NAME_LENGTH);
        buffer.writeUtf(payload.genderName(), GENDER_NAME_LENGTH);
        buffer.writeBoolean(payload.baby());
        buffer.writeVarInt(payload.reputation());
        buffer.writeEnum(payload.reputationLevel());
        buffer.writeEnum(payload.mood());
        buffer.writeEnum(payload.primaryMood());
        buffer.writeBoolean(payload.followingPlayer());
        buffer.writeBoolean(payload.stayingHere());
        buffer.writeBoolean(payload.forcedDialogue());
        buffer.writeBoolean(payload.clipboardMenu());
        buffer.writeBoolean(payload.hiredByPlayer());
        buffer.writeBoolean(payload.hiredByOtherPlayer());
        buffer.writeVarInt(payload.hiredRemainingDays());
        buffer.writeVarInt(payload.walletEmeralds());
        buffer.writeVarInt(payload.maxWalletEmeralds());
        buffer.writeVarInt(payload.lifetimeWalletEarned());
        buffer.writeVarInt(payload.lifetimeWalletDeposited());
        buffer.writeUtf(payload.walletCurrencyName(), CURRENCY_LABEL_LENGTH);
        buffer.writeUtf(payload.walletCurrencyPluralName(), CURRENCY_LABEL_LENGTH);
        buffer.writeUtf(payload.walletCurrencyLabel(), CURRENCY_LABEL_LENGTH);
        buffer.writeResourceLocation(payload.walletCurrencyIconSprite());
        buffer.writeInt(payload.walletCurrencyTextColor());
        buffer.writeBoolean(payload.forceCameraTowardsVillager());
        writeHiredRoles(buffer, payload.availableHiredRoles());
        buffer.writeEnum(payload.activeHiredRole());
        buffer.writeBoolean(payload.activeBrewingOrder());
        buffer.writeBoolean(payload.activeBuilderTask());
        DialogueOptionPayloadCodec.writeStringList(buffer, payload.selectedLoggingFilters());
        buffer.writeBoolean(payload.loggingStripLogs());
        buffer.writeBoolean(payload.loggingHarvestLeaves());
        buffer.writeBoolean(payload.loggingBonemealSaplings());
        buffer.writeBoolean(payload.loggingPlantSaplings());
        buffer.writeBoolean(payload.loggingPickUpDecayDrops());
        DialogueOptionPayloadCodec.writeStringList(buffer, payload.selectedAnimalBreedingTargets());
        DialogueOptionPayloadCodec.writeDialogueOptions(buffer, payload.dialogueOptions());
        DialogueOptionPayloadCodec.writeStringList(buffer, payload.knownLikedGiftNames());
        DialogueOptionPayloadCodec.writeStringList(buffer, payload.knownDislikedGiftNames());
        writeFamilyTree(buffer, payload.familyTree());
        writeRelationships(buffer, payload.relationships());
    }

    private static OpenVillagerInteractionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new OpenVillagerInteractionPayload(
                buffer.readVarInt(),
                buffer.readUtf(VILLAGER_NAME_KEY_LENGTH),
                buffer.readUtf(VILLAGER_NAME_FALLBACK_LENGTH),
                buffer.readUtf(PROFESSION_NAME_LENGTH),
                buffer.readUtf(GENDER_NAME_LENGTH),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readEnum(VillagerReputationLevel.class),
                buffer.readEnum(DialogueDisposition.class),
                buffer.readEnum(VillagerMood.class),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readUtf(CURRENCY_LABEL_LENGTH),
                buffer.readUtf(CURRENCY_LABEL_LENGTH),
                buffer.readUtf(CURRENCY_LABEL_LENGTH),
                buffer.readResourceLocation(),
                buffer.readInt(),
                buffer.readBoolean(),
                readHiredRoles(buffer),
                buffer.readEnum(HiredVillagerRole.class),
                buffer.readBoolean(),
                buffer.readBoolean(),
                DialogueOptionPayloadCodec.readStringList(buffer),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                DialogueOptionPayloadCodec.readStringList(buffer),
                DialogueOptionPayloadCodec.readDialogueOptions(buffer),
                DialogueOptionPayloadCodec.readStringList(buffer),
                DialogueOptionPayloadCodec.readStringList(buffer),
                readFamilyTree(buffer),
                readRelationships(buffer)
        );
    }

    private static void writeHiredRoles(RegistryFriendlyByteBuf buffer, List<HiredVillagerRole> roles) {
        int size = Math.min(roles.size(), MAX_HIRED_ROLES);
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            buffer.writeEnum(roles.get(i));
        }
    }

    private static List<HiredVillagerRole> readHiredRoles(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, MAX_HIRED_ROLES, "hired roles");
        List<HiredVillagerRole> roles = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            roles.add(buffer.readEnum(HiredVillagerRole.class));
        }
        return roles;
    }

    private static void writeFamilyTree(RegistryFriendlyByteBuf buffer, VillagerFamilyTreeSnapshot familyTree) {
        VillagerFamilyTreeSnapshot safeFamilyTree = familyTree == null ? VillagerFamilyTreeSnapshot.EMPTY : familyTree;
        writeFamilyMembers(buffer, safeFamilyTree.parents());
        writeFamilyMembers(buffer, safeFamilyTree.birthParents());
        writeFamilyMembers(buffer, safeFamilyTree.adoptiveParents());
        writeFamilyMembers(buffer, safeFamilyTree.stepParents());
        writeFamilyMembers(buffer, safeFamilyTree.siblings());
        writeFamilyMembers(buffer, safeFamilyTree.spouses());
        writeFamilyMembers(buffer, safeFamilyTree.children());
        writeFamilyMembers(buffer, safeFamilyTree.auntsUncles());
        writeFamilyMembers(buffer, safeFamilyTree.cousins());
        writeFamilyMembers(buffer, safeFamilyTree.niecesNephews());
        writeFamilyMembers(buffer, safeFamilyTree.friends());
        writeFamilyMembers(buffer, safeFamilyTree.rivals());
        writeAncestry(buffer, safeFamilyTree.ancestry());
        writeDescendants(buffer, safeFamilyTree.descendants());
    }

    private static VillagerFamilyTreeSnapshot readFamilyTree(RegistryFriendlyByteBuf buffer) {
        return new VillagerFamilyTreeSnapshot(
                readFamilyMembers(buffer),
                readFamilyMembers(buffer),
                readFamilyMembers(buffer),
                readFamilyMembers(buffer),
                readFamilyMembers(buffer),
                readFamilyMembers(buffer),
                readFamilyMembers(buffer),
                readFamilyMembers(buffer),
                readFamilyMembers(buffer),
                readFamilyMembers(buffer),
                readFamilyMembers(buffer),
                readFamilyMembers(buffer),
                readAncestry(buffer),
                readDescendants(buffer)
        );
    }

    private static void writeFamilyMembers(RegistryFriendlyByteBuf buffer, List<VillagerFamilyTreeSnapshot.FamilyMember> members) {
        int size = Math.min(members.size(), MAX_FAMILY_MEMBERS);
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            VillagerFamilyTreeSnapshot.FamilyMember member = members.get(i);
            buffer.writeUtf(member.name(), 128);
            buffer.writeEnum(member.gender());
            buffer.writeBoolean(member.alive());
        }
    }

    private static List<VillagerFamilyTreeSnapshot.FamilyMember> readFamilyMembers(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, MAX_FAMILY_MEMBERS, "family members");
        List<VillagerFamilyTreeSnapshot.FamilyMember> members = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            members.add(new VillagerFamilyTreeSnapshot.FamilyMember(
                    buffer.readUtf(128),
                    buffer.readEnum(VillagerGender.class),
                    buffer.readBoolean()
            ));
        }
        return members;
    }

    private static void writeAncestry(RegistryFriendlyByteBuf buffer, List<VillagerFamilyTreeSnapshot.AncestorGeneration> ancestry) {
        int size = Math.min(ancestry.size(), MAX_FAMILY_GENERATIONS);
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            VillagerFamilyTreeSnapshot.AncestorGeneration generation = ancestry.get(i);
            buffer.writeVarInt(generation.generation());
            writeFamilyMembers(buffer, generation.ancestors());
        }
    }

    private static List<VillagerFamilyTreeSnapshot.AncestorGeneration> readAncestry(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, MAX_FAMILY_GENERATIONS, "ancestor generations");
        List<VillagerFamilyTreeSnapshot.AncestorGeneration> ancestry = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ancestry.add(new VillagerFamilyTreeSnapshot.AncestorGeneration(buffer.readVarInt(), readFamilyMembers(buffer)));
        }
        return ancestry;
    }

    private static void writeDescendants(RegistryFriendlyByteBuf buffer, List<VillagerFamilyTreeSnapshot.DescendantGeneration> descendants) {
        int size = Math.min(descendants.size(), MAX_FAMILY_GENERATIONS);
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            VillagerFamilyTreeSnapshot.DescendantGeneration generation = descendants.get(i);
            buffer.writeVarInt(generation.generation());
            writeFamilyMembers(buffer, generation.descendants());
        }
    }

    private static List<VillagerFamilyTreeSnapshot.DescendantGeneration> readDescendants(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, MAX_FAMILY_GENERATIONS, "descendant generations");
        List<VillagerFamilyTreeSnapshot.DescendantGeneration> descendants = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            descendants.add(new VillagerFamilyTreeSnapshot.DescendantGeneration(buffer.readVarInt(), readFamilyMembers(buffer)));
        }
        return descendants;
    }

    private static void writeRelationships(RegistryFriendlyByteBuf buffer, VillagerRelationshipSnapshot relationships) {
        VillagerRelationshipSnapshot safeRelationships = relationships == null ? VillagerRelationshipSnapshot.EMPTY : relationships;
        writeRomanticBondViews(buffer, safeRelationships.current());
        writeRomanticBondViews(buffer, safeRelationships.past());
    }

    private static VillagerRelationshipSnapshot readRelationships(RegistryFriendlyByteBuf buffer) {
        return new VillagerRelationshipSnapshot(readRomanticBondViews(buffer), readRomanticBondViews(buffer));
    }

    private static void writeRomanticBondViews(
            RegistryFriendlyByteBuf buffer,
            List<VillagerRelationshipSnapshot.RomanticBondView> bonds
    ) {
        int size = Math.min(bonds.size(), MAX_ROMANTIC_BONDS);
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            VillagerRelationshipSnapshot.RomanticBondView bond = bonds.get(i);
            buffer.writeUtf(bond.partnerName(), 128);
            buffer.writeBoolean(bond.partnerAlive());
            buffer.writeEnum(bond.stage());
            buffer.writeVarInt(bond.affection());
            buffer.writeVarInt(bond.compatibility());
            buffer.writeLong(bond.startedGameTime());
            buffer.writeLong(bond.stageSinceGameTime());
            buffer.writeLong(bond.endedGameTime());
            buffer.writeUtf(bond.endReason(), 128);
        }
    }

    private static List<VillagerRelationshipSnapshot.RomanticBondView> readRomanticBondViews(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, MAX_ROMANTIC_BONDS, "romantic bonds");
        List<VillagerRelationshipSnapshot.RomanticBondView> bonds = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            bonds.add(new VillagerRelationshipSnapshot.RomanticBondView(
                    buffer.readUtf(128),
                    buffer.readBoolean(),
                    buffer.readEnum(VillagerRelationshipStage.class),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readUtf(128)
            ));
        }
        return bonds;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.dialogue.DialogueDisposition;
import com.jvn.villagerretaliation.dialogue.DialogueEntryMetadata;
import com.jvn.villagerretaliation.dialogue.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueQuestAction;
import com.jvn.villagerretaliation.dialogue.DialogueRequestType;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.social.VillagerFamilyTreeSnapshot;
import com.jvn.villagerretaliation.social.VillagerRelationshipSnapshot;
import com.jvn.villagerretaliation.social.VillagerRelationshipStage;
import com.jvn.villagerretaliation.util.VillagerEquipmentCondition;
import com.jvn.villagerretaliation.util.VillagerPlayerItemCondition;
import com.jvn.villagerretaliation.util.VillagerReputationCondition;
import com.jvn.villagerretaliation.villager.VillagerGender;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
        boolean forcedDialogue,
        boolean forceCameraTowardsVillager,
        List<DialogueOptionDefinition> dialogueOptions,
        List<String> knownLikedGiftNames,
        List<String> knownDislikedGiftNames,
        VillagerFamilyTreeSnapshot familyTree,
        VillagerRelationshipSnapshot relationships)
        implements CustomPacketPayload {
    public static final Type<OpenVillagerInteractionPayload> TYPE = VillagerPayloads.type("open_villager_interaction");
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenVillagerInteractionPayload> STREAM_CODEC =
            VillagerPayloads.codec(OpenVillagerInteractionPayload::encode, OpenVillagerInteractionPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, OpenVillagerInteractionPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUtf(payload.villagerNameKey());
        buffer.writeUtf(payload.villagerNameFallback());
        buffer.writeUtf(payload.professionName());
        buffer.writeUtf(payload.genderName(), 32);
        buffer.writeBoolean(payload.baby());
        buffer.writeVarInt(payload.reputation());
        buffer.writeEnum(payload.reputationLevel());
        buffer.writeEnum(payload.mood());
        buffer.writeEnum(payload.primaryMood());
        buffer.writeBoolean(payload.followingPlayer());
        buffer.writeBoolean(payload.forcedDialogue());
        buffer.writeBoolean(payload.forceCameraTowardsVillager());
        writeDialogueOptions(buffer, payload.dialogueOptions());
        writeStringList(buffer, payload.knownLikedGiftNames());
        writeStringList(buffer, payload.knownDislikedGiftNames());
        writeFamilyTree(buffer, payload.familyTree());
        writeRelationships(buffer, payload.relationships());
    }

    private static OpenVillagerInteractionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new OpenVillagerInteractionPayload(
                buffer.readVarInt(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(32),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readEnum(VillagerReputationLevel.class),
                buffer.readEnum(DialogueDisposition.class),
                buffer.readEnum(VillagerMood.class),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                readDialogueOptions(buffer),
                readStringList(buffer),
                readStringList(buffer),
                readFamilyTree(buffer),
                readRelationships(buffer)
        );
    }

    private static void writeDialogueOptions(RegistryFriendlyByteBuf buffer, List<DialogueOptionDefinition> options) {
        buffer.writeVarInt(options.size());
        for (DialogueOptionDefinition option : options) {
            buffer.writeUtf(option.id(), 128);
            buffer.writeUtf(option.label(), 128);
            buffer.writeEnum(option.requestType());
            buffer.writeBoolean(option.forceCameraTowardsVillager());
            buffer.writeVarInt(option.order());
        }
    }

    private static List<DialogueOptionDefinition> readDialogueOptions(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<DialogueOptionDefinition> options = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            options.add(new DialogueOptionDefinition(
                    buffer.readUtf(128),
                    null,
                    DialogueEntryMetadata.EMPTY,
                    DialogueQuestAction.EMPTY,
                    buffer.readUtf(128),
                    buffer.readEnum(DialogueRequestType.class),
                    true,
                    true,
                    Set.of(),
                    Set.of(),
                    VillagerEquipmentCondition.empty(),
                    VillagerPlayerItemCondition.empty(),
                    VillagerReputationCondition.empty(),
                    com.jvn.villagerretaliation.dialogue.DialogueItemPayment.empty(),
                    buffer.readBoolean(),
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    List.of(),
                    false,
                    buffer.readVarInt()
            ));
        }
        return options;
    }

    private static void writeStringList(RegistryFriendlyByteBuf buffer, List<String> values) {
        buffer.writeVarInt(values.size());
        for (String value : values) {
            buffer.writeUtf(value, 128);
        }
    }

    private static List<String> readStringList(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buffer.readUtf(128));
        }
        return values;
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
        buffer.writeVarInt(members.size());
        for (VillagerFamilyTreeSnapshot.FamilyMember member : members) {
            buffer.writeUtf(member.name(), 128);
            buffer.writeEnum(member.gender());
            buffer.writeBoolean(member.alive());
        }
    }

    private static List<VillagerFamilyTreeSnapshot.FamilyMember> readFamilyMembers(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
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
        buffer.writeVarInt(ancestry.size());
        for (VillagerFamilyTreeSnapshot.AncestorGeneration generation : ancestry) {
            buffer.writeVarInt(generation.generation());
            writeFamilyMembers(buffer, generation.ancestors());
        }
    }

    private static List<VillagerFamilyTreeSnapshot.AncestorGeneration> readAncestry(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<VillagerFamilyTreeSnapshot.AncestorGeneration> ancestry = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ancestry.add(new VillagerFamilyTreeSnapshot.AncestorGeneration(buffer.readVarInt(), readFamilyMembers(buffer)));
        }
        return ancestry;
    }

    private static void writeDescendants(RegistryFriendlyByteBuf buffer, List<VillagerFamilyTreeSnapshot.DescendantGeneration> descendants) {
        buffer.writeVarInt(descendants.size());
        for (VillagerFamilyTreeSnapshot.DescendantGeneration generation : descendants) {
            buffer.writeVarInt(generation.generation());
            writeFamilyMembers(buffer, generation.descendants());
        }
    }

    private static List<VillagerFamilyTreeSnapshot.DescendantGeneration> readDescendants(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
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
        buffer.writeVarInt(bonds.size());
        for (VillagerRelationshipSnapshot.RomanticBondView bond : bonds) {
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
        int size = buffer.readVarInt();
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

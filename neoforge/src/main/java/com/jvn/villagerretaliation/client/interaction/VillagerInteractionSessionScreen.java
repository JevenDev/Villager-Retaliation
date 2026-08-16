package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.dialogue.normal.DialogueDisposition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import com.jvn.villagerretaliation.interaction.GiftPreferenceView;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.network.OpenVillagerDuelPayload;
import com.jvn.villagerretaliation.network.RecruitmentResultPayload;
import java.util.List;

interface VillagerInteractionSessionScreen {
    boolean matchesVillager(int entityId);

    void updateReputation(
            int reputation,
            VillagerReputationLevel reputationLevel,
            DialogueDisposition mood,
            VillagerMood primaryMood,
            boolean forceCameraTowardsVillager,
            List<DialogueOptionDefinition> dialogueOptions,
            List<GiftPreferenceView> giftPreferences);

    void updateDuelStatus(OpenVillagerDuelPayload payload);

    void acceptRecruitmentResult(RecruitmentResultPayload payload);

    void replaceFromServer();

    void closeFromServer();

    void acceptVillagerDialogue(String text, List<DialogueTextSegment> textSegments);

    void copyCurrentDialogueTo(VillagerInteractionScreen target);

    void prepareReplacementTransition(VillagerInteractionScreen target);
}

package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;

/**
 * Temporarily loads payment-box chunks while an auto-payment renewal is due.
 *
 * <p>A block entity cannot be loaded independently from its owning chunk. This uses a level-33
 * region ticket (distance zero), which makes only the target chunk FULL without requesting block
 * or entity ticking. The ticket is removed immediately after payment and also has a timeout as a
 * safety net.</p>
 */
public final class PaymentBoxChunkLoadingService {
    private static final int FULL_CHUNK_TICKET_DISTANCE = 0;
    private static final int TICKET_TIMEOUT_TICKS = 20 * 15;
    private static final int MAX_CHUNKS_PER_RENEWAL = 4;
    private static final int LOAD_BUDGET_WINDOW_TICKS = 20;
    private static final int MAX_CHUNKS_PER_SERVER_WINDOW = 4;
    private static final Map<MinecraftServer, LoadBudget> LOAD_BUDGETS = new WeakHashMap<>();
    private static final TicketType<UUID> PAYMENT_BOX_TICKET = TicketType.create(
            "villagerretaliation_payment_box",
            UUID::compareTo,
            TICKET_TIMEOUT_TICKS);

    private PaymentBoxChunkLoadingService() {
    }

    /**
     * Requests bounded, non-ticking chunk loads for this living villager's unloaded payment boxes.
     * Re-adding the same villager/chunk ticket is idempotent.
     */
    public static int requestLoads(ServerLevel level, Villager villager) {
        if (level == null
                || villager == null
                || !villager.isAlive()
                || !HiredVillagerContractService.hasContract(villager)) {
            return 0;
        }

        int requested = 0;
        for (ChunkPos chunkPos : assignedPaymentChunks(level, villager)) {
            if (requested >= MAX_CHUNKS_PER_RENEWAL) {
                break;
            }
            if (level.hasChunk(chunkPos.x, chunkPos.z)) {
                continue;
            }
            if (!claimServerLoadBudget(level)) {
                break;
            }
            level.getChunkSource().addRegionTicket(
                    PAYMENT_BOX_TICKET,
                    chunkPos,
                    FULL_CHUNK_TICKET_DISTANCE,
                    villager.getUUID(),
                    false);
            requested++;
        }
        return requested;
    }

    private static boolean claimServerLoadBudget(ServerLevel level) {
        long gameTime = level.getGameTime();
        LoadBudget budget = LOAD_BUDGETS.computeIfAbsent(
                level.getServer(),
                ignored -> new LoadBudget(gameTime));
        if (gameTime < budget.windowStartGameTime
                || gameTime - budget.windowStartGameTime >= LOAD_BUDGET_WINDOW_TICKS) {
            budget.windowStartGameTime = gameTime;
            budget.used = 0;
        }
        if (budget.used >= MAX_CHUNKS_PER_SERVER_WINDOW) {
            return false;
        }
        budget.used++;
        return true;
    }

    public static void releaseLoads(ServerLevel level, Villager villager) {
        if (level == null || villager == null) {
            return;
        }
        for (ChunkPos chunkPos : assignedPaymentChunks(level, villager)) {
            level.getChunkSource().removeRegionTicket(
                    PAYMENT_BOX_TICKET,
                    chunkPos,
                    FULL_CHUNK_TICKET_DISTANCE,
                    villager.getUUID(),
                    false);
        }
    }

    private static Set<ChunkPos> assignedPaymentChunks(ServerLevel level, Villager villager) {
        Optional<UUID> contractHirer = HiredVillagerContractService.currentContractHirer(villager);
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        for (AssignedContainerRecord record : AssignedStorageService.assignedPaymentStorage(level, villager)) {
            if (!record.dimension().equals(level.dimension())) {
                continue;
            }
            if (contractHirer.isPresent() && !contractHirer.get().equals(record.hirerId())) {
                continue;
            }
            chunks.add(new ChunkPos(record.pos()));
        }
        return chunks;
    }

    private static final class LoadBudget {
        private long windowStartGameTime;
        private int used;

        private LoadBudget(long windowStartGameTime) {
            this.windowStartGameTime = windowStartGameTime;
        }
    }
}

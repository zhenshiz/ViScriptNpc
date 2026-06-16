package com.viscript.npc.network.c2s;

import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacket;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import com.lowdragmc.lowdraglib2.Platform;
import com.viscript.npc.network.s2c.S2CPayload;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.NpcRegister;
import com.viscript.npc.npc.data.ai.NpcAI;
import com.viscript.npc.npc.data.ai.runtime.NpcBehaviorDataSerializers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class C2SPayload {
    public static final String CREATE_NEW_NPC = "createNewNpc";
    public static final String OVERWRITE_NPC = "overwriteNpc";
    public static final String SET_NPC_AI_DEBUG_PAUSED = "setNpcAiDebugPaused";
    public static final String CONTINUE_NPC_AI_DEBUG = "continueNpcAiDebug";
    public static final String STEP_NPC_AI_DEBUG = "stepNpcAiDebug";
    public static final String STOP_NPC_AI_DEBUG = "stopNpcAiDebug";
    public static final String REQUEST_NPC_AI_DEBUG_SNAPSHOT = "requestNpcAiDebugSnapshot";
    public static final String SYNC_NPC_AI_DEBUG_CONFIG = "syncNpcAiDebugConfig";
    public static final String SET_NPC_AI_DEBUG_IGNORED_PLAYER = "setNpcAiDebugIgnoredPlayer";
    public static final String BREAK_NPC_AI_DEBUG_WORLD_BLOCK = "breakNpcAiDebugWorldBlock";
    public static final String USE_NPC_AI_DEBUG_WORLD_BLOCK = "useNpcAiDebugWorldBlock";

    @RPCPacket(CREATE_NEW_NPC)
    public static void createNewNpc(RPCSender sender, CompoundTag tag) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            if (player == null) return;

            CustomNpc npc = NpcRegister.CUSTOM_NPC.get().create(player.level());
            if (npc != null) {
                npc.moveTo(player.position());
                npc.readAdditionalSaveData(tag);
                player.level().addFreshEntity(npc);
            }
        }
    }

    @RPCPacket(OVERWRITE_NPC)
    public static void overwriteNpc(RPCSender sender, int entityId, CompoundTag tag) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            if (player == null) return;
            Entity entity = ((ServerLevel) player.level()).getEntity(entityId);
            if (entity instanceof CustomNpc npc) {
                npc.readAdditionalSaveData(tag);
            }
        }
    }

    @RPCPacket(SET_NPC_AI_DEBUG_PAUSED)
    public static void setNpcAiDebugPaused(RPCSender sender, int entityId, boolean paused) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            CustomNpc npc = getDebugNpc(player, entityId);
            if (npc == null) return;
            if (paused) {
                npc.setNpcBehaviorDebugPaused(true);
            } else {
                npc.continueNpcBehaviorDebug();
            }
            sendNpcAiDebugSnapshot(player, entityId, npc);
        }
    }

    @RPCPacket(CONTINUE_NPC_AI_DEBUG)
    public static void continueNpcAiDebug(RPCSender sender, int entityId) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            CustomNpc npc = getDebugNpc(player, entityId);
            if (npc == null) return;
            npc.continueNpcBehaviorDebug();
            sendNpcAiDebugSnapshot(player, entityId, npc);
        }
    }

    @RPCPacket(STEP_NPC_AI_DEBUG)
    public static void stepNpcAiDebug(RPCSender sender, int entityId) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            CustomNpc npc = getDebugNpc(player, entityId);
            if (npc == null) return;
            npc.stepNpcBehaviorDebug();
            sendNpcAiDebugSnapshot(player, entityId, npc);
        }
    }

    @RPCPacket(STOP_NPC_AI_DEBUG)
    public static void stopNpcAiDebug(RPCSender sender, int entityId) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            CustomNpc npc = getDebugNpc(player, entityId);
            if (npc == null) return;
            npc.stopNpcBehaviorDebug();
            sendNpcAiDebugSnapshot(player, entityId, npc);
        }
    }

    @RPCPacket(REQUEST_NPC_AI_DEBUG_SNAPSHOT)
    public static void requestNpcAiDebugSnapshot(RPCSender sender, int entityId) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            CustomNpc npc = getDebugNpc(player, entityId);
            if (npc == null) return;
            sendNpcAiDebugSnapshot(player, entityId, npc);
        }
    }

    @RPCPacket(SYNC_NPC_AI_DEBUG_CONFIG)
    public static void syncNpcAiDebugConfig(RPCSender sender, int entityId, CompoundTag aiTag) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            CustomNpc npc = getDebugNpc(player, entityId);
            if (npc == null || aiTag == null || aiTag.isEmpty()) return;
            NpcAI ai = npc.getNpcAI();
            if (ai == null) return;
            ai.deserializeNBT(Platform.getFrozenRegistry(), aiTag);
            ai.setBehaviorProgram(ai.compileBehaviorProgram(Platform.getFrozenRegistry()));
            npc.updateNpcState();
            sendNpcAiDebugSnapshot(player, entityId, npc);
        }
    }

    @RPCPacket(SET_NPC_AI_DEBUG_IGNORED_PLAYER)
    public static void setNpcAiDebugIgnoredPlayer(RPCSender sender, int entityId, boolean ignored) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            CustomNpc npc = getDebugNpc(player, entityId);
            if (player == null || npc == null) return;
            if (ignored) {
                npc.setNpcBehaviorEditorDebugIgnoredPlayer(player.getUUID());
            } else {
                npc.clearNpcBehaviorEditorDebugIgnoredPlayer(player.getUUID());
            }
        }
    }

    @RPCPacket(BREAK_NPC_AI_DEBUG_WORLD_BLOCK)
    public static void breakNpcAiDebugWorldBlock(RPCSender sender, int entityId, int x, int y, int z,
                                                 int directionOrdinal, double hitX, double hitY, double hitZ) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            if (player == null || getDebugNpc(player, entityId) == null) return;
            BlockPos pos = new BlockPos(x, y, z);
            if (!player.level().isLoaded(pos)) return;
            player.gameMode.destroyBlock(pos);
        }
    }

    @RPCPacket(USE_NPC_AI_DEBUG_WORLD_BLOCK)
    public static void useNpcAiDebugWorldBlock(RPCSender sender, int entityId, int x, int y, int z,
                                               int directionOrdinal, double hitX, double hitY, double hitZ) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            if (player == null || getDebugNpc(player, entityId) == null) return;
            BlockPos pos = new BlockPos(x, y, z);
            if (!player.level().isLoaded(pos)) return;
            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) return;
            Direction direction = directionFromOrdinal(directionOrdinal);
            BlockHitResult hit = new BlockHitResult(new Vec3(hitX, hitY, hitZ), direction, pos, false);
            player.gameMode.useItemOn(player, player.level(), stack, InteractionHand.MAIN_HAND, hit);
        }
    }

    private static Direction directionFromOrdinal(int ordinal) {
        Direction[] values = Direction.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : Direction.UP;
    }

    private static CustomNpc getDebugNpc(ServerPlayer player, int entityId) {
        if (player == null || entityId < 0) return null;
        Entity entity = ((ServerLevel) player.level()).getEntity(entityId);
        return entity instanceof CustomNpc npc ? npc : null;
    }

    private static void sendNpcAiDebugSnapshot(ServerPlayer player, int entityId, CustomNpc npc) {
        NpcBehaviorDataSerializers.register();
        CompoundTag payload = new CompoundTag();
        payload.putInt("entityId", entityId);
        payload.putBoolean("paused", npc.isNpcBehaviorDebugPaused());
        payload.putString("npcType", npc.getNpcType());
        payload.put("snapshot", npc.getNpcBehaviorDebugSnapshot().serializeNBT(Platform.getFrozenRegistry()));
        RPCPacketDistributor.rpcToPlayer(player, S2CPayload.SEND_NPC_AI_DEBUG_SNAPSHOT, payload);
    }
}

package com.viscript.npc.network.c2s;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacket;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import com.viscript.npc.compat.team.NpcFactionBridge;
import com.viscript.npc.network.s2c.S2CPayload;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.NpcRegister;
import com.viscript.npc.npc.data.ai.NpcAI;
import com.viscript.npc.npc.data.ai.runtime.NpcBehaviorDataSerializers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public class C2SPayload {
    public static final String CREATE_NEW_NPC = "createNewNpc";
    public static final String OVERWRITE_NPC = "overwriteNpc";
    public static final String BREAK_NPC_AI_WORLD_TEST_BLOCK = "breakNpcAiWorldTestBlock";
    public static final String USE_NPC_AI_WORLD_TEST_BLOCK = "useNpcAiWorldTestBlock";
    public static final String MOVE_NPC_AI_WORLD_TEST_MOB = "moveNpcAiWorldTestMob";
    public static final String REQUEST_NPC_AI_WORLD_TEST_PATH = "requestNpcAiWorldTestPath";
    public static final String SET_NPC_AI_DEBUG_PAUSED = "setNpcAiDebugPaused";
    public static final String CONTINUE_NPC_AI_DEBUG = "continueNpcAiDebug";
    public static final String STEP_NPC_AI_DEBUG = "stepNpcAiDebug";
    public static final String STOP_NPC_AI_DEBUG = "stopNpcAiDebug";
    public static final String REQUEST_NPC_AI_DEBUG_SNAPSHOT = "requestNpcAiDebugSnapshot";
    public static final String SYNC_NPC_AI_DEBUG_CONFIG = "syncNpcAiDebugConfig";
    public static final String SET_NPC_AI_DEBUG_IGNORED_PLAYER = "setNpcAiDebugIgnoredPlayer";
    public static final String REQUEST_FACTION_IDS = "requestFactionIds";

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

    @RPCPacket(BREAK_NPC_AI_WORLD_TEST_BLOCK)
    public static void breakNpcAiWorldTestBlock(RPCSender sender, int entityId, int x, int y, int z,
                                                 int directionOrdinal, double hitX, double hitY, double hitZ) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            if (player == null || getWorldTestNpc(player, entityId) == null) return;
            BlockPos pos = new BlockPos(x, y, z);
            if (!player.level().isLoaded(pos)) return;
            player.gameMode.destroyBlock(pos);
        }
    }

    @RPCPacket(USE_NPC_AI_WORLD_TEST_BLOCK)
    public static void useNpcAiWorldTestBlock(RPCSender sender, int entityId, int x, int y, int z,
                                               int directionOrdinal, double hitX, double hitY, double hitZ) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            if (player == null || getWorldTestNpc(player, entityId) == null) return;
            BlockPos pos = new BlockPos(x, y, z);
            if (!player.level().isLoaded(pos)) return;
            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) return;
            Direction direction = directionFromOrdinal(directionOrdinal);
            BlockHitResult hit = new BlockHitResult(new Vec3(hitX, hitY, hitZ), direction, pos, false);
            player.gameMode.useItemOn(player, player.level(), stack, InteractionHand.MAIN_HAND, hit);
        }
    }

    @RPCPacket(MOVE_NPC_AI_WORLD_TEST_MOB)
    public static void moveNpcAiWorldTestMob(RPCSender sender, int worldTestNpcId, int draggedEntityId,
                                              double x, double y, double z) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            CustomNpc worldTestNpc = getWorldTestNpc(player, worldTestNpcId);
            if (player == null || worldTestNpc == null || !player.getMainHandItem().isEmpty()) return;
            ServerLevel level = (ServerLevel) player.level();
            Entity entity = level.getEntity(draggedEntityId);
            if (!(entity instanceof Mob mob) || entity.isRemoved() || !entity.isAlive()) return;
            if (!entity.level().dimension().equals(level.dimension())) return;
            double maxDistance = 128.0D;
            if (worldTestNpc.distanceToSqr(x, y, z) > maxDistance * maxDistance
                    || worldTestNpc.distanceToSqr(entity) > maxDistance * maxDistance) {
                return;
            }
            int minY = level.getMinBuildHeight();
            int maxY = level.getMaxBuildHeight() - 1;
            double targetY = Mth.clamp(y, minY, maxY);
            BlockPos targetBlock = BlockPos.containing(x, targetY, z);
            if (!level.isLoaded(targetBlock)) return;
            mob.getNavigation().stop();
            mob.setDeltaMovement(Vec3.ZERO);
            mob.fallDistance = 0.0F;
            mob.teleportTo(x, targetY, z);
            mob.hasImpulse = true;
            mob.hurtMarked = true;
        }
    }

    @RPCPacket(REQUEST_NPC_AI_WORLD_TEST_PATH)
    public static void requestNpcAiWorldTestPath(RPCSender sender, int entityId) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            CustomNpc npc = getWorldTestNpc(player, entityId);
            if (player == null || npc == null) return;
            RPCPacketDistributor.rpcToPlayer(player, S2CPayload.SEND_NPC_AI_WORLD_TEST_PATH, serializePath(npc));
        }
    }

    @RPCPacket(SET_NPC_AI_DEBUG_PAUSED)
    public static void setNpcAiDebugPaused(RPCSender sender, int entityId, boolean paused) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            CustomNpc npc = getWorldTestNpc(player, entityId);
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
            CustomNpc npc = getWorldTestNpc(player, entityId);
            if (npc == null) return;
            npc.continueNpcBehaviorDebug();
            sendNpcAiDebugSnapshot(player, entityId, npc);
        }
    }

    @RPCPacket(STEP_NPC_AI_DEBUG)
    public static void stepNpcAiDebug(RPCSender sender, int entityId) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            CustomNpc npc = getWorldTestNpc(player, entityId);
            if (npc == null) return;
            npc.stepNpcBehaviorDebug();
            sendNpcAiDebugSnapshot(player, entityId, npc);
        }
    }

    @RPCPacket(STOP_NPC_AI_DEBUG)
    public static void stopNpcAiDebug(RPCSender sender, int entityId) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            CustomNpc npc = getWorldTestNpc(player, entityId);
            if (npc == null) return;
            npc.stopNpcBehaviorDebug();
            sendNpcAiDebugSnapshot(player, entityId, npc);
        }
    }

    @RPCPacket(REQUEST_NPC_AI_DEBUG_SNAPSHOT)
    public static void requestNpcAiDebugSnapshot(RPCSender sender, int entityId) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            CustomNpc npc = getWorldTestNpc(player, entityId);
            if (npc == null) return;
            sendNpcAiDebugSnapshot(player, entityId, npc);
        }
    }

    @RPCPacket(SYNC_NPC_AI_DEBUG_CONFIG)
    public static void syncNpcAiDebugConfig(RPCSender sender, int entityId, CompoundTag aiTag) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            CustomNpc npc = getWorldTestNpc(player, entityId);
            if (npc == null || aiTag == null || aiTag.isEmpty()) return;
            NpcAI ai = npc.getNpcAI();
            ai.deserializeNBT(Platform.getFrozenRegistry(), aiTag);
            ai.setBehaviorProgram(ai.getCompiledBehaviorProgram());
            npc.updateNpcState();
            sendNpcAiDebugSnapshot(player, entityId, npc);
        }
    }

    @RPCPacket(SET_NPC_AI_DEBUG_IGNORED_PLAYER)
    public static void setNpcAiDebugIgnoredPlayer(RPCSender sender, int entityId, boolean ignored) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            CustomNpc npc = getWorldTestNpc(player, entityId);
            if (player == null || npc == null) return;
            if (ignored) {
                npc.setNpcBehaviorEditorDebugIgnoredPlayer(player.getUUID());
            } else {
                npc.clearNpcBehaviorEditorDebugIgnoredPlayer(player.getUUID());
            }
            sendNpcAiDebugSnapshot(player, entityId, npc);
        }
    }

    @RPCPacket(REQUEST_FACTION_IDS)
    public static void requestFactionIds(RPCSender sender) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            if (player == null) return;
            Set<String> factionIds = NpcFactionBridge.getFactionIds(player.serverLevel());
            RPCPacketDistributor.rpcToPlayer(player, S2CPayload.SEND_FACTION_IDS, factionIds);
        }
    }

    private static Direction directionFromOrdinal(int ordinal) {
        Direction[] values = Direction.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : Direction.UP;
    }

    private static CompoundTag serializePath(CustomNpc npc) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("entityId", npc.getId());
        ListTag points = new ListTag();
        Path path = npc.getNavigation().getPath();
        if (path != null && path.getNodeCount() > 0 && !path.isDone()) {
            addPoint(points, npc.position().add(0.0D, 0.08D, 0.0D));
            int start = Math.max(0, path.getNextNodeIndex());
            int end = Math.min(path.getNodeCount(), start + 128);
            for (int index = start; index < end; index++) {
                addPoint(points, path.getEntityPosAtNode(npc, index).add(0.0D, 0.08D, 0.0D));
            }
        }
        tag.put("points", points);
        return tag;
    }

    private static void addPoint(ListTag points, Vec3 position) {
        CompoundTag point = new CompoundTag();
        point.putDouble("x", position.x());
        point.putDouble("y", position.y());
        point.putDouble("z", position.z());
        points.add(point);
    }

    private static CustomNpc getWorldTestNpc(ServerPlayer player, int entityId) {
        if (player == null || entityId < 0) return null;
        Entity entity = ((ServerLevel) player.level()).getEntity(entityId);
        return entity instanceof CustomNpc npc ? npc : null;
    }

    private static void sendNpcAiDebugSnapshot(ServerPlayer player, int entityId, CustomNpc npc) {
        if (player == null || npc == null) return;
        NpcBehaviorDataSerializers.register();
        CompoundTag payload = new CompoundTag();
        payload.putInt("entityId", entityId);
        payload.putBoolean("paused", npc.isNpcBehaviorDebugPaused());
        payload.putString("npcType", npc.getNpcType());
        payload.put("snapshot", npc.getNpcBehaviorDebugSnapshot().serializeNBT(Platform.getFrozenRegistry()));
        RPCPacketDistributor.rpcToPlayer(player, S2CPayload.SEND_NPC_AI_DEBUG_SNAPSHOT, payload);
    }
}

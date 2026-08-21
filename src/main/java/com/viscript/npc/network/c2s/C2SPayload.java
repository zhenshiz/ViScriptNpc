package com.viscript.npc.network.c2s;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacket;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.compat.team.NpcFactionBridge;
import com.viscript.npc.gui.test.NpcTestSceneProject;
import com.viscript.npc.network.s2c.S2CPayload;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.NpcRegister;
import com.viscript.npc.npc.ai.AMIntentionAssetPublisher;
import com.viscript.npc.npc.ai.test.NpcAiTestEnvironment;
import com.viscript.npc.npc.ai.test.NpcAiTestLevelService;
import com.viscript.npc.util.NpcRuntimeFiles;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import com.viscript.npc.npc.data.ai.NpcAI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Set;

public class C2SPayload {
    public static final String CREATE_NEW_NPC = "createNewNpc";
    public static final String OVERWRITE_NPC = "overwriteNpc";
    public static final String SET_NPC_AI_DEBUG_PAUSED = "setNpcAiDebugPaused";
    public static final String CONTINUE_NPC_AI_DEBUG = "continueNpcAiDebug";
    public static final String STEP_NPC_AI_DEBUG = "stepNpcAiDebug";
    public static final String STOP_NPC_AI_DEBUG = "stopNpcAiDebug";
    public static final String REQUEST_NPC_AI_DEBUG_SNAPSHOT = "requestNpcAiDebugSnapshot";
    public static final String SYNC_NPC_AI_DEBUG_CONFIG = "syncNpcAiDebugConfig";
    public static final String REQUEST_FACTION_IDS = "requestFactionIds";
    public static final String UPLOAD_AM_INTENTION_ASSET = "uploadAmIntentionAsset";
    public static final String SET_NPC_AI_DEBUG_BREAKPOINTS = "setNpcAiDebugBreakpoints";
    public static final String ENTER_NPC_AI_TEST = ViScriptNpc.MOD_ID + ":enterNpcAiTest";
    public static final String CREATE_NPC_AI_TEST_ARENA = ViScriptNpc.MOD_ID + ":createNpcAiTestArena";
    public static final String LEAVE_NPC_AI_TEST = ViScriptNpc.MOD_ID + ":leaveNpcAiTest";
    public static final String APPLY_NPC_AI_TEST_ACTION = ViScriptNpc.MOD_ID + ":applyNpcAiTestAction";
    public static final String REQUEST_NPC_AI_TEST_RUNTIME_NPCS =
            ViScriptNpc.MOD_ID + ":requestNpcAiTestRuntimeNpcs";

    @RPCPacket(ENTER_NPC_AI_TEST)
    public static void enterNpcAiTest(RPCSender sender, CompoundTag projectTag) {
        if (sender.isServer()) return;
        ServerPlayer player = sender.asPlayer();
        NpcTestSceneProject project = readNpcTestSceneProject(projectTag);
        if (player != null && NpcAiTestLevelService.enter(
                player, project.getArenaWidth(), project.getArenaLength())) {
            openNpcAiTestEditor(player, projectTag);
        }
    }

    /**
     * 接收新测试场景工程的场地创建请求，并用服务端分配的坐标重新打开工程。
     *
     * @param sender RPC 发送方信息
     * @param projectTag 新测试场景工程的序列化数据
     */
    @RPCPacket(CREATE_NPC_AI_TEST_ARENA)
    public static void createNpcAiTestArena(RPCSender sender, CompoundTag projectTag) {
        if (sender.isServer()) return;
        ServerPlayer player = sender.asPlayer();
        NpcTestSceneProject project = readNpcTestSceneProject(projectTag);
        if (player != null && NpcAiTestLevelService.renewArena(
                player, project.getArenaWidth(), project.getArenaLength())) {
            syncNpcAiTestProject(player, projectTag);
        }
    }

    @RPCPacket(LEAVE_NPC_AI_TEST)
    public static void leaveNpcAiTest(RPCSender sender) {
        if (sender.isServer()) return;
        NpcAiTestLevelService.leave(sender.asPlayer());
    }

    @RPCPacket(APPLY_NPC_AI_TEST_ACTION)
    public static void applyNpcAiTestAction(RPCSender sender, String action, CompoundTag payload) {
        if (!sender.isServer()) {
            NpcAiTestLevelService.applyAction(sender.asPlayer(), action, payload);
        }
    }

    /**
     * 查询服务端 NPC 运行时目录，并把逻辑文件标识和 <code>npcId</code> 返回给测试场景界面。
     *
     * @param sender RPC 发送方信息
     */
    @RPCPacket(REQUEST_NPC_AI_TEST_RUNTIME_NPCS)
    public static void requestNpcAiTestRuntimeNpcs(RPCSender sender) {
        if (sender.isServer()) return;
        ServerPlayer player = sender.asPlayer();
        if (!NpcAiTestLevelService.isAuthorized(player)
                || !NpcAiTestEnvironment.isTestLevel(player.serverLevel())) {
            return;
        }
        ListTag values = new ListTag();
        for (NpcRuntimeFiles.Entry entry : NpcRuntimeFiles.listEntries()) {
            CompoundTag value = new CompoundTag();
            value.putString("fileId", entry.fileId());
            value.putString("npcId", entry.npcId());
            values.add(value);
        }
        CompoundTag payload = new CompoundTag();
        payload.put("values", values);
        RPCPacketDistributor.rpcToPlayer(player, S2CPayload.SEND_NPC_AI_TEST_RUNTIME_NPCS, payload);
    }

    @RPCPacket(UPLOAD_AM_INTENTION_ASSET)
    public static void uploadAmIntentionAsset(RPCSender sender, String assetId, String json) {
        if (sender.isServer()) return;
        ServerPlayer player = sender.asPlayer();
        if (player == null) return;
        try {
            AMIntentionAssetPublisher.publish(player, assetId, json);
        } catch (RuntimeException exception) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "AM intention publish failed: " + exception.getMessage()));
        }
    }

    @RPCPacket(SET_NPC_AI_DEBUG_BREAKPOINTS)
    public static void setNpcAiDebugBreakpoints(RPCSender sender, int entityId, CompoundTag payload) {
        if (sender.isServer()) return;
        ServerPlayer player = sender.asPlayer();
        CustomNpc npc = getNpcAiDebugTarget(player, entityId);
        if (npc == null) return;
        Set<java.util.UUID> parsed = new java.util.LinkedHashSet<>();
        if (payload != null) {
            ListTag ids = payload.getList("values", net.minecraft.nbt.Tag.TAG_STRING);
            for (int index = 0; index < ids.size(); index++) {
                String id = ids.getString(index);
                try { parsed.add(java.util.UUID.fromString(id)); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        npc.setNpcAiDebugBreakpoints(parsed);
        sendNpcAiDebugSnapshot(player, entityId, npc);
    }

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
            CustomNpc npc = getNpcAiDebugTarget(player, entityId);
            if (npc == null) return;
            if (paused) {
                npc.setNpcAiDebugPaused(true);
            } else {
                npc.continueNpcAiDebug();
            }
            sendNpcAiDebugSnapshot(player, entityId, npc);
        }
    }

    @RPCPacket(CONTINUE_NPC_AI_DEBUG)
    public static void continueNpcAiDebug(RPCSender sender, int entityId) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            CustomNpc npc = getNpcAiDebugTarget(player, entityId);
            if (npc == null) return;
            npc.continueNpcAiDebug();
            sendNpcAiDebugSnapshot(player, entityId, npc);
        }
    }

    @RPCPacket(STEP_NPC_AI_DEBUG)
    public static void stepNpcAiDebug(RPCSender sender, int entityId) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            CustomNpc npc = getNpcAiDebugTarget(player, entityId);
            if (npc == null) return;
            npc.stepNpcAiDebug();
            sendNpcAiDebugSnapshot(player, entityId, npc);
        }
    }

    @RPCPacket(STOP_NPC_AI_DEBUG)
    public static void stopNpcAiDebug(RPCSender sender, int entityId) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            CustomNpc npc = getNpcAiDebugTarget(player, entityId);
            if (npc == null) return;
            npc.stopNpcAiDebug();
            sendNpcAiDebugSnapshot(player, entityId, npc);
        }
    }

    @RPCPacket(REQUEST_NPC_AI_DEBUG_SNAPSHOT)
    public static void requestNpcAiDebugSnapshot(RPCSender sender, int entityId) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            CustomNpc npc = getNpcAiDebugTarget(player, entityId);
            if (npc == null) return;
            sendNpcAiDebugSnapshot(player, entityId, npc);
        }
    }

    @RPCPacket(SYNC_NPC_AI_DEBUG_CONFIG)
    public static void syncNpcAiDebugConfig(RPCSender sender, int entityId, CompoundTag aiTag) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            CustomNpc npc = getNpcAiDebugTarget(player, entityId);
            if (npc == null || aiTag == null || aiTag.isEmpty()) return;
            NpcAI ai = npc.getNpcAI();
            ai.deserializeNBT(Platform.getFrozenRegistry(), aiTag);
            npc.updateNpcState();
            sendNpcAiDebugSnapshot(player, entityId, npc);
        }
    }

    @RPCPacket(REQUEST_FACTION_IDS)
    public static void requestFactionIds(RPCSender sender) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            if (player == null) return;
            Set<String> factionIds = NpcFactionBridge.getFactionIds(player.serverLevel());
            RPCPacketDistributor.rpcToPlayer(player, S2CPayload.SEND_FACTION_IDS,
                    S2CPayload.encodeStrings(factionIds));
        }
    }

    private static CustomNpc getNpcAiDebugTarget(ServerPlayer player, int entityId) {
        if (player == null || entityId < 0) return null;
        Entity entity = ((ServerLevel) player.level()).getEntity(entityId);
        return entity instanceof CustomNpc npc ? npc : null;
    }

    private static NpcTestSceneProject readNpcTestSceneProject(CompoundTag projectTag) {
        NpcTestSceneProject project = new NpcTestSceneProject();
        project.initNewProject();
        if (projectTag != null && !projectTag.isEmpty()) {
            project.deserializeNBT(Platform.getFrozenRegistry(), projectTag);
        }
        return project;
    }

    private static void openNpcAiTestEditor(ServerPlayer player, CompoundTag projectTag) {
        var arenaCenter = NpcAiTestLevelService.getArenaCenter(player);
        if (arenaCenter == null) return;
        CompoundTag editorTag = projectTag.copy();
        editorTag.putLong(NpcAiTestEnvironment.ARENA_CENTER_TAG, arenaCenter.asLong());
        ViScriptNpcServerUtil.openNpcEditor(player, editorTag);
    }

    private static void syncNpcAiTestProject(ServerPlayer player, CompoundTag projectTag) {
        var arenaCenter = NpcAiTestLevelService.getArenaCenter(player);
        if (arenaCenter == null) return;
        CompoundTag editorTag = projectTag.copy();
        editorTag.putLong(NpcAiTestEnvironment.ARENA_CENTER_TAG, arenaCenter.asLong());
        RPCPacketDistributor.rpcToPlayer(player, S2CPayload.OPEN_NPC_EDITOR, editorTag);
    }

    private static void sendNpcAiDebugSnapshot(ServerPlayer player, int entityId, CustomNpc npc) {
        if (player == null || npc == null) return;
        CompoundTag payload = new CompoundTag();
        payload.putInt("entityId", entityId);
        payload.putBoolean("paused", npc.isNpcAiDebugPaused());
        payload.putString("npcType", npc.getNpcType());
        payload.put("snapshot", npc.getAiDebugSnapshot());
        RPCPacketDistributor.rpcToPlayer(player, S2CPayload.SEND_NPC_AI_DEBUG_SNAPSHOT, payload);
    }
}

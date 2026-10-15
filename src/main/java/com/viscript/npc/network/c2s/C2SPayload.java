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
import com.viscript.npc.npc.ai.test.NpcAiTestEnvironment;
import com.viscript.npc.npc.ai.test.NpcAiTestLevelService;
import com.viscript.npc.util.NpcRuntimeFiles;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Set;

public class C2SPayload {
    public static final String CREATE_NEW_NPC = "createNewNpc";
    public static final String OVERWRITE_NPC = "overwriteNpc";
    public static final String REQUEST_FACTION_IDS = "requestFactionIds";
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
}

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
    public static final String SAVE_NPC_AI = ViScriptNpc.MOD_ID + ":saveNpcAi";
    public static final String REQUEST_FACTION_IDS = "requestFactionIds";
    public static final String REQUEST_NPC_ACTION_LIBRARY = ViScriptNpc.MOD_ID + ":requestNpcActionLibrary";
    public static final String ENTER_NPC_AI_TEST = ViScriptNpc.MOD_ID + ":enterNpcAiTest";
    public static final String CREATE_NPC_AI_TEST_ARENA = ViScriptNpc.MOD_ID + ":createNpcAiTestArena";
    public static final String LEAVE_NPC_AI_TEST = ViScriptNpc.MOD_ID + ":leaveNpcAiTest";
    public static final String APPLY_NPC_AI_TEST_ACTION = ViScriptNpc.MOD_ID + ":applyNpcAiTestAction";
    public static final String CAPTURE_NPC_AI_TEST_SCENE = ViScriptNpc.MOD_ID + ":captureNpcAiTestScene";
    public static final String REQUEST_NPC_AI_TEST_RUNTIME_NPCS =
            ViScriptNpc.MOD_ID + ":requestNpcAiTestRuntimeNpcs";

    /**
     * 向编辑器发送服务端行为功能文件；每个定义单独传输，最后发送完成标识。
     *
     * @param sender RPC 发送方信息
     * @param requestId 客户端列表请求标识
     */
    @RPCPacket(REQUEST_NPC_ACTION_LIBRARY)
    public static void requestNpcActionLibrary(RPCSender sender, String requestId) {
        if (sender.isServer()) return;
        ServerPlayer player = sender.asPlayer();
        if (player == null) return;
        if (player.hasPermissions(2)) {
            com.viscript.npc.util.NpcActionFiles.invalidate();
            for (String id : com.viscript.npc.util.NpcEditorFiles.listRuntimeFiles(
                    com.viscript.npc.util.NpcEditorFormats.NPC_ACTION, true)) {
                var definition = com.viscript.npc.util.NpcActionFiles.load(id);
                if (definition != null) RPCPacketDistributor.rpcToPlayer(player,
                        S2CPayload.SEND_NPC_ACTION_LIBRARY, requestId, id,
                        definition.serializeNBT(Platform.getFrozenRegistry()), false);
            }
        }
        RPCPacketDistributor.rpcToPlayer(player, S2CPayload.SEND_NPC_ACTION_LIBRARY,
                requestId, "", new CompoundTag(), true);
    }

    @RPCPacket(ENTER_NPC_AI_TEST)
    public static void enterNpcAiTest(RPCSender sender, CompoundTag projectTag) {
        if (sender.isServer()) return;
        ServerPlayer player = sender.asPlayer();
        NpcTestSceneProject project = readNpcTestSceneProject(projectTag);
        if (player != null && NpcAiTestLevelService.enter(
                player, project.getArenaWidth(), project.getArenaLength())) {
            NpcAiTestLevelService.restoreArena(player, project.getSceneBlocks());
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
            NpcAiTestLevelService.restoreArena(player, project.getSceneBlocks());
            syncNpcAiTestProject(player, projectTag);
        }
    }

    /**
     * 返回指定编辑器工程所绑定场地的服务器权威方块快照。
     *
     * @param sender RPC 发送方信息
     * @param requestId 本次保存请求的标识
     * @param center 场地中心的打包坐标
     */
    @RPCPacket(CAPTURE_NPC_AI_TEST_SCENE)
    public static void captureNpcAiTestScene(RPCSender sender, String requestId, long center) {
        if (sender.isServer()) return;
        ServerPlayer player = sender.asPlayer();
        if (player == null) return;
        CompoundTag snapshot = NpcAiTestLevelService.captureArena(player, net.minecraft.core.BlockPos.of(center));
        CompoundTag response = new CompoundTag();
        response.putBoolean("success", snapshot != null);
        if (snapshot != null) response.put("scene_blocks", snapshot);
        RPCPacketDistributor.rpcToPlayer(player, S2CPayload.SEND_NPC_AI_TEST_SCENE, requestId, response);
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
            if (player == null || !player.hasPermissions(2) || !prepareNpcConfig(player, tag)) return;

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
            if (player == null || !player.hasPermissions(2) || !prepareNpcConfig(player, tag)) return;
            Entity entity = ((ServerLevel) player.level()).getEntity(entityId);
            if (entity instanceof CustomNpc npc) {
                npc.readAdditionalSaveData(tag);
            }
        }
    }

    /**
     * 保存已完成编辑的 AI 快照，更新标记匹配的 NPC，并反馈更新数量。
     *
     * @param sender RPC 发送方信息
     * @param marker 需要精确匹配的 NPC 标记
     * @param snapshot AI 配置与完整行为依赖
     */
    @RPCPacket(SAVE_NPC_AI)
    public static void saveNpcAi(RPCSender sender, String marker, CompoundTag snapshot) {
        if (sender.isServer()) return;
        ServerPlayer player = sender.asPlayer();
        if (player == null) return;
        boolean success = false;
        net.minecraft.network.chat.Component message;
        try {
            if (!player.hasPermissions(2)) throw new IllegalArgumentException("viscript_npc.ai.save.no_permission");
            int count = com.viscript.npc.util.NpcAiUpdates.apply(player.server, marker, snapshot);
            success = true;
            message = net.minecraft.network.chat.Component.translatable(count == 0
                    ? "viscript_npc.ai.save.no_matches" : "viscript_npc.ai.save.applied", marker, count);
        } catch (RuntimeException exception) {
            message = net.minecraft.network.chat.Component.translatable(
                    java.util.Objects.toString(exception.getMessage(), "viscript_npc.ai.save.failed"));
        }
        RPCPacketDistributor.rpcToPlayer(player, S2CPayload.SEND_NPC_AI_SAVE_RESULT, success, message);
    }

    private static boolean prepareNpcConfig(ServerPlayer player, CompoundTag tag) {
        try { com.viscript.npc.util.NpcAiPublication.prepareConfig(tag, true); return true; }
        catch (Exception exception) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(exception.getMessage()), false); return false;
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

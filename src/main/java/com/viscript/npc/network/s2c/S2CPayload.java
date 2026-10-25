package com.viscript.npc.network.s2c;

import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacket;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.util.ViScriptNpcClientUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.Set;

public class S2CPayload {
    public static final String OPEN_NPC_EDITOR = "openNpcEditor";
    public static final String SEND_LOOT_TABLES = "sendLootTables";
    public static final String SEND_FACTION_IDS = "sendFactionIds";
    public static final String SEND_NPC_ACTION_LIBRARY = ViScriptNpc.MOD_ID + ":sendNpcActionLibrary";
    public static final String SEND_NPC_AI_SAVE_RESULT = ViScriptNpc.MOD_ID + ":sendNpcAiSaveResult";

    /**
     * 显示服务器完成 AI 保存后的结果。
     *
     * @param sender RPC 发送方信息
     * @param success 是否通过校验并完成应用
     * @param message 已更新数量或失败原因
     */
    @RPCPacket(SEND_NPC_AI_SAVE_RESULT)
    public static void sendNpcAiSaveResult(RPCSender sender, boolean success, net.minecraft.network.chat.Component message) {
        if (sender.isServer()) com.viscript.npc.gui.ai.AiDrafts.showSaveResult(success, message);
    }
    public static final String SEND_NPC_AI_TEST_SCENE = ViScriptNpc.MOD_ID + ":sendNpcAiTestScene";
    public static final String SEND_NPC_AI_TEST_RUNTIME_NPCS =
            ViScriptNpc.MOD_ID + ":sendNpcAiTestRuntimeNpcs";

    /**
     * 将服务端行为功能文件快照交给请求列表的客户端编辑器。
     *
     * @param sender RPC 发送方信息
     * @param requestId 客户端列表请求标识
     * @param fileId 行为文件的逻辑标识
     * @param definition 行为定义的 NBT 数据
     * @param complete 是否已发送全部行为
     */
    @RPCPacket(SEND_NPC_ACTION_LIBRARY)
    public static void sendNpcActionLibrary(RPCSender sender, String requestId, String fileId,
                                           CompoundTag definition, boolean complete) {
        if (sender.isServer()) com.viscript.npc.gui.ai.AiServerActions.receive(requestId, fileId, definition, complete);
    }

    @RPCPacket(OPEN_NPC_EDITOR)
    public static void openNpcEditor(RPCSender sender, CompoundTag tag) {
        ViScriptNpcClientUtil.openNpcEditor(tag);
    }

    /**
     * 将场地快照交给等待保存或导出的客户端编辑器。
     *
     * @param sender RPC 发送方信息
     * @param requestId 保存请求标识
     * @param payload 服务端场地快照或失败标识
     */
    @RPCPacket(SEND_NPC_AI_TEST_SCENE)
    public static void sendNpcAiTestScene(RPCSender sender, String requestId, CompoundTag payload) {
        if (sender.isServer()) ViScriptNpcClientUtil.receiveNpcAiTestScene(requestId, payload);
    }

    @RPCPacket(SEND_LOOT_TABLES)
    public static void setSendLootTables(RPCSender sender, CompoundTag payload) {
        if (sender.isServer()) CustomNpc.lootTableKeys = decodeStrings(payload);
    }

    @RPCPacket(SEND_FACTION_IDS)
    public static void setSendFactionIds(RPCSender sender, CompoundTag payload) {
        if (sender.isServer()) CustomNpc.factionIds = decodeStrings(payload);
    }

    /**
     * 把服务端 NPC 运行时文件列表保存到客户端测试场景状态中。
     *
     * @param sender RPC 发送方信息
     * @param payload 包含逻辑文件标识和 <code>npcId</code> 的列表数据
     */
    @RPCPacket(SEND_NPC_AI_TEST_RUNTIME_NPCS)
    public static void sendNpcAiTestRuntimeNpcs(RPCSender sender, CompoundTag payload) {
        if (sender.isServer()) ViScriptNpcClientUtil.setNpcAiTestRuntimeNpcs(payload);
    }

    public static CompoundTag encodeStrings(Set<String> values) {
        CompoundTag payload = new CompoundTag();
        ListTag entries = new ListTag();
        if (values != null) values.stream().sorted().forEach(value -> entries.add(StringTag.valueOf(value)));
        payload.put("values", entries);
        return payload;
    }

    private static Set<String> decodeStrings(CompoundTag payload) {
        if (payload == null) return Set.of();
        ListTag entries = payload.getList("values", Tag.TAG_STRING);
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>();
        for (int index = 0; index < entries.size(); index++) values.add(entries.getString(index));
        return Set.copyOf(values);
    }
}

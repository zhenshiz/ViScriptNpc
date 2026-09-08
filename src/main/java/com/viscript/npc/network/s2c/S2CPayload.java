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
    public static final String SEND_NPC_AI_DEBUG_SNAPSHOT = "sendNpcAiDebugSnapshot";
    public static final String SEND_NPC_AI_DESCRIPTORS = "sendNpcAiDescriptors";
    public static final String SEND_NPC_AI_TEST_RUNTIME_NPCS =
            ViScriptNpc.MOD_ID + ":sendNpcAiTestRuntimeNpcs";

    @RPCPacket(OPEN_NPC_EDITOR)
    public static void openNpcEditor(RPCSender sender, CompoundTag tag) {
        ViScriptNpcClientUtil.openNpcEditor(tag);
    }

    @RPCPacket(SEND_LOOT_TABLES)
    public static void setSendLootTables(RPCSender sender, CompoundTag payload) {
        if (sender.isServer()) CustomNpc.lootTableKeys = decodeStrings(payload);
    }

    @RPCPacket(SEND_FACTION_IDS)
    public static void setSendFactionIds(RPCSender sender, CompoundTag payload) {
        if (sender.isServer()) CustomNpc.factionIds = decodeStrings(payload);
    }

    @RPCPacket(SEND_NPC_AI_DEBUG_SNAPSHOT)
    public static void sendNpcAiDebugSnapshot(RPCSender sender, CompoundTag payload) {
        if (sender.isServer()) ViScriptNpcClientUtil.setNpcAiDebugSnapshot(payload);
    }

    @RPCPacket(SEND_NPC_AI_DESCRIPTORS)
    public static void sendNpcAiDescriptors(RPCSender sender, CompoundTag payload) {
        if (sender.isServer()) ViScriptNpcClientUtil.setNpcAiDescriptors(payload);
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

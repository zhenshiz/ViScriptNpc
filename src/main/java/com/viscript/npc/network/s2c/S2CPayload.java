package com.viscript.npc.network.s2c;

import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacket;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
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
    public static final String SEND_NPC_AI_WORLD_TEST_PATH = "sendNpcAiWorldTestPath";
    public static final String SEND_NPC_AI_DEBUG_SNAPSHOT = "sendNpcAiDebugSnapshot";
    public static final String SEND_NPC_AI_DESCRIPTORS = "sendNpcAiDescriptors";

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

    @RPCPacket(SEND_NPC_AI_WORLD_TEST_PATH)
    public static void sendNpcAiWorldTestPath(RPCSender sender, CompoundTag tag) {
        if (sender.isServer()) ViScriptNpcClientUtil.receiveNpcAiWorldPath(tag);
    }

    @RPCPacket(SEND_NPC_AI_DEBUG_SNAPSHOT)
    public static void sendNpcAiDebugSnapshot(RPCSender sender, CompoundTag payload) {
        if (sender.isServer()) ViScriptNpcClientUtil.setNpcAiDebugSnapshot(payload);
    }

    @RPCPacket(SEND_NPC_AI_DESCRIPTORS)
    public static void sendNpcAiDescriptors(RPCSender sender, CompoundTag payload) {
        if (sender.isServer()) ViScriptNpcClientUtil.setNpcAiDescriptors(payload);
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

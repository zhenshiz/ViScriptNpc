package com.viscript.npc.network.s2c;

import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacket;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.util.ViScriptNpcClientUtil;
import net.minecraft.nbt.CompoundTag;

import java.util.Set;

public class S2CPayload {
    public static final String OPEN_NPC_EDITOR = "openNpcEditor";
    public static final String SEND_LOOT_TABLES = "sendLootTables";
    public static final String SEND_NPC_AI_DEBUG_SNAPSHOT = "sendNpcAiDebugSnapshot";

    @RPCPacket(OPEN_NPC_EDITOR)
    public static void openNpcEditor(RPCSender sender, CompoundTag tag) {
        ViScriptNpcClientUtil.openNpcEditor(tag);
    }

    @RPCPacket(SEND_LOOT_TABLES)
    public static void setSendLootTables(RPCSender sender, Set<String> keys) {
        if (sender.isServer()) CustomNpc.lootTableKeys = keys;
    }

    @RPCPacket(SEND_NPC_AI_DEBUG_SNAPSHOT)
    public static void sendNpcAiDebugSnapshot(RPCSender sender, CompoundTag payload) {
        if (sender.isServer()) {
            ViScriptNpcClientUtil.setNpcAiDebugSnapshot(payload);
        }
    }
}

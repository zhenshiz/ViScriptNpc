package com.viscript.npc.network.s2c;

import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacket;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import com.viscript.npc.util.ViScriptNpcClientUtil;

public class S2CPayload {
    public static final String OPEN_NPC_EDITOR = "openNpcEditor";

    @RPCPacket(OPEN_NPC_EDITOR)
    public static void openNpcEditor(RPCSender sender) {
        ViScriptNpcClientUtil.openNpcEditor();
    }
}

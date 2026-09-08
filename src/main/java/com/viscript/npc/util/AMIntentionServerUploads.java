package com.viscript.npc.util;

import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.viscript.npc.network.c2s.C2SPayload;
import com.viscript.npc.npc.ai.editor.AMIntentionGraphCompiler;
import net.minecraft.resources.ResourceLocation;

public final class AMIntentionServerUploads {
    private AMIntentionServerUploads() {
    }

    public static void upload(ResourceLocation requestedId, AMIntentionGraphCompiler.Compilation compilation) {
        if (!compilation.isSuccess()) {
            throw new IllegalStateException(String.join("\n", compilation.errors()));
        }
        if (!requestedId.equals(compilation.assetId())) {
            throw new IllegalArgumentException("Published asset ID must match the graph Definition ID ("
                    + compilation.assetId() + ")");
        }
        RPCPacketDistributor.rpcToServer(C2SPayload.UPLOAD_AM_INTENTION_ASSET,
                requestedId.toString(), compilation.json());
    }
}

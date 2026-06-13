package com.viscript.npc.network.c2s;

import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacket;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.NpcRegister;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class C2SPayload {
    public static final String CREATE_NEW_NPC = "createNewNpc";
    public static final String OVERWRITE_NPC = "overwriteNpc";

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
}

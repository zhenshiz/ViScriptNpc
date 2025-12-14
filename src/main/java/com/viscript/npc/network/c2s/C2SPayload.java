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
    public static final String CREATE_NPC = "createNpc";

    @RPCPacket(CREATE_NPC)
    public static void createNpc(RPCSender sender, CompoundTag tag) {
        if (sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            if (player == null) return;
            if (tag.contains("npcType")) {
                String npcType = tag.getString("npcType");
                for (Entity entity : ((ServerLevel) player.level()).getEntities().getAll()) {
                    if (entity instanceof CustomNpc npc && npc.getNpcType().equals(npcType)) {
                        npc.readAdditionalSaveData(tag);
                    }
                }
            } else {
                CustomNpc npc = NpcRegister.CUSTOM_NPC.get().create(player.level());
                if (npc != null) {
                    npc.moveTo(player.position());
                    npc.readAdditionalSaveData(tag);
                    player.level().addFreshEntity(npc);
                }
            }
        }
    }
}

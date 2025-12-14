package com.viscript.npc.util;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.viscript.npc.gui.edit.npc.NPC;
import com.viscript.npc.network.s2c.S2CPayload;
import com.viscript.npc.npc.NpcRegister;
import com.viscript.npc.util.npc.NpcHelper;
import dev.latvian.mods.kubejs.typings.Info;
import lombok.SneakyThrows;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

public class ViScriptNpcServerUtil {

    @Info("服务端打开NPC编辑器")
    public static void openNpcEditor(ServerPlayer player) {
        RPCPacketDistributor.rpcToPlayer(player, S2CPayload.OPEN_NPC_EDITOR);
    }

    @Info("生成NPC")
    @SneakyThrows
    public static Entity summonNpc(ResourceLocation location, Vec3 pos) {
        NPC npc = NpcHelper.getNPC(location);
        if (npc != null) {
            return SummonCommand.createEntity(Platform.getMinecraftServer().createCommandSourceStack(), (Holder.Reference<EntityType<?>>) NpcRegister.CUSTOM_NPC.getDelegate(), pos, npc.serializeNBT(Platform.getFrozenRegistry()), false);
        }
        return null;
    }
}

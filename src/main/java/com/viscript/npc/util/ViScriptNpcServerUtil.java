package com.viscript.npc.util;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.viscript.npc.network.s2c.S2CPayload;
import com.viscript.npc.npc.NpcRegister;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

public class ViScriptNpcServerUtil {

    @Info("服务端打开NPC编辑器")
    public static void openNpcEditor(ServerPlayer player, CompoundTag tag) {
        RPCPacketDistributor.rpcToPlayer(player, S2CPayload.OPEN_NPC_EDITOR, tag);
    }

    @Info("生成NPC")
    public static Entity summonNpc(CompoundTag tag, Vec3 pos) {
        try {
            return SummonCommand.createEntity(Platform.getMinecraftServer().createCommandSourceStack(), (Holder.Reference<EntityType<?>>) NpcRegister.CUSTOM_NPC.getDelegate(), pos, tag, false);
        } catch (CommandSyntaxException e) {
            return null;
        }
    }
}

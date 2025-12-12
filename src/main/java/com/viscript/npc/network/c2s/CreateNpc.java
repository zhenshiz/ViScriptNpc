package com.viscript.npc.network.c2s;

import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.NpcRegister;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record CreateNpc(CompoundTag nbt) implements CustomPacketPayload {
    public static final Type<CreateNpc> TYPE = new Type<>(ViScriptNpc.id("create_npc"));
    public static final StreamCodec<FriendlyByteBuf, CreateNpc> CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, CreateNpc::nbt,
            CreateNpc::new
    );

    public static void execute(CreateNpc payload, IPayloadContext context) {
        CompoundTag tag = payload.nbt;
        ServerPlayer player = (ServerPlayer) context.player();
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

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

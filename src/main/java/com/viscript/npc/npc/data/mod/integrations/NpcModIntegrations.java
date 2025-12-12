package com.viscript.npc.npc.data.mod.integrations;

import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.mojang.serialization.Codec;
import com.viscript.npc.npc.data.INpcData;
import io.netty.buffer.ByteBuf;
import lombok.Data;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

//关于联动模组的配置
@Data
public class NpcModIntegrations implements INpcData {
    public static final StreamCodec<ByteBuf, NpcModIntegrations> STREAM_CODEC;
    public static final Codec<NpcModIntegrations> CODEC;

    static {
        CODEC = PersistedParser.createCodec(NpcModIntegrations::new);
        STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
    }
}

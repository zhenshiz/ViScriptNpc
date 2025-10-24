package com.viscript.npc.npc.data.ai;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import lombok.Data;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

@Data
public class NpcAI implements IConfigurable, IPersistedSerializable {
    public static final StreamCodec<ByteBuf, NpcAI> STREAM_CODEC;
    public static final Codec<NpcAI> CODEC;

    static  {
        CODEC = PersistedParser.createCodec(NpcAI::new);
        STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
    }
}

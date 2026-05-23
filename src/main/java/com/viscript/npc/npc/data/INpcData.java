package com.viscript.npc.npc.data;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.registry.ILDLRegister;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.utils.LDLibExtraCodecs;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.mojang.serialization.Codec;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.ViScriptNpcRegistries;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.function.Supplier;

public interface INpcData extends ILDLRegister<INpcData, Supplier<INpcData>>, IConfigurable, IPersistedSerializable {
    String ID = ViScriptNpc.MOD_ID + ":npc_data";

    Codec<INpcData> CODEC = ViScriptNpcRegistries.NPC_DATA.optionalCodec()
            .dispatch(ILDLRegister::getRegistryHolderOptional,
                    optional -> optional.map(holder -> PersistedParser.createCodec(holder.value()).fieldOf("data"))
                            .orElseGet(LDLibExtraCodecs::errorDecoder));
    StreamCodec<ByteBuf, INpcData> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
}

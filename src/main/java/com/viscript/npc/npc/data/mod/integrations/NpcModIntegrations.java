package com.viscript.npc.npc.data.mod.integrations;

import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
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

    @Configurable(name = "npcConfig.npcModIntegrations.chatBoxConfig", tips = "npcConfig.npcModIntegrations.chatBoxConfig.tips", subConfigurable = true)
    private ChatBoxConfig chatBoxConfig = new ChatBoxConfig();

    static {
        CODEC = PersistedParser.createCodec(NpcModIntegrations::new);
        STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
    }
}

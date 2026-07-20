package com.viscript.npc.npc.data;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.registry.ILDLRegister;
import com.lowdragmc.lowdraglib2.utils.LDLibExtraCodecs;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.mojang.serialization.Codec;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.ViScriptNpcRegistries;
import com.viscript.npc.gui.edit.page.NpcEditorPageIds;
import com.viscript_lib.util.ISkipDefaultedSerialize;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public interface INpcData extends ILDLRegister<INpcData, Supplier<INpcData>>, IConfigurable, ISkipDefaultedSerialize {
    String ID = ViScriptNpc.MOD_ID + ":npc_data";

    Codec<INpcData> CODEC = ViScriptNpcRegistries.NPC_DATA.optionalCodec()
            .dispatch(ILDLRegister::getRegistryHolderOptional,
                    optional -> optional.map(holder -> PersistedParser.createMapCodec(holder.value())).orElseGet(LDLibExtraCodecs::errorDecoder));
    StreamCodec<ByteBuf, INpcData> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    default ResourceLocation getEditorPage() {
        return NpcEditorPageIds.BASIC;
    }

    default int getEditorOrder() {
        return isLDLRegister() ? getRegisterUI().priority() : 0;
    }

    @Override
    default void deserializeNBT(HolderLookup.@NotNull Provider provider, @NotNull CompoundTag tag) {
        String name = getConfigurableName();
        CompoundTag branch;
        // 存储于实体上的数据附件
        var tagKey = ViScriptNpc.MOD_ID + ":" + name;
        if (tag.contains("neoforge:attachments") && tag.getCompound("neoforge:attachments").contains(tagKey))
            branch = tag.getCompound("neoforge:attachments").getCompound(tagKey);
        // ldlib2序列化的nbt
        else if (tag.contains(name)) branch = tag.getCompound(name);
        else branch = tag;
        if (branch.contains("data")) branch = branch.getCompound("data");
        ISkipDefaultedSerialize.super.deserializeNBT(provider, branch);
    }
}

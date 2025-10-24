package com.viscript.npc.gui.edit.npc;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.ISceneObject;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.registry.ILDLRegisterClient;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.utils.LDLibExtraCodecs;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.mojang.serialization.Codec;
import com.viscript.npc.ViScriptNpcRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public interface INPCObject extends ISceneObject, IPersistedSerializable, IConfigurable, ILDLRegisterClient<INPCObject, Supplier<INPCObject>> {
    Codec<INPCObject> CODEC = ViScriptNpcRegistries.NPC_OBJECTS.optionalCodec().dispatch(ILDLRegisterClient::getRegistryHolderOptional,
            optional -> optional.map(holder -> PersistedParser.createCodec(holder.value()).fieldOf("data"))
                    .orElseGet(LDLibExtraCodecs::errorDecoder));

    @Nullable
    default CompoundTag serializeWrapper() {
        return (CompoundTag) CODEC.encodeStart(NbtOps.INSTANCE, this).result().orElse(null);
    }

    @Nullable
    static INPCObject deserializeWrapper(Tag tag) {
        return CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null);
    }

    default IGuiTexture getIcon() {
        return IGuiTexture.EMPTY;
    }

    default INPCObject deepCopy() {
        return CODEC.encodeStart(NbtOps.INSTANCE, this).result().flatMap((tag) -> CODEC.parse(NbtOps.INSTANCE, tag).result()).orElse(null);
    }

    default INPCObject shallowCopy() {
        return deepCopy();
    }

    default INPCObject copy(boolean deep) {
        return deep ? deepCopy() : shallowCopy();
    }
}

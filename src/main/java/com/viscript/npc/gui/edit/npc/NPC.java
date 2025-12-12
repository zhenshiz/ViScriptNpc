package com.viscript.npc.gui.edit.npc;

import com.mojang.blaze3d.MethodsReturnNonnullByDefault;
import com.viscript.npc.gui.edit.data.NpcConfig;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@Getter
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class NPC implements INBTSerializable<CompoundTag> {
    public static final String SUFFIX = ".npc";
    @Nullable
    @Setter
    private String path;
    public NpcConfig npcConfig;

    public NPC() {
        npcConfig = new NpcConfig();
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return npcConfig.serializeNBT(provider);
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        npcConfig.deserializeNBT(provider, tag);
    }
}

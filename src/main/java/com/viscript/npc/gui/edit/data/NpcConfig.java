package com.viscript.npc.gui.edit.data;

import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.viscript.npc.ViScriptNpcRegistries;
import com.viscript.npc.npc.data.INpcData;
import com.viscript.npc.util.common.StrUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

public class NpcConfig implements INpcData {
    final Set<INpcData> npcData = new LinkedHashSet<>();

    {
        for (var holder : ViScriptNpcRegistries.NPC_DATA) {
            npcData.add(holder.value().get());
        }
    }

    public <T extends INpcData> T getNpcData(Class<T> npcDataClass) {
        for (INpcData npcData : this.npcData) {
            if (npcDataClass.isInstance(npcData)) return npcDataClass.cast(npcData);
        }
        return null;
    }

    @Override
    public String getConfigurableName() {
        return "npcConfig";
    }

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        INpcData.super.buildConfigurator(father);
        for (INpcData npcData : this.npcData) {
            String name = "npcConfig." + StrUtil.toCamelCase(npcData.getConfigurableName());
            ConfiguratorGroup newGroup = new ConfiguratorGroup(name, true);
            npcData.buildConfigurator(newGroup);
            father.addConfigurators(newGroup);
        }
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.@NotNull Provider provider) {
        CompoundTag tag = INpcData.super.serializeNBT(provider);
        for (INpcData npcData : this.npcData) {
            tag.put(npcData.getConfigurableName(), npcData.serializeNBT(provider));
        }
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.@NotNull Provider provider, @NotNull CompoundTag tag) {
        INpcData.super.deserializeNBT(provider, tag);
        for (INpcData npcData : this.npcData) npcData.deserializeNBT(provider, tag);
    }
}

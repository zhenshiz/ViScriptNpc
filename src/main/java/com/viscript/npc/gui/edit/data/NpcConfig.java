package com.viscript.npc.gui.edit.data;

import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.viscript.npc.gui.edit.npc.NpcObject;
import com.viscript.npc.npc.data.INpcData;
import com.viscript.npc.util.common.StrUtil;
import lombok.EqualsAndHashCode;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
public class NpcConfig extends NpcObject implements INpcData {
    public static final Set<Class<? extends INpcData>> NPC_DATA_CLASSES = new LinkedHashSet<>();

    private final Set<INpcData> npcData = new LinkedHashSet<>();
    {
        for (Class<? extends INpcData> npcDataClass : NPC_DATA_CLASSES) {
            try {
                npcData.add(npcDataClass.getDeclaredConstructor().newInstance());
            } catch (Exception ignored) {}
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
        super.buildConfigurator(father);
        for (INpcData npcData : this.npcData) {
            String name = "npcConfig." + StrUtil.toCamelCase(npcData.getConfigurableName());
            ConfiguratorGroup newGroup = new ConfiguratorGroup(name, true);
            newGroup.setCanCollapse(true);
            npcData.buildConfigurator(newGroup);
            father.addConfigurators(newGroup);
        }
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.@NotNull Provider provider) {
        CompoundTag tag = super.serializeNBT(provider);
        for (INpcData npcData : this.npcData) {
            tag.put(StrUtil.toCamelCase(npcData.getConfigurableName()), npcData.serializeNBT(provider));
        }
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.@NotNull Provider provider, @NotNull CompoundTag tag) {
        super.deserializeNBT(provider, tag);
        for (INpcData npcData : this.npcData) {
            if (tag.contains(npcData.getConfigurableName())) {
                npcData.deserializeNBT(provider, tag.getCompound(StrUtil.toCamelCase(npcData.getConfigurableName())));
            }
        }
    }
}

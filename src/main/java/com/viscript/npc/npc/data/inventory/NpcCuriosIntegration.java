package com.viscript.npc.npc.data.inventory;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigList;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.viscript.npc.compat.curios.NpcCuriosEditorCompat;
import com.viscript.npc.gui.edit.page.NpcEditorPageIds;
import com.viscript.npc.npc.data.INpcData;
import lombok.Data;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Data
@LDLRegister(name = "npc_curios_integration", registry = INpcData.ID, modID = "curios", priority = -10)
public class NpcCuriosIntegration implements INpcData {
    @Configurable(name = "npcConfig.npcCuriosIntegration.curios")
    @ConfigList(configuratorMethod = "createCurioStackConfigurator", addDefaultMethod = "createDefaultCurioStack")
    private List<ItemStack> curios = new ArrayList<>();

    @Override
    public ResourceLocation getEditorPage() {
        return NpcEditorPageIds.INVENTORY;
    }

    private ItemStack createDefaultCurioStack() {
        return ItemStack.EMPTY;
    }

    private Configurator createCurioStackConfigurator(Supplier<ItemStack> getter, Consumer<ItemStack> setter) {
        return NpcCuriosEditorCompat.createCurioStackConfigurator(getter, setter);
    }
}

package com.viscript.npc.npc.data.inventory;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.viscript.npc.util.ConfiguratorUtil;
import lombok.Data;
import net.minecraft.world.item.Items;

@Data
public class LootTableConfig implements IConfigurable, IPersistedSerializable {
    @Persisted
    private String item = Items.AIR.toString();
    @Configurable(name = "npcConfig.npcInventory.LootTableConfig.name")
    private String name = "";
    @Configurable(name = "npcConfig.npcInventory.LootTableConfig.count")
    private int count = 0;
    @Configurable(name = "npcConfig.npcInventory.LootTableConfig.chance")
    @ConfigNumber(range = {0, 1}, wheel = 0.1)
    private float chance = 0;

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        father.addConfigurator(ConfiguratorUtil.createItemSearchComponentConfigurator("npcConfig.npcInventory.LootTableConfig.item",
                this::getItem,
                this::setItem
        ));
        IConfigurable.super.buildConfigurator(father);
    }
}

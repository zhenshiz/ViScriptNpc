package com.viscript.npc.npc.data.inventory;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.viscript.npc.npc.data.INpcData;
import lombok.Data;
import net.minecraft.world.item.ItemStack;

@Data
public class LootTableConfig implements INpcData {
    @Configurable(name = "npcConfig.npcInventory.LootTableConfig.item")
    private ItemStack item = ItemStack.EMPTY;
    @Configurable(name = "npcConfig.npcInventory.LootTableConfig.chance")
    @ConfigNumber(range = {0, 1}, wheel = 0.1)
    private float chance = 0;
}

package com.viscript.npc.npc.data.attributes;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import lombok.Data;

@Data
public class DefenseConfig implements IConfigurable, IPersistedSerializable {
    @Configurable(name = "npcConfig.npcAttributes.defenseConfig.armor")
    @ConfigNumber(range = {0, 30}, wheel = 0.1)
    private float armor = 0;
    @Configurable(name = "npcConfig.npcAttributes.defenseConfig.armorToughness")
    @ConfigNumber(range = {0, 20}, wheel = 0.1)
    private float armorToughness = 0;
    @Configurable(name = "npcConfig.npcAttributes.defenseConfig.reboundDamage")
    @ConfigNumber(range = {0, Integer.MAX_VALUE}, wheel = 0.1)
    private float reboundDamage = 0;
}

package com.viscript.npc.npc.data.attributes;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import lombok.Data;

@Data
public class ResistanceConfig implements IConfigurable, IPersistedSerializable {
    @Configurable(name = "npcConfig.npcAttributes.resistanceConfig.knockback")
    @ConfigNumber(range = {0, 1}, wheel = 0.01)
    private double knockback = 0;
    @Configurable(name = "npcConfig.npcAttributes.resistanceConfig.projectile")
    @ConfigNumber(range = {0, 1}, wheel = 0.01)
    private float projectile = 0;
    @Configurable(name = "npcConfig.npcAttributes.resistanceConfig.explosion")
    @ConfigNumber(range = {0, 1}, wheel = 0.01)
    private float explosion = 0;
    @Configurable(name = "npcConfig.npcAttributes.resistanceConfig.melee")
    @ConfigNumber(range = {0, 1}, wheel = 0.01)
    private float melee = 0;
}

package com.viscript.npc.npc.data.dynamic.model;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import lombok.Data;

@Data
public class ModelPartConfig implements IConfigurable, IPersistedSerializable {
    @Configurable(name = "npcConfig.npcDynamicModel.modelPartConfig.xScale")
    @ConfigNumber(range = {0, Float.MAX_VALUE})
    private float xScale = 1.0f;
    @Configurable(name = "npcConfig.npcDynamicModel.modelPartConfig.yScale")
    @ConfigNumber(range = {0, Float.MAX_VALUE})
    private float yScale = 1.0f;
    @Configurable(name = "npcConfig.npcDynamicModel.modelPartConfig.zScale")
    @ConfigNumber(range = {0, Float.MAX_VALUE})
    private float zScale = 1.0f;
    @Configurable(name = "npcConfig.npcDynamicModel.modelPartConfig.visible")
    private boolean visible = true;
}

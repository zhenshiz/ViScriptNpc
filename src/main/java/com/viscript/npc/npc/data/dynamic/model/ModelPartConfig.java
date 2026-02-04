package com.viscript.npc.npc.data.dynamic.model;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.viscript.npc.npc.data.INpcData;
import lombok.Data;

@Data
public class ModelPartConfig implements INpcData {
    @Configurable
    @ConfigNumber(range = {-1145, 1145})
    public float x = 0;
    @Configurable
    @ConfigNumber(range = {-1145, 1145})
    public float y = 0;
    @Configurable
    @ConfigNumber(range = {-1145, 1145})
    public float z = 0;
    @Configurable
    @ConfigNumber(range = {-314, 314})
    public float xRot = 0;
    @Configurable
    @ConfigNumber(range = {-314, 314})
    public float yRot = 0;
    @Configurable
    @ConfigNumber(range = {-314, 314})
    public float zRot = 0;
    @Configurable(name = "npcConfig.npcDynamicModel.modelPartConfig.xScale")
    @ConfigNumber(range = {0, Float.MAX_VALUE})
    public float xScale = 1.0f;
    @Configurable(name = "npcConfig.npcDynamicModel.modelPartConfig.yScale")
    @ConfigNumber(range = {0, Float.MAX_VALUE})
    public float yScale = 1.0f;
    @Configurable(name = "npcConfig.npcDynamicModel.modelPartConfig.zScale")
    @ConfigNumber(range = {0, Float.MAX_VALUE})
    public float zScale = 1.0f;
    @Configurable(name = "npcConfig.npcDynamicModel.modelPartConfig.visible")
    public boolean visible = true;
}

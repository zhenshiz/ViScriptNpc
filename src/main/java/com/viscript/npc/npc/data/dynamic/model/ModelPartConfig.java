package com.viscript.npc.npc.data.dynamic.model;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.viscript.npc.npc.data.INpcData;
import lombok.Data;

@Data
public class ModelPartConfig implements INpcData {
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

    public static class GeoBoneConfig {
        public float scaleX = 1f;
        public float scaleY = 1f;
        public float scaleZ = 1f;
        public boolean hidden = false;
    }

    public GeoBoneConfig transform() {
        GeoBoneConfig geoBoneConfig = new GeoBoneConfig();
        geoBoneConfig.scaleX = this.xScale;
        geoBoneConfig.scaleY = this.yScale;
        geoBoneConfig.scaleZ = this.zScale;
        geoBoneConfig.hidden = !this.visible;
        return geoBoneConfig;
    }
}

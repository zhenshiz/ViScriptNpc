package com.viscript.npc.gui.edit.data;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.viscript.npc.gui.edit.npc.NpcObject;
import com.viscript.npc.npc.data.attributes.NpcAttributes;
import com.viscript.npc.npc.data.basics.setting.NpcBasicsSetting;
import com.viscript.npc.npc.data.dynamic.model.NpcDynamicModel;
import com.viscript.npc.npc.data.inventory.NpcInventory;
import com.viscript.npc.npc.data.mod.integrations.NpcModIntegrations;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class NpcConfig extends NpcObject implements IConfigurable, IPersistedSerializable {
    @Configurable(name = "npcConfig.npcBasicsSetting", subConfigurable = true)
    public final NpcBasicsSetting npcBasicsSetting = new NpcBasicsSetting();
    @Configurable(name = "npcConfig.npcDynamicModel", subConfigurable = true)
    public final NpcDynamicModel npcDynamicModel = new NpcDynamicModel();
    @Configurable(name = "npcConfig.npcAttributes", subConfigurable = true)
    public final NpcAttributes npcAttributes = new NpcAttributes();
    @Configurable(name = "npcConfig.npcInventory", subConfigurable = true)
    public final NpcInventory npcInventory = new NpcInventory();
    @Configurable(name = "npcConfig.npcModIntegrations", subConfigurable = true)
    public final NpcModIntegrations npcModIntegrations = new NpcModIntegrations();

    @Override
    public String getConfigurableName() {
        return "npcConfig";
    }
}

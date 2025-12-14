package com.viscript.npc.plugin;

import com.viscript.npc.npc.NpcAttachmentType;
import com.viscript.npc.npc.data.attributes.NpcAttributes;
import com.viscript.npc.npc.data.basics.setting.NpcBasicsSetting;
import com.viscript.npc.npc.data.dynamic.model.NpcDynamicModel;
import com.viscript.npc.npc.data.inventory.NpcInventory;
import com.viscript.npc.npc.data.mod.integrations.NpcModIntegrations;

@ViScriptNpcPlugin
public class NpcPlugin implements IViScriptNpcPlugin {
    @Override
    public void init() {
    }

    @Override
    public void registerNpc(RegisterNpcEvent event) {
        event.registerNpcAttachment(NpcBasicsSetting.class, NpcAttachmentType.NPC_BASICS_SETTING.get());
        event.registerNpcAttachment(NpcDynamicModel.class, NpcAttachmentType.NPC_DYNAMIC_MODEL.get());
        event.registerNpcAttachment(NpcAttributes.class, NpcAttachmentType.NPC_ATTRIBUTES.get());
        event.registerNpcAttachment(NpcInventory.class, NpcAttachmentType.NPC_INVENTORY.get());
        event.registerNpcAttachment(NpcModIntegrations.class, NpcAttachmentType.NPC_MOD_INTEGRATIONS.get());
    }
}

package com.viscript.npc.npc.data.mod.integrations;

import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.viscript.npc.npc.data.INpcData;
import lombok.Data;

//关于联动模组的配置
@Data
@LDLRegister(name = "npc_mod_integrations", registry = INpcData.ID)
public class NpcModIntegrations implements INpcData {
}

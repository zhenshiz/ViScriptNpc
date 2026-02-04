package com.viscript.npc.npc.data.basics.setting;

import com.viscript.npc.npc.data.dynamic.model.NpcDynamicModel;

public interface INpcRenderer {

    int getSkinColor();

    void setSkinColor(int color);

    void setDynamicModel(NpcDynamicModel dynamicModel);

    NpcDynamicModel getDynamicModel();
}

package com.viscript.npc.npc.data.basics_setting;

import com.viscript.npc.npc.data.model.NpcDynamicModel;

public interface INpcRenderer {

    int getSkinColor();

    void setSkinColor(int color);

    void setDynamicModel(NpcDynamicModel dynamicModel);

    NpcDynamicModel getDynamicModel();
}

package com.viscript.npc.npc.data.ai;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.viscript.npc.gui.edit.page.NpcEditorPageIds;
import com.viscript.npc.npc.data.INpcData;
import lombok.Data;
import net.minecraft.resources.ResourceLocation;
import org.thexeler.api.MindMachineConfig;
import org.thexeler.api.navigation.NavigationRegistry;

import java.util.ArrayList;
import java.util.List;

@Data
@LDLRegister(name = "npc_ai", registry = INpcData.ID)
public class NpcAI implements INpcData {
    @Configurable(name = "npcConfig.npcAI.enabled")
    @Persisted
    private boolean enabled = true;

    @Configurable(name = "npcConfig.npcAI.tickRate", tips = "npcConfig.npcAI.tickRate.tips")
    @ConfigNumber(range = {1, 100}, type = ConfigNumber.Type.INTEGER)
    @Persisted
    private int tickRate = 8;

    @Configurable(name = "npcConfig.npcAI.navigation")
    @Persisted
    private ResourceLocation navigation = NavigationRegistry.defaultStrategy().getId();

    @Configurable(name = "npcConfig.npcAI.intentions", subConfigurable = true)
    @Persisted
    private List<IntentionEntry> intentions = new ArrayList<>();

    @Override
    public ResourceLocation getEditorPage() {
        return NpcEditorPageIds.AI;
    }

    public MindMachineConfig toConfig() {
        return new MindMachineConfig(tickRate, navigation);
    }
}

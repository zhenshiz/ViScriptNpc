package com.viscript.npc.npc.data.mod.integrations;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.SearchComponentConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.util.ConfiguratorUtil;
import com.zhenshiz.chatbox.data.ChatBoxDialoguesLoader;
import lombok.Data;
import net.minecraft.resources.ResourceLocation;

import java.util.stream.Collectors;

@Data
public class ChatBoxConfig implements IConfigurable, IPersistedSerializable {
    @Persisted
    private boolean enabled = false;
    @Persisted
    private String dialogResourceLocation = "";
    @Persisted
    private String group = "";
    @Persisted
    private Number index = 0;

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        IConfigurable.super.buildConfigurator(father);
        if (ViScriptNpc.isChatBoxLoaded()) {
            father.addConfigurator(createEnabledConfigurator(father));
        }
    }

    public BooleanConfigurator createEnabledConfigurator(ConfiguratorGroup father) {
        BooleanConfigurator booleanConfigurator = new BooleanConfigurator("npcConfig.npcModIntegrations.chatBoxConfig.enabled",
                this::isEnabled,
                this::setEnabled,
                this.enabled,
                true
        );
        booleanConfigurator.addEventListener(UIEvents.CLICK, event -> {
            if (this.enabled) {
                father.addConfigurators(
                        createDialogResourceLocationSearch(),
                        createGroupSearch(),
                        new NumberConfigurator("npcConfig.npcModIntegrations.chatBoxConfig.index", this::getIndex, this::setIndex, index, true)
                                .setRange(0, Integer.MAX_VALUE)
                                .setWheel(1));
            } else {
                father.removeAllConfigurators();
                father.addConfigurator(booleanConfigurator);
            }
        });
        return booleanConfigurator;
    }

    public SearchComponentConfigurator<String> createDialogResourceLocationSearch() {
        return ConfiguratorUtil.createStrArrSearchComponentConfigurator("npcConfig.npcModIntegrations.chatBoxConfig.dialogResourceLocation",
                ChatBoxDialoguesLoader.dialoguesGroupMap.keySet().stream().map(ResourceLocation::toString).collect(Collectors.toSet()),
                this::getDialogResourceLocation,
                this::setDialogResourceLocation
        );
    }

    public SearchComponentConfigurator<String> createGroupSearch() {
        return ConfiguratorUtil.createStrArrSearchComponentConfigurator("npcConfig.npcModIntegrations.chatBoxConfig.group",
                ChatBoxDialoguesLoader.dialoguesGroupMap.get(ResourceLocation.parse(this.dialogResourceLocation)),
                this::getGroup,
                this::setGroup
        );
    }
}

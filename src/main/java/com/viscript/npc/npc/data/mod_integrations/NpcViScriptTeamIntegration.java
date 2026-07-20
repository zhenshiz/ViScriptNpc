package com.viscript.npc.npc.data.mod_integrations;

import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.compat.team.NpcFactionBridge;
import com.viscript.npc.gui.edit.page.NpcEditorPageIds;
import com.viscript.npc.network.c2s.C2SPayload;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.data.INpcData;
import com.viscript.npc.util.ConfiguratorUtil;
import lombok.Data;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

@Data
@LDLRegister(name = "npc_viscript_team_integration", registry = INpcData.ID, modID = "viscript_team", priority = -10)
public class NpcViScriptTeamIntegration implements INpcData {
    private static final String LEGACY_BASICS_SETTING = "npc_basics_setting";

    @Persisted
    private String factionId = "";

    @Override
    public ResourceLocation getEditorPage() {
        return NpcEditorPageIds.INTEGRATIONS;
    }

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        RPCPacketDistributor.rpcToServer(C2SPayload.REQUEST_FACTION_IDS);
        father.addConfigurator(ConfiguratorUtil.createStrArrSearchComponentConfigurator(
                "npcConfig.npcViScriptTeamIntegration.factionId",
                () -> CustomNpc.factionIds,
                this::getFactionId,
                this::setFactionId
        ));
    }

    public void setFactionId(String factionId) {
        this.factionId = NpcFactionBridge.normalizeFactionId(factionId);
    }

    @Override
    public void deserializeNBT(HolderLookup.@NotNull Provider provider, @NotNull CompoundTag tag) {
        boolean hasCurrentFactionId = unwrapBranch(tag, getConfigurableName()).contains("factionId");
        INpcData.super.deserializeNBT(provider, tag);
        if (!hasCurrentFactionId && factionId.isBlank()) {
            setFactionId(unwrapBranch(tag, LEGACY_BASICS_SETTING).getString("factionId"));
        }
    }

    private CompoundTag unwrapBranch(CompoundTag tag, String name) {
        CompoundTag branch;
        if (tag.contains("neoforge:attachments")) {
            branch = tag.getCompound("neoforge:attachments").getCompound(ViScriptNpc.id(name).toString());
        } else if (tag.contains(name)) {
            branch = tag.getCompound(name);
        } else {
            branch = tag;
        }
        return branch.contains("data") ? branch.getCompound("data") : branch;
    }
}

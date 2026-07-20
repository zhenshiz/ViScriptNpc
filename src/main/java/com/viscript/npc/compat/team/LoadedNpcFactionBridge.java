package com.viscript.npc.compat.team;

import com.viscript.npc.npc.CustomNpc;
import com.viscript_team.util.FactionApi;
import com.viscript_team.util.ViScriptTeamServerUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;

final class LoadedNpcFactionBridge {
    private LoadedNpcFactionBridge() {
    }

    static void applyConfiguredFaction(CustomNpc npc) {
        String factionId = NpcFactionBridge.normalizeFactionId(npc.getNpcViScriptTeamIntegration().getFactionId());
        if (factionId.isBlank()) {
            ViScriptTeamServerUtil.clearEntityFaction(npc);
            return;
        }
        FactionApi.data(npc.level()).ifPresent(data -> data.getOrCreateFaction(factionId));
        FactionApi.setEntityFaction(npc, factionId);
    }

    static boolean shouldActivelyTarget(CustomNpc npc, LivingEntity target) {
        return FactionApi.shouldActivelyTarget(npc, target);
    }

    static boolean canHurt(LivingEntity attacker, LivingEntity target) {
        return FactionApi.canHurt(attacker, target);
    }

    static boolean canHurtFromSource(DamageSource source, LivingEntity target) {
        return FactionApi.canHurtFromSource(source, target);
    }

    static Set<String> getFactionIds(ServerLevel level) {
        return Set.copyOf(ViScriptTeamServerUtil.getFactionIds(level));
    }
}

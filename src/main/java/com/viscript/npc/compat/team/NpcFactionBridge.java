package com.viscript.npc.compat.team;

import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.CustomNpc;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public final class NpcFactionBridge {
    private NpcFactionBridge() {
    }

    public static void applyConfiguredFaction(CustomNpc npc) {
        if (!ViScriptNpc.isViScriptTeamLoaded() || npc == null || npc.level().isClientSide) {
            return;
        }
        LoadedNpcFactionBridge.applyConfiguredFaction(npc);
    }

    public static boolean shouldActivelyTarget(CustomNpc npc, @Nullable LivingEntity target) {
        return ViScriptNpc.isViScriptTeamLoaded()
                && npc != null
                && target != null
                && LoadedNpcFactionBridge.shouldActivelyTarget(npc, target);
    }

    public static boolean hasConfiguredFaction(CustomNpc npc) {
        return ViScriptNpc.isViScriptTeamLoaded()
                && npc != null
                && !normalizeFactionId(npc.getNpcViScriptTeamIntegration().getFactionId()).isBlank();
    }

    public static boolean canHurt(LivingEntity attacker, @Nullable LivingEntity target) {
        return !ViScriptNpc.isViScriptTeamLoaded()
                || (attacker != null && target != null && LoadedNpcFactionBridge.canHurt(attacker, target));
    }

    public static boolean canHurtFromSource(DamageSource source, LivingEntity target) {
        return !ViScriptNpc.isViScriptTeamLoaded()
                || LoadedNpcFactionBridge.canHurtFromSource(source, target);
    }

    public static Set<String> getFactionIds(@Nullable ServerLevel level) {
        if (!ViScriptNpc.isViScriptTeamLoaded() || level == null) {
            return Set.of();
        }
        return LoadedNpcFactionBridge.getFactionIds(level);
    }

    public static String normalizeFactionId(@Nullable String factionId) {
        return factionId == null ? "" : factionId.trim();
    }
}

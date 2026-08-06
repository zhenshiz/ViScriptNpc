package com.viscript.npc.npc.ai.flow;

import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.thexeler.AttentionMind;

import java.util.LinkedHashSet;
import java.util.Set;

@EventBusSubscriber(modid = ViScriptNpc.MOD_ID)
public final class NpcAiReloadEvents {
    private NpcAiReloadEvents() {}

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ResourceManagerReloadListener() {
            @Override
            public void onResourceManagerReload(ResourceManager resourceManager) {
                var server = ServerLifecycleHooks.getCurrentServer();
                try {
                    Set<net.minecraft.resources.ResourceLocation> changed =
                            NpcFlowTemplateRegistry.getInstance().reload(resourceManager);
                    if (server != null) {
                        var amReload = AttentionMind.intentionAssets().lastReloadResult();
                        if (!amReload.isSuccess()) {
                            String detail = amReload.diagnostics().stream()
                                    .map(value -> value.stableCode() + ": " + value.message())
                                    .reduce((left, right) -> left + "; " + right)
                                    .orElse("Attention Mind asset reload failed");
                            NpcAiDependencyIndex.invalidateAll(server, detail);
                        } else {
                            boolean recovered = NpcAiDependencyIndex.recoverAll(server);
                            if (!recovered && !changed.isEmpty()) NpcAiDependencyIndex.resetAffected(server, changed);
                        }
                        ViScriptNpcServerUtil.broadcastNpcAiDescriptors(server);
                    }
                } catch (RuntimeException exception) {
                    ViScriptNpc.LOGGER.error("NPC AI template reload failed", exception);
                    if (server != null) {
                        NpcAiDependencyIndex.invalidateAll(server, "NPC AI template reload failed: "
                                + (exception.getMessage() == null ? exception.getClass().getSimpleName()
                                : exception.getMessage()));
                    }
                }
            }
        });
    }

    private static AutoCloseable assetListener;

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        if (assetListener != null) return;
        assetListener = AttentionMind.intentionAssets().addListener(changeSet -> {
            Set<net.minecraft.resources.ResourceLocation> changed = new LinkedHashSet<>(changeSet.added());
            changed.addAll(changeSet.removed());
            changed.addAll(changeSet.directlyChanged());
            changed.addAll(changeSet.transitivelyAffected());
            changed.addAll(NpcFlowTemplateRegistry.getInstance().refreshResolvedHashes());
            boolean recovered = NpcAiDependencyIndex.recoverAll(event.getServer());
            if (!recovered) NpcAiDependencyIndex.resetAffected(event.getServer(), changed);
            ViScriptNpcServerUtil.broadcastNpcAiDescriptors(event.getServer());
        });
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        NpcAiDependencyIndex.clear();
        if (assetListener != null) {
            try { assetListener.close(); } catch (Exception ignored) {}
            assetListener = null;
        }
    }
}

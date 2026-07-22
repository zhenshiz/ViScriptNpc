package com.viscript.npc;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.factory.PlayerUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.utils.ReflectionUtils;
import com.mojang.logging.LogUtils;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.npc.NpcAttachmentType;
import com.viscript.npc.npc.NpcRegister;
import com.viscript.npc.npc.data.ai.runtime.NpcBehaviorDataSerializers;
import com.viscript.npc.plugin.IViScriptNpcPlugin;
import com.viscript.npc.plugin.ViScriptNpcPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

import java.util.function.Consumer;

@Mod(ViScriptNpc.MOD_ID)
public class ViScriptNpc {
    public static final String MOD_ID = "viscript_npc";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ViScriptNpc(IEventBus modEventBus, ModContainer modContainer, Dist dist) {
        NpcBehaviorDataSerializers.register();
        NpcRegister.ENTITY_TYPES.register(modEventBus);
        NpcAttachmentType.ATTACHMENT_TYPES.register(modEventBus);
        PlayerUIMenuType.register(NpcEditor.EDITOR_ID, ignored -> player -> {
            if (player.level().isClientSide) {
                ModularUI modularUI = new ModularUI(UI.of(EditorWindow.open(NpcEditor.EDITOR_ID, NpcEditor::new)))
                        .shouldCloseOnKeyInventory(false);
                if (!Platform.isDevEnv()) {
                    modularUI.shouldCloseOnEsc(false);
                }
                return modularUI;
            }
            return new ModularUI(UI.empty());
        });
        executePluginMethod(IViScriptNpcPlugin::init);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static String formattedMod(String path) {
        return ("%s:" + path).formatted(MOD_ID);
    }

    public static boolean isPresentResource(ResourceLocation resourceLocation) {
        return Minecraft.getInstance().getResourceManager().getResource(resourceLocation).isPresent();
    }

    public static boolean isChatBoxLoaded() {
        return isModLoaded("chatbox");
    }

    public static boolean isCuriosLoaded() {
        return isModLoaded("curios");
    }

    public static boolean isViScriptTeamLoaded() {
        return isModLoaded("viscript_team");
    }

    private static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    public static void executePluginMethod(Consumer<IViScriptNpcPlugin> consumer) {
        ReflectionUtils.findAnnotationClasses(ViScriptNpcPlugin.class, data -> true, clazz -> {
            try {
                if (clazz.getConstructor().newInstance() instanceof IViScriptNpcPlugin plugin) {
                    consumer.accept(plugin);
                }
            } catch (Throwable throwable) {
                LOGGER.error("Failed to load plugin {}", clazz.getName(), throwable);
            }
        }, () -> {
        });
    }
}

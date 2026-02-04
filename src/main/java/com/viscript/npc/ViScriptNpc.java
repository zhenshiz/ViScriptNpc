package com.viscript.npc;

import com.lowdragmc.lowdraglib2.utils.ReflectionUtils;
import com.mojang.logging.LogUtils;
import com.viscript.npc.npc.NpcAttachmentType;
import com.viscript.npc.npc.NpcRegister;
import com.viscript.npc.plugin.IViScriptNpcPlugin;
import com.viscript.npc.plugin.ViScriptNpcPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import java.util.function.Consumer;

@Mod(ViScriptNpc.MOD_ID)
public class ViScriptNpc {
    public static final String MOD_ID = "viscript_npc";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ViScriptNpc(IEventBus modEventBus, ModContainer modContainer, Dist dist) {
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NpcRegister.ENTITY_TYPES.register(modEventBus);
        NpcAttachmentType.ATTACHMENT_TYPES.register(modEventBus);
        executePluginMethod(IViScriptNpcPlugin::init);
    }

    //注册指令
    private void onRegisterCommands(RegisterCommandsEvent event) {
        for (var command : ViScriptNpcRegistries.COMMANDS) {
            command.value().get().register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection());
        }
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

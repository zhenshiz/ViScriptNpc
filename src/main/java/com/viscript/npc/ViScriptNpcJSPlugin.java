package com.viscript.npc;

import com.viscript.npc.event.CommonEventsPostJS;
import com.viscript.npc.event.ViScriptNpcEventJS;
import com.viscript.npc.util.ViScriptNpcClientUtil;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.neoforged.neoforge.common.NeoForge;

public class ViScriptNpcJSPlugin implements KubeJSPlugin {

    @Override
    public void init() {
        NeoForge.EVENT_BUS.register(CommonEventsPostJS.class);
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(ViScriptNpcEventJS.NPC_EVENT);
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        ScriptType type = bindings.type();
        if (type.equals(ScriptType.CLIENT)) {
            bindings.add("ViScriptNpcUtil", ViScriptNpcClientUtil.class);
        } else if (type.equals(ScriptType.SERVER)) {
            bindings.add("ViScriptNpcUtil", ViScriptNpcServerUtil.class);
        }
    }
}

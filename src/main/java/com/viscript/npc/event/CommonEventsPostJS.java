package com.viscript.npc.event;

import com.viscript.npc.event.kubejs.NpcEventJS;
import com.viscript.npc.event.neoforge.NpcEvent;
import dev.latvian.mods.kubejs.event.EventResult;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

public class CommonEventsPostJS {

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onNpcSpawn(NpcEvent.Spawn event) {
        if (ViScriptNpcEventJS.SPAWN.hasListeners()) {
            String type = event.getNpc().getNpcType();

            if (ViScriptNpcEventJS.SPAWN.hasListeners(type)) {
                EventResult result = ViScriptNpcEventJS.SPAWN.post(ScriptType.SERVER, type, new NpcEventJS.Spawn(event));
                if (result.interruptFalse()) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onNpcDeath(NpcEvent.Death event) {
        if (ViScriptNpcEventJS.DEATH.hasListeners()) {
            String type = event.getNpc().getNpcType();

            if (ViScriptNpcEventJS.DEATH.hasListeners(type)) {
                EventResult result = ViScriptNpcEventJS.DEATH.post(ScriptType.SERVER, type, new NpcEventJS.Death(event));
                if (result.interruptFalse()) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onNpcInteract(NpcEvent.Interact event) {
        if (ViScriptNpcEventJS.INTERACT.hasListeners()) {
            String type = event.getNpc().getNpcType();

            if (ViScriptNpcEventJS.INTERACT.hasListeners(type)) {
                EventResult result = ViScriptNpcEventJS.INTERACT.post(ScriptType.SERVER, type, new NpcEventJS.Interact(event));
                if (result.interruptFalse()) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onNpcHurt(NpcEvent.Hurt event) {
        if (ViScriptNpcEventJS.HURT.hasListeners()) {
            String type = event.getNpc().getNpcType();

            if (ViScriptNpcEventJS.HURT.hasListeners(type)) {
                EventResult result = ViScriptNpcEventJS.HURT.post(ScriptType.SERVER, type, new NpcEventJS.Hurt(event));
                if (result.interruptFalse()) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onNpcTick(NpcEvent.Tick event) {
        if (ViScriptNpcEventJS.TICK.hasListeners()) {
            String type = event.getNpc().getNpcType();

            if (ViScriptNpcEventJS.TICK.hasListeners(type)) {
                ViScriptNpcEventJS.TICK.post(ScriptType.SERVER, type, new NpcEventJS.Tick(event));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onNpcKilled(NpcEvent.Killed event) {
        if (ViScriptNpcEventJS.KILLED.hasListeners()) {
            String type = event.getNpc().getNpcType();

            if (ViScriptNpcEventJS.KILLED.hasListeners(type)) {
                EventResult result = ViScriptNpcEventJS.KILLED.post(ScriptType.SERVER, type, new NpcEventJS.Killed(event));
                if (result.interruptFalse()) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onNpcAttack(NpcEvent.Attack event) {
        if (ViScriptNpcEventJS.ATTACK.hasListeners()) {
            String type = event.getNpc().getNpcType();

            if (ViScriptNpcEventJS.ATTACK.hasListeners(type)) {
                EventResult result = ViScriptNpcEventJS.ATTACK.post(ScriptType.SERVER, type, new NpcEventJS.Attack(event));
                if (result.interruptFalse()) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onNpcTarget(NpcEvent.TargetEvent event) {
        if (ViScriptNpcEventJS.TARGET.hasListeners()) {
            String type = event.getNpc().getNpcType();

            if (ViScriptNpcEventJS.TARGET.hasListeners(type)) {
                EventResult result = ViScriptNpcEventJS.TARGET.post(ScriptType.SERVER, type, new NpcEventJS.TargetEvent(event));
                if (result.interruptFalse()) {
                    event.setCanceled(true);
                }
            }
        }
    }
}
package com.viscript.npc.event;

import com.viscript.npc.event.kubejs.NpcEventJS;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventTargetType;
import dev.latvian.mods.kubejs.event.TargetedEventHandler;

public interface ViScriptNpcEventJS {
    EventGroup NPC_EVENT = EventGroup.of("ViScriptNpcEvents");

    EventTargetType<String> NPC_TYPE = EventTargetType.STRING;

    TargetedEventHandler<String> SPAWN = NPC_EVENT.server("spawn", () -> NpcEventJS.Spawn.class).hasResult().supportsTarget(NPC_TYPE);
    TargetedEventHandler<String> DEATH = NPC_EVENT.server("death", () -> NpcEventJS.Death.class).hasResult().supportsTarget(NPC_TYPE);
    TargetedEventHandler<String> INTERACT = NPC_EVENT.server("interact", () -> NpcEventJS.Interact.class).hasResult().supportsTarget(NPC_TYPE);
    TargetedEventHandler<String> HURT = NPC_EVENT.server("hurt", () -> NpcEventJS.Hurt.class).hasResult().supportsTarget(NPC_TYPE);
    TargetedEventHandler<String> TICK = NPC_EVENT.server("tick", () -> NpcEventJS.Tick.class).supportsTarget(NPC_TYPE);
    TargetedEventHandler<String> KILLED = NPC_EVENT.server("killed", () -> NpcEventJS.Killed.class).hasResult().supportsTarget(NPC_TYPE);
    TargetedEventHandler<String> ATTACK = NPC_EVENT.server("attack", () -> NpcEventJS.Attack.class).hasResult().supportsTarget(NPC_TYPE);
    TargetedEventHandler<String> TARGET = NPC_EVENT.server("target", () -> NpcEventJS.TargetEvent.class).hasResult().supportsTarget(NPC_TYPE);
}

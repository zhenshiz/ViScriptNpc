package com.viscript.npc.event.neoforge;

import com.viscript.npc.npc.CustomNpc;
import lombok.Getter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Npc事件(纯服务端事件)
 * 支持npc在创建，死亡，交互，受伤，tick，击杀，攻击，切换攻击对象时触发自定义内容
 */
@Getter
public abstract class NpcEvent extends Event {
    private final CustomNpc npc;
    private final ServerLevel level;
    private final MinecraftServer server;

    public NpcEvent(CustomNpc npc) {
        this.npc = npc;
        this.level = (ServerLevel) npc.level();
        this.server = npc.getServer();
    }

    public static class Spawn extends NpcEvent implements ICancellableEvent {
        public Spawn(CustomNpc npc) {
            super(npc);
        }
    }

    @Getter
    public static class Death extends NpcEvent implements ICancellableEvent {
        private final DamageSource source;

        public Death(CustomNpc npc, DamageSource source) {
            super(npc);
            this.source = source;
        }
    }

    @Getter
    public static class Interact extends NpcEvent implements ICancellableEvent {
        private final ServerPlayer player;

        public Interact(CustomNpc npc, ServerPlayer player) {
            super(npc);
            this.player = player;
        }
    }

    public static class Hurt extends NpcEvent implements ICancellableEvent {
        private final LivingIncomingDamageEvent event;
        @Getter
        private final DamageSource source;
        @Getter
        private final DamageContainer container;

        public Hurt(LivingIncomingDamageEvent event) {
            super((CustomNpc) event.getEntity());
            this.event = event;
            this.source = event.getSource();
            this.container = event.getContainer();
        }

        public float getAmount() {
            return event.getAmount();
        }

        public void setAmount(float newDamage) {
            event.setAmount(newDamage);
        }
    }

    public static class Tick extends NpcEvent {
        public Tick(CustomNpc npc) {
            super(npc);
        }
    }

    @Getter
    public static class Killed extends NpcEvent implements ICancellableEvent {
        private final Entity entity;
        private final DamageSource source;

        public Killed(CustomNpc npc, Entity entity, DamageSource source) {
            super(npc);
            this.entity = entity;
            this.source = source;
        }
    }

    public static class Attack extends NpcEvent implements ICancellableEvent {
        private final LivingIncomingDamageEvent event;
        @Getter
        private final Entity target;
        @Getter
        private final DamageSource source;
        @Getter
        private final DamageContainer container;

        public Attack(CustomNpc npc, LivingIncomingDamageEvent event) {
            super(npc);
            this.event = event;
            this.target = event.getEntity();
            this.source = event.getSource();
            this.container = event.getContainer();
        }

        public float getAmount() {
            return event.getAmount();
        }

        public void setAmount(float newDamage) {
            event.setAmount(newDamage);
        }
    }

    public static class TargetEvent extends NpcEvent implements ICancellableEvent {
        private final LivingChangeTargetEvent event;

        public TargetEvent(CustomNpc npc, LivingChangeTargetEvent event) {
            super(npc);
            this.event = event;
        }

        public LivingEntity getOriginalTarget() {
            return event.getOriginalAboutToBeSetTarget();
        }

        public LivingEntity getNewTarget() {
            return event.getNewAboutToBeSetTarget();
        }

        public void setNewTarget(LivingEntity newTarget) {
            event.setNewAboutToBeSetTarget(newTarget);
        }
    }
}

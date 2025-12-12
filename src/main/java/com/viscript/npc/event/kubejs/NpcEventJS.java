package com.viscript.npc.event.kubejs;

import com.viscript.npc.event.neoforge.NpcEvent;
import com.viscript.npc.npc.CustomNpc;
import dev.latvian.mods.kubejs.event.KubeEvent;
import lombok.Getter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;

@Getter
public class NpcEventJS implements KubeEvent {
    private final CustomNpc npc;
    private final ServerLevel level;
    private final MinecraftServer server;

    public NpcEventJS(NpcEvent event) {
        this.npc = event.getNpc();
        this.level = event.getLevel();
        this.server = event.getServer();
    }

    public static class Spawn extends NpcEventJS {
        public Spawn(NpcEvent.Spawn event) {
            super(event);
        }
    }

    @Getter
    public static class Death extends NpcEventJS {
        private final DamageSource source;

        public Death(NpcEvent.Death event) {
            super(event);
            this.source = event.getSource();
        }
    }

    @Getter
    public static class Interact extends NpcEventJS {
        private final ServerPlayer player;

        public Interact(NpcEvent.Interact event) {
            super(event);
            this.player = event.getPlayer();
        }
    }

    public static class Hurt extends NpcEventJS {
        private final NpcEvent.Hurt event;
        @Getter
        private final DamageSource source;
        @Getter
        private final DamageContainer container;

        public Hurt(NpcEvent.Hurt event) {
            super(event);
            this.event = event;
            this.source = event.getSource();
            this.container = event.getContainer();
        }

        public float getAmount() {
            return event.getAmount();
        }

        public void setAmount(float amount) {
            event.setAmount(amount);
        }
    }

    public static class Tick extends NpcEventJS {
        public Tick(NpcEvent.Tick event) {
            super(event);
        }
    }

    @Getter
    public static class Killed extends NpcEventJS {
        private final Entity entity;
        private final DamageSource source;

        public Killed(NpcEvent.Killed event) {
            super(event);
            this.entity = event.getEntity();
            this.source = event.getSource();
        }
    }

    public static class Attack extends NpcEventJS {
        private final NpcEvent.Attack event;
        @Getter
        private final Entity target;
        @Getter
        private final DamageSource source;
        @Getter
        private final DamageContainer container;

        public Attack(NpcEvent.Attack event) {
            super(event);
            this.event = event;
            this.target = event.getTarget();
            this.source = event.getSource();
            this.container = event.getContainer();
        }

        public float getAmount() {
            return event.getAmount();
        }

        public void setAmount(float amount) {
            event.setAmount(amount);
        }
    }

    public static class TargetEvent extends NpcEventJS {
        private final NpcEvent.TargetEvent event;

        public TargetEvent(NpcEvent.TargetEvent event) {
            super(event);
            this.event = event;
        }

        public LivingEntity getOriginalTarget() {
            return event.getOriginalTarget();
        }

        public LivingEntity getNewTarget() {
            return event.getNewTarget();
        }

        public void setNewTarget(LivingEntity target) {
            event.setNewTarget(target);
        }
    }
}
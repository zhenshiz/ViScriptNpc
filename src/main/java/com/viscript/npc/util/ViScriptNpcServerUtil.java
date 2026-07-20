package com.viscript.npc.util;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.gui.factory.PlayerUIMenuType;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.viscript.npc.gui.edit.NpcEditor;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.viscript.npc.network.s2c.S2CPayload;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.NpcRegister;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.thexeler.MindMachine;
import org.thexeler.api.IntentionFunctionRegistries;
import org.thexeler.api.IntentionPriority;
import org.thexeler.api.world.MindActor;
import org.thexeler.api.world.MindEntityActor;
import org.thexeler.api.world.MindPosition;
import org.thexeler.intention.BaseIntention;
import org.thexeler.intention.base.IdleIntention;
import org.thexeler.intention.base.MoveIntention;
import org.thexeler.intention.target.FollowIntention;
import org.thexeler.intention.target.MeleeAttackIntention;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class ViScriptNpcServerUtil {

    @Info("服务端打开NPC编辑器")
    public static void openNpcEditor(ServerPlayer player, CompoundTag tag) {
        PlayerUIMenuType.openUI(player, NpcEditor.EDITOR_ID);
        RPCPacketDistributor.rpcToPlayer(player, S2CPayload.OPEN_NPC_EDITOR, tag);
    }

    @Info("生成NPC")
    public static Entity summonNpc(CompoundTag tag, Vec3 pos) {
        try {
            return SummonCommand.createEntity(Platform.getMinecraftServer().createCommandSourceStack(), (Holder.Reference<EntityType<?>>) NpcRegister.CUSTOM_NPC.getDelegate(), pos, tag, false);
        } catch (CommandSyntaxException e) {
            return null;
        }
    }

    @Info("将 MC 实体包装成 MindActor")
    public static MindActor wrap(LivingEntity entity) {
        return MindEntityActor.wrap(entity);
    }

    @Info("获取 NPC 的 MindMachine（未启用或客户端可能为 null）")
    public static MindMachine getMind(CustomNpc npc) {
        return npc.getMind();
    }

    @Info("向 NPC 添加 Intention")
    public static void addIntention(CustomNpc npc, IntentionPriority priority, BaseIntention intention) {
        MindMachine mind = npc.getMind();
        if (mind != null) mind.addIntention(priority, intention);
    }

    @Info("注册可被 CustomIntention 引用的 execute 函数（返回 true 表示执行完毕）")
    public static void registerExecuteFunction(String id, Predicate<BaseIntention> function) {
        IntentionFunctionRegistries.registerExecuteFunction(net.minecraft.resources.ResourceLocation.parse(id), function);
    }

    @Info("注册可被 CustomIntention 引用的 hold 函数")
    public static void registerHoldFunction(String id, Consumer<BaseIntention> function) {
        IntentionFunctionRegistries.registerHoldFunction(net.minecraft.resources.ResourceLocation.parse(id), function);
    }

    @Info("构造 IdleIntention")
    public static IdleIntention idle(MindMachine machine, MindActor self) {
        return new IdleIntention(machine, self);
    }

    @Info("构造 MoveIntention")
    public static MoveIntention move(MindMachine machine, MindActor self, MindPosition pos) {
        return new MoveIntention(machine, self, pos);
    }

    @Info("构造 FollowIntention（默认距离 0 表示贴脸跟随）")
    public static FollowIntention follow(MindMachine machine, MindActor self, MindActor target) {
        return new FollowIntention(machine, self, target);
    }

    @Info("构造 FollowIntention 并指定保持距离")
    public static FollowIntention follow(MindMachine machine, MindActor self, MindActor target, double distance) {
        return new FollowIntention(machine, self, target, distance);
    }

    @Info("构造 MeleeAttackIntention")
    public static MeleeAttackIntention meleeAttack(MindMachine machine, MindActor self, MindActor target) {
        return new MeleeAttackIntention(machine, self, target);
    }
}

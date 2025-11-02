package com.viscript.npc.npc.data.dynamic.model;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.mojang.serialization.Codec;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.util.common.BeanUtil;
import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NpcDynamicModel implements IConfigurable, IPersistedSerializable {
    public static final StreamCodec<ByteBuf, NpcDynamicModel> STREAM_CODEC;
    public static final Codec<NpcDynamicModel> CODEC;

    //NPC代理的的生物类型，可以使用这个生物的模型和材质
    @Configurable(name = "npcConfig.npcDynamicModel.entityType", tips = "npcConfig.npcDynamicModel.entityType.tips")
    private ResourceLocation entityType = ResourceLocation.withDefaultNamespace("player");
    @Configurable(name = "npcConfig.npcDynamicModel.head", subConfigurable = true)
    private ModelPartConfig head = new ModelPartConfig();
    @Configurable(name = "npcConfig.npcDynamicModel.body", subConfigurable = true)
    private ModelPartConfig body = new ModelPartConfig();
    @Configurable(name = "npcConfig.npcDynamicModel.armNotShared")
    private boolean armNotShared = false;
    @Configurable(name = "npcConfig.npcDynamicModel.armL", subConfigurable = true)
    private ModelPartConfig armL = new ModelPartConfig();
    @Configurable(name = "npcConfig.npcDynamicModel.armR", subConfigurable = true)
    private ModelPartConfig armR = new ModelPartConfig();
    @Configurable(name = "npcConfig.npcDynamicModel.legNotShared")
    private boolean legNotShared = false;
    @Configurable(name = "npcConfig.npcDynamicModel.legL", subConfigurable = true)
    private ModelPartConfig legL = new ModelPartConfig();
    @Configurable(name = "npcConfig.npcDynamicModel.legR", subConfigurable = true)
    private ModelPartConfig legR = new ModelPartConfig();
    //碰撞箱大小
    @Configurable(name = "npcConfig.npcDynamicModel.aabb")
    private AABB aabb = new AABB(-0.3, 0, -0.3, 0.3, 2, 0.3);
    //缓存的生物，用于让npc展示其模型和材质
    private Entity entity;
    private ResourceLocation tempEntityType;

    static {
        CODEC = PersistedParser.createCodec(NpcDynamicModel::new);
        STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
    }

    public Entity getEntity(CustomNpc npc) {
        // 只有当entityType改变时才需要创建新的实体，否则直接返回缓存的实体。把自己添加到黑名单，防止无限递归
        if (entityType != null && !entityType.equals(tempEntityType) && !entityType.equals(ViScriptNpc.id("custom_npc"))) {
            // 因为默认是玩家模型，所以不会有逝
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(this.entityType)) return entity;
            // 如果entityType是玩家，这样是不会创建出玩家实体的，因此entity还是null
            entity = BuiltInRegistries.ENTITY_TYPE.get(this.entityType).create(npc.level());
            CompoundTag compoundTag = new CompoundTag();
            if (entity != null) {
                tempEntityType = entityType;
                if (entity instanceof LivingEntity living) {
                    living.addAdditionalSaveData(compoundTag);
                    // 保留扩展兼容，可以给compoundTag添加其它的nbt内容再还给实体

                    living.readAdditionalSaveData(compoundTag);
                    AttributeInstance maxHealth = living.getAttribute(Attributes.MAX_HEALTH);
                    if (maxHealth != null) {
                        maxHealth.setBaseValue(npc.getMaxHealth());
                    }
                }
            }
        }
        return this.entity;
    }

    public void updateNpcModelPart(HumanoidModel<?> model) {
        BeanUtil.copyProperties(this.head, model.head);
        BeanUtil.copyProperties(this.body, model.body);
        BeanUtil.copyProperties(this.armL, model.leftArm);
        BeanUtil.copyProperties(this.armR, model.rightArm);
        BeanUtil.copyProperties(this.legL, model.leftLeg);
        BeanUtil.copyProperties(this.legR, model.rightLeg);
    }

    public void updateGeoPart(BakedGeoModel model) {
        for (var geoBone : model.topLevelBones()) {
            if (tryCopyConfig(geoBone)) continue;
            for (var child : geoBone.getChildBones()) {
                tryCopyConfig(child);
            }
        }
    }

    private boolean tryCopyConfig(GeoBone geoBone) {
        String name = geoBone.getName().toLowerCase();
        if (name.contains("head")) return copy(head, geoBone);
        if (name.contains("body")) return copy(body, geoBone);
        if (name.contains("left") && (name.contains("arm") || name.contains("hand"))) {
            return copy(armL, geoBone);
        }
        if (name.contains("right") && (name.contains("arm") || name.contains("hand"))) {
            return copy(armR, geoBone);
        }
        if (name.contains("left") && (name.contains("leg") || name.contains("foot"))) {
            return copy(legL, geoBone);
        }
        if (name.contains("right") && (name.contains("leg") || name.contains("foot"))) {
            return copy(legR, geoBone);
        }
        return false;
    }

    private boolean copy(ModelPartConfig config, GeoBone geoBone) {
        BeanUtil.copyProperties(config.transform(), geoBone);
        return true;
    }

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        IConfigurable.super.buildConfigurator(father);
        father.getConfigurators().forEach(configurator -> {
            switch (((TranslatableContents) configurator.label.getText().getContents()).getKey()) {
                case "npcConfig.npcDynamicModel.armL" -> {
                    configurator.addEventListener(UIEvents.CHAR_TYPED, event -> {
                        if (this.armNotShared) BeanUtil.copyProperties(this.armL, this.armR);
                    });
                    configurator.addEventListener(UIEvents.DRAG_UPDATE, event -> {
                        if (this.armNotShared) BeanUtil.copyProperties(this.armL, this.armR);
                    });
                }
                case "npcConfig.npcDynamicModel.armR" -> {
                    configurator.addEventListener(UIEvents.CHAR_TYPED, event -> {
                        if (this.armNotShared) BeanUtil.copyProperties(this.armR, this.armL);
                    });
                    configurator.addEventListener(UIEvents.DRAG_UPDATE, event -> {
                        if (this.armNotShared) BeanUtil.copyProperties(this.armR, this.armL);
                    });
                }
                case "npcConfig.npcDynamicModel.legL" -> {
                    configurator.addEventListener(UIEvents.CHAR_TYPED, event -> {
                        if (this.legNotShared) BeanUtil.copyProperties(this.legL, this.legR);
                    });
                    configurator.addEventListener(UIEvents.DRAG_UPDATE, event -> {
                        if (this.legNotShared) BeanUtil.copyProperties(this.legL, this.legR);
                    });
                }
                case "npcConfig.npcDynamicModel.legR" -> {
                    configurator.addEventListener(UIEvents.CHAR_TYPED, event -> {
                        if (this.legNotShared) BeanUtil.copyProperties(this.legR, this.legL);
                    });
                    configurator.addEventListener(UIEvents.DRAG_UPDATE, event -> {
                        if (this.legNotShared) BeanUtil.copyProperties(this.legR, this.legL);
                    });
                }
            }
        });
    }
}

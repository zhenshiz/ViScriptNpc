package com.viscript.npc.npc.data.dynamic.model;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.mojang.serialization.Codec;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.util.common.BeanUtil;
import io.netty.buffer.ByteBuf;
import lombok.Data;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

@Data
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
    private LivingEntity entity;

    static {
        CODEC = PersistedParser.createCodec(NpcDynamicModel::new);
        STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
    }

    public LivingEntity getEntity(CustomNpc npc) {
        if (entityType != null) {
            this.entity = (LivingEntity) (BuiltInRegistries.ENTITY_TYPE.get(this.entityType)).create(npc.level());
            CompoundTag compoundTag = new CompoundTag();
            if (this.entity != null) {
                this.entity.addAdditionalSaveData(compoundTag);
                // 保留扩展兼容，可以给compoundTag添加其它的nbt内容再还给实体

                this.entity.readAdditionalSaveData(compoundTag);
                AttributeInstance maxHealth = this.entity.getAttribute(Attributes.MAX_HEALTH);
                if (maxHealth != null) {
                    maxHealth.setBaseValue(npc.getMaxHealth());
                }
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    this.entity.setItemSlot(slot, npc.getItemBySlot(slot));
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

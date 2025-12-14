package com.viscript.npc.npc.data.dynamic.model;

import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.data.INpcData;
import com.viscript.npc.util.ConfiguratorUtil;
import com.viscript.npc.util.common.BeanUtil;
import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NpcDynamicModel implements INpcData {
    public static final StreamCodec<ByteBuf, NpcDynamicModel> STREAM_CODEC;
    public static final Codec<NpcDynamicModel> CODEC;

    //NPC代理的的生物类型，可以使用这个生物的模型和材质
    @Persisted
    private ResourceLocation entityType = ResourceLocation.withDefaultNamespace("player");
    //NPC模型生物的NBT数据
    @Configurable(name = "npcConfig.npcDynamicModel.nbt")
    private String nbt = "";
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

    public static CompoundTag tagFromString(String nbt) {
        StringReader reader = new StringReader(nbt);
        try {
            return new TagParser(reader).readStruct();
        } catch (CommandSyntaxException ignored) {
            return new CompoundTag();
        }
    }

    public Entity getEntity(CustomNpc npc) {
        // 只有当entityType改变时才需要创建新的实体，否则直接返回缓存的实体。把自己添加到黑名单，防止无限递归
        if (entityType != null && !entityType.equals(tempEntityType) && !entityType.equals(ViScriptNpc.id("custom_npc"))) {
            // 因为默认是玩家模型，所以不会有逝
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(this.entityType)) return entity;
            // 如果entityType是玩家，这样是不会创建出玩家实体的，因此entity还是null
            entity = BuiltInRegistries.ENTITY_TYPE.get(this.entityType).create(npc.level());
            if (entity != null) {
                tempEntityType = entityType;
            }
        }
        if (entity != null) entity.load(tagFromString(nbt));
        return this.entity;
    }

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        father.addConfigurator(ConfiguratorUtil.createStrArrSearchComponentConfigurator("npcConfig.npcDynamicModel.entityType", BuiltInRegistries.ENTITY_TYPE.stream().map(i -> EntityType.getKey(i).toString()).collect(Collectors.toSet()), () -> getEntityType().toString(), s -> setEntityType(ResourceLocation.parse(s))));
        INpcData.super.buildConfigurator(father);
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

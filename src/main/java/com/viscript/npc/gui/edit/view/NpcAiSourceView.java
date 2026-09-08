package com.viscript.npc.gui.edit.view;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.gui.edit.page.INpcEditorPage;
import com.viscript.npc.npc.ai.editor.NpcAiDescriptorCache;
import com.viscript.npc.npc.data.ai.NpcAI;
import com.viscript.npc.npc.data.ai.NpcAiSource;
import com.viscript.npc.util.ConfiguratorUtil;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.Set;

public class NpcAiSourceView extends View implements INpcEditorSlotView {
    private final NpcEditor editor;
    private final NPCProject project;
    private ConfiguratorGroup controls;

    public NpcAiSourceView(NpcEditor editor, NPCProject project) {
        super(ViScriptNpc.MOD_ID + ".editor.view.ai_source");
        this.editor = editor;
        this.project = project;
        layout(layout -> layout.widthPercent(100).heightPercent(100).paddingAll(4));
        rebuild();
    }

    public void onViewSelected(NpcEditor editor, NPCProject project, INpcEditorPage page) {
        rebuild();
    }

    private void rebuild() {
        clearAllChildren();
        controls = new ConfiguratorGroup("viscript_npc.editor.ai.source_settings", false);
        controls.layout(layout -> layout.widthPercent(100));
        addChild(controls);
        NpcAI ai = project.npc.getNpcData(NpcAI.class);
        controls.addConfigurator(ConfiguratorUtil.createStrArrSearchComponentConfigurator(
                "viscript_npc.editor.ai.source", Set.of(NpcAiSource.EMBEDDED.name(), NpcAiSource.TEMPLATE.name()),
                () -> ai.getSource().name(), selected -> switchSource(NpcAiSource.valueOf(selected))));
        if (!ai.usesTemplate()) return;
        controls.addConfigurator(ConfiguratorUtil.createStrArrSearchComponentConfigurator(
                "viscript_npc.editor.ai.template", NpcAiDescriptorCache::templateIds,
                () -> ai.getTemplateId().toString(), this::selectTemplate));
        addOverrideConfigurators(ai);
        Button clear = new Button().setText("viscript_npc.editor.ai.clear_template_overrides")
                .setOnClick(event -> {
                    ai.useTemplate(ai.getTemplateId(), new CompoundTag());
                    rebuild();
                });
        clear.layout(layout -> layout.widthPercent(100).height(18));
        addChild(clear);
        Button copy = new Button().setText("viscript_npc.editor.ai.copy_template_embedded")
                .setOnClick(event -> copyTemplate());
        copy.layout(layout -> layout.widthPercent(100).height(18));
        addChild(copy);
    }

    private void switchSource(NpcAiSource source) {
        NpcAI ai = project.npc.getNpcData(NpcAI.class);
        if (source == NpcAiSource.TEMPLATE) {
            Set<String> templates = NpcAiDescriptorCache.templateIds();
            String selected = templates.contains(ai.getTemplateId().toString())
                    ? ai.getTemplateId().toString() : templates.stream().sorted().findFirst().orElse("");
            if (!selected.isBlank()) ai.useTemplate(ResourceLocation.parse(selected), new CompoundTag());
        } else {
            ai.useEmbeddedGraph(ai.getEmbeddedGraph(), ai.getEmbeddedFlow(), ai.getEmbeddedParameters());
        }
        editor.reloadNpcEditorPages();
    }

    private void selectTemplate(String selected) {
        ResourceLocation id = ResourceLocation.tryParse(selected);
        if (id == null) return;
        project.npc.getNpcData(NpcAI.class).useTemplate(id, new CompoundTag());
        editor.reloadNpcEditorPages();
    }

    private void addOverrideConfigurators(NpcAI ai) {
        CompoundTag template = NpcAiDescriptorCache.template(ai.getTemplateId().toString());
        CompoundTag defaults = template.getCompound("public_parameters");
        CompoundTag overrides = ai.getTemplateParameterOverrides();
        for (String name : new LinkedHashSet<>(defaults.getAllKeys())) {
            Tag defaultValue = defaults.get(name);
            if (defaultValue == null) continue;
            String label = "viscript_npc.editor.ai.template_parameter." + name;
            if (defaultValue instanceof ByteTag byteValue) {
                controls.addConfigurator(new BooleanConfigurator(label,
                        () -> overrides.contains(name) ? overrides.getBoolean(name) : byteValue.getAsByte() != 0,
                        value -> setOverride(ai, name, ByteTag.valueOf(value)), byteValue.getAsByte() != 0, true));
            } else if (defaultValue instanceof NumericTag numeric) {
                ConfigNumber.Type numberType = numberType(defaultValue);
                NumberConfigurator configurator = new NumberConfigurator(label,
                        () -> overrides.get(name) instanceof NumericTag current ? current.getAsNumber() : numeric.getAsNumber(),
                        value -> setOverride(ai, name, numericTag(defaultValue, value)), numeric.getAsNumber(), true)
                        .setType(numberType);
                controls.addConfigurator(configurator);
            } else if (defaultValue instanceof StringTag string) {
                controls.addConfigurator(new StringConfigurator(label,
                        () -> overrides.contains(name, Tag.TAG_STRING) ? overrides.getString(name) : string.getAsString(),
                        value -> setOverride(ai, name, StringTag.valueOf(value)), string.getAsString(), true));
            } else {
                controls.addConfigurator(new StringConfigurator(label,
                        () -> overrides.contains(name) ? overrides.get(name).toString() : defaultValue.toString(),
                        value -> setOverride(ai, name, parseNbtValue(value)), defaultValue.toString(), true));
            }
        }
    }

    private void setOverride(NpcAI ai, String name, Tag value) {
        CompoundTag updated = ai.getTemplateParameterOverrides().copy();
        updated.put(name, value);
        ai.useTemplate(ai.getTemplateId(), updated);
    }

    private void copyTemplate() {
        NpcAI ai = project.npc.getNpcData(NpcAI.class);
        if (!ai.usesTemplate()) return;
        CompoundTag template = NpcAiDescriptorCache.template(ai.getTemplateId().toString());
        if (template.isEmpty()) return;
        CompoundTag parameters = template.getCompound("public_parameters");
        parameters.merge(ai.getTemplateParameterOverrides());
        ai.useEmbeddedGraph(template.getCompound("graph"), template.getCompound("flow"), parameters);
        editor.reloadNpcEditorPages();
    }

    private static ConfigNumber.Type numberType(Tag tag) {
        if (tag instanceof LongTag) return ConfigNumber.Type.LONG;
        if (tag instanceof FloatTag) return ConfigNumber.Type.FLOAT;
        if (tag instanceof DoubleTag) return ConfigNumber.Type.DOUBLE;
        return ConfigNumber.Type.INTEGER;
    }

    private static Tag numericTag(Tag type, Number value) {
        if (type instanceof ShortTag) return ShortTag.valueOf(value.shortValue());
        if (type instanceof IntTag) return IntTag.valueOf(value.intValue());
        if (type instanceof LongTag) return LongTag.valueOf(value.longValue());
        if (type instanceof FloatTag) return FloatTag.valueOf(value.floatValue());
        if (type instanceof DoubleTag) return DoubleTag.valueOf(value.doubleValue());
        return IntTag.valueOf(value.intValue());
    }

    private static Tag parseNbtValue(String value) {
        try {
            return TagParser.parseTag("{value:" + value + "}").get("value");
        } catch (Exception exception) {
            return StringTag.valueOf(value);
        }
    }
}

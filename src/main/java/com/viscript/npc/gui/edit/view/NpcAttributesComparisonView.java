package com.viscript.npc.gui.edit.view;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.npc.data.attributes.NpcAttributes;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

public class NpcAttributesComparisonView extends View implements INpcEditorSlotView {
    private final NpcEditor editor;
    private final NpcAttributesPageState state;
    private final ScrollerView scrollerView = new ScrollerView();

    public NpcAttributesComparisonView(NpcEditor editor, NpcAttributesPageState state) {
        super(String.format("%s.editor.view.attributes_comparison", ViScriptNpc.MOD_ID));
        this.editor = editor;
        this.state = state;

        layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.gapAll(2);
        });

        scrollerView.layout(layout -> {
            layout.widthPercent(100);
            layout.flex(1);
        });
        addChild(scrollerView);
        state.addChangeListener(this::rebuild);

        rebuild();
    }

    @Override
    public void onViewSelected(NpcEditor editor, NPCProject project, com.viscript.npc.gui.edit.page.INpcEditorPage page) {
        rebuild();
    }

    public void rebuild() {
        scrollerView.viewContainer.clearAllChildren();

        NpcAttributes current = getCurrentAttributes();
        if (current == null) {
            scrollerView.viewContainer.addChild(new Label()
                    .setText(Component.translatable("viscript_npc.editor.attributes.no_current_project")));
            return;
        }

        addHeaderRow();
        addGroup("viscript_npc.editor.attributes.group.core", List.of(
                row("npcConfig.npcAttributes.maxHealth", current, NpcAttributes::getMaxHealth),
                row("npcConfig.npcAttributes.movementSpeed", current, NpcAttributes::getMovementSpeed),
                row("npcConfig.npcAttributes.combatRegenRate", current, attributes -> attributes.getCombatRegenRate()),
                row("npcConfig.npcAttributes.outOfCombatRegenRate", current, attributes -> attributes.getOutOfCombatRegenRate())
        ));

        addGroup("viscript_npc.editor.attributes.group.offense", List.of(
                row("npcConfig.npcAttributes.meleeConfig.attackDamage", current, attributes -> attributes.getMeleeConfig().getAttackDamage()),
                row("npcConfig.npcAttributes.meleeConfig.attackRange", current, attributes -> attributes.getMeleeConfig().getAttackRange()),
                row("npcConfig.npcAttributes.meleeConfig.knockback", current, attributes -> attributes.getMeleeConfig().getKnockback()),
                row("npcConfig.npcAttributes.rangedConfig.damage", current, attributes -> attributes.getRangedConfig().getDamage()),
                row("npcConfig.npcAttributes.rangedConfig.knockback", current, attributes -> attributes.getRangedConfig().getKnockback()),
                row("npcConfig.npcAttributes.rangedConfig.speed", current, attributes -> attributes.getRangedConfig().getSpeed()),
                row("npcConfig.npcAttributes.rangedConfig.explosionPower", current, attributes -> attributes.getRangedConfig().getExplosionPower())
        ));

        addGroup("viscript_npc.editor.attributes.group.defense", List.of(
                row("npcConfig.npcAttributes.defenseConfig.armor", current, attributes -> attributes.getDefenseConfig().getArmor()),
                row("npcConfig.npcAttributes.defenseConfig.armorToughness", current, attributes -> attributes.getDefenseConfig().getArmorToughness()),
                row("npcConfig.npcAttributes.defenseConfig.reboundDamage", current, attributes -> attributes.getDefenseConfig().getReboundDamage()),
                row("npcConfig.npcAttributes.resistanceConfig.knockback", current, attributes -> attributes.getResistanceConfig().getKnockback()),
                row("npcConfig.npcAttributes.resistanceConfig.projectile", current, attributes -> attributes.getResistanceConfig().getProjectile()),
                row("npcConfig.npcAttributes.resistanceConfig.explosion", current, attributes -> attributes.getResistanceConfig().getExplosion()),
                row("npcConfig.npcAttributes.resistanceConfig.melee", current, attributes -> attributes.getResistanceConfig().getMelee()),
                row("npcConfig.npcAttributes.resistanceConfig.fire", current, attributes -> attributes.getResistanceConfig().getFire())
        ));

        addGroup("viscript_npc.editor.attributes.group.flags", List.of(
                booleanRow("npcConfig.npcAttributes.isCanDrown", current, NpcAttributes::isCanDrown),
                booleanRow("npcConfig.npcAttributes.isPotionImmune", current, NpcAttributes::isPotionImmune),
                booleanRow("npcConfig.npcAttributes.isFallDamage", current, NpcAttributes::isFallDamage),
                booleanRow("npcConfig.npcAttributes.isBurnInDay", current, NpcAttributes::isBurnInDay),
                booleanRow("npcConfig.npcAttributes.isIgnoreCobweb", current, NpcAttributes::isIgnoreCobweb),
                booleanRow("npcConfig.npcAttributes.sensitiveToSmite", current, NpcAttributes::isSensitiveToSmite),
                booleanRow("npcConfig.npcAttributes.sensitiveToBaneOfArthropods", current, NpcAttributes::isSensitiveToBaneOfArthropods),
                booleanRow("npcConfig.npcAttributes.sensitiveToImpaling", current, NpcAttributes::isSensitiveToImpaling)
        ));
    }

    private void addHeaderRow() {
        var row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(12);
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(1);
        });
        row.style(style -> style.backgroundTexture(ColorPattern.T_GRAY.rectTexture()));
        row.addChild(cell("viscript_npc.editor.attributes.column.name", 44));
        row.addChild(cell("viscript_npc.editor.attributes.column.current", 18));
        row.addChild(referenceHeader(0));
        row.addChild(referenceHeader(1));
        row.addChild(cell("viscript_npc.editor.attributes.column.delta", 18));
        scrollerView.viewContainer.addChild(row);
    }

    private UIElement referenceHeader(int index) {
        String text = index < state.getSelectedReferences().size()
                ? state.getSelectedReferences().get(index).getNpcType()
                : Component.translatable("viscript_npc.editor.attributes.column.reference_empty").getString();
        return new Label()
                .setText(Component.literal(text))
                .layout(layout -> {
                    layout.widthPercent(20);
                    layout.heightPercent(100);
                });
    }

    private void addGroup(String key, List<UIElement> rows) {
        scrollerView.viewContainer.addChild(new Label()
                .setText(Component.translatable(key))
                .layout(layout -> {
                    layout.widthPercent(100);
                    layout.height(12);
                })
                .style(style -> style.backgroundTexture(ColorPattern.T_BLUE.rectTexture())));
        for (UIElement row : rows) {
            scrollerView.viewContainer.addChild(row);
        }
    }

    private UIElement row(String key, NpcAttributes current, Function<NpcAttributes, Number> getter) {
        Number currentValue = getter.apply(current);
        Number referenceA = getReferenceValue(0, getter);
        Number referenceB = getReferenceValue(1, getter);
        String delta = referenceA == null ? "-" : formatNumber(currentValue.doubleValue() - referenceA.doubleValue());

        UIElement row = baseRow();
        row.addChild(cell(key, 44));
        row.addChild(literalCell(formatNumber(currentValue.doubleValue()), 18));
        row.addChild(literalCell(formatNullable(referenceA), 20));
        row.addChild(literalCell(formatNullable(referenceB), 20));
        row.addChild(literalCell(delta, 18));
        return row;
    }

    private UIElement booleanRow(String key, NpcAttributes current, Function<NpcAttributes, Boolean> getter) {
        Boolean currentValue = getter.apply(current);
        Boolean referenceA = getReferenceValue(0, getter);
        Boolean referenceB = getReferenceValue(1, getter);
        String delta = referenceA == null ? "-" : (currentValue.equals(referenceA) ? "=" : "!");

        UIElement row = baseRow();
        row.addChild(cell(key, 44));
        row.addChild(literalCell(formatBoolean(currentValue), 18));
        row.addChild(literalCell(formatBoolean(referenceA), 20));
        row.addChild(literalCell(formatBoolean(referenceB), 20));
        row.addChild(literalCell(delta, 18));
        return row;
    }

    private UIElement baseRow() {
        return new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(11);
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(1);
        });
    }

    private Label cell(String key, float widthPercent) {
        Label label = new Label();
        label.setText(Component.translatable(key));
        label.layout(layout -> {
            layout.widthPercent(widthPercent);
            layout.heightPercent(100);
        });
        return label;
    }

    private Label literalCell(String text, float widthPercent) {
        Label label = new Label();
        label.setText(Component.literal(text));
        label.layout(layout -> {
            layout.widthPercent(widthPercent);
            layout.heightPercent(100);
        });
        return label;
    }

    @Nullable
    private <T> T getReferenceValue(int index, Function<NpcAttributes, T> getter) {
        if (index >= state.getSelectedReferences().size()) {
            return null;
        }
        return getter.apply(state.getSelectedReferences().get(index).getNpcAttributes());
    }

    @Nullable
    private NpcAttributes getCurrentAttributes() {
        if (editor.getCurrentProject() instanceof NPCProject npcProject) {
            return npcProject.npc.npcConfig.getNpcData(NpcAttributes.class);
        }
        return null;
    }

    private static String formatNullable(@Nullable Number value) {
        return value == null ? "-" : formatNumber(value.doubleValue());
    }

    private static String formatBoolean(@Nullable Boolean value) {
        if (value == null) {
            return "-";
        }
        return value ? "Y" : "N";
    }

    private static String formatNumber(double value) {
        if (Math.floor(value) == value) {
            return Integer.toString((int) value);
        }
        return String.format("%.2f", value);
    }
}

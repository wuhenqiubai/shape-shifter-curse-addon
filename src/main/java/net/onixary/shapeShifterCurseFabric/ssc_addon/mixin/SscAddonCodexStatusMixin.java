package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.onixary.shapeShifterCurseFabric.data.CodexData;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.AxolotlTree;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionNode;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.FamiliarFoxTree;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.RegEvolutionComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 幻形者之书「外观」段动态化：对进化使魔 / 进化美西螈，在静态外观描述后追加
 * 已解锁加点节点的书页化叙述，让书内 appearance 段随加点状态实时变化。
 *
 * <p><b>历史</b>：本 mixin 最初由 0299132 提交引入，含两个 @Inject——
 * {@code getPlayerStatusText}(按形态返回固定 status) 与 {@code appendEvolutionToAppearance}(动态追加加点)。
 * 前者会覆盖原版的诅咒之月 / 变身中状态显示(status bug)，故 349b435 提交将整个 mixin 删除。
 * 现仅恢复 {@code appendEvolutionToAppearance} 这一无 bug 的功能，{@code getPlayerStatusText} 走原版逻辑不动。</p>
 *
 * <p>仅对进化使魔({@code UPGRADE_FAMILIAR_FOX}) / 进化美西螈({@code UPGRADE_AXOLOTL}) 的
 * {@code APPEARANCE} 类型生效；其它形态 / 其它 ContentType 完全走原版。</p>
 */
@Mixin(CodexData.class)
public class SscAddonCodexStatusMixin {

    @Inject(method = "getContentText", at = @At("RETURN"), cancellable = true)
    private static void appendEvolutionToAppearance(CodexData.ContentType type, Player player,
                                                    CallbackInfoReturnable<Component> cir) {
        if (type != CodexData.ContentType.APPEARANCE) {
            return;
        }
        PlayerFormComponent component = RegPlayerFormComponent.PLAYER_FORM.get(player);
        if (component == null) {
            return;
        }
        IForm currentForm = component.nowForm;
        if (currentForm == null || currentForm.getFormID() == null) {
            return;
        }

        // 根据当前形态选择对应的加点树；非进化形态不处理
        List<EvolutionNode> nodes;
        String baseNodeId;
        if (FormIdentifiers.UPGRADE_FAMILIAR_FOX.equals(currentForm.getFormID())) {
            nodes = FamiliarFoxTree.nodes();
            baseNodeId = FamiliarFoxTree.NODE_BASE;
        } else if (FormIdentifiers.UPGRADE_AXOLOTL.equals(currentForm.getFormID())) {
            nodes = AxolotlTree.nodes();
            baseNodeId = AxolotlTree.NODE_BASE;
        } else {
            return;
        }
        if (nodes.isEmpty()) {
            return;  // route 未加载，跳过
        }

        EvolutionComponent comp = RegEvolutionComponent.EVOLUTION.get(player);
        if (comp == null) {
            return;
        }
        Component base = cir.getReturnValue();
        if (base == null) {
            return;
        }

        // 统计已解锁的可加点节点数
        int unlockableCount = 0;
        int unlockedCount = 0;
        for (EvolutionNode node : nodes) {
            if (baseNodeId.equals(node.id)) {
                continue;
            }
            unlockableCount++;
            if (comp.isUnlocked(node.id)) {
                unlockedCount++;
            }
        }

        MutableComponent dynamic = Component.empty();
        dynamic.append(base);
        dynamic.append(Component.literal("\n\n"));
        dynamic.append(Component.translatable("text.ssc_addon.evolution.book.summary_title"));
        dynamic.append(Component.literal("\n"));
        dynamic.append(Component.translatable("text.ssc_addon.evolution.book.summary_stats",
                unlockedCount, unlockableCount, comp.getPoints(), player.experienceLevel));
        // 逐个已解锁节点追加书页化叙述
        for (EvolutionNode node : nodes) {
            if (baseNodeId.equals(node.id)) {
                continue;
            }
            if (!comp.isUnlocked(node.id)) {
                continue;
            }
            dynamic.append(Component.literal("\n\n\u2022 "));
            dynamic.append(Component.translatable(node.nameKey));
            dynamic.append(Component.literal("\n"));
            String bookKey = node.descKey.substring(0, node.descKey.length() - 5) + ".book";
            dynamic.append(Component.translatable(bookKey));
        }
        cir.setReturnValue(dynamic);
    }
}

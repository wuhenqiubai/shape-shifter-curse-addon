package net.onixary.shapeShifterCurseFabric.ssc_addon.client.evolution;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.onixary.shapeShifterCurseFabric.networking.BytePayload;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionNode;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionRegistry;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionRoute;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;

import java.util.ArrayList;
import java.util.List;

/**
 * SSCA 进化路线 - 开局形态选择界面（类似书籍的可翻页界面）。
 *
 * <p>玩家在 {@link net.onixary.shapeShifterCurseFabric.custom_ui.StartBookScreenV2 翻开幻形者之书界面} 选择
 * 「SSCA 进化路线」并确认后进入本界面。每一页展示一个可选的 SSCA 进化形态（图标 + 名称 + 描述），
 * 点击「选择此形态」即发包让服务端走 SSCA 进化路线并播放进化动画。</p>
 *
 * <p><b>当前阶段</b>：SSCA 进化形态只有「进化使魔」一个，故只有 1 页；界面已做成可翻页框架，
 */
public class SscaFormSelectScreen extends Screen {

    /** 一个可在开局选择的 SSCA 进化形态条目。 */
    private static final class StartForm {
        final ResourceLocation formId;
        final Item icon;
        final String nameKey;
        final String descKey;

        StartForm(ResourceLocation formId, Item icon, String nameKey, String descKey) {
            this.formId = formId;
            this.icon = icon;
            this.nameKey = nameKey;
            this.descKey = descKey;
        }
    }

    /**
     * 开局可选的 SSCA 进化形态列表（数据驱动：来自所有「已开放且有起点形态」的进化路线）。
     * <p>加新形态只需新增 route JSON，界面自动出现并支持翻页。</p>
     */
    private final List<StartForm> forms = new ArrayList<>();

    /**
     * 开局可选形态的显示顺序（route id 升序）。不在此表中的 route 排在最后、按 route id 字母序。
     * <p>调整翻页顺序只需调整此数组。</p>
     */
    private static final List<String> DISPLAY_ORDER = List.of(
            "familiar_fox",  // 进化使魔（第一页）
            "axolotl"        // 进化美西螈（第二页）
    );

    /** 从已加载的进化路线动态构建可选形态列表（按 {@link #DISPLAY_ORDER} 排序）。 */
    private void buildForms() {
        forms.clear();
        // 先按 DISPLAY_ORDER 收集，保证指定顺序；未列出的 route 追加在末尾（字母序兜底）
        List<EvolutionRoute> ordered = new ArrayList<>();
        // 用 TreeMap 保证未指定 route 的稳定字母序
        java.util.Map<String, EvolutionRoute> remain = new java.util.TreeMap<>();
        for (EvolutionRoute route : EvolutionRegistry.INSTANCE.all().values()) {
            if (!route.enabled || route.startForm == null) {
                continue;
            }
            remain.put(route.routeId, route);
        }
        for (String rid : DISPLAY_ORDER) {
            EvolutionRoute r = remain.remove(rid);
            if (r != null) {
                ordered.add(r);
            }
        }
        ordered.addAll(remain.values());

        for (EvolutionRoute route : ordered) {
            ResourceLocation fid = route.startForm;
            String ns = fid.getNamespace();
            String path = fid.getPath();
            // 图标取初始节点图标；名称 / 描述按形态 id 推导对应 lang key
            Item icon = Items.NETHER_STAR;
            String baseId = route.getBaseNodeId();
            if (baseId != null) {
                EvolutionNode baseNode = route.getNode(baseId);
                if (baseNode != null && baseNode.icon != null) {
                    icon = baseNode.icon;
                }
            }
            String nameKey = "origin." + ns + ".form_" + path + ".name";
            String descKey = "evolution." + ns + ".start.form." + path + ".desc";
            forms.add(new StartForm(fid, icon, nameKey, descKey));
        }
    }

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 210;
    private static final int ICON_SCALE = 3;

    private final Screen parent;
    private int page = 0;

    public SscaFormSelectScreen(Screen parent) {
        super(Component.translatable("evolution.my_addon.start.select.title"));
        this.parent = parent;
    }

    private int panelX() {
        return (this.width - PANEL_W) / 2;
    }

    private int panelY() {
        return (this.height - PANEL_H) / 2;
    }

    @Override
    protected void init() {
        buildForms();
        int px = panelX();
        int py = panelY();
        boolean multiPage = forms.size() > 1;

        // 翻页按钮（面板两侧中部）；仅有一页时禁用，体现可翻页框架
        Button prev = Button.builder(Component.literal("<"), b -> prevPage())
                .bounds(px + 6, py + PANEL_H / 2 - 10, 20, 20).build();
        prev.active = multiPage;
        this.addRenderableWidget(prev);

        Button next = Button.builder(Component.literal(">"), b -> nextPage())
                .bounds(px + PANEL_W - 26, py + PANEL_H / 2 - 10, 20, 20).build();
        next.active = multiPage;
        this.addRenderableWidget(next);

        // 选择此形态
        this.addRenderableWidget(Button.builder(
                Component.translatable("evolution.my_addon.start.select.choose"),
                b -> chooseCurrentForm()
        ).bounds(this.width / 2 - 80, py + PANEL_H - 54, 160, 20).build());

        // 返回
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.back"),
                b -> this.onClose()
        ).bounds(this.width / 2 - 60, py + PANEL_H - 30, 120, 20).build());
    }

    private void prevPage() {
        if (forms.isEmpty()) {
            return;
        }
        page = (page - 1 + forms.size()) % forms.size();
    }

    private void nextPage() {
        if (forms.isEmpty()) {
            return;
        }
        page = (page + 1) % forms.size();
    }

    private void chooseCurrentForm() {
        if (page < 0 || page >= forms.size()) {
            return;
        }
        StartForm form = forms.get(page);
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(form.formId.toString());
        ClientPlayNetworking.send(new BytePayload(BytePayload.id(SscAddonNetworking.PACKET_SSCA_START_ROUTE), buf));
        // 关闭所有界面，让玩家看到进化动画
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);

        int px = panelX();
        int py = panelY();
        // 面板背景 + 边框
        ctx.fill(px, py, px + PANEL_W, py + PANEL_H, 0xE0140C08);
        drawBorder(ctx, px, py, PANEL_W, PANEL_H, 0xFFB8893A);

        // 标题
        ctx.drawCenteredString(this.font, this.title, this.width / 2, py + 10, 0xFFE8C66A);

        if (!forms.isEmpty()) {
            // 页码
            Component pageInfo = Component.translatable("evolution.my_addon.start.select.page", page + 1, forms.size());
            ctx.drawCenteredString(this.font, pageInfo, this.width / 2, py + 24, 0xFFAAAAAA);

            StartForm form = forms.get(page);

            // 形态图标（放大）
            int iconCenterX = this.width / 2;
            int iconTopY = py + 44;
            ctx.pose().pushPose();
            ctx.pose().translate(iconCenterX - 8 * ICON_SCALE, iconTopY, 0);
            ctx.pose().scale(ICON_SCALE, ICON_SCALE, 1);
            ctx.renderItem(new ItemStack(form.icon), 0, 0);
            ctx.pose().popPose();

            // 形态名称
            Component name = Component.translatable(form.nameKey).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            ctx.drawCenteredString(this.font, name, this.width / 2, iconTopY + 16 * ICON_SCALE + 8, 0xFFFFFFFF);

            // 形态描述（多行居中）
            MultiLineLabel desc = MultiLineLabel.create(this.font,
                    Component.translatable(form.descKey).withStyle(ChatFormatting.GRAY), PANEL_W - 40);
            desc.renderCentered(ctx, this.width / 2, iconTopY + 16 * ICON_SCALE + 26);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawBorder(GuiGraphics ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
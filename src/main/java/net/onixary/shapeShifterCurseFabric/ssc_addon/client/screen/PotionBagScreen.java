package net.onixary.shapeShifterCurseFabric.ssc_addon.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.onixary.shapeShifterCurseFabric.ssc_addon.screen.PotionBagScreenHandler;

public class PotionBagScreen extends AbstractContainerScreen<PotionBagScreenHandler> {
	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("ssc_addon", "textures/gui/container/potion_bag.png");

	public PotionBagScreen(PotionBagScreenHandler handler, Inventory inventory, Component title) {
		super(handler, inventory, title);
		this.imageWidth = 176;
		this.imageHeight = 132; // Assuming 1-row chest height approx
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.setShaderTexture(0, TEXTURE);
		int x = (this.width - this.imageWidth) / 2;
		int y = (this.height - this.imageHeight) / 2;

		// Explicitly specify texture size (textureWidth, textureHeight) as 256x256
		// params: texture, x, y, u, v, width, height, textureWidth, textureHeight
		context.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight, 256, 256);

		// 高亮最左侧槽位（快捷投放栏）：金色边框，提示该槽位可在手持时直接右键快速使用
		int slotX = x + 8;
		int slotY = y + 18;
		int border = 0xFFFFD700; // 不透明金色
		context.fill(slotX - 1, slotY - 1, slotX + 17, slotY, border);       // 上
		context.fill(slotX - 1, slotY + 16, slotX + 17, slotY + 17, border); // 下
		context.fill(slotX - 1, slotY, slotX, slotY + 16, border);           // 左
		context.fill(slotX + 16, slotY, slotX + 17, slotY + 16, border);     // 右
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context, mouseX, mouseY,  delta);
		super.render(context, mouseX, mouseY, delta);
		this.renderTooltip(context, mouseX, mouseY);
	}
}
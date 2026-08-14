package net.jackcooper.shapeShifterCurseAddon.block;

import com.jcraft.jorbis.Block;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * SSCA 附属方块注册（jackcooper 署名）。
 * 主类 onInitialize 调用 {@link #init()}；客户端 onInitializeClient 调用 {@link #clientInit()}。
 */
public final class RegAddonBlocks {

	private RegAddonBlocks() {}

	private static final String NAMESPACE = "ssc_addon";

	// 蛛网膜：多面薄层蛛网，减速陷阱，可燃蔓延、遇水冲毁
	public static final Block WEB_MEMBRANE = new WebMembraneBlock(
			BlockBehaviour.properties.create()
					.mapColor(MapColor.WOOL)
					.strength(0.2f)
					.sounds(SoundType.WOOL)
					.noCollision()
					.nonOpaque()
					.burnable()
					.dropsNothing()
					.pistonBehavior(PushReaction.DESTROY));

	public static void init() {
		register("web_membrane", WEB_MEMBRANE);
		// 燃烧快、蔓延强（蛛丝易燃；数值对齐草 / 树叶级别）
		FlammableBlockRegistry.getDefaultInstance().add(WEB_MEMBRANE, 60, 100);
	}

	@Environment(EnvType.CLIENT)
	public static void clientInit() {
		// 贴图含大量真半透明像素（约 40%），用 translucent 层才能正确渲染 alpha 渐变；cutout 会把半透明二值化导致大片「不显示」
		BlockRenderLayerMap.INSTANCE.putBlock(WEB_MEMBRANE, RenderType.translucent());
	}

	private static void register(String path, Block block) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
		Registry.register(Registries.BLOCK, id, block);
		Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
	}
}
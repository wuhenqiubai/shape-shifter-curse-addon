package net.onixary.shapeShifterCurseFabric.ssc_addon.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.TridentEntityModel;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.renderer.WaterSpearEntityRenderer;

public class SscAddonClient implements ClientModInitializer {
    public static final String CATEGORY = "key.categories.ssc_addon";
    
    private TridentEntityModel tridentModel;
    
    // SP Allay Keys
    /*public static final KeyBinding KEY_ALLAY_HEAL = new KeyBinding(
        "key.ssc_addon.allay_heal", 
        InputUtil.Type.KEYSYM, 
        GLFW.GLFW_KEY_R, 
        CATEGORY
    );*/

    
    // SP Familiar Fox Keys
    public static final KeyBinding KEY_FOX_FIRE = new KeyBinding(
        "key.ssc_addon.fox_fire", 
        InputUtil.Type.KEYSYM, 
        GLFW.GLFW_KEY_R, 
        CATEGORY
    );
    
    public static final KeyBinding KEY_BLUE_RING = new KeyBinding(
        "key.ssc_addon.blue_ring", 
        InputUtil.Type.KEYSYM, 
        GLFW.GLFW_KEY_G, 
        CATEGORY
    );
    
    // SP Axolotl Keys
    public static final KeyBinding KEY_VORTEX = new KeyBinding(
        "key.ssc_addon.vortex", 
        InputUtil.Type.KEYSYM, 
        GLFW.GLFW_KEY_G, 
        CATEGORY
    );

    public static final KeyBinding KEY_PLAY_DEAD = new KeyBinding(
        "key.ssc_addon.play_dead", 
        InputUtil.Type.KEYSYM, 
        GLFW.GLFW_KEY_Z, 
        CATEGORY
    );

    @Override
    public void onInitializeClient() {
        System.out.println("SSC ADDON DEBUG: Registering Client KeyBindings...");
        
        // SP Allay Keys
        //KeyBindingHelper.registerKeyBinding(KEY_ALLAY_HEAL);

        // SP Familiar Fox Keys
        KeyBindingHelper.registerKeyBinding(KEY_FOX_FIRE);
        KeyBindingHelper.registerKeyBinding(KEY_BLUE_RING);
        
        // SP Axolotl Keys
        KeyBindingHelper.registerKeyBinding(KEY_VORTEX);
        KeyBindingHelper.registerKeyBinding(KEY_PLAY_DEAD);

        EntityRendererRegistry.register(SscAddon.WATER_SPEAR_ENTITY, WaterSpearEntityRenderer::new);

        // Register predicate for 3D model when held (0.0 = inventory/ground, 1.0 = held)
        ModelPredicateProviderRegistry.register(SscAddon.WATER_SPEAR, new Identifier("ssc_addon", "held"), (stack, world, entity, seed) -> 
            entity != null && (entity.getMainHandStack() == stack || entity.getOffHandStack() == stack) ? 1.0F : 0.0F
        );
        
        // Also register "throwing" predicate for trident animation support if needed
        ModelPredicateProviderRegistry.register(SscAddon.WATER_SPEAR, new Identifier("ssc_addon", "throwing"), (stack, world, entity, seed) -> 
            entity != null && entity.isUsingItem() && entity.getActiveItem() == stack ? 1.0F : 0.0F
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            /*if (KEY_ALLAY_HEAL.isPressed()) {
                net.minecraft.network.PacketByteBuf buf = PacketByteBufs.create();
                buf.writeInt(1); // Key ID 1 for Allay Heal
                ClientPlayNetworking.send(SscAddonNetworking.PACKET_KEY_PRESS, buf);
            }*/
            
            // Add other key checks here
        });
    }
}

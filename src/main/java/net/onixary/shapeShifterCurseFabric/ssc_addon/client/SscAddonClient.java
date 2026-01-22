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

import net.onixary.shapeShifterCurseFabric.ssc_addon.client.SscAddonKeybindings;

public class SscAddonClient implements ClientModInitializer {
    public static final String CATEGORY = "key.categories.ssc_addon";
    
    private TridentEntityModel tridentModel;
    
    // SP Keybindings are now managed in SscAddonKeybindings.java
    
    @Override
    public void onInitializeClient() {
        System.out.println("SSC ADDON DEBUG: Registering Client KeyBindings...");
        
        SscAddonKeybindings.register();

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

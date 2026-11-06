package com.mogaidiaolin.witherchallenge.client;

import com.mogaidiaolin.witherchallenge.WitherChallenge;
import com.mogaidiaolin.witherchallenge.entity.InfectionArrowEntity;
import com.mogaidiaolin.witherchallenge.registry.ModEntities;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** 客户端注册：感染力量之箭的渲染器。 */
@Mod.EventBusSubscriber(modid = WitherChallenge.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> EntityRenderers.register(ModEntities.INFECTION_ARROW.get(),
                InfectionArrowRenderer::new));
    }

    /** 先用 16×16 的物品贴图当箭的贴图；后续可以换成专门的箭矢贴图。 */
    public static final class InfectionArrowRenderer extends ArrowRenderer<InfectionArrowEntity> {
        private static final ResourceLocation TEXTURE =
                new ResourceLocation(WitherChallenge.MODID, "textures/entity/projectile/infection_arrow.png");

        public InfectionArrowRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public ResourceLocation getTextureLocation(InfectionArrowEntity entity) {
            return TEXTURE;
        }
    }

    private ClientSetup() {
    }
}

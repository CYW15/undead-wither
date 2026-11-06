package com.mogaidiaolin.witherchallenge.registry;

import com.google.gson.JsonObject;
import com.mogaidiaolin.witherchallenge.WitherChallenge;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * 三个自定义进度触发器（task/成就.docx 第六节）：
 * {@code awaken_storm}、{@code beacon_4}、{@code final}。
 *
 * <p>1.20.1 的进度触发器走 {@link CriteriaTriggers#register} 的静态表（没有可用的
 * TRIGGER_TYPES 注册表常量），所以在 common setup 里注册。</p>
 */
@Mod.EventBusSubscriber(modid = WitherChallenge.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModCriteria {

    public static final SimpleTrigger AWAKEN_STORM = new SimpleTrigger(WitherChallenge.id("awaken_storm"));
    public static final SimpleTrigger BEACON_4 = new SimpleTrigger(WitherChallenge.id("beacon_4"));
    public static final SimpleTrigger FINAL = new SimpleTrigger(WitherChallenge.id("final"));

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CriteriaTriggers.register(AWAKEN_STORM);
            CriteriaTriggers.register(BEACON_4);
            CriteriaTriggers.register(FINAL);
            WitherChallenge.LOGGER.info("[凋亡回响] 已注册 3 个自定义进度触发器");
        });
    }

    /** 无条件的触发器：条件永远成立，只用来「到点就打勾」。 */
    public static final class SimpleTrigger extends SimpleCriterionTrigger<SimpleTrigger.Instance> {
        private final ResourceLocation id;

        SimpleTrigger(ResourceLocation id) {
            this.id = id;
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        protected Instance createInstance(JsonObject json, ContextAwarePredicate predicate,
                                          DeserializationContext context) {
            return new Instance(id, predicate);
        }

        public void trigger(ServerPlayer player) {
            trigger(player, instance -> true);
        }

        public static final class Instance extends AbstractCriterionTriggerInstance {
            public Instance(ResourceLocation id, ContextAwarePredicate predicate) {
                super(id, predicate);
            }

            @Override
            public JsonObject serializeToJson(SerializationContext context) {
                JsonObject json = super.serializeToJson(context);
                json.add("conditions", new JsonObject());
                return json;
            }
        }
    }

    public static void trigger(SimpleTrigger trigger, ServerPlayer player) {
        if (player != null) {
            trigger.trigger(player);
        }
    }

    private ModCriteria() {
    }
}

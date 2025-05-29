package com.custommobsforge.custommobsforge.client.event;

import com.custommobsforge.custommobsforge.client.gui.MobCreatorGUI;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.common.event.MobDataReceivedEvent;
import com.custommobsforge.custommobsforge.common.config.ClientMobDataCache;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;

@Mod.EventBusSubscriber(modid = "custommobsforge_client", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onMobDataReceived(MobDataReceivedEvent event) {
        // Получаем данные моба
        com.custommobsforge.custommobsforge.common.data.MobData data = event.getMobData();

        if (data != null) {
            System.out.println("ClientEventHandler: Received mob data from server for ID: " + data.getId() +
                    ", name: " + data.getName() +
                    ", model: " + data.getModelPath() +
                    ", texture: " + data.getTexturePath());

            // Кэшируем данные в общем кэше
            ClientMobDataCache.cacheMobData(data);

            // Обновляем GUI если есть
            try {
                MobCreatorGUI gui = MobCreatorGUI.getInstance();
                if (gui != null && gui.getMobSaveService() != null) {
                    gui.getMobSaveService().updateCache(data);
                    System.out.println("ClientEventHandler: Updated GUI cache for mob: " + data.getId());
                }
            } catch (Exception e) {
                System.err.println("ClientEventHandler: Error updating GUI cache: " + e.getMessage());
            }

            // Обновляем данные всех существующих мобов с этим ID
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                int updatedEntities = 0;

                for (Entity entity : mc.level.entitiesForRendering()) {
                    if (entity instanceof CustomMobEntity) {
                        CustomMobEntity mobEntity = (CustomMobEntity) entity;

                        // Проверяем ID моба
                        String mobId = mobEntity.getMobId();
                        if (mobId != null && mobId.equals(data.getId())) {
                            // Проверяем, нужно ли обновлять данные
                            if (mobEntity.getMobData() == null ||
                                    !mobEntity.getMobData().equals(data)) {

                                System.out.println("ClientEventHandler: Updating existing entity " + entity.getId() +
                                        " with mob data for ID: " + data.getId());

                                mobEntity.setMobData(data);
                                updatedEntities++;
                            }
                        }
                    }
                }

                if (updatedEntities > 0) {
                    System.out.println("ClientEventHandler: Updated " + updatedEntities +
                            " entities with mob data: " + data.getId());
                }
            }
        } else {
            System.err.println("ClientEventHandler: Received null mob data from server");
        }
    }

    /**
     * Дополнительный метод для принудительного обновления всех мобов
     */
    public static void refreshAllCustomMobs() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        System.out.println("ClientEventHandler: Refreshing all custom mobs...");

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof CustomMobEntity) {
                CustomMobEntity mobEntity = (CustomMobEntity) entity;
                String mobId = mobEntity.getMobId();

                if (mobId != null && !mobId.isEmpty()) {
                    // Пробуем загрузить данные из кэша
                    com.custommobsforge.custommobsforge.common.data.MobData cachedData =
                            ClientMobDataCache.getMobData(mobId);

                    if (cachedData != null && mobEntity.getMobData() == null) {
                        mobEntity.setMobData(cachedData);
                        System.out.println("ClientEventHandler: Loaded cached data for entity " +
                                entity.getId() + " (mob: " + mobId + ")");
                    }
                }
            }
        }
    }
}
package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Исполнитель узла отображения текста
 * Отображает текст игроку (заголовок и подзаголовок)
 */
public class DisplayTitleNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        if (entity.level().isClientSide) {
            return BehaviorTreeExecutor.NodeStatus.SUCCESS; // Только на сервере
        }

        // Получаем параметры
        String title = getParameter(node, "title", "", String.class);
        String subtitle = getParameter(node, "subtitle", "", String.class);
        String color = getParameter(node, "color", "", String.class);
        int fadeIn = getParameter(node, "fade_in", 20, Integer.class); // тики
        int stay = getParameter(node, "stay", 60, Integer.class); // тики
        int fadeOut = getParameter(node, "fade_out", 20, Integer.class); // тики
        double range = getParameter(node, "range", 16.0, Double.class);
        boolean allPlayers = getParameter(node, "all_players", false, Boolean.class);

        // Находим игроков для отправки сообщения
        List<ServerPlayer> targetPlayers;

        if (allPlayers) {
            // Всем игрокам на сервере
            targetPlayers = entity.level().getServer().getPlayerList().getPlayers();
        } else {
            // Игрокам в радиусе
            targetPlayers = entity.level().getEntitiesOfClass(ServerPlayer.class,
                    entity.getBoundingBox().inflate(range));
        }

        if (targetPlayers.isEmpty()) {
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        if (title.isEmpty()) {
            title = getParameter(node, "titleText", "", String.class);
        }
        if (subtitle.isEmpty()) {
            subtitle = getParameter(node, "subtitleText", "", String.class);
        }

        if (!color.isEmpty() && !title.isEmpty()) {
            title = "§" + getColorCode(color) + title;
        }

        // Создаем компоненты текста
        Component titleComponent = Component.literal(title);
        Component subtitleComponent = Component.literal(subtitle);

        // Отправляем пакеты всем целевым игрокам
        for (ServerPlayer player : targetPlayers) {
            // Устанавливаем анимацию
            player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));

            // Отправляем заголовок
            if (!title.isEmpty()) {
                player.connection.send(new ClientboundSetTitleTextPacket(titleComponent));
            }

            // Отправляем подзаголовок
            if (!subtitle.isEmpty()) {
                player.connection.send(new ClientboundSetSubtitleTextPacket(subtitleComponent));
            }
        }

        System.out.println("[DisplayTitleNode] Displayed title '" + title +
                "' and subtitle '" + subtitle + "' to " + targetPlayers.size() + " players");

        return BehaviorTreeExecutor.NodeStatus.SUCCESS;
    }

    private String getColorCode(String colorName) {
        switch (colorName.toLowerCase()) {
            case "gold": return "6";
            case "blue": return "9";
            case "red": return "c";
            case "green": return "a";
            case "yellow": return "e";
            case "purple": return "5";
            case "white": return "f";
            default: return "f";
        }
    }
}
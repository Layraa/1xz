package com.custommobsforge.custommobsforge.client.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Глобальный менеджер анимаций для обеспечения доступа из разных компонентов
 */
public class GlobalAnimationManager {
    private static final List<AnimationLoaderService.AnimationInfo> animations = new ArrayList<>();
    private static final List<AnimationListener> listeners = new ArrayList<>();

    /**
     * Обновить список анимаций
     */
    public static void updateAnimations(List<AnimationLoaderService.AnimationInfo> newAnimations) {
        animations.clear();
        if (newAnimations != null) {
            animations.addAll(newAnimations);
        }

        // Уведомляем всех слушателей
        notifyListeners();

        System.out.println("GlobalAnimationManager: Updated animations list - " + animations.size() +
                " animations available");
    }

    /**
     * Получить список всех доступных анимаций
     */
    public static List<AnimationLoaderService.AnimationInfo> getAnimations() {
        return new ArrayList<>(animations);
    }

    /**
     * Получить список строковых идентификаторов анимаций
     */
    public static List<String> getAnimationIds() {
        List<String> ids = new ArrayList<>();
        for (AnimationLoaderService.AnimationInfo info : animations) {
            ids.add(info.getId());
        }
        return ids;
    }

    /**
     * Получить информацию об анимации по ее идентификатору
     */
    public static AnimationLoaderService.AnimationInfo getAnimationInfo(String animationId) {
        for (AnimationLoaderService.AnimationInfo info : animations) {
            if (info.getId().equals(animationId)) {
                return info;
            }
        }
        return null;
    }

    /**
     * Добавить слушателя изменений
     */
    public static void addListener(AnimationListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Удалить слушателя
     */
    public static void removeListener(AnimationListener listener) {
        listeners.remove(listener);
    }

    /**
     * Уведомить всех слушателей об изменении списка анимаций
     */
    private static void notifyListeners() {
        for (AnimationListener listener : new ArrayList<>(listeners)) {
            listener.onAnimationsUpdated(animations);
        }
    }

    /**
     * Интерфейс для слушателей изменений списка анимаций
     */
    public interface AnimationListener {
        void onAnimationsUpdated(List<AnimationLoaderService.AnimationInfo> animations);
    }
}
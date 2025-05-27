package com.custommobsforge.custommobsforge.server.animation;

import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import com.custommobsforge.custommobsforge.common.network.NetworkManager;
import com.custommobsforge.custommobsforge.common.network.packet.AnimationSyncPacket;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Контроллер анимаций с системой приоритетов (SERVER SIDE ONLY)
 */
public class AnimationController {

    // Приоритеты анимаций
    public enum Priority {
        CRITICAL(100),  // Смерть, получение урона
        HIGH(75),       // Атаки, особые действия
        MEDIUM(50),     // Общие действия
        LOW(25),        // Ходьба
        LOWEST(0);      // Ожидание

        private final int value;

        Priority(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    private final CustomMobEntity entity;
    private final Map<String, Runnable> completionCallbacks = new ConcurrentHashMap<>();
    private final Map<String, Long> animationStartTimes = new ConcurrentHashMap<>();

    private String currentAnimation = "";
    private Priority currentPriority = Priority.LOWEST;
    private boolean isLooping = true;
    private float animationSpeed = 1.0f;
    private long currentAnimationDuration = 0;

    public AnimationController(CustomMobEntity entity) {
        this.entity = entity;
    }

    /**
     * Воспроизводит анимацию с приоритетом
     */
    public boolean playAnimation(String animationId, Priority priority, boolean loop, float speed, Runnable onComplete) {
        // Проверка аргументов
        if (animationId == null || animationId.isEmpty()) {
            return false;
        }

        // Проверка приоритета текущей анимации
        if (!currentAnimation.isEmpty() && currentPriority.getValue() > priority.getValue()) {
            System.out.println("[AnimationController] Animation " + animationId +
                    " blocked by higher priority animation " + currentAnimation);
            return false;
        }

        // Если та же анимация уже воспроизводится с тем же приоритетом, пропускаем
        if (animationId.equals(currentAnimation) && priority == currentPriority) {
            return true;
        }

        // Завершаем текущую анимацию
        if (!currentAnimation.isEmpty()) {
            handleAnimationInterrupted(currentAnimation);
        }

        // Устанавливаем новую анимацию
        currentAnimation = animationId;
        currentPriority = priority;
        isLooping = loop;
        animationSpeed = speed;

        // Оцениваем длительность анимации
        currentAnimationDuration = estimateAnimationDuration(animationId, speed);

        // Запускаем анимацию через базовую систему CustomMobEntity
        entity.setAnimation(animationId, loop, speed);

        // Сохраняем время начала
        long startTime = System.currentTimeMillis();
        animationStartTimes.put(animationId, startTime);

        // Для зацикленных анимаций вызываем колбэк сразу
        if (loop && onComplete != null) {
            onComplete.run();
        } else if (onComplete != null) {
            // Для незацикленных анимаций сохраняем колбэк
            completionCallbacks.put(animationId, onComplete);
        }

        System.out.println("[AnimationController] Started animation: " + animationId +
                " (priority: " + priority + ", loop: " + loop + ", speed: " + speed + ")");

        return true;
    }

    /**
     * Упрощенные методы
     */
    public boolean playAnimation(String animationId, Priority priority, boolean loop, float speed) {
        return playAnimation(animationId, priority, loop, speed, null);
    }

    public boolean playAnimation(String animationId, Priority priority) {
        return playAnimation(animationId, priority, false, 1.0f, null);
    }

    public boolean playAnimationWithCallback(String animationId, Priority priority, Runnable onComplete) {
        return playAnimation(animationId, priority, false, 1.0f, onComplete);
    }

    /**
     * Останавливает текущую анимацию
     */
    public void stopAnimation() {
        if (!currentAnimation.isEmpty()) {
            handleAnimationInterrupted(currentAnimation);
            resetToIdle();
        }
    }

    /**
     * Принудительно останавливает анимацию (игнорирует приоритеты)
     */
    public void forceStopAnimation() {
        if (!currentAnimation.isEmpty()) {
            handleAnimationInterrupted(currentAnimation);
            currentAnimation = "";
            currentPriority = Priority.LOWEST;
        }
    }

    /**
     * Обновление в tick() - проверяет завершение анимаций
     */
    public void update() {
        if (currentAnimation.isEmpty() || isLooping) {
            return;
        }

        // Проверяем, завершилась ли незацикленная анимация
        Long startTime = animationStartTimes.get(currentAnimation);
        if (startTime != null) {
            long elapsedTime = System.currentTimeMillis() - startTime;

            if (elapsedTime >= currentAnimationDuration) {
                // Анимация завершилась
                String completedAnimation = currentAnimation;

                // Вызываем колбэк
                Runnable callback = completionCallbacks.remove(completedAnimation);
                if (callback != null) {
                    try {
                        callback.run();
                    } catch (Exception e) {
                        System.err.println("[AnimationController] Error in completion callback: " + e.getMessage());
                    }
                }

                // Очищаем данные
                animationStartTimes.remove(completedAnimation);

                System.out.println("[AnimationController] Animation completed: " + completedAnimation);

                // Возвращаемся к состоянию покоя
                resetToIdle();
            }
        }
    }

    /**
     * Определяет приоритет анимации по ее ID/типу
     */
    public Priority determinePriority(String animationId) {
        if (animationId == null) {
            return Priority.LOWEST;
        }

        String lowerAnimId = animationId.toLowerCase();

        // Критические анимации
        if (lowerAnimId.contains("death") || lowerAnimId.contains("die")) {
            return Priority.CRITICAL;
        }
        if (lowerAnimId.contains("hurt") || lowerAnimId.contains("damage")) {
            return Priority.CRITICAL;
        }

        // Высокий приоритет
        if (lowerAnimId.contains("attack") || lowerAnimId.contains("slash") ||
                lowerAnimId.contains("strike") || lowerAnimId.contains("hit")) {
            return Priority.HIGH;
        }
        if (lowerAnimId.contains("cast") || lowerAnimId.contains("spell") ||
                lowerAnimId.contains("magic")) {
            return Priority.HIGH;
        }
        if (lowerAnimId.contains("special") || lowerAnimId.contains("ultimate") ||
                lowerAnimId.contains("summon")) {
            return Priority.HIGH;
        }

        // Средний приоритет
        if (lowerAnimId.contains("block") || lowerAnimId.contains("defend")) {
            return Priority.MEDIUM;
        }
        if (lowerAnimId.contains("jump") || lowerAnimId.contains("dodge")) {
            return Priority.MEDIUM;
        }

        // Низкий приоритет
        if (lowerAnimId.contains("walk") || lowerAnimId.contains("run") ||
                lowerAnimId.contains("move")) {
            return Priority.LOW;
        }

        // Самый низкий приоритет
        if (lowerAnimId.contains("idle") || lowerAnimId.contains("stand")) {
            return Priority.LOWEST;
        }

        // По умолчанию средний приоритет
        return Priority.MEDIUM;
    }

    /**
     * Возвращается к анимации покоя
     */
    private void resetToIdle() {
        currentAnimation = "";
        currentPriority = Priority.LOWEST;

        // Можно автоматически запустить IDLE анимацию
        if (entity.getMobData() != null && entity.getMobData().getAnimations() != null) {
            var idleMapping = entity.getMobData().getAnimations().get("IDLE");
            if (idleMapping != null) {
                playAnimation(idleMapping.getAnimationName(), Priority.LOWEST,
                        idleMapping.isLoop(), idleMapping.getSpeed());
            }
        }
    }

    /**
     * Обрабатывает прерывание анимации
     */
    private void handleAnimationInterrupted(String animationId) {
        // Удаляем колбэк для прерванной анимации
        completionCallbacks.remove(animationId);
        animationStartTimes.remove(animationId);

        System.out.println("[AnimationController] Animation interrupted: " + animationId);
    }

    /**
     * Оценивает длительность анимации
     */
    private long estimateAnimationDuration(String animationId, float speed) {
        if (animationId == null || animationId.isEmpty()) {
            return 1000L;
        }

        String lowerAnimId = animationId.toLowerCase();
        long baseDuration = 2000L; // 2 секунды по умолчанию

        // Определяем примерную длительность по имени
        if (lowerAnimId.contains("summon") || lowerAnimId.contains("spawn") ||
                lowerAnimId.contains("cast")) {
            baseDuration = 3000L;
        } else if (lowerAnimId.contains("attack") || lowerAnimId.contains("slash") ||
                lowerAnimId.contains("strike")) {
            baseDuration = 1200L;
        } else if (lowerAnimId.contains("death") || lowerAnimId.contains("die")) {
            baseDuration = 3500L;
        } else if (lowerAnimId.contains("hurt") || lowerAnimId.contains("damage")) {
            baseDuration = 800L;
        } else if (lowerAnimId.contains("block") || lowerAnimId.contains("defend")) {
            baseDuration = 1500L;
        } else if (lowerAnimId.contains("jump") || lowerAnimId.contains("dodge")) {
            baseDuration = 1000L;
        }

        // Корректируем на скорость
        return (long)(baseDuration / (speed > 0.1f ? speed : 1.0f));
    }

    // Геттеры
    public String getCurrentAnimation() {
        return currentAnimation;
    }

    public Priority getCurrentPriority() {
        return currentPriority;
    }

    public boolean isPlaying(String animationId) {
        return currentAnimation.equals(animationId);
    }

    public boolean isLooping() {
        return isLooping;
    }

    public float getAnimationSpeed() {
        return animationSpeed;
    }

    public boolean hasActiveAnimation() {
        return !currentAnimation.isEmpty();
    }

    /**
     * Проверяет, может ли анимация прервать текущую
     */
    public boolean canInterrupt(Priority newPriority) {
        return newPriority.getValue() >= currentPriority.getValue();
    }
}
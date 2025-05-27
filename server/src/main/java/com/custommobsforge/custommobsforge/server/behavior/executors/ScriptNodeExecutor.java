package com.custommobsforge.custommobsforge.server.behavior.executors;

import com.custommobsforge.custommobsforge.server.behavior.BehaviorTreeExecutor;
import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Исполнитель узла скриптов
 * Выполняет пользовательский скрипт для специфического поведения
 */
public class ScriptNodeExecutor implements NodeExecutor {

    @Override
    public BehaviorTreeExecutor.NodeStatus execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем параметры скрипта
        String scriptType = getParameter(node, "script_type", "command", String.class);
        String script = getParameter(node, "script", "", String.class);
        boolean async = getParameter(node, "async", false, Boolean.class);

        if (script.isEmpty()) {
            System.err.println("[ScriptNode] No script specified");
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        System.out.println("[ScriptNode] Executing " + scriptType + " script: " + script);

        try {
            switch (scriptType.toLowerCase()) {
                case "command":
                case "minecraft_command":
                    return executeMinecraftCommand(entity, script);

                case "javascript":
                case "js":
                    return executeJavaScript(entity, script, executor);

                default:
                    System.err.println("[ScriptNode] Unsupported script type: " + scriptType);
                    return BehaviorTreeExecutor.NodeStatus.FAILURE;
            }
        } catch (Exception e) {
            System.err.println("[ScriptNode] Error executing script: " + e.getMessage());
            e.printStackTrace();
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }
    }

    /**
     * Выполняет команду Minecraft
     */
    private BehaviorTreeExecutor.NodeStatus executeMinecraftCommand(CustomMobEntity entity, String command) {
        if (entity.level().isClientSide || !(entity.level() instanceof ServerLevel)) {
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }

        ServerLevel serverLevel = (ServerLevel) entity.level();

        // Создаем CommandSourceStack для выполнения команды от имени моба
        CommandSourceStack commandSource = new CommandSourceStack(
                CommandSource.NULL,
                new Vec3(entity.getX(), entity.getY(), entity.getZ()),
                new Vec2(entity.getXRot(), entity.getYRot()),
                serverLevel,
                2, // Уровень прав (оператор)
                entity.getName().getString(),
                entity.getDisplayName(),
                serverLevel.getServer(),
                entity
        );

        try {
            // Заменяем плейсхолдеры в команде
            String processedCommand = processCommandPlaceholders(command, entity);

            // Выполняем команду
            int result = serverLevel.getServer().getCommands().performPrefixedCommand(
                    commandSource, processedCommand
            );

            return result > 0 ? BehaviorTreeExecutor.NodeStatus.SUCCESS : BehaviorTreeExecutor.NodeStatus.FAILURE;

        } catch (Exception e) {
            System.err.println("[ScriptNode] Error executing command '" + command + "': " + e.getMessage());
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }
    }

    /**
     * Упрощенная реализация JavaScript (базовые операции)
     */
    private BehaviorTreeExecutor.NodeStatus executeJavaScript(CustomMobEntity entity, String script, BehaviorTreeExecutor executor) {
        // Простая реализация для базовых операций
        // В реальном проекте можно использовать Nashorn или GraalVM

        try {
            // Заменяем переменные в скрипте
            String processedScript = processScriptVariables(script, entity, executor);

            // Выполняем простые операции
            if (processedScript.contains("entity.setHealth(")) {
                float health = extractFloatFromFunction(processedScript, "entity.setHealth(", ")");
                entity.setHealth(health);
                return BehaviorTreeExecutor.NodeStatus.SUCCESS;
            }

            if (processedScript.contains("entity.teleport(")) {
                String coords = extractStringFromFunction(processedScript, "entity.teleport(", ")");
                String[] parts = coords.split(",");
                if (parts.length >= 3) {
                    double x = Double.parseDouble(parts[0].trim());
                    double y = Double.parseDouble(parts[1].trim());
                    double z = Double.parseDouble(parts[2].trim());
                    entity.teleportTo(x, y, z);
                    return BehaviorTreeExecutor.NodeStatus.SUCCESS;
                }
            }

            if (processedScript.contains("blackboard.setValue(")) {
                // Пример: blackboard.setValue("key", "value")
                String params = extractStringFromFunction(processedScript, "blackboard.setValue(", ")");
                String[] parts = params.split(",", 2);
                if (parts.length >= 2) {
                    String key = parts[0].trim().replace("\"", "");
                    String value = parts[1].trim().replace("\"", "");
                    executor.getBlackboard().setValue(key, value);
                    return BehaviorTreeExecutor.NodeStatus.SUCCESS;
                }
            }

            // Добавьте другие простые операции по необходимости

            System.out.println("[ScriptNode] JavaScript executed: " + processedScript);
            return BehaviorTreeExecutor.NodeStatus.SUCCESS;

        } catch (Exception e) {
            System.err.println("[ScriptNode] Error executing JavaScript: " + e.getMessage());
            return BehaviorTreeExecutor.NodeStatus.FAILURE;
        }
    }

    /**
     * Обрабатывает плейсхолдеры в команде
     */
    private String processCommandPlaceholders(String command, CustomMobEntity entity) {
        return command
                .replace("{x}", String.valueOf((int) entity.getX()))
                .replace("{y}", String.valueOf((int) entity.getY()))
                .replace("{z}", String.valueOf((int) entity.getZ()))
                .replace("{entity_id}", String.valueOf(entity.getId()))
                .replace("{entity_name}", entity.getName().getString())
                .replace("{health}", String.valueOf(entity.getHealth()))
                .replace("{max_health}", String.valueOf(entity.getMaxHealth()));
    }

    /**
     * Обрабатывает переменные в скрипте
     */
    private String processScriptVariables(String script, CustomMobEntity entity, BehaviorTreeExecutor executor) {
        return script
                .replace("entity.x", String.valueOf(entity.getX()))
                .replace("entity.y", String.valueOf(entity.getY()))
                .replace("entity.z", String.valueOf(entity.getZ()))
                .replace("entity.health", String.valueOf(entity.getHealth()))
                .replace("entity.maxHealth", String.valueOf(entity.getMaxHealth()));
    }

    /**
     * Извлекает float значение из функции
     */
    private float extractFloatFromFunction(String script, String functionStart, String functionEnd) {
        int start = script.indexOf(functionStart) + functionStart.length();
        int end = script.indexOf(functionEnd, start);
        String value = script.substring(start, end).trim();
        return Float.parseFloat(value);
    }

    /**
     * Извлекает строку из функции
     */
    private String extractStringFromFunction(String script, String functionStart, String functionEnd) {
        int start = script.indexOf(functionStart) + functionStart.length();
        int end = script.indexOf(functionEnd, start);
        return script.substring(start, end).trim();
    }
}
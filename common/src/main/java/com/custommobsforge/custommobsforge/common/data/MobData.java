package com.custommobsforge.custommobsforge.common.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.HashMap;
import java.util.Map;

public class MobData {
    private static final Gson GSON = new GsonBuilder().create();

    private String id;
    private String name;
    private String modelPath;
    private String texturePath;
    private String animationFilePath;
    private Map<String, AnimationMapping> animations = new HashMap<>();
    private BehaviorTree behaviorTree;  // ВОЗВРАЩАЕМ
    private Map<String, Float> attributes = new HashMap<>();

    // Конструкторы
    public MobData() {
        // Пустой конструктор для Gson
    }

    public MobData(String id, String name) {
        this.id = id;
        this.name = name;
    }

    // Геттеры и сеттеры
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getModelPath() { return modelPath; }
    public void setModelPath(String modelPath) { this.modelPath = modelPath; }

    public String getTexturePath() { return texturePath; }
    public void setTexturePath(String texturePath) { this.texturePath = texturePath; }

    public String getAnimationFilePath() { return animationFilePath; }
    public void setAnimationFilePath(String animationFilePath) { this.animationFilePath = animationFilePath; }

    public Map<String, AnimationMapping> getAnimations() { return animations; }
    public void setAnimations(Map<String, AnimationMapping> animations) { this.animations = animations; }

    public BehaviorTree getBehaviorTree() { return behaviorTree; }  // ВОЗВРАЩАЕМ
    public void setBehaviorTree(BehaviorTree behaviorTree) { this.behaviorTree = behaviorTree; }  // ВОЗВРАЩАЕМ

    public Map<String, Float> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Float> attributes) { this.attributes = attributes; }

    // Методы сериализации
    public void writeToBuffer(FriendlyByteBuf buffer) {
        buffer.writeUtf(id);
        buffer.writeUtf(name);
        buffer.writeUtf(modelPath != null ? modelPath : "");
        buffer.writeUtf(texturePath != null ? texturePath : "");
        buffer.writeUtf(animationFilePath != null ? animationFilePath : "");

        // Сериализуем атрибуты
        buffer.writeInt(attributes.size());
        for (Map.Entry<String, Float> entry : attributes.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeFloat(entry.getValue());
        }

        // Сериализуем анимации
        buffer.writeInt(animations.size());
        for (Map.Entry<String, AnimationMapping> entry : animations.entrySet()) {
            buffer.writeUtf(entry.getKey());
            entry.getValue().writeToBuffer(buffer);
        }

        // ВОЗВРАЩАЕМ: Сериализуем дерево поведения если оно есть
        buffer.writeBoolean(behaviorTree != null);
        if (behaviorTree != null) {
            behaviorTree.writeToBuffer(buffer);
        }
    }

    public static MobData readFromBuffer(FriendlyByteBuf buffer) {
        MobData data = new MobData();
        data.id = buffer.readUtf();
        data.name = buffer.readUtf();
        data.modelPath = buffer.readUtf();
        data.texturePath = buffer.readUtf();
        data.animationFilePath = buffer.readUtf();

        // Десериализуем атрибуты
        int attrCount = buffer.readInt();
        for (int i = 0; i < attrCount; i++) {
            String key = buffer.readUtf();
            float value = buffer.readFloat();
            data.attributes.put(key, value);
        }

        // Десериализуем анимации
        int animCount = buffer.readInt();
        for (int i = 0; i < animCount; i++) {
            String key = buffer.readUtf();
            AnimationMapping mapping = AnimationMapping.readFromBuffer(buffer);
            data.animations.put(key, mapping);
        }

        // ВОЗВРАЩАЕМ: Десериализуем дерево поведения если оно есть
        boolean hasBehaviorTree = buffer.readBoolean();
        if (hasBehaviorTree) {
            data.behaviorTree = BehaviorTree.readFromBuffer(buffer);
        }

        return data;
    }

    public CompoundTag writeToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("name", name);
        tag.putString("modelPath", modelPath != null ? modelPath : "");
        tag.putString("texturePath", texturePath != null ? texturePath : "");
        tag.putString("animationFilePath", animationFilePath != null ? animationFilePath : "");

        // Сохраняем атрибуты
        CompoundTag attributesTag = new CompoundTag();
        for (Map.Entry<String, Float> entry : attributes.entrySet()) {
            attributesTag.putFloat(entry.getKey(), entry.getValue());
        }
        tag.put("attributes", attributesTag);

        // Сохраняем анимации как JSON
        tag.putString("animations", GSON.toJson(animations));

        // ВОЗВРАЩАЕМ: Сохраняем дерево поведения как JSON
        if (behaviorTree != null) {
            tag.putString("behaviorTree", GSON.toJson(behaviorTree));
        }

        return tag;
    }

    public static MobData readFromNBT(CompoundTag tag) {
        MobData data = new MobData();
        data.id = tag.getString("id");
        data.name = tag.getString("name");
        data.modelPath = tag.getString("modelPath");
        data.texturePath = tag.getString("texturePath");
        data.animationFilePath = tag.getString("animationFilePath");

        // Загружаем атрибуты
        CompoundTag attributesTag = tag.getCompound("attributes");
        for (String key : attributesTag.getAllKeys()) {
            data.attributes.put(key, attributesTag.getFloat(key));
        }

        // Загружаем анимации из JSON
        String animationsJson = tag.getString("animations");
        if (!animationsJson.isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, AnimationMapping> animations = GSON.fromJson(
                        animationsJson,
                        new com.google.gson.reflect.TypeToken<HashMap<String, AnimationMapping>>(){}.getType()
                );
                data.animations = animations;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // ВОЗВРАЩАЕМ: Загружаем дерево поведения из JSON
        if (tag.contains("behaviorTree")) {
            String behaviorTreeJson = tag.getString("behaviorTree");
            if (!behaviorTreeJson.isEmpty()) {
                data.behaviorTree = GSON.fromJson(behaviorTreeJson, BehaviorTree.class);
            }
        }

        return data;
    }

    // Дополнительные методы
    public void addAnimation(String action, String animationName, boolean loop, float speed) {
        AnimationMapping mapping = new AnimationMapping(animationName, loop, speed);
        animations.put(action, mapping);
    }

    public AnimationMapping getAnimation(String action) {
        return animations.get(action);
    }

    public void setAttribute(String name, float value) {
        attributes.put(name, value);
    }

    public float getAttribute(String name, float defaultValue) {
        return attributes.getOrDefault(name, defaultValue);
    }
}
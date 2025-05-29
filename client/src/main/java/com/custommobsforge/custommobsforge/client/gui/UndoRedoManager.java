package com.custommobsforge.custommobsforge.client.gui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.util.ArrayList;
import java.util.List;

public class UndoRedoManager {
    private List<Operation> operations = new ArrayList<>();
    private int currentIndex = -1;

    private BooleanProperty canUndo = new SimpleBooleanProperty(false);
    private BooleanProperty canRedo = new SimpleBooleanProperty(false);

    /**
     * Регистрирует операцию для возможности отмены/повтора
     */
    public void registerOperation(Runnable undoAction, Runnable redoAction) {
        // Удаляем все операции после текущей, если они есть
        if (currentIndex < operations.size() - 1) {
            operations = new ArrayList<>(operations.subList(0, currentIndex + 1));
        }

        operations.add(new Operation(undoAction, redoAction));
        currentIndex = operations.size() - 1;

        updateProperties();
    }

    /**
     * Отменяет последнюю операцию
     */
    public void undo() {
        if (canUndo.get() && currentIndex >= 0) {
            operations.get(currentIndex).undo();
            currentIndex--;
            updateProperties();
        }
    }

    /**
     * Повторяет последнюю отмененную операцию
     */
    public void redo() {
        if (canRedo.get() && currentIndex < operations.size() - 1) {
            currentIndex++;
            operations.get(currentIndex).redo();
            updateProperties();
        }
    }

    /**
     * Обновляет свойства возможности отмены/повтора
     */
    private void updateProperties() {
        canUndo.set(currentIndex >= 0);
        canRedo.set(currentIndex < operations.size() - 1);
    }

    /**
     * Свойство возможности отмены
     */
    public BooleanProperty canUndoProperty() {
        return canUndo;
    }

    /**
     * Свойство возможности повтора
     */
    public BooleanProperty canRedoProperty() {
        return canRedo;
    }

    /**
     * Внутренний класс операции для отмены/повтора
     */
    private static class Operation {
        private final Runnable undoAction;
        private final Runnable redoAction;

        public Operation(Runnable undoAction, Runnable redoAction) {
            this.undoAction = undoAction;
            this.redoAction = redoAction;
        }

        public void undo() {
            if (undoAction != null) {
                undoAction.run();
            }
        }

        public void redo() {
            if (redoAction != null) {
                redoAction.run();
            }
        }
    }
}
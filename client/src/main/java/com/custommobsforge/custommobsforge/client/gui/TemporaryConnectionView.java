package com.custommobsforge.custommobsforge.client.gui;

import javafx.geometry.Point2D;
import javafx.scene.input.MouseButton;
import javafx.scene.shape.*;

public class TemporaryConnectionView extends Path {
    private final NodeView sourceView;
    private Point2D currentPos;
    private Runnable onCancelled;

    // Используем флаг, чтобы отложить рисование до первого обновления
    private boolean readyToDraw = false;

    public TemporaryConnectionView(NodeView sourceView) {
        this.sourceView = sourceView;

        // Применяем CSS-стили
        getStyleClass().add(Constants.TEMP_CONNECTION_STYLE_CLASS);

        // Не рисуем путь сразу - это предотвратит скачок в угол
        // Вместо этого ждем первого обновления позиции

        // Используем положение выходного пина в качестве начальной позиции конца
        Circle outputPin = sourceView.getOutputPin();
        Point2D pinPos = null;
        if (sourceView.getScene() != null && sourceView.getParent() != null) {
            // Пытаемся получить абсолютную позицию пина и преобразовать её
            pinPos = outputPin.localToScene(
                    outputPin.getBoundsInLocal().getCenterX(),
                    outputPin.getBoundsInLocal().getCenterY()
            );
            // Если родитель есть, преобразуем координаты сцены в координаты родителя
            if (getParent() != null) {
                pinPos = getParent().sceneToLocal(pinPos);
            }
        } else {
            // Если нет сцены, используем локальные координаты
            pinPos = new Point2D(
                    outputPin.getBoundsInLocal().getCenterX(),
                    outputPin.getBoundsInLocal().getCenterY()
            );
        }
        currentPos = pinPos;

        // Добавляем обработчик для отмены соединения по правому клику
        setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                if (onCancelled != null) {
                    onCancelled.run();
                }
                event.consume();
            }
        });
    }

    public void updateEndPoint(Point2D screenPoint) {
        if (getParent() != null) {
            // Обновляем текущую позицию
            currentPos = getParent().sceneToLocal(screenPoint);

            // Если это первое обновление, ставим флаг готовности к рисованию
            if (!readyToDraw) {
                readyToDraw = true;
            }

            // Обновляем путь
            updatePath();
        }
    }

    private void createPath() {
        // Если мы не готовы рисовать, просто выходим
        if (!readyToDraw) return;

        // Получаем точную позицию выходного пина
        Circle outputPin = sourceView.getOutputPin();
        Point2D pinSceneCoords = outputPin.localToScene(
                outputPin.getBoundsInLocal().getCenterX(),
                outputPin.getBoundsInLocal().getCenterY()
        );
        Point2D startPos = getParent().sceneToLocal(pinSceneCoords);

        // Очищаем элементы пути
        getElements().clear();

        // Создаем путь
        getElements().add(new MoveTo(startPos.getX(), startPos.getY()));

        // Создаем кривую Безье
        double midX = (startPos.getX() + currentPos.getX()) / 2;

        getElements().add(new CubicCurveTo(
                midX, startPos.getY(),
                midX, currentPos.getY(),
                currentPos.getX(), currentPos.getY()
        ));
    }

    public void updatePath() {
        // Создаем или обновляем путь
        createPath();
    }

    public void setOnCancelled(Runnable handler) {
        onCancelled = handler;
    }
}
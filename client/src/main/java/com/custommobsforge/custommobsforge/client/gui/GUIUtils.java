package com.custommobsforge.custommobsforge.client.gui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import java.util.Optional;

public class GUIUtils {
    /**
     * Показывает диалоговое окно с сообщением
     */
    public static void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Показывает временное уведомление поверх указанной панели
     */
    public static void showNotification(Pane parent, String message) {
        Label notification = new Label(message);
        notification.getStyleClass().add(Constants.NOTIFICATION_STYLE_CLASS);
        notification.setLayoutX(20);
        notification.setLayoutY(60);

        parent.getChildren().add(notification);

        // Анимация появления
        FadeTransition fadeIn = new FadeTransition(Duration.millis(Constants.ANIMATION_DURATION_SHORT), notification);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        fadeIn.play();

        // Задержка перед исчезновением
        PauseTransition pause = new PauseTransition(Duration.millis(Constants.NOTIFICATION_DURATION));
        pause.setOnFinished(e -> {
            // Анимация исчезновения
            FadeTransition fadeOut = new FadeTransition(Duration.millis(Constants.ANIMATION_DURATION_SHORT), notification);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(ev -> parent.getChildren().remove(notification));
            fadeOut.play();
        });
        pause.play();
    }

    /**
     * Показывает диалоговое окно с подтверждением
     * @param title Заголовок
     * @param message Сообщение
     * @return true если пользователь подтвердил, false если отменил
     */
    public static boolean showConfirmDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
}
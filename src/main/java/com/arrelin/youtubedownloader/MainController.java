package com.arrelin.youtubedownloader;


import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import com.arrelin.youtubedownloader.helpers.DPAPI;

public class MainController {

    @FXML private TextField urlField;
    @FXML private TextArea logArea;

    @FXML
    protected void onDownloadButtonClick() {
        String url = urlField.getText();

        if (url == null || url.isBlank()) {
            appendLog("❗ Вставьте ссылку на видео.");
            return;
        }

        appendLog("🚀 Запускаем загрузку...");

        new Thread(() -> {
            try {
                File ytDlp = extractYtDlpFromResources();

                ProcessBuilder pb = new ProcessBuilder(
                    ytDlp.getAbsolutePath(),
                    "--cookies", "cookies.txt",
                    "-f", "bestvideo+bestaudio/best",
                    url
                );

                pb.redirectErrorStream(true);
                Process process = pb.start();

                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String finalLine = line;
                        Platform.runLater(() -> appendLog(finalLine));
                    }
                }

                int exitCode = process.waitFor();
                Platform.runLater(() -> appendLog("✅ Загрузка завершена. Код: " + exitCode));

            } catch (Exception e) {
                Platform.runLater(() -> appendLog("❌ Ошибка: " + e.getMessage()));
            }
        }).start();
    }

    @FXML
    protected void onExitButtonClick() {
        System.exit(0);
    }

    private void appendLog(String message) {
        Platform.runLater(() -> logArea.appendText(message + "\n"));
    }

    private File extractYtDlpFromResources() throws IOException {
        // путь к ресурсу должен совпадать с его положением в resources/
        String resourcePath = "/com/arrelin/youtubedownloader/yt-dlp.exe";

        InputStream input = getClass().getResourceAsStream(resourcePath);
        if (input == null) {
            throw new FileNotFoundException("Не найден yt-dlp.exe в ресурсах: " + resourcePath);
        }

        File tempFile = new File(System.getProperty("java.io.tmpdir"), "yt-dlp.exe");
        Files.copy(input, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        tempFile.setExecutable(true);

        return tempFile;
    }

    @FXML
    protected void onLoadCookiesClick() {
        appendLog("🔐 Извлечение cookies из Chrome...");

        try {
            DPAPI.extractYouTubeCookies();
            appendLog("✅ Cookies успешно извлечены и сохранены в cookies.txt");
        } catch (Exception e) {
            appendLog("❌ Ошибка при загрузке cookies: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

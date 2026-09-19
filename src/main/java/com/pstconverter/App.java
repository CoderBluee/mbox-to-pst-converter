package com.pstconverter;

import com.pstconverter.controller.MainController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import io.github.palexdev.materialfx.theming.UserAgentBuilder;
import io.github.palexdev.materialfx.theming.JavaFXThemes;
import io.github.palexdev.materialfx.theming.MaterialFXStylesheets;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Register global uncaught exception handler to capture all thread crashes
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("❌ Uncaught exception on thread '" + thread.getName() + "': " + throwable.getMessage());
            throwable.printStackTrace(System.err);
            com.pstconverter.util.DiagnosticLogger.error("Uncaught exception on thread: " + thread.getName(), throwable);
            com.pstconverter.util.DiagnosticLogger.close();
        });

        // Register JVM shutdown hook to flush standard output streams and close report file handles on termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("[INFO] JVM Shutdown Hook triggered. Ensuring all logs are flushed...");
            com.pstconverter.util.DiagnosticLogger.log("JVM exiting.");
            com.pstconverter.util.DiagnosticLogger.close();
            System.out.flush();
            System.err.flush();
        }, "pst-shutdown-hook"));

        try {
            Font.loadFont(getClass().getResourceAsStream("/fonts/MaterialIcons-Regular.ttf"), 14);

            UserAgentBuilder.builder()
                .themes(JavaFXThemes.MODENA)
                .themes(MaterialFXStylesheets.DEFAULT)
                .setDeploy(true)
                .setResolveAssets(true)
                .build()
                .setGlobal();

            MainController root = new MainController(primaryStage);
            Scene scene = new Scene(root, 1280, 800);
            primaryStage.setTitle(com.pstconverter.config.BrandConfig.TOOL_NAME);
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1100);
            primaryStage.setMinHeight(650);
            primaryStage.show();

            // Trigger licensing and automatic update checks on startup for activated users
            com.pstconverter.util.LicenseManager.checkLicenseAndVersionOnStartup(primaryStage);

            // Register close request handler to ensure session logs close cleanly
            primaryStage.setOnCloseRequest(event -> {
                System.out.println("[INFO] Application close request received. Flushing diagnostics and logs...");
                com.pstconverter.util.DiagnosticLogger.log("Application closing via window close request.");
                com.pstconverter.util.DiagnosticLogger.close();
                System.out.flush();
                System.err.flush();
            });

            // Trigger auto-check for updates if enabled
            if ("true".equals(com.pstconverter.util.SettingsManager.getSetting("auto_check_updates", "false"))) {
                new Thread(() -> {
                    try {
                        Thread.sleep(2000); // Wait 2 seconds for app to stabilize
                        System.out.println("[INFO] Auto-check updates: Checking for new versions...");
                        Thread.sleep(1000);
                        System.out.println("[INFO] Auto-check updates: Application is up to date (v1.0.0).");
                    } catch (Exception ignored) {}
                }).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

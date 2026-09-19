package com.pstconverter.controller;

import com.pstconverter.controller.MainController;
import com.pstconverter.controller.conversion.*;
import com.pstconverter.core.model.SourceFileModel;
import com.pstconverter.model.MailMessage;
import com.pstconverter.util.MaterialIcons;
import com.pstconverter.core.adapter.SourceAdapter;
import com.pstconverter.core.adapter.SourceAdapterFactory;
import com.pstconverter.core.filter.FilterEngine;
import com.pstconverter.core.destination.DestinationAdapter;
import com.pstconverter.core.destination.EmailDestinationConfig;
import com.pstconverter.core.destination.DestinationAdapterFactory;
import com.pstconverter.view.Step2ExplorerView;
import com.pstconverter.view.Step3FilterView;
import com.pstconverter.view.Step4DestinationView;
import com.pstconverter.view.Step5ConversionView;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thin coordinator for the Step 5 conversion process.
 * Owns the UI controls and wires together the extracted conversion components:
 * <ul>
 *   <li>{@link ConversionConfig} — immutable settings snapshot</li>
 *   <li>{@link ConversionTelemetryTracker} — atomic counters & skip tracking</li>
 *   <li>{@link ConversionSessionTracker} — SQLite session persistence</li>
 *   <li>{@link CloudUploadHandler} — cloud connection & internet monitoring</li>
 *   <li>{@link MessageConsumerWorker} — per-message processing</li>
 *   <li>{@link MessageProducerLoop} — folder iteration & queue feeding</li>
 * </ul>
 */
public class ConversionTaskManager {

    // ── State ─────────────────────────────────────────────────────────────────
    private final MainController controller;
    private final ConversionUIContext ui;
    private Task<Void> conversionTask;
    private final AtomicBoolean stopRequested  = new AtomicBoolean(false);
    private final AtomicBoolean pauseRequested = new AtomicBoolean(false);
    private final AtomicBoolean isParallelMode = new AtomicBoolean(false);
    private int                 totalFoldersToProcess;

    // ── Extracted components ──────────────────────────────────────────────────
    private final ConversionTelemetryTracker telemetry = new ConversionTelemetryTracker();
    private final ConversionSessionTracker sessions = new ConversionSessionTracker();
    private CloudUploadHandler cloudHandler;

    // ── Left panel ────────────────────────────────────────────────────────────
    private TreeItem<String>       rootItem = new TreeItem<>("Root");
    private int                    totalFilesCount;
    private final List<File>       uniqueFiles = new ArrayList<>();

    private boolean  shouldAutoResume = false;

    public void setShouldAutoResume(boolean val) {
        this.shouldAutoResume = val;
    }

    // ── Timers / counters ─────────────────────────────────────────────────
    private Timeline elapsedTimer;
    private long     startTimeMs;
    private long     totalPausedMs = 0;
    private long     currentPauseStartMs = 0;
    private long     totalItems;

    // ── Folder conversion data ────────────────────────────────────────────
    private final Map<String, TreeItem<String>> folderNodeMap = new LinkedHashMap<>();
    private final Map<String, Integer>          folderItemCounts = new LinkedHashMap<>();
    private final Map<String, TreeItem<String>> originalFolderNodeMap = new LinkedHashMap<>();
    private final Map<String, Integer>          actualFolderItemCounts = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, File>             folderToFileMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<File, List<String>>       fileFoldersMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<String>                   activeFolders = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // ── Resolved output path (from Step4) ────────────────────────────────
    private String resolvedOutputPath = null;

    // ── Polling UI state variables ──────────────────────────────────────────
    private final AtomicReference<String> latestSubject = new AtomicReference<>("");

    private final MessageConsumerWorker.VolatileProgressState progressState = new MessageConsumerWorker.VolatileProgressState();

    // ─────────────────────────────────────────────────────────────────────────

    public ConversionTaskManager(MainController controller, ConversionUIContext ui) {
        this.controller = controller;
        this.ui = ui;

        // Add scan listener to dynamically show matching filter counts
        controller.addScanListener(() -> Platform.runLater(() -> {
            Integer matching = controller.getLastScannedMatchingCount();
            if (matching != null) {
                ui.setSessionItems("Total items: " + totalItems + " (" + matching + " matching filters)");
            } else if (controller.isScanningFilters()) {
                ui.setSessionItems("Total items: " + totalItems + " (Scanning...)");
            } else {
                ui.setSessionItems("Total items: " + totalItems);
            }
        }));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UI CONSTRUCTION
    // ═══════════════════════════════════════════════════════════════════════




    // ── LOG FILE HELPER ──────────────────────────────────────────────────────

    public void openMigrationLog() {
        File sessionDir = controller.getSessionDir();
        if (sessionDir != null && sessionDir.exists()) {
            File logFile = new File(new File(sessionDir, "logs"), "session_run.log");
            System.out.println("Open Migration Log action triggered. Opening log file: " + logFile.getAbsolutePath());
            if (logFile.exists()) {
                try {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                        Desktop.getDesktop().open(logFile);
                    } else {
                        new ProcessBuilder("open", logFile.getAbsolutePath()).start();
                    }
                } catch (Exception ex) {
                    System.err.println("Could not open log file: " + ex.getMessage());
                    ui.appendLog("⚠ Could not open log file: " + ex.getMessage());
                }
            } else {
                System.err.println("Log file not found at " + logFile.getAbsolutePath());
                ui.appendLog("⚠ Log file not found at " + logFile.getAbsolutePath());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════

    // CONVERSION LOGIC
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Entry point called by MainController when entering Step 5.
     * Reads the real tree from Step2, reads output path from Step4,
     * resets all state, and launches the background conversion task.
     */
    public void prepareConversionView() {
        System.out.println("Entering Step 5 (Conversion View).");
        com.pstconverter.core.destination.ImapDestinationHandler.clearCache();
        resetState();

        // ── Resolve output path from Step4 ───────────────────────────────
        Step4DestinationView step4 = controller.getStep4DestinationView();
        String outputDisplay = "(cloud / none)";
        if (step4 != null) {
            String fmt = step4.getSelectedFormat();
            String displayFmt = fmt;
            if ("Gmail".equalsIgnoreCase(fmt)) {
                EmailDestinationConfig config = step4.getEmailDestinationConfig();
                if (config != null) {
                    if ("App Password (IMAP)".equalsIgnoreCase(config.authMode())) {
                        displayFmt = "Gmail (IMAP)";
                    } else {
                        displayFmt = "Gmail (Modern Auth)";
                    }
                }
            }
            ui.setSessionFormat(displayFmt);
            if (step4.isCloudFormat(fmt)) {
                resolvedOutputPath = null;
                EmailDestinationConfig config = step4.getEmailDestinationConfig();
                if (config != null) {
                    String host = config.host();
                    String user = config.username();
                    String targetFolder = config.targetFolder();
                    if (fmt.equalsIgnoreCase("Gmail")) {
                        outputDisplay = "Gmail API [" + user + "] / " + targetFolder;
                    } else if (fmt.equalsIgnoreCase("Office 365")) {
                        outputDisplay = "Office 365 Graph API [" + user + "] / " + targetFolder;
                    } else {
                        outputDisplay = fmt + " (" + host + ") [" + user + "] / " + targetFolder;
                    }
                }
            } else {
                resolvedOutputPath = step4.getOutputPath();
                outputDisplay = resolvedOutputPath != null ? resolvedOutputPath : "(none)";
            }

            System.out.println("Session Format: " + displayFmt);
            System.out.println("Session Output/Server: " + outputDisplay);
            System.out.println("Export Structure: " + step4.getExportStructure());
            System.out.println("Attachment Handling: " + step4.getAttachmentHandling());
            System.out.println("Naming Convention: " + step4.getNamingConvention());
        } else {
            resolvedOutputPath = null;
            ui.setSessionFormat("—");
            System.out.println("Session Format: None (Step 4 destination view not found)");
        }
        ui.setSessionInfo("Output: " + outputDisplay);

        populateFolderTreeFromStep2();

        // Check if there is any resumeable progress
        int totalMigrated = 0;
        Set<String> scannedFiles = new HashSet<>();
        Step2ExplorerView step2 = controller.getStep2ExplorerView();
        String format = step4 != null ? step4.getSelectedFormat() : "TXT";
        String outPath = resolvedOutputPath != null ? resolvedOutputPath : "";
        if (step2 != null) {
            for (String key : folderItemCounts.keySet()) {
                TreeItem<String> sourceNode = originalFolderNodeMap.get(key);
                if (sourceNode != null) {
                    File sourceFile = step2.getSourceFileForNode(sourceNode);
                    if (sourceFile != null && scannedFiles.add(sourceFile.getAbsolutePath())) {
                        totalMigrated += com.pstconverter.util.SettingsManager.getMigratedCountForFile(sourceFile.getAbsolutePath(), format, outPath);
                    }
                }
            }
        }

        if (totalMigrated > 0) {
            ui.setResumeStatus("Found " + totalMigrated + " previously migrated items. Click Start to resume.");
        } else {
            shouldAutoResume = false;
            ui.setResumeStatus("");
        }

        ui.setMasterStatus("Ready to start");
        ui.setFolderStatus("Ready");

        // Trigger auto-scan in background if not already scanned
        if (controller.getLastScannedMatchingCount() == null && !controller.isScanningFilters()) {
            controller.runFilterScan(null, null);
        }
    }

    public void startConversion() {
        System.out.println("Start Migration button clicked. Conversion task execution initiated.");

        if (!com.pstconverter.util.LicenseManager.isActivated()) {
            Alert trialAlert = new Alert(Alert.AlertType.INFORMATION);
            trialAlert.setTitle("Demo Mode Active");
            trialAlert.setHeaderText("Free Trial Mode Active");
            trialAlert.setContentText("Please note that since you are using the Free Trial version, only the first " + com.pstconverter.util.LicenseManager.TRIAL_LIMIT_PER_FOLDER + " emails per folder will be converted.\n\nTo convert unlimited items, please activate or upgrade your license key.");
            if (controller != null && controller.getPrimaryStage() != null) {
                trialAlert.initOwner(controller.getPrimaryStage());
            }
            DialogPane dp = trialAlert.getDialogPane();
            dp.getStyleClass().add("custom-alert-dialog");
            try {
                dp.getStylesheets().add(controller.getActiveThemeStylesheet());
            } catch (Exception ignored) {}
            
            ButtonType btnTypeSupport = new ButtonType("Chat with Support", ButtonBar.ButtonData.HELP_2);
            trialAlert.getButtonTypes().setAll(btnTypeSupport, ButtonType.OK);
            
            Optional<ButtonType> result = trialAlert.showAndWait();
            if (result.isPresent() && result.get() == btnTypeSupport) {
                controller.openHelpChat();
                return;
            } else if (!result.isPresent() || result.get() != ButtonType.OK) {
                return;
            }
        }

        // Check if there is any resumeable progress
        int totalMigrated = 0;
        Set<String> scannedFiles = new java.util.HashSet<>();
        Step2ExplorerView step2 = controller.getStep2ExplorerView();
        Step4DestinationView step4 = controller.getStep4DestinationView();
        String format = step4 != null ? step4.getSelectedFormat() : "TXT";
        String outPath = resolvedOutputPath != null ? resolvedOutputPath : "";
        if (step2 != null) {
            for (String key : folderItemCounts.keySet()) {
                TreeItem<String> sourceNode = originalFolderNodeMap.get(key);
                if (sourceNode != null) {
                    File sourceFile = step2.getSourceFileForNode(sourceNode);
                    if (sourceFile != null && scannedFiles.add(sourceFile.getAbsolutePath())) {
                        totalMigrated += com.pstconverter.util.SettingsManager.getMigratedCountForFile(sourceFile.getAbsolutePath(), format, outPath);
                    }
                }
            }
        }

        if (totalMigrated > 0 && !this.shouldAutoResume) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Previous Progress Found");
            alert.setHeaderText("Previous Migration Progress Found");
            alert.setContentText("We found " + totalMigrated + " previously migrated items for the selected file(s).\n\n"
                    + "Would you like to resume from where you left off (skipping already converted items), or start a clean migration from scratch?");

            ButtonType btnTypeResume = new ButtonType("Resume Migration");
            ButtonType btnTypeClean = new ButtonType("Start Clean");
            ButtonType btnTypeCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(btnTypeResume, btnTypeClean, btnTypeCancel);
            alert.initOwner(getScene() != null ? getScene().getWindow() : null);

            DialogPane dp = alert.getDialogPane();
            dp.getStyleClass().add("custom-alert-dialog");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == btnTypeResume) {
                    this.shouldAutoResume = true;
                } else if (result.get() == btnTypeClean) {
                    this.shouldAutoResume = false;
                    for (String filePath : scannedFiles) {
                        com.pstconverter.util.SettingsManager.clearMigrationProgressForFile(filePath, format, outPath);
                        com.pstconverter.util.DiagnosticLogger.logSessionCleared(filePath, format, outPath);
                    }
                    ui.setResumeStatus("");

                    controller.setFolderNameManuallyEdited(false);
                    controller.setCustomExportFolderName(null);
                    controller.refreshFolderNameSuggestion();
                    if (step4 != null) {
                        step4.refreshDestinationPaths();
                        String newFmt = step4.getSelectedFormat();
                        if (step4.isCloudFormat(newFmt)) {
                            resolvedOutputPath = null;
                            EmailDestinationConfig config = step4.getEmailDestinationConfig();
                            if (config != null) {
                                String targetFolder = config.targetFolder();
                                ui.setSessionInfo("Output: " + newFmt + " [" + config.username() + "] / " + targetFolder);
                            }
                        } else {
                            resolvedOutputPath = step4.getOutputPath();
                            ui.setSessionInfo("Output: " + resolvedOutputPath);
                        }
                    }
                } else {
                    System.out.println("Migration start canceled by user.");
                    return;
                }
            } else {
                return;
            }
        }
        ui.setMasterStatus("Running...");

        // Register standard output/error stream listener to redirect relevant network warnings to UI
        com.pstconverter.controller.MainController.TimestampedOutputStream.setLogListener(line -> {
            String lower = line.toLowerCase(Locale.ROOT);
            boolean isNormalProgress = line.contains("📂 Processing:") 
                    || line.contains("✔ [") 
                    || line.contains("✅") 
                    || line.contains("⏹") 
                    || line.contains("started") 
                    || line.contains("complete");
            boolean isIssueOrWarning = line.contains("[ERROR]") 
                    || line.contains("[WARNING]") 
                    || lower.contains("fail") 
                    || lower.contains("error") 
                    || lower.contains("warn") 
                    || lower.contains("exception") 
                    || lower.contains("retry") 
                    || lower.contains("connection") 
                    || lower.contains("timeout") 
                    || lower.contains("socket") 
                    || lower.contains("refused") 
                    || lower.contains("reset")
                    || line.trim().startsWith("at ") 
                    || line.contains(".java:");
            
            if (isNormalProgress || isIssueOrWarning) {
                Platform.runLater(() -> {
                    String ts = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
                    ui.appendLog("[" + ts + "]  " + line + "\n");
                });
            }
        });

        startTimeMs = System.currentTimeMillis();
        startElapsedTimer();
        ui.setControlsState("RUNNING");
        launchConversionTask();
    }

    private void resetState() {
        stopRequested.set(false);
        pauseRequested.set(false);
        isParallelMode.set(false);
        totalFoldersToProcess = 0;

        latestSubject.set("");

        progressState.currentIdx = 0;
        progressState.estCount = 0;
        progressState.folderIndexInCurrentFile = 0;
        progressState.totalFoldersInCurrentFile = 1;
        progressState.currentFileIndex = 1;

        telemetry.reset();
        startTimeMs     = System.currentTimeMillis();
        totalPausedMs = 0;
        currentPauseStartMs = 0;

        ui.resetTelemetry();
        ui.updateMasterProgress(0);
        ui.updateFolderProgress(0);
        ui.setMasterStatus("Ready to start");
        ui.setFolderStatus("Waiting…");
        ui.updateSessionFiles("Files: 0/0 processed");

        rootItem.getChildren().clear();
        folderNodeMap.clear();
        folderItemCounts.clear();
        originalFolderNodeMap.clear();
        activeFolders.clear();
        actualFolderItemCounts.clear();
        folderToFileMap.clear();
        fileFoldersMap.clear();
    }

    // ── Tree mirroring from Step2 ──────────────────────────────────────────

    private void populateFolderTreeFromStep2() {
        Step2ExplorerView step2 = controller.getStep2ExplorerView();
        if (step2 == null) return;

        TreeItem<String> step2Root = step2.getSelectedFolderTreeRoot();
        if (step2Root == null) return;

        long estimatedTotal = 0;

        for (TreeItem<String> topNode : step2Root.getChildren()) {
            estimatedTotal += mirrorSubtree(topNode, rootItem, "");
        }

        totalItems = Math.max(estimatedTotal, 1);
        if (controller.isScanningFilters()) {
            ui.setSessionItems("Total items: " + totalItems + " (Scanning...)");
        } else if (controller.getLastScannedMatchingCount() != null) {
            ui.setSessionItems("Total items: " + totalItems + " (" + controller.getLastScannedMatchingCount() + " matching filters)");
        } else {
            ui.setSessionItems("Total items: " + totalItems);
        }
        actualFolderItemCounts.clear();
        actualFolderItemCounts.putAll(folderItemCounts);
    }

    private long mirrorSubtree(TreeItem<String> source, TreeItem<String> parentDest, String pathPrefix) {
        if (source == null) return 0;

        if (source instanceof CheckBoxTreeItem) {
            CheckBoxTreeItem<String> cb = (CheckBoxTreeItem<String>) source;
            if (!cb.isSelected() && !cb.isIndeterminate()) return 0;
        }

        String label      = source.getValue();
        String uniqueKey  = pathPrefix + "/" + label;
        boolean isLeaf    = source.getChildren().isEmpty();

        boolean isFileGroupNode = label.equals("Mailbox File (Single)") || label.equals("Mailbox File (Folder)")
                || source.getParent() == folderNodeMap.get("__root__")
                || (source.getParent() != null && source.getParent().getParent() == null);

        int itemsHere = 0;
        if (isLeaf && source.getChildren().isEmpty() && !(label.startsWith("⚠️")) && !isFileGroupNode) {
            Step2ExplorerView step2 = controller.getStep2ExplorerView();
            Integer actualCount = null;
            if (step2 != null) {
                actualCount = step2.getFolderMessageCount(source);
            }
            if (actualCount != null) {
                itemsHere = actualCount;
            } else {
                Random rng = new Random(uniqueKey.hashCode());
                itemsHere = 20 + rng.nextInt(180);
            }
        }

        String nodeStatus;
        if (source.getChildren().isEmpty()
                && !(label.startsWith("⚠️"))
                && !isFileGroupNode) {
            nodeStatus = "PENDING";
        } else if (label.startsWith("⚠️")) {
            nodeStatus = "ERROR";
        } else {
            nodeStatus = source.getChildren().isEmpty() ? "PENDING" : "FILE";
        }

        TreeItem<String> destNode = new TreeItem<>(nodeStatus + "|" + label);
        destNode.setExpanded(true);
        parentDest.getChildren().add(destNode);

        if (nodeStatus.equals("PENDING")) {
            folderNodeMap.put(uniqueKey, destNode);
            originalFolderNodeMap.put(uniqueKey, source);
            folderItemCounts.put(uniqueKey, itemsHere);
        }

        long childItems = itemsHere;
        for (TreeItem<String> child : source.getChildren()) {
            childItems += mirrorSubtree(child, destNode, uniqueKey);
        }

        if (nodeStatus.equals("FILE")) {
            folderNodeMap.put(uniqueKey, destNode);
        }

        return childItems;
    }

    // ── Elapsed Timer ─────────────────────────────────────────────────────

    private void startElapsedTimer() {
        if (elapsedTimer != null) elapsedTimer.stop();
        elapsedTimer = new Timeline(new KeyFrame(Duration.millis(200), e -> {
            updateProgressUiPolling(false);
        }));
        elapsedTimer.setCycleCount(Animation.INDEFINITE);
        elapsedTimer.play();
    }

    // ── Background Task ───────────────────────────────────────────────────

    public void launchConversionTask() {
        List<String> pendingFolders = new ArrayList<>(folderItemCounts.keySet());
        int totalFolders = pendingFolders.size();

        if (totalFolders == 0) {
            ui.appendLog("⚠ No selected mail folders found. Check your selection in Step 2.");
            ui.updateMasterProgress(1.0);
            ui.onConversionFinished(false);
            return;
        }

        // Determine unique files and folder mapping
        uniqueFiles.clear();
        folderToFileMap.clear();
        fileFoldersMap.clear();
        Step2ExplorerView step2 = controller.getStep2ExplorerView();
        for (String key : pendingFolders) {
            TreeItem<String> sourceNode = originalFolderNodeMap.get(key);
            File sourceFile = null;
            if (sourceNode != null && step2 != null) {
                sourceFile = step2.getSourceFileForNode(sourceNode);
            }
            if (sourceFile != null) {
                folderToFileMap.put(key, sourceFile);
                fileFoldersMap.computeIfAbsent(sourceFile, f -> new ArrayList<>()).add(key);
                
                boolean alreadyPresent = false;
                for (File f : uniqueFiles) {
                    if (f.getAbsolutePath().equalsIgnoreCase(sourceFile.getAbsolutePath())) {
                        alreadyPresent = true;
                        break;
                    }
                }
                if (!alreadyPresent) {
                    uniqueFiles.add(sourceFile);
                }
            }
        }
        final int totalFiles = Math.max(uniqueFiles.size(), 1);
        final boolean isResume = this.shouldAutoResume;

        if (!isResume && !controller.isFolderNameManuallyEdited()) {
            controller.setCustomExportFolderName(null);
            controller.refreshFolderNameSuggestion();
            Step4DestinationView step4View = controller.getStep4DestinationView();
            if (step4View != null) {
                step4View.refreshDestinationPaths();
                String newFmt = step4View.getSelectedFormat();
                if (step4View.isCloudFormat(newFmt)) {
                    resolvedOutputPath = null;
                    EmailDestinationConfig config = step4View.getEmailDestinationConfig();
                    if (config != null) {
                        String targetFolder = config.targetFolder();
                        ui.setSessionInfo("Output: " + newFmt + " [" + config.username() + "] / " + targetFolder);
                    }
                } else {
                    resolvedOutputPath = step4View.getOutputPath();
                    ui.setSessionInfo("Output: " + resolvedOutputPath);
                }
            }
        }

        // ── Build immutable ConversionConfig snapshot ─────────────────────
        Step4DestinationView step4 = controller.getStep4DestinationView();
        final String format = step4 != null ? step4.getSelectedFormat() : "TXT";
        final boolean isCloud = step4 != null && step4.isCloudFormat(format);
        final String exportStructure = step4 != null ? step4.getExportStructure() : "Individual File per Email (One file per message)";
        final String attachHandling = step4 != null ? step4.getAttachmentHandling() : "Keep Attachments in Folder";
        final String namingConvention = step4 != null ? step4.getNamingConvention() : "Original Subject";
        final EmailDestinationConfig cloudConfig = step4 != null ? step4.getEmailDestinationConfig() : null;
        final boolean isMonolithic = ConversionConfig.checkMonolithic(exportStructure);
        final boolean isSplitPst = step4 != null && step4.isSplitPst();
        final String splitSize = step4 != null ? step4.getSplitSize() : "0";

        java.util.Properties filterProps = controller.getStep3FilterView() != null 
                ? controller.getStep3FilterView().getFilterProperties() 
                : new java.util.Properties();

        int userThreads = 4;
        try {
            userThreads = Integer.parseInt(com.pstconverter.util.SettingsManager.getSetting(com.pstconverter.util.SettingsManager.KEY_THREAD_COUNT, "4"));
        } catch (NumberFormatException ignored) {}
        
        int threadCount;
        if (isMonolithic) {
            if (userThreads > 1) {
                System.out.println("ℹ Monolithic export formats are restricted to 1 thread to prevent file corruption. Overriding selection of " + userThreads + " threads to 1.");
                com.pstconverter.util.DiagnosticLogger.warn("Monolithic export formats restricted to 1 thread. Capped user's " + userThreads + " threads.");
                threadCount = 1;
            } else {
                threadCount = Math.max(1, userThreads);
            }
        } else if (isCloud) {
            if (userThreads > 3) {
                System.out.println("ℹ Cloud migrations are limited to a maximum of 3 threads to prevent server blocks. Overriding selection of " + userThreads + " threads to 3.");
                com.pstconverter.util.DiagnosticLogger.warn("Cloud migrations capped to 3 threads. Capped user's " + userThreads + " threads.");
                threadCount = 3;
            } else {
                threadCount = Math.max(1, userThreads);
            }
        } else {
            if (userThreads > 8) {
                System.out.println("ℹ Local individual file conversions are limited to a maximum of 8 threads to prevent disk performance degradation. Overriding selection of " + userThreads + " threads to 8.");
                com.pstconverter.util.DiagnosticLogger.warn("Local file conversions capped to 8 threads. Capped user's " + userThreads + " threads.");
                threadCount = 8;
            } else {
                threadCount = Math.max(1, userThreads);
            }
        }

        if (resolvedOutputPath != null && !resolvedOutputPath.trim().isEmpty() && !isCloud) {
            try {
                File exportDir = new File(resolvedOutputPath.trim());
                if (!exportDir.exists()) {
                    exportDir.mkdirs();
                }
            } catch (Exception ex) {
                System.err.println("Failed to create export output directory: " + ex.getMessage());
            }
        }

        final ConversionConfig config = new ConversionConfig(
            format, isCloud, exportStructure, attachHandling, namingConvention,
            resolvedOutputPath, cloudConfig, isMonolithic, isResume, threadCount,
            filterProps, isSplitPst, splitSize
        );

        isParallelMode.set(threadCount > 1);
        totalFoldersToProcess = totalFolders;
        totalFilesCount = totalFiles;

        // ── Save IN_PROGRESS session ─────────────────────────────────────
        String filterStr = ConversionSessionTracker.serializePropertiesToString(filterProps);
        java.util.Properties outputProps = new java.util.Properties();
        outputProps.setProperty("format", format != null ? format : "");
        outputProps.setProperty("export_structure", exportStructure != null ? exportStructure : "");
        outputProps.setProperty("attachment_handling", attachHandling != null ? attachHandling : "");
        outputProps.setProperty("naming_convention", namingConvention != null ? namingConvention : "");
        outputProps.setProperty("destination_path", resolvedOutputPath != null ? resolvedOutputPath : "");
        if (isCloud && cloudConfig != null) {
            outputProps.setProperty("cloud_username", cloudConfig.username() != null ? cloudConfig.username() : "");
            outputProps.setProperty("cloud_host", cloudConfig.host() != null ? cloudConfig.host() : "");
            outputProps.setProperty("cloud_port", String.valueOf(cloudConfig.port()));
            outputProps.setProperty("cloud_ssl", String.valueOf(cloudConfig.ssl()));
            outputProps.setProperty("cloud_target_folder", cloudConfig.targetFolder() != null ? cloudConfig.targetFolder() : "");
            outputProps.setProperty("cloud_auth_mode", cloudConfig.authMode() != null ? cloudConfig.authMode() : "");
        }
        String outputStr = ConversionSessionTracker.serializePropertiesToString(outputProps);
        sessions.saveInProgressSessions(uniqueFiles, fileFoldersMap, actualFolderItemCounts, folderToFileMap, config, filterStr, outputStr, totalFolders, pendingFolders);

        if (isResume) {
            ui.appendLog("▶  Resuming migration — skipping already migrated items...");
        } else {
            ui.appendLog("▶  Conversion started — " + totalFolders + " folder(s) across " + totalFiles + " file(s) to process");
        }

        // Initialize the diagnostic logger session before creating the Task
        String firstFileName = uniqueFiles.isEmpty() ? "Unknown" : uniqueFiles.get(0).getName();
        String sessionLabel = String.format("File: %s | Format: %s", firstFileName, format);
        com.pstconverter.util.DiagnosticLogger.init(sessionLabel);

        // ── Create shared state for producer/consumer ────────────────────
        final Set<String> foldersWithErrors = java.util.concurrent.ConcurrentHashMap.newKeySet();
        final Map<String, com.pstconverter.core.output.OutputHandler.Session> activeSessions = new java.util.concurrent.ConcurrentHashMap<>();
        final Map<String, Long> folderStartTimes = new java.util.concurrent.ConcurrentHashMap<>();
        final int finalThreadCount = threadCount;

        conversionTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                com.pstconverter.util.DiagnosticLogger.log("Background task thread started...");
                
                // Preload migrated message IDs cache for resume optimization
                if (isResume) {
                    sessions.preloadMigratedIds(uniqueFiles, format, resolvedOutputPath != null ? resolvedOutputPath : "");
                } else {
                    sessions.clearMigratedIds();
                }

                System.out.println("Conversion background task started.");
                System.out.println("Filter Settings Loaded: " + filterProps);
                if (resolvedOutputPath != null && !resolvedOutputPath.trim().isEmpty()) {
                    System.setProperty("active_export_output_path", resolvedOutputPath);
                    File exportDir = new File(resolvedOutputPath.trim());
                    if (!exportDir.exists()) {
                        exportDir.mkdirs();
                    }
                    if (step4 != null) {
                        System.setProperty("active_export_split_pst", String.valueOf(config.isSplitPst()));
                        System.setProperty("active_export_split_size", config.splitSize());
                    }
                    // Clear existing monolithic files to start fresh for the selected format if NOT resuming
                    if (!isResume) {
                        com.pstconverter.core.output.OutputHandler handler = com.pstconverter.core.output.OutputFactory.getHandler(format);
                        if (handler != null) {
                            try {
                                handler.cleanMonolithicFiles(new File(resolvedOutputPath), exportStructure);
                            } catch (Exception ex) {
                                System.err.println("Failed to clean monolithic files: " + ex.getMessage());
                            }
                        }
                    }
                }

                try (FilterEngine filterEngine = new FilterEngine(filterProps)) {

                    // Fetch total items using pre-populated folder message counts
                    long actualTotalItems = 0;
                    for (String key : pendingFolders) {
                        actualTotalItems += actualFolderItemCounts.getOrDefault(key, 0);
                    }

                    final long finalTotal = Math.max(actualTotalItems, 1);
                    Platform.runLater(() -> {
                        totalItems = finalTotal;
                        ui.setSessionItems("Total items: " + finalTotal);
                        ui.updateSessionFiles("Files: 0/" + totalFiles + " processed");
                    });

                    com.pstconverter.util.DiagnosticLogger.logConversionStart(
                        isResume, totalFolders, totalFiles, finalTotal, format, finalThreadCount, isMonolithic
                    );

                    // ── Create work queue ────────────────────────────────────
                    final java.util.concurrent.LinkedBlockingQueue<WorkItem> workQueue = new java.util.concurrent.LinkedBlockingQueue<>(100);

                    // ── Create cloud handler if needed ────────────────────────
                    CloudUploadHandler localCloudHandler = null;
                    if (isCloud) {
                        localCloudHandler = new CloudUploadHandler(config, ui, stopRequested, pauseRequested, foldersWithErrors);
                        localCloudHandler.setUIControls(null, null);
                    }
                    cloudHandler = localCloudHandler;

                    // ── Start Consumer executor service ───────────────────────
                    java.util.concurrent.ExecutorService consumerExecutor;
                    if (isMonolithic) {
                        consumerExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
                    } else if (isCloud) {
                        consumerExecutor = java.util.concurrent.Executors.newCachedThreadPool();
                    } else {
                        consumerExecutor = java.util.concurrent.Executors.newFixedThreadPool(finalThreadCount);
                    }
                    
                    // ── Start Consumer threads ───────────────────────────────
                    for (int i = 0; i < finalThreadCount; i++) {
                        consumerExecutor.submit(new MessageConsumerWorker(
                            workQueue, config, telemetry, sessions, localCloudHandler, filterEngine, ui,
                            stopRequested, pauseRequested,
                            actualFolderItemCounts, folderToFileMap, fileFoldersMap, uniqueFiles,
                            activeFolders, foldersWithErrors, activeSessions, folderStartTimes,
                            totalFiles, latestSubject, progressState
                        ));
                    }

                    // ── Run Producer on this thread ──────────────────────────
                    MessageProducerLoop producer = new MessageProducerLoop(
                        pendingFolders, workQueue, config, telemetry, sessions, ui,
                        stopRequested, isParallelMode,
                        actualFolderItemCounts, originalFolderNodeMap, folderToFileMap, fileFoldersMap,
                        uniqueFiles, foldersWithErrors, step2, totalFiles, finalThreadCount, totalFoldersToProcess
                    );

                    try {
                        producer.produce();
                    } finally {
                        // Shutdown and wait for consumers
                        consumerExecutor.shutdown();
                        while (!consumerExecutor.isTerminated()) {
                            try {
                                if (stopRequested.get()) {
                                    consumerExecutor.shutdownNow();
                                }
                                consumerExecutor.awaitTermination(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                            } catch (InterruptedException ie) {
                                consumerExecutor.shutdownNow();
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }

                        if (localCloudHandler != null) {
                            localCloudHandler.close();
                        }
                    }
                } finally {
                    System.clearProperty("active_export_output_path");
                    System.clearProperty("active_export_split_pst");
                    System.clearProperty("active_export_split_size");
                }

                return null;
            }
        };

        conversionTask.setOnSucceeded(e -> {
            System.out.println("Conversion background task completed (setOnSucceeded).");
            com.pstconverter.util.DiagnosticLogger.logConversionComplete(
                telemetry.getProcessedItems(), telemetry.getSuccessItems(), telemetry.getSkippedItems(), telemetry.getFailedItems(),
                stopRequested.get(), System.currentTimeMillis() - startTimeMs
            );
            com.pstconverter.util.DiagnosticLogger.close();
            this.onConversionFinished(false);
        });
        conversionTask.setOnFailed(e    -> {
            Throwable ex = conversionTask.getException();
            System.err.println("Conversion background task failed (setOnFailed). Reason: " + (ex != null ? ex.getMessage() : "Unknown"));
            if (ex != null) ex.printStackTrace();
            com.pstconverter.util.DiagnosticLogger.error(
                "Conversion task threw an unhandled exception: " + (ex != null ? ex.getMessage() : "Unknown"), ex
            );
            com.pstconverter.util.DiagnosticLogger.logConversionComplete(
                telemetry.getProcessedItems(), telemetry.getSuccessItems(), telemetry.getSkippedItems(), telemetry.getFailedItems(),
                true, System.currentTimeMillis() - startTimeMs
            );
            com.pstconverter.util.DiagnosticLogger.close();
            this.onConversionFinished(true);
        });
        conversionTask.setOnCancelled(e -> {
            System.out.println("Conversion background task cancelled (setOnCancelled).");
            ui.appendLog("⏹  Task cancelled.");
            com.pstconverter.util.DiagnosticLogger.log("Conversion task was externally cancelled.");
            com.pstconverter.util.DiagnosticLogger.close();
            this.onConversionFinished(false);
        });

        Thread t = new Thread(conversionTask, "pst-conversion-thread");
        t.setDaemon(true);
        t.start();
    }




    // ── Task Helpers ──────────────────────────────────────────────────────


    // ── Completion ────────────────────────────────────────────────────────

    private void onConversionFinished(boolean hadError) {
        // Guarantee all pending migration progress records are flushed to DB
        com.pstconverter.util.SettingsManager.flushPendingMigrations();

        // ── Update session records based on outcome ────────────────────────
        Step4DestinationView step4fin = controller.getStep4DestinationView();
        String finFormat = step4fin != null ? step4fin.getSelectedFormat() : "TXT";
        String finOutPath = resolvedOutputPath != null ? resolvedOutputPath : "";
        if (stopRequested.get()) {
            sessions.keepSessionsInProgress(uniqueFiles, finFormat, finOutPath);
        } else {
            sessions.markSessionsCompleted(uniqueFiles, finFormat, finOutPath);
        }

        // Clear cached file handles to release file locks
        if (uniqueFiles != null) {
            for (File file : uniqueFiles) {
                com.pstconverter.core.adapter.SourceAdapter adapter = com.pstconverter.core.adapter.SourceAdapterFactory.getAdapterForFile(file);
                if (adapter != null) {
                    adapter.releaseResources(file);
                }
            }
        }

        // Unregister stream listener
        com.pstconverter.controller.MainController.TimestampedOutputStream.setLogListener(null);

        long elapsed    = (System.currentTimeMillis() - startTimeMs) / 1000;
        String elapsed$ = formatElapsedTime(elapsed);

        Platform.runLater(() -> {
            if (elapsedTimer != null) elapsedTimer.stop();
            updateProgressUiPolling(true);
            
            ui.updateMasterProgress(1.0);
            ui.updateFolderProgress(1.0);

            ui.setMasterStatus("Complete — " + telemetry.getSuccessItems() + " items converted");
            ui.updateSessionFiles("Files: " + totalFilesCount + "/" + totalFilesCount + " processed");
        });

        double finalActualBytes = getOutputSizeBytes();
        double finalActualMb = finalActualBytes / (1024.0 * 1024.0);
        Platform.runLater(() -> {
            String finalStatus = stopRequested.get() ? "Stopped" : "Complete";
            ui.updateTelemetry(String.valueOf(telemetry.getSuccessItems()), String.valueOf(telemetry.getFailedItems()), String.valueOf(telemetry.getSkippedItems()), elapsed$, "--", "0.00", String.format("%.2f MB", finalActualMb), finalStatus);
        });
        ui.appendLog("═══════════════════════════════════════════");
        ui.appendLog(stopRequested.get() 
                ? "⏹  Migration stopped by user. Converted: " + telemetry.getSuccessItems() + "  Failed: " + telemetry.getFailedItems() + "  Skipped: " + telemetry.getSkippedItems()
                : "✅ Done! Converted: " + telemetry.getSuccessItems() + "  Failed: " + telemetry.getFailedItems() + "  Skipped: " + telemetry.getSkippedItems() + "  Time: " + elapsed$);


        // Log final audit summary directly to session_run.log
        System.out.println("================================================================================");
        System.out.println(com.pstconverter.config.BrandConfig.TOOL_NAME.toUpperCase() + " - CONVERSION SUMMARY");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("  Status:                " + (stopRequested.get() ? "CANCELLED" : (hadError ? "FAILED" : "SUCCESS")));
        System.out.println("  Total Elapsed Time:    " + elapsed$);
        System.out.println("  Total Items Processed: " + telemetry.getProcessedItems());
        System.out.println("  Successfully Exported: " + telemetry.getSuccessItems());
        System.out.println("  Failed to Export:      " + telemetry.getFailedItems());
        System.out.println("  Skipped by Filters:    " + telemetry.getSkippedItems());
        if (telemetry.getProcessedItems() > 0 && elapsed > 0) {
            double speed = telemetry.getProcessedItems() / (double) elapsed;
            System.out.println(String.format("  Average Throughput:    %.2f items/sec", speed));
        }
        double sizeMb = telemetry.getOutputSizeBytes() / (1024.0 * 1024.0);
        System.out.println(String.format("  Export Size Footprint: %.2f MB", sizeMb));
        System.out.println("================================================================================");

        // Send Email Notification on successful migration completion
        if (!stopRequested.get()) {
            String recipientEmail = com.pstconverter.util.SettingsManager.getSetting("license_email", "");
            if (recipientEmail.isEmpty()) {
                recipientEmail = "trial_user@example.com";
            }
            final String targetEmail = recipientEmail;
            
            long successVal = telemetry.getSuccessItems();
            long skippedVal = telemetry.getSkippedItems();
            long failedVal = telemetry.getFailedItems();
            String durationVal = elapsed$;
            
            double bytes = telemetry.getOutputSizeBytes();
            double finalSizeMb = bytes / (1024.0 * 1024.0);
            String sizeStrVal = finalSizeMb > 1024 ? String.format("%.1f GB", finalSizeMb / 1024) : String.format("%.1f MB", finalSizeMb);
            String outPathVal = finOutPath;

            javafx.concurrent.Task<Void> emailTask = new javafx.concurrent.Task<>() {
                @Override
                protected Void call() throws Exception {
                    com.pstconverter.util.EmailNotifier.sendEmailNotification(
                        targetEmail,
                        successVal,
                        skippedVal,
                        failedVal,
                        durationVal,
                        sizeStrVal,
                        outPathVal
                    );
                    return null;
                }

                @Override
                protected void succeeded() {
                    super.succeeded();
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Email Notification Sent");
                        alert.setHeaderText("Summary Notification Emailed");
                        alert.setContentText("A summary report email of this migration has been sent to:\n" + targetEmail + "\n\nFrom: " + com.pstconverter.config.BrandConfig.COMPANY_EMAIL_SENDER);
                        alert.initOwner(controller.getPrimaryStage());
                        alert.showAndWait();
                    });
                }

                @Override
                protected void failed() {
                    super.failed();
                    Throwable ex = getException();
                    ex.printStackTrace();
                    System.err.println("Email notification failed: " + ex.getMessage());
                }
            };
            
            Thread emailThread = new Thread(emailTask);
            emailThread.setName("email-notification-thread");
            emailThread.setDaemon(true);
            emailThread.start();
        }

        Map<String, Integer> filterSkipCounts = telemetry.getFilterSkipCounts();
        double delaySec = stopRequested.get() ? 0.2 : 1.5;
        PauseTransition delay = new PauseTransition(Duration.seconds(delaySec));
        delay.setOnFinished(ev -> {
            if (!stopRequested.get() && !com.pstconverter.util.LicenseManager.isActivated()) {
                int missingEmails = filterSkipCounts.getOrDefault("Trial Mode Limit (Skipped)", 0);
                if (missingEmails > 0) {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Unlock Unlimited Migration");
                    alert.setHeaderText("Demo Mode Cap Reached");
                    
                    String content = String.format(
                        "You have successfully migrated %d emails, but %d items were skipped because " +
                        "the Free Trial version is capped at %d emails per folder.\n\n" +
                        "Upgrade to a Business or Enterprise license to unlock:\n" +
                        " • Unlimited email conversions with zero caps\n" +
                        " • Direct high-speed upload to Office 365, Gmail, and IMAP\n" +
                        " • Priority 24/7 corporate IT support\n" +
                        " • Detailed white-label report custom branding\n\n" +
                        "Click 'Upgrade Now' to purchase a license key online and complete your migration.",
                        telemetry.getSuccessItems(), missingEmails, com.pstconverter.util.LicenseManager.TRIAL_LIMIT_PER_FOLDER
                    );
                    alert.setContentText(content);
                    alert.initOwner(controller.getPrimaryStage());
                    
                    ButtonType btnUpgrade = new ButtonType("Upgrade Now", ButtonBar.ButtonData.HELP_2);
                    ButtonType btnOk = new ButtonType("Close", ButtonBar.ButtonData.OK_DONE);
                    alert.getButtonTypes().setAll(btnUpgrade, btnOk);
                    
                    Optional<ButtonType> opt = alert.showAndWait();
                    if (opt.isPresent() && opt.get() == btnUpgrade) {
                        try {
                            java.awt.Desktop.getDesktop().browse(java.net.URI.create(com.pstconverter.config.BrandConfig.UPGRADE_URL));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            controller.showStep(6);
        });
        delay.play();
    }

    // ── Getters for Step 6 Report Panel ───────────────────────────────────

    public long getSuccessItems() {
        return telemetry.getSuccessItems();
    }

    public long getFailedItems() {
        return telemetry.getFailedItems();
    }

    public long getSkippedItems() {
        return telemetry.getSkippedItems();
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public double getOutputSizeBytes() {
        if (resolvedOutputPath != null) {
            File dir = new File(resolvedOutputPath);
            if (dir.exists()) {
                long actualSize = getDirectorySize(dir);
                if (actualSize > 0) {
                    return actualSize;
                }
            }
        }
        return telemetry.getOutputSizeBytes();
    }

    private long getDirectorySize(File directory) {
        long length = 0;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    length += file.length();
                } else {
                    length += getDirectorySize(file);
                }
            }
        }
        return length;
    }

    public String getResolvedOutputPath() {
        return resolvedOutputPath;
    }

    public boolean isStopRequested() {
        return stopRequested.get();
    }

    // ── Controls Handlers ─────────────────────────────────────────────────

    public void requestPauseResume() {
        System.out.println("Pause/Resume toggled. Requested state: " + (pauseRequested.get() ? "RESUME" : "PAUSE"));
        if (pauseRequested.get()) {
            pauseRequested.set(false);
            if (currentPauseStartMs > 0) {
                totalPausedMs += (System.currentTimeMillis() - currentPauseStartMs);
                currentPauseStartMs = 0;
            }
            ui.setControlsState("RUNNING");
            ui.appendLog("▶  Conversion resumed.");
        } else {
            pauseRequested.set(true);
            currentPauseStartMs = System.currentTimeMillis();
            ui.setControlsState("PAUSED");
            ui.appendLog("⏸  Conversion paused.");
        }
    }

    public void confirmStop() {
        System.out.println("Stop button clicked. Showing confirmation alert.");
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Stop Conversion");
        alert.setHeaderText(null);
        alert.setContentText("Stop the conversion? Unprocessed folders will be skipped.");
        alert.initOwner(getScene() != null ? getScene().getWindow() : null);
        DialogPane dp = alert.getDialogPane();
        dp.getStyleClass().add("custom-alert-dialog");
        try { dp.getStylesheets().add(controller.getActiveThemeStylesheet()); } catch (Exception ignored) {}
        alert.showAndWait().ifPresent(result -> {
            System.out.println("Stop confirmation result: " + (result == ButtonType.OK ? "STOP CONFIRMED" : "STOP CANCELLED"));
            if (result == ButtonType.OK) {
                stopRequested.set(true);
                if (pauseRequested.get()) { pauseRequested.set(false); }
                if (conversionTask != null) conversionTask.cancel();
            }
        });
    }

    public void openOutputFolder() {
        String pathToOpen = resolvedOutputPath;
        System.out.println("Open Output Folder action triggered. Path to open: " + pathToOpen);

        if (pathToOpen != null && !pathToOpen.trim().isEmpty()) {
            File dir = new File(pathToOpen.trim());
            if (!dir.exists()) {
                try {
                    dir.mkdirs();
                } catch (Exception ex) {
                    System.err.println("Failed to create folder when opening output directory: " + ex.getMessage());
                }
            }
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(dir);
                } else {
                    new ProcessBuilder("open", dir.getAbsolutePath()).start();
                }
            } catch (Exception ex) {
                ui.appendLog("⚠ Could not open output folder: " + ex.getMessage());
            }
        }
    }



    public TreeItem<String> getRootItem() {
        return rootItem;
    }

    public Map<String, TreeItem<String>> getFolderNodeMap() {
        return folderNodeMap;
    }

    public Map<String, ConversionTelemetryTracker.FolderTelemetry> getFolderTelemetryMap() {
        return telemetry.getFolderTelemetryMap();
    }

    public Map<String, Integer> getFilterSkipCounts() {
        return telemetry.getFilterSkipCounts();
    }

    private void updateProgressUiPolling(boolean forceUpdate) {
        long now = System.currentTimeMillis();
        
        final double fileProgress = (double) progressState.currentIdx / Math.max(progressState.estCount, 1);
        final double folderProgressForMaster = (progressState.folderIndexInCurrentFile + fileProgress) / (double) progressState.totalFoldersInCurrentFile;
        final double masterProgress = (double) (progressState.currentFileIndex - 1 + folderProgressForMaster) / totalFilesCount;
        long activePausedMs = totalPausedMs;
        if (pauseRequested.get() && currentPauseStartMs > 0) {
            activePausedMs += (now - currentPauseStartMs);
        }
        final long elapsed = Math.max(0, now - startTimeMs - activePausedMs);
        
        final long succVal = telemetry.getSuccessItems();
        final long failVal = telemetry.getFailedItems();
        final long skipVal = telemetry.getSkippedItems();
        final double outMb = telemetry.getOutputSizeBytes() / (1024.0 * 1024.0);
        final long processed = telemetry.getProcessedItems();
        final String currentSubject = latestSubject.get();

        boolean isInternetDown = cloudHandler != null && cloudHandler.isInternetDisconnected();

        // Apply folder status updates to tree nodes efficiently using live telemetry counts
        List<String> activeKeysList = new ArrayList<>(activeFolders);
        for (String activeKey : activeKeysList) {
            int total = actualFolderItemCounts.getOrDefault(activeKey, 0);
            int success = telemetry.getSuccessInFolder(activeKey);
            int failed = telemetry.getFailedInFolder(activeKey);
            int skipped = telemetry.getSkippedInFolder(activeKey);
            ui.updateTreeItemStatus(activeKey, "ACTIVE", success, skipped, failed, total);
        }
        

        // Formulate active files and active folders display
        List<String> activeFilesList = new ArrayList<>();
        List<String> activeFoldersList = new ArrayList<>();
        // activeKeysList is already defined above
        for (String activeKey : activeKeysList) {
            File f = folderToFileMap.get(activeKey);
            if (f != null && !activeFilesList.contains(f.getName())) {
                activeFilesList.add(f.getName());
            }
            String folderName = activeKey.substring(activeKey.lastIndexOf('/') + 1);
            activeFoldersList.add(folderName);
        }
        final String activeFilesStr = activeFilesList.isEmpty() ? "—" : String.join(", ", activeFilesList);
        final String activeFoldersStr = activeFoldersList.isEmpty() ? "—" : String.join(", ", activeFoldersList);
        final String activeFoldersDisplay = activeFoldersList.isEmpty() ? "None" : String.join(", ", activeFoldersList);

        int fd = telemetry.getFoldersDone();
        int tf = totalFoldersToProcess;

        if (isParallelMode.get()) {
            double masterProg = (double) processed / Math.max(totalItems, 1);
            double smoothFolderProg = tf > 0 ? (double) fd / tf : 0.0;
            
            ui.updateMasterProgress(Math.min(masterProg, 1.0));
            ui.updateFolderProgress(Math.min(smoothFolderProg, 1.0));
            
            if (isInternetDown) {
                ui.setMasterStatus("⚠️ Internet connection lost. Pausing...");
                ui.setFolderStatus("Paused (No Internet)");
            } else if (pauseRequested.get()) {
                ui.setMasterStatus("Conversion Paused");
                ui.setFolderStatus("Paused");
            } else {
                ui.setFolderStatus("Processing: " + activeFoldersDisplay);
                ui.setMasterStatus("Folder " + fd + " of " + tf + " completed");
            }
            
            // Compute completed files count dynamically
            int completedFilesCount = 0;
            Map<String, ConversionTelemetryTracker.FolderTelemetry> telemetryMap = telemetry.getFolderTelemetryMap();
            for (File f : uniqueFiles) {
                List<String> fileFolders = fileFoldersMap.get(f);
                if (fileFolders != null) {
                    boolean allDone = true;
                    for (String fKey : fileFolders) {
                        ConversionTelemetryTracker.FolderTelemetry tel = telemetryMap.get(fKey);
                        if (tel == null || (!tel.status().equals("DONE") && !tel.status().equals("ERROR"))) {
                            allDone = false;
                            break;
                        }
                    }
                    if (allDone) {
                        completedFilesCount++;
                    }
                }
            }
            ui.updateSessionFiles("Files: " + completedFilesCount + "/" + totalFilesCount + " processed");
        } else {
            if (isInternetDown) {
                ui.setMasterStatus("⚠️ Internet connection lost. Pausing...");
                ui.setFolderStatus("Paused (No Internet)");
            } else if (pauseRequested.get()) {
                ui.setMasterStatus("Conversion Paused");
                ui.setFolderStatus("Paused");
            } else {
                ui.setFolderStatus("Processing: " + (activeFoldersDisplay.equals("None") ? "—" : activeFoldersDisplay));
                ui.setMasterStatus("File " + progressState.currentFileIndex + " of " + totalFilesCount + " | Folder " + (fd + 1) + " of " + tf);
            }
            ui.updateSessionFiles("Files: " + (progressState.currentFileIndex - 1) + "/" + totalFilesCount + " processed");
        }

        String currentStatus = "Running";
        if (isInternetDown) currentStatus = "Connection Lost";
        else if (pauseRequested.get()) currentStatus = "Paused";
        else if (stopRequested.get()) currentStatus = "Stopped";

        if (processed > 0 && elapsed > 0 && !pauseRequested.get()) {
            long etaMs  = (long)(elapsed * (totalItems - processed) / (double) processed);
            long etaSec = etaMs / 1000;
            double speed = processed / (elapsed / 1000.0);
            
            ui.updateTelemetry(
                String.valueOf(succVal),
                String.valueOf(failVal),
                String.valueOf(skipVal),
                formatElapsedTime(elapsed / 1000),
                formatElapsedTime(etaSec),
                String.format("%.2f", speed),
                String.format("%.2f MB", outMb),
                currentStatus
            );
        } else {
            ui.updateTelemetry(
                String.valueOf(succVal),
                String.valueOf(failVal),
                String.valueOf(skipVal),
                formatElapsedTime(elapsed / 1000),
                "—",
                "—",
                String.format("%.2f MB", outMb),
                currentStatus
            );
        }

        String currentFile = activeFilesList.isEmpty() ? "None" : String.join(", ", activeFilesList);
        String outPath = "—";
        com.pstconverter.view.Step4DestinationView step4 = controller.getStep4DestinationView();
        if (step4 != null) {
            outPath = step4.getOutputPath();
            if (outPath == null || outPath.isEmpty()) outPath = step4.getSelectedFormat();
        }
        
        ui.updateCurrentLabel(
            currentSubject != null ? currentSubject : "—",
            activeFoldersDisplay,
            currentFile,
            outPath
        );

    }

    private String getDestinationPathForFolder(String key, String format, String resolvedOutputPath, EmailDestinationConfig cloudConfig) {
        Step4DestinationView step4 = controller.getStep4DestinationView();
        boolean isCloud = step4 != null && step4.isCloudFormat(format);
        if (isCloud) {
            if (cloudConfig != null) {
                String host = cloudConfig.host();
                String user = cloudConfig.username();
                String rootFolder = cloudConfig.targetFolder() != null ? cloudConfig.targetFolder().trim() : "";
                
                String displayFolderPath = "";
                String[] parts = key.split("/");
                if (parts.length > 2) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 2; i < parts.length; i++) {
                        if (sb.length() > 0) sb.append("/");
                        sb.append(parts[i]);
                    }
                    displayFolderPath = sb.toString();
                } else {
                    displayFolderPath = key.substring(key.lastIndexOf('/') + 1);
                }

                String fullTargetFolder = displayFolderPath;
                if (!rootFolder.isEmpty()) {
                    fullTargetFolder = rootFolder + "/" + displayFolderPath;
                }
                
                if (format.equalsIgnoreCase("Gmail")) {
                    return "Gmail API [" + user + "] / " + fullTargetFolder;
                } else if (format.equalsIgnoreCase("Office 365")) {
                    return "Office 365 Graph API [" + user + "] / " + fullTargetFolder;
                } else {
                    return format + " (" + host + ") [" + user + "] / " + fullTargetFolder;
                }
            } else {
                return format + " (No Config)";
            }
        } else {
            if (resolvedOutputPath == null) {
                return "(none)";
            }
            String exportStructure = step4 != null ? step4.getExportStructure() : "Individual File per Email (One file per message)";
            boolean isMonolithic = ConversionConfig.checkMonolithic(exportStructure);
            if (isMonolithic) {
                return new File(resolvedOutputPath).getAbsolutePath();
            } else {
                String relativePath = "";
                String[] parts = key.split("/");
                if (parts.length > 2) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 2; i < parts.length; i++) {
                        if (sb.length() > 0) sb.append(File.separator);
                        sb.append(parts[i]);
                    }
                    relativePath = sb.toString();
                }
                if (!relativePath.isEmpty()) {
                    return new File(resolvedOutputPath, relativePath).getAbsolutePath();
                } else {
                    return new File(resolvedOutputPath).getAbsolutePath();
                }
            }
        }
    }

    private String formatElapsedTime(long elapsedSeconds) {
        if (elapsedSeconds < 60) {
            return elapsedSeconds + "s";
        } else if (elapsedSeconds < 3600) {
            return (elapsedSeconds / 60) + "m " + (elapsedSeconds % 60) + "s";
        } else {
            long hours = elapsedSeconds / 3600;
            long mins = (elapsedSeconds % 3600) / 60;
            long secs = elapsedSeconds % 60;
            return hours + "h " + mins + "m " + secs + "s";
        }
    }

    // ── Session Persistence (delegated) ──────────────────────────────────────

    public void saveInProgressSessionsForFiles(List<String> pendingFolderKeys, int totalFolders) {
        Step4DestinationView step4 = controller.getStep4DestinationView();
        Step3FilterView step3 = controller.getStep3FilterView();

        if (step4 == null) return;

        String format    = step4.getSelectedFormat();
        String outPath   = resolvedOutputPath != null ? resolvedOutputPath : "";
        String expStruct = step4.getExportStructure();
        String attachH   = step4.getAttachmentHandling();
        String namingC   = step4.getNamingConvention();

        java.util.Properties filterProps = step3 != null ? step3.getFilterProperties() : new java.util.Properties();
        String filterStr = ConversionSessionTracker.serializePropertiesToString(filterProps);

        java.util.Properties outputProps = new java.util.Properties();
        outputProps.setProperty("format", format != null ? format : "");
        outputProps.setProperty("export_structure", expStruct != null ? expStruct : "");
        outputProps.setProperty("attachment_handling", attachH != null ? attachH : "");
        outputProps.setProperty("naming_convention", namingC != null ? namingC : "");
        outputProps.setProperty("destination_path", outPath);

        if (step4.isCloudFormat(format)) {
            EmailDestinationConfig cloudConfig = step4.getEmailDestinationConfig();
            if (cloudConfig != null) {
                outputProps.setProperty("cloud_username", cloudConfig.username() != null ? cloudConfig.username() : "");
                outputProps.setProperty("cloud_host", cloudConfig.host() != null ? cloudConfig.host() : "");
                outputProps.setProperty("cloud_port", String.valueOf(cloudConfig.port()));
                outputProps.setProperty("cloud_ssl", String.valueOf(cloudConfig.ssl()));
                outputProps.setProperty("cloud_target_folder", cloudConfig.targetFolder() != null ? cloudConfig.targetFolder() : "");
                outputProps.setProperty("cloud_auth_mode", cloudConfig.authMode() != null ? cloudConfig.authMode() : "");
            }
        }

        String outputStr = ConversionSessionTracker.serializePropertiesToString(outputProps);

        ConversionConfig config = new ConversionConfig(
            format, step4.isCloudFormat(format), expStruct, attachH, namingC,
            outPath, step4.getEmailDestinationConfig(),
            ConversionConfig.checkMonolithic(expStruct), false, 1,
            filterProps, false, "0"
        );

        sessions.saveInProgressSessions(uniqueFiles, fileFoldersMap, actualFolderItemCounts, folderToFileMap, config, filterStr, outputStr, totalFolders, pendingFolderKeys);
    }
    
    private javafx.scene.Scene getScene() {
        return controller != null ? controller.getScene() : null;
    }

}

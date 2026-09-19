package com.pstconverter.util;

import com.pstconverter.controller.MainController;
import com.pstconverter.core.model.SourceFileModel;
import com.pstconverter.model.MailMessage;
import com.pstconverter.view.*;
import com.pstconverter.view.conversion.ConversionLogPanel;
import com.pstconverter.view.conversion.ConversionTelemetryPanel;
import com.pstconverter.view.conversion.ConversionTreePanel;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class CaptureAllGuideScreenshots extends Application {

    private static final String BASE_OUTPUT = "/Users/akashsahu.blue/Documents/Akas/Local to Claud Converters/ALL_PROJECT_SCREENSHOTS/MBOX_Converter";
    private static final String LOCAL_OUTPUT = "/Users/akashsahu.blue/Documents/Akas/Local to Claud Converters/MBOX Converter/screenshots";

    private File dirSteps;
    private File dirFilters;
    private File dirDestinations;
    private File dirDrawers;
    private File dirDark;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            System.out.println("🚀 [MASTER SUITE] Launching Comprehensive UI Screenshot Pipeline for MBOX Converter...");

            // Create target directories
            dirSteps = new File(BASE_OUTPUT, "01_Main_Steps");
            dirFilters = new File(BASE_OUTPUT, "02_Filters_All_Tabs");
            dirDestinations = new File(BASE_OUTPUT, "03_Destination_Modes");
            dirDrawers = new File(BASE_OUTPUT, "04_Drawers_and_Dialogs");
            dirDark = new File(BASE_OUTPUT, "05_Dark_Mode_Theme");

            dirSteps.mkdirs();
            dirFilters.mkdirs();
            dirDestinations.mkdirs();
            dirDrawers.mkdirs();
            dirDark.mkdirs();
            new File(LOCAL_OUTPUT).mkdirs();

            MainController controller = new MainController(primaryStage);
            Scene scene = new Scene(controller, 1300, 820);

            ThemeManager.applyTheme(scene, "Light");

            primaryStage.setScene(scene);
            primaryStage.setTitle("Outlook MBOX Converter Wizard — Official Documentation Capture");
            primaryStage.show();

            // Sequence of captures
            Timeline timeline = new Timeline();
            int delay = 400; // ms

            // 1. Step 1 Empty
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(delay), e -> {
                controller.showStep(1);
                captureNode(scene, new File(dirSteps, "Step1_Import_Empty.png"));
            }));

            // 2. Step 1 Populated
            delay += 400;
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(delay), e -> {
                populateSampleFiles(controller);
                captureNode(scene, new File(dirSteps, "Step1_Import_Populated.png"));
            }));

            // 3. Step 2 Mailbox Tree & List
            delay += 400;
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(delay), e -> {
                controller.showStep(2);
                Step2ExplorerView step2 = getPrivateField(controller, "step2View");
                populateStep2(step2);
                captureNode(scene, new File(dirSteps, "Step2_Mailbox_Explorer_Tree_and_List.png"));
            }));

            // 4. Step 2 Preview with selected message
            delay += 400;
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(delay), e -> {
                captureNode(scene, new File(dirSteps, "Step2_Mailbox_Explorer_Message_Preview.png"));
            }));

            // 5. Step 3 Filter Overview
            delay += 400;
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(delay), e -> {
                controller.showStep(3);
                captureNode(scene, new File(dirSteps, "Step3_Filter_Engine_Overview.png"));
            }));

            // 6. Step 3 - ALL 7 TABS
            String[] filterTabNames = {
                "Filter_Tab1_Folder_Hygiene.png",
                "Filter_Tab2_Date_Range_Filter.png",
                "Filter_Tab3_Sender_Recipient_Filter.png",
                "Filter_Tab4_Subject_Keyword_Filter.png",
                "Filter_Tab5_Attachment_Filter.png",
                "Filter_Tab6_Dedup_Duplicate_Remover.png",
                "Filter_Tab7_Item_Type_Selector.png"
            };

            for (int i = 0; i < 7; i++) {
                final int tabIdx = i;
                delay += 350;
                timeline.getKeyFrames().add(new KeyFrame(Duration.millis(delay), e -> {
                    controller.showStep(3);
                    Step3FilterView step3 = getPrivateField(controller, "step3View");
                    if (step3 != null) {
                        invokeMethod(step3, "selectCategory", new Class<?>[]{int.class}, tabIdx);
                    }
                    captureNode(scene, new File(dirFilters, filterTabNames[tabIdx]));
                }));
            }

            // 7. Step 4 Destination Overview & Modes
            delay += 400;
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(delay), e -> {
                controller.showStep(4);
                captureNode(scene, new File(dirSteps, "Step4_Destination_Selection.png"));
            }));

            // Step 4 - Local PDF
            delay += 350;
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(delay), e -> {
                Step4DestinationView step4 = getPrivateField(controller, "step4View");
                if (step4 != null) {
                    invokeMethod(step4, "selectCategory", new Class<?>[]{String.class}, "DOCUMENTS");
                    invokeMethod(step4, "selectFormat", new Class<?>[]{String.class}, "PDF");
                }
                captureNode(scene, new File(dirDestinations, "Destination_Category_Documents_PDF.png"));
            }));

            // Step 4 - Local Emails (MBOX/EML)
            delay += 350;
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(delay), e -> {
                Step4DestinationView step4 = getPrivateField(controller, "step4View");
                if (step4 != null) {
                    invokeMethod(step4, "selectCategory", new Class<?>[]{String.class}, "EMAILS");
                    invokeMethod(step4, "selectFormat", new Class<?>[]{String.class}, "MBOX");
                }
                captureNode(scene, new File(dirDestinations, "Destination_Category_Emails_MBOX_EML.png"));
            }));

            // Step 4 - Cloud Office 365
            delay += 350;
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(delay), e -> {
                Step4DestinationView step4 = getPrivateField(controller, "step4View");
                if (step4 != null) {
                    invokeMethod(step4, "selectCategory", new Class<?>[]{String.class}, "CLOUD");
                    invokeMethod(step4, "selectFormat", new Class<?>[]{String.class}, "Office 365");
                }
                captureNode(scene, new File(dirDestinations, "Destination_Cloud_Office365.png"));
            }));

            // Step 4 - Cloud Gmail
            delay += 350;
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(delay), e -> {
                Step4DestinationView step4 = getPrivateField(controller, "step4View");
                if (step4 != null) {
                    invokeMethod(step4, "selectCategory", new Class<?>[]{String.class}, "CLOUD");
                    invokeMethod(step4, "selectFormat", new Class<?>[]{String.class}, "Gmail");
                }
                captureNode(scene, new File(dirDestinations, "Destination_Cloud_Gmail.png"));
            }));

            // Step 4 - Cloud IMAP
            delay += 350;
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(delay), e -> {
                Step4DestinationView step4 = getPrivateField(controller, "step4View");
                if (step4 != null) {
                    invokeMethod(step4, "selectCategory", new Class<?>[]{String.class}, "CLOUD");
                    invokeMethod(step4, "selectFormat", new Class<?>[]{String.class}, "IMAP");
                }
                captureNode(scene, new File(dirDestinations, "Destination_Cloud_IMAP.png"));
            }));

            // 8. Step 5 Live Conversion Dashboard
            delay += 400;
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(delay), e -> {
                controller.showStep(5);
                Step5ConversionView step5 = getPrivateField(controller, "step5View");
                populateStep5Data(step5);
                captureNode(scene, new File(dirSteps, "Step5_Live_Conversion_Telemetry_Dashboard.png"));
            }));

            // 9. Step 6 Conversion Report Summary
            delay += 400;
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(delay), e -> {
                controller.showStep(6);
                captureNode(scene, new File(dirSteps, "Step6_Conversion_Report_Summary.png"));
            }));

            // 10. Drawers & Dialogs
            // Quick Settings Drawer
            delay += 400;
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(delay), e -> {
                controller.showStep(1);
                controller.toggleQuickSettings();
                Platform.runLater(() -> captureNode(scene, new File(dirDrawers, "Quick_Settings_Drawer.png")));
            }));

            // Close Drawer
            delay += 400;
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(delay), e -> {
                controller.toggleQuickSettings();
            }));

            // 11. Dark Theme Captures for All 6 Steps
            delay += 400;
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(delay), e -> {
                ThemeManager.applyTheme(scene, "Dark");
            }));

            String[] darkStepNames = {
                "Dark_Step1_Import.png",
                "Dark_Step2_Explorer.png",
                "Dark_Step3_Filters.png",
                "Dark_Step4_Destination.png",
                "Dark_Step5_Conversion.png",
                "Dark_Step6_Report.png"
            };

            for (int s = 1; s <= 6; s++) {
                final int stepNum = s;
                delay += 350;
                timeline.getKeyFrames().add(new KeyFrame(Duration.millis(delay), e -> {
                    controller.showStep(stepNum);
                    if (stepNum == 2) {
                        Step2ExplorerView step2 = getPrivateField(controller, "step2View");
                        populateStep2(step2);
                    } else if (stepNum == 5) {
                        Step5ConversionView step5 = getPrivateField(controller, "step5View");
                        populateStep5Data(step5);
                    }
                    captureNode(scene, new File(dirDark, darkStepNames[stepNum - 1]));
                }));
            }

            // Finish and copy to local screenshots directory
            delay += 500;
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(delay), e -> {
                System.out.println("✅ Copying captures to local project screenshots directory...");
                copyDirectory(new File(BASE_OUTPUT), new File(LOCAL_OUTPUT));
                System.out.println("🎉 MBOX Converter ALL UI SCREENS captured successfully!");
                primaryStage.close();
                Platform.exit();
            }));

            timeline.play();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static void populateSampleFiles(MainController controller) {
        controller.getFileList().clear();
        controller.getFileList().add(new SourceFileModel(
            "/Users/enterprise/MailArchives/Enterprise_Archive.mbox",
            "Enterprise_Archive.mbox", "12.4 GB", "VALID", true, "File", "", "MBOX"));
        controller.getFileList().add(new SourceFileModel(
            "/Users/enterprise/MailArchives/Department_Audit.mbox",
            "Department_Audit.mbox", "8.9 GB", "VALID", true, "File", "", "MBOX"));
        controller.getFileList().add(new SourceFileModel(
            "/Volumes/CloudStorage/Backups/Company_Records.mbox",
            "Company_Records.mbox", "4.2 GB", "VALID", true, "File", "", "MBOX"));
    }

    private static void populateStep2(Step2ExplorerView step2View) {
        if (step2View == null) return;
        try {
            CheckBoxTreeItem<String> rootNode = new CheckBoxTreeItem<>("Enterprise_Archive.mbox");
            rootNode.setExpanded(true);
            rootNode.setSelected(true);

            CheckBoxTreeItem<String> inboxNode = new CheckBoxTreeItem<>("Inbox (4,250 items)");
            inboxNode.setExpanded(true);
            inboxNode.setSelected(true);
            rootNode.getChildren().add(inboxNode);

            CheckBoxTreeItem<String> sentNode = new CheckBoxTreeItem<>("Sent Items (1,890 items)");
            sentNode.setSelected(true);
            rootNode.getChildren().add(sentNode);

            CheckBoxTreeItem<String> projectsNode = new CheckBoxTreeItem<>("Projects (2,010 items)");
            projectsNode.setExpanded(true);
            projectsNode.setSelected(true);
            projectsNode.getChildren().add(new CheckBoxTreeItem<>("Q4 Enterprise Migration (1,120 items)"));
            projectsNode.getChildren().add(new CheckBoxTreeItem<>("Cloud Architecture Review (890 items)"));
            rootNode.getChildren().add(projectsNode);

            rootNode.getChildren().add(new CheckBoxTreeItem<>("Drafts (45 items)"));
            rootNode.getChildren().add(new CheckBoxTreeItem<>("Deleted Items (320 items)"));

            Field treeField = Step2ExplorerView.class.getDeclaredField("folderTreeView");
            treeField.setAccessible(true);
            @SuppressWarnings("unchecked")
            TreeView<String> treeView = (TreeView<String>) treeField.get(step2View);
            if (treeView != null) {
                treeView.setRoot(rootNode);
                treeView.setShowRoot(true);
            }

            Field tableField = Step2ExplorerView.class.getDeclaredField("emailTableView");
            tableField.setAccessible(true);
            @SuppressWarnings("unchecked")
            TableView<MailMessage> tableView = (TableView<MailMessage>) tableField.get(step2View);
            if (tableView != null) {
                tableView.setItems(FXCollections.observableArrayList(
                    new MailMessage("sarah.jenkins@acme.com", "Q4 Financial Performance Audit & Executive Summary", "2024-06-12 10:14 AM", "Dear Executive Team, Attached is the completed Q4 financial audit report covering all departmental expenditures and cloud infrastructure allocation..."),
                    new MailMessage("david.miller@techcorp.io", "Updated Enterprise Cloud Migration Architecture Roadmap", "2024-06-11 04:45 PM", "Hi Team, We have finalized the architecture for migrating on-premise MBOX mailboxes directly to Office 365 and Google Workspace with full OAuth2 security..."),
                    new MailMessage("alex.rivera@globalnet.org", "Re: Multi-Domain Contract Renewal & SLA Terms Confirmation", "2024-06-10 09:30 AM", "Thank you for sending over the signed SLA agreement. Our migration cluster is configured with 10Gbps dedicated pipe..."),
                    new MailMessage("billing@cloudservices.com", "Monthly Infrastructure & Storage Usage Invoice #8492", "2024-06-09 02:15 PM", "Your monthly invoice for cloud mailbox ingestion storage is ready for download..."),
                    new MailMessage("support@datamigrate.pro", "MBOX Migrator Elite Enterprise Tier License Activation", "2024-06-08 11:00 AM", "Your enterprise license has been verified with unlimited conversion capacity and premium support...")
                ));
                tableView.getSelectionModel().select(0);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static void populateStep5Data(Step5ConversionView step5View) {
        if (step5View == null) return;
        try {
            Field telemetryField = Step5ConversionView.class.getDeclaredField("telemetryPanel");
            telemetryField.setAccessible(true);
            ConversionTelemetryPanel telemetryPanel = (ConversionTelemetryPanel) telemetryField.get(step5View);

            if (telemetryPanel != null) {
                telemetryPanel.updateMasterProgress(0.674);
                telemetryPanel.setMasterStatus("8,420 / 12,500 items (67.4%)");
                telemetryPanel.updateFolderProgress(0.850);
                telemetryPanel.setFolderStatus("1,606 / 1,890 items (85.0%)");
                telemetryPanel.updateTelemetry("8,100", "0", "320", "00:15:32", "00:07:15", "145 items/sec", "4.2 GB", "Active Migration");
                telemetryPanel.updateCurrentLabel("Re: Enterprise Cloud Migration Architecture", "/Sent Items", "Enterprise_Archive.mbox", "Sent Items");
                telemetryPanel.updateCurrentLabelProgress(0.850, "1,606 / 1,890 (85.0%)");
            }

            Field treeField = Step5ConversionView.class.getDeclaredField("treePanel");
            treeField.setAccessible(true);
            ConversionTreePanel treePanel = (ConversionTreePanel) treeField.get(step5View);

            if (treePanel != null) {
                treePanel.setSessionInfo("Format: PDF / Office 365", "Files: 1/3 processed", "Items: 12,500 total", "Output: /Users/enterprise/Migrated_Output/");

                TreeItem<String> root = new TreeItem<>("Enterprise_Archive.mbox");
                root.setExpanded(true);
                TreeItem<String> inbox = new TreeItem<>("Inbox (4,250 items)");
                TreeItem<String> sent = new TreeItem<>("Sent Items (1,890 items)");
                TreeItem<String> drafts = new TreeItem<>("Drafts (45 items)");
                root.getChildren().addAll(inbox, sent, drafts);

                treePanel.setRootItem(root);
                Map<String, TreeItem<String>> nodeMap = new HashMap<>();
                nodeMap.put("/Inbox", inbox);
                nodeMap.put("/Sent Items", sent);
                nodeMap.put("/Drafts", drafts);
                treePanel.setFolderNodeMap(nodeMap);

                treePanel.updateTreeItemStatus("/Inbox", "COMPLETED", 4250, 0, 0, 4250);
                treePanel.updateTreeItemStatus("/Sent Items", "PROCESSING", 1606, 50, 0, 1890);
                treePanel.updateTreeItemStatus("/Drafts", "QUEUED", 0, 0, 0, 45);
            }

            Field logField = Step5ConversionView.class.getDeclaredField("logPanel");
            logField.setAccessible(true);
            ConversionLogPanel logPanel = (ConversionLogPanel) logField.get(step5View);

            if (logPanel != null) {
                logPanel.appendLog("[10:18:01] [INFO] Conversion engine initialized with 4 parallel worker threads.");
                logPanel.appendLog("[10:18:02] [SUCCESS] Folder /Inbox completed — 4,250 items converted successfully.");
                logPanel.appendLog("[10:18:03] [INFO] Entering Folder /Sent Items (1,890 total items)...");
                logPanel.appendLog("[10:18:04] [SUCCESS] Exported Message #8418 \"Q4 Financial Audit\" -> PDF (2.4 MB)");
                logPanel.appendLog("[10:18:04] [SUCCESS] Exported Message #8419 \"Updated Enterprise Cloud Architecture\" -> PDF (1.8 MB)");
                logPanel.appendLog("[10:18:05] [SUCCESS] Exported Message #8420 \"Re: Contract Renewal & SLA Terms\" -> PDF (980 KB)");
                logPanel.appendLog("[10:18:05] [SKIP] Filter Rule (Date Boundary Excluded) -> Message ID #8421");
                logPanel.appendLog("[10:18:06] [SUCCESS] Exported Message #8422 \"Monthly Infrastructure Invoice #8492\" -> PDF (1.2 MB)");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static void captureNode(Scene scene, File targetFile) {
        try {
            WritableImage image = scene.snapshot(null);
            int w = (int) image.getWidth();
            int h = (int) image.getHeight();
            BufferedImage bufImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            PixelReader reader = image.getPixelReader();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    bufImg.setRGB(x, y, reader.getArgb(x, y));
                }
            }
            targetFile.getParentFile().mkdirs();
            ImageIO.write(bufImg, "png", targetFile);
            System.out.println("📸 [MBOX Captured] " + targetFile.getName() + " (" + w + "x" + h + ")");
        } catch (Exception ex) {
            System.err.println("Failed capture: " + targetFile.getName() + " -> " + ex.getMessage());
        }
    }

    private static void copyDirectory(File sourceLocation, File targetLocation) {
        try {
            if (sourceLocation.isDirectory()) {
                if (!targetLocation.exists()) targetLocation.mkdirs();
                String[] children = sourceLocation.list();
                if (children != null) {
                    for (String child : children) {
                        copyDirectory(new File(sourceLocation, child), new File(targetLocation, child));
                    }
                }
            } else {
                Files.copy(sourceLocation.toPath(), targetLocation.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            System.err.println("Copy error: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getPrivateField(Object obj, String fieldName) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return (T) f.get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private static void invokeMethod(Object obj, String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            Method m = obj.getClass().getDeclaredMethod(methodName, paramTypes);
            m.setAccessible(true);
            m.invoke(obj, args);
        } catch (Exception e) {
            try {
                Method m = obj.getClass().getMethod(methodName, paramTypes);
                m.invoke(obj, args);
            } catch (Exception ex) {
                System.err.println("Invoke failed: " + methodName + " -> " + ex.getMessage());
            }
        }
    }
}

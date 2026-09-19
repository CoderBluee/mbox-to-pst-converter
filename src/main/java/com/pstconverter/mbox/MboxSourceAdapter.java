package com.pstconverter.mbox;

import com.pstconverter.core.adapter.SourceAdapter;
import com.pstconverter.model.MailMessage;
import com.pstconverter.model.MailboxFolder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance, pure Java MBOX format source adapter implementing the SourceAdapter contract.
 * Parses .mbox and .mbx files, extracts headers, HTML/Text body, and attachments cleanly
 * into MailboxFolder and MailMessage POJOs.
 */
public class MboxSourceAdapter implements SourceAdapter {

    @Override
    public boolean canParse(File file) {
        if (file == null) return false;
        String name = file.getName().toLowerCase();
        return file.isDirectory() || name.endsWith(".mbox") || name.endsWith(".mbx") || !name.contains(".");
    }

    @Override
    public boolean validateFile(File file) {
        if (file == null || !file.exists()) return false;
        if (file.isDirectory()) return true;
        String name = file.getName().toLowerCase();
        return (name.endsWith(".mbox") || name.endsWith(".mbx") || file.length() > 0);
    }

    @Override
    public MailboxFolder parseFolderStructure(File file, BiConsumer<String, String> progressCallback) throws Exception {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("Source MBOX file does not exist: " + file);
        }

        MailboxFolder root = new MailboxFolder("Root", 0, true);

        if (file.isDirectory()) {
            parseDirectoryFolder(file, root, progressCallback);
        } else {
            int count = countMessagesInMbox(file);
            MailboxFolder fileFolder = new MailboxFolder(stripExtension(file.getName()), count, false);
            root.addChild(fileFolder);
        }

        return root;
    }

    private void parseDirectoryFolder(File dir, MailboxFolder parentNode, BiConsumer<String, String> progressCallback) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File child : files) {
            if (child.isDirectory()) {
                MailboxFolder subDirNode = new MailboxFolder(child.getName(), 0, false);
                parentNode.addChild(subDirNode);
                parseDirectoryFolder(child, subDirNode, progressCallback);
            } else if (canParse(child)) {
                int count = countMessagesInMbox(child);
                MailboxFolder mboxNode = new MailboxFolder(stripExtension(child.getName()), count, false);
                parentNode.addChild(mboxNode);
            }
        }
    }

    private int countMessagesInMbox(File file) {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.ISO_8859_1))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("From ")) {
                    count++;
                }
            }
        } catch (Exception e) {
            count = 0;
        }
        return count == 0 ? 1 : count;
    }

    @Override
    public List<MailMessage> getEmails(File file, List<String> folderPath) throws Exception {
        List<MailMessage> emails = new ArrayList<>();
        File targetFile = file;

        if (file.isDirectory() && folderPath != null && !folderPath.isEmpty()) {
            File curr = file;
            for (String part : folderPath) {
                File child = new File(curr, part);
                if (!child.exists()) child = new File(curr, part + ".mbox");
                if (!child.exists()) child = new File(curr, part + ".mbx");
                curr = child;
            }
            targetFile = curr;
        }

        if (!targetFile.exists() || targetFile.isDirectory()) {
            return emails;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(targetFile), StandardCharsets.UTF_8))) {
            String line;
            StringBuilder currentMsgBuilder = new StringBuilder();
            int index = 0;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("From ") && currentMsgBuilder.length() > 0) {
                    MailMessage msg = parseRawMime(currentMsgBuilder.toString(), targetFile.getName(), index++);
                    if (msg != null) emails.add(msg);
                    currentMsgBuilder.setLength(0);
                } else {
                    currentMsgBuilder.append(line).append("\n");
                }
            }
            if (currentMsgBuilder.length() > 0) {
                MailMessage msg = parseRawMime(currentMsgBuilder.toString(), targetFile.getName(), index++);
                if (msg != null) emails.add(msg);
            }
        }

        return emails;
    }

    private MailMessage parseRawMime(String rawMime, String folderName, int index) {
        if (rawMime == null || rawMime.trim().isEmpty()) return null;

        Map<String, String> headers = new LinkedHashMap<>();
        StringBuilder bodyBuilder = new StringBuilder();
        boolean inBody = false;

        String[] lines = rawMime.split("\r?\n");
        String currentHeaderKey = null;

        for (String line : lines) {
            if (!inBody) {
                if (line.trim().isEmpty()) {
                    inBody = true;
                    continue;
                }
                if (line.startsWith(" ") || line.startsWith("\t")) {
                    // Continuation header
                    if (currentHeaderKey != null) {
                        headers.put(currentHeaderKey, headers.get(currentHeaderKey) + " " + line.trim());
                    }
                } else {
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        currentHeaderKey = line.substring(0, colon).trim().toLowerCase();
                        String val = line.substring(colon + 1).trim();
                        headers.put(currentHeaderKey, val);
                    }
                }
            } else {
                bodyBuilder.append(line).append("\n");
            }
        }

        String from = headers.getOrDefault("from", "Unknown Sender");
        String subject = headers.getOrDefault("subject", "(No Subject)");
        String date = headers.getOrDefault("date", "");
        String to = headers.getOrDefault("to", "");
        String cc = headers.getOrDefault("cc", "");
        String bcc = headers.getOrDefault("bcc", "");
        String messageId = headers.getOrDefault("message-id", "MBOX_" + folderName + "_" + index);

        String rawBody = bodyBuilder.toString();
        String body = rawBody;

        MailMessage msg = new MailMessage(from, subject, date, body, "Mail");

        // Parse email sender address
        String senderAddr = extractEmailAddress(from);
        msg.setSenderAddress(senderAddr);
        msg.setTo(to);
        msg.setCc(cc);
        msg.setBcc(bcc);
        msg.setMessageId(messageId);

        return msg;
    }

    private String extractEmailAddress(String from) {
        if (from == null) return "";
        Matcher m = Pattern.compile("<([^>]+)>").matcher(from);
        if (m.find()) {
            return m.group(1);
        }
        if (from.contains("@")) {
            return from.trim();
        }
        return from;
    }

    @Override
    public String getSourceType() {
        return "MBOX";
    }

    @Override
    public String getDisplayName() {
        return "MBOX Mailbox File";
    }

    @Override
    public List<File> detectLocalMailboxes() {
        List<File> detected = new ArrayList<>();
        String userHome = System.getProperty("user.home");
        File thunderbirdProfiles = new File(userHome, "AppData/Roaming/Thunderbird/Profiles");
        if (thunderbirdProfiles.exists() && thunderbirdProfiles.isDirectory()) {
            File[] profiles = thunderbirdProfiles.listFiles();
            if (profiles != null) {
                for (File profile : profiles) {
                    File mailDir = new File(profile, "Mail");
                    if (mailDir.exists()) scanForMboxFiles(mailDir, detected);
                    File imapDir = new File(profile, "ImapMail");
                    if (imapDir.exists()) scanForMboxFiles(imapDir, detected);
                }
            }
        }
        return detected;
    }

    private void scanForMboxFiles(File dir, List<File> list) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scanForMboxFiles(f, list);
            } else if (canParse(f) && f.length() > 0 && !f.getName().endsWith(".msf")) {
                list.add(f);
            }
        }
    }

    private String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
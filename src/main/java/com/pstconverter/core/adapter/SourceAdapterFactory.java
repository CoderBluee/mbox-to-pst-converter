package com.pstconverter.core.adapter;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Factory class for creating and retrieving SourceAdapter instances.
 * Registers source adapters dynamically to minimize compile-time coupling.
 */
public class SourceAdapterFactory {
    private static final Map<String, String> ADAPTER_REGISTRY = new HashMap<>();

    static {
        try (java.io.InputStream in = SourceAdapterFactory.class.getResourceAsStream("/adapters.properties")) {
            if (in != null) {
                java.util.Properties props = new java.util.Properties();
                props.load(in);
                for (String key : props.stringPropertyNames()) {
                    String cleanKey = key.replace("\uFEFF", "").trim().toLowerCase();
                    ADAPTER_REGISTRY.put(cleanKey, props.getProperty(key).trim());
                }
            } else {
                System.err.println("[WARN] adapters.properties not found on classpath");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static SourceAdapter getAdapterForFile(File file) {
        if (file == null) return null;
        String ext = getFileExtension(file).toLowerCase();
        SourceAdapter adapter = getAdapterByType(ext);
        if (adapter != null) {
            return adapter;
        }
        for (SourceAdapter a : getRegisteredAdapters()) {
            if (a.canParse(file)) {
                return a;
            }
        }
        return null;
    }

    public static SourceAdapter getAdapterByType(String type) {
        if (type == null) return null;
        String className = ADAPTER_REGISTRY.get(type.toLowerCase());
        if (className != null) {
            try {
                Class<?> clazz = Class.forName(className);
                return (SourceAdapter) clazz.getDeclaredConstructor().newInstance();
            } catch (Throwable e) {
                System.err.println("Failed to load adapter class dynamically: " + className + " - " + e.getMessage());
            }
        }
        return null;
    }

    public static boolean isSupported(File file) {
        if (file == null) return false;
        String ext = getFileExtension(file).toLowerCase();
        if (ADAPTER_REGISTRY.containsKey(ext)) {
            return true;
        }
        for (SourceAdapter adapter : getRegisteredAdapters()) {
            if (adapter.canParse(file)) {
                return true;
            }
        }
        return false;
    }

    public static Set<String> getSupportedExtensions() {
        return ADAPTER_REGISTRY.keySet();
    }

    public static java.util.List<SourceAdapter> getRegisteredAdapters() {
        java.util.List<SourceAdapter> list = new java.util.ArrayList<>();
        for (String ext : ADAPTER_REGISTRY.keySet()) {
            SourceAdapter adapter = getAdapterByType(ext);
            if (adapter != null) {
                list.add(adapter);
            }
        }
        return list;
    }

    public static java.util.List<File> detectAllLocalMailboxes() {
        java.util.List<File> allDetected = new java.util.ArrayList<>();
        for (SourceAdapter adapter : getRegisteredAdapters()) {
            try {
                allDetected.addAll(adapter.detectLocalMailboxes());
            } catch (Exception e) {
                System.err.println("Error running auto-detection for adapter " + adapter.getDisplayName() + ": " + e.getMessage());
            }
        }
        return allDetected;
    }

    private static String getFileExtension(File file) {
        String name = file.getName();
        int lastIdx = name.lastIndexOf('.');
        return (lastIdx > 0) ? name.substring(lastIdx + 1) : "";
    }
}
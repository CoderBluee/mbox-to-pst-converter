package com.pstconverter.util;

import java.nio.charset.StandardCharsets;

/**
 * High-performance string masking and unmasking utility.
 * Prevents plain-text secret strings from appearing in compiled bytecode constant pools.
 */
public final class CryptoMask {

    private CryptoMask() {}

    /**
     * Dynamically unmasks a byte array using a single-byte XOR key.
     *
     * @param masked The XOR-masked byte array
     * @param key    The single-byte XOR key
     * @return The original plaintext string
     */
    public static String unmask(byte[] masked, int key) {
        if (masked == null || masked.length == 0) {
            return "";
        }
        byte[] unmasked = new byte[masked.length];
        for (int i = 0; i < masked.length; i++) {
            unmasked[i] = (byte) (masked[i] ^ (key & 0xFF));
        }
        return new String(unmasked, StandardCharsets.UTF_8);
    }

    /**
     * Utility method to generate masked byte arrays during development.
     */
    public static byte[] mask(String plain, int key) {
        if (plain == null) return new byte[0];
        byte[] bytes = plain.getBytes(StandardCharsets.UTF_8);
        byte[] masked = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            masked[i] = (byte) (bytes[i] ^ (key & 0xFF));
        }
        return masked;
    }
}

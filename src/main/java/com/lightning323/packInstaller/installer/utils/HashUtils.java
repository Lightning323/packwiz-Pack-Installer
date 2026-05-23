package com.lightning323.packInstaller.installer.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtils {

    /**
     * Dispatches hashing to the correct algorithm based on the hashType string.
     * * @param hashType The algorithm name (e.g., "sha256", "sha1", "murmur2")
     *
     * @param data The byte array to hash
     * @return A hex string of the hash
     */
    public static String getHash(String hashType, byte[] data) {
        if (data == null) return "";
        if (hashType == null) throw new IllegalArgumentException("Hash type cannot be null");

        // Standardize input
        String normalizedType = hashType.toLowerCase().replace("-", "");

        return switch (normalizedType) {
            case "sha512" -> getStandardHash("SHA-512", data); // Added SHA-512 support
            case "sha256" -> getStandardHash("SHA-256", data);
            case "sha1"   -> getStandardHash("SHA-1", data);
            case "murmur2" -> getMurmur2Hash(data);
            default -> throw new IllegalArgumentException("Unsupported hash type: " + hashType);
        };
    }

    /**
     * Handles standard MessageDigest algorithms (SHA, MD5, etc)
     */
    private static String getStandardHash(String algorithm, byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] encodedHash = digest.digest(data);
            return bytesToHex(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algorithm " + algorithm + " not found in this JVM", e);
        }
    }

    /**
     * Specifically handles the Murmur2 64-bit variant
     */
    private static String getMurmur2Hash(byte[] data) {
        long hash = Murmur2Lib.hash64(data);
        // Note: For Murmur, it's common to return the long as a hex string 
        // or a simple string representation. Using hex for consistency.
        return Long.toHexString(hash);
    }

    /**
     * Converts a byte array into a hex string
     */
    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
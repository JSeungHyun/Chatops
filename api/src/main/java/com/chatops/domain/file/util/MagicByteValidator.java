package com.chatops.domain.file.util;

import java.util.Map;

public final class MagicByteValidator {

    private MagicByteValidator() {}

    private static final Map<String, byte[]> SIGNATURES = Map.ofEntries(
        Map.entry("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
        Map.entry("image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}),
        Map.entry("image/gif", new byte[]{0x47, 0x49, 0x46, 0x38}),
        Map.entry("image/webp", new byte[]{0x52, 0x49, 0x46, 0x46}),
        Map.entry("application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46}),
        Map.entry("application/zip", new byte[]{0x50, 0x4B, 0x03, 0x04}),
        Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            new byte[]{0x50, 0x4B, 0x03, 0x04}),
        Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            new byte[]{0x50, 0x4B, 0x03, 0x04})
    );

    // video/mp4 has "ftyp" at offset 4
    private static final byte[] FTYP = {0x66, 0x74, 0x79, 0x70};

    /**
     * Validate that the first bytes of the file match the declared content type.
     * Returns true if valid or if type has no known signature (pass-through for text/doc types).
     */
    // WebP requires both RIFF header at offset 0 AND "WEBP" at offset 8
    private static final byte[] WEBP_MARKER = {0x57, 0x45, 0x42, 0x50};

    public static boolean validate(byte[] headerBytes, String declaredContentType) {
        if (headerBytes == null || headerBytes.length < 4) return false;
        if (declaredContentType == null) return false;

        // Special case: video types use ftyp at offset 4
        if (declaredContentType.startsWith("video/")) {
            return headerBytes.length >= 8 && matchesAt(headerBytes, FTYP, 4);
        }

        // Special case: WebP requires RIFF at 0 AND WEBP at 8
        if ("image/webp".equals(declaredContentType)) {
            return headerBytes.length >= 12
                && matchesAt(headerBytes, SIGNATURES.get("image/webp"), 0)
                && matchesAt(headerBytes, WEBP_MARKER, 8);
        }

        byte[] expected = SIGNATURES.get(declaredContentType);
        if (expected == null) return true; // no signature to check (text/plain, etc.)

        return matchesAt(headerBytes, expected, 0);
    }

    private static boolean matchesAt(byte[] data, byte[] pattern, int offset) {
        if (data.length < offset + pattern.length) return false;
        for (int i = 0; i < pattern.length; i++) {
            if (data[offset + i] != pattern[i]) return false;
        }
        return true;
    }
}

package com.citydisruptors.incidentservice.photo;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PhotoDataUrl {

    public static final int MAX_BYTES = 2 * 1024 * 1024;

    private static final int MAX_DATA_URL_LENGTH = MAX_BYTES / 3 * 4 + 64;
    private static final Pattern DATA_URL =
            Pattern.compile("^data:(image/(?:jpeg|png|webp));base64,([A-Za-z0-9+/]+={0,2})$");
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);

    private final String contentType;
    private final byte[] data;

    private PhotoDataUrl(String contentType, byte[] data) {
        this.contentType = contentType;
        this.data = data;
    }

    public String contentType() {
        return contentType;
    }

    public byte[] data() {
        return data.clone();
    }

    public static PhotoDataUrl parse(String value) {
        if (value.length() > MAX_DATA_URL_LENGTH) {
            throw new IllegalArgumentException("photo must not be larger than 2 MB");
        }

        Matcher matcher = DATA_URL.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("photo must be a base64 data URL of a JPEG, PNG or WebP image");
        }

        String contentType = matcher.group(1);
        byte[] data = Base64.getDecoder().decode(matcher.group(2));
        if (data.length > MAX_BYTES) {
            throw new IllegalArgumentException("photo must not be larger than 2 MB");
        }
        if (!hasSignatureOf(contentType, data)) {
            throw new IllegalArgumentException("photo content does not match " + contentType);
        }

        return new PhotoDataUrl(contentType, data);
    }

    private static boolean hasSignatureOf(String contentType, byte[] data) {
        return switch (contentType) {
            case "image/jpeg" -> startsWith(data, 0, JPEG);
            case "image/png" -> startsWith(data, 0, PNG);
            case "image/webp" -> startsWith(data, 0, RIFF) && startsWith(data, 8, WEBP);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        return data.length >= offset + prefix.length
                && Arrays.equals(data, offset, offset + prefix.length, prefix, 0, prefix.length);
    }
}

package org.eclipse.sirius.web.ai.codec;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UUIDCodec {
    private final String BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    public UUIDCodec() {}

    public String compress(String stringUUID) {
        var uuid = UUID.fromString(stringUUID);
        return compress(uuid);
    }

    public String compress(UUID uuid) {
        var bb = ByteBuffer.allocate(Long.BYTES * 2);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return encodeBase58(bb.array());
    }

    public UUID decompress(String compressedUUID) {
        if (compressedUUID.length() != 22) {
            throw new IllegalArgumentException("Invalid Id length: " + compressedUUID.length());
        }
        byte[] decoded = decodeBase58(compressedUUID);
        var byteBuffer = ByteBuffer.wrap(decoded);
        return new UUID(byteBuffer.getLong(), byteBuffer.getLong());

    }

    private String encodeBase58(byte[] input) {
        BigInteger num = new BigInteger(1, input);
        BigInteger base = BigInteger.valueOf(58);
        List<Character> encoded = new ArrayList<>();

        while (num.signum() > 0) {
            encoded.add(0, BASE58_ALPHABET.charAt(num.mod(base).intValue()));
            num = num.divide(base);
        }

        for (byte b : input) {
            if (b == 0) {
                encoded.add(0, BASE58_ALPHABET.charAt(0));
            } else {
                break;
            }
        }

        return encoded.stream()
                .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
                .toString();
    }

    private byte[] decodeBase58(String input) {
        BigInteger num = BigInteger.ZERO;
        BigInteger base = BigInteger.valueOf(58);

        for (char c : input.toCharArray()) {
            num = num.multiply(base).add(BigInteger.valueOf(BASE58_ALPHABET.indexOf(c)));
        }

        byte[] bytes = num.toByteArray();
        int length = bytes.length;

        if (length == 17 && bytes[0] == 0) {
            return java.util.Arrays.copyOfRange(bytes, 1, 17);
        }
        if (length < 16) {
            byte[] adjusted = new byte[16];
            System.arraycopy(bytes, 0, adjusted, 16 - length, length);
            return adjusted;
        }

        return bytes;
    }
}

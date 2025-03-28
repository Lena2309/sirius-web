/***********************************************************************************************
 * Copyright (c) 2025 Obeo. All Rights Reserved.
 * This software and the attached documentation are the exclusive ownership
 * of its authors and was conceded to the profit of Obeo S.A.S.
 * This software and the attached documentation are protected under the rights
 * of intellectual ownership, including the section "Titre II  Droits des auteurs (Articles L121-1 L123-12)"
 * By installing this software, you acknowledge being aware of these rights and
 * accept them, and as a consequence you must:
 * - be in possession of a valid license of use conceded by Obeo only.
 * - agree that you have read, understood, and will comply with the license terms and conditions.
 * - agree not to do anything that could conflict with intellectual ownership owned by Obeo or its beneficiaries
 * or the authors of this software.
 *
 * Should you not agree with these terms, you must stop to use this software and give it back to its legitimate owner.
 ***********************************************************************************************/
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

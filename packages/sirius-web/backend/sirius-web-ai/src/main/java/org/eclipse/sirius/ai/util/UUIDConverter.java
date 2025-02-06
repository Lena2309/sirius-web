package org.eclipse.sirius.ai.util;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;

public class UUIDConverter {
    public static String compress(String stringUUID) {
        UUID uuid = UUID.fromString(stringUUID);
        return compress(uuid);
    }

    public static String compress(UUID uuid) {
        ByteBuffer bb = ByteBuffer.allocate(Long.BYTES * 2);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        byte[] array = bb.array();
        return Base64.getEncoder().encodeToString(array);
    }

    public static UUID decompress(String compressedUUID) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(Base64.getDecoder().decode(compressedUUID));
        return new UUID(byteBuffer.getLong(), byteBuffer.getLong());
    }
}

package pers.u8f23.fabric.chaincode;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

public final class UidUtils {
    private UidUtils() {
    }

    public static String generateUid(Instant instant, String clientId, String assetValue) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + Integer.BYTES + Short.BYTES * 2);
        buffer.putLong(instant.getEpochSecond());
        buffer.putInt(instant.getNano());
        buffer.putShort(crc16(clientId));
        buffer.putShort(crc16(assetValue));
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    private static short crc16(String str) {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        int crc = 0xFFFF;
        for (byte b : bytes) {
            crc = (crc & 0xFF00) | (crc & 0x00FF) ^ (b & 0xFF);
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x0001) > 0) {
                    crc = crc >> 1;
                    crc = crc ^ 0xA001;
                } else {
                    crc = crc >> 1;
                }
            }
        }
        return (short) crc;
    }
}

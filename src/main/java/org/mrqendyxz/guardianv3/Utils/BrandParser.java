package org.mrqendyxz.guardianv3.Utils;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class BrandParser {
    public static String parse(byte[] data) {
        if (data == null || data.length == 0 || data.length > 1024) return null;
        int length = 0, offset = 0;
        for (int i = 0; i < 5; i++) {
            if (offset >= data.length) return null;
            int value = data[offset++] & 255;
            if (i == 4 && (value & 240) != 0) return null;
            length |= (value & 127) << (i * 7);
            if ((value & 128) == 0) {
                if (length <= 0 || length > 256 || length != data.length - offset) return null;
                try {
                    String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(data, offset, length)).toString();
                    text = text.replaceAll("[^\\p{L}\\p{N} ._/-]", "").trim();
                    if (text.isEmpty()) return null;
                    return text.substring(0, Math.min(64, text.length()));
                } catch (java.nio.charset.CharacterCodingException ex) { return null; }
            }
        }
        return null;
    }
}

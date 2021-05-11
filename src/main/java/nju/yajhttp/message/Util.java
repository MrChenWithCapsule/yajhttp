package nju.yajhttp.message;

import lombok.SneakyThrows;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class Util {
    @SneakyThrows
    static String readUntil(InputStream stream, char c) {
        var s = new ByteArrayOutputStream();
        for (int i = stream.read(); i != -1 && (char) i != c; i = stream.read()) {
            s.write(i);
        }
        return fromBytes(s.toByteArray()).strip();
    }

    @SneakyThrows
    static String readUntilSeparator(InputStream stream) {
        var s = new ByteArrayOutputStream();
        var last = 0;
        while (true) {
            last = stream.read();
            if (last == -1 || last == ' ' || last == '\r')
                break;
            s.write(last);
        }

        // skip '\n' for crlf
        if (last == '\r') {
            var c = stream.read();
            assert c == '\n';
        }

        return fromBytes(s.toByteArray()).strip();
    }

    static byte[] toBytes(String str) {
        return str.getBytes(StandardCharsets.US_ASCII);
    }

    static String fromBytes(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }
}

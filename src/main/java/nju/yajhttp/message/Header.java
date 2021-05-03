package nju.yajhttp.message;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import lombok.Data;
import lombok.SneakyThrows;

/**
 * HTTP Header {@link https://tools.ietf.org/html/rfc2616#section-4.2}
 */
@Data
public class Header {
    private final String name;
    private final String value;

    @SneakyThrows
    public byte[] toBytes() {
        var s = new ByteArrayOutputStream();
        s.write(Util.toBytes(name));
        s.write(Constants.colonsep);
        s.write(Util.toBytes(value));
        return s.toByteArray();
    }

    static Header read(InputStream s) {
        var h = Util.readUntil(s, '\n').split(":", 2);
        if (h.length != 2)
            return null;
        return new Header(h[0].strip(), h[1].strip());
    }
}

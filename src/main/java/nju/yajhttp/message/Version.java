package nju.yajhttp.message;

import java.io.InputStream;

/**
 * HTTP Versions HTTP 1.0: {@link https://tools.ietf.org/html/rfc1945} HTTP 1.1:
 * {@link https://tools.ietf.org/html/rfc2616}
 */
public enum Version {
    HTTP1_0, HTTP1_1, HTTP2_0;

    public byte[] toBytes() {
        return Util.toBytes(toString().replace('_', '.').replace("HTTP", "HTTP/"));
    }

    static Version read(InputStream s) {
        return Version
                .valueOf(Util.readUntilSeparator(s).replace("HTTP/", "HTTP").replace('.', '_'));
    }
}

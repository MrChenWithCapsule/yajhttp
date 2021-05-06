package nju.yajhttp.message;

import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import lombok.ToString;

import java.io.InputStream;

/**
 * {@link https://tools.ietf.org/html/rfc2616#section-3.2}
 */
@EqualsAndHashCode
@ToString
public class URI {
    private final java.net.URI uri;

    @SneakyThrows
    public URI(String str) {
        uri = new java.net.URI(str);
    }

    public URI(java.net.URI uri) {
        this.uri = uri;
    }

    public String authority() {
        return uri.getAuthority();
    }

    public String fragment() {
        return uri.getFragment();
    }

    public String host() {
        return uri.getHost();
    }

    public String path() {
        return uri.getPath();
    }

    public int port() {
        return uri.getPort();
    }

    public String query() {
        return uri.getQuery();
    }

    public String scheme() {
        return uri.getScheme();
    }

    public String userInfo() {
        return uri.getUserInfo();
    }

    public URI resolve(URI uri) {
        return new URI(this.uri.resolve(uri.uri));
    }

    public URI resolve(String str) {
        return new URI(uri.resolve(str));
    }

    public byte[] toBytes() {
        return Util.toBytes(uri.toASCIIString());
    }

    static URI read(InputStream stream) {
        return new URI(Util.readUntil(stream, ' '));
    }
}

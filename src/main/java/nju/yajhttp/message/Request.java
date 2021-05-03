package nju.yajhttp.message;

import lombok.Data;
import lombok.NonNull;
import lombok.SneakyThrows;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

import static nju.yajhttp.message.Constants.crlf;

/**
 * HTTP Request {@link https://tools.ietf.org/html/rfc2616#section-5}
 */
@Data
public class Request {
    @NonNull
    private Method method = Method.GET;
    @NonNull
    private URI uri = new URI("/");
    @NonNull
    private Version version = Version.HTTP1_1;
    @NonNull
    private HashMap<String, Header> headers = new HashMap<>();
    private byte[] body;

    /**
     * Get header value, or {@code null} if header does not exist
     * 
     * @param name header name
     */
    public String header(String name) {
        var h = headers.get(name);
        if (h == null)
            return null;
        else
            return h.value();
    }

    /**
     * Set header value, or remove header
     * 
     * @param name  header name
     * @param value header value, or {@code null} to remove header
     */
    public Request header(String name, String value) {
        if (value == null)
            headers.remove(name);
        else
            headers.put(name, new Header(name, value));
        return this;
    }

    /**
     * Set header value
     * 
     * @param header header
     */
    public Request header(@NonNull Header header) {
        headers.put(header.name(), header);
        return this;
    }

    /**
     * Set URI to string
     * 
     * @param str uri string
     */
    public Request uri(String str) {
        uri = new URI(str);
        return this;
    }

    public Request uri(URI uri) {
        this.uri = uri;
        return this;
    }

    /**
     * Read {@link Request} from {@link InputStream}
     * 
     * @param stream
     */
    @SneakyThrows
    public static Request read(InputStream stream) {
        var r = new Request().method(Method.read(stream)).uri(URI.read(stream)).version(Version.read(stream));

        for (var h = Header.read(stream); h != null; h = Header.read(stream)) {
            r.header(h);
        }

        var len = r.header("Content-Length");
        if (len != null) {
            r.body(stream.readNBytes(Integer.valueOf(len)));
        }

        return r;
    }

    /**
     * Write {@link Request} to {@link OutputStream}
     * 
     * @param stream
     */
    @SneakyThrows
    public void write(OutputStream stream) {
        stream.write(toBytes());
        stream.flush();
    }

    /**
     * Convert {@link Request} to HTTP message in the form of bytes
     */
    @SneakyThrows
    public byte[] toBytes() {
        var s = new ByteArrayOutputStream();
        s.write(method.toBytes());
        s.write(' ');
        s.write(uri.toBytes());
        s.write(' ');
        s.write(version.toBytes());
        s.write(crlf);
        for (var h : headers.values()) {
            s.write(h.toBytes());
            s.write(crlf);
        }
        s.write(crlf);
        if (body != null)
            s.write(body);
        return s.toByteArray();
    }
}

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
 * HTTP Response {@link https://tools.ietf.org/html/rfc2616#section-6}
 */
@Data
public class Response {
    @NonNull
    private Version version = Version.HTTP1_1;
    @NonNull
    private Status status = Status.OK;
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
    public Response header(String name, String value) {
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
    public Response header(@NonNull Header header) {
        headers.put(header.name(), header);
        return this;
    }

    /**
     * Read {@link Response} from {@link InputStream}
     * 
     * @param stream
     */
    @SneakyThrows
    public static Response read(InputStream stream) {
        Response r = new Response().version(Version.read(stream)).status(Status.read(stream));

        // skip message
        Util.readUntil(stream, '\n');

        for (var h = Header.read(stream); h != null; h = Header.read(stream)) {
            r.header(h);
        }

        var len = r.header("Content-Length");
        if (len != null) {
            r.body(stream.readNBytes(Integer.valueOf(len)));
        } else {
            r.body(stream.readAllBytes());
        }

        return r;
    }

    /**
     * Write {@link Response} to {@link OutputStream}
     * 
     * @param stream
     */
    @SneakyThrows
    public void write(OutputStream stream) {
        stream.write(toBytes());
        stream.flush();
    }

    /**
     * Convert {@link Response} to HTTP message in the form of bytes
     */
    @SneakyThrows
    public byte[] toBytes() {
        var s = new ByteArrayOutputStream();
        s.write(version.toBytes());
        s.write(' ');
        s.write(status.toBytes());
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

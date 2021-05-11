package nju.yajhttp.message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;

/**
 * Response Code {@link https://tools.ietf.org/html/rfc2616#section-6.1.1}
 */
@AllArgsConstructor
@Getter
public enum Status {
    CONTINUE(100), SWITCHING_PROTOCOLS(101), OK(200), CREATED(201), ACCEPTED(202), NON_AUTHORITATIVE_INFORMATION(203),
    NO_CONTENT(204), RESET_CONTENT(205), PARTIAL_CONTENT(206), MULTIPLE_CHOICES(300), MOVED_PERMANENTLY(301),
    FOUND(302), SEE_OTHER(303), NOT_MODIFIED(304), USE_PROXY(305), TEMPORARY_REDIRECT(307), BAD_REQUEST(400),
    UNAUTHORIZED(401), PAYMENT_REQUIRED(402), FORBIDDEN(403), NOT_FOUND(404), METHOD_NOT_ALLOWED(405),
    NOT_ACCEPTABLE(406), PROXY_AUTHENTICATION_REQUIRED(407), REQUEST_TIMEOUT(408), CONFLICT(409), GONE(410),
    LENGTH_REQUIRED(411), PRECONDITION_FAILED(412), REQUEST_ENTITY_TOO_LARGE(413), REQUEST_URI_TOO_LARGE(414),
    UNSUPPORTED_MEDIA_TYPE(415), REQUESTED_RANGE_NOT_SATISFIABLE(416), EXPECTATION_FAILED(417),
    INTERNAL_SERVER_ERROR(500), NOT_IMPLEMENTED(501), BAD_GATEWAY(502), SERVICE_UNAVAILABLE(503), GATEWAY_TIMEOUT(504),
    HTTP_VERSION_NOT_SUPPORTED(505);

    private final int code;

    private static HashMap<Integer, Status> valueMap = new HashMap<>();

    static {
        for (var v : Status.values()) {
            valueMap.put(v.code, v);
        }
    }

    @SneakyThrows
    public byte[] toBytes() {
        var s = new ByteArrayOutputStream();
        s.write(Util.toBytes(Integer.toString(code)));
        s.write(' ');
        s.write(Util.toBytes(StringUtils.capitalize(toString().replace('_', ' ').toLowerCase())));
        return s.toByteArray();
    }

    static Status read(InputStream s) {
        var v = valueMap.get(Integer.valueOf(Util.readUntil(s, ' ')));
        if (v == null)
            throw new IllegalArgumentException();
        return v;
    }
}

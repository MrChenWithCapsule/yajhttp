package nju.yajhttp.interceptor;

import cn.hutool.crypto.SecureUtil;
import nju.yajhttp.Server;
import nju.yajhttp.message.Header;
import nju.yajhttp.message.Request;
import nju.yajhttp.message.Response;
import nju.yajhttp.message.Status;
import nju.yajhttp.util.FileUtil;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;

public class ConditionProcessor {

    public static Response handle(Request request) {
        return switch (request.method()) {
            case GET -> handleGet(request);
            case POST -> handlePost(request);
            default -> null;
        };
    }

    private static Response handleGet(Request request) {
        var relPath = Path.of("./" + Path.of(request.uri().path()).normalize());
        var file = Server.workingDirectory.resolve(relPath).toFile();
        HashMap<String, Header> headers = request.headers();

        for (var cur : headers.entrySet()) {
            // only support if none match
            if (cur.getKey().equals("If-None-Match")) {
                String eTag = cur.getValue().value();
                String local = SecureUtil.md5(Arrays.toString(FileUtil.read(file)));
                if (eTag.equals(local)) {
                    // if none match: false, return 304
                    return new Response().status(Status.NOT_MODIFIED);
                }
                continue;
            }

        }
        
        return null;
    }

    private static Response handlePost(Request request) {
        return null;
    }

}

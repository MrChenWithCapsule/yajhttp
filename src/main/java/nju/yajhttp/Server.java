package nju.yajhttp;

import cn.hutool.crypto.SecureUtil;
import lombok.AllArgsConstructor;
import lombok.Cleanup;
import lombok.NonNull;
import lombok.SneakyThrows;
import nju.yajhttp.interceptor.ConditionProcessor;
import nju.yajhttp.message.Request;
import nju.yajhttp.message.Response;
import nju.yajhttp.message.Status;
import nju.yajhttp.util.FileUtil;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Server {

    public static Path workingDirectory = Path.of(System.getProperty("user.dir"));
    static int port = 8000;

    static Options options = new Options();

    static ConcurrentHashMap<String, String> users = new ConcurrentHashMap<>();

    public static final String signupPath = "/_/signup";

    static {
        options.addOption(Option.builder("h")
                .longOpt("help")
                .desc("Print help message")
                .build());
        options.addOption(Option.builder()
                .longOpt("directory")
                .hasArg()
                .argName("dir")
                .numberOfArgs(1)
                .desc("Base directory of server")
                .build());
    }

    @SneakyThrows
    public static void main(String[] args) {
        // parse args
        var parser = new DefaultParser();
        var cmd = parser.parse(options, args);

        if (cmd.hasOption("h")) {
            new HelpFormatter().printHelp("yajhttp-server [option]... [port]",
                    "options:",
                    options,
                    "");
            return;
        }

        if (cmd.hasOption("directory")) {
            workingDirectory = Path.of(cmd.getOptionValue("directory"));
        }

        if (cmd.getArgs().length != 0) {
            port = Integer.parseInt(cmd.getArgs()[0]);
        }
        // start serving requests
        @Cleanup
        var ss = new ServerSocket(port);
        var pool = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),
                2 * Runtime.getRuntime().availableProcessors(), 1, TimeUnit.SECONDS, new LinkedBlockingDeque<>());
        System.out.printf("Server started at localhost:%d%n", port);

        while (true) {
            var s = ss.accept();
            System.out.println("Accepted connection from " + s.getRemoteSocketAddress());
            pool.execute(new RequestHandler(s));
        }
    }
}

@AllArgsConstructor
class RequestHandler implements Runnable {
    @NonNull
    Socket socket;

    private static final String templateHTML = "<!DOCTYPE html>\n" + "<html>\n" + "  <head>\n"
            + "    <meta charset=\"utf-8\">\n" + "  </head>\n" + "  <h1>Index of ${directory}</h1>\n" + "  <ol>\n"
            + "    ${content}\n" + "  </ol>\n" + "</html>\n";

    @Override
    @SneakyThrows
    public void run() {
        @Cleanup
        var socket = this.socket;

        try {
            while (true) {
                var request = Request.read(socket.getInputStream());
                if (!authorize(request))
                    continue;
                if (request.uri().path().equals(Server.signupPath))
                    handleSignup(request);
                // before handle request
                // condition request
                Response response = ConditionProcessor.handle(request);

                if (response != null) {
                    response.write(socket.getOutputStream());
                    continue;
                }

                response = switch (request.method()) {
                case GET -> handleGet(request);
                case POST -> handlePost(request);
                default -> error(Status.BAD_REQUEST);
                };
                // 统一处理，如果需要
                response.write(socket.getOutputStream());
            }
        } catch(RuntimeException e) {
            // exit
        }
    }


    private Response handleGet(Request request) throws IOException {
        System.out.println("GET: " + request.uri().path());
        var relPath = Path.of("./" + Path.of(request.uri().path()).normalize());
        var file = Server.workingDirectory.resolve(relPath).toFile();

        if (!file.exists())
            return error(Status.NOT_FOUND);
        else if (file.isDirectory())
            return listDirectory(file);
        else {
            //把文件发给服务端
            var body = FileUtil.read(file);
            String eTag = SecureUtil.md5(Arrays.toString(body));
            //获取文件MIME类型
            String MIME = handleMIMEType(file);
            return new Response()
                    .status(Status.OK)
                    .header("Content-Length", Integer.toString(body.length))
                    .header("Content-Type", MIME)
                    .header("ETag", eTag)
                    .body(body);
        }
    }


    private Response handlePost(Request request) throws IOException {
        //将请求内容附加到对应文件
        System.out.println("POST: " + request.uri().path());
        var relPath = Path.of("./" + Path.of(request.uri().path()).normalize());
        var file = Server.workingDirectory.resolve(relPath).toFile();

        if (!file.exists())
            return error(Status.NOT_FOUND);
        else if (file.isDirectory())
            return listDirectory(file);
        else {
            //将请求内容附加到对应文件中
            byte[] addition = request.body();
            OutputStream out = new BufferedOutputStream(new FileOutputStream(file, true));
            out.write(addition);
            out.flush();
            var body = FileUtil.read(file);
            //获取文件MIME类型
            String MIME = handleMIMEType(file);
            return new Response()
                    .status(Status.OK)
                    .header("Content-Length", Integer.toString(body.length))
                    .header("Content-Type", MIME)
                    .body(body);
        }
    }

    private String handleMIMEType(File file) throws IOException {
        Path path = new File(String.valueOf(file)).toPath();
        String mimeType = Files.probeContentType(path);
        if (mimeType == null) {
            if (isText(file))
                mimeType = "text/plain";
            else
                mimeType = "application/octet-stream";
        }
        return mimeType;
    }

    private boolean isText(File file) {
        boolean isText = true;
        try {
            FileInputStream fin = new FileInputStream(file);
            long len = file.length();
            for (int j = 0; j < (int) len; j++) {
                int t = fin.read();
                if ((t > 127 && t < 160) || (t < 32 && t != 9 && t != 10 && t != 13)) {
                    isText = false;
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return isText;
    }

    @SneakyThrows
    private Response listDirectory(File dir) {
        assert dir.isDirectory();
        var files = dir.listFiles();
        assert files != null;
        var filelist = Arrays.stream(files).map(f -> {
            var str = f.getName() + (f.isDirectory() ? "/" : "");
            return "<li><a href='" + str + "'>" + str + "</li>";
        }).sorted().reduce((f1, f2) -> f1 + "\n" + f2).orElse("");

        filelist = "<li><a href='.'>.</li>\n<li><a href='..'>..</li>\n" + filelist;

        var body = templateHTML
                .replace("${directory}", Server.workingDirectory.relativize(dir.toPath()).toString() + "/")
                .replace("${content}", filelist).getBytes(StandardCharsets.UTF_8);

        return new Response().status(Status.OK).header("Content-Length", Integer.toString(body.length))
                .header("Content-Type", "text/html").body(body);
    }

    @SneakyThrows
    private Response error(Status err) {
        new Response()
                .status(err)
                .header("Connection", "close")
                .body(err.toBytes())
                .write(socket.getOutputStream());
        throw new RuntimeException("Exit");
    }

    @SneakyThrows
    private boolean authorize(Request request) {
        if (request.uri().path().equals(Server.signupPath))
            return true;

        var msg = request.header("Authorization");
        if (msg == null || !msg.startsWith("Basic ")) {
            errorUnauthorized(false);
            return false;
        }

        var info = new String(Base64.getDecoder().decode(msg.substring(6)), StandardCharsets.UTF_8).split(":", 2);
        if (info.length != 2) {
            errorUnauthorized(true);
            return false;
        }

        var passwd = Server.users.get(info[0]);
        if (passwd == null || !passwd.equals(info[1])) {
            errorUnauthorized(true);
            return false;
        }
        return true;
    }

    @SneakyThrows
    private void errorUnauthorized(boolean redirect) {
        if (!redirect) {
            new Response()
                    .status(Status.UNAUTHORIZED)
                    .header("WWW-Authenticate", "Basic realm=\"Visit /_/signup to signup\"")
                    .header("Content-Length", "0")
                    .write(socket.getOutputStream());
        } else {
            var msg = "<html><body>Visit <a href='/_/signup'>/_/signup</a> to signup</body></html>".getBytes(StandardCharsets.UTF_8);
            new Response()
                    .status(Status.FORBIDDEN)
                    .header("Content-Type", "text/html")
                    .header("Content-Length", Integer.toString(msg.length))
                    .body(msg)
                    .write(socket.getOutputStream());
        }
    }

    private static final byte[] signupPage = ("<html>\n" + "  <head>\n" + "    <meta charset='utf-8'>\n" + "  </head>\n"
            + "  <body>\n" + "    <form name='signup' action='/_/signup' method='post'>\n"
            + "      username: <input type='text' name='username'><br>\n"
            + "      password: <input type='text' name='password'><br>\n"
            + "      <input type='submit' value='submit'>\n" + "    </form>\n" + "  </body>\n"
            + "</html>\n").getBytes(StandardCharsets.UTF_8);

    @SneakyThrows
    private void handleSignup(Request request) {
        assert request.uri().path().equals(Server.signupPath);

        switch (request.method()) {
            case GET:
                new Response()
                        .status(Status.OK)
                        .header("Content-Length", Integer.toString(signupPage.length))
                        .header("Content-Type", "text/html").body(signupPage)
                        .write(socket.getOutputStream());
                break;
            case POST:
                var args = parseArgs(new String(request.body(), StandardCharsets.UTF_8));
                if (!args.containsKey("password") || !args.containsKey("username")) {
                    error(Status.BAD_REQUEST);
                }

                Server.users.put(URLDecoder.decode(args.get("username"), StandardCharsets.UTF_8),
                        URLDecoder.decode(args.get("password"), StandardCharsets.UTF_8));

                var message = "Signup Success".getBytes(StandardCharsets.UTF_8);
                new Response().status(Status.OK).header("Content-Length", Integer.toString(message.length)).body(message).write(socket.getOutputStream());
                break;
            default:
                error(Status.BAD_REQUEST);
        }
    }

    private static HashMap<String, String> parseArgs(String query) {
        if (query == null)
            return new HashMap<>();

        var args = query.split("&");
        var m = new HashMap<String, String>();
        for (var arg : args) {
            var pair = arg.split("=");
            if (pair.length != 2)
                continue;
            m.put(pair[0], pair[1]);
        }
        return m;
    }
}


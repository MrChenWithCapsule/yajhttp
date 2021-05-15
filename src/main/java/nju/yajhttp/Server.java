package nju.yajhttp;

import lombok.AllArgsConstructor;
import lombok.Cleanup;
import lombok.NonNull;
import lombok.SneakyThrows;
import nju.yajhttp.message.Method;
import nju.yajhttp.message.Request;
import nju.yajhttp.message.Response;
import nju.yajhttp.message.Status;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Server {
    static Path workingDirectory = Path.of(System.getProperty("user.dir"));
    static int port = 8000;

    static Options options = new Options();

    static ConcurrentHashMap<String, String> users = new ConcurrentHashMap<>();

    public static final String signupPath = "/_/signup";

    static {
        options.addOption(Option.builder("h").longOpt("help").desc("Print help message").build());
        options.addOption(Option.builder().longOpt("directory").hasArg().argName("dir").numberOfArgs(1)
                .desc("Base directory of server").build());
    }

    @SneakyThrows
    public static void main(String[] args) {
        // parse args
        var parser = new DefaultParser();
        var cmd = parser.parse(options, args);
        if (cmd.hasOption("h")) {
            new HelpFormatter().printHelp("yajhttp-server [option]... [port]", "options:", options, "");
            return;
        }
        if (cmd.hasOption("directory")) {
            workingDirectory = Path.of(cmd.getOptionValue("directory"));
        }
        if (cmd.getArgs().length != 0) {
            port = Integer.valueOf(cmd.getArgs()[0]);
        }

        // start serving requests
        @Cleanup
        var ss = new ServerSocket(port);
        var pool = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),
                2 * Runtime.getRuntime().availableProcessors(), 1, TimeUnit.SECONDS, new LinkedBlockingDeque<>());
        System.out.println(String.format("Server started at localhost:%d", port));
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

        while (true) {
            var request = Request.read(socket.getInputStream());

            if (!authorize(request))
                continue;

            if (request.uri().path().equals(Server.signupPath))
                handleSignup(request);

            switch (request.method()) {
            case GET:
                handleGet(request);
                break;
            default:
                error(Status.BAD_REQUEST);
            }
        }
    }

    private void handleGet(Request request) {
        System.out.println("GET: " + request.uri().path());
        var relPath = Path.of("./" + Path.of(request.uri().path()).normalize());
        var file = Server.workingDirectory.resolve(relPath).toFile();

        if (!file.exists())
            error(Status.NOT_FOUND);
        else if (file.isDirectory())
            listDirectory(file);
        else
            error(Status.NOT_IMPLEMENTED);
    }

    @SneakyThrows
    private void listDirectory(File dir) {
        assert dir.isDirectory();

        var files = dir.listFiles();
        var filelist = Arrays.stream(files).map(f -> {
            var str = f.getName() + (f.isDirectory() ? "/" : "");
            return "<li><a href='" + str + "'>" + str + "</li>";
        }).sorted().reduce((f1, f2) -> f1 + "\n" + f2).get();
        filelist = "<li><a href='.'>.</li>\n<li><a href='..'>..</li>\n" + filelist;

        var body = templateHTML
                .replace("${directory}", Server.workingDirectory.relativize(dir.toPath()).toString() + "/")
                .replace("${content}", filelist).getBytes(StandardCharsets.UTF_8);

        new Response().status(Status.OK).header("Content-Length", Integer.toString(body.length))
                .header("Content-Type", "text/html").body(body).write(socket.getOutputStream());
    }

    @SneakyThrows
    private void error(Status err) {
        new Response().status(err).header("Connection", "close").body(err.toBytes()).write(socket.getOutputStream());
        throw new RuntimeException("Exit");
    }

    @SneakyThrows
    private boolean authorize(Request request) {
        if (checkAuthorize(request))
            return true;

        errorUnauthorized();
        return false;
    }

    private boolean checkAuthorize(Request request) {
        if (request.uri().path().equals(Server.signupPath))
            return true;

        var msg = request.header("Authorization");
        if (msg == null)
            return false;

        var info = new String(Base64.getDecoder().decode(msg), StandardCharsets.UTF_8).split(":", 1);
        if (info.length != 2)
            return false;

        var passwd = Server.users.get(info[0]);
        if (passwd == null || !passwd.equals(info[1]))
            return false;

        return true;
    }

    @SneakyThrows
    private void errorUnauthorized() {
        new Response().status(Status.UNAUTHORIZED).header("WWW-Authorization", "Basic").write(socket.getOutputStream());
    }

    private static final byte[] signupPage = "".getBytes(StandardCharsets.UTF_8);

    @SneakyThrows
    private void handleSignup(Request request) {
        assert request.uri().path().equals(Server.signupPath);

        if (request.method() != Method.GET)
            error(Status.NOT_IMPLEMENTED);

        new Response().status(Status.OK).header("Content-Length", Integer.toString(signupPage.length))
                .header("Content-Type", "text/html").body(signupPage).write(socket.getOutputStream());
    }
}

package nju.yajhttp;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.File;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import lombok.Cleanup;
import lombok.SneakyThrows;
import nju.yajhttp.message.Method;
import nju.yajhttp.message.Request;
import nju.yajhttp.message.URI;
import nju.yajhttp.message.Version;

public class Client {

    Map<String, String> header = new HashMap<String, String>();
    ArrayList<String> body = new ArrayList<String>();// 需要转成byte
    FileOutputStream fos;
    Method method = Method.GET;
    URI uri;
    boolean ish = false;
@SneakyThrows
    public void parse(String[] args) {
        // TODO:headers的name需要化成标准格式;
        for (int i = 0; i < args.length; ++i) {
            if (args[i].equals("-h") || args[i].equals("--help")) {
                ish = true;
                System.out.println("-u --user <username:password>: 指定用户名和密码\n-o <file>: 输出到文件\n-d --data <data>: 指定使用 POST 请求以及 body\n-H --header <header>: 指定发送请求时的 header，可以多次使用以指定多个 header\n-h --help: 打印帮助信息<url>: 要请求的网址");// helpString的值需要更换
                return;
            }
        }
        for (int i = 0; i < args.length; ++i) {
            switch (args[i]) {
                case "-u":
                case "--user":
                    String tempstr = "Basic "
                            + Base64.getEncoder().encodeToString(args[++i].getBytes(StandardCharsets.US_ASCII));
                    header.put("Authorization", tempstr);
                    break;
                case "-o":
                    String pathname = args[++i];
                    // TODO: pathname
                    File file=new File(pathname);
                    File parentFile=file.getParentFile();
                    if(!parentFile.exists()){
                        parentFile.mkdirs();
                    }
                    if(!file.exists()){
                        file.createNewFile();
                    }
                    fos = new FileOutputStream(file);
                    break;
                case "-d":
                case "--data":
                    method = Method.POST;
                    body.add(args[++i]);
                    header.put("Content-Type", "application/x-www-form-urlencoded");
                    break;
                case "-H":
                case "--header":
                    String[] arg = args[++i].split(":");
                    header.put(arg[0], arg[1]);
                    break;
                default:
                    uri = new URI(args[i]);
                    break;
            }
        }
    }

    @SneakyThrows
    public static void main(String[] args) {
        /*
         * var uri = new URI(args[0]); var port = 0; switch (uri.scheme()) { case
         * "http": port = 80; break; default: throw new Error(); } var request = new
         * Request().method(Method.GET).version(Version.HTTP1_0).uri(uri).header("Host",
         * uri.host());
         * 
         * @Cleanup var socket = new Socket(uri.host(), port); var os =
         * socket.getOutputStream(); request.write(os); var is =
         * socket.getInputStream(); System.out.println(new String(is.readAllBytes(),
         * StandardCharsets.UTF_8));
         */
        Client client = new Client();
        client.parse(args);
        if (client.ish) {
            return;
        }
        int port = 0;
        switch (client.uri.scheme()) {
            case "http":
                port = 80;
                break;
            default:
                throw new Error();
        }
        if(client.uri.port()!=-1){
            port=client.uri.port();
        }
        Request request = new Request().method(client.method).version(Version.HTTP1_0).uri(client.uri).header("Host",
                client.uri.host());
        if (!client.body.isEmpty()) {
            String tempStr = String.join("&", client.body);
            byte[] bytes = new String(tempStr).getBytes();
            request.body(bytes);
            client.header.put("Content-Length", String.valueOf(bytes.length));
        }
        for (var i : client.header.keySet()) {
            request.header(i, client.header.get(i));
        }
        @Cleanup
        Socket socket = new Socket(client.uri.host(), port);
        var os = socket.getOutputStream();
        request.write(os);
        var is = socket.getInputStream();
        if (client.fos == null) {
            System.out.println(new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
        else{
            client.fos.write(is.readAllBytes());
        }
    }
}

package nju.yajhttp;

import java.io.*;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import lombok.Cleanup;
import lombok.NonNull;
import lombok.SneakyThrows;
import nju.yajhttp.message.*;
import org.mozilla.universalchardet.UniversalDetector;

public class Client {
    Map<String, String> header = new HashMap<String, String>();
    ArrayList<String> body = new ArrayList<String>();// 需要转成byte
    FileOutputStream fos;
    Method method = Method.POST;
    URI uri;
    boolean ish = false;


    Response response;
    Request request;
    int requestCnt = 0;
    String msg = "";



    @SneakyThrows
    public static void main(String[] args) {


        Client client = new Client();
//        client.body.add("aaa");

        //解析命令行
        client.parse(args);
        if (client.ish) {
            return;
        }

        //构造请求
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
        client.request = request;


        //发送请求,得到相应
        client.response = client.sendRequest();

        //处理响应状态
        client.handleResponseStatus();

        //输出
        client.output();


    }


    @SneakyThrows
    public void parse(String[] args) {
        for (int i = 0; i < args.length; ++i) {
            if (args[i].equals("-h") || args[i].equals("--help")) {
                ish = true;
                System.out.println("-u --user <username:password>: 指定用户名和密码\n-o <file>: 输出到文件\n-d --data <data>: 指定使用 POST 请求以及 body\n-H --header <header>: 指定发送请求时的 header，可以多次使用以指定多个 header\n-h --help: 打印帮助信息\n<url>: 要请求的网址");// helpString的值需要更换
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






    Response sendRequest() throws IOException {
        URI uri = request.uri();
        var port = 0;
        switch (uri.scheme()) {
            case "http":
                port = 80;
                break;
            default:
                throw new Error();
        }

        if(uri.port()!=-1){
            port=uri.port();
        }
        @Cleanup
        Socket socket = new Socket(uri.host(), port);
        OutputStream os = socket.getOutputStream();
        request.write(os);

        //response = Response.read(socket.getInputStream());


        return Response.read(socket.getInputStream());
    }

    void handleResponseStatus() throws IOException {

        switch (response.status().code()){
            case 200:
                break;
            case 301:
            case 302:
                @NonNull
                String location = response.header("Location");
                URI newUri = request.uri().resolve(location);
                request.uri(newUri);
                request.header("Host", newUri.host());
                response = sendRequest();
                if(requestCnt > 10){
                    msg = "重定向次数过多";
                    break;
                }
                requestCnt++;
                handleResponseStatus();
                break;
            case 304:
                msg = "页面未作调整";
                break;
            default:
                msg = "未处理的状态：" + response.status().toString();

        }

    }

    void output() throws IOException {
        @Cleanup
        OutputStream os = this.fos == null ? System.out : this.fos;
        //System.out.println(new String(response.toBytes()));
        if(!msg.equals("")){
            System.out.println(msg);
        }
        if(response.body() != null && response.body().length != 0){
            String[] contentType = response.header("Content-Type").split("; *");
            switch (contentType[0].split("/")[0]){
                case "text":
                    @Cleanup
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));

                    //解析body编码
                    String charset = "us-ascii"; //默认编码类型应该为us-ascii参考https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types，但访问百度得到的body部分的编码为gbk，且content-type中没有说明编码类型(是在网页的源代码中说明的)
                    if(contentType.length > 1){//如果有编码参数，则按照参数解码
                        charset = contentType[1].split("=")[1];
                    }else {//如果没有编码参数则使用库推测编码
                        String result = getEncoding(response.body());
                        if(result != null){//如果推测成功则使用推测结果，否则仍然使用默认编码
                            charset = result;
                        }
                    }

                    //通过解析到的编码来将body部分解析成字符串
                    String content = new String(response.body(), charset);
                    writer.write(content);
                    break;
                default:
                    if(os == System.out){
                        System.out.println("返回类型为：" + contentType[0] + "建议重新发送请求并使用-o选项将结果保存到文件");
                    }else {
                        //System.out.println("返回类型为：" + contentType[0] + "请检查文件后缀是否正确");
                    }
                    os.write(response.body());
                    break;
            }
        }
    }

    String getEncoding(byte[] bytes){
        UniversalDetector detector = new UniversalDetector(null);
        detector.handleData(bytes, 0, bytes.length);
        detector.dataEnd();
        String encoding = detector.getDetectedCharset();
        detector.reset();
        return encoding;
    }


}


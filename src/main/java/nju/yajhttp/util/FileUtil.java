package nju.yajhttp.util;

import lombok.SneakyThrows;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;


public class FileUtil {

    @SneakyThrows
    public static byte[] read(File file) {
        FileInputStream fis = new FileInputStream(file);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] b = new byte[1024];
        int len;
        while ((len = fis.read(b)) != -1) {
            bos.write(b, 0, len);
        }
        return bos.toByteArray();
    }

}

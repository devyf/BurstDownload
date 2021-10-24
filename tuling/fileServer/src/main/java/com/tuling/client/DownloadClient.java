package com.tuling.client;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.concurrent.*;

/**
 * @Description:
 * @Author: huang
 * @Date: 2021/10/23 12:52
 */
@RestController
public class DownloadClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadClient.class);
    private final static long PER_PAGE = 1024L * 1024L * 50L;
    private final static String DOWN_PATH = "F:\\fileItem";
    ExecutorService taskExecutor = Executors.newFixedThreadPool(10);

    @RequestMapping("/downloadFile")
    public String downloadFile() {
        // 探测下载
        FileInfo fileInfo = download(0, 10, -1, null);
        if (fileInfo != null) {
            long pages =  fileInfo.fSize / PER_PAGE;
            for (long i = 0; i <= pages; i++) {
                Future<FileInfo> future = taskExecutor.submit(new DownloadThread(i * PER_PAGE, (i + 1) * PER_PAGE - 1, i, fileInfo.fName));
                if (!future.isCancelled()) {
                    try {
                        fileInfo = future.get();
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                }
            }
            return System.getProperty("user.home") + "\\Downloads\\" + fileInfo.fName;
        }
        return null;
    }

    class FileInfo {
        long fSize;
        String fName;

        public FileInfo(long fSize, String fName) {
            this.fSize = fSize;
            this.fName = fName;
        }
    }

    /**
     * 根据开始位置/结束位置
     * 分片下载文件，临时存储文件分片
     * 文件大小=结束位置-开始位置
     *
     * @return
     */
    private FileInfo download(long start, long end, long page, String fName) {
        File dir = new File(DOWN_PATH);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 断点下载
        File file = new File(DOWN_PATH, page + "-" + fName);
        if (file.exists() && page != -1 && file.length() == PER_PAGE) {
            return null;
        }
        try {
            HttpClient client = HttpClients.createDefault();
            HttpGet httpGet = new HttpGet("http://127.0.0.1:8080/download");
            httpGet.setHeader("Range", "bytes=" + start + "-" + end);
            HttpResponse response = client.execute(httpGet);
            String fSize = response.getFirstHeader("fSize").getValue();
            fName = URLDecoder.decode(response.getFirstHeader("fName").getValue(), "UTF-8");
            HttpEntity entity = response.getEntity();
            InputStream is = entity.getContent();
            FileOutputStream fos = new FileOutputStream(file);
            byte[] buffer = new byte[1024];
            int ch;
            while ((ch = is.read(buffer)) != -1) {
                fos.write(buffer, 0, ch);
            }
            is.close();
            fos.flush();
            fos.close();
            // 最后一个分片
            if (end - Long.parseLong(fSize) > 0) {
                // 开始合并文件
                mergeFile(fName, page);
            }

            return new FileInfo(Long.parseLong(fSize), fName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void mergeFile(String fName, long page) {
        File file = new File(DOWN_PATH, fName);
        try {
            BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(file));
            for (long i = 0; i <= page; i++) {
                File tempFile = new File(DOWN_PATH, i + "-" + fName);
                while (!file.exists() || (i != page && tempFile.length() < PER_PAGE)) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                byte[] bytes = FileUtils.readFileToByteArray(tempFile);
                os.write(bytes);
                os.flush();
                tempFile.delete();
            }
            File testFile = new File(DOWN_PATH, -1 + "-null");
            testFile.delete();
            os.flush();
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取远程文件尺寸
     */
    private long getRemoteFileSize(String remoteFileUrl) throws IOException {
        long fileSize = 0;
        HttpURLConnection httpConnection = (HttpURLConnection) new URL(remoteFileUrl).openConnection();
        //使用HEAD方法
        httpConnection.setRequestMethod("HEAD");
        int responseCode = httpConnection.getResponseCode();
        if (responseCode >= 400) {
            LOGGER.debug("Web服务器响应错误!");
            return 0;
        }
        String sHeader;
        for (int i = 1;; i++) {
            sHeader = httpConnection.getHeaderFieldKey(i);
            if (sHeader != null && sHeader.equals("Content-Length")) {
                LOGGER.debug("文件大小ContentLength:" + httpConnection.getContentLength());
                fileSize = Long.parseLong(httpConnection.getHeaderField(sHeader));
                break;
            }
        }
        return fileSize;
    }

    class DownloadThread implements Callable<FileInfo> {
        long start;
        long end;
        long page;
        String fName;

        public DownloadThread(long start, long end, long page, String fName) {
            this.start = start;
            this.end = end;
            this.page = page;
            this.fName = fName;
        }

        @Override
        public FileInfo call() {
            return download(start, end, page, fName);
        }
    }
}

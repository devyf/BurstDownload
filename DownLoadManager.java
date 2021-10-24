package com.fengye.download;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Description: 文件下载管理类
 * @Author: huang
 * @Date: 2021/10/20 23:06
 */
public class DownLoadManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DownLoadManager.class);
    /**
     * 每个线程下载的字节数
     */
    private long unitSize = 1000 * 1024;
    private ExecutorService taskExecutor = Executors.newFixedThreadPool(10);

    private CloseableHttpClient httpClient;

    public DownLoadManager() {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(100);
        httpClient = HttpClients.custom().setConnectionManager(cm).build();
    }
    public static void main(String[] args) throws IOException {
        new DownLoadManager().doDownload();
    }
    /**
     * 启动多个线程下载文件
     */
    public void doDownload() throws IOException {
        //要下载的url
        String remoteFileUrl = "https://dldir1.qq.com/qqyy/pc/QQPlayerSetup4.6.3.1104.exe";
        String localPath = "E://MyTestMp3//";
        String fileName = new URL(remoteFileUrl).getFile();
        System.out.println("远程文件名称：" + fileName);
        fileName = fileName.substring(fileName.lastIndexOf("/") + 1, fileName.length()).replace("%20", " ");
        System.out.println("本地文件名称：" + fileName);
        long fileSize = this.getRemoteFileSize(remoteFileUrl);
        this.createFile(localPath, localPath + fileName, fileSize);
        Long threadCount = (fileSize / unitSize) + (fileSize % unitSize != 0 ? 1 : 0);
        long offset = 0;

        CountDownLatch end = new CountDownLatch(threadCount.intValue());
        if (fileSize <= unitSize) {// 如果远程文件尺寸小于等于unitSize
            DownloadThreadTask downloadThread = new DownloadThreadTask(remoteFileUrl, localPath + fileName, offset, fileSize, end, httpClient);
            taskExecutor.execute(downloadThread);
        } else {// 如果远程文件尺寸大于unitSize
            for (int i = 1; i < threadCount; i++) {
                DownloadThreadTask downloadThread = new DownloadThreadTask(remoteFileUrl, localPath + fileName, offset, unitSize, end, httpClient);
                taskExecutor.execute(downloadThread);
                offset = offset + unitSize;
            }
            if (fileSize % unitSize != 0) {// 如果不能整除，则需要再创建一个线程下载剩余字节
                DownloadThreadTask downloadThread = new DownloadThreadTask(remoteFileUrl, localPath + fileName, offset, fileSize - unitSize * (threadCount - 1), end, httpClient);
                taskExecutor.execute(downloadThread);
            }
        }
        try {
            end.await();
        } catch (InterruptedException e) {
            LOGGER.error("DownLoadManager exception msg:{}", ExceptionUtils.getFullStackTrace(e));
            e.printStackTrace();
        }
        taskExecutor.shutdown();
        LOGGER.debug("下载完成！{} ", localPath + fileName);
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
                System.out.println("文件大小ContentLength:" + httpConnection.getContentLength());
                fileSize = Long.parseLong(httpConnection.getHeaderField(sHeader));
                break;
            }
        }
        return fileSize;
    }

    /**
     * 创建指定大小的文件
     */
    private void createFile(String localPath, String fileName, long fileSize) throws IOException {
        File file = new File(localPath);
        if (!file.exists()) {
            file.mkdirs();
        }
        File newFile = new File(fileName);
        RandomAccessFile raf = new RandomAccessFile(newFile, "rw");
        raf.setLength(fileSize);
        raf.close();
    }
}

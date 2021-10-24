package com.fengye.download;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.CountDownLatch;

/**
 * @Description:
 * @Author: huang
 * @Date: 2021/10/20 23:06
 */
public class DownloadThreadTask implements Runnable{
    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadThreadTask.class);

    /**
     * 待下载的文件
     */
    private String url = null;

    /**
     * 本地文件名
     */
    private String fileName = null;

    /**
     * 偏移量
     */
    private long offset = 0;

    /**
     * 分配给本线程的下载字节数
     */
    private long length = 0;

    private CountDownLatch end;
    private CloseableHttpClient httpClient;
    private HttpContext context;

    /**
     * @param url
     *            下载文件地址
     * @param file 另存文件名
     *
     * @param offset
     *            本线程下载偏移量
     * @param length
     *            本线程下载长度
     */
    public DownloadThreadTask(String url, String file, long offset, long length, CountDownLatch end, CloseableHttpClient httpClient) {
        this.url = url;
        this.fileName = file;
        this.offset = offset;
        this.length = length;
        this.end = end;
        this.httpClient = httpClient;
        this.context = new BasicHttpContext();
        LOGGER.debug("偏移量=" + offset + ";字节数=" + length);
    }


    @Override
    public void run() {
        try {
            HttpGet httpGet = new HttpGet(this.url);
            httpGet.addHeader("Range", "bytes=" + this.offset + "-" + (this.offset + this.length - 1));
            //httpGet.addHeader("Referer", "http://api.bilibili.com");
            CloseableHttpResponse response = httpClient.execute(httpGet, context);
            BufferedInputStream bis = new BufferedInputStream(response.getEntity().getContent());
            byte[] buff = new byte[1024];
            int bytesRead;
            File newFile = new File(fileName);
            RandomAccessFile raf = new RandomAccessFile(newFile, "rw");
            while ((bytesRead = bis.read(buff, 0, buff.length)) != -1) {
                raf.seek(this.offset);
                raf.write(buff, 0, bytesRead);
                this.offset = this.offset + bytesRead;
            }
            raf.close();
            bis.close();
        } catch (ClientProtocolException e) {
            LOGGER.error("DownloadThread exception msg:{}", e.getMessage());
        } catch (IOException e) {
            LOGGER.error("DownloadThread exception msg:{}", ExceptionUtils.getFullStackTrace(e));
        } finally {
            end.countDown();
            LOGGER.info(end.getCount() + " is go on!");
            System.out.println(end.getCount() + " is go on!");
        }
    }
}

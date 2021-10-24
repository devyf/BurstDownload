package com.tuling.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;

/**
 * @Description:
 * @Author: huang
 * @Date: 2021/10/22 7:16
 */
@Controller
public class DownLoadController {
    private static final String UTF8 = "UTF-8";
    @RequestMapping("/download")
    public void downLoadFile(HttpServletRequest request, HttpServletResponse response) throws IOException {
        File file = new File("D:\\DevTools\\ideaIU-2021.1.3.exe");
        response.setCharacterEncoding(UTF8);
        InputStream is = null;
        OutputStream os = null;
        try {
            // 分片下载 Range表示方式 bytes=100-1000  100-
            long fSize = file.length();
            response.setContentType("application/x-download");
            String fileName = URLEncoder.encode(file.getName(), UTF8);
            response.addHeader("Content-Disposition", "attachment;filename=" + fileName);
            // 支持分片下载
            response.setHeader("Accept-Range", "bytes");
            response.setHeader("fSize", String.valueOf(fSize));
            response.setHeader("fName", fileName);

            long pos = 0, last = fSize - 1, sum = 0;
            if (null != request.getHeader("Range")) {
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                String numberRange = request.getHeader("Range").replaceAll("bytes=", "");
                String[] strRange = numberRange.split("-");
                if (strRange.length == 2) {
                    pos = Long.parseLong(strRange[0].trim());
                    last = Long.parseLong(strRange[1].trim());
                    if (last > fSize-1) {
                        last = fSize - 1;
                    }
                } else {
                    pos = Long.parseLong(numberRange.replaceAll("-", "").trim());
                }
            }
            long rangeLength = last - pos + 1;
            String contentRange = new StringBuffer("bytes").append(pos).append("-").append(last).append("/").append(fSize).toString();
            response.setHeader("Content-Range", contentRange);
            response.setHeader("Content-Length", String.valueOf(rangeLength));

            os = new BufferedOutputStream(response.getOutputStream());
            is = new BufferedInputStream(new FileInputStream(file));
            is.skip(pos);
            byte[] buffer = new byte[1024];
            int length = 0;
            while (sum < rangeLength) {
                int readLength = (int) (rangeLength - sum);
                length = is.read(buffer, 0, (rangeLength - sum) <= buffer.length ? readLength : buffer.length);
                sum += length;
                os.write(buffer,0, length);
            }
            System.out.println("下载完成");
        }finally {
            if (is != null){
                is.close();
            }
            if (os != null){
                os.close();
            }
        }
    }
}

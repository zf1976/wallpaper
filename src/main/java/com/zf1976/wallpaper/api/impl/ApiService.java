package com.zf1976.wallpaper.api.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Console;
import cn.hutool.core.util.ReUtil;
import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.zf1976.wallpaper.enums.TypeEnum;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;

/**
 * @author mac
 * Create by Ant on 2020/8/15 下午9:37
 */
public class ApiService {

    private static String cookie;
    private static final Set<String> TYPES;
    private static final Map<String, String> TYPE_MAPS;
    private static final Connection CONN;
    public static final Properties PROPERTIES;
    public static final String BASE_URL;
    public static final String BASE_INFO_URL;
    public static final String ACCEPT;
    public static final String ACCEPT_ENCODING;
    public static final String ACCEPT_LANGUAGE;
    public static final String CACHE_CONTROL;
    public static final String CONNECTION;
    public static final String DNT;
    public static final String UPGRADE_INSECURE_REQUESTS;
    public static final String HOST;
    public static final Boolean STORE;

    static {
        final InputStream is = ApiService.class.getClassLoader().getResourceAsStream("config.properties");
        HttpRequest.closeCookie();

        final Properties properties = new Properties();
        try {
            properties.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String baseUrl = properties.getProperty("url.base");
        cookie = properties.getProperty("Cookie");
        TYPES = new HashSet<>(12);
        TYPE_MAPS = new HashMap<>(12);
        PROPERTIES = properties;
        BASE_URL = baseUrl;
        BASE_INFO_URL = properties.getProperty("url.base.info");
        ACCEPT = properties.getProperty("header.Accept");
        ACCEPT_ENCODING = properties.getProperty("header.Accept-Encoding");
        ACCEPT_LANGUAGE = properties.getProperty("header.Accept-Language");
        CACHE_CONTROL = properties.getProperty("header.Cache-Control");
        CONNECTION = properties.getProperty("header.Connection");
        DNT = properties.getProperty("header.DNT");
        UPGRADE_INSECURE_REQUESTS = properties.getProperty("Upgrade-Insecure-Requests");
        HOST = properties.getProperty("header.host");
        CONN = Jsoup.connect(baseUrl);
        STORE = Boolean.valueOf(properties.getProperty("store"));
        Document document = null;
        try {
            document = CONN.get();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 壁纸分类连接
        final Elements links = Objects.requireNonNull(document).select("a[href]");
        // 壁纸类型
        for (TypeEnum value : TypeEnum.values()) {
            TYPES.add(value.description);
        }
        // 壁纸分类页面
        for (Element link : links) {
            final String url = link.attr("abs:href");
            final String text = trim(link.text());
            if (TYPES.contains(text)){
                TYPE_MAPS.put(text,url);
            }
        }

    }


    public static long saveWallpaper(String wallpaperId, String wallpaperType) throws IOException {
        final String downloadUri = getDownloadUri(wallpaperId);
        if (downloadUri == null || Objects.equals(downloadUri,"")){
            return -1L;
        }
        HttpResponse response = null;
        response = HttpRequest.get(BASE_URL + downloadUri)
                              .cookie(cookie)
                              .header(Header.ACCEPT, ACCEPT)
                              .header(Header.ACCEPT_ENCODING, ACCEPT_ENCODING)
                              .header(Header.ACCEPT_LANGUAGE, ACCEPT_LANGUAGE)
                              .header(Header.CACHE_CONTROL, CACHE_CONTROL)
                              .header(Header.CONNECTION, CONNECTION)
                              .header(Header.HOST, HOST)
                              .timeout(2000)
                              .execute(false);
        final String doestPath = System.getProperty("user.home");
        final String wallpaperDir = PROPERTIES.getProperty("wallpaper.file.name");
        final String fileName = getFileName(response);

        //文件大小
        int size = response.bodyBytes().length;
        Console.log("Start downloading the wallpaper: {} Size: {}", fileName, getByteConversion(size));
        File wallpaperFile = FileUtil.file(doestPath, wallpaperDir, wallpaperType, fileName);
        long startTime;
        try (InputStream bodyStream = response.bodyStream();
             OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(wallpaperFile))
        ){
            byte[] data = new byte[4 * 1024];
            int len;
            startTime= System.currentTimeMillis();
            while ((len = bodyStream.read(data)) != -1) {
                outputStream.write(data, 0, len);
            }
            Db.use().insert(Entity.create("index_entity")
                                  .set("data_id",wallpaperId)
                                  .set("name",fileName)
                                  .set("type",wallpaperType));
        } catch (FileNotFoundException | SQLException e) {
            e.printStackTrace();
            return -1L;
        }
        try {
            printTime(startTime);
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return 1L;
    }

    private static String getFileName(HttpResponse response) throws UnsupportedEncodingException {
        assert response != null;
        String fileName = response.header(Header.CONTENT_DISPOSITION);
        fileName = ReUtil.get("filename=\"(.*?)\"", fileName, 1);
        return new String(fileName.getBytes(StandardCharsets.ISO_8859_1), "GB2312");
    }

    private static String getDownloadUri(String wallpaperId) {
        final HashMap<String, Object> form = new HashMap<>(2);
        form.put("t", Math.random());
        form.put("id", wallpaperId);
        final HttpResponse response = HttpRequest.get(BASE_INFO_URL)
                                                 .cookie(cookie)
                                                 .form(form)
                                                 .timeout(20000)
                                                 .execute(false);
        final String body = response.body();
        final String uri = (String) JSONUtil.parse(body).getByPath("pic");
        Console.log("下载链接:{}",BASE_URL + uri);
        return uri;
    }

    /**
     * 打印总耗时和平均每秒速度
     * @param startTime 开始时间
     */
    private static void printTime(long startTime) {
        //获取总时间
        long time = System.currentTimeMillis() - startTime;
        //时间转换倍率
        int conversion = 1;
        //打印时间单位
        String timeConversion = "";
        //获取时间单位和转换倍率
        {
            if (time / 1000 >= 60 && time / 1000 < 60 * 60) {
                //大于等于一分钟, 小于一小时
                conversion = 60;
                timeConversion = "分钟";
            } else if (time / 1000 >= 60 * 60) {
                //大于等于一小时
                conversion = 60 * 60;
                timeConversion = "小时";
            } else {
                timeConversion = "秒";
            }
        }
        StringBuilder stringBuilder = new StringBuilder();
        //打印时间
        {
            stringBuilder.append("下载完成，总耗时: ");
            //总毫秒 转换成秒在 除 转换倍率 ---> 保留两位小数点
            stringBuilder.append(String.format("%.2f", (time + 0.0) / conversion / 1000));
            stringBuilder.append(timeConversion);
        }
        System.out.println(stringBuilder.toString());
    }

    private static StringBuilder getByteConversion(double num) {
        StringBuilder stringBuilder = new StringBuilder();
        if (num < 1024) {
            stringBuilder.append(String.format("%.2f", num));
            stringBuilder.append("B");
        } else if (num < 1024 * 1024) {
            stringBuilder.append(String.format("%.2f", num / 1024));
            stringBuilder.append("KB");
        } else {
            stringBuilder.append(String.format("%.2f", num / 1024 / 1024));
            stringBuilder.append("MB");
        }
        return stringBuilder;
    }

    public static void setCookie(String cookie) {
        ApiService.cookie = cookie;
    }

    public static Properties getProperties(){
        return PROPERTIES;
    }

    public static String getCookie(){
        return cookie;
    }

    public static Map<String,String> getTypeMaps(){
        return TYPE_MAPS;
    }

    public static Connection getConnection(){
        return CONN;
    }

    private static String trim(String str){
        int width = 35;
        if (str.length() > width){
            return str.substring(0, width - 1) + ".";
        }else {
            return str;
        }
    }

}

package com.telegram.server.service.gridfs;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

/**
 * GridFS工具类
 * 提供GridFS相关的公共工具方法
 * 
 * @author liubo
 * @date 2025-01-27
 */
public class GridFSUtils {

    /**
     * 检查数据是否为有效的GZIP格式
     * 
     * @param data 待检查的数据
     * @return 是否为有效的GZIP格式
     */
    public static boolean isValidGzipData(byte[] data) {
        if (data == null || data.length < 2) {
            return false;
        }
        
        // 检查GZIP魔数
        if ((data[0] & 0xFF) != 0x1F || (data[1] & 0xFF) != 0x8B) {
            return false;
        }
        
        // 尝试解压缩验证
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             GZIPInputStream gzis = new GZIPInputStream(bais)) {
            
            // 读取一些字节来验证格式
            byte[] buffer = new byte[1024];
            gzis.read(buffer);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 检查数据是否可能是JSON格式
     * 
     * @param data 待检查的数据
     * @return 是否可能是JSON格式
     */
    public static boolean isLikelyJsonData(byte[] data) {
        if (data == null || data.length < 2) {
            return false;
        }
        
        // 检查是否以JSON对象或数组开始和结束
        char firstChar = (char) data[0];
        char lastChar = (char) data[data.length - 1];
        
        return (firstChar == '{' && lastChar == '}') || (firstChar == '[' && lastChar == ']');
    }
}
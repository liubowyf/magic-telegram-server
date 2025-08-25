package com.telegram.server.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 路径验证和处理工具类
 * 提供路径验证、目录创建、权限检查和路径规范化功能
 * 
 * @author liubo
 * @date 2025-08-19
 */
@Component
public class PathValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(PathValidator.class);
    
    // 路径验证缓存
    private final Map<String, ValidationResult> validationCache = new ConcurrentHashMap<>();
    
    // 危险路径模式
    private static final List<Pattern> DANGEROUS_PATTERNS = Arrays.asList(
        Pattern.compile(".*\\.\\..*"), // 包含 ..
        Pattern.compile(".*/etc/.*"), // 系统配置目录
        Pattern.compile(".*/bin/.*"), // 系统二进制目录
        Pattern.compile(".*/sbin/.*"), // 系统管理二进制目录
        Pattern.compile(".*/usr/bin/.*"), // 用户二进制目录
        Pattern.compile(".*/usr/sbin/.*"), // 用户管理二进制目录
        Pattern.compile(".*/System/.*"), // macOS系统目录
        Pattern.compile(".*/Windows/.*"), // Windows系统目录
        Pattern.compile(".*/Program Files.*") // Windows程序目录
    );
    
    // 允许的根目录模式
    private static final List<Pattern> ALLOWED_ROOT_PATTERNS = Arrays.asList(
        Pattern.compile("/tmp/.*"), // 临时目录
        Pattern.compile("/var/tmp/.*"), // 变量临时目录
        Pattern.compile("/var/folders/.*/.*"), // macOS系统临时目录
        Pattern.compile("/Users/.*/.*"), // macOS用户目录
        Pattern.compile("/home/.*/.*"), // Linux用户目录
        Pattern.compile("C:\\\\Users\\\\.*"), // Windows用户目录
        Pattern.compile("[A-Za-z]:\\\\temp\\\\.*"), // Windows临时目录
        Pattern.compile("\\./.*"), // 相对路径（当前目录）
        Pattern.compile("[^/\\\\].*") // 相对路径（不以/或\开头）
    );
    
    /**
     * 路径验证结果类
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String normalizedPath;
        private final String errorMessage;
        private final Set<String> warnings;
        private final PathInfo pathInfo;
        
        private ValidationResult(boolean valid, String normalizedPath, String errorMessage, 
                               Set<String> warnings, PathInfo pathInfo) {
            this.valid = valid;
            this.normalizedPath = normalizedPath;
            this.errorMessage = errorMessage;
            this.warnings = warnings != null ? new HashSet<>(warnings) : new HashSet<>();
            this.pathInfo = pathInfo;
        }
        
        public static ValidationResult success(String normalizedPath, PathInfo pathInfo) {
            return new ValidationResult(true, normalizedPath, null, null, pathInfo);
        }
        
        public static ValidationResult success(String normalizedPath, PathInfo pathInfo, Set<String> warnings) {
            return new ValidationResult(true, normalizedPath, null, warnings, pathInfo);
        }
        
        public static ValidationResult failure(String errorMessage) {
            return new ValidationResult(false, null, errorMessage, null, null);
        }
        
        // Getters
        public boolean isValid() { return valid; }
        public String getNormalizedPath() { return normalizedPath; }
        public String getErrorMessage() { return errorMessage; }
        public Set<String> getWarnings() { return warnings; }
        public PathInfo getPathInfo() { return pathInfo; }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
    }
    
    /**
     * 路径信息类
     */
    public static class PathInfo {
        private final boolean exists;
        private final boolean isDirectory;
        private final boolean isFile;
        private final boolean readable;
        private final boolean writable;
        private final boolean executable;
        private final long size;
        private final String parent;
        private final String absolutePath;
        
        public PathInfo(Path path) {
            this.exists = Files.exists(path);
            this.isDirectory = Files.isDirectory(path);
            this.isFile = Files.isRegularFile(path);
            this.readable = Files.isReadable(path);
            this.writable = Files.isWritable(path);
            this.executable = Files.isExecutable(path);
            this.absolutePath = path.toAbsolutePath().toString();
            
            long tempSize = 0;
            try {
                if (exists && isFile) {
                    tempSize = Files.size(path);
                }
            } catch (IOException e) {
                logger.debug("Failed to get file size for: {}", path, e);
            }
            this.size = tempSize;
            
            Path parentPath = path.getParent();
            this.parent = parentPath != null ? parentPath.toString() : null;
        }
        
        // Getters
        public boolean exists() { return exists; }
        public boolean isDirectory() { return isDirectory; }
        public boolean isFile() { return isFile; }
        public boolean isReadable() { return readable; }
        public boolean isWritable() { return writable; }
        public boolean isExecutable() { return executable; }
        public long getSize() { return size; }
        public String getParent() { return parent; }
        public String getAbsolutePath() { return absolutePath; }
    }
    
    /**
     * 验证路径的有效性和安全性
     * 
     * @param pathString 要验证的路径字符串
     * @return 验证结果
     */
    public ValidationResult validatePath(String pathString) {
        return validatePath(pathString, false);
    }
    
    /**
     * 验证路径的有效性和安全性
     * 
     * @param pathString 要验证的路径字符串
     * @param useCache 是否使用缓存
     * @return 验证结果
     */
    public ValidationResult validatePath(String pathString, boolean useCache) {
        if (pathString == null || pathString.trim().isEmpty()) {
            return ValidationResult.failure("Path cannot be null or empty");
        }
        
        String trimmedPath = pathString.trim();
        
        // 检查缓存
        if (useCache && validationCache.containsKey(trimmedPath)) {
            ValidationResult cached = validationCache.get(trimmedPath);
            logger.debug("Using cached validation result for path: {}", trimmedPath);
            return cached;
        }
        
        try {
            // 基本安全检查
            ValidationResult securityCheck = performSecurityCheck(trimmedPath);
            if (!securityCheck.isValid()) {
                if (useCache) {
                    validationCache.put(trimmedPath, securityCheck);
                }
                return securityCheck;
            }
            
            // 路径规范化
            Path path = Paths.get(trimmedPath).toAbsolutePath().normalize();
            String normalizedPath = path.toString();
            
            // 再次进行安全检查（规范化后）
            ValidationResult normalizedSecurityCheck = performSecurityCheck(normalizedPath);
            if (!normalizedSecurityCheck.isValid()) {
                if (useCache) {
                    validationCache.put(trimmedPath, normalizedSecurityCheck);
                }
                return normalizedSecurityCheck;
            }
            
            // 收集路径信息
            PathInfo pathInfo = new PathInfo(path);
            
            // 收集警告
            Set<String> warnings = new HashSet<>();
            
            // 检查路径是否存在
            if (!pathInfo.exists()) {
                warnings.add("Path does not exist: " + normalizedPath);
            }
            
            // 检查权限
            if (pathInfo.exists()) {
                if (!pathInfo.isReadable()) {
                    warnings.add("Path is not readable: " + normalizedPath);
                }
                if (!pathInfo.isWritable()) {
                    warnings.add("Path is not writable: " + normalizedPath);
                }
            }
            
            // 检查父目录
            if (pathInfo.getParent() != null) {
                Path parentPath = Paths.get(pathInfo.getParent());
                if (!Files.exists(parentPath)) {
                    warnings.add("Parent directory does not exist: " + pathInfo.getParent());
                } else if (!Files.isWritable(parentPath)) {
                    warnings.add("Parent directory is not writable: " + pathInfo.getParent());
                }
            }
            
            ValidationResult result = warnings.isEmpty() ? 
                ValidationResult.success(normalizedPath, pathInfo) :
                ValidationResult.success(normalizedPath, pathInfo, warnings);
            
            // 缓存结果
            if (useCache) {
                validationCache.put(trimmedPath, result);
            }
            
            logger.debug("Path validation successful: {} -> {}", trimmedPath, normalizedPath);
            return result;
            
        } catch (InvalidPathException e) {
            ValidationResult result = ValidationResult.failure("Invalid path format: " + e.getMessage());
            if (useCache) {
                validationCache.put(trimmedPath, result);
            }
            return result;
        } catch (Exception e) {
            logger.error("Unexpected error during path validation: {}", trimmedPath, e);
            ValidationResult result = ValidationResult.failure("Path validation failed: " + e.getMessage());
            if (useCache) {
                validationCache.put(trimmedPath, result);
            }
            return result;
        }
    }
    
    /**
     * 执行安全检查
     * 
     * @param pathString 路径字符串
     * @return 验证结果
     */
    private ValidationResult performSecurityCheck(String pathString) {
        // 检查危险路径模式
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(pathString).matches()) {
                return ValidationResult.failure("Dangerous path detected: " + pathString);
            }
        }
        
        // 检查是否在允许的根目录下
        boolean isAllowed = false;
        for (Pattern pattern : ALLOWED_ROOT_PATTERNS) {
            if (pattern.matcher(pathString).matches()) {
                isAllowed = true;
                break;
            }
        }
        
        if (!isAllowed) {
            return ValidationResult.failure("Path not in allowed root directories: " + pathString);
        }
        
        return ValidationResult.success(pathString, null);
    }
    
    /**
     * 确保目录存在，如果不存在则创建
     * 
     * @param pathString 目录路径
     * @return 创建结果
     */
    public ValidationResult ensureDirectoryExists(String pathString) {
        ValidationResult validation = validatePath(pathString);
        if (!validation.isValid()) {
            return validation;
        }
        
        try {
            Path path = Paths.get(validation.getNormalizedPath());
            
            if (Files.exists(path)) {
                if (!Files.isDirectory(path)) {
                    return ValidationResult.failure("Path exists but is not a directory: " + path);
                }
                logger.debug("Directory already exists: {}", path);
                return validation;
            }
            
            // 创建目录（包括父目录）
            Files.createDirectories(path);
            logger.info("Created directory: {}", path);
            
            // 重新获取路径信息
            PathInfo newPathInfo = new PathInfo(path);
            return ValidationResult.success(validation.getNormalizedPath(), newPathInfo);
            
        } catch (IOException e) {
            logger.error("Failed to create directory: {}", validation.getNormalizedPath(), e);
            return ValidationResult.failure("Failed to create directory: " + e.getMessage());
        }
    }
    
    /**
     * 验证文件路径并确保父目录存在
     * 
     * @param pathString 文件路径
     * @return 验证结果
     */
    public ValidationResult validateFilePathAndEnsureParent(String pathString) {
        ValidationResult validation = validatePath(pathString);
        if (!validation.isValid()) {
            return validation;
        }
        
        try {
            Path path = Paths.get(validation.getNormalizedPath());
            Path parentPath = path.getParent();
            
            if (parentPath != null && !Files.exists(parentPath)) {
                ValidationResult parentResult = ensureDirectoryExists(parentPath.toString());
                if (!parentResult.isValid()) {
                    return ValidationResult.failure("Failed to create parent directory: " + parentResult.getErrorMessage());
                }
            }
            
            // 重新获取路径信息
            PathInfo newPathInfo = new PathInfo(path);
            return ValidationResult.success(validation.getNormalizedPath(), newPathInfo);
            
        } catch (Exception e) {
            logger.error("Failed to validate file path and ensure parent: {}", validation.getNormalizedPath(), e);
            return ValidationResult.failure("File path validation failed: " + e.getMessage());
        }
    }
    
    /**
     * 清理路径（删除文件或目录）
     * 
     * @param pathString 要清理的路径
     * @param recursive 是否递归删除目录
     * @return 是否成功
     */
    public boolean cleanPath(String pathString, boolean recursive) {
        ValidationResult validation = validatePath(pathString);
        if (!validation.isValid()) {
            logger.error("Cannot clean invalid path: {}", pathString);
            return false;
        }
        
        try {
            Path path = Paths.get(validation.getNormalizedPath());
            
            if (!Files.exists(path)) {
                logger.debug("Path does not exist, nothing to clean: {}", path);
                return true;
            }
            
            if (Files.isDirectory(path)) {
                if (recursive) {
                    // 递归删除目录
                    Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }
                        
                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                    logger.info("Recursively deleted directory: {}", path);
                } else {
                    // 只删除空目录
                    Files.delete(path);
                    logger.info("Deleted empty directory: {}", path);
                }
            } else {
                // 删除文件
                Files.delete(path);
                logger.info("Deleted file: {}", path);
            }
            
            return true;
            
        } catch (IOException e) {
            logger.error("Failed to clean path: {}", validation.getNormalizedPath(), e);
            return false;
        }
    }
    
    /**
     * 获取路径的详细信息
     * 
     * @param pathString 路径字符串
     * @return 路径信息，如果路径无效则返回null
     */
    public PathInfo getPathInfo(String pathString) {
        ValidationResult validation = validatePath(pathString);
        if (!validation.isValid()) {
            return null;
        }
        
        return validation.getPathInfo();
    }
    
    /**
     * 检查路径是否可用于写入
     * 
     * @param pathString 路径字符串
     * @return 是否可写
     */
    public boolean isWritable(String pathString) {
        ValidationResult validation = validatePath(pathString);
        if (!validation.isValid()) {
            return false;
        }
        
        PathInfo pathInfo = validation.getPathInfo();
        if (pathInfo.exists()) {
            return pathInfo.isWritable();
        } else {
            // 检查父目录是否可写
            String parent = pathInfo.getParent();
            if (parent != null) {
                try {
                    return Files.isWritable(Paths.get(parent));
                } catch (Exception e) {
                    logger.debug("Failed to check parent directory writability: {}", parent, e);
                    return false;
                }
            }
            return false;
        }
    }
    
    /**
     * 检查路径是否可用于读取
     * 
     * @param pathString 路径字符串
     * @return 是否可读
     */
    public boolean isReadable(String pathString) {
        ValidationResult validation = validatePath(pathString);
        if (!validation.isValid()) {
            return false;
        }
        
        PathInfo pathInfo = validation.getPathInfo();
        return pathInfo.exists() && pathInfo.isReadable();
    }
    
    /**
     * 规范化路径
     * 
     * @param pathString 原始路径
     * @return 规范化后的路径，如果路径无效则返回null
     */
    public String normalizePath(String pathString) {
        ValidationResult validation = validatePath(pathString);
        return validation.isValid() ? validation.getNormalizedPath() : null;
    }
    
    /**
     * 清理验证缓存
     */
    public void clearValidationCache() {
        int size = validationCache.size();
        validationCache.clear();
        logger.info("Cleared path validation cache, removed {} entries", size);
    }
    

    

    
    /**
     * 获取系统临时目录
     * 
     * @return 系统临时目录路径
     */
    public String getSystemTempDirectory() {
        return System.getProperty("java.io.tmpdir");
    }
    

}
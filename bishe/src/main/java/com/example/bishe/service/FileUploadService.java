package com.example.bishe.service;

import com.example.bishe.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 本地文件上传服务。返回可直接访问的 URL 路径(/uploads/...)。
 */
@Service
public class FileUploadService {

    private static final Set<String> ALLOWED_IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");
    private static final Set<String> ALLOWED_FILE_EXT = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "zip", "rar"
    );

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.max-size-mb:10}")
    private long maxSizeMb;

    /**
     * 上传图片(头像/群头像/聊天图片)。
     */
    public String uploadImage(MultipartFile file) {
        return store(file, ALLOWED_IMAGE_EXT, "image");
    }

    /**
     * 上传通用文件(聊天附件等)。
     */
    public String uploadFile(MultipartFile file) {
        return store(file, ALLOWED_FILE_EXT, "file");
    }

    private String store(MultipartFile file, Set<String> allowedExt, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        long maxBytes = maxSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessException("文件大小不能超过 " + maxSizeMb + "MB");
        }

        String original = file.getOriginalFilename();
        String ext = extractExt(original);
        if (!allowedExt.contains(ext)) {
            throw new BusinessException("不支持的文件类型: " + (ext.isEmpty() ? "未知" : ext));
        }
        validateFileSignature(file, ext);

        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = resolveUploadRoot().resolve(subDir);
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("文件保存失败，请稍后重试");
        }
        return "/uploads/" + subDir + "/" + filename;
    }

    public Path resolveUploadRoot() {
        Path path = Paths.get(uploadDir);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(uploadDir);
        }
        return path.toAbsolutePath().normalize();
    }

    private String extractExt(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    /** 扩展名之外再校验文件头，拦截伪装成图片/文档的可执行文件。 */
    private void validateFileSignature(MultipartFile file, String ext) {
        byte[] head = new byte[16];
        int read;
        try (InputStream input = file.getInputStream()) {
            read = input.read(head);
        } catch (IOException e) {
            throw new BusinessException("无法读取上传文件");
        }
        if (read <= 0) throw new BusinessException("上传文件为空");

        boolean valid = switch (ext) {
            case "jpg", "jpeg" -> startsWith(head, read, 0xFF, 0xD8, 0xFF);
            case "png" -> startsWith(head, read, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "gif" -> ascii(head, read, 0, "GIF87a") || ascii(head, read, 0, "GIF89a");
            case "bmp" -> ascii(head, read, 0, "BM");
            case "webp" -> ascii(head, read, 0, "RIFF") && ascii(head, read, 8, "WEBP");
            case "pdf" -> ascii(head, read, 0, "%PDF-");
            case "doc", "xls", "ppt" -> startsWith(head, read, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
            case "docx", "xlsx", "pptx", "zip" -> startsWith(head, read, 0x50, 0x4B, 0x03, 0x04)
                    || startsWith(head, read, 0x50, 0x4B, 0x05, 0x06);
            case "rar" -> startsWith(head, read, 0x52, 0x61, 0x72, 0x21, 0x1A, 0x07);
            case "txt" -> !containsNull(head, read);
            default -> false;
        };
        if (!valid) throw new BusinessException("文件内容与扩展名不匹配");
    }

    private boolean startsWith(byte[] data, int read, int... expected) {
        if (read < expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((data[i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }

    private boolean ascii(byte[] data, int read, int offset, String expected) {
        byte[] bytes = expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        if (read < offset + bytes.length) return false;
        for (int i = 0; i < bytes.length; i++) {
            if (data[offset + i] != bytes[i]) return false;
        }
        return true;
    }

    private boolean containsNull(byte[] data, int read) {
        for (int i = 0; i < read; i++) if (data[i] == 0) return true;
        return false;
    }
}

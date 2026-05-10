package com.recommend.shop.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class UploadService {

    public String saveUpload(MultipartFile file, String uploadFolder) {
        String filename = secureFilename(file == null ? null : file.getOriginalFilename());
        if (filename == null || filename.isBlank()) {
            return null;
        }
        String ext = "";
        int dot = filename.lastIndexOf('.');
        if (dot >= 0) {
            ext = filename.substring(dot).toLowerCase();
        }
        String uniqueName = UUID.randomUUID().toString().replace("-", "") + ext;
        try {
            Path folder = resolvePath(uploadFolder);
            Files.createDirectories(folder);
            Path fullPath = folder.resolve(uniqueName);
            file.transferTo(fullPath);
            return "/static/uploads/" + uniqueName;
        } catch (Exception ex) {
            return null;
        }
    }

    private Path resolvePath(String path) {
        Path raw = Paths.get(path);
        if (raw.isAbsolute()) {
            return raw.normalize();
        }
        return Paths.get(System.getProperty("user.dir")).resolve(raw).normalize();
    }

    private String secureFilename(String filename) {
        if (filename == null) {
            return null;
        }
        String value = filename.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        value = value.trim().replace(" ", "_");
        value = value.replaceAll("[^A-Za-z0-9_.-]", "_");
        value = value.replaceAll("_+", "_");
        if (value.startsWith(".")) {
            value = value.substring(1);
        }
        return value;
    }
}

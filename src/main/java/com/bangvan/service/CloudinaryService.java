package com.bangvan.service;

import com.bangvan.exception.AppException;
import com.bangvan.exception.ErrorCode;
import com.bangvan.service.FileStorageService;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService implements FileStorageService {

    private final Cloudinary cloudinary;

    @Override
    public String uploadFile(MultipartFile file) {
        try {
            if (file.isEmpty()) {
                throw new AppException(ErrorCode.INVALID_INPUT, "Failed to store empty file.");
            }

            if (!isImage(file)) {
                throw new AppException(ErrorCode.INVALID_INPUT, "Only image files are allowed.");
            }

            String publicId = UUID.randomUUID().toString();
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(),
                    ObjectUtils.asMap(
                            "public_id", publicId,
                            "resource_type", "auto"
                    ));

            String url = (String) uploadResult.get("secure_url");
            log.info("File uploaded successfully to Cloudinary. URL: {}", url);
            return url;

        } catch (IOException e) {
            log.error("Failed to upload file to Cloudinary", e);
            throw new RuntimeException("Failed to upload file", e);
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        try {

            String publicId = extractPublicIdFromUrl(fileUrl);
            if (publicId != null) {
                cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
                log.info("File deleted from Cloudinary. Public ID: {}", publicId);
            }
        } catch (IOException e) {
            log.error("Failed to delete file from Cloudinary", e);

        }
    }

    private boolean isImage(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.startsWith("image/");
    }

    private String extractPublicIdFromUrl(String url) {


        try {
            int lastSlashIndex = url.lastIndexOf('/');
            int lastDotIndex = url.lastIndexOf('.');
            return url.substring(lastSlashIndex + 1, lastDotIndex);
        } catch (Exception e) {
            log.warn("Could not extract publicId from URL: {}", url);
            return null;
        }
    }
}
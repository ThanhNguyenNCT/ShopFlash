package com.shopflash.modules.backup.service;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.backup.dto.response.BackupPageResponse;
import com.shopflash.modules.backup.dto.response.BackupRecordResponse;
import com.shopflash.modules.backup.dto.response.BackupSystemStatusResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Tạo manifest file-based cho backup cục bộ; không lưu đường dẫn hay command do client gửi lên. */
@Service
public class BackupService {

    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss").withZone(ZoneOffset.UTC);
    private final BackupStatusService statusService;
    private final BackupRunner runner;
    private final ObjectMapper json;
    private final ReentrantLock createLock = new ReentrantLock();

    public BackupService(BackupStatusService statusService, BackupRunner runner, ObjectMapper json) {
        this.statusService = statusService;
        this.runner = runner;
        this.json = json;
    }

    public BackupRecordResponse create() {
        if (!createLock.tryLock()) throw ApiException.conflict("BACKUP_IN_PROGRESS", "Đang có một bản sao lưu khác chạy");
        try {
            BackupSystemStatusResponse baseline = statusService.status();
            if (!"UP".equals(baseline.oracle().status()) || !"UP".equals(baseline.redis().status())) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BACKUP_SOURCE_UNAVAILABLE",
                        "Oracle và Redis phải sẵn sàng trước khi tạo bản sao lưu");
            }
            if (!baseline.runner().configured()) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BACKUP_RUNNER_UNAVAILABLE", baseline.runner().message());
            }

            OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
            String backupId = "BKP-" + ID_TIME.format(createdAt) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            Path directory = runner.artifactRoot().resolve(backupId);
            try {
                var artifacts = runner.create(backupId, directory);
                BackupRecordResponse completed = new BackupRecordResponse(backupId, "COMPLETED", createdAt,
                        OffsetDateTime.now(ZoneOffset.UTC), artifacts.oracle(), artifacts.redis(), baseline, null);
                writeManifest(directory, completed);
                return completed;
            } catch (BackupRunner.BackupExecutionException error) {
                BackupRecordResponse failed = new BackupRecordResponse(backupId, "FAILED", createdAt,
                        OffsetDateTime.now(ZoneOffset.UTC), null, null, baseline, "BACKUP_FAILED");
                writeManifestQuietly(directory, failed);
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BACKUP_FAILED",
                        "Không thể tạo bản sao lưu. Hãy kiểm tra Docker Compose và thử lại.");
            }
        } finally {
            createLock.unlock();
        }
    }

    public BackupPageResponse history(int page, int size) {
        List<BackupRecordResponse> all = readManifests();
        int offset = (page - 1) * size;
        if (offset >= all.size()) return new BackupPageResponse(List.of(), page, size, all.size());
        return new BackupPageResponse(all.subList(offset, Math.min(offset + size, all.size())), page, size, all.size());
    }

    private List<BackupRecordResponse> readManifests() {
        Path root = runner.artifactRoot();
        if (!Files.isDirectory(root)) return List.of();
        List<BackupRecordResponse> records = new ArrayList<>();
        try (var directories = Files.list(root)) {
            for (Path directory : directories.filter(Files::isDirectory).toList()) {
                Path manifest = directory.resolve("manifest.json");
                if (!Files.isRegularFile(manifest)) continue;
                try {
                    records.add(json.readValue(manifest.toFile(), BackupRecordResponse.class));
                } catch (RuntimeException ignored) {
                    // Artifact dở dang không được đưa vào lịch sử; API không lộ nội dung file cục bộ.
                }
            }
        } catch (IOException error) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "BACKUP_HISTORY_UNAVAILABLE", "Không đọc được lịch sử sao lưu");
        }
        records.sort(Comparator.comparing(BackupRecordResponse::createdAt).reversed());
        return records;
    }

    private void writeManifest(Path directory, BackupRecordResponse record) {
        try {
            Files.createDirectories(directory);
            json.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("manifest.json").toFile(), record);
        } catch (IOException error) {
            throw new BackupRunner.BackupExecutionException("Không ghi được tệp thông tin bản sao lưu", error);
        }
    }

    private void writeManifestQuietly(Path directory, BackupRecordResponse record) {
        try {
            writeManifest(directory, record);
        } catch (RuntimeException ignored) {
            // Giữ lỗi backup chính, không che bằng lỗi ghi manifest phụ.
        }
    }
}

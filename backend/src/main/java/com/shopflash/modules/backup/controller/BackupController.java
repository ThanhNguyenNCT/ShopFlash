package com.shopflash.modules.backup.controller;

import com.shopflash.modules.backup.dto.response.BackupSystemStatusResponse;
import com.shopflash.modules.backup.dto.response.BackupPageResponse;
import com.shopflash.modules.backup.dto.response.BackupRecordResponse;
import com.shopflash.modules.backup.service.BackupService;
import com.shopflash.modules.backup.service.BackupStatusService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** F11: API trạng thái cho giao diện Backup & Recovery. */
@RestController
@RequestMapping("/api/admin/backups")
public class BackupController {

    private final BackupStatusService service;
    private final BackupService backups;

    public BackupController(BackupStatusService service, BackupService backups) {
        this.service = service;
        this.backups = backups;
    }

    @GetMapping("/status")
    public BackupSystemStatusResponse status() {
        return service.status();
    }

    @GetMapping
    public BackupPageResponse history(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") @Min(1) int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return backups.history(page, size);
    }

    @PostMapping
    public BackupRecordResponse create() {
        return backups.create();
    }
}

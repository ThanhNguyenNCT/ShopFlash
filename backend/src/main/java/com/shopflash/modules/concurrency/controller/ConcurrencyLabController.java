package com.shopflash.modules.concurrency.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopflash.modules.concurrency.dto.request.LabResetRequest;
import com.shopflash.modules.concurrency.dto.request.LabRunRequest;
import com.shopflash.modules.concurrency.dto.response.LabRunResponse;
import com.shopflash.modules.concurrency.dto.response.LabStateResponse;
import com.shopflash.modules.concurrency.service.ConcurrencyLabService;

import jakarta.validation.Valid;

/**
 * Concurrency Lab (admin; /api/admin/** đã yêu cầu ADMIN trong SecurityConfig). Contract: AGENTS.md của module,
 * mục "Admin: Concurrency Lab".
 */
@RestController
@RequestMapping("/api/admin/concurrency-lab")
public class ConcurrencyLabController {

    private final ConcurrencyLabService lab;

    public ConcurrencyLabController(ConcurrencyLabService lab) {
        this.lab = lab;
    }

    /** Chạy đồng bộ, có thể mất vài chục giây với tham số lớn. */
    @PostMapping("/runs")
    public LabRunResponse run(@Valid @RequestBody(required = false) LabRunRequest request) {
        return lab.run(request);
    }

    @PostMapping("/reset")
    public LabStateResponse reset(@Valid @RequestBody LabResetRequest request) {
        return lab.reset(request.quota());
    }

    @GetMapping("/state")
    public LabStateResponse state() {
        return lab.state();
    }
}

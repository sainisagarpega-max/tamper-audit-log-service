package com.sainisagar.auditlog.controller;

import com.sainisagar.auditlog.dto.LocalTokenRequest;
import com.sainisagar.auditlog.dto.LocalTokenResponse;
import com.sainisagar.auditlog.service.LocalTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequestMapping("/api/v1/dev/token")
public class LocalTokenController {

    private final LocalTokenService service;

    public LocalTokenController(LocalTokenService service) {
        this.service = service;
    }

    @PostMapping
    @SecurityRequirements
    @Operation(summary = "Generate a short-lived JWT for local development only")
    public LocalTokenResponse token(@Valid @RequestBody LocalTokenRequest request) {
        return service.issue(request);
    }
}

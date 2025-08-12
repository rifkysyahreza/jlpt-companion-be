package com.nca.jlpt_companion.content.controller;

import com.nca.jlpt_companion.content.dto.DeltaResponse;
import com.nca.jlpt_companion.content.dto.VersionResponse;
import com.nca.jlpt_companion.content.service.ContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Content", description = "Content versioning and scoped download")
@RestController
@RequestMapping("/api/v1/content")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;

    @Operation(summary = "Get latest content version for a domain/level")
    @GetMapping("/version")
    public ResponseEntity<VersionResponse> version(
            @RequestParam(defaultValue = "JLPT") String domain,
            @RequestParam(required = false) String level  // null = domain-wide
    ) {
        return ResponseEntity.ok(contentService.getLatestVersion(domain, level));
    }

    @Operation(summary = "Download content delta or snapshot for a domain/level")
    @GetMapping("/download")
    public ResponseEntity<DeltaResponse> download(
            @RequestParam(defaultValue = "JLPT") String domain,
            @RequestParam(required = false) String level,
            @RequestParam(name = "since", defaultValue = "0") int sinceVersion
    ) {
        return ResponseEntity.ok(contentService.getDelta(domain, level, sinceVersion));
    }
}

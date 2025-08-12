package com.nca.jlpt_companion.content.controller;

import com.nca.jlpt_companion.content.dto.DeltaResponse;
import com.nca.jlpt_companion.content.dto.VersionResponse;
import com.nca.jlpt_companion.content.service.ContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/content")
@RequiredArgsConstructor
public class ContentController {
    private final ContentService contentService;

    @GetMapping("/version")
    public ResponseEntity<VersionResponse> version() {
        return ResponseEntity.ok(contentService.getLatestVersion());
    }

    @GetMapping("/delta")
    public ResponseEntity<DeltaResponse> delta(@RequestParam(name = "since", defaultValue = "0") long since) {
        return ResponseEntity.ok(contentService.getDelta(since));
    }
}

package com.nca.jlpt_companion.content.dto;

public record VersionResponse(long latestVersion, String checksum, Long sizeHint) {}

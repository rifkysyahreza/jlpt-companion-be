package com.nca.jlpt_companion.content.dto;

import java.util.List;

public record DeltaResponse(
        long from,
        long to,
        String checksum,
        List<Object> decks,
        List<Object> cards,
        List<Object> passages,
        List<Object> i18n
) {}

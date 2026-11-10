package com.example.bishe.dto;

import java.util.List;

public record ErrorBookSelection(List<Ref> questions) {
    public record Ref(Long questionId, Integer questionType) {}

    public record Answer(Long questionId, Integer questionType, String answer) {}
}

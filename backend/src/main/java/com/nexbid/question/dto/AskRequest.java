package com.nexbid.question.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** EN: A question about the lot. / VI: Một câu hỏi về lô. */
public record AskRequest(@NotBlank @Size(min = 5, max = 500) String body) {
}

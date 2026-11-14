package com.nexbid.question.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** EN: The seller's answer, public once sent. / VI: Câu trả lời của người bán, công khai ngay khi gửi. */
public record AnswerRequest(@NotBlank @Size(max = 1000) String answer) {
}

package com.nexbid.question;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;
import com.nexbid.question.dto.AnswerRequest;
import com.nexbid.question.dto.AskRequest;

import jakarta.validation.Valid;

/**
 * EN: Reading is public, like the lot page; asking needs a signed-in user (SecurityConfig); answering is under
 *     /api/seller/**, so only sellers reach it, and only the lot's own seller gets past the service.
 * VI: Đọc là công khai, như trang lô; hỏi cần đăng nhập (SecurityConfig); trả lời nằm dưới /api/seller/**, nên chỉ người
 *     bán tới được, và chỉ người bán của chính lô đó qua được service.
 */
@RestController
public class QuestionController {

    private final QuestionService questions;

    public QuestionController(QuestionService questions) {
        this.questions = questions;
    }

    @GetMapping("/api/auctions/{auctionId}/questions")
    public ApiResponse<List<QuestionView>> forLot(@PathVariable UUID auctionId) {
        return ApiResponse.of(questions.forLot(auctionId));
    }

    @PostMapping("/api/auctions/{auctionId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QuestionView> ask(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID auctionId,
            @Valid @RequestBody AskRequest request) {
        return ApiResponse.of(questions.ask(me.id(), auctionId, request.body()), "Question posted");
    }

    @PostMapping("/api/seller/questions/{questionId}/answer")
    public ApiResponse<QuestionView> answer(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID questionId,
            @Valid @RequestBody AnswerRequest request) {
        return ApiResponse.of(questions.answer(me.id(), questionId, request.answer()), "Answer posted");
    }
}

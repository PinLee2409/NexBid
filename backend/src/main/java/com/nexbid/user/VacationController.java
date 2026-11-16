package com.nexbid.user;

import java.time.Instant;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * EN: A seller's vacation switch. {@code until} null ends the vacation.
 * VI: Công tắc kỳ nghỉ của người bán. {@code until} null là kết thúc kỳ nghỉ.
 */
@RestController
@RequestMapping("/api/seller/vacation")
public class VacationController {

    private final Vacations vacations;

    VacationController(Vacations vacations) {
        this.vacations = vacations;
    }

    public record VacationRequest(Instant until, @Size(max = 200) String note) {
    }

    @GetMapping
    public ApiResponse<Vacations.Vacation> get(@AuthenticationPrincipal CurrentUser seller) {
        return ApiResponse.of(vacations.of(seller.id(), Instant.now()));
    }

    @PutMapping
    public ApiResponse<Vacations.Vacation> set(@AuthenticationPrincipal CurrentUser seller,
            @Valid @RequestBody VacationRequest request) {
        Vacations.Vacation saved = vacations.set(seller.id(), request.until(), request.note(), Instant.now());
        return ApiResponse.of(saved, saved.until() == null ? "Welcome back" : "Vacation on");
    }
}

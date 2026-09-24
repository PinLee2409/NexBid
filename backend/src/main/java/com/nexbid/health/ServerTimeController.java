package com.nexbid.health;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;

/**
 * EN: The server's clock (guide §24, spec §11). A page reads it once, keeps the difference from its own
 *     clock, and every countdown on the site is then drawn against the server's time, not the machine's.
 * VI: Đồng hồ của server (guide §24, spec §11). Trang đọc một lần, giữ lại độ lệch so với đồng hồ máy
 *     mình, và từ đó mọi đồng hồ đếm ngược trên site vẽ theo giờ server chứ không theo giờ máy.
 *
 * <p>EN: It lives beside the health probe because it describes the process, not the auction domain — and
 *     a lot page already receives serverTime with its own data, so this is only for resyncing later.
 * <p>VI: Đặt cạnh probe sức khoẻ vì nó mô tả tiến trình chứ không phải nghiệp vụ đấu giá — và trang một lô
 *     vốn đã nhận serverTime kèm dữ liệu của nó, nên đây chỉ dùng để đồng bộ lại về sau.
 */
@RestController
@RequestMapping("/api")
public class ServerTimeController {

    @GetMapping("/server-time")
    public ApiResponse<ServerTime> now() {
        return ApiResponse.of(new ServerTime(Instant.now()));
    }

    public record ServerTime(Instant serverTime) {
    }
}

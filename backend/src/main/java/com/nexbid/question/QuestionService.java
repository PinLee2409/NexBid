package com.nexbid.question;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.auction.AuctionService;
import com.nexbid.auction.AuctionStatus;
import com.nexbid.auction.AuctionSummaryView;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.user.UserService;

/**
 * EN: Public questions on a lot. Anyone signed in but the seller may ask while the lot is upcoming or live; only the
 *     seller answers, once. A person can have at most {@link #WAITING_LIMIT} questions waiting on one lot, so the
 *     seller's inbox cannot be flooded.
 * VI: Câu hỏi công khai trên một lô. Ai đã đăng nhập, trừ người bán, đều hỏi được khi lô sắp mở hoặc đang mở; chỉ người
 *     bán trả lời, một lần. Mỗi người có tối đa {@link #WAITING_LIMIT} câu đang chờ trên một lô, để hộp thư của người bán
 *     không bị làm ngập.
 */
@Service
public class QuestionService {

    static final int WAITING_LIMIT = 3;
    private static final Set<AuctionStatus> OPEN = Set.of(AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE);

    private final JdbcTemplate jdbc;
    private final AuctionService auctions;
    private final UserService users;
    private final ApplicationEventPublisher events;

    QuestionService(JdbcTemplate jdbc, AuctionService auctions, UserService users, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.auctions = auctions;
        this.users = users;
        this.events = events;
    }

    private record Row(UUID id, UUID auctionId, UUID askerId, String body, String answer, Instant createdAt,
            Instant answeredAt) {
    }

    /** EN: Newest first; the lot must be one the public can see. / VI: Mới nhất trước; lô phải là lô công khai. */
    @Transactional(readOnly = true)
    public List<QuestionView> forLot(UUID auctionId) {
        auctions.requirePubliclyVisible(auctionId);
        List<Row> rows = jdbc.query(
                "SELECT * FROM lot_questions WHERE auction_id = ? ORDER BY created_at DESC LIMIT 100",
                (rs, n) -> row(rs), auctionId);
        Map<UUID, String> names = users.namesOf(rows.stream().map(Row::askerId).distinct().toList());
        return rows.stream().map(row -> view(row, names.get(row.askerId()))).toList();
    }

    @Transactional
    public QuestionView ask(UUID askerId, UUID auctionId, String body) {
        auctions.requirePubliclyVisible(auctionId);
        AuctionSummaryView lot = auctions.participantSummariesOf(List.of(auctionId)).get(auctionId);
        if (!OPEN.contains(lot.auction().status())) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_ALLOWED, "Questions close when the lot does");
        }
        UUID sellerId = lot.auction().sellerId();
        if (sellerId.equals(askerId)) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_ALLOWED, "A seller cannot ask about their own lot");
        }
        // EN: Locks the asker's waiting rows, so two quick posts cannot both slip under the limit.
        // VI: Khoá các câu đang chờ của người hỏi, để hai lần gửi liền nhau không cùng lọt dưới giới hạn.
        int waiting = jdbc.queryForList("""
                SELECT id FROM lot_questions WHERE auction_id = ? AND asker_id = ? AND answer IS NULL FOR UPDATE
                """, UUID.class, auctionId, askerId).size();
        if (waiting >= WAITING_LIMIT) {
            throw new BusinessException(ErrorCode.QUESTION_LIMIT,
                    "You already have " + WAITING_LIMIT + " questions waiting for the seller on this lot");
        }

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String text = body.trim();
        jdbc.update("INSERT INTO lot_questions (id, auction_id, asker_id, body, created_at) VALUES (?, ?, ?, ?, ?)",
                id, auctionId, askerId, text, utc(now));
        events.publishEvent(new QuestionEvents.Asked(id, auctionId, sellerId, askerId, text, now));
        return view(new Row(id, auctionId, askerId, text, null, now, null),
                users.namesOf(List.of(askerId)).get(askerId));
    }

    /**
     * EN: The seller of the lot answers, once. Someone else's question answers 404, as one that does not exist.
     * VI: Người bán của lô trả lời, một lần. Câu hỏi trên lô của người khác trả 404, như câu không tồn tại.
     */
    @Transactional
    public QuestionView answer(UUID sellerId, UUID questionId, String answer) {
        Row question = jdbc.query("SELECT * FROM lot_questions WHERE id = ? FOR UPDATE", (rs, n) -> row(rs), questionId)
                .stream().findFirst()
                .filter(found -> auctions.sellerOf(found.auctionId()).equals(sellerId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.QUESTION_NOT_FOUND, "No question with id " + questionId));
        if (question.answer() != null) {
            throw new BusinessException(ErrorCode.QUESTION_ANSWERED, "This question has already been answered");
        }

        Instant now = Instant.now();
        String text = answer.trim();
        jdbc.update("UPDATE lot_questions SET answer = ?, answered_at = ? WHERE id = ?", text, utc(now), questionId);
        events.publishEvent(new QuestionEvents.Answered(questionId, question.auctionId(), question.askerId(),
                question.body(), text, now));
        Row answered = new Row(question.id(), question.auctionId(), question.askerId(), question.body(), text,
                question.createdAt(), now);
        return view(answered, users.namesOf(List.of(question.askerId())).get(question.askerId()));
    }

    private static QuestionView view(Row row, String askerName) {
        return new QuestionView(row.id(), row.auctionId(), UserService.handleOf(askerName), row.body(), row.answer(),
                row.createdAt(), row.answeredAt());
    }

    private static Row row(ResultSet rs) throws SQLException {
        return new Row(rs.getObject("id", UUID.class), rs.getObject("auction_id", UUID.class),
                rs.getObject("asker_id", UUID.class), rs.getString("body"), rs.getString("answer"),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("answered_at")));
    }

    private static Instant instant(Timestamp value) {
        return Optional.ofNullable(value).map(Timestamp::toInstant).orElse(null);
    }

    private static OffsetDateTime utc(Instant at) {
        return at.atOffset(ZoneOffset.UTC);
    }
}

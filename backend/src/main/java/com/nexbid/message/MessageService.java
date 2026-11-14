package com.nexbid.message;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.auction.AuctionService;
import com.nexbid.auction.AuctionSummaryView;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.order.OrderService;
import com.nexbid.order.OrderStatus;
import com.nexbid.user.UserService;

/**
 * EN: Messages between the buyer and the seller of one order — nobody else writes, an admin can read (for a dispute).
 *     Open from the win until 30 days after completion; a cancelled order is closed. Contact details are refused (see
 *     {@link ContactGuard}), and a sender is limited to {@link #BURST} messages per {@link #BURST_WINDOW} on an order.
 * VI: Tin nhắn giữa người mua và người bán của một đơn — không ai khác viết, admin đọc được (khi có khiếu nại). Mở từ lúc
 *     thắng tới 30 ngày sau khi hoàn tất; đơn đã huỷ thì đóng. Thông tin liên lạc bị từ chối (xem {@link ContactGuard}),
 *     và mỗi người gửi tối đa {@link #BURST} tin trong {@link #BURST_WINDOW} trên một đơn.
 */
@Service
public class MessageService {

    static final int BURST = 20;
    static final Duration BURST_WINDOW = Duration.ofMinutes(10);
    static final Duration OPEN_AFTER_COMPLETION = Duration.ofDays(30);

    public record MessageView(UUID id, UUID orderId, boolean mine, String sender, String body, Instant createdAt,
            Instant readAt) {
    }

    /** EN: One conversation in the inbox. / VI: Một cuộc trò chuyện trong hộp thư. */
    public record ThreadView(UUID orderId, UUID auctionId, String lotTitle, String coverImageUrl, String counterpart,
            boolean asSeller, String lastMessage, boolean lastMine, Instant lastAt, int unread, boolean open) {
    }

    public record ThreadDetail(ThreadView thread, List<MessageView> messages) {
    }

    private record Row(UUID id, UUID orderId, UUID senderId, String body, Instant createdAt, Instant readAt) {
    }

    private final JdbcTemplate jdbc;
    private final OrderService orders;
    private final AuctionService auctions;
    private final UserService users;
    private final ApplicationEventPublisher events;

    MessageService(JdbcTemplate jdbc, OrderService orders, AuctionService auctions, UserService users,
            ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.auctions = auctions;
        this.users = users;
        this.events = events;
    }

    /** EN: The thread, marking what the other side wrote as read. / VI: Cuộc trò chuyện, đánh dấu tin bên kia viết là đã đọc. */
    @Transactional
    public ThreadDetail open(UUID userId, UUID orderId) {
        OrderService.Parties order = partyTo(userId, orderId);
        Instant now = Instant.now();
        jdbc.update("UPDATE order_messages SET read_at = ? WHERE order_id = ? AND sender_id <> ? AND read_at IS NULL",
                utc(now), orderId, userId);
        return new ThreadDetail(thread(userId, order), messages(userId, orderId));
    }

    /** EN: An admin reads a thread without marking anything. / VI: Admin đọc cuộc trò chuyện mà không đánh dấu gì. */
    @Transactional(readOnly = true)
    public List<MessageView> read(UUID orderId) {
        orders.partiesOf(orderId).orElseThrow(() -> notFound(orderId));
        return messages(null, orderId);
    }

    @Transactional
    public MessageView send(UUID senderId, UUID orderId, String body) {
        OrderService.Parties order = partyTo(senderId, orderId);
        Instant now = Instant.now();
        if (!isOpen(order, now)) {
            throw new BusinessException(ErrorCode.MESSAGES_CLOSED, "Messages on this order are closed");
        }
        String text = body.trim();
        String contact = ContactGuard.findContact(text);
        if (contact != null) {
            throw new BusinessException(ErrorCode.MESSAGE_BLOCKED,
                    "Messages cannot contain " + contact + "; keep the sale on NexBid");
        }
        // EN: Locks the order's messages from this sender, so a burst cannot slip past the limit.
        // VI: Khoá các tin của người gửi trên đơn này, để gửi dồn dập không lọt qua giới hạn.
        int recent = jdbc.queryForList("""
                SELECT id FROM order_messages WHERE order_id = ? AND sender_id = ? AND created_at > ? FOR UPDATE
                """, UUID.class, orderId, senderId, utc(now.minus(BURST_WINDOW))).size();
        if (recent >= BURST) {
            throw new BusinessException(ErrorCode.MESSAGE_LIMIT, "Too many messages; wait a few minutes");
        }

        UUID recipient = senderId.equals(order.buyerId()) ? order.sellerId() : order.buyerId();
        Integer waiting = jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_messages WHERE order_id = ? AND sender_id = ? AND read_at IS NULL",
                Integer.class, orderId, senderId);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO order_messages (id, order_id, sender_id, body, created_at) VALUES (?, ?, ?, ?, ?)",
                id, orderId, senderId, text, utc(now));
        String handle = UserService.handleOf(users.namesOf(List.of(senderId)).get(senderId));
        events.publishEvent(new MessageEvents.Sent(id, orderId, order.auctionId(), senderId, recipient, handle, text,
                now, waiting == null || waiting == 0));
        return new MessageView(id, orderId, true, handle, text, now, null);
    }

    /** EN: Every conversation the person has, latest first. / VI: Mọi cuộc trò chuyện của người đó, mới nhất trước. */
    @Transactional(readOnly = true)
    public List<ThreadView> threads(UUID userId) {
        List<UUID> orderIds = jdbc.queryForList("""
                SELECT DISTINCT m.order_id FROM order_messages m JOIN orders o ON o.id = m.order_id
                WHERE o.buyer_id = ? OR o.seller_id = ?
                """, UUID.class, userId, userId);
        Map<UUID, OrderService.Parties> parties = orders.partiesOf(orderIds);
        return parties.values().stream()
                .map(order -> thread(userId, order))
                .sorted(Comparator.comparing(ThreadView::lastAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public int unreadCount(UUID userId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM order_messages m JOIN orders o ON o.id = m.order_id
                WHERE (o.buyer_id = ? OR o.seller_id = ?) AND m.sender_id <> ? AND m.read_at IS NULL
                """, Integer.class, userId, userId, userId);
        return count == null ? 0 : count;
    }

    private ThreadView thread(UUID userId, OrderService.Parties order) {
        AuctionSummaryView lot = auctions.participantSummariesOf(List.of(order.auctionId())).get(order.auctionId());
        boolean asSeller = userId.equals(order.sellerId());
        UUID other = asSeller ? order.buyerId() : order.sellerId();
        Row last = jdbc.query("SELECT * FROM order_messages WHERE order_id = ? ORDER BY created_at DESC LIMIT 1",
                (rs, n) -> row(rs), order.orderId()).stream().findFirst().orElse(null);
        Integer unread = jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_messages WHERE order_id = ? AND sender_id <> ? AND read_at IS NULL",
                Integer.class, order.orderId(), userId);
        return new ThreadView(order.orderId(), order.auctionId(), lot == null ? "" : lot.product().name(),
                lot == null ? null : lot.product().coverImageUrl(),
                UserService.handleOf(users.namesOf(List.of(other)).get(other)), asSeller,
                last == null ? null : last.body(), last != null && last.senderId().equals(userId),
                last == null ? null : last.createdAt(), unread == null ? 0 : unread, isOpen(order, Instant.now()));
    }

    private List<MessageView> messages(UUID viewerId, UUID orderId) {
        List<Row> rows = jdbc.query("""
                SELECT * FROM (SELECT * FROM order_messages WHERE order_id = ? ORDER BY created_at DESC LIMIT 300) recent
                ORDER BY created_at
                """, (rs, n) -> row(rs), orderId);
        Map<UUID, String> names = users.namesOf(rows.stream().map(Row::senderId).distinct().toList());
        return rows.stream().map(row -> new MessageView(row.id(), row.orderId(),
                row.senderId().equals(viewerId), UserService.handleOf(names.get(row.senderId())), row.body(),
                row.createdAt(), row.readAt())).toList();
    }

    /** EN: Someone else's order answers 404, as one that does not exist. / VI: Đơn của người khác trả 404, như đơn không tồn tại. */
    private OrderService.Parties partyTo(UUID userId, UUID orderId) {
        return orders.partiesOf(orderId)
                .filter(order -> userId.equals(order.buyerId()) || userId.equals(order.sellerId()))
                .orElseThrow(() -> notFound(orderId));
    }

    private static boolean isOpen(OrderService.Parties order, Instant now) {
        if (order.status() == OrderStatus.CANCELLED) {
            return false;
        }
        return order.status() != OrderStatus.COMPLETED || order.completedAt() == null
                || order.completedAt().plus(OPEN_AFTER_COMPLETION).isAfter(now);
    }

    private static Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp read = rs.getTimestamp("read_at");
        return new Row(rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class),
                rs.getObject("sender_id", UUID.class), rs.getString("body"), rs.getTimestamp("created_at").toInstant(),
                read == null ? null : read.toInstant());
    }

    private static ResourceNotFoundException notFound(UUID orderId) {
        return new ResourceNotFoundException(ErrorCode.ORDER_NOT_FOUND, "No order with id " + orderId);
    }

    private static OffsetDateTime utc(Instant at) {
        return at.atOffset(ZoneOffset.UTC);
    }
}

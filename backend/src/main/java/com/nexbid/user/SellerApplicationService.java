package com.nexbid.user;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.user.entity.Role;
import com.nexbid.user.entity.SellerApplication;
import com.nexbid.user.entity.User;
import com.nexbid.user.repository.RoleRepository;
import com.nexbid.user.repository.SellerApplicationRepository;
import com.nexbid.user.repository.UserRepository;

/**
 * EN: Becoming a seller (spec §7.1): a buyer asks, an admin approves or rejects with a reason, and a rejected
 *     buyer may ask again. Approval grants the SELLER role in the same transaction as the decision.
 * VI: Trở thành người bán (spec §7.1): người mua gửi yêu cầu, admin duyệt hoặc từ chối kèm lý do, và người bị
 *     từ chối được gửi lại. Duyệt thì cấp vai trò SELLER trong cùng transaction với quyết định.
 */
@Service
public class SellerApplicationService {

    private final SellerApplicationRepository applications;
    private final UserRepository users;
    private final RoleRepository roles;
    private final ApplicationEventPublisher events;

    SellerApplicationService(
            SellerApplicationRepository applications,
            UserRepository users,
            RoleRepository roles,
            ApplicationEventPublisher events) {
        this.applications = applications;
        this.users = users;
        this.roles = roles;
        this.events = events;
    }

    /**
     * EN: The account row is the lock: two tabs sending at once queue here, and the second sees the first.
     * VI: Dòng tài khoản là khoá: hai tab gửi cùng lúc sẽ xếp hàng ở đây, và tab thứ hai thấy yêu cầu của tab đầu.
     */
    @Transactional
    public SellerApplicationView apply(UUID userId, String note) {
        User user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "No account with id " + userId));
        if (isSeller(user)) {
            throw new BusinessException(ErrorCode.ALREADY_SELLER, "This account can already sell");
        }
        if (applications.existsByUserIdAndStatus(userId, SellerApplicationStatus.PENDING)) {
            throw new BusinessException(ErrorCode.SELLER_APPLICATION_PENDING, "A request is already waiting for review");
        }

        SellerApplication application = applications.save(new SellerApplication(userId, note.trim(), Instant.now()));
        events.publishEvent(new SellerApplicationEvent(
                application.getId(), userId, userId, SellerApplicationStatus.PENDING, null, application.getCreatedAt()));
        return view(application, user);
    }

    /** EN: The caller's most recent request, if any. / VI: Yêu cầu gần nhất của người gọi, nếu có. */
    @Transactional(readOnly = true)
    public Optional<SellerApplicationView> latestOf(UUID userId) {
        return applications.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(application -> view(application, users.findById(userId).orElse(null)));
    }

    /**
     * EN: The admin queue. Waiting requests read oldest first, so nobody is left at the bottom; decided ones newest first.
     * VI: Hàng chờ của admin. Yêu cầu đang chờ xếp cũ nhất trước để không ai bị bỏ quên dưới đáy; đã quyết định thì mới nhất trước.
     */
    @Transactional(readOnly = true)
    public SellerApplicationPage list(SellerApplicationStatus status, int page, int size) {
        Sort order = status == SellerApplicationStatus.PENDING
                ? Sort.by(Sort.Direction.ASC, "createdAt")
                : Sort.by(Sort.Direction.DESC, "createdAt");
        PageRequest request = PageRequest.of(Math.max(0, page - 1), Math.clamp(size, 1, 100), order);
        Page<SellerApplication> found = status == null
                ? applications.findAll(request)
                : applications.findByStatus(status, request);

        Map<UUID, User> applicants = users.findAllById(
                        found.getContent().stream().map(SellerApplication::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return new SellerApplicationPage(
                found.getContent().stream().map(a -> view(a, applicants.get(a.getUserId()))).toList(),
                found.getNumber() + 1, found.getSize(), found.getTotalElements(), found.getTotalPages());
    }

    @Transactional
    public SellerApplicationView approve(UUID applicationId, UUID adminId) {
        SellerApplication application = pending(applicationId);
        User user = users.findById(application.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.USER_NOT_FOUND, "No account with id " + application.getUserId()));
        Role seller = roles.findByName(RoleName.SELLER)
                .orElseThrow(() -> new IllegalStateException("Role missing from database: " + RoleName.SELLER));

        user.addRole(seller);
        users.save(user);
        return decide(application, user, SellerApplicationStatus.APPROVED, null, adminId);
    }

    @Transactional
    public SellerApplicationView reject(UUID applicationId, UUID adminId, String reason) {
        SellerApplication application = pending(applicationId);
        return decide(application, users.findById(application.getUserId()).orElse(null),
                SellerApplicationStatus.REJECTED, reason.trim(), adminId);
    }

    /**
     * EN: Locked, so two admins deciding at once cannot both win: the second finds it already decided.
     * VI: Có khoá, nên hai admin quyết định cùng lúc không thể cùng thành công: người thứ hai thấy nó đã được quyết định.
     */
    private SellerApplication pending(UUID applicationId) {
        SellerApplication application = applications.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.SELLER_APPLICATION_NOT_FOUND, "No seller application with id " + applicationId));
        if (application.getStatus() != SellerApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.SELLER_APPLICATION_NOT_PENDING, "This request was already decided");
        }
        return application;
    }

    private SellerApplicationView decide(
            SellerApplication application, User user, SellerApplicationStatus outcome, String reason, UUID adminId) {
        Instant now = Instant.now();
        application.decide(outcome, reason, adminId, now);
        applications.save(application);
        events.publishEvent(new SellerApplicationEvent(
                application.getId(), application.getUserId(), adminId, outcome, reason, now));
        return view(application, user);
    }

    private static boolean isSeller(User user) {
        return user.getRoles().stream().map(Role::getName).anyMatch(RoleName.SELLER::equals);
    }

    private static SellerApplicationView view(SellerApplication application, User user) {
        return new SellerApplicationView(
                application.getId(),
                application.getUserId(),
                user == null ? null : user.getFullName(),
                user == null ? null : user.getEmail(),
                application.getNote(),
                application.getStatus(),
                application.getRejectionReason(),
                application.getCreatedAt(),
                application.getDecidedAt());
    }
}

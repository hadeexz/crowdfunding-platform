package com.hadee.crowdfunding_platform.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hadee.crowdfunding_platform.config.GiveLivesProperties;
import com.hadee.crowdfunding_platform.dto.CampaignResponse;
import com.hadee.crowdfunding_platform.dto.CampaignSummaryResponse;
import com.hadee.crowdfunding_platform.dto.CreateCampaignRequest;
import com.hadee.crowdfunding_platform.dto.PageResponse;
import com.hadee.crowdfunding_platform.dto.UpdateCampaignRequest;
import com.hadee.crowdfunding_platform.entity.Campaign;
import com.hadee.crowdfunding_platform.entity.User;
import com.hadee.crowdfunding_platform.entity.enums.AccountType;
import com.hadee.crowdfunding_platform.entity.enums.CampaignCategory;
import com.hadee.crowdfunding_platform.entity.enums.CampaignStatus;
import com.hadee.crowdfunding_platform.entity.enums.DonationStatus;
import com.hadee.crowdfunding_platform.entity.enums.VerificationStatus;
import com.hadee.crowdfunding_platform.exception.ConflictException;
import com.hadee.crowdfunding_platform.exception.ForbiddenActionException;
import com.hadee.crowdfunding_platform.exception.ResourceNotFoundException;
import com.hadee.crowdfunding_platform.repository.CampaignRepository;
import com.hadee.crowdfunding_platform.repository.CampaignSpecifications;
import com.hadee.crowdfunding_platform.repository.DonationRepository;
import com.hadee.crowdfunding_platform.repository.UserRepository;

@Service
public class CampaignService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_SEARCH_LENGTH = 100;

    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final DonationRepository donationRepository;
    private final GiveLivesProperties properties;

    public CampaignService(CampaignRepository campaignRepository,
                           UserRepository userRepository,
                           DonationRepository donationRepository,
                           GiveLivesProperties properties) {
        this.campaignRepository = campaignRepository;
        this.userRepository = userRepository;
        this.donationRepository = donationRepository;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // CREATE (Part 4)
    // ------------------------------------------------------------------
    @Transactional
    public CampaignResponse create(Long userId, CreateCampaignRequest request) {

        // The creator comes from the token (userId), never from the request body
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 1. Only creators and organizations raise funds
        AccountType type = creator.getAccountType();
        if (type != AccountType.CREATOR && type != AccountType.ORGANIZATION) {
            throw new ForbiddenActionException(
                    "Only creators and organizations can create campaigns");
        }

        // 2. They must be verified (KYC approved) and not banned
        if (creator.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new ForbiddenActionException(
                    "Your account must be verified (KYC approved) before you can create campaigns");
        }
        if (creator.isBanned()) {
            throw new ForbiddenActionException("This account has been suspended");
        }

        // 3. Goal: minimum from config, no maximum
        BigDecimal goal = request.goalAmount();
        BigDecimal minGoal = properties.getMinCampaignGoal();
        if (goal.compareTo(minGoal) < 0) {
            throw new IllegalArgumentException("Goal amount must be at least " + minGoal);
        }

        // 4. Duration: limits from config
        int days = request.durationDays();
        int minDays = properties.getCampaignMinDays();
        int maxDays = properties.getCampaignMaxDays();
        if (days < minDays || days > maxDays) {
            throw new IllegalArgumentException(
                    "Duration must be between " + minDays + " and " + maxDays + " days");
        }

        // 5. The server decides every system field. All times are UTC.
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);

        Campaign campaign = new Campaign();
        campaign.setCreator(creator);
        campaign.setTitle(request.title().trim());
        campaign.setDescription(request.description().trim());
        campaign.setImageUrl(request.imageUrl() == null ? null : request.imageUrl().trim());
        campaign.setCategory(request.category());
        campaign.setGoalAmount(goal);
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaign.setStartDate(now);
        campaign.setDeadline(now.plusDays(days));
        // currentAmount stays 0 (the entity default)

        Campaign saved = campaignRepository.save(campaign);
        return toResponse(saved);
    }

    // ------------------------------------------------------------------
    // VIEW ONE (Part 5). viewerId is null for anonymous visitors.
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public CampaignResponse getById(Long campaignId, Long viewerId, boolean viewerIsAdmin) {

        Campaign campaign = campaignRepository.findByIdWithCreator(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found"));

        // DRAFT and SUSPENDED campaigns are visible only to the owner and admins.
        // Everyone else gets "not found", so we don't even confirm they exist.
        boolean hiddenFromPublic = campaign.getStatus() == CampaignStatus.DRAFT
                || campaign.getStatus() == CampaignStatus.SUSPENDED;

        if (hiddenFromPublic) {
            boolean isOwner = viewerId != null
                    && viewerId.equals(campaign.getCreator().getId());
            if (!isOwner && !viewerIsAdmin) {
                throw new ResourceNotFoundException("Campaign not found");
            }
        }

        return toResponse(campaign);
    }

    // ------------------------------------------------------------------
    // PUBLIC BROWSE LIST (Part 6)
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public PageResponse<CampaignSummaryResponse> list(CampaignCategory category,
                                                      CampaignStatus status,
                                                      String q,
                                                      String sort,
                                                      int page,
                                                      int size) {
        validatePaging(page, size);

        // No status given means "live campaigns"
        CampaignStatus effectiveStatus = (status == null) ? CampaignStatus.ACTIVE : status;

        // The public list never shows DRAFT or SUSPENDED campaigns
        if (effectiveStatus == CampaignStatus.DRAFT
                || effectiveStatus == CampaignStatus.SUSPENDED) {
            throw new IllegalArgumentException(
                    "This status is not available for public browsing");
        }

        // Start with the always-on block, then add only what the visitor asked for
        Specification<Campaign> spec = CampaignSpecifications.hasStatus(effectiveStatus);

        // Until the deadline scheduler exists, hide "ACTIVE" campaigns that already expired
        if (effectiveStatus == CampaignStatus.ACTIVE) {
            spec = spec.and(CampaignSpecifications.deadlineAfter(LocalDateTime.now(ZoneOffset.UTC)));
        }
        if (category != null) {
            spec = spec.and(CampaignSpecifications.hasCategory(category));
        }
        if (q != null && !q.isBlank()) {
            if (q.length() > MAX_SEARCH_LENGTH) {
                throw new IllegalArgumentException(
                        "Search text must be at most " + MAX_SEARCH_LENGTH + " characters");
            }
            spec = spec.and(CampaignSpecifications.titleContains(q.trim()));
        }

        Pageable pageable = PageRequest.of(page, size, toSort(sort));
        Page<Campaign> result = campaignRepository.findAll(spec, pageable);

        return PageResponse.from(result.map(this::toSummary));
    }

    // ------------------------------------------------------------------
    // MY CAMPAIGNS (Part 6): every status, only the logged-in user's own
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public PageResponse<CampaignSummaryResponse> listMine(Long userId,
                                                          CampaignStatus status,
                                                          int page,
                                                          int size) {
        validatePaging(page, size);

        Specification<Campaign> spec = CampaignSpecifications.byCreator(userId);
        if (status != null) {
            spec = spec.and(CampaignSpecifications.hasStatus(status));
        }

        // Newest created first; id breaks ties so paging stays stable
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Campaign> result = campaignRepository.findAll(spec, pageable);

        return PageResponse.from(result.map(this::toSummary));
    }

    // ------------------------------------------------------------------
    // EDIT (Part 7)
    // ------------------------------------------------------------------
    @Transactional
    public CampaignResponse update(Long userId, Long campaignId, UpdateCampaignRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Campaign campaign = campaignRepository.findByIdWithCreator(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found"));

        // 1. Only the owner may edit. Hidden campaigns look non-existent to others.
        if (!campaign.getCreator().getId().equals(userId)) {
            boolean hidden = campaign.getStatus() == CampaignStatus.DRAFT
                    || campaign.getStatus() == CampaignStatus.SUSPENDED;
            if (hidden) {
                throw new ResourceNotFoundException("Campaign not found");
            }
            throw new ForbiddenActionException("You can only edit your own campaigns");
        }

        // 2. The owner must still be verified and not banned
        if (user.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new ForbiddenActionException(
                    "Your account must be verified to edit campaigns");
        }
        if (user.isBanned()) {
            throw new ForbiddenActionException("This account has been suspended");
        }

        // 3. Only running campaigns can be edited
        CampaignStatus status = campaign.getStatus();
        if (status != CampaignStatus.ACTIVE && status != CampaignStatus.GOAL_REACHED) {
            throw new ConflictException(
                    "A campaign with status " + status + " can no longer be edited");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (!campaign.getDeadline().isAfter(now)) {
            throw new ConflictException("This campaign has already ended");
        }

        // 4. The request must change something
        boolean nothingSent = request.title() == null
                && request.description() == null
                && request.imageUrl() == null
                && request.category() == null
                && request.goalAmount() == null
                && request.durationDays() == null;
        if (nothingSent) {
            throw new IllegalArgumentException("Nothing to update. Send at least one field");
        }

        // 5. Have donations started? Then the strict rule applies.
        boolean donationsStarted = campaign.getCurrentAmount().signum() > 0
                || donationRepository.existsByCampaignIdAndStatusIn(
                        campaignId, List.of(DonationStatus.COMPLETED, DonationStatus.REFUNDED));

        if (donationsStarted) {
            applyGoalIncreaseOnly(campaign, request);
        } else {
            applyFullEdit(campaign, request, now);
        }

        Campaign saved = campaignRepository.save(campaign);
        return toResponse(saved);
    }

    // After the first donation: only a HIGHER goal is allowed
    private void applyGoalIncreaseOnly(Campaign campaign, UpdateCampaignRequest r) {

        boolean otherFieldsSent = r.title() != null
                || r.description() != null
                || r.imageUrl() != null
                || r.category() != null
                || r.durationDays() != null;
        if (otherFieldsSent) {
            throw new ConflictException(
                    "Donations have started, so only the goal amount can be changed (and only increased)");
        }

        BigDecimal newGoal = r.goalAmount();
        if (newGoal.compareTo(campaign.getGoalAmount()) <= 0) {
            throw new IllegalArgumentException(
                    "The new goal must be higher than the current goal of " + campaign.getGoalAmount());
        }

        campaign.setGoalAmount(newGoal);

        // There is room for donations again, so a "goal reached" campaign reopens
        if (campaign.getStatus() == CampaignStatus.GOAL_REACHED) {
            campaign.setStatus(CampaignStatus.ACTIVE);
        }
    }

    // Before any donation: every editable field may change
    private void applyFullEdit(Campaign campaign, UpdateCampaignRequest r, LocalDateTime now) {

        if (r.title() != null) {
            String title = r.title().trim();
            if (title.isEmpty()) {
                throw new IllegalArgumentException("Title cannot be blank");
            }
            campaign.setTitle(title);
        }

        if (r.description() != null) {
            String description = r.description().trim();
            if (description.length() < 20) {
                throw new IllegalArgumentException(
                        "Description must be at least 20 characters");
            }
            campaign.setDescription(description);
        }

        if (r.imageUrl() != null) {
            campaign.setImageUrl(r.imageUrl().trim());
        }

        if (r.category() != null) {
            campaign.setCategory(r.category());
        }

        if (r.goalAmount() != null) {
            BigDecimal minGoal = properties.getMinCampaignGoal();
            if (r.goalAmount().compareTo(minGoal) < 0) {
                throw new IllegalArgumentException("Goal amount must be at least " + minGoal);
            }
            campaign.setGoalAmount(r.goalAmount());
        }

        if (r.durationDays() != null) {
            int days = r.durationDays();
            int minDays = properties.getCampaignMinDays();
            int maxDays = properties.getCampaignMaxDays();
            if (days < minDays || days > maxDays) {
                throw new IllegalArgumentException(
                        "Duration must be between " + minDays + " and " + maxDays + " days");
            }
            LocalDateTime newDeadline = campaign.getStartDate().plusDays(days);
            if (!newDeadline.isAfter(now)) {
                throw new IllegalArgumentException(
                        "With " + days + " days from the start date, the deadline would already be in the past");
            }
            campaign.setDeadline(newDeadline);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    private void validatePaging(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    // Every sort ends with id, so equal values always come in the same order
    private Sort toSort(String sort) {
        String s = (sort == null) ? "newest" : sort.trim().toLowerCase();
        return switch (s) {
            case "newest" -> Sort.by(Sort.Order.desc("startDate"), Sort.Order.desc("id"));
            case "ending-soon" -> Sort.by(Sort.Order.asc("deadline"), Sort.Order.desc("id"));
            case "most-funded" -> Sort.by(Sort.Order.desc("currentAmount"), Sort.Order.desc("id"));
            default -> throw new IllegalArgumentException(
                    "Sort must be one of: newest, ending-soon, most-funded");
        };
    }

    private CampaignResponse toResponse(Campaign c) {
        User creator = c.getCreator();
        return new CampaignResponse(
                c.getId(),
                creator.getId(),
                creator.getFullName(),
                c.getTitle(),
                c.getDescription(),
                c.getImageUrl(),
                c.getCategory(),
                c.getGoalAmount(),
                c.getCurrentAmount(),
                c.getStatus(),
                c.getStartDate(),
                c.getDeadline(),
                calculateProgress(c.getCurrentAmount(), c.getGoalAmount()),
                c.getGoalAmount().subtract(c.getCurrentAmount()).max(BigDecimal.ZERO),
                calculateDaysLeft(c));
    }

    private CampaignSummaryResponse toSummary(Campaign c) {
        return new CampaignSummaryResponse(
                c.getId(),
                c.getTitle(),
                c.getImageUrl(),
                c.getCategory(),
                c.getCreator().getFullName(),
                c.getGoalAmount(),
                c.getCurrentAmount(),
                calculateProgress(c.getCurrentAmount(), c.getGoalAmount()),
                c.getStatus(),
                c.getDeadline(),
                calculateDaysLeft(c));
    }

    // current / goal * 100, 2 decimals, rounded DOWN so 99.999% never shows as 100%
    private BigDecimal calculateProgress(BigDecimal current, BigDecimal goal) {
        if (goal.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return current.multiply(BigDecimal.valueOf(100))
                .divide(goal, 2, RoundingMode.DOWN);
    }

    // Rounded UP: 1 minute left still counts as "1 day left". 0 once not running.
    private long calculateDaysLeft(Campaign c) {
        boolean running = c.getStatus() == CampaignStatus.ACTIVE
                || c.getStatus() == CampaignStatus.GOAL_REACHED;
        if (!running) {
            return 0;
        }
        long minutes = ChronoUnit.MINUTES.between(
                LocalDateTime.now(ZoneOffset.UTC), c.getDeadline());
        return minutes <= 0 ? 0 : (minutes + 1439) / 1440;
    }
}
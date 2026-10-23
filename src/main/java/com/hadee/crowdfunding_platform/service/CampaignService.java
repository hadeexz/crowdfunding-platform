package com.hadee.crowdfunding_platform.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hadee.crowdfunding_platform.config.GiveLivesProperties;
import com.hadee.crowdfunding_platform.dto.CampaignResponse;
import com.hadee.crowdfunding_platform.dto.CreateCampaignRequest;
import com.hadee.crowdfunding_platform.entity.Campaign;
import com.hadee.crowdfunding_platform.entity.User;
import com.hadee.crowdfunding_platform.entity.enums.AccountType;
import com.hadee.crowdfunding_platform.entity.enums.CampaignStatus;
import com.hadee.crowdfunding_platform.entity.enums.VerificationStatus;
import com.hadee.crowdfunding_platform.exception.ForbiddenActionException;
import com.hadee.crowdfunding_platform.exception.ResourceNotFoundException;
import com.hadee.crowdfunding_platform.repository.CampaignRepository;
import com.hadee.crowdfunding_platform.repository.UserRepository;

@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final GiveLivesProperties properties;

    public CampaignService(CampaignRepository campaignRepository,
                           UserRepository userRepository,
                           GiveLivesProperties properties) {
        this.campaignRepository = campaignRepository;
        this.userRepository = userRepository;
        this.properties = properties;
    }

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

    // viewerId is null for anonymous visitors
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
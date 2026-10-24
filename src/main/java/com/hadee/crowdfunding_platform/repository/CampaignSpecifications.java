package com.hadee.crowdfunding_platform.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.domain.Specification;

import com.hadee.crowdfunding_platform.entity.Campaign;
import com.hadee.crowdfunding_platform.entity.enums.CampaignCategory;
import com.hadee.crowdfunding_platform.entity.enums.CampaignStatus;

public final class CampaignSpecifications {

    private CampaignSpecifications() {
    }

    public static Specification<Campaign> hasStatus(CampaignStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Campaign> hasCategory(CampaignCategory category) {
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    public static Specification<Campaign> deadlineAfter(LocalDateTime time) {
        return (root, query, cb) -> cb.greaterThan(root.<LocalDateTime>get("deadline"), time);
    }

    public static Specification<Campaign> byCreator(Long creatorId) {
        return (root, query, cb) -> cb.equal(root.get("creator").get("id"), creatorId);
    }

    // Case-insensitive "title contains text". The user's %, _ and [ are made harmless.
    public static Specification<Campaign> titleContains(String text) {
        String escaped = text.toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
                .replace("[", "\\[");
        String pattern = "%" + escaped + "%";
        return (root, query, cb) ->
                cb.like(cb.lower(root.<String>get("title")), pattern, '\\');
    }
}
package com.hadee.crowdfunding_platform.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

import com.hadee.crowdfunding_platform.entity.AdminAction;
import com.hadee.crowdfunding_platform.entity.enums.AdminTargetType;

public interface AdminActionRepository extends Repository<AdminAction, Long> {

    AdminAction save(AdminAction adminAction);

    Optional<AdminAction> findById(Long id);

    List<AdminAction> findByAdminIdOrderByCreatedAtDesc(Long adminId);

    List<AdminAction> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(AdminTargetType targetType, Long targetId);
}
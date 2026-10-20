package com.hadee.crowdfunding_platform.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hadee.crowdfunding_platform.entity.CreatorVerification;
import com.hadee.crowdfunding_platform.entity.enums.KycStatus;

public interface CreatorVerificationRepository
        extends JpaRepository<CreatorVerification, Long> {

    Optional<CreatorVerification> findTopByUserIdOrderByIdDesc(Long userId);

    @Query("select k from CreatorVerification k join fetch k.user "
         + "where k.status = :status order by k.submittedAt asc")
    List<CreatorVerification> findByStatusWithUser(@Param("status") KycStatus status);

    @Query("select k from CreatorVerification k join fetch k.user where k.id = :id")
    Optional<CreatorVerification> findByIdWithUser(@Param("id") Long id);
}
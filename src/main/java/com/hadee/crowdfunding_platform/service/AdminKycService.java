package com.hadee.crowdfunding_platform.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hadee.crowdfunding_platform.dto.AdminKycResponse;
import com.hadee.crowdfunding_platform.entity.AdminAction;
import com.hadee.crowdfunding_platform.entity.CreatorVerification;
import com.hadee.crowdfunding_platform.entity.User;
import com.hadee.crowdfunding_platform.entity.enums.AdminActionType;
import com.hadee.crowdfunding_platform.entity.enums.AdminTargetType;
import com.hadee.crowdfunding_platform.entity.enums.KycStatus;
import com.hadee.crowdfunding_platform.entity.enums.Role;
import com.hadee.crowdfunding_platform.entity.enums.VerificationStatus;
import com.hadee.crowdfunding_platform.exception.ConflictException;
import com.hadee.crowdfunding_platform.exception.ForbiddenActionException;
import com.hadee.crowdfunding_platform.exception.ResourceNotFoundException;
import com.hadee.crowdfunding_platform.repository.AdminActionRepository;
import com.hadee.crowdfunding_platform.repository.CreatorVerificationRepository;
import com.hadee.crowdfunding_platform.repository.UserRepository;

@Service
public class AdminKycService {

    private final CreatorVerificationRepository kycRepository;
    private final UserRepository userRepository;
    private final AdminActionRepository adminActionRepository;

    public AdminKycService(CreatorVerificationRepository kycRepository,
                           UserRepository userRepository,
                           AdminActionRepository adminActionRepository) {
        this.kycRepository = kycRepository;
        this.userRepository = userRepository;
        this.adminActionRepository = adminActionRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminKycResponse> list(KycStatus status) {
        return kycRepository.findByStatusWithUser(status).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdminKycResponse approve(Long adminId, Long kycId, String ipAddress) {
        return review(adminId, kycId, true, null, ipAddress);
    }

    @Transactional
    public AdminKycResponse reject(Long adminId, Long kycId, String remarks, String ipAddress) {
        return review(adminId, kycId, false, remarks.trim(), ipAddress);
    }

    // One method for both outcomes, so approve and reject can never drift apart
    private AdminKycResponse review(Long adminId, Long kycId, boolean approve,
                                    String remarks, String ipAddress) {

        // 1. Second lock on the door: the reviewer must really be an admin
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
        if (admin.getRole() != Role.ADMIN) {
            throw new ForbiddenActionException("Only admins can review KYC");
        }

        // 2. The request must exist and still be waiting
        CreatorVerification kyc = kycRepository.findByIdWithUser(kycId)
                .orElseThrow(() -> new ResourceNotFoundException("Verification request not found"));
        if (kyc.getStatus() != KycStatus.PENDING) {
            throw new ConflictException("This request has already been reviewed");
        }

        // 3. Update the request
        kyc.setStatus(approve ? KycStatus.APPROVED : KycStatus.REJECTED);
        kyc.setReviewedBy(admin);
        kyc.setReviewedAt(LocalDateTime.now(ZoneOffset.UTC));
        kyc.setRemarks(remarks);
        kycRepository.save(kyc);

        // 4. Update the applicant's status (same transaction)
        User applicant = kyc.getUser();
        applicant.setVerificationStatus(
                approve ? VerificationStatus.VERIFIED : VerificationStatus.REJECTED);
        userRepository.save(applicant);

        // 5. Write the permanent audit entry
        AdminAction action = new AdminAction();
        action.setAdmin(admin);
        action.setActionType(approve ? AdminActionType.APPROVE_KYC : AdminActionType.REJECT_KYC);
        action.setTargetType(AdminTargetType.CREATOR_VERIFICATION);
        action.setTargetId(kyc.getId());
        action.setReason(remarks);
        action.setIpAddress(ipAddress);
        adminActionRepository.save(action);

        return toResponse(kyc);
    }

    private AdminKycResponse toResponse(CreatorVerification kyc) {
        User u = kyc.getUser();
        return new AdminKycResponse(
                kyc.getId(),
                u.getId(),
                u.getFullName(),
                u.getEmail(),
                u.getAccountType(),
                kyc.getDocumentType(),
                kyc.getDocumentNumberMasked(),
                kyc.getDocumentFileUrl(),
                kyc.getStatus(),
                kyc.getSubmittedAt(),
                kyc.getReviewedAt(),
                kyc.getRemarks());
    }
}
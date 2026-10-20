package com.hadee.crowdfunding_platform.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hadee.crowdfunding_platform.dto.KycResponse;
import com.hadee.crowdfunding_platform.dto.SubmitKycRequest;
import com.hadee.crowdfunding_platform.entity.CreatorVerification;
import com.hadee.crowdfunding_platform.entity.User;
import com.hadee.crowdfunding_platform.entity.enums.AccountType;
import com.hadee.crowdfunding_platform.entity.enums.DocumentType;
import com.hadee.crowdfunding_platform.entity.enums.KycStatus;
import com.hadee.crowdfunding_platform.entity.enums.VerificationStatus;
import com.hadee.crowdfunding_platform.exception.ConflictException;
import com.hadee.crowdfunding_platform.exception.ForbiddenActionException;
import com.hadee.crowdfunding_platform.exception.ResourceNotFoundException;
import com.hadee.crowdfunding_platform.repository.CreatorVerificationRepository;
import com.hadee.crowdfunding_platform.repository.UserRepository;

@Service
public class KycService {

    private final CreatorVerificationRepository kycRepository;
    private final UserRepository userRepository;

    public KycService(CreatorVerificationRepository kycRepository,
                      UserRepository userRepository) {
        this.kycRepository = kycRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void submit(Long userId, SubmitKycRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 1. Only creators and organizations go through KYC
        AccountType type = user.getAccountType();
        if (type != AccountType.CREATOR && type != AccountType.ORGANIZATION) {
            throw new ForbiddenActionException(
                    "Only creators and organizations can submit KYC");
        }

        // 2. The document must suit the account type
        boolean isOrgDocument = request.documentType() == DocumentType.ORG_REGISTRATION;
        if (type == AccountType.ORGANIZATION && !isOrgDocument) {
            throw new IllegalArgumentException(
                    "Organizations must submit an ORG_REGISTRATION document");
        }
        if (type == AccountType.CREATOR && isOrgDocument) {
            throw new IllegalArgumentException(
                    "ORG_REGISTRATION is only for organization accounts");
        }

        // 3. Only NOT_SUBMITTED and REJECTED users may (re)submit
        VerificationStatus current = user.getVerificationStatus();
        if (current == VerificationStatus.PENDING) {
            throw new ConflictException("Your verification is already under review");
        }
        if (current == VerificationStatus.VERIFIED) {
            throw new ConflictException("You are already verified");
        }

        // 4. Build the submission. We store ONLY a masked number.
        CreatorVerification kyc = new CreatorVerification();
        kyc.setUser(user);
        kyc.setDocumentType(request.documentType());
        kyc.setDocumentNumberMasked("XXXXXX" + request.documentNumberLast4().toUpperCase());
        kyc.setDocumentFileUrl(request.documentFileUrl().trim());
        kyc.setStatus(KycStatus.PENDING);

        try {
            kycRepository.saveAndFlush(kyc);
        } catch (DataIntegrityViolationException ex) {
            // Safety net: the database allows only one PENDING/APPROVED row per user,
            // e.g. if the button is double-clicked.
            throw new ConflictException(
                    "A verification request already exists for your account");
        }

        // 5. Same transaction: the user's status moves to PENDING
        user.setVerificationStatus(VerificationStatus.PENDING);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public KycResponse getMyLatest(Long userId) {
        CreatorVerification kyc = kycRepository.findTopByUserIdOrderByIdDesc(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "You have not submitted any verification yet"));
        return toResponse(kyc);
    }

    private KycResponse toResponse(CreatorVerification kyc) {
        return new KycResponse(
                kyc.getId(),
                kyc.getDocumentType(),
                kyc.getDocumentNumberMasked(),
                kyc.getDocumentFileUrl(),
                kyc.getStatus(),
                kyc.getSubmittedAt(),
                kyc.getReviewedAt(),
                kyc.getRemarks());
    }
}
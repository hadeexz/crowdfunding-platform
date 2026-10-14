package com.hadee.crowdfunding_platform.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Nationalized;

import com.hadee.crowdfunding_platform.entity.enums.DocumentType;
import com.hadee.crowdfunding_platform.entity.enums.KycStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "creator_verifications")
@Getter
@Setter
@NoArgsConstructor
public class CreatorVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    @Column(name = "document_number_masked", nullable = false, length = 30)
    private String documentNumberMasked;

    @Nationalized
    @Column(name = "document_file_url", length = 500)
    private String documentFileUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private KycStatus status;

    @Column(name = "submitted_at", insertable = false, updatable = false)
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Nationalized
    @Column(length = 500)
    private String remarks;
}
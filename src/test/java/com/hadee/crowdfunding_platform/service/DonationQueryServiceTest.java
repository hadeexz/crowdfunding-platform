package com.hadee.crowdfunding_platform.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.hadee.crowdfunding_platform.dto.CampaignDonorResponse;
import com.hadee.crowdfunding_platform.dto.MyDonationResponse;
import com.hadee.crowdfunding_platform.entity.Campaign;
import com.hadee.crowdfunding_platform.entity.Donation;
import com.hadee.crowdfunding_platform.entity.PaymentTransaction;
import com.hadee.crowdfunding_platform.entity.User;
import com.hadee.crowdfunding_platform.entity.enums.CampaignStatus;
import com.hadee.crowdfunding_platform.entity.enums.DonationStatus;
import com.hadee.crowdfunding_platform.entity.enums.PaymentStatus;
import com.hadee.crowdfunding_platform.exception.ResourceNotFoundException;
import com.hadee.crowdfunding_platform.repository.CampaignRepository;
import com.hadee.crowdfunding_platform.repository.DonationRepository;
import com.hadee.crowdfunding_platform.repository.PaymentTransactionRepository;

class DonationQueryServiceTest {

    private static final Long CREATOR_ID = 1L;

    private DonationRepository donationRepo;
    private PaymentTransactionRepository txRepo;
    private CampaignRepository campaignRepo;
    private DonationQueryService service;

    @BeforeEach
    void setUp() {
        donationRepo = mock(DonationRepository.class);
        txRepo = mock(PaymentTransactionRepository.class);
        campaignRepo = mock(CampaignRepository.class);
        service = new DonationQueryService(donationRepo, txRepo, campaignRepo);
    }

    private Campaign campaign(Long id, CampaignStatus status) {
        User creator = new User();
        creator.setId(CREATOR_ID);

        Campaign c = new Campaign();
        c.setId(id);
        c.setTitle("Help Asha");
        c.setStatus(status);
        c.setCreator(creator);
        return c;
    }

    private Donation donation(Long id, Campaign c, String amount, boolean anonymous,
                              DonationStatus status, String donorName) {
        User donor = new User();
        donor.setFullName(donorName);

        Donation d = new Donation();
        d.setId(id);
        d.setCampaign(c);
        d.setDonor(donor);
        d.setAmount(new BigDecimal(amount));
        d.setAnonymous(anonymous);
        d.setStatus(status);
        d.setCompletedAt(LocalDateTime.now());
        return d;
    }

    private <T> Page<T> pageOf(List<T> items) {
        return new PageImpl<>(items, PageRequest.of(0, 12), items.size());
    }

    @Test
    void invalid_page_or_size_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> service.listMine(7L, null, 0, 51));
        assertThrows(IllegalArgumentException.class, () -> service.listMine(7L, null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.listMine(7L, null, -1, 12));
    }

    @Test
    void my_donations_show_campaign_title_and_refund_note() {
        Donation d = donation(11L, campaign(5L, CampaignStatus.ACTIVE), "500", false,
                DonationStatus.FAILED, "Me");
        when(donationRepo.findPageByDonorId(eq(7L), any(Pageable.class))).thenReturn(pageOf(List.of(d)));

        PaymentTransaction tx = new PaymentTransaction();
        tx.setDonation(d);
        tx.setStatus(PaymentStatus.REFUND_INITIATED);
        when(txRepo.findByDonationIdIn(List.of(11L))).thenReturn(List.of(tx));

        MyDonationResponse r = service.listMine(7L, null, 0, 12).getContent().get(0);

        assertEquals("Help Asha", r.campaignTitle());
        assertEquals(PaymentStatus.REFUND_INITIATED, r.paymentStatus());
        assertNotNull(r.note());
    }

    @Test
    void completed_donation_has_no_note() {
        Donation d = donation(12L, campaign(5L, CampaignStatus.ACTIVE), "100", false,
                DonationStatus.COMPLETED, "Me");
        when(donationRepo.findPageByDonorId(eq(7L), any(Pageable.class))).thenReturn(pageOf(List.of(d)));

        PaymentTransaction tx = new PaymentTransaction();
        tx.setDonation(d);
        tx.setStatus(PaymentStatus.SUCCESS);
        when(txRepo.findByDonationIdIn(List.of(12L))).thenReturn(List.of(tx));

        MyDonationResponse r = service.listMine(7L, null, 0, 12).getContent().get(0);

        assertEquals(PaymentStatus.SUCCESS, r.paymentStatus());
        assertNull(r.note());
    }

    @Test
    void status_filter_uses_the_filtered_query() {
        when(donationRepo.findPageByDonorIdAndStatus(eq(7L), eq(DonationStatus.COMPLETED), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.listMine(7L, DonationStatus.COMPLETED, 0, 12);

        verify(donationRepo).findPageByDonorIdAndStatus(eq(7L), eq(DonationStatus.COMPLETED), any(Pageable.class));
        verify(donationRepo, never()).findPageByDonorId(any(), any());
    }

    @Test
    void empty_page_does_not_query_transactions() {
        when(donationRepo.findPageByDonorId(eq(7L), any(Pageable.class))).thenReturn(Page.empty());

        service.listMine(7L, null, 0, 12);

        verify(txRepo, never()).findByDonationIdIn(any());
    }

    @Test
    void anonymous_donor_name_is_hidden_in_the_supporters_list() {
        Campaign c = campaign(9L, CampaignStatus.ACTIVE);
        Donation secret = donation(1L, c, "100", true, DonationStatus.COMPLETED, "Secret Person");
        Donation open = donation(2L, c, "200", false, DonationStatus.COMPLETED, "Asha Khan");

        when(campaignRepo.findByIdWithCreator(9L)).thenReturn(Optional.of(c));
        when(donationRepo.findPageByCampaignIdAndStatus(eq(9L), eq(DonationStatus.COMPLETED), any(Pageable.class)))
                .thenReturn(pageOf(List.of(secret, open)));

        List<CampaignDonorResponse> rows = service.listForCampaign(9L, null, false, 0, 12).getContent();

        assertEquals("Anonymous", rows.get(0).donorName());
        assertTrue(rows.get(0).anonymous());
        assertEquals("Asha Khan", rows.get(1).donorName());
        assertFalse(rows.toString().contains("Secret Person"));
    }

    @Test
    void suspended_campaign_is_hidden_from_the_public() {
        when(campaignRepo.findByIdWithCreator(9L))
                .thenReturn(Optional.of(campaign(9L, CampaignStatus.SUSPENDED)));

        assertThrows(ResourceNotFoundException.class,
                () -> service.listForCampaign(9L, null, false, 0, 12));
        assertThrows(ResourceNotFoundException.class,
                () -> service.listForCampaign(9L, 99L, false, 0, 12));
    }

    @Test
    void owner_and_admin_can_see_a_hidden_campaign() {
        when(campaignRepo.findByIdWithCreator(9L))
                .thenReturn(Optional.of(campaign(9L, CampaignStatus.SUSPENDED)));
        when(donationRepo.findPageByCampaignIdAndStatus(eq(9L), eq(DonationStatus.COMPLETED), any(Pageable.class)))
                .thenReturn(Page.empty());

        assertDoesNotThrow(() -> service.listForCampaign(9L, CREATOR_ID, false, 0, 12));
        assertDoesNotThrow(() -> service.listForCampaign(9L, 99L, true, 0, 12));
    }

    @Test
    void unknown_campaign_is_not_found() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.listForCampaign(404L, null, false, 0, 12));
    }
}
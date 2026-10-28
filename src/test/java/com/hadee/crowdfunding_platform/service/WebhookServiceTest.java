package com.hadee.crowdfunding_platform.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import com.hadee.crowdfunding_platform.entity.WebhookEvent;
import com.hadee.crowdfunding_platform.entity.enums.WebhookProcessingStatus;
import com.hadee.crowdfunding_platform.exception.WebhookProcessingException;
import com.hadee.crowdfunding_platform.payment.PaymentGateway;
import com.hadee.crowdfunding_platform.repository.WebhookEventRepository;

class WebhookServiceTest {

    private static final String BODY = "{\"event\":\"payment.captured\"}";

    private WebhookEventRepository repo;
    private PaymentGateway gateway;
    private PaymentEventProcessor processor;
    private WebhookService service;

    @BeforeEach
    void setUp() {
        repo = mock(WebhookEventRepository.class);
        gateway = mock(PaymentGateway.class);
        processor = mock(PaymentEventProcessor.class);
        service = new WebhookService(repo, gateway, processor);
    }

    private byte[] body() {
        return BODY.getBytes(StandardCharsets.UTF_8);
    }

    private WebhookEvent existing(Long id, WebhookProcessingStatus status) {
        WebhookEvent e = new WebhookEvent();
        e.setId(id);
        e.setProcessingStatus(status);
        return e;
    }

    @Test
    void invalid_signature_is_rejected_and_nothing_happens() {
        when(gateway.verifyWebhookSignature(BODY, "bad")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.receive(body(), "bad", "evt_1"));
        verify(repo, never()).save(any());
        verify(processor, never()).process(any());
    }

    @Test
    void missing_event_id_is_rejected() {
        when(gateway.verifyWebhookSignature(BODY, "good")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.receive(body(), "good", "  "));
        verify(repo, never()).save(any());
    }

    @Test
    void body_that_is_not_json_is_rejected() {
        when(gateway.verifyWebhookSignature("not json", "good")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.receive(
                "not json".getBytes(StandardCharsets.UTF_8), "good", "evt_1"));
        verify(repo, never()).save(any());
    }

    @Test
    void empty_body_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> service.receive(new byte[0], "good", "evt_1"));
        assertThrows(IllegalArgumentException.class, () -> service.receive(null, "good", "evt_1"));
    }

    @Test
    void new_event_is_saved_as_received_and_then_processed() {
        when(gateway.verifyWebhookSignature(BODY, "good")).thenReturn(true);

        assertTrue(service.receive(body(), "good", "evt_1"));

        ArgumentCaptor<WebhookEvent> captor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(repo).save(captor.capture());
        WebhookEvent saved = captor.getValue();
        assertEquals("evt_1", saved.getRazorpayEventId());
        assertEquals("payment.captured", saved.getEventType());
        assertEquals(BODY, saved.getPayload());
        assertTrue(saved.isSignatureValid());
        assertEquals(WebhookProcessingStatus.RECEIVED, saved.getProcessingStatus());
        verify(processor).process(any());
    }

    @Test
    void finished_duplicate_is_ignored_and_not_processed_again() {
        when(gateway.verifyWebhookSignature(BODY, "good")).thenReturn(true);
        when(repo.findByRazorpayEventId("evt_1"))
                .thenReturn(Optional.of(existing(5L, WebhookProcessingStatus.PROCESSED)));

        assertFalse(service.receive(body(), "good", "evt_1"));
        verify(repo, never()).save(any());
        verify(processor, never()).process(any());
    }

    @Test
    void ignored_duplicate_is_ignored() {
        when(gateway.verifyWebhookSignature(BODY, "good")).thenReturn(true);
        when(repo.findByRazorpayEventId("evt_1"))
                .thenReturn(Optional.of(existing(5L, WebhookProcessingStatus.IGNORED)));

        assertFalse(service.receive(body(), "good", "evt_1"));
        verify(processor, never()).process(any());
    }

    @Test
    void failed_event_is_retried_when_the_gateway_sends_it_again() {
        when(gateway.verifyWebhookSignature(BODY, "good")).thenReturn(true);
        when(repo.findByRazorpayEventId("evt_1"))
                .thenReturn(Optional.of(existing(5L, WebhookProcessingStatus.FAILED)));

        assertTrue(service.receive(body(), "good", "evt_1"));
        verify(repo, never()).save(any());
        verify(processor).process(5L);
    }

    @Test
    void processing_error_marks_event_failed_and_tells_gateway_to_retry() {
        when(gateway.verifyWebhookSignature(BODY, "good")).thenReturn(true);
        doThrow(new IllegalStateException("boom")).when(processor).process(any());

        assertThrows(WebhookProcessingException.class, () -> service.receive(body(), "good", "evt_1"));
        verify(processor).markFailed(any(), contains("boom"));
    }

    @Test
    void duplicate_found_after_insert_race_is_ignored() {
        when(gateway.verifyWebhookSignature(BODY, "good")).thenReturn(true);
        // first check says "new", then the database unique rule says "already there"
        when(repo.findByRazorpayEventId("evt_1")).thenReturn(
                Optional.empty(),
                Optional.of(existing(5L, WebhookProcessingStatus.RECEIVED)));
        when(repo.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertFalse(service.receive(body(), "good", "evt_1"));
        verify(processor, never()).process(any());
    }
}
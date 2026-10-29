package com.hadee.crowdfunding_platform.service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.dao.ConcurrencyFailureException;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.hadee.crowdfunding_platform.entity.WebhookEvent;
import com.hadee.crowdfunding_platform.entity.enums.WebhookProcessingStatus;
import com.hadee.crowdfunding_platform.exception.WebhookProcessingException;
import com.hadee.crowdfunding_platform.payment.PaymentGateway;
import com.hadee.crowdfunding_platform.repository.WebhookEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private static final int MAX_BODY_BYTES = 200_000;
    private static final int MAX_PROCESS_ATTEMPTS = 3;

    private final WebhookEventRepository webhookEventRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentEventProcessor eventProcessor;

    // NOT @Transactional: storing, processing and marking a failure are separate small
    // transactions, so a processing error can never undo the stored event.
    /** Returns true if this call handled the event, false if it was a finished duplicate. */
    public boolean receive(byte[] body, String signature, String eventId) {

        if (body == null || body.length == 0) {
            throw new IllegalArgumentException("Empty webhook body");
        }
        if (body.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Webhook body is too large");
        }
        String rawBody = new String(body, StandardCharsets.UTF_8);

        // 1) Who is knocking? Check the signature before anything else.
        if (signature == null || !paymentGateway.verifyWebhookSignature(rawBody, signature)) {
            log.warn("Rejected webhook with invalid signature");
            throw new IllegalArgumentException("Invalid webhook signature");
        }

        if (eventId == null || eventId.isBlank() || eventId.length() > 100) {
            throw new IllegalArgumentException("Missing or invalid event id");
        }

        String eventType;
        try {
            eventType = new JSONObject(rawBody).optString("event", "");
        } catch (JSONException e) {
            throw new IllegalArgumentException("Webhook body is not valid JSON");
        }
        if (eventType.isBlank() || eventType.length() > 60) {
            throw new IllegalArgumentException("Missing or invalid event type");
        }

        // 2) Seen this event before?
        Optional<WebhookEvent> existing = webhookEventRepository.findByRazorpayEventId(eventId);
        if (existing.isPresent()) {
            WebhookEvent old = existing.get();
            WebhookProcessingStatus status = old.getProcessingStatus();
            if (status == WebhookProcessingStatus.PROCESSED || status == WebhookProcessingStatus.IGNORED) {
                log.info("Duplicate webhook ignored: {}", eventId);
                return false;
            }
            // RECEIVED or FAILED: the gateway is retrying, so try again
            log.info("Retrying webhook {}", eventId);
            processSafely(old.getId());
            return true;
        }

        // 3) Store it exactly as received
        WebhookEvent event = new WebhookEvent();
        event.setRazorpayEventId(eventId);
        event.setEventType(eventType);
        event.setPayload(rawBody);
        event.setSignatureValid(true);

        try {
            webhookEventRepository.save(event);
        } catch (DataIntegrityViolationException e) {
            // two deliveries arrived at the same moment: the unique rule caught the second one
            if (webhookEventRepository.findByRazorpayEventId(eventId).isPresent()) {
                log.info("Duplicate webhook ignored (race): {}", eventId);
                return false;
            }
            throw e;
        }

        log.info("Webhook stored: {} ({})", eventId, eventType);

        // 4) Act on it
        processSafely(event.getId());
        return true;
    }

        private void processSafely(Long eventRowId) {
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= MAX_PROCESS_ATTEMPTS; attempt++) {
            try {
                eventProcessor.process(eventRowId);
                return;
            } catch (ConcurrencyFailureException e) {
                // Another payment changed the same campaign at the same moment.
                // Nothing was saved, so reload everything and try again.
                log.warn("Concurrent update while processing event row {} (attempt {})",
                        eventRowId, attempt);
                lastError = e;
            } catch (RuntimeException e) {
                lastError = e;
                break;
            }
        }

        log.error("Webhook processing failed for event row {}", eventRowId, lastError);
        String detail = lastError.getClass().getSimpleName()
                + (lastError.getMessage() == null ? "" : ": " + lastError.getMessage());
        eventProcessor.markFailed(eventRowId, detail);
        throw new WebhookProcessingException("Webhook processing failed. It will be retried.");
    }
}
package com.hadee.crowdfunding_platform.service;

import java.nio.charset.StandardCharsets;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.hadee.crowdfunding_platform.entity.WebhookEvent;
import com.hadee.crowdfunding_platform.payment.PaymentGateway;
import com.hadee.crowdfunding_platform.repository.WebhookEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private static final int MAX_BODY_BYTES = 200_000;

    private final WebhookEventRepository webhookEventRepository;
    private final PaymentGateway paymentGateway;

    // NOT @Transactional: each repository call is its own small transaction, so a
    // duplicate-key error cannot poison anything else.
    /** Returns true if the event is new and was stored, false if it was a duplicate. */
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

        // 2) Seen this event before? Then do nothing.
        if (webhookEventRepository.existsByRazorpayEventId(eventId)) {
            log.info("Duplicate webhook ignored: {}", eventId);
            return false;
        }

        // 3) Store it exactly as received (processing happens in Part 6)
        WebhookEvent event = new WebhookEvent();
        event.setRazorpayEventId(eventId);
        event.setEventType(eventType);
        event.setPayload(rawBody);
        event.setSignatureValid(true);

        try {
            webhookEventRepository.save(event);
        } catch (DataIntegrityViolationException e) {
            // two deliveries arrived at the same moment: the unique rule caught the second one
            if (webhookEventRepository.existsByRazorpayEventId(eventId)) {
                log.info("Duplicate webhook ignored (race): {}", eventId);
                return false;
            }
            throw e;
        }

        log.info("Webhook stored: {} ({})", eventId, eventType);
        return true;
    }
}
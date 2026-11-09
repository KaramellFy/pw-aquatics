package com.pwaquatics.shop.content.application;

/** Command for {@link ContactService#submit}. Values are already validated and trimmed. */
public record SubmitContactMessage(
        String name, String email, String subject, String message, String orderNumber, String honeypot) {}

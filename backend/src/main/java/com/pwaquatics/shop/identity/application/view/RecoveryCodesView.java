package com.pwaquatics.shop.identity.application.view;

import java.util.List;

/** New one-time recovery codes; shown once, only their hashes are stored. */
public record RecoveryCodesView(List<String> recoveryCodes) {}

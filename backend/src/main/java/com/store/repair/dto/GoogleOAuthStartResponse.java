package com.store.repair.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GoogleOAuthStartResponse {
    private final String authUrl;
    private final String state;
}

package com.store.repair.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RemoteBackupFileResponse {

    private final String fileId;
    private final String fileName;
    private final long sizeBytes;
    private final String createdAt;
    private final String modifiedAt;
}

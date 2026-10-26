package com.impactgraph.dto;

import com.impactgraph.entity.FindingStatus;
import java.util.UUID;

public record VerificationResultDto(
        UUID findingId,
        FindingStatus previousStatus,
        FindingStatus newStatus,
        boolean verifiedResolved,
        String verificationMessage,
        String executedCheck
) {}

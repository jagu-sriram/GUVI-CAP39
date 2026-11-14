
package com.sriram.cap39.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DefectRequest(

        @NotBlank
        String defectId,

        @NotBlank
        String batchId,

        String inspectionId,

        @NotBlank
        String defectCategory,

        @NotBlank
        String defectCode,

        @NotNull
        @PositiveOrZero
        BigDecimal affectedQuantity,

        @NotNull
        @Positive
        BigDecimal inspectedQuantity,

        @NotNull
        LocalDateTime detectedAt,

        @NotBlank
        String severity,

        @NotBlank
        String registeredBy

) {}

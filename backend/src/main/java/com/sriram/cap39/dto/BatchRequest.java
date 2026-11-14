
package com.sriram.cap39.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record BatchRequest(

        @NotBlank
        String batchId,

        @NotBlank
        String productId,

        @NotBlank
        String productionLine,

        @NotBlank
        String shift,

        @NotNull
        @PositiveOrZero
        BigDecimal plannedQuantity,

        @NotNull
        @PositiveOrZero
        BigDecimal producedQuantity,

        @NotNull
        LocalDate productionDate,

        @NotBlank
        String status,

        @NotBlank
        String registeredBy

) {}

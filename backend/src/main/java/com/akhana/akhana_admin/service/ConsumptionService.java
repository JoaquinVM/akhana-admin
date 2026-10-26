package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.ConsumptionRequest;
import com.akhana.akhana_admin.dto.ConsumptionResponse;
import com.akhana.akhana_admin.dto.VoidConsumptionRequest;
import com.akhana.akhana_admin.model.ConsumptionStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ConsumptionService {

    List<ConsumptionResponse> getConsumptions(LocalDate from, LocalDate to, UUID productId, ConsumptionStatus status,
                                              String search);

    ConsumptionResponse getConsumptionById(UUID id);

    ConsumptionResponse registerConsumption(ConsumptionRequest request, String username);

    ConsumptionResponse voidConsumption(UUID id, VoidConsumptionRequest request, String username);
}

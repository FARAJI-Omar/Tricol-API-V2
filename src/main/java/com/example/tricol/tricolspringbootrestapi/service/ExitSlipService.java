package com.example.tricol.tricolspringbootrestapi.service;

import java.util.List;

import com.example.tricol.tricolspringbootrestapi.dto.request.CreateExitSlipRequest;
import com.example.tricol.tricolspringbootrestapi.dto.response.ExitSlipResponse;
import com.example.tricol.tricolspringbootrestapi.enums.ExitSlipStatus;

public interface ExitSlipService {
ExitSlipResponse createExitSlip(CreateExitSlipRequest request);
ExitSlipResponse validateExitSlip(Long id);
ExitSlipResponse cancelExitSlip(Long id);
ExitSlipResponse getExitSlip(Long id);
List<ExitSlipResponse> getAllExitSlips();
List<ExitSlipResponse> getExitSlipsByStatus(ExitSlipStatus status);
List<ExitSlipResponse> getExitSlipsByWorkshop(String workshop);

double calculateStockValue(Long productId);
} 

package com.demo.example.mstransfercontrol.controller;

import com.demo.example.mstransfercontrol.dto.TransferRequest;
import com.demo.example.mstransfercontrol.dto.TransferResponse;
import com.demo.example.mstransfercontrol.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferControllerTest {

    @Mock
    private TransferService transferService;

    @InjectMocks
    private TransferController transferController;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(transferController).build();
    }

    @Test
    void shouldDelegateTransferRequestAndReturnApprovedResponse() {
        // Arrange
        TransferResponse response = TransferResponse.builder()
                .status("APPROVED")
                .sourceAccount("account-01")
                .targetAccount("account-02")
                .amount(new BigDecimal("2000.00"))
                .build();
        when(transferService.validateAndTransfer(any(TransferRequest.class)))
                .thenReturn(Mono.just(response));

        // Act
        webTestClient.post()
                .uri("/api/transfers/control")
                .header("Content-Type", "application/json")
                .bodyValue("""
                        {
                          "customerId": "customer-01",
                          "sourceAccount": "account-01",
                          "targetAccount": "account-02",
                          "amount": 2000.00
                        }
                        """)
                .exchange()

                // Assert
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("APPROVED")
                .jsonPath("$.sourceAccount").isEqualTo("account-01")
                .jsonPath("$.targetAccount").isEqualTo("account-02")
                .jsonPath("$.amount").isEqualTo(2000.00);

        ArgumentCaptor<TransferRequest> requestCaptor = ArgumentCaptor.forClass(TransferRequest.class);
        verify(transferService, times(1)).validateAndTransfer(requestCaptor.capture());
        TransferRequest actualRequest = requestCaptor.getValue();
        assertEquals("customer-01", actualRequest.getCustomerId());
        assertEquals("account-01", actualRequest.getSourceAccount());
        assertEquals("account-02", actualRequest.getTargetAccount());
        assertEquals(new BigDecimal("2000.00"), actualRequest.getAmount());
    }
}

package com.demo.example.mstransfercontrol.service.impl;

import com.demo.example.mstransfercontrol.dto.TransferRequest;
import com.demo.example.mstransfercontrol.dto.TransferResponse;
import com.demo.example.mstransfercontrol.entity.Account;
import com.demo.example.mstransfercontrol.entity.Transfer;
import com.demo.example.mstransfercontrol.mapper.TransferMapper;
import com.demo.example.mstransfercontrol.mapper.impl.TransferMapperImpl;
import com.demo.example.mstransfercontrol.repository.AccountRepository;
import com.demo.example.mstransfercontrol.repository.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransferRepository transferRepository;

    @Spy
    private TransferMapper transferMapper = new TransferMapperImpl();

    @InjectMocks
    private TransferServiceImpl transferService;

    private TransferRequest request;

    @BeforeEach
    void setUp() {
        request = TransferRequest.builder()
                .customerId("customer-01")
                .sourceAccount("account-01")
                .targetAccount("account-02")
                .amount(new BigDecimal("2000.00"))
                .build();
    }

    @Test
    void shouldApproveAndSaveTransferWhenAllRulesAreSatisfied() {
        // Arrange
        Account activeAccount = Account.builder()
                .accountNumber(request.getSourceAccount())
                .status("ACTIVE")
                .build();
        Transfer savedTransfer = Transfer.builder().id(1L).build();
        when(accountRepository.findByAccountNumber(request.getSourceAccount()))
                .thenReturn(Mono.just(activeAccount));
        when(transferRepository.totalTransferredToday(anyString(), any(), any()))
                .thenReturn(Mono.just(new BigDecimal("15000.00")));
        when(transferRepository.save(any(Transfer.class))).thenReturn(Mono.just(savedTransfer));

        // Act
        Mono<TransferResponse> result = transferService.validateAndTransfer(request);

        // Assert
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals("APPROVED", response.getStatus());
                    assertEquals(request.getSourceAccount(), response.getSourceAccount());
                    assertEquals(request.getTargetAccount(), response.getTargetAccount());
                    assertEquals(request.getAmount(), response.getAmount());
                })
                .verifyComplete();
        verify(accountRepository).findByAccountNumber(request.getSourceAccount());
        verify(transferRepository).totalTransferredToday(anyString(), any(), any());
        verify(transferRepository).save(any(Transfer.class));
        verify(transferMapper).toTransferEntity(request);
        verify(transferMapper).toApprovedResponse(request);
    }

    @Test
    void shouldFailWhenSourceAccountDoesNotExist() {
        // Arrange
        when(accountRepository.findByAccountNumber(request.getSourceAccount())).thenReturn(Mono.empty());

        // Act
        Mono<TransferResponse> result = transferService.validateAndTransfer(request);

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(error -> error instanceof NoSuchElementException
                        && "Cuenta origen no existe".equals(error.getMessage()))
                .verify();
        verify(accountRepository).findByAccountNumber(request.getSourceAccount());
        verifyNoInteractions(transferRepository, transferMapper);
    }

    @Test
    void shouldFailWhenSourceAccountIsNotActive() {
        // Arrange
        Account inactiveAccount = Account.builder()
                .accountNumber(request.getSourceAccount())
                .status("BLOCKED")
                .build();
        when(accountRepository.findByAccountNumber(request.getSourceAccount()))
                .thenReturn(Mono.just(inactiveAccount));

        // Act
        Mono<TransferResponse> result = transferService.validateAndTransfer(request);

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && "Cuenta origen no esta activa".equals(error.getMessage()))
                .verify();
        verify(accountRepository).findByAccountNumber(request.getSourceAccount());
        verifyNoInteractions(transferRepository, transferMapper);
    }

    @ParameterizedTest
    @MethodSource("invalidAmounts")
    void shouldFailWhenAmountIsNotGreaterThanZero(BigDecimal invalidAmount) {
        // Arrange
        request.setAmount(invalidAmount);

        // Act
        Mono<TransferResponse> result = transferService.validateAndTransfer(request);

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(error -> error instanceof IllegalArgumentException
                        && "Monto debe ser mayor a cero".equals(error.getMessage()))
                .verify();
        verifyNoInteractions(accountRepository, transferRepository, transferMapper);
    }

    @Test
    void shouldFailWhenDailyLimitWouldBeExceeded() {
        // Arrange
        Account activeAccount = Account.builder()
                .accountNumber(request.getSourceAccount())
                .status("ACTIVE")
                .build();
        when(accountRepository.findByAccountNumber(request.getSourceAccount()))
                .thenReturn(Mono.just(activeAccount));
        when(transferRepository.totalTransferredToday(anyString(), any(), any()))
                .thenReturn(Mono.just(new BigDecimal("15000.01")));

        // Act
        Mono<TransferResponse> result = transferService.validateAndTransfer(request);

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(error -> error instanceof IllegalArgumentException
                        && "Limite diario excedido".equals(error.getMessage()))
                .verify();
        verify(accountRepository).findByAccountNumber(request.getSourceAccount());
        verify(transferRepository).totalTransferredToday(anyString(), any(), any());
        verify(transferRepository, never()).save(any(Transfer.class));
        verifyNoInteractions(transferMapper);
    }

    private static Stream<BigDecimal> invalidAmounts() {
        return Stream.of(null, BigDecimal.ZERO, new BigDecimal("-0.01"));
    }
}

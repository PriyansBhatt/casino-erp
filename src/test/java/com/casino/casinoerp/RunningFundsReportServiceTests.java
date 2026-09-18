package com.casino.casinoerp;
import com.casino.casinoerp.entity.BusinessDate;
import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunningFundsReportServiceTests {
    final BusinessDateRepository dates=mock(BusinessDateRepository.class);
    final BusinessDateService lifecycle=mock(BusinessDateService.class);
    final ManagementReportReadRepository reads=mock(ManagementReportReadRepository.class);
    final CurrentUserRoleService roles=mock(CurrentUserRoleService.class);
    final RunningFundsReportService service=new RunningFundsReportService(dates,lifecycle,reads,roles);
    final LocalDate date=LocalDate.of(2026,9,2);
    BusinessDate selected;
    @BeforeEach void setup(){
        selected=new BusinessDate();selected.setBusinessDate(date);selected.setStatus("CLOSED");
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.DIRECTOR));
        when(dates.findByBusinessDate(date)).thenReturn(Optional.of(selected));
        when(reads.guests(date)).thenReturn(new ManagementReportReadRepository.Guests(0,0));
        var tender=Map.of("CASH",new RunningFundsReportResponse.Tender(0,BigDecimal.ZERO));
        when(reads.payments(date)).thenReturn(new ManagementReportReadRepository.Payments(tender,tender));
        when(reads.payouts(date)).thenReturn(new ManagementReportReadRepository.Payouts(0,BigDecimal.ZERO));
        when(reads.gaming(date)).thenReturn(new ManagementReportReadRepository.Gaming(BigDecimal.ZERO,BigDecimal.ZERO));
        when(reads.reconciliations(date)).thenReturn(new ManagementReportReadRepository.Reconciliations(0,0,null));
    }
    @Test void historicalDateDoesNotRequireOpenDateAndKeepsKathmanduWindow(){
        var report=service.getReport(date);assertThat(report.status()).isEqualTo("CLOSED");
        assertThat(report.windowStart().toString()).isEqualTo("2026-09-02T09:00+05:45");
        assertThat(report.windowEnd().toString()).isEqualTo("2026-09-03T09:00+05:45");
        assertThat(report.sessionEntries()).isZero();assertThat(report.aggregateSubmittedVariance()).isNull();
        verifyNoInteractions(lifecycle);
        verify(reads).guests(date);verify(reads).payments(date);verify(reads).payouts(date);verify(reads).gaming(date);verify(reads).reconciliations(date);verifyNoMoreInteractions(reads);
    }
    @Test void defaultUsesOnlyCurrentOpenDate(){when(lifecycle.getCurrentOpenBusinessDate()).thenReturn(Optional.of(selected));assertThat(service.getReport(null).businessDate()).isEqualTo(date);verifyNoInteractions(dates);}
    @Test void missingOpenAndUnknownExplicitDateFailWithoutReadingTotals(){
        assertThatThrownBy(()->service.getReport(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.getReport(date.plusDays(1))).hasMessageContaining("not found");verifyNoInteractions(reads);
    }
    @Test void usesPersistedPayoutAndGamingAggregatesWithoutEligibilityOrCustody(){
        when(reads.payouts(date)).thenReturn(new ManagementReportReadRepository.Payouts(2,new BigDecimal("123.45")));
        when(reads.gaming(date)).thenReturn(new ManagementReportReadRepository.Gaming(new BigDecimal("100"),new BigDecimal("70")));
        when(reads.reconciliations(date)).thenReturn(new ManagementReportReadRepository.Reconciliations(1,2,new BigDecimal("-8")));
        var r=service.getReport(date);assertThat(r.losingReturnAmountPaid()).isEqualByComparingTo("123.45");assertThat(r.customerWins()).isEqualByComparingTo("100");assertThat(r.customerLosses()).isEqualByComparingTo("70");assertThat(r.aggregateSubmittedVariance()).isEqualByComparingTo("-8");assertThat(r.reopenedCount()).isEqualTo(2);
    }
    @Test void rejectsEveryNonManagementRole(){for(Role role:Role.values())if(role!=Role.DIRECTOR&&role!=Role.SUPER_ADMIN){when(roles.getCurrentRole()).thenReturn(Optional.of(role));assertThatThrownBy(()->service.getReport(date)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);}verifyNoInteractions(dates,reads,lifecycle);}
    @Test void boundsPagesAndReturnsLookahead(){
        var row=new RunningFundsReconciliationResponse(UUID.randomUUID(),date,UUID.randomUUID(),null,null,"REOPENED",null,"REOPENED_SAVED_RECORD",BigDecimal.TEN,BigDecimal.TEN,null,null,date.atTime(9,0),date.atTime(10,0));
        when(reads.rows(date,0,1)).thenReturn(List.of(row,row));
        var result=service.reconciliations(date,0,1);assertThat(result.items()).hasSize(1);assertThat(result.hasNext()).isTrue();assertThat(result.items().getFirst().calculationBasis()).isEqualTo("REOPENED_SAVED_RECORD");
        for(int size:List.of(0,101))assertThatThrownBy(()->service.reconciliations(date,0,size)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.reconciliations(date,-1,50)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void moneySerializesAsExactDecimalStringsAndAbsentVarianceAsNull() throws Exception {
        var exact=new BigDecimal("12345678901234567.89");
        when(reads.gaming(date)).thenReturn(new ManagementReportReadRepository.Gaming(exact,BigDecimal.ZERO));
        var tender=Map.of("CASH",new RunningFundsReportResponse.Tender(1,exact));
        when(reads.payments(date)).thenReturn(new ManagementReportReadRepository.Payments(tender,tender));
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var json=mapper.readTree(mapper.writeValueAsString(service.getReport(date)));
        assertThat(json.get("customerWins").isTextual()).isTrue();
        assertThat(json.get("customerWins").asText()).isEqualTo("12345678901234567.89");
        assertThat(json.path("buyIn").path("CASH").path("amount").asText()).isEqualTo("12345678901234567.89");
        assertThat(json.get("aggregateSubmittedVariance").isNull()).isTrue();
        assertThat(json.has("outstandingCustomerChipPosition")).isFalse();assertThat(json.has("unresolvedCashiers")).isFalse();
    }
}

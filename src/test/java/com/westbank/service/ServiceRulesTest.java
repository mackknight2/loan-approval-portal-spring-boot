package com.westbank.service;

import com.westbank.entity.LoanFile;
import com.westbank.entity.LoanFileStatus;
import com.westbank.entity.Role;
import com.westbank.entity.Staff;
import com.westbank.repository.LoanFileRepository;
import com.westbank.repository.StaffRepository;
import com.westbank.ws.business.creditworthiness._2019._01.CreditWorthinessRequest;
import com.westbank.ws.client.callbackloanapproval._2019._01.CallbackLoanApprovalRequest;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests (no Spring context) for service-layer business rules.
 */
public class ServiceRulesTest {

    @Test
    public void updateLoanFile_setsStatusAndDescription() {
        LoanFileRepository repository = mock(LoanFileRepository.class);
        LoanFile loanFile = new LoanFile();
        when(repository.findById("LF1")).thenReturn(Optional.of(loanFile));
        CallbackLoanApprovalRequest request = new CallbackLoanApprovalRequest();
        request.setLoanFileId("LF1");
        request.setStatus("APPROVED");
        request.setDescription("ok");

        new LoanFileService(repository).updateLoanFile(request);

        assertThat(loanFile.getStatus()).isEqualTo(LoanFileStatus.APPROVED);
        assertThat(loanFile.getDescription()).isEqualTo("ok");
        verify(repository).save(loanFile);
    }

    @Test
    public void updateLoanFile_unknownStatusFallsBackToInitialized() {
        LoanFileRepository repository = mock(LoanFileRepository.class);
        LoanFile loanFile = new LoanFile();
        loanFile.setStatus(LoanFileStatus.REJECTED);
        when(repository.findById("LF1")).thenReturn(Optional.of(loanFile));
        CallbackLoanApprovalRequest request = new CallbackLoanApprovalRequest();
        request.setLoanFileId("LF1");
        request.setStatus("NOT_A_STATUS");

        new LoanFileService(repository).updateLoanFile(request);

        assertThat(loanFile.getStatus()).isEqualTo(LoanFileStatus.INITIALIZED);
    }

    @Test
    public void updateLoanFile_unknownLoanFileIsIgnored() {
        LoanFileRepository repository = mock(LoanFileRepository.class);
        when(repository.findById(any())).thenReturn(Optional.empty());
        CallbackLoanApprovalRequest request = new CallbackLoanApprovalRequest();
        request.setLoanFileId("nope");

        new LoanFileService(repository).updateLoanFile(request);

        verify(repository, never()).save(any());
    }

    @Test
    public void checkCreditWorthiness_isRandomAndMostlyTrue_currentBehaviour() {
        // Current behaviour (placeholder in the app): roughly 97% of checks pass at random.
        LoanFileService service = new LoanFileService(mock(LoanFileRepository.class));
        int passed = 0;
        for (int i = 0; i < 2000; i++) {
            if (service.checkCreditWorthiness(new CreditWorthinessRequest())) passed++;
        }
        assertThat(passed).isBetween(1800, 2000);
    }

    @Test
    public void staffAuthenticate_matchesIdOrEmailAndPlainTextPassword() {
        StaffRepository repository = mock(StaffRepository.class);
        Staff broker = new Staff("broker", "broker@westbank.com.au", "abcd1234");
        broker.setRole(Role.CREDIT_BROKER);
        when(repository.findByIdOrEmail("broker", "broker")).thenReturn(Optional.of(broker));
        when(repository.findByIdOrEmail("ghost", "ghost")).thenReturn(Optional.empty());
        StaffService service = new StaffService(repository);

        assertThat(service.authenticate("broker", "abcd1234")).isSameAs(broker);
        assertThat(service.authenticate("broker", "wrong")).isNull();
        assertThat(service.authenticate("ghost", "abcd1234")).isNull();
    }
}

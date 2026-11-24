package com.westbank.ws.impl;

import com.westbank.AppProperties;
import com.westbank.entity.Customer;
import com.westbank.entity.EstateType;
import com.westbank.entity.LoanFile;
import com.westbank.entity.LoanRisk;
import com.westbank.entity.Role;
import com.westbank.repository.LoanFileRepository;
import com.westbank.service.LoanFileService;
import com.westbank.ws.business.bankinformation._2019._01.BankInformationRequest;
import com.westbank.ws.business.bankinformation._2019._01.BankInformationResponse;
import com.westbank.ws.business.bankprivilege._2019._01.BankPrivilegeRequest;
import com.westbank.ws.business.bankprivilege._2019._01.BankPrivilegeResponse;
import com.westbank.ws.business.creditworthiness._2019._01.CreditWorthinessRequest;
import com.westbank.ws.business.loanapprovalclosing._2019._01.LoanApprovalClosingRequest;
import com.westbank.ws.business.loanrisk._2019._01.LoanRiskRequest;
import com.westbank.ws.business.loanrisk._2019._01.LoanRiskResponse;
import com.westbank.ws.business.loansettlement._2019._01.LoanSettlementRequest;
import com.westbank.ws.business.taskdispatch._2019._01.TaskDispatchRequest;
import com.westbank.ws.client.callbackloanapproval._2019._01.CallbackLoanApprovalRequest;
import com.westbank.ws.client.callbackloancontract._2019._01.CallbackLoanContractRequest;
import com.westbank.ws.test._2019._01.TestRequest;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pins the current behaviour of the hand-written SOAP endpoint implementations.
 */
public class BusinessServicesTest {

    @Test
    public void bankInformation_calculatesMonthlyAmortizedPayment() {
        BankInformationRequest request = new BankInformationRequest();
        request.setLoanAmount(100000.0);
        request.setInterestRate(0.06);
        request.setLoanTerm(30);

        BankInformationResponse response = new BankInformationPortImpl().retrieve(request);

        assertThat(response.getMonthlyPayment()).isCloseTo(599.55, within(0.01));
    }

    @Test
    public void bankInformation_rejectsNullRequest() {
        assertThatThrownBy(() -> new BankInformationPortImpl().retrieve(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void bankPrivilege_customerNamedPowerIsAlwaysClean() {
        BankPrivilegeRequest request = new BankPrivilegeRequest();
        request.setBorrowerLastName("power");
        for (int i = 0; i < 50; i++) {
            BankPrivilegeResponse response = new BankPrivilegePortImpl().verify(request);
            assertThat(response.isRegistered()).isTrue();
            assertThat(response.getNumberOfIncidents()).isEqualTo(0);
            assertThat(response.getNumberOfBanks()).isEqualTo(2);
            assertThat(response.getDescription()).isEqualTo("Bank privilege checking");
        }
    }

    @Test
    public void bankPrivilege_otherCustomersGetRandomResult_currentBehaviour() {
        // Current behaviour: about 91% clean, otherwise 10-19 incidents with 1-10 banks.
        BankPrivilegeRequest request = new BankPrivilegeRequest();
        request.setBorrowerLastName("Smith");
        for (int i = 0; i < 500; i++) {
            BankPrivilegeResponse response = new BankPrivilegePortImpl().verify(request);
            assertThat(response.isRegistered()).isTrue();
            if (response.getNumberOfIncidents() == 0) {
                assertThat(response.getNumberOfBanks()).isEqualTo(2);
            } else {
                assertThat(response.getNumberOfIncidents()).isBetween(10, 19);
                assertThat(response.getNumberOfBanks()).isBetween(1, 10);
            }
        }
    }

    @Test
    public void creditWorthiness_delegatesToLoanFileService() {
        LoanFileService service = mock(LoanFileService.class);
        when(service.checkCreditWorthiness(any())).thenReturn(false);
        CreditWorthinessPortImpl port = new CreditWorthinessPortImpl();
        ReflectionTestUtils.setField(port, "loanFileService", service);

        assertThat(port.check(new CreditWorthinessRequest()).isCreditWorthiness()).isFalse();
    }

    @Test
    public void loanRisk_houseIsHighRisk() {
        LoanRiskResponse response = evaluateRisk(EstateType.HOUSE, "Smith", LoanRisk.HIGH);
        assertThat(response.isHighRisk()).isTrue();
        assertThat(response.getRisk()).isEqualTo("HIGH");
    }

    @Test
    public void loanRisk_houseForCustomerNamedPowerIsLowRisk() {
        LoanRiskResponse response = evaluateRisk(EstateType.HOUSE, "POWER", LoanRisk.LOW);
        assertThat(response.isHighRisk()).isFalse();
        assertThat(response.getRisk()).isEqualTo("LOW");
    }

    @Test
    public void loanRisk_nonHouseIsLowRisk() {
        LoanRiskResponse response = evaluateRisk(EstateType.FLAT, "Smith", LoanRisk.LOW);
        assertThat(response.isHighRisk()).isFalse();
    }

    @Test
    public void loanRisk_unknownLoanFileGivesEmptyResponse() {
        LoanFileRepository repository = mock(LoanFileRepository.class);
        when(repository.findById("nope")).thenReturn(Optional.empty());
        LoanRiskPortImpl port = new LoanRiskPortImpl();
        ReflectionTestUtils.setField(port, "loanFileDao", repository);
        LoanRiskRequest request = new LoanRiskRequest();
        request.setLoanFileId("nope");

        LoanRiskResponse response = port.evaluate(request);

        assertThat(response.getRisk()).isNull();
        verify(repository, never()).save(any());
    }

    private LoanRiskResponse evaluateRisk(EstateType estateType, String lastName, LoanRisk expectedRisk) {
        Customer borrower = new Customer();
        borrower.setLastName(lastName);
        LoanFile loanFile = new LoanFile();
        loanFile.setLoanFileId("abc123");
        loanFile.setEstateType(estateType);
        loanFile.setBorrower(borrower);
        LoanFileRepository repository = mock(LoanFileRepository.class);
        when(repository.findById("abc123")).thenReturn(Optional.of(loanFile));
        LoanRiskPortImpl port = new LoanRiskPortImpl();
        ReflectionTestUtils.setField(port, "loanFileDao", repository);
        LoanRiskRequest request = new LoanRiskRequest();
        request.setLoanFileId("abc123");

        LoanRiskResponse response = port.evaluate(request);

        ArgumentCaptor<LoanFile> saved = ArgumentCaptor.forClass(LoanFile.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getRisk()).isEqualTo(expectedRisk);
        return response;
    }

    @Test
    public void taskDispatch_belowThresholdGoesToClerk_otherwiseSupervisor() {
        TaskDispatchPortImpl port = new TaskDispatchPortImpl();
        ReflectionTestUtils.setField(port, "appProperties", new AppProperties());
        TaskDispatchRequest request = new TaskDispatchRequest();

        request.setLoanAmount(999999.99);
        assertThat(port.dispatch(request).getRole()).isEqualTo(Role.ID_POST_PROCESSING_CLERK);

        request.setLoanAmount(1000000.0);
        assertThat(port.dispatch(request).getRole()).isEqualTo(Role.ID_SUPERVISOR);
    }

    @Test
    public void taskDispatch_rethrowsErrors() {
        TaskDispatchPortImpl port = new TaskDispatchPortImpl();
        assertThatThrownBy(() -> port.dispatch(new TaskDispatchRequest()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void closingAndSettlement_alwaysSucceed() {
        assertThat(new LoanApprovalClosingPortImpl().close(new LoanApprovalClosingRequest()).isStatus()).isTrue();
        assertThat(new LoanSettlementPortImpl().start(new LoanSettlementRequest()).isStatus()).isTrue();
    }

    @Test
    public void callbackLoanApproval_updatesLoanFile() {
        LoanFileService service = mock(LoanFileService.class);
        CallbackLoanApprovalPortImpl port = new CallbackLoanApprovalPortImpl();
        ReflectionTestUtils.setField(port, "loanFileService", service);
        CallbackLoanApprovalRequest request = new CallbackLoanApprovalRequest();

        port.notify(request);

        verify(service).updateLoanFile(request);
    }

    @Test
    public void callbackLoanContract_acceptsRequestAndDoesNothing() {
        new CallbackLoanContractPortImpl().send(new CallbackLoanContractRequest());
        assertThatThrownBy(() -> new CallbackLoanContractPortImpl().send(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testEndpoint_returnsFixedStatus() {
        assertThat(new TestImpl().go(new TestRequest()).getStatus()).isEqualTo("1994582911");
    }
}

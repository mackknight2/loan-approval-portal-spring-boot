package com.westbank.ws;

import com.westbank.demo.ExampleData;
import com.westbank.entity.Address;
import com.westbank.entity.Country;
import com.westbank.entity.Customer;
import com.westbank.entity.MaritalStatus;
import com.westbank.helper.Generator;
import com.westbank.repository.CustomerRepository;
import com.westbank.web.form.LoanForm;
import com.westbank.ws.process.loanapproval._2019._01.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class LoanApprovalProcessProxyTest {

    private LoanApprovalProcessProxy proxy;
    private LoanApproval processClient;
    private CustomerRepository customerRepository;

    @BeforeEach
    public void setUp() {
        proxy = new LoanApprovalProcessProxy();
        processClient = mock(LoanApproval.class);
        customerRepository = mock(CustomerRepository.class);
        Generator generator = mock(Generator.class);
        when(generator.newLoanFileId()).thenReturn("LF0001");
        ReflectionTestUtils.setField(proxy, "processClient", processClient);
        ReflectionTestUtils.setField(proxy, "customerRepository", customerRepository);
        ReflectionTestUtils.setField(proxy, "generator", generator);
    }

    private Customer alice() {
        Customer alice = new Customer();
        alice.setId(7L);
        alice.setTitle("Mrs.");
        alice.setFirstName("Alice");
        alice.setLastName("Power");
        alice.setEmail("alice@test.com");
        alice.setMaritalStatus(MaritalStatus.MARRIED);
        Address address = new Address();
        address.setStreet("123 King St.");
        address.setCity("Glen Iris");
        address.setZipcode("3146");
        address.setState("Victoria");
        address.setCountry(new Country("AU"));
        alice.setAddress(address);
        return alice;
    }

    @Test
    public void startProcess_sendsBorrowerCoBorrowerAndLoanData() {
        LoanForm form = ExampleData.applicationForm(true);

        assertThat(proxy.startProcess(form, alice())).isTrue();

        ArgumentCaptor<LoanApprovalRequest> captor = ArgumentCaptor.forClass(LoanApprovalRequest.class);
        verify(processClient).start(captor.capture());
        LoanApprovalRequest request = captor.getValue();
        assertThat(request.getLoanFileId()).isEqualTo("LF0001");
        assertThat(request.getBorrowerCustomerId()).isEqualTo(7L);
        assertThat(request.getBorrowerLastName()).isEqualTo("Power");
        assertThat(request.getBorrowerMaritalStatus()).isEqualTo("MARRIED");
        assertThat(request.getBorrowerAddress().getCountry()).isEqualTo("AU");
        assertThat(request.isCoBorrower()).isTrue();
        assertThat(request.getCoBorrowerLastName()).isEqualTo("Schneider");
        assertThat(request.getLoanAmount()).isEqualTo(100000.0);
        assertThat(request.getEstateType()).isEqualTo("FLAT");
        assertThat(request.getResidenceType()).isEqualTo("MAIN_HOUSE");
        assertThat(request.getAgencyCode()).isEqualTo("VIC002");
    }

    @Test
    public void startProcess_loadsCustomerFromDatabaseWhenNotGiven() {
        LoanForm form = ExampleData.applicationForm(false);
        form.setBorrowerCustomerId(7L);
        when(customerRepository.findById(7L)).thenReturn(Optional.of(alice()));

        assertThat(proxy.startProcess(form, null)).isTrue();

        ArgumentCaptor<LoanApprovalRequest> captor = ArgumentCaptor.forClass(LoanApprovalRequest.class);
        verify(processClient).start(captor.capture());
        assertThat(captor.getValue().getBorrowerFirstName()).isEqualTo("Alice");
        assertThat(captor.getValue().isCoBorrower()).isFalse();
    }

    @Test
    public void startProcess_returnsFalseWhenProcessEngineFails() {
        doThrow(new RuntimeException("engine down")).when(processClient).start(any());
        assertThat(proxy.startProcess(ExampleData.applicationForm(false), alice())).isFalse();
    }

    @Test
    public void staffDecisions_sendBase64TokenOfStaffAndRole() {
        assertThat(proxy.processedByCreditBroker("broker", "CBR", "LF1")).isTrue();
        ArgumentCaptor<CreditBrokerDecision> broker = ArgumentCaptor.forClass(CreditBrokerDecision.class);
        verify(processClient).processedByCreditBroker(broker.capture());
        assertThat(new String(Base64.getDecoder().decode(broker.getValue().getSecureToken()))).isEqualTo("broker/CBR");
        assertThat(broker.getValue().getLoanFileId()).isEqualTo("LF1");

        assertThat(proxy.processedByClerkOrSupervisor("clerk", "PPC", "LF1")).isTrue();
        ArgumentCaptor<ClerkOrSupervisorDecision> clerk = ArgumentCaptor.forClass(ClerkOrSupervisorDecision.class);
        verify(processClient).processedByClerkOrSupervisor(clerk.capture());
        assertThat(clerk.getValue().getStaffRole()).isEqualTo("PPC");

        assertThat(proxy.informManagerDecision("manager", "MGR", "LF1", true)).isTrue();
        ArgumentCaptor<ManagerDecision> manager = ArgumentCaptor.forClass(ManagerDecision.class);
        verify(processClient).decidedByManager(manager.capture());
        assertThat(manager.getValue().isGranted()).isTrue();
    }

    @Test
    public void staffDecisions_returnFalseWhenProcessEngineFails() {
        doThrow(new RuntimeException()).when(processClient).processedByCreditBroker(any());
        doThrow(new RuntimeException()).when(processClient).processedByClerkOrSupervisor(any());
        doThrow(new RuntimeException()).when(processClient).decidedByManager(any());
        doThrow(new RuntimeException()).when(processClient).informedByCustomer(any());
        doThrow(new RuntimeException()).when(processClient).signedByManager(any());

        assertThat(proxy.processedByCreditBroker("b", "CBR", "LF1")).isFalse();
        assertThat(proxy.processedByClerkOrSupervisor("c", "PPC", "LF1")).isFalse();
        assertThat(proxy.informManagerDecision("m", "MGR", "LF1", false)).isFalse();
        assertThat(proxy.informCustomerDecision(1L, "Alice Power", "LF1", 2L, true)).isFalse();
        assertThat(proxy.signedContractByManager("m", "Manager Brown", "LF1", 2L)).isFalse();
    }

    @Test
    public void signatures_carryTokenWithPrincipalName() {
        assertThat(proxy.informCustomerDecision(1L, "Alice Power", "LF1", 2L, true)).isTrue();
        ArgumentCaptor<CustomerDecision> customer = ArgumentCaptor.forClass(CustomerDecision.class);
        verify(processClient).informedByCustomer(customer.capture());
        assertThat(customer.getValue().isAccepted()).isTrue();
        assertThat(customer.getValue().getContractId()).isEqualTo(2L);
        assertThat(customer.getValue().getToken().getX509SubjectName()).isEqualTo("Alice Power");

        assertThat(proxy.signedContractByManager("manager", "Manager Brown", "LF1", 2L)).isTrue();
        ArgumentCaptor<ManagerSignature> manager = ArgumentCaptor.forClass(ManagerSignature.class);
        verify(processClient).signedByManager(manager.capture());
        assertThat(manager.getValue().getToken().getX509SubjectName()).isEqualTo("Manager Brown");
        assertThat(manager.getValue().getStaffId()).isEqualTo("manager");
    }
}

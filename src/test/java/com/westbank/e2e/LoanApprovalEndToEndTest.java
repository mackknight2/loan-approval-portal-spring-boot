package com.westbank.e2e;

import com.westbank.entity.LoanContract;
import com.westbank.entity.LoanFile;
import com.westbank.entity.LoanFileStatus;
import com.westbank.repository.LoanContractRepository;
import com.westbank.repository.LoanFileRepository;
import com.westbank.ws.ServiceFactory;
import com.westbank.ws.business.bankinformation._2019._01.BankInformation;
import com.westbank.ws.business.bankinformation._2019._01.BankInformationRequest;
import com.westbank.ws.business.loancontract._2019._01.LoanContractRequest;
import com.westbank.ws.business.loanfile._2019._01.LoanFileRequest;
import com.westbank.ws.business.loanfile._2019._01.LoanFileResponse;
import com.westbank.ws.business.loanrisk._2019._01.LoanRiskRequest;
import com.westbank.ws.business.taskdispatch._2019._01.TaskDispatch;
import com.westbank.ws.business.taskdispatch._2019._01.TaskDispatchRequest;
import com.westbank.ws.client.callbackloanapproval._2019._01.CallbackLoanApproval;
import com.westbank.ws.client.callbackloanapproval._2019._01.CallbackLoanApprovalRequest;
import com.westbank.ws.process.loanapproval._2019._01.CreditBrokerDecision;
import com.westbank.ws.process.loanapproval._2019._01.CustomerDecision;
import com.westbank.ws.process.loanapproval._2019._01.LoanApprovalRequest;
import com.westbank.ws.process.loanapproval._2019._01.ManagerSignature;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.net.HttpURLConnection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

/**
 * Walks the main loan approval flow over real HTTP against the running application:
 * a customer applies for a loan, the bank's SOAP services process it, staff work the
 * application in the staff portal, and the customer signs the contract.
 */
public class LoanApprovalEndToEndTest extends AbstractFullAppTest {

    @LocalServerPort
    private int port;

    @Autowired
    private LoanFileRepository loanFileRepository;

    @Autowired
    private LoanContractRepository loanContractRepository;

    private final RestTemplate http = noRedirectRestTemplate();

    private static RestTemplate noRedirectRestTemplate() {
        RestTemplate template = new RestTemplate(new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws java.io.IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        });
        template.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });
        return template;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private <T> T soap(Class<T> service) {
        return new ServiceFactory<T>().create(service, url("/services/" + service.getSimpleName()));
    }

    private ResponseEntity<String> get(String path, String cookie) {
        HttpHeaders headers = new HttpHeaders();
        if (cookie != null) headers.add(HttpHeaders.COOKIE, cookie);
        return http.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> post(String path, String cookie, MultiValueMap<String, String> form) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (cookie != null) headers.add(HttpHeaders.COOKIE, cookie);
        return http.exchange(url(path), HttpMethod.POST, new HttpEntity<>(form, headers), String.class);
    }

    private static MultiValueMap<String, String> form(String... keyValues) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        for (int i = 0; i < keyValues.length; i += 2) form.add(keyValues[i], keyValues[i + 1]);
        return form;
    }

    private String login(String path, String loginId, String expectedRedirect) {
        ResponseEntity<String> page = get(path, null);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        String cookie = sessionCookie(page);
        ResponseEntity<String> response = post(path, cookie, form("loginId", loginId, "loginPassword", "abcd1234"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation().getPath()).isEqualTo(expectedRedirect);
        return cookie;
    }

    private static String sessionCookie(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotEmpty();
        return cookies.get(0).split(";")[0];
    }

    @Test
    public void customerAppliesStaffProcessesAndCustomerSignsContract() {
        // 1. Customer logs in and submits a loan request.
        String alice = login("/login", "alice@test.com", "/portal");
        ResponseEntity<String> portal = get("/portal", alice);
        assertThat(portal.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(portal.getBody()).contains("Alice");

        assertThat(get("/request", alice).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> submitted = post("/request", alice, form(
                "agency", "VIC002", "loanReason", "Home Mortgage", "loanAmount", "250000", "loanTerm", "20",
                "interestRate", "0.05", "totalPurchasePrice", "500000", "personalCapitalContribution", "250000",
                "residenceType", "MAIN_HOUSE", "estateType", "FLAT", "estateAddress", "123 Green Ave",
                "borrowerTitle", "Mrs.", "borrowerFirstName", "Alice", "borrowerLastName", "Power",
                "borrowerStreet", "123 King St.", "borrowerCity", "Glen Iris", "borrowerZipcode", "3146",
                "borrowerState", "Victoria", "borrowerPhone", "0412345678", "borrowerEmail", "alice@test.com",
                "borrowerOccupation", "Engineer", "borrowerIncome", "120000", "borrowerLengthOfService", "10",
                "accessSensitiveData", "true"));
        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.OK);

        ArgumentCaptor<LoanApprovalRequest> started = ArgumentCaptor.forClass(LoanApprovalRequest.class);
        verify(processEngine).start(started.capture());
        LoanApprovalRequest application = started.getValue();
        assertThat(application.getBorrowerLastName()).isEqualTo("Power");
        assertThat(application.getLoanAmount()).isEqualTo(250000.0);
        assertThat(application.isAccessSensitiveData()).isTrue();
        String loanFileId = application.getLoanFileId();
        assertThat(loanFileId).isNotBlank();

        // 2. Process engine stand-in calls the bank's SOAP services, as Apache ODE would.
        LoanFileRequest fileRequest = new LoanFileRequest();
        fileRequest.setLoanFileId(loanFileId);
        fileRequest.setBorrowerCustomerId(application.getBorrowerCustomerId());
        fileRequest.setLoanAmount(application.getLoanAmount());
        fileRequest.setLoanTerm(application.getLoanTerm());
        fileRequest.setInterestRate(application.getInterestRate());
        fileRequest.setLoanReason(application.getLoanReason());
        fileRequest.setResidenceType(application.getResidenceType());
        fileRequest.setEstateType(application.getEstateType());
        fileRequest.setEstateLocation(application.getEstateLocation());
        fileRequest.setTotalPurchasePrice(application.getTotalPurchasePrice());
        fileRequest.setPersonalCapitalContribution(application.getPersonalCapitalContribution());
        fileRequest.setAccessSensitiveData(application.isAccessSensitiveData());
        fileRequest.setStaffId("broker");
        LoanFileResponse fileResponse = soap(com.westbank.ws.business.loanfile._2019._01.LoanFile.class).update(fileRequest);
        assertThat(fileResponse.getLoanFileId()).isEqualTo(loanFileId);
        assertThat(fileResponse.getBorrowerId()).isEqualTo(application.getBorrowerCustomerId());

        BankInformationRequest bankRequest = new BankInformationRequest();
        bankRequest.setLoanAmount(250000.0);
        bankRequest.setInterestRate(0.05);
        bankRequest.setLoanTerm(20);
        assertThat(soap(BankInformation.class).retrieve(bankRequest).getMonthlyPayment()).isBetween(1649.0, 1650.0);

        LoanRiskRequest riskRequest = new LoanRiskRequest();
        riskRequest.setLoanFileId(loanFileId);
        assertThat(soap(com.westbank.ws.business.loanrisk._2019._01.LoanRisk.class).evaluate(riskRequest).getRisk()).isEqualTo("LOW");

        TaskDispatchRequest dispatchRequest = new TaskDispatchRequest();
        dispatchRequest.setLoanAmount(250000.0);
        assertThat(soap(TaskDispatch.class).dispatch(dispatchRequest).getRole()).isEqualTo("PPC");

        // 3. Credit broker accepts the application in the staff portal.
        String broker = login("/staff/login", "broker", "/staff/broker");
        ResponseEntity<String> brokerPage = get("/staff/broker", broker);
        assertThat(brokerPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(brokerPage.getBody()).contains(loanFileId);
        assertThat(post("/staff/broker", broker, form("action", "accept", "loanFileId", loanFileId)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(status(loanFileId)).isEqualTo(LoanFileStatus.UNDER_CONSIDERATION);
        ArgumentCaptor<CreditBrokerDecision> brokerDecision = ArgumentCaptor.forClass(CreditBrokerDecision.class);
        verify(processEngine).processedByCreditBroker(brokerDecision.capture());
        assertThat(brokerDecision.getValue().getLoanFileId()).isEqualTo(loanFileId);

        // 4. Post-processing clerk accepts it.
        String clerk = login("/staff/login", "clerk", "/staff/clerk");
        assertThat(get("/staff/clerk", clerk).getBody()).contains(loanFileId);
        post("/staff/clerk", clerk, form("action", "accept", "loanFileId", loanFileId));
        assertThat(status(loanFileId)).isEqualTo(LoanFileStatus.WORTHINESS_ANALYSIS);

        // 5. Process engine approves and creates the contract.
        CallbackLoanApprovalRequest callback = new CallbackLoanApprovalRequest();
        callback.setLoanFileId(loanFileId);
        callback.setStatus("APPROVED");
        callback.setDescription("Approved by the process");
        soap(CallbackLoanApproval.class).notify(callback);
        // notify() is a one-way SOAP operation, handled asynchronously by the server.
        awaitStatus(loanFileId, LoanFileStatus.APPROVED);

        com.westbank.ws.business.loancontract._2019._01.LoanContract contracts =
                soap(com.westbank.ws.business.loancontract._2019._01.LoanContract.class);
        LoanContractRequest contractRequest = new LoanContractRequest();
        contractRequest.setLoanFileId(loanFileId);
        contractRequest.setStaffId("manager");
        // Known bug (pinned, not fixed): creating a contract for an agency that already exists
        // fails, because the contract cascades a duplicate insert of the agency row.
        contractRequest.setAgencyCode(application.getAgencyCode());
        assertThatThrownBy(() -> contracts.create(contractRequest))
                .isInstanceOf(jakarta.xml.ws.soap.SOAPFaultException.class)
                .hasMessageContaining("could not execute statement");
        contractRequest.setAgencyCode("TST001");
        Long contractId = contracts.create(contractRequest).getLoanContractId();
        assertThat(contractId).isNotNull();

        // 6. Manager signs the contract.
        String manager = login("/staff/login", "manager", "/staff/manager");
        assertThat(get("/staff/manager", manager).getStatusCode()).isEqualTo(HttpStatus.OK);
        post("/staff/manager", manager, form("action", "sign", "loanFileId", loanFileId, "contractId", contractId.toString()));
        verify(processEngine).signedByManager(ArgumentCaptor.forClass(ManagerSignature.class).capture());
        LoanContract contract = loanContractRepository.findById(contractId).get();
        assertThat(contract.getSignedByManager()).isNotNull();

        // 7. Customer sees the contract in the portal and signs it.
        assertThat(get("/portal", alice).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> signed = post("/portal", alice,
                form("action", "sign", "loanFileId", loanFileId, "contractId", contractId.toString()));
        ArgumentCaptor<CustomerDecision> customerDecision = ArgumentCaptor.forClass(CustomerDecision.class);
        verify(processEngine).informedByCustomer(customerDecision.capture());
        assertThat(customerDecision.getValue().isAccepted()).isTrue();
        assertThat(loanContractRepository.findById(contractId).get().getSignedByCustomer()).isNotNull();
        // Known bug (pinned, not fixed): the contract created through the SOAP service is not linked
        // back to its loan file, so the customer's signature ends in an HTTP 500 error page and the
        // loan file is never marked ACCEPTED.
        assertThat(signed.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(status(loanFileId)).isEqualTo(LoanFileStatus.APPROVED);

        // 8. Both sides log out.
        assertThat(get("/logout", alice).getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(get("/staff/logout", manager).getHeaders().getLocation().getPath()).isEqualTo("/staff/login");
    }

    private void awaitStatus(String loanFileId, LoanFileStatus expected) {
        long deadline = System.currentTimeMillis() + 10000;
        while (status(loanFileId) != expected && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(status(loanFileId)).isEqualTo(expected);
    }

    private LoanFileStatus status(String loanFileId) {
        LoanFile loanFile = loanFileRepository.findById(loanFileId).get();
        return loanFile.getStatus();
    }
}

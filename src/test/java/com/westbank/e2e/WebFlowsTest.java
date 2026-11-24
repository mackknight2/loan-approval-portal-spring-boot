package com.westbank.e2e;

import com.westbank.entity.Customer;
import com.westbank.entity.LoanFile;
import com.westbank.entity.LoanFileStatus;
import com.westbank.entity.Staff;
import com.westbank.repository.CustomerRepository;
import com.westbank.repository.LoanFileRepository;
import com.westbank.repository.StaffRepository;
import com.westbank.web.controller.CustomerSession;
import com.westbank.web.controller.StaffSession;
import com.westbank.ws.process.loanapproval._2019._01.ManagerDecision;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Exercises the customer and staff web pages against the fully started application
 * (real database, services and validators), pinning today's behaviour of each page.
 */
public class WebFlowsTest extends AbstractFullAppTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private LoanFileRepository loanFileRepository;

    private MockHttpSession customerSession() {
        Customer alice = customerRepository.findByEmail("alice@test.com").get();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CustomerSession.ACTIVE_AUTHENTICATION, alice);
        return session;
    }

    private MockHttpSession staffSession(String staffId) {
        Staff staff = staffRepository.findById(staffId).get();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(StaffSession.ACTIVE_AUTHENTICATION, staff);
        session.setAttribute(StaffSession.ACTIVE_ROLE, staff.getRole().getId());
        return session;
    }

    private String newLoanFile(LoanFileStatus status) {
        LoanFile loanFile = new LoanFile();
        loanFile.setLoanFileId(RandomStringUtils.randomAlphanumeric(6));
        loanFile.setBorrower(customerRepository.findByEmail("alice@test.com").get());
        loanFile.setLoanAmount(100000.0);
        loanFile.setLoanTerm(20);
        loanFile.setInterestRate(0.03);
        loanFile.setCreatedDate(LocalDate.now());
        loanFile.setStatus(status);
        loanFile.setAccessSensitiveData(false);
        loanFileRepository.save(loanFile);
        return loanFile.getLoanFileId();
    }

    private LoanFile loanFile(String id) {
        return loanFileRepository.findById(id).get();
    }

    private static MockHttpServletRequestBuilder task(String path, MockHttpSession session, String action, String loanFileId) {
        MockHttpServletRequestBuilder request = post(path).session(session).param("action", action);
        if (loanFileId != null) request.param("loanFileId", loanFileId);
        return request;
    }

    // ---------- access guards ----------

    @Test
    public void customerPagesRedirectAnonymousVisitorsToLogin() throws Exception {
        for (String path : new String[]{"/portal", "/request", "/profile"}) {
            mvc.perform(get(path)).andExpect(redirectedUrl("/login"));
            mvc.perform(post(path)).andExpect(redirectedUrl("/login"));
        }
    }

    @Test
    public void staffPagesRedirectAnonymousVisitorsToStaffLogin() throws Exception {
        for (String path : new String[]{"/staff/portal", "/staff/broker", "/staff/clerk", "/staff/manager"}) {
            mvc.perform(get(path)).andExpect(redirectedUrl("/staff/login"));
        }
        for (String path : new String[]{"/staff/portal", "/staff/broker", "/staff/clerk"}) {
            mvc.perform(post(path)).andExpect(redirectedUrl("/staff/login"));
        }
    }

    @Test
    public void managerPostHasNoAccessGuard_currentBehaviour() {
        // Known bug (pinned, not fixed): POST /staff/manager does not check the login and fails
        // with a NullPointerException for anonymous visitors.
        assertThatThrownBy(() -> mvc.perform(post("/staff/manager").param("action", "grant").param("loanFileId", "x")))
                .isInstanceOf(jakarta.servlet.ServletException.class)
                .hasCauseInstanceOf(NullPointerException.class);
    }

    // ---------- customer home, login, logout ----------

    @Test
    public void homePageShowsIndexOrReturnsToLastCustomerPage() throws Exception {
        mvc.perform(get("/")).andExpect(forwardedUrl("/WEB-INF/jsp/customer/index.jsp"));
        MockHttpSession session = customerSession();
        session.setAttribute(CustomerSession.NAV_INDEX, CustomerSession.NAV_PORTAL);
        mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/portal"));
        session.setAttribute(CustomerSession.NAV_INDEX, CustomerSession.NAV_REQUEST);
        mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/request"));
        session.setAttribute(CustomerSession.NAV_INDEX, CustomerSession.NAV_PROFILE);
        mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/profile"));
        session.setAttribute(CustomerSession.NAV_INDEX, CustomerSession.NAV_HOME);
        mvc.perform(get("/").session(session)).andExpect(status().isOk());
    }

    @Test
    public void customerLoginRejectsWrongPassword() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login").session(session).param("loginId", "alice@test.com").param("loginPassword", "wrong"))
                .andExpect(status().isOk());
        assertThat(session.getAttribute(CustomerSession.ACTIVE_AUTHENTICATION)).isNull();
        mvc.perform(post("/login").session(session).param("loginId", "alice@test.com").param("loginPassword", "abcd1234"))
                .andExpect(redirectedUrl("/portal"));
        assertThat(session.getAttribute(CustomerSession.ACTIVE_AUTHENTICATION)).isNotNull();
        mvc.perform(get("/logout").session(session)).andExpect(redirectedUrl("/login"));
        assertThat(session.getAttribute(CustomerSession.ACTIVE_AUTHENTICATION)).isNull();
    }

    // ---------- customer portal ----------

    @Test
    public void customerPortalListsOwnLoanFilesAndManagesDataAccess() throws Exception {
        String id = newLoanFile(LoanFileStatus.INITIALIZED);
        MockHttpSession session = customerSession();

        mvc.perform(get("/portal").session(session)).andExpect(status().isOk());
        assertThat((List<?>) session.getAttribute(CustomerSession.LOAN_FILES)).isNotEmpty();

        mvc.perform(task("/portal", session, "grant", id)).andExpect(status().isOk());
        assertThat(loanFile(id).isAccessSensitiveData()).isTrue();
        mvc.perform(task("/portal", session, "deny", id)).andExpect(status().isOk());
        assertThat(loanFile(id).isAccessSensitiveData()).isFalse();
        mvc.perform(task("/portal", session, "grant", "unknown")).andExpect(status().isOk());
        mvc.perform(task("/portal", session, "reload", null)).andExpect(status().isOk());
    }

    @Test
    public void customerCancelsLoanWhenProcessEngineAgrees() throws Exception {
        String id = newLoanFile(LoanFileStatus.APPROVED);
        mvc.perform(task("/portal", customerSession(), "cancel", id).param("contractId", "999999"))
                .andExpect(status().isOk());
        assertThat(loanFile(id).getStatus()).isEqualTo(LoanFileStatus.CANCELED);
    }

    @Test
    public void customerDecisionIsIgnoredWhenProcessEngineFails() throws Exception {
        doThrow(new RuntimeException("engine down")).when(processEngine).informedByCustomer(any());
        String id = newLoanFile(LoanFileStatus.APPROVED);
        mvc.perform(task("/portal", customerSession(), "cancel", id).param("contractId", "999999"))
                .andExpect(status().isOk());
        assertThat(loanFile(id).getStatus()).isEqualTo(LoanFileStatus.APPROVED);
    }

    @Test
    public void customerSignWithUnknownContractChangesNothing() throws Exception {
        String id = newLoanFile(LoanFileStatus.APPROVED);
        mvc.perform(task("/portal", customerSession(), "sign", id).param("contractId", "999999"))
                .andExpect(status().isOk());
        assertThat(loanFile(id).getStatus()).isEqualTo(LoanFileStatus.APPROVED);
    }

    // ---------- loan request ----------

    @Test
    public void loanRequestFormShowsAndValidates() throws Exception {
        MockHttpSession session = customerSession();
        mvc.perform(get("/request").session(session)).andExpect(status().isOk())
                .andExpect(forwardedUrl("/WEB-INF/jsp/customer/loanrequest.jsp"));
        mvc.perform(post("/request").session(session).param("hasCoborrower", "true"))
                .andExpect(forwardedUrl("/WEB-INF/jsp/customer/loanrequest.jsp"))
                .andExpect(model().attributeHasFieldErrors("form", "coborrowerFirstName", "coborrowerLastName"));
    }

    @Test
    public void loanRequestReportsErrorWhenProcessEngineFails() throws Exception {
        doThrow(new RuntimeException("engine down")).when(processEngine).start(any());
        MockHttpSession session = customerSession();
        mvc.perform(validLoanRequest(session)).andExpect(forwardedUrl("/WEB-INF/jsp/customer/loanrequest.jsp"));
        assertThat(session.getAttribute(CustomerSession.PROCESS_STATUS)).isEqualTo(CustomerSession.PROCESS_STATUS_ERROR);
        assertThat(session.getAttribute(CustomerSession.PROCESS_STATUS_KEY)).isEqualTo(CustomerSession.MSG_INVOCATION_ERR);
    }

    @Test
    public void loanRequestShowsConfirmationWhenProcessStarts() throws Exception {
        MockHttpSession session = customerSession();
        mvc.perform(validLoanRequest(session)).andExpect(forwardedUrl("/WEB-INF/jsp/customer/info.jsp"));
        assertThat(session.getAttribute(CustomerSession.PROCESS_STATUS)).isEqualTo(CustomerSession.PROCESS_STATUS_OK);
    }

    private static MockHttpServletRequestBuilder validLoanRequest(MockHttpSession session) {
        return post("/request").session(session)
                .param("loanReason", "Home").param("residenceType", "MAIN_HOUSE").param("estateType", "HOUSE")
                .param("estateAddress", "1 Main St").param("totalPurchasePrice", "500000")
                .param("personalCapitalContribution", "100000").param("loanAmount", "400000").param("loanTerm", "25")
                .param("borrowerTitle", "Mrs.").param("borrowerFirstName", "Alice").param("borrowerLastName", "Power")
                .param("borrowerStreet", "1 King St").param("borrowerCity", "Melbourne").param("borrowerZipcode", "3000")
                .param("borrowerPhone", "0400000000").param("borrowerEmail", "alice@test.com")
                .param("borrowerOccupation", "Engineer").param("borrowerIncome", "100000")
                .param("borrowerLengthOfService", "5");
    }

    // ---------- customer profile ----------

    @Test
    public void profilePageIsPrefilledFromCustomer() throws Exception {
        mvc.perform(get("/profile").session(customerSession()))
                .andExpect(forwardedUrl("/WEB-INF/jsp/customer/profile.jsp"))
                .andExpect(model().attribute("applicationForm",
                        org.hamcrest.Matchers.hasProperty("borrowerLastName", org.hamcrest.Matchers.is("Power"))));
    }

    @Test
    public void profileUpdateWithMissingFieldsReportsError() throws Exception {
        MockHttpSession session = customerSession();
        mvc.perform(post("/profile").session(session).param("newPin", "a").param("newPinAgain", "b"))
                .andExpect(forwardedUrl("/WEB-INF/jsp/customer/profile.jsp"));
        assertThat(session.getAttribute(CustomerSession.PROCESS_STATUS)).isEqualTo(CustomerSession.PROCESS_STATUS_ERROR);
    }

    @Test
    public void profileUpdateWithValidFormFails_currentBehaviour() {
        // Known bug (pinned, not fixed): the controller casts the logged-in Customer to a Long id,
        // so every valid profile update fails with a ClassCastException.
        assertThatThrownBy(() -> mvc.perform(post("/profile").session(customerSession())
                .param("borrowerFirstName", "Alice").param("borrowerLastName", "Power")
                .param("borrowerStreet", "1 King St").param("borrowerCity", "Melbourne")
                .param("borrowerZipcode", "3000").param("borrowerPhone", "0400000000")
                .param("borrowerEmail", "alice@test.com").param("borrowerOccupation", "Engineer")
                .param("borrowerIncome", "100000").param("borrowerLengthOfService", "5")))
                .isInstanceOf(jakarta.servlet.ServletException.class)
                .hasCauseInstanceOf(ClassCastException.class);
    }

    // ---------- staff login, logout, portal ----------

    @Test
    public void staffLoginDispatchesEachRoleToItsPage() throws Exception {
        mvc.perform(get("/staff/login")).andExpect(status().isOk());
        String[][] expectations = {{"broker", "/staff/broker"}, {"clerk", "/staff/clerk"},
                {"supervisor", "/staff/clerk"}, {"manager", "/staff/manager"}};
        for (String[] e : expectations) {
            MockHttpSession session = new MockHttpSession();
            mvc.perform(post("/staff/login").session(session).param("loginId", e[0]).param("loginPassword", "abcd1234"))
                    .andExpect(redirectedUrl(e[1]));
            assertThat(session.getAttribute(StaffSession.ACTIVE_AUTHENTICATION)).isNotNull();
            mvc.perform(get("/staff/logout").session(session)).andExpect(redirectedUrl("/staff/login"));
            assertThat(session.getAttribute(StaffSession.ACTIVE_AUTHENTICATION)).isNull();
        }
    }

    @Test
    public void staffLoginRejectsEmptyAndWrongCredentials() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/staff/login").session(session).param("loginId", "").param("loginPassword", ""))
                .andExpect(model().attributeHasFieldErrors("loginForm", "loginId", "loginPassword"));
        mvc.perform(post("/staff/login").session(session).param("loginId", "broker").param("loginPassword", "nope"))
                .andExpect(status().isOk());
        assertThat(session.getAttribute(StaffSession.PROCESS_STATUS_KEY)).isEqualTo(StaffSession.MSG_LOGIN_FAILED);
    }

    @Test
    public void staffPortalShowsManagedLoans() throws Exception {
        MockHttpSession session = staffSession("broker");
        mvc.perform(get("/staff/portal").session(session)).andExpect(status().isOk());
        assertThat(session.getAttribute(StaffSession.MANAGED_LOAN_FILES)).isNotNull();
        mvc.perform(post("/staff/portal").session(session)).andExpect(status().isOk());
    }

    // ---------- broker, clerk / supervisor ----------

    @Test
    public void brokerAndClerkReloadOrIgnoreUnknownActions() throws Exception {
        for (String[] page : new String[][]{{"/staff/broker", "broker"}, {"/staff/clerk", "clerk"}}) {
            MockHttpSession session = staffSession(page[1]);
            mvc.perform(get(page[0]).session(session)).andExpect(status().isOk());
            mvc.perform(task(page[0], session, "reload", null)).andExpect(status().isOk());
            mvc.perform(task(page[0], session, "dance", null)).andExpect(status().isOk());
            assertThat(session.getAttribute(StaffSession.MANAGED_LOAN_FILES)).isNotNull();
        }
    }

    @Test
    public void supervisorAcceptMovesLoanToWorthinessAnalysis() throws Exception {
        String id = newLoanFile(LoanFileStatus.UNDER_CONSIDERATION);
        mvc.perform(task("/staff/clerk", staffSession("supervisor"), "accept", id)).andExpect(status().isOk());
        assertThat(loanFile(id).getStatus()).isEqualTo(LoanFileStatus.WORTHINESS_ANALYSIS);
        assertThat(loanFile(id).getUpdatedByRole()).isEqualTo("SPR");
    }

    @Test
    public void brokerAcceptOfUnknownLoanStillNotifiesProcess() throws Exception {
        mvc.perform(task("/staff/broker", staffSession("broker"), "accept", "unknown")).andExpect(status().isOk());
        verify(processEngine).processedByCreditBroker(any());
    }

    // ---------- manager ----------

    @Test
    public void managerGrantApprovesLoanButTellsProcessItWasNotGranted_currentBehaviour() throws Exception {
        String id = newLoanFile(LoanFileStatus.RISK_ANALYSIS);
        MockHttpSession session = staffSession("manager");
        mvc.perform(get("/staff/manager").session(session)).andExpect(status().isOk());
        mvc.perform(task("/staff/manager", session, "grant", id)).andExpect(status().isOk());
        assertThat(loanFile(id).getStatus()).isEqualTo(LoanFileStatus.APPROVED);
        // Known bug (pinned, not fixed): the decision is sent before it is read from the form,
        // so the process engine is always told "not granted".
        ArgumentCaptor<ManagerDecision> decision = ArgumentCaptor.forClass(ManagerDecision.class);
        verify(processEngine).decidedByManager(decision.capture());
        assertThat(decision.getValue().isGranted()).isFalse();
    }

    @Test
    public void managerRejectRejectsLoan() throws Exception {
        String id = newLoanFile(LoanFileStatus.RISK_ANALYSIS);
        mvc.perform(task("/staff/manager", staffSession("manager"), "reject", id)).andExpect(status().isOk());
        assertThat(loanFile(id).getStatus()).isEqualTo(LoanFileStatus.REJECTED);
    }

    @Test
    public void managerDecisionReportsErrorWhenProcessEngineFails() throws Exception {
        doThrow(new RuntimeException("engine down")).when(processEngine).decidedByManager(any());
        doThrow(new RuntimeException("engine down")).when(processEngine).signedByManager(any());
        String id = newLoanFile(LoanFileStatus.RISK_ANALYSIS);
        MockHttpSession session = staffSession("manager");

        mvc.perform(task("/staff/manager", session, "grant", id)).andExpect(status().isOk());
        assertThat(loanFile(id).getStatus()).isEqualTo(LoanFileStatus.RISK_ANALYSIS);
        assertThat(session.getAttribute(StaffSession.PROCESS_STATUS_KEY)).isEqualTo(StaffSession.MSG_INVOCATION_ERR);

        session.removeAttribute(StaffSession.PROCESS_STATUS_KEY);
        mvc.perform(task("/staff/manager", session, "sign", id).param("contractId", "999999")).andExpect(status().isOk());
        assertThat(session.getAttribute(StaffSession.PROCESS_STATUS_KEY)).isEqualTo(StaffSession.MSG_INVOCATION_ERR);
    }

    @Test
    public void managerSignOfUnknownContractIsLoggedOnly() throws Exception {
        String id = newLoanFile(LoanFileStatus.APPROVED);
        mvc.perform(task("/staff/manager", staffSession("manager"), "sign", id).param("contractId", "999999"))
                .andExpect(status().isOk());
        assertThat(loanFile(id).getStatus()).isEqualTo(LoanFileStatus.APPROVED);
    }
}

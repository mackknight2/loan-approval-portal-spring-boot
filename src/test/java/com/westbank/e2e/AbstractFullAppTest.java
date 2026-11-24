package com.westbank.e2e;

import com.westbank.PortalApplication;
import com.westbank.demo.DataInitializer;
import com.westbank.ws.process.loanapproval._2019._01.LoanApproval;
import org.junit.runner.RunWith;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Starts the whole application (embedded Tomcat on a random port, H2 with the demo data,
 * SOAP services, JSP views). The only stand-in is the external Apache ODE loan approval
 * process engine, which is not part of this repository.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = PortalApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(DataInitializer.class)
public abstract class AbstractFullAppTest {

    @MockBean
    protected LoanApproval processEngine;
}

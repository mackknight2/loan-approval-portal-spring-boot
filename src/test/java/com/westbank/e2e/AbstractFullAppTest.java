package com.westbank.e2e;

import com.westbank.PortalApplication;
import com.westbank.demo.DataInitializer;
import com.westbank.ws.process.loanapproval._2019._01.LoanApproval;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;

/**
 * Starts the whole application (embedded Tomcat on a random port, H2 with the demo data,
 * SOAP services, JSP views). The only stand-in is the external Apache ODE loan approval
 * process engine, which is not part of this repository.
 */
@SpringBootTest(classes = PortalApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(DataInitializer.class)
public abstract class AbstractFullAppTest {

    @MockitoBean
    protected LoanApproval processEngine;
}

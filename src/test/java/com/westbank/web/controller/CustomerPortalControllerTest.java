package com.westbank.web.controller;

import com.westbank.config.*;
import com.westbank.web.controller.CustomerPortalController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;

@ActiveProfiles("web")
@WebMvcTest(CustomerPortalController.class)
@ContextHierarchy({@ContextConfiguration(classes = {
        DataConfig.class,
        HelperConfig.class,
        RemoteConfig.class,
        ServiceConfig.class,
        ServiceClientConfig.class,
        WebTestConfig.class
})})
public class CustomerPortalControllerTest {

    @Test
    public void prepare() {
    }

    @Test
    public void processLoanList() {
    }
}
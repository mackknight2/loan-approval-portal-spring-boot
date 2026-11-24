package com.westbank.web.controller;

import com.westbank.config.WebTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.HttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("web")
@WebMvcTest
@ContextConfiguration(classes = {WebTestConfig.class, CustomerLogoutController.class})
public class CustomerLogoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void logout() throws Exception {
        HttpSession session = mockMvc.perform(get(CustomerLogoutController.PATH))
                .andExpect(status().is3xxRedirection())
                .andDo(print())
                .andExpect(view().name(CustomerLogoutController.REDIRECT_LOGIN_VIEW))
                .andExpect(redirectedUrl(CustomerLogoutController.LOGIN_VIEW))
                .andReturn()
                .getRequest()
                .getSession();
        assertThat(session.getAttribute(CustomerSession.ACTIVE_AUTHENTICATION)).isNull();
        assertThat(session.getAttribute(CustomerSession.TITLE)).isNull();
        assertThat(session.getAttribute(CustomerSession.FIRSTNAME)).isNull();
        assertThat(session.getAttribute(CustomerSession.LASTNAME)).isNull();
        assertThat(session.getAttribute(CustomerSession.EMAIL)).isNull();
    }
}

package com.westbank.repository;

import com.westbank.config.DataConfig;
import com.westbank.config.DataTestConfig;
import com.westbank.config.HelperConfig;
import com.westbank.entity.Customer;
import com.westbank.entity.LoanFile;
import com.westbank.helper.Generator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("data")
@ContextHierarchy({@ContextConfiguration(classes = {DataConfig.class, HelperConfig.class, DataTestConfig.class})})
public class LoanFileQueriesCurrentBehaviourTest {

    @Autowired
    private LoanFileRepository loanFileRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private Generator generator;

    private Customer customer(String firstName, String email) {
        Customer customer = new Customer();
        customer.setFirstName(firstName);
        customer.setLastName("Tester");
        customer.setPassword("abcd1234");
        customer.setUsername(generator.newUsername());
        customer.setEmail(email);
        return testEntityManager.persist(customer);
    }

    private LoanFile loanFile(Customer borrower) {
        LoanFile loanFile = new LoanFile();
        loanFile.setLoanFileId(generator.newLoanFileId());
        loanFile.setBorrower(borrower);
        return testEntityManager.persist(loanFile);
    }

    // Known bug (pinned, not fixed): the query uses "LEFT JOIN ... ON" instead of a WHERE clause,
    // so it returns every loan file, including files that belong to other customers.
    @Test
    public void findAllByBorrowerId_returnsOtherCustomersLoanFiles_currentBehaviour() {
        Customer first = customer("First", "first@example.com");
        Customer second = customer("Second", "second@example.com");
        LoanFile firstLoan = loanFile(first);
        LoanFile secondLoan = loanFile(second);

        assertThat(loanFileRepository.findAllByBorrowerId(first.getId()))
                .contains(firstLoan, secondLoan);
    }
}

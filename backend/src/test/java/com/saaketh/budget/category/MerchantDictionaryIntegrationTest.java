package com.saaketh.budget.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import com.saaketh.budget.TestUsers;
import com.saaketh.budget.TestcontainersConfiguration;
import com.saaketh.budget.transaction.CategorySource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/** The real built-in dictionary against messy, realistic descriptions. "-" means Uncategorized. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MerchantDictionaryIntegrationTest {

    /** The matching part of V11's backfill query, for one description and amount. */
    private static final String SQL_MATCH = """
            SELECT k.category_name
            FROM merchant_keywords k
            WHERE (' ' || btrim(regexp_replace(upper(?), '[^A-Z0-9]+', ' ', 'g')) || ' ') LIKE ('% ' || k.keyword || ' %')
              AND (NOT k.money_in_only OR ?::numeric > 0)
            ORDER BY length(k.keyword) DESC, k.keyword
            LIMIT 1""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryAssigner assigner;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CategoryAssigner.Context context;
    private Map<Long, String> categoryNames;

    @BeforeEach
    void setUp() throws Exception {
        MockHttpSession session = TestUsers.registerAndLogin(mockMvc);
        String me = mockMvc.perform(get("/api/auth/me").session(session)).andReturn().getResponse().getContentAsString();
        long userId = ((Number) JsonPath.read(me, "$.id")).longValue();
        context = assigner.contextFor(userId);
        categoryNames = context.categoryIdByLowerName().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
    }

    @ParameterizedTest(name = "{0} ({1}) -> {2}")
    @CsvSource(delimiter = '|', textBlock = """
            SQ *JOE'S PIZZA #12               | -18.20 | Food & Dining
            TST* BLUE WATER GRILL             | -30.00 | Food & Dining
            MCDONALD'S F1234                  |  -8.00 | Food & Dining
            CHICK-FIL-A #0456                 |  -9.00 | Food & Dining
            DOORDASH*CHIPOTLE                 | -22.00 | Food & Dining
            UBER *EATS PENDING                | -20.00 | Food & Dining
            UBER *TRIP HELP.UBER.COM          | -12.40 | Transportation
            SHELL OIL 57444                   | -40.00 | Transportation
            FRESH MART #212                   | -48.33 | Groceries
            HARRIS TEETER #0123               | -60.00 | Groceries
            WALMART SUPERCENTER #1234         | -50.00 | Shopping
            AMZN Mktp US*2K4AB                | -25.00 | Shopping
            PAYPAL *POSHMARK                  |  -6.09 | Shopping
            THE HOME DEPOT #123               | -80.00 | Shopping
            APPLE.COM/BILL                    |  -2.99 | Subscriptions
            NETFLIX.COM                       | -15.49 | Subscriptions
            STEAMGAMES.COM 4259522985         | -19.99 | Entertainment
            STYLE STREET BARBERS              | -41.40 | Health & Personal Care
            CVS/PHARMACY #1234                | -12.00 | Health & Personal Care
            AT&T*BILL PAYMENT                 | -65.00 | Bills & Utilities
            CELL PHONE CO AUTOPAY             | -45.00 | Bills & Utilities
            UNC STUDENT STORES                | -62.10 | Education
            INTEREST CHARGE ON PURCHASES      |  -5.12 | Fees & Interest
            ATM FEE                           |  -3.00 | Fees & Interest
            ATM WITHDRAWAL 123 MAIN ST        | -60.00 | Other
            INTERNET PAYMENT - THANK YOU      | 250.00 | Payments & Transfers
            ONLINE PAYMENT, THANK YOU         | 250.00 | Payments & Transfers
            VENMO *JANE DOE                   | -20.00 | Payments & Transfers
            ZELLE TO JOHN                     | -50.00 | Payments & Transfers
            STATE UNIVERSITY PAYROLL          | 400.00 | Income
            MOBILE DEPOSIT                    | 100.00 | Income
            STATE UNIVERSITY PAYROLL          | -400.00 | -
            SNACK BAR                         |  -4.00 | -
            MYSTERY SHOP LLC                  | -10.00 | -
            """)
    void categorizesRealisticDescriptions(String description, String amount, String expected) {
        BigDecimal value = new BigDecimal(amount);

        CategoryAssigner.Assignment java = assigner.assign(context, description, null, value);
        String fromJava = java.categoryId() == null ? "-" : categoryNames.get(java.categoryId());
        List<String> sql = jdbcTemplate.queryForList(SQL_MATCH, String.class, description, value);
        String fromSql = sql.isEmpty() ? "-" : sql.getFirst().toLowerCase();

        assertThat(fromJava).as("Java matcher").isEqualToIgnoringCase(expected);
        assertThat(fromSql).as("SQL backfill agrees with Java").isEqualToIgnoringCase(expected);
        if (!expected.equals("-")) {
            assertThat(java.source()).isEqualTo(CategorySource.KEYWORD);
        }
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            Dining        | PIZZA PALACE
            Merchandise   | STARBUCKS
            """)
    void bankLabelsWinOverTheDictionary(String bankLabel, String description) {
        CategoryAssigner.Assignment a = assigner.assign(context, description, bankLabel, new BigDecimal("-5.00"));

        assertThat(a.source()).isEqualTo(CategorySource.BANK);
    }
}

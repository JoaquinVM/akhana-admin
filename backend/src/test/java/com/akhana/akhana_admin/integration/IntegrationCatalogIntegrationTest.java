package com.akhana.akhana_admin.integration;

import com.akhana.akhana_admin.config.IntegrationApiKeyFilter;
import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.service.JwtService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * INT-01: the WhatsApp bot reads the active catalog with its own key and never sees costs, suppliers or the
 * special products of the POS. Data is created inside a rolled-back transaction; the database may be shared.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.integrations.catalog-api-key=" + IntegrationCatalogIntegrationTest.KEY)
class IntegrationCatalogIntegrationTest {

    static final String KEY = "it-catalog-key-0123456789abcdef0123456789";
    private static final String URL = "/api/integrations/catalog";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    private String suffix;
    private UUID category;
    private UUID supplier;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        category = jdbc.queryForObject("INSERT INTO categories (name, color, created_by) VALUES (?, '#000000', 'it') RETURNING id",
            UUID.class, "Frutos secos " + suffix);
        supplier = jdbc.queryForObject("INSERT INTO suppliers (code, name, created_by) VALUES (?, ?, 'it') RETURNING id",
            UUID.class, "IT-" + suffix, "Proveedor secreto " + suffix);
    }

    private UUID product(String code, String name, String unit, String status, String sellPrice, String specialType, UUID owner) {
        return jdbc.queryForObject("INSERT INTO products (code, name, category_id, supplier_id, description, buy_price, "
            + "sell_price, fixed_profit, percentage_profit, status, sale_unit, special_type, owner_creditor_id, created_by) "
            + "VALUES (?, ?, ?, ?, 'Para el desayuno', 77.77, ?::numeric, 1, 10, ?, ?, ?, ?, 'it') RETURNING id", UUID.class,
            code + suffix, name + " " + suffix, category, supplier, sellPrice, status, unit, specialType, owner);
    }

    private void presentation(UUID product, String name, String weight, String fixedPrice, boolean active) {
        jdbc.update("INSERT INTO product_presentations (product_id, name, weight_kg, fixed_price, active, created_by) "
            + "VALUES (?, ?, ?::numeric, ?::numeric, ?, 'it')", product, name, weight, fixedPrice, active);
    }

    private List<Map<String, Object>> mine(String body) {
        List<Map<String, Object>> all = JsonPath.read(body, "$.products");
        return all.stream().filter(p -> String.valueOf(p.get("code")).endsWith(suffix)).toList();
    }

    @Test
    @DisplayName("Con la clave devuelve solo productos activos y normales, con precios y presentaciones")
    void withKey_ReturnsActiveCatalogOnly() throws Exception {
        product("MIEL", "Miel", "UNIT", "ACTIVO", "35.5", null, null);
        product("INAC", "Inactivo", "UNIT", "INACTIVO", "10", null, null);
        product("ELIM", "Eliminado", "UNIT", "ELIMINADO", "10", null, null);
        UUID owner = jdbc.queryForObject("INSERT INTO creditors (name, status, created_by) VALUES (?, 'ACTIVO', 'it') RETURNING id",
            UUID.class, "Dueña " + suffix);
        product("TERC", "De terceros", "UNIT", "ACTIVO", "10", "THIRD_PARTY", owner);
        UUID nuez = product("NUEZ", "Nuez pelada", "KILOGRAM", "ACTIVO", "210", null, null);
        presentation(nuez, "100 g", "0.100", null, true);
        presentation(nuez, "1/2 libra", "0.227", "45", true);
        presentation(nuez, "1 kilo viejo", "1.000", null, false);

        String body = mockMvc.perform(get(URL).header(IntegrationApiKeyFilter.HEADER, KEY))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(body, "$.currency")).isEqualTo("Bs");
        List<Map<String, Object>> products = mine(body);
        assertThat(products).extracting(p -> p.get("code")).containsExactly("MIEL" + suffix, "NUEZ" + suffix);

        Map<String, Object> miel = products.get(0);
        assertThat(miel.get("name")).isEqualTo("Miel " + suffix);
        assertThat(miel.get("category")).isEqualTo("Frutos secos " + suffix);
        assertThat(miel.get("description")).isEqualTo("Para el desayuno");
        assertThat(miel.get("saleUnit")).isEqualTo("UNIT");
        assertThat(new BigDecimal(String.valueOf(miel.get("price")))).isEqualByComparingTo("35.50");
        assertThat((List<?>) miel.get("presentations")).isEmpty();

        Map<String, Object> nuezItem = products.get(1);
        assertThat(new BigDecimal(String.valueOf(nuezItem.get("price")))).isEqualByComparingTo("210.00");
        assertThat(nuezItem.get("saleUnit")).isEqualTo("KILOGRAM");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> presentations = (List<Map<String, Object>>) nuezItem.get("presentations");
        assertThat(presentations).extracting(p -> p.get("name")).containsExactly("100 g", "1/2 libra");
        assertThat(new BigDecimal(String.valueOf(presentations.get(0).get("price")))).isEqualByComparingTo("21.00");
        assertThat(new BigDecimal(String.valueOf(presentations.get(1).get("price")))).isEqualByComparingTo("45.00");

        // Nothing a customer must not know
        assertThat(body).doesNotContain("77.77", "buyPrice", "Proveedor secreto", "supplier", "fixedProfit", "createdBy");
    }

    @Test
    @DisplayName("Sin clave o con una clave distinta responde 401")
    void withoutKey_Unauthorized() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL).header(IntegrationApiKeyFilter.HEADER, KEY + "x")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un usuario con sesión no lee el catálogo del bot, y la clave del bot no abre otras pantallas")
    void userAndBotStayApart() throws Exception {
        User user = userRepository.saveAndFlush(User.builder()
            .username("it_bot_" + suffix)
            .fullName("Integración")
            .password("x")
            .role(Role.OPERATOR)
            .active(true)
            .mustChangePassword(false)
            .createdBy("TEST")
            .build());
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(user)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/products").header(IntegrationApiKeyFilter.HEADER, KEY))
            .andExpect(status().isUnauthorized());
    }
}

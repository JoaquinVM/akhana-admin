package com.akhana.akhana_admin.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/** INT-01: the bot's key authenticates only on /api/integrations/ and only when configured. */
class IntegrationApiKeyFilterTest {

    private static final String KEY = "k".repeat(40);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private Authentication run(IntegrationApiKeyFilter filter, String path, String key) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (key != null) {
            request.addHeader(IntegrationApiKeyFilter.HEADER, key);
        }
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    @DisplayName("Con la clave correcta autentica al bot solo con el permiso de integración")
    void rightKey_AuthenticatesWithIntegrationAuthorityOnly() throws Exception {
        Authentication auth = run(new IntegrationApiKeyFilter(KEY), "/api/integrations/catalog", KEY);
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo(IntegrationApiKeyFilter.PRINCIPAL);
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
            .containsExactly(IntegrationApiKeyFilter.AUTHORITY);
    }

    @Test
    @DisplayName("Sin clave, con una clave distinta o fuera de /api/integrations no autentica")
    void wrongKeyOrOtherPath_DoesNotAuthenticate() throws Exception {
        IntegrationApiKeyFilter filter = new IntegrationApiKeyFilter(KEY);
        assertThat(run(filter, "/api/integrations/catalog", null)).isNull();
        assertThat(run(filter, "/api/integrations/catalog", KEY + "x")).isNull();
        assertThat(run(filter, "/api/products", KEY)).isNull();
        assertThat(run(filter, "/api/integrationsX/catalog", KEY)).isNull();
    }

    @Test
    @DisplayName("Sin CATALOG_API_KEY la integración queda apagada")
    void noConfiguredKey_IsOff() throws Exception {
        IntegrationApiKeyFilter off = new IntegrationApiKeyFilter("  ");
        assertThat(off.isEnabled()).isFalse();
        assertThat(run(off, "/api/integrations/catalog", "")).isNull();
        assertThat(run(off, "/api/integrations/catalog", "  ")).isNull();
        assertThat(new IntegrationApiKeyFilter(null).isEnabled()).isFalse();
    }
}

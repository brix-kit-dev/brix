package io.brix.platform.tenant.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.brix.platform.auth.context.AuthenticatedUser;
import io.brix.platform.auth.context.SecurityContextHolder;
import io.brix.platform.tenant.repository.TenantMemberRepository;
import io.brix.platform.tenant.repository.TenantPrincipalRepository;

class IdentityValidationFilterTest {

    private final SecurityContextHolder context = new SecurityContextHolder();
    private final TenantMemberRepository members = mock(TenantMemberRepository.class);
    private final TenantPrincipalRepository principals = mock(TenantPrincipalRepository.class);
    private final IdentityValidationFilter filter = new IdentityValidationFilter(context, members, principals);

    @Test
    void checksTheMemberInsideTheTokensTenant() throws Exception {
        when(members.existsByIdAndTenantId(7L, 42L)).thenReturn(true);
        context.setCurrentUser(actor("7", "42"));

        MockHttpServletResponse response = run();

        assertEquals(200, response.getStatus());
        verify(members, never()).existsById(anyLong());
    }

    @Test
    void rejectsAMemberOfAnotherTenantOrATokenWithoutTenant() throws Exception {
        when(members.existsByIdAndTenantId(7L, 43L)).thenReturn(false);
        context.setCurrentUser(actor("7", "43"));
        assertEquals(401, run().getStatus());

        context.setCurrentUser(actor("7", null));
        assertEquals(401, run().getStatus());
    }

    private MockHttpServletResponse run() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/tenant/session"), response, new MockFilterChain());
        return response;
    }

    private static AuthenticatedUser actor(String memberId, String tenantId) {
        AuthenticatedUser user = new AuthenticatedUser();
        user.setUserId("21");
        user.setMemberId(memberId);
        user.setTenantId(tenantId);
        return user;
    }
}

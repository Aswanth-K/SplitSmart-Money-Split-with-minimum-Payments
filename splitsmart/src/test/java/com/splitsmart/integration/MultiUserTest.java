package com.splitsmart.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.splitsmart.repository.AccountRepository;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:multiuser;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            "razorpay.key-id=",
            "razorpay.key-secret="
        })
@AutoConfigureMockMvc
class MultiUserTest {
    static final Path data;

    static {
        try {
            data = Files.createTempDirectory("splitsmart-auth-test");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("splitsmart.data-directory", data::toString);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;

    JsonNode body(MockHttpServletRequestBuilder request, int status) throws Exception {
        return json.readTree(
                mvc.perform(request.contentType("application/x-www-form-urlencoded"))
                        .andExpect(status().is(status))
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    void register(String username) throws Exception {
        body(
                post("/api/auth/register")
                        .with(csrf())
                        .param("username", username)
                        .param("password", "password-for-testing"),
                201);
    }

    MockHttpSession login(String username) throws Exception {
        return (MockHttpSession)
                mvc.perform(
                                post("/api/auth/login")
                                        .with(csrf())
                                        .param("username", username)
                                        .param("password", "password-for-testing"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getRequest()
                        .getSession(false);
    }

    @Test
    void accountsSearchDirectMembershipAndPayerPermissions() throws Exception {
        mvc.perform(get("/api/groups")).andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/auth/register")
                                .param("username", "no_csrf")
                                .param("password", "password-for-testing"))
                .andExpect(status().isForbidden());
        register("alice");
        register("bob");
        register("charlie");
        assertTrue(accounts.findById("alice").orElseThrow().getPasswordHash().startsWith("$2"));
        body(
                post("/api/auth/register")
                        .with(csrf())
                        .param("username", "Alice")
                        .param("password", "password-for-testing"),
                409);
        body(
                post("/api/auth/login")
                        .with(csrf())
                        .param("username", "alice")
                        .param("password", "wrong"),
                401);
        var alice = login("alice");
        var bob = login("bob");
        var charlie = login("charlie");
        String group =
                body(post("/api/groups").session(alice).with(csrf()).param("name", "Trip"), 201)
                        .path("selected")
                        .asText();
        mvc.perform(get("/groups/" + group))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
        assertEquals(0, body(get("/api/groups").session(bob), 200).path("groups").size());
        body(get("/api/state").session(bob).param("group", group), 403);
        var search =
                body(get("/api/users").session(alice).param("group", group).param("q", "BoB"), 200);
        assertEquals(1, search.size());
        assertEquals("bob", search.get(0).path("username").asText());
        assertFalse(search.get(0).has("passwordHash"));
        body(get("/api/users").session(charlie).param("group", group), 403);
        body(
                post("/api/members")
                        .session(alice)
                        .with(csrf())
                        .param("group", group)
                        .param("username", "bob"),
                200);
        assertEquals(0, body(get("/api/invitations").session(bob), 200).size());
        assertEquals(
                0,
                body(get("/api/users").session(alice).param("group", group).param("q", "bob"), 200)
                        .size());
        body(
                post("/api/members")
                        .session(alice)
                        .with(csrf())
                        .param("group", group)
                        .param("username", "bob"),
                409);
        assertEquals(
                2,
                body(get("/api/state").session(bob).param("group", group), 200)
                        .path("members")
                        .size());
        body(
                post("/api/members")
                        .session(bob)
                        .with(csrf())
                        .param("group", group)
                        .param("username", "charlie"),
                403);
        var expense =
                body(
                        post("/api/expenses")
                                .session(alice)
                                .with(csrf())
                                .param("group", group)
                                .param("title", "Dinner")
                                .param("amount", "100")
                                .param("payer", "alice")
                                .param("member", "alice", "bob"),
                        200);
        String expenseId = expense.path("expenses").get(0).path("id").asText();
        body(
                post("/api/delete")
                        .session(bob)
                        .with(csrf())
                        .param("group", group)
                        .param("id", expenseId),
                403);
        body(post("/api/reset").session(bob).with(csrf()).param("group", group), 403);
        body(
                post("/api/payments/demo-orders")
                        .session(alice)
                        .with(csrf())
                        .param("group", group)
                        .param("from", "bob")
                        .param("to", "alice"),
                403);
        String order =
                body(
                                post("/api/payments/demo-orders")
                                        .session(bob)
                                        .with(csrf())
                                        .param("group", group)
                                        .param("from", "bob")
                                        .param("to", "alice")
                                        .param("amount", "0.01"),
                                201)
                        .path("orderId")
                        .asText();
        body(
                post("/api/payments/demo-complete")
                        .session(alice)
                        .with(csrf())
                        .param("group", group)
                        .param("orderId", order)
                        .param("outcome", "success"),
                403);
        var paid =
                body(
                        post("/api/payments/demo-complete")
                                .session(bob)
                                .with(csrf())
                                .param("group", group)
                                .param("orderId", order)
                                .param("outcome", "success"),
                        200);
        for (var balance : paid.path("balances")) assertEquals(0, balance.path("cents").asLong());
        assertEquals(
                paid.path("balances"),
                body(
                                post("/api/payments/demo-complete")
                                        .session(bob)
                                        .with(csrf())
                                        .param("group", group)
                                        .param("orderId", order)
                                        .param("outcome", "success"),
                                200)
                        .path("balances"));
        body(get("/api/state").session(charlie).param("group", group), 403);
        mvc.perform(post("/api/auth/logout").session(alice).with(csrf()))
                .andExpect(status().isOk());
        assertTrue(alice.isInvalid());
    }
}

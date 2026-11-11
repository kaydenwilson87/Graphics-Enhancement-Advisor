package com.graphicsadvisor.app.data.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ApiErrorsTest {
    @Test
    public void usesTheDetailMessageWhenThereIsOne() {
        assertEquals("No active account found.", ApiErrors.message(401, "{\"detail\":\"No active account found.\"}"));
    }

    @Test
    public void prefixesFieldErrorsWithAReadableFieldName() {
        String body = "{\"email\":[\"An account with this email already exists.\"]}";
        assertEquals("Email: An account with this email already exists.", ApiErrors.message(400, body));
    }

    @Test
    public void replacesUnderscoresInFieldNames() {
        String body = "{\"release_year\":[\"A valid integer is required.\"]}";
        assertEquals("Release year: A valid integer is required.", ApiErrors.message(400, body));
    }

    @Test
    public void nonFieldErrorsAreShownWithoutAPrefix() {
        assertEquals("Passwords do not match.", ApiErrors.message(400, "{\"non_field_errors\":[\"Passwords do not match.\"]}"));
    }

    @Test
    public void emptyForbiddenResponseGetsAPermissionMessage() {
        assertEquals("You do not have permission to do that.", ApiErrors.message(403, ""));
    }

    @Test
    public void htmlOrEmptyBodiesFallBackToTheStatusCode() {
        assertEquals("Server error (500).", ApiErrors.message(500, "<html>Server Error</html>"));
        assertEquals("Server error (502).", ApiErrors.message(502, null));
    }
}

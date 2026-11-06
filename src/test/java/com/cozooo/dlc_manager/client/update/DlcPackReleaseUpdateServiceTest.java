package com.cozooo.dlc_manager.client.update;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DlcPackReleaseUpdateServiceTest {
    @Test
    void cnbUsesPublicReleasePageBeforeAuthenticatedApiFallback() throws Exception {
        Object repo = invoke("parseRepoCoordinates", new Class<?>[] {String.class}, "https://cnb.cool/team/sub/pack/-/releases");
        @SuppressWarnings("unchecked")
        List<Object> endpoints = (List<Object>) invoke("buildReleaseApiEndpoints", new Class<?>[] {repo.getClass()}, repo);

        assertEquals(2, endpoints.size());
        assertEquals("https://cnb.cool/team/sub/pack/-/releases?per_page=100", field(endpoints.get(0), "url"));
        assertEquals("https://api.cnb.cool/team%2Fsub%2Fpack/-/releases?page_size=100", field(endpoints.get(1), "url"));
        assertEquals("Authorization", field(endpoints.get(1), "authHeaderName"));
        String authHeader = (String) field(endpoints.get(1), "authHeaderValue");
        assertEquals("Bearer ", authHeader.substring(0, "Bearer ".length()));
        assertEquals(27, authHeader.length() - "Bearer ".length());
    }

    @Test
    void cnbAuthorizationHeaderUsesEmbeddedTokenWhenNoOverrideIsConfigured() throws Exception {
        String header = (String) invoke("cnbAuthorizationHeader", new Class<?>[0]);

        assertEquals("Bearer ", header.substring(0, "Bearer ".length()));
        assertEquals(27, header.length() - "Bearer ".length());
    }

    @Test
    void parsesBaseOnlyTagsAndAssetsWhileKeepingLegacyPairs() throws Exception {
        Object parsed = invoke("parseTag", new Class<?>[] {String.class}, "3.14.0-3.14.2");
        assertEquals("3.14.0", field(parsed, "oldBase"));
        assertEquals(null, field(parsed, "oldDlc"));
        assertEquals("3.14.2", field(parsed, "newBase"));
        assertEquals(null, field(parsed, "newDlc"));

        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) invoke(
            "buildAssetNameCandidates",
            new Class<?>[] {String.class, String.class, String.class},
            "Universal_", "3.14.2", null
        );
        assertEquals(List.of("Universal_3.14.2.zip"), names);

        Object legacy = invoke("parseTag", new Class<?>[] {String.class}, "3.14.0_1.0.0-3.14.2_1.0.0");
        assertEquals("1.0.0", field(legacy, "oldDlc"));
        assertEquals("1.0.0", field(legacy, "newDlc"));
    }

    private static Object invoke(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = DlcPackReleaseUpdateService.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        Object receiver = Modifier.isStatic(method.getModifiers()) ? null : DlcPackReleaseUpdateService.class.getField("INSTANCE").get(null);
        return method.invoke(receiver, args);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}

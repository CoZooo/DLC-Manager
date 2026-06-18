package com.cozooo.dlc_manager.client.download;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DlcDownloadCandidateTest {
    @Test
    void parsePriorityReturnsEmptyForMissingOrUnsupportedValues() {
        assertEquals("", DlcDownloadCandidate.parsePriority(null));
        assertEquals("", DlcDownloadCandidate.parsePriority(JsonNull.INSTANCE));

        JsonObject object = new JsonObject();
        assertEquals("", DlcDownloadCandidate.parsePriority(object));
    }

    @Test
    void parsePrioritySupportsStringValue() {
        assertEquals("curseforge,modrinth", DlcDownloadCandidate.parsePriority(new JsonPrimitive("curseforge,modrinth")));
    }

    @Test
    void parsePrioritySupportsArrayValueAndSkipsBlankItems() {
        JsonArray array = new JsonArray();
        array.add(" curseforge ");
        array.add("");
        array.add("modrinth");
        array.add(JsonNull.INSTANCE);
        array.add(" directurl ");

        assertEquals("curseforge,modrinth,directurl", DlcDownloadCandidate.parsePriority(array));
    }

    @Test
    void readPriorityIgnoresCase() {
        JsonObject object = new JsonObject();
        JsonArray array = new JsonArray();
        array.add("modrinth");
        array.add("curseforge");
        object.add("Priority", array);

        assertEquals("modrinth,curseforge", DlcDownloadCandidate.readPriority(object));
    }

    @Test
    void directCandidateKeepsUrlAndHasNoKnownMetadata() {
        DlcDownloadCandidate candidate = DlcDownloadCandidate.direct("https://example.com/file.jar");

        assertEquals("https://example.com/file.jar", candidate.url);
        assertEquals(true, candidate.direct);
        assertEquals(null, candidate.fileNameOverride);
        assertEquals(null, candidate.hashType);
        assertEquals(null, candidate.hashValue);
        assertEquals(-1L, candidate.expectedSize);
    }

    @Test
    void curseForgeCandidatePrefersSha1ThenFallsBackToMd5() {
        DlcCurseForgeClient.CurseForgeFile file = new DlcCurseForgeClient.CurseForgeFile();
        file.url = "https://example.com/cf.jar";
        file.fileName = "cf.jar";
        file.sha1 = "sha1-value";
        file.md5 = "md5-value";
        file.size = 123L;

        DlcDownloadCandidate withSha1 = DlcDownloadCandidate.curseForge(file);
        assertEquals("https://example.com/cf.jar", withSha1.url);
        assertFalse(withSha1.direct);
        assertEquals("cf.jar", withSha1.fileNameOverride);
        assertEquals("sha1", withSha1.hashType);
        assertEquals("sha1-value", withSha1.hashValue);
        assertEquals(123L, withSha1.expectedSize);

        file.sha1 = " ";
        DlcDownloadCandidate withMd5 = DlcDownloadCandidate.curseForge(file);
        assertEquals("md5", withMd5.hashType);
        assertEquals("md5-value", withMd5.hashValue);
    }

    @Test
    void modrinthCandidatePrefersSha1ThenFallsBackToSha512() {
        DlcModrinthClient.ModrinthVersion version = new DlcModrinthClient.ModrinthVersion();
        version.url = "https://example.com/mr.jar";
        version.fileName = "mr.jar";
        version.sha1 = "sha1-value";
        version.sha512 = "sha512-value";
        version.size = 456L;

        DlcDownloadCandidate withSha1 = DlcDownloadCandidate.modrinth(version);
        assertEquals("https://example.com/mr.jar", withSha1.url);
        assertFalse(withSha1.direct);
        assertEquals("mr.jar", withSha1.fileNameOverride);
        assertEquals("sha1", withSha1.hashType);
        assertEquals("sha1-value", withSha1.hashValue);
        assertEquals(456L, withSha1.expectedSize);

        version.sha1 = "";
        DlcDownloadCandidate withSha512 = DlcDownloadCandidate.modrinth(version);
        assertEquals("sha512", withSha512.hashType);
        assertEquals("sha512-value", withSha512.hashValue);
    }

    @Test
    void resolveReturnsDirectCandidatesWhenDirectUrlHasPriority() throws Exception {
        List<DlcDownloadCandidate> candidates = DlcDownloadCandidate.resolve(
            List.of("directurl", "curseforge"),
            null,
            null,
            null,
            null,
            List.of("https://example.com/a.jar", " ", "https://example.com/b.jar")
        );

        assertEquals(2, candidates.size());
        assertEquals("https://example.com/a.jar", candidates.get(0).url);
        assertEquals("https://example.com/b.jar", candidates.get(1).url);
        assertEquals(true, candidates.get(0).direct);
        assertEquals(true, candidates.get(1).direct);
    }

    @Test
    void resolverFallsBackToDirectUrlsWhenPriorityOmitsDirectUrl() throws Exception {
        DlcDownloadCandidate.Resolver resolver = DlcDownloadCandidate.resolver((com.cozooo.dlc_manager.client.remote.DlcRemoteEntry) null);
        assertEquals(List.of(), resolver.next());

        List<DlcDownloadCandidate> candidates = DlcDownloadCandidate.resolve(
            List.of("curseforge", "modrinth"),
            null,
            null,
            null,
            null,
            List.of("https://example.com/fallback.jar")
        );

        assertEquals(1, candidates.size());
        assertEquals("https://example.com/fallback.jar", candidates.get(0).url);
        assertEquals(true, candidates.get(0).direct);
    }

    @Test
    void resolverDeduplicatesPriorityOrder() throws Exception {
        List<DlcDownloadCandidate> candidates = DlcDownloadCandidate.resolve(
            List.of("directurl", "directurl", "curseforge"),
            null,
            null,
            null,
            null,
            List.of("https://example.com/one.jar")
        );

        assertEquals(1, candidates.size());
        assertEquals("https://example.com/one.jar", candidates.get(0).url);
    }
}

package com.cozooo.dclanguage;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Objects;

public final class DcDocuments {
    private DcDocuments() { }
    public static DcDocument create() { return new DcDocument(new JsonObject()); }
    public static DcDocument fromJson(JsonObject value) { return new DcDocument(value); }
    public static DcDocument read(Path path) throws IOException {
        return readText(Files.readString(Objects.requireNonNull(path), StandardCharsets.UTF_8));
    }
    public static java.util.List<DcCategoryHeader> categoryHeaders(String text) throws DcParseException {
        return DcParser.categoryHeaders(Objects.requireNonNull(text));
    }
    public static DcDocument readText(String text) throws DcParseException {
        DcParser.Parsed parsed = DcParser.parse(Objects.requireNonNull(text));
        return new DcDocument(parsed.root(), parsed);
    }
    public static void write(Path path, DcDocument document) throws IOException { write(path, document, DcWriteOptions.PRESERVE_FORMATTING); }
    public static void write(Path path, DcDocument document, DcWriteOptions options) throws IOException {
        String text = writeText(document, options);
        Path target = Objects.requireNonNull(path).toAbsolutePath().normalize();
        if (Files.isSymbolicLink(target)) target = target.toRealPath();
        if (Files.isDirectory(target)) throw new IOException("Document target is a directory: " + path);
        Files.createDirectories(target.getParent());
        Path staging = Files.createTempFile(target.getParent(), ".dclanguage-", ".tmp");
        try {
            Files.writeString(staging, text, StandardCharsets.UTF_8);
            if (Files.exists(target)) copyPermissions(target, staging);
            try { Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(staging); }
    }
    private static void copyPermissions(Path source, Path target) throws IOException {
        PosixFileAttributeView fromPosix = Files.getFileAttributeView(source, PosixFileAttributeView.class);
        PosixFileAttributeView toPosix = Files.getFileAttributeView(target, PosixFileAttributeView.class);
        if (fromPosix != null && toPosix != null) {
            var attributes = fromPosix.readAttributes();
            toPosix.setOwner(attributes.owner());
            toPosix.setGroup(attributes.group());
            toPosix.setPermissions(attributes.permissions());
        }
        AclFileAttributeView fromAcl = Files.getFileAttributeView(source, AclFileAttributeView.class);
        AclFileAttributeView toAcl = Files.getFileAttributeView(target, AclFileAttributeView.class);
        if (fromAcl != null && toAcl != null) {
            toAcl.setOwner(fromAcl.getOwner());
            toAcl.setAcl(fromAcl.getAcl());
        }
    }
    public static String writeValue(com.google.gson.JsonElement value) {
        String text = DcWriter.formatValue(value, "");
        try {
            var parsed = DcParser.parse("value " + com.cozooo.dclanguage.generated.DcGeneratedSyntax.TOKEN_ASSIGNMENT + " " + text).root().get("value");
            if (!DcValues.same(value == null ? com.google.gson.JsonNull.INSTANCE : value, parsed)) throw new IllegalStateException("Written value differs from its data");
        } catch (DcParseException exception) { throw new IllegalStateException("Cannot write value without data loss", exception); }
        return text;
    }
    public static String writeText(DcDocument document) { return writeText(document, DcWriteOptions.PRESERVE_FORMATTING); }
    public static String writeText(DcDocument document, DcWriteOptions options) {
        Objects.requireNonNull(document);
        Objects.requireNonNull(options);
        for (var path : document.sectionPaths) {
            var value = DcDocument.valueAt(document.root(), path);
            if (value == null || !value.isJsonObject()) throw new IllegalArgumentException("Category is missing or is not an object: " + path);
        }
        String text = options == DcWriteOptions.NORMALIZE || document.source == null ? DcWriter.normalize(document.root()) : DcWriter.preserve(document);
        try {
            if (!DcValues.same(DcParser.parse(text).root(), document.root())) throw new IllegalStateException("Written document differs from its values");
        } catch (DcParseException exception) { throw new IllegalStateException("Cannot write document without data loss", exception); }
        return text;
    }
}

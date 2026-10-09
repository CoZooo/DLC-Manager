package com.cozooo.dclanguage.mps.persistence;

import com.cozooo.dclanguage.*;
import com.cozooo.dclanguage.mps.*;
import jetbrains.mps.smodel.*;
import jetbrains.mps.smodel.loading.ModelLoadingState;
import jetbrains.mps.smodel.persistence.def.ModelReadException;
import jetbrains.mps.extapi.model.PersistenceProblem;
import jetbrains.mps.extapi.persistence.FileDataSource;
import org.jetbrains.mps.openapi.language.SLanguage;
import org.jetbrains.mps.openapi.model.SModelReference;
import org.jetbrains.mps.openapi.model.SNode;
import org.jetbrains.mps.openapi.persistence.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class DcModelDescriptor extends EditableModelDescriptor {
    private String original;
    private boolean saving;
    private final jetbrains.mps.extapi.persistence.ModelSourceChangeTracker tracker = new jetbrains.mps.extapi.persistence.ModelSourceChangeTracker(() -> {
        if (saving) return;
        var repository = getRepository();
        if (repository != null) repository.getModelAccess().runWriteAction(this::reloadFromSource);
    });
    private List<org.jetbrains.mps.openapi.model.SModel.Problem> problems = List.of();
    DcModelDescriptor(SModelReference reference, DataSource source) { super(reference, source); }
    @Override protected ModelLoadResult<jetbrains.mps.smodel.SModel> createModel() {
        try {
            original = getSource().isAlive() ? read(getSource()) : null;
            SNode root = DcModelCodec.read(getName().getSimpleName(), original == null ? "" : original);
            var model = new DefaultSModel(getReference());
            model.addRootNode(root);
            problems = List.of();
            tracker.updateTimestamp(getSource());
            return new ModelLoadResult<>(model, ModelLoadingState.FULLY_LOADED);
        } catch (IOException exception) {
            problems = List.of(new PersistenceProblem(org.jetbrains.mps.openapi.model.SModel.Problem.Kind.Load, exception.getMessage(), null, true));
            var model = new DefaultSModel.InvalidDefaultSModel(getReference(), new ModelReadException(exception.getMessage(), exception));
            return new ModelLoadResult<>(model, ModelLoadingState.NOT_LOADED);
        }
    }
    static String read(DataSource source) throws IOException {
        try (InputStream input = ((StreamDataSource) source).openInputStream()) { return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(input.readAllBytes())).toString(); }
    }
    public void saveChecked() throws IOException, ModelSaveException {
        assertCanChange();
        getModelData();
        if (getLoadingState() == ModelLoadingState.NOT_LOADED) throw new ModelSaveException("Cannot overwrite an invalid DC document", problems);
        try {
            if (getSource().isReadOnly()) throw new IOException("DC document is read-only: " + getSource().getLocation());
            String disk = getSource().isAlive() ? read(getSource()) : null;
            if (!Objects.equals(original, disk)) throw new IOException("DC document changed on disk; reload before saving: " + getSource().getLocation());
            SNode root = root(this);
            DcDocument document = DcModelCodec.document(root);
            String text = DcDocuments.writeText(document);
            saving = true;
            try { write(getSource(), document); } finally { saving = false; }
            tracker.updateTimestamp(getSource());
            original = text;
            problems = List.of();
            DcModelCodec.remember(root, text);
            setChanged(false);
            fireModelSaved();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ModelSaveException(exception.getMessage(), List.of(new PersistenceProblem(org.jetbrains.mps.openapi.model.SModel.Problem.Kind.Save, exception.getMessage(), null, true)), exception);
        }
    }
    static void write(DataSource source, DcDocument document) throws IOException {
        if (source.isReadOnly()) throw new IOException("DC document is read-only: " + source.getLocation());
        if (source instanceof FileDataSource file) {
            DcDocuments.write(Path.of(file.getFile().getPath()), document);
            source.refresh();
        } else {
            byte[] content = DcDocuments.writeText(document).getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = ((StreamDataSource) source).openOutputStream()) { output.write(content); }
        }
    }
    public static SNode root(org.jetbrains.mps.openapi.model.SModel model) {
        var roots = model.getRootNodes().iterator();
        if (!roots.hasNext()) throw new IllegalArgumentException("A .dc model needs one Document root");
        SNode root = roots.next();
        if (roots.hasNext()) throw new IllegalArgumentException("A .dc model supports exactly one Document root");
        return root;
    }
    @Override public void save() {
        try { saveChecked(); }
        catch (IOException | ModelSaveException exception) {
            problems = List.of(new PersistenceProblem(org.jetbrains.mps.openapi.model.SModel.Problem.Kind.Save, exception.getMessage(), null, true));
            fireProblemsDetected(problems);
            throw new IllegalStateException("Cannot save DC document: " + exception.getMessage(), exception);
        }
    }
    @Override public void attach(org.jetbrains.mps.openapi.module.SRepository repository) {
        super.attach(repository);
        tracker.attach(this);
    }
    @Override public void detach() {
        tracker.detach(this);
        super.detach();
    }
    @Override public void reloadFromSource() {
        if (isChanged()) { fireConflictDetected(); return; }
        replace(createModel());
        setChanged(false);
    }
    @Override public Collection<SLanguage> importedLanguageIds() { return List.of(DcConcepts.LANGUAGE); }
    @Override public Iterable<org.jetbrains.mps.openapi.model.SModel.Problem> getProblems() { return problems; }
}

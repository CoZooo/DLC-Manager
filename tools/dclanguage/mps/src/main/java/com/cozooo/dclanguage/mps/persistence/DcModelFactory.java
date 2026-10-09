package com.cozooo.dclanguage.mps.persistence;

import com.cozooo.dclanguage.mps.DcModelCodec;
import org.jetbrains.mps.openapi.persistence.*;
import org.jetbrains.mps.openapi.persistence.datasource.*;
import org.jetbrains.mps.openapi.model.*;
import java.io.IOException;
import java.util.List;

public final class DcModelFactory implements ModelFactory {
    public enum Type implements ModelFactoryType {
        DC;
        @Override public String getFormatTitle() { return "DC configuration (.dc)"; }
    }
    @SuppressWarnings("removal")
    @Override public List<DataSourceType> getPreferredDataSourceTypes() { return List.of(FileExtensionDataSourceType.of("dc")); }
    @Override public ModelFactoryType getType() { return Type.DC; }
    @Override public boolean supports(DataSource source) { return source instanceof StreamDataSource && FileExtensionDataSourceType.of("dc").equals(source.getType()); }
    @Override public SModel load(DataSource source, ModelLoadingOption... options) throws UnsupportedDataSourceException {
        require(source);
        String file = ((StreamDataSource) source).getStreamName();
        String name = file.endsWith(".dc") ? file.substring(0, file.length() - 3) : file;
        return descriptor(source, new SModelName(name));
    }
    @Override public SModel create(DataSource source, SModelName name, ModelLoadingOption... options) throws UnsupportedDataSourceException, ModelCreationException {
        require(source);
        if (source.isAlive()) throw new ModelCreationException("DC document already exists: " + source.getLocation(), List.of());
        return descriptor(source, name);
    }
    private DcModelDescriptor descriptor(DataSource source, SModelName name) {
        PersistenceFacade persistence = PersistenceFacade.getInstance();
        SModelId id = persistence.createModelId("path:" + source.getLocation());
        return new DcModelDescriptor(persistence.createModelReference(null, id, name), source);
    }
    private void require(DataSource source) throws UnsupportedDataSourceException { if (!supports(source)) throw new UnsupportedDataSourceException(source); }
    @Override public void save(SModel model, DataSource source) throws ModelSaveException, IOException {
        require(source);
        if (model instanceof DcModelDescriptor dc && dc.getSource().equals(source)) dc.saveChecked();
        else DcModelDescriptor.write(source, DcModelCodec.document(DcModelDescriptor.root(model)));
    }
}

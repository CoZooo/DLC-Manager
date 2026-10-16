package com.cozooo.dclanguage.definition;

import jetbrains.mps.smodel.language.LanguageRuntime;
import jetbrains.mps.smodel.adapter.ids.SLanguageId;
import jetbrains.mps.smodel.runtime.*;
import jetbrains.mps.openapi.editor.descriptor.EditorAspectDescriptor;
import jetbrains.mps.text.rt.TextGenAspectDescriptor;
import java.util.Collection;
import org.jetbrains.mps.openapi.language.SLanguage;

public final class Language extends LanguageRuntime {
    @Override public String getNamespace() { return DefinitionConcepts.NAMESPACE; }
    @Override public SLanguageId getId() { return SLanguageId.deserialize("4b259ac3-0fd5-481e-b370-5e307e623184"); }
    @Override public int getVersion() { return 0; }
    @Override protected void fillExtendedLanguages(Collection<SLanguage> languages) {
        languages.add(jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory.getLanguage(0xceab519525ea4f22L, 0x9b92103b95ca8c0cL, "jetbrains.mps.lang.core"));
    }
    @Override protected <T extends ILanguageAspect> T createAspect(Class<T> type) {
        if (type == StructureAspectDescriptor.class) return type.cast(new com.cozooo.dclanguage.definition.structure.DefinitionStructure());
        if (type == EditorAspectDescriptor.class) return type.cast(new com.cozooo.dclanguage.definition.editor.DefinitionEditors());
        if (type == jetbrains.mps.lang.typesystem.runtime.IHelginsDescriptor.class) return type.cast(new DefinitionTypesystem());
        if (type == TextGenAspectDescriptor.class) return type.cast(new com.cozooo.dclanguage.definition.textgen.DefinitionTextGen());
        return null;
    }
}

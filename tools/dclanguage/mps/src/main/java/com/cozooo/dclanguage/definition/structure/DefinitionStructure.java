package com.cozooo.dclanguage.definition.structure;

import static com.cozooo.dclanguage.definition.DefinitionConcepts.*;
import jetbrains.mps.smodel.adapter.ids.*;
import jetbrains.mps.smodel.runtime.*;
import jetbrains.mps.smodel.runtime.impl.ConceptDescriptorBuilder2;
import java.util.*;

public final class DefinitionStructure extends BaseStructureAspectDescriptor {
    private final Map<SConceptId, ConceptDescriptor> concepts = new LinkedHashMap<>();
    public DefinitionStructure() {
        for (Concept definition : CONCEPTS.values()) {
            var builder = new ConceptDescriptorBuilder2(NAMESPACE, definition.name(), HIGH, LOW, definition.id()).version(3)
                .class_(false, false, definition.root()).origin(STRUCTURE + "/" + definition.id())
                .super_(0xceab519525ea4f22L, 0x9b92103b95ca8c0cL, 0x10802efe25aL)
                .parent(0xceab519525ea4f22L, 0x9b92103b95ca8c0cL, 0x10802efe25aL)
                .parent(0xceab519525ea4f22L, 0x9b92103b95ca8c0cL, 0x110396eaaa4L);
            for (Property property : definition.properties()) {
                var type = switch (property.type()) { case "boolean" -> PrimitiveTypeId.BOOLEAN; case "integer" -> PrimitiveTypeId.INTEGER; default -> PrimitiveTypeId.STRING; };
                builder.property(property.name(), property.id()).origin(Long.toString(property.id())).type(type).done();
            }
            for (Child child : definition.children()) builder.aggregate(child.name(), child.id()).origin(Long.toString(child.id()))
                .target(HIGH, LOW, CONCEPTS.get(child.target()).id()).multiple(true).optional(true).done();
            var descriptor = builder.create();concepts.put(descriptor.getId(), descriptor);
        }
    }
    @Override public Collection<ConceptDescriptor> getDescriptors() { return concepts.values(); }
    @Override public ConceptDescriptor getDescriptor(SConceptId id) { return concepts.get(id); }
    @Override public void reportDependencies(Dependencies dependencies) { dependencies.extendedLanguage(0xceab519525ea4f22L, 0x9b92103b95ca8c0cL, "jetbrains.mps.lang.core"); }
}

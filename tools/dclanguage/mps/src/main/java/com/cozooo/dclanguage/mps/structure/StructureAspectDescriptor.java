package com.cozooo.dclanguage.mps.structure;

import static com.cozooo.dclanguage.mps.DcConcepts.*;
import jetbrains.mps.smodel.adapter.ids.*;
import jetbrains.mps.smodel.runtime.*;
import jetbrains.mps.smodel.runtime.impl.ConceptDescriptorBuilder2;
import java.util.*;

public final class StructureAspectDescriptor extends BaseStructureAspectDescriptor {
    private final Map<SConceptId, ConceptDescriptor> concepts = new LinkedHashMap<>();
    public StructureAspectDescriptor() {
        var document = builder(1001, "Document", true, false, 0);
        document.parent(0xceab519525ea4f22L, 0x9b92103b95ca8c0cL, 0x110396eaaa4L);
        document.aggregate("members", 1201).origin("1201").target(HIGH, LOW, 1002).multiple(true).optional(true).done();
        add(document);
        var member = builder(1002, "Member", false, false, 0);
        member.parent(0xceab519525ea4f22L, 0x9b92103b95ca8c0cL, 0x110396eaaa4L);
        member.aggregate("value", 1202).origin("1202").target(HIGH, LOW, 1003).multiple(false).optional(false).done();
        add(member);
        add(builder(1003, "Value", false, true, 0));
        var object = builder(1004, "ObjectValue", false, false, 1003);
        object.alias("object").aggregate("members", 1204).origin("1204").target(HIGH, LOW, 1002).multiple(true).optional(true).done();
        add(object);
        var array = builder(1005, "ArrayValue", false, false, 1003);
        array.alias("array").aggregate("values", 1205).origin("1205").target(HIGH, LOW, 1003).multiple(true).optional(true).done();
        add(array);
        var scalar = builder(1006, "ScalarValue", false, false, 1003);
        scalar.alias("value").property("text", 1106).origin("1106").type(PrimitiveTypeId.STRING).done();
        add(scalar);
    }
    private ConceptDescriptorBuilder2 builder(long id, String name, boolean root, boolean abstractConcept, long parent) {
        var result = new ConceptDescriptorBuilder2(NAMESPACE, name, HIGH, LOW, id).version(3).class_(false, abstractConcept, root)
            .origin("r:2de281de-6087-49df-acad-52ea3a52d028(com.cozooo.dclanguage.mps.structure)/" + id);
        if (parent != 0) result.super_(HIGH, LOW, parent).parent(HIGH, LOW, parent);
        else result.super_(0xceab519525ea4f22L, 0x9b92103b95ca8c0cL, 0x10802efe25aL).parent(0xceab519525ea4f22L, 0x9b92103b95ca8c0cL, 0x10802efe25aL);
        return result;
    }
    private void add(ConceptDescriptorBuilder2 builder) { var descriptor = builder.create(); concepts.put(descriptor.getId(), descriptor); }
    @Override public Collection<ConceptDescriptor> getDescriptors() { return concepts.values(); }
    @Override public ConceptDescriptor getDescriptor(SConceptId id) { return concepts.get(id); }
    @Override public void reportDependencies(Dependencies dependencies) { dependencies.extendedLanguage(0xceab519525ea4f22L, 0x9b92103b95ca8c0cL, "jetbrains.mps.lang.core"); }
}

package com.cozooo.dclanguage.mps;

import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory;
import org.jetbrains.mps.openapi.language.*;

public final class DcConcepts {
    public static final String NAMESPACE = "com.cozooo.dclanguage.mps";
    public static final long HIGH = 0x872f19e2dc714dbaL;
    public static final long LOW = 0xa42a0b0d8813df59L;
    public static final SLanguage LANGUAGE = MetaAdapterFactory.getLanguage(HIGH, LOW, NAMESPACE);
    public static final SConcept DOCUMENT = concept(1001, "Document");
    public static final SConcept MEMBER = concept(1002, "Member");
    public static final SConcept VALUE = concept(1003, "Value");
    public static final SConcept OBJECT = concept(1004, "ObjectValue");
    public static final SConcept ARRAY = concept(1005, "ArrayValue");
    public static final SConcept SCALAR = concept(1006, "ScalarValue");
    public static final SProperty NAME = MetaAdapterFactory.getProperty(0xceab519525ea4f22L, 0x9b92103b95ca8c0cL, 0x110396eaaa4L, 0x110396ec041L, "name");
    public static final SProperty TEXT = MetaAdapterFactory.getProperty(HIGH, LOW, 1006, 1106, "text");
    public static final SContainmentLink ROOT_MEMBERS = link(1001, 1201, "members");
    public static final SContainmentLink MEMBER_VALUE = link(1002, 1202, "value");
    public static final SContainmentLink OBJECT_MEMBERS = link(1004, 1204, "members");
    public static final SContainmentLink ARRAY_VALUES = link(1005, 1205, "values");
    private DcConcepts() { }
    private static SConcept concept(long id, String name) { return MetaAdapterFactory.getConcept(HIGH, LOW, id, NAMESPACE + ".structure." + name); }
    private static SContainmentLink link(long concept, long id, String name) { return MetaAdapterFactory.getContainmentLink(HIGH, LOW, concept, id, name); }
}

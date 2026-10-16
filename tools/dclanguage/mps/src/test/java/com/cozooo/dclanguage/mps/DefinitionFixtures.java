package com.cozooo.dclanguage.mps;

import com.cozooo.dclanguage.definition.DefinitionConcepts;
import org.jetbrains.mps.openapi.model.SNode;

final class DefinitionFixtures {
    private DefinitionFixtures() { }

    static SNode registerProfile(SNode definition) {
        SNode profile = node("SchemaProfile", "strict-example");
        SNode category = node("Category", "basic");
        category.setProperty(DefinitionConcepts.property("Category", "required"), "true");
        SNode field = node("Field", "identifier");
        field.setProperty(DefinitionConcepts.property("Field", "type"), "string");
        field.setProperty(DefinitionConcepts.property("Field", "required"), "true");
        SNode constraint = node("Constraint", "minLength");
        constraint.setProperty(DefinitionConcepts.property("Constraint", "value"), "1");
        field.addChild(DefinitionConcepts.child("Field", "constraints"), constraint);
        category.addChild(DefinitionConcepts.child("Category", "fields"), field);
        profile.addChild(DefinitionConcepts.child("SchemaProfile", "categories"), category);
        definition.addChild(DefinitionConcepts.child("Definition", "profiles"), profile);
        return profile;
    }

    private static SNode node(String concept, String name) {
        SNode node = new jetbrains.mps.smodel.SNode(DefinitionConcepts.concept(concept));
        node.setProperty(DefinitionConcepts.NAME, name);
        return node;
    }
}

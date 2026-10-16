package com.cozooo.dclanguage.definition;

import jetbrains.mps.lang.typesystem.runtime.*;
import jetbrains.mps.typesystem.inference.TypeCheckingContext;
import jetbrains.mps.errors.messageTargets.NodeMessageTarget;
import org.jetbrains.mps.openapi.language.SAbstractConcept;
import org.jetbrains.mps.openapi.model.SNode;

public final class DefinitionTypesystem extends BaseHelginsDescriptor {
    public DefinitionTypesystem(){myNonTypesystemRules.add(new Rule());}
    private static final class Rule extends AbstractNonTypesystemRule_Runtime {
        @Override public SAbstractConcept getApplicableConcept(){return DefinitionConcepts.concept("Definition");}
        @Override public IsApplicableStatus isApplicableAndPattern(SNode node){return new IsApplicableStatus(node.getConcept().isSubConceptOf(getApplicableConcept()),null);}
        @Override public void applyRule(SNode node,TypeCheckingContext context,IsApplicableStatus status){
            for(String issue:DefinitionCompiler.validate(node))context.reportTypeError(node,issue,DefinitionConcepts.STRUCTURE,"2001",null,new NodeMessageTarget());
        }
    }
}

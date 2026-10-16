package com.cozooo.dclanguage.mps.typesystem;

import com.cozooo.dclanguage.mps.DcConcepts;
import com.cozooo.dclanguage.mps.DcModelCodec;
import jetbrains.mps.lang.typesystem.runtime.*;
import jetbrains.mps.typesystem.inference.TypeCheckingContext;
import jetbrains.mps.errors.messageTargets.NodeMessageTarget;
import org.jetbrains.mps.openapi.language.SAbstractConcept;
import org.jetbrains.mps.openapi.model.SNode;

public final class DcTypesystem extends BaseHelginsDescriptor {
    public DcTypesystem() { myNonTypesystemRules.add(new DocumentRule()); }
    private static final class DocumentRule extends AbstractNonTypesystemRule_Runtime {
        @Override public SAbstractConcept getApplicableConcept() { return DcConcepts.DOCUMENT; }
        @Override public IsApplicableStatus isApplicableAndPattern(SNode node) { return new IsApplicableStatus(node.getConcept().isSubConceptOf(getApplicableConcept()), null); }
        @Override public void applyRule(SNode node, TypeCheckingContext context, IsApplicableStatus status) {
            for (String issue : DcModelCodec.validate(node)) context.reportTypeError(node, issue,
                "r:2de281de-6087-49df-acad-52ea3a52d028(com.cozooo.dclanguage.mps.structure)", "1001", null, new NodeMessageTarget());
        }
    }
}

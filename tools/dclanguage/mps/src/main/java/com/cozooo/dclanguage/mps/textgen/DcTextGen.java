package com.cozooo.dclanguage.mps.textgen;

import static com.cozooo.dclanguage.mps.DcConcepts.*;
import com.cozooo.dclanguage.mps.DcModelCodec;
import jetbrains.mps.text.rt.*;
import jetbrains.mps.text.impl.TextGenSupport;
import org.jetbrains.mps.openapi.language.SAbstractConcept;

public final class DcTextGen implements TextGenAspectDescriptor {
    @Override public TextGenDescriptor getDescriptor(SAbstractConcept concept) {
        if (!concept.equals(DOCUMENT)) return null;
        return new TextGenDescriptorBase() {
            @Override public void generateText(TextGenContext context) {
                TextGenSupport output = new TextGenSupport(context);
                try { output.append(DcModelCodec.write(context.getPrimaryInput())); }
                catch (IllegalArgumentException exception) { output.reportError(exception.getMessage()); }
            }
        };
    }
    @Override public void breakdownToUnits(TextGenModelOutline outline) {
        for (var root : outline.getModel().getRootNodes()) {
            if (!root.getConcept().equals(DOCUMENT)) continue;
            String name = root.getProperty(NAME);
            if (name == null || name.isBlank()) name = "document";
            name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
            outline.registerTextUnit(name.endsWith(".dc") ? name : name + ".dc", root);
        }
    }
}

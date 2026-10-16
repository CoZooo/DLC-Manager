package com.cozooo.dclanguage.definition.textgen;

import com.cozooo.dclanguage.definition.*;
import jetbrains.mps.text.rt.*;
import jetbrains.mps.text.impl.TextGenSupport;
import org.jetbrains.mps.openapi.language.SAbstractConcept;
import org.jetbrains.mps.openapi.persistence.StreamDataSource;

public final class DefinitionTextGen implements TextGenAspectDescriptor {
    @Override public TextGenDescriptor getDescriptor(SAbstractConcept concept) {
        if (!concept.equals(DefinitionConcepts.concept("Definition"))) return null;
        return new TextGenDescriptorBase() {
            @Override public void generateText(TextGenContext context) {
                var output=new TextGenSupport(context);
                try {
                    var source=(StreamDataSource)context.getPrimaryInput().getModel().getSource();String hash;
                    try(var input=source.openInputStream()){hash=DefinitionCompiler.sha256(input.readAllBytes());}
                    var files=DefinitionCompiler.files(context.getPrimaryInput(),hash);
                    String path=output.getContextObject("dc.generatedPath",String.class);
                    output.append(files.get(path==null?DefinitionCompiler.METADATA_PATH:path));
                }catch(Exception exception){output.reportError(exception.getMessage());}
            }
        };
    }
    @Override public void breakdownToUnits(TextGenModelOutline outline) {
        for(var root:outline.getModel().getRootNodes())if(root.getConcept().equals(DefinitionConcepts.concept("Definition"))) {
            for(String path:java.util.List.of(DefinitionCompiler.METADATA_PATH,DefinitionCompiler.JAVA_PATH,"java/com/cozooo/dclanguage/DcParser.java","java/com/cozooo/dclanguage/DcWriter.java")) {
                int slash=path.lastIndexOf('/');
                var unit=new jetbrains.mps.text.impl.RegularTextUnit(root,path.substring(slash+1),path.substring(0,slash),java.nio.charset.StandardCharsets.UTF_8,outline.getPlatform());
                unit.addContextObject("dc.generatedPath",path);outline.registerTextUnit(unit);
            }
        }
    }
}

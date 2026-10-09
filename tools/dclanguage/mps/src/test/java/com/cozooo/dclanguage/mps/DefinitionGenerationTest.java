package com.cozooo.dclanguage.mps;

import static org.junit.jupiter.api.Assertions.*;
import com.cozooo.dclanguage.definition.*;
import jetbrains.mps.tool.environment.*;
import jetbrains.mps.smodel.language.LanguageRegistry;
import jetbrains.mps.openapi.editor.descriptor.EditorAspectDescriptor;
import jetbrains.mps.text.rt.*;
import jetbrains.mps.text.TextUnit;
import jetbrains.mps.text.impl.RegularTextUnit;
import org.jetbrains.mps.openapi.model.SNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DefinitionGenerationTest {
    private MpsEnvironment environment;
    private jetbrains.mps.project.Project project;
    private SNode definition;
    private Path modelFile;
    @TempDir Path temporary;
    @BeforeAll void openDefinitionProject() {
        environment=new MpsEnvironment(EnvironmentConfig.emptyConfig().withCorePlugin().withRepositoryPlugin().withBootstrapLibraries().withWorkbenchPath());environment.init();
        project=environment.openProject(Path.of(System.getProperty("dc.mps.project")).toFile());modelFile=Path.of(System.getProperty("dc.mps.project"),"solutions/definition/models/dc.mps");
        project.getModelAccess().runReadAction(()->{for(var module:project.getModules())for(var model:module.getModels())for(var root:model.getRootNodes())if(root.getConcept().equals(DefinitionConcepts.concept("Definition")))definition=root;});assertNotNull(definition);
    }
    @AfterAll void close(){if(project!=null)environment.closeProject(project);if(environment!=null)environment.dispose();}
    private String modelHash(){try{return DefinitionCompiler.sha256(Files.readAllBytes(modelFile));}catch(Exception e){throw new RuntimeException(e);}}
    @Test void nativeDefinitionHasStructuredRulesAndEditors() {
        project.getModelAccess().runReadAction(()->{
            var runtime=environment.getPlatform().findComponent(LanguageRegistry.class).getLanguage(DefinitionConcepts.LANGUAGE);assertNotNull(runtime);
            for(String name:DefinitionConcepts.CONCEPTS.keySet()){
                var concept=DefinitionConcepts.concept(name);assertTrue(concept.isValid());assertNotNull(concept.getSourceNode().resolve(project.getRepository()));assertEquals(1,runtime.getAspect(EditorAspectDescriptor.class).getDeclaredEditors(concept).size());
            }
            assertTrue(DefinitionCompiler.children(definition,"tokens").size()>=15);
            assertTrue(DefinitionCompiler.children(definition,"profiles").size()>=10);
            assertTrue(DefinitionCompiler.validate(definition).isEmpty());
        });
    }
    @Test void generatedFilesAreDeterministicAndCurrent() {
        project.getModelAccess().runReadAction(()->{
            try{
                Path output=temporary.resolve("generated");DefinitionCompiler.emit(definition,modelFile,output,false);DefinitionCompiler.emit(definition,modelFile,output,true);
                DefinitionCompiler.emit(definition,modelFile,Path.of(System.getProperty("dc.mps.project"),"../src/generated"),true);
                assertTrue(Files.readString(output.resolve(DefinitionCompiler.JAVA_PATH)).contains("TOKEN_ASSIGNMENT = \"=\""));
            }catch(Exception e){throw new RuntimeException(e);}
        });
    }
    @Test void staleOutputAndModelHashAreDetected() {
        project.getModelAccess().runReadAction(()->{
            try{
                Path output=temporary.resolve("stale");DefinitionCompiler.emit(definition,modelFile,output,false);Files.writeString(output.resolve(DefinitionCompiler.JAVA_PATH),"modified");
                assertThrows(java.io.IOException.class,()->DefinitionCompiler.emit(definition,modelFile,output,true));
                DefinitionCompiler.emit(definition,modelFile,output,false);Path changed=temporary.resolve("definition.mps");Files.writeString(changed,Files.readString(modelFile)+"\n");assertThrows(java.io.IOException.class,()->DefinitionCompiler.emit(definition,changed,output,true));
            }catch(Exception e){throw new RuntimeException(e);}
        });
    }
    @Test void changingNativeTokenChangesBothJavaAndSharedMetadata() {
        project.getModelAccess().executeCommand(()->{
            SNode assignment=DefinitionCompiler.children(definition,"tokens").stream().filter(n->DefinitionCompiler.get(n,"name").equals("assignment")).findFirst().orElseThrow();var property=DefinitionConcepts.property("SyntaxToken","value");String original=assignment.getProperty(property);
            try{
                assignment.setProperty(property,":=");var files=DefinitionCompiler.files(definition,modelHash());
                assertTrue(files.get(DefinitionCompiler.JAVA_PATH).contains("TOKEN_ASSIGNMENT = \":=\""));
                assertTrue(files.get(DefinitionCompiler.METADATA_PATH).contains("\"assignment\": \":=\""));
            }finally{assignment.setProperty(property,original);((org.jetbrains.mps.openapi.model.EditableSModel)definition.getModel()).setChanged(false);}
        });
    }
    @Test void invalidNativeTokensAndDelimiterConflictsStopGeneration() {
        project.getModelAccess().executeCommand(()->{
            SNode assignment=DefinitionCompiler.children(definition,"tokens").stream().filter(n->DefinitionCompiler.get(n,"name").equals("assignment")).findFirst().orElseThrow();var property=DefinitionConcepts.property("SyntaxToken","value");String original=assignment.getProperty(property);
            try{
                assignment.setProperty(property,"");assertThrows(IllegalArgumentException.class,()->DefinitionCompiler.files(definition,modelHash()));
                assignment.setProperty(property,"[");assertThrows(IllegalArgumentException.class,()->DefinitionCompiler.files(definition,modelHash()));
            }finally{assignment.setProperty(property,original);((org.jetbrains.mps.openapi.model.EditableSModel)definition.getModel()).setChanged(false);}
        });
    }
    @Test void changingNativeFieldRequiredUpdatesSharedProfile() {
        project.getModelAccess().executeCommand(()->{
            SNode profile=DefinitionCompiler.children(definition,"profiles").stream().filter(n->DefinitionCompiler.get(n,"name").equals("strict-example")).findFirst().orElseThrow();SNode category=DefinitionCompiler.children(profile,"categories").getFirst();SNode field=DefinitionCompiler.children(category,"fields").getFirst();var property=DefinitionConcepts.property("Field","required");String original=field.getProperty(property);
            try{
                field.setProperty(property,"false");var profiles=DefinitionCompiler.metadata(definition,modelHash()).getAsJsonArray("profiles");
                var changed=profiles.asList().stream().map(p->p.getAsJsonObject()).filter(p->p.get("id").getAsString().equals("strict-example")).findFirst().orElseThrow();assertFalse(changed.getAsJsonArray("categories").get(0).getAsJsonObject().getAsJsonArray("fields").get(0).getAsJsonObject().get("required").getAsBoolean());
            }finally{field.setProperty(property,original);((org.jetbrains.mps.openapi.model.EditableSModel)definition.getModel()).setChanged(false);}
        });
    }
    @Test void missingOrWronglyTypedNativePoliciesStopGeneration() {
        project.getModelAccess().executeCommand(()->{
            for(String name:List.of("allowBareValues","allowEmptyAssignment","allowTrailingComma","allowNewlineSeparator","caseInsensitiveBooleans","allowDottedCategories","allowColonCategories","allowBareEntries","preserveUnknownEscapes","allowBom")) {
                SNode policy=named(definition,"policies",name);
                rejectProperty(policy,"name",name+"Missing");
                rejectProperty(policy,"value","1","trueish","");
            }
            for(String name:List.of("maxDepth","indentWidth")) {
                SNode policy=named(definition,"policies",name);
                rejectProperty(policy,"name",name+"Missing");
                rejectProperty(policy,"value","true","0","-1","1.5","2147483648");
            }
        });
    }
    @Test void nativeLiteralsCannotConflictWithLexicalBoundaries() {
        project.getModelAccess().executeCommand(()->{
            for(String name:List.of("true","false","null")) {
                SNode literal=named(definition,"literals",name);
                rejectProperty(literal,"value","#yes","yes//comment","[yes]","true,other","true=other","\"yes\"","yes'no"," true","true\nfalse","1");
            }
            rejectProperty(named(definition,"literals","false"),"value","TRUE");
            rejectProperty(named(definition,"literals","null"),"value","TrUe");
        });
    }
    @Test void nativeConstraintsMustHaveSupportedNamesAndTypedValues() {
        project.getModelAccess().executeCommand(()->{
            SNode field=exampleField();SNode constraint=DefinitionCompiler.children(field,"constraints").getFirst();
            rejectProperty(constraint,"name","minItems","unknown");
            rejectProperty(constraint,"value","true","null","[1]","\"1\"","-1","1.5","2147483648");
            rejectProperty(field,"type","boolean","array");
            var nameProperty=DefinitionConcepts.NAME;var valueProperty=DefinitionConcepts.property("Constraint","value");String name=constraint.getProperty(nameProperty),value=constraint.getProperty(valueProperty);
            try {
                constraint.setProperty(nameProperty,"pattern");constraint.setProperty(valueProperty,"[");
                assertThrows(IllegalArgumentException.class,()->DefinitionCompiler.files(definition,modelHash()));
                constraint.setProperty(valueProperty,"[A-Z]+");assertTrue(DefinitionCompiler.validate(definition).isEmpty());
            } finally {
                constraint.setProperty(nameProperty,name);constraint.setProperty(valueProperty,value);clearChanged();
            }
        });
    }
    @Test void nativeConstraintRangesMustBeOrdered() {
        project.getModelAccess().executeCommand(()->{
            SNode field=exampleField();SNode minimum=DefinitionCompiler.children(field,"constraints").getFirst();
            var maximum=new jetbrains.mps.smodel.SNode(DefinitionConcepts.concept("Constraint"));maximum.setProperty(DefinitionConcepts.NAME,"maxLength");maximum.setProperty(DefinitionConcepts.property("Constraint","value"),"0");
            field.addChild(DefinitionConcepts.child("Field","constraints"),maximum);
            var fieldType=DefinitionConcepts.property("Field","type");String type=field.getProperty(fieldType),minName=minimum.getProperty(DefinitionConcepts.NAME),minValue=minimum.getProperty(DefinitionConcepts.property("Constraint","value"));
            try {
                assertThrows(IllegalArgumentException.class,()->DefinitionCompiler.files(definition,modelHash()));
                field.setProperty(fieldType,"number");minimum.setProperty(DefinitionConcepts.NAME,"min");minimum.setProperty(DefinitionConcepts.property("Constraint","value"),"2");maximum.setProperty(DefinitionConcepts.NAME,"max");maximum.setProperty(DefinitionConcepts.property("Constraint","value"),"1");
                assertThrows(IllegalArgumentException.class,()->DefinitionCompiler.files(definition,modelHash()));
                rejectProperty(minimum,"value","false","null","[]","NaN","Infinity");
                maximum.setProperty(DefinitionConcepts.property("Constraint","value"),"3");assertTrue(DefinitionCompiler.validate(definition).isEmpty());
            } finally {
                field.removeChild(maximum);field.setProperty(fieldType,type);minimum.setProperty(DefinitionConcepts.NAME,minName);minimum.setProperty(DefinitionConcepts.property("Constraint","value"),minValue);clearChanged();
            }
        });
    }
    private SNode exampleField() {
        SNode profile=named(definition,"profiles","strict-example");return DefinitionCompiler.children(DefinitionCompiler.children(profile,"categories").getFirst(),"fields").getFirst();
    }
    private static SNode named(SNode parent,String role,String name) {
        return DefinitionCompiler.children(parent,role).stream().filter(node->DefinitionCompiler.get(node,"name").equals(name)).findFirst().orElseThrow();
    }
    private void rejectProperty(SNode node,String property,String... values) {
        var key=DefinitionConcepts.property(node.getConcept().getName(),property);String original=node.getProperty(key);
        try {
            for(String value:values) {node.setProperty(key,value);assertThrows(IllegalArgumentException.class,()->DefinitionCompiler.files(definition,modelHash()),node.getConcept().getName()+"."+property+"="+value);}
        } finally {node.setProperty(key,original);clearChanged();}
    }
    private void clearChanged(){((org.jetbrains.mps.openapi.model.EditableSModel)definition.getModel()).setChanged(false);}
    @Test void registeredMpsTextGenProducesEveryToolchainArtifact() {
        project.getModelAccess().runReadAction(()->{
            var runtime=environment.getPlatform().findComponent(LanguageRegistry.class).getLanguage(DefinitionConcepts.LANGUAGE);var textgen=runtime.getAspect(TextGenAspectDescriptor.class);assertNotNull(textgen.getDescriptor(definition.getConcept()));
            List<TextUnit> units=new ArrayList<>();textgen.breakdownToUnits(new TextGenModelOutline(){
                @Override public org.jetbrains.mps.openapi.model.SModel getModel(){return definition.getModel();}
                @Override public jetbrains.mps.components.ComponentHost getPlatform(){return environment.getPlatform();}
                @Override public void registerTextUnit(TextUnit unit){units.add(unit);}
                @Override public void registerTextUnit(String name,String path,java.nio.charset.Charset charset,SNode... nodes){throw new UnsupportedOperationException();}
                @Override public void registerBinaryUnit(String name,String path,SNode... nodes){throw new UnsupportedOperationException();}
                @Override public UnitBuilder unitBuilder(String name,SNode node){throw new UnsupportedOperationException();}
            });
            assertEquals(4,units.size());var expected=DefinitionCompiler.files(definition,modelHash());
            for(TextUnit unit:units){unit.generate();assertEquals(TextUnit.Status.Generated,unit.getState());assertEquals(expected.get(unit.getFilePath()+"/"+unit.getFileName()),new String(unit.getBytes(),StandardCharsets.UTF_8));}
        });
    }
}

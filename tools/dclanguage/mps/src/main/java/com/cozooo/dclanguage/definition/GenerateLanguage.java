package com.cozooo.dclanguage.definition;

import jetbrains.mps.tool.environment.*;
import java.nio.file.*;
import java.io.IOException;

public final class GenerateLanguage {
    private GenerateLanguage() { }
    public static void main(String[] args)throws Exception {
        if(args.length<2)throw new IllegalArgumentException("Usage: GenerateLanguage <MPS project directory> <generated directory> [--verify]");
        Path projectDir=Path.of(args[0]).toAbsolutePath().normalize();Path output=Path.of(args[1]).toAbsolutePath().normalize();boolean verify=args.length>2&&args[2].equals("--verify");
        var environment=new MpsEnvironment(EnvironmentConfig.emptyConfig().withCorePlugin().withRepositoryPlugin().withBootstrapLibraries().withWorkbenchPath());
        jetbrains.mps.project.Project project=null;
        try{
            environment.init();project=environment.openProject(projectDir.resolve("definition-project").toFile());var current=project;
            current.getModelAccess().runReadAction(()->{
                for(var module:current.getModules())for(var model:module.getModels())for(var root:model.getRootNodes())if(root.getConcept().equals(DefinitionConcepts.concept("Definition"))){
                    try{DefinitionCompiler.emit(root,projectDir.resolve("solutions/definition/models/dc.mps"),output,verify);return;}
                    catch(IOException exception){throw new java.io.UncheckedIOException(exception);}
                }
                throw new IllegalStateException("No DC Definition model found");
            });
            System.out.println(verify?"DC generated outputs are current.":"Generated DC syntax, runtime facades, schema metadata, and manifest.");
        }finally{if(project!=null)environment.closeProject(project);environment.dispose();}
    }
}

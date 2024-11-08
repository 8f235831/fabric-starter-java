package pers.u8f23.fabric.plugin.api;

import org.gradle.api.*;
import org.gradle.api.file.Directory;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.jetbrains.annotations.NotNull;

/**
 * This Gradle Plugin is used to generate chaincode interface and pojo classes.
 * Example configuration for {@code build.gradle} scripts: <pre>{@code
 * plugins {
 *     // other plugins...
 *     id 'pers.u8f23.fabric.plugin.api' version 'version-codes'
 * }
 *
 * // some scripts here...
 *
 * generateApi {
 *     classes {
 *         // define pojo classes.
 *         registerPojo("pojo_class_name", [
 *             "field_name_1" : "String",
 *             "field_name_2" : "int",
 *         ])
 *
 *         // define api classes.
 *         registerApi("api_class_name", ApiDefinition.API_TYPE_SUBMIT) {
 *             registerMethod("method_name_1", "String", [
 *                 "parameter_name_1" : "String"
 *             ])
 *             registerMethod("method_name_2", "int", [
 *                 "parameter_name_1" : "String",
 *                 "parameter_name_2" : "int"
 *             ])
 *         }
 *     }
 *     sourcePackageName = "${project.ext.cons.group}.chaincode.api"
 *     generateType = ChaincodeApiExtension.API_TYPE_CHAINCODE
 * }
 * </pre>
 */
public class ChaincodeApiPlugin implements Plugin<Project> {
    public static final String PLUGIN_ALIAS = "generateApi";

    @Override
    public void apply(@NotNull Project project) {
        ChaincodeApiExtension extension = project.getExtensions().create(PLUGIN_ALIAS, ChaincodeApiExtension.class);

        extension.getSourceOutputPath().convention(project.getLayout().getBuildDirectory().dir("generated/sources/chaincode-api/main"));
        extension.getSourcePackageName().convention(project.getGroup().toString());

        Iterable<JavaCompile> compileTasks = project.getTasks()
                .withType(JavaCompile.class);

        Directory sourceOutputPath = extension.getSourceOutputPath().get();
        @SuppressWarnings("all")
        boolean __ignored = sourceOutputPath.getAsFile().mkdirs();

        project.getTasks().register(PLUGIN_ALIAS, GenerateApiTask.class, new Action<GenerateApiTask>() {
            @Override
            public void execute(@NotNull GenerateApiTask generateTask) {

                // make sure directory exists.
                generateTask.getSourceOutputPath().set(sourceOutputPath);
                generateTask.getClasses().set(extension.getClasses());
                generateTask.getSourcePackageName().set(extension.getSourcePackageName().get());
                generateTask.getGenerateType().set(extension.getGenerateType().get());

                // add source to compiler.
                compileTasks.forEach(javaCompile -> {
                    javaCompile.source(sourceOutputPath.getAsFile());
                });
            }
        });

        // register source set so IDEA can scan and index generated codes.
        project.getExtensions()
                .findByType(SourceSetContainer.class)
                .findByName("main")
                .java(new Action<SourceDirectorySet>() {
                    @Override
                    public void execute(SourceDirectorySet files) {
                        files.srcDirs(sourceOutputPath.getAsFile());
                    }
                });

        // generate codes before compile.
        compileTasks.forEach(javaCompile -> javaCompile.dependsOn(PLUGIN_ALIAS));
    }
}

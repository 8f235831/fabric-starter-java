package pers.u8f23.fabric.plugin.api;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import pers.u8f23.fabric.plugin.api.config.ClassesDefinition;
import pers.u8f23.fabric.plugin.api.generators.AbstractApiGenerator;
import pers.u8f23.fabric.plugin.api.generators.ApplicationApiGenerator;
import pers.u8f23.fabric.plugin.api.generators.ChaincodeApiGenerator;

import java.io.File;
import java.util.Objects;

/**
 * Task to generate api codes.
 */
public abstract class GenerateApiTask extends DefaultTask {
    @OutputDirectory
    public abstract DirectoryProperty getSourceOutputPath();

    @Input
    public abstract Property<ClassesDefinition> getClasses();

    @Input
    public abstract Property<String> getSourcePackageName();

    @Input
    public abstract Property<String> getGenerateType();

    @TaskAction
    public void runTask() throws Exception {
        String generateType = getGenerateType().get();
        String packageName = getSourcePackageName().get();
        File outputPath = getSourceOutputPath().getAsFile().get();
        ClassesDefinition classes = getClasses().get();

        // TODO refactor plugin to make it incremental so that the plugin will not need to clean before each build.
        deleteRecursive(outputPath);

        AbstractApiGenerator generator;
        switch (generateType) {
            case ChaincodeApiExtension.API_TYPE_CHAINCODE:
                generator = new ChaincodeApiGenerator();
                break;
            case ChaincodeApiExtension.API_TYPE_APPLICATION:
                generator = new ApplicationApiGenerator();
                break;
            default:
                throw new RuntimeException(String.format("unexpected generate type in {%s}, val: %s", getGenerateType(), generateType));
        }
        generator.generate(classes, outputPath, packageName);
    }

//    private void printInput(){
//        System.out.println("getSourceOutputPath: " + getSourceOutputPath().get());
//        System.out.println("getSourcePackageName: " + getSourcePackageName().get());
//        System.out.println("getGenerateType: " + getGenerateType().get());
//        NamedDomainObjectContainer<PojoDefinition> pojos = getClasses().get().getPojos();
//        pojos.forEach(pojo -> {
//            System.out.println("pojo Name: " + pojo.getName());
//            MapProperty<String, String> fields = pojo.getFields();
//            fields.get().forEach((name, type) -> {
//                System.out.println("pojo field: " + name + "=>" + type);
//            });
//        });
//
//        // ----
//
//        getClasses().get().getApis().forEach(api -> {
//            System.out.println("api name:" + api.getName() + ", type:" + api.getType().get());
//            api.getMethods().forEach(m -> {
//                System.out.println("api method:" + m.getName() + ", return:" + m.getReturnType().get() + ", param:" + m.getParameters().get());
//            });
//        });
//    }


    private void deleteRecursive(File root) {
        if (root.isDirectory()) {
            for (File child : Objects.requireNonNullElse(root.listFiles(), new File[]{})) {
                deleteRecursive(child);
            }
        }
        if (root.exists()) {
            root.delete();
        }
    }
}

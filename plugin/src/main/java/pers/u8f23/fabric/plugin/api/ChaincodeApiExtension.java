package pers.u8f23.fabric.plugin.api;

import org.gradle.api.Action;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import pers.u8f23.fabric.plugin.api.config.ClassesDefinition;

/**
 * Config extension for this plugin.
 */
public interface ChaincodeApiExtension {
    String API_TYPE_CHAINCODE = "chaincode";
    String API_TYPE_APPLICATION = "app";

    /**
     * path where to generate API Java codes.
     * Default in {@code ./build } directory.
     */
    @OutputDirectory
    DirectoryProperty getSourceOutputPath();

    /**
     * Config how to generate api codes.
     * Should manually assign this field.
     */
    @Nested
    ClassesDefinition getClasses();

    /**
     * Config how to generate api codes.
     */
    default void classes(Action<? super ClassesDefinition> action){
        action.execute(getClasses());
    }

    /**
     * Java package name of generated classes.
     */
    @Input
    Property<String> getSourcePackageName();

    /**
     * Which type to generate codes.
     * Valued at {@link #API_TYPE_CHAINCODE} or {@link #API_TYPE_APPLICATION} .
     */
    @Input
    Property<String> getGenerateType();
}

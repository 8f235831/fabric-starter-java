package pers.u8f23.fabric.plugin.api.generators;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import pers.u8f23.fabric.plugin.api.config.ApiDefinition;
import pers.u8f23.fabric.plugin.api.config.ClassesDefinition;
import pers.u8f23.fabric.plugin.api.config.PojoDefinition;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class AbstractApiGenerator {
    private final Map<String, ClassName> classNames = new HashMap<>();

    public final void generate(ClassesDefinition classes, File outputDirectory, String packageName) {
        classes.getPojos()
                .stream()
                .map(this::buildPojoClass)
                .filter(Objects::nonNull)
                .forEach(typeSpec -> this.writeSpec(typeSpec, outputDirectory, packageName));
        classes.getApis()
                .stream()
                .map(this::buildApiClass)
                .filter(Objects::nonNull)
                .forEach(typeSpec -> this.writeSpec(typeSpec, outputDirectory, packageName));
        buildOtherClasses(classes, packageName)
                .stream()
                .filter(Objects::nonNull)
                .forEach(typeSpec -> this.writeSpec(typeSpec, outputDirectory, packageName));
    }

    public abstract TypeSpec buildPojoClass(PojoDefinition def);

    public abstract TypeSpec buildApiClass(ApiDefinition def);

    /**
     * Run after {@link #buildPojoClass(PojoDefinition)} and {@link  #buildApiClass(ApiDefinition)}.
     */
    public abstract List<TypeSpec> buildOtherClasses(ClassesDefinition classes, String packageName);

    private void writeSpec(TypeSpec typeSpec, File outputDirectory, String packageName) {
        try {
            JavaFile.builder(packageName, typeSpec).build().writeTo(outputDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write generated code.", e);
        }
    }

    protected final ClassName declareCustomClass(String classNameStr) {
        return classNames.computeIfAbsent(classNameStr, s -> ClassName.get("", s));
    }

    protected final ClassName declareCustomClass(String packageName, String classNameStr) {
        return classNames.computeIfAbsent(classNameStr, s -> ClassName.get(packageName, s));
    }

    protected final String castFieldToGetter(String fieldName) {
        if (fieldName.isEmpty()) {
            return fieldName;
        }
        char first = fieldName.charAt(0);
        if (!Character.isLowerCase(first)) {
            return fieldName;
        }
        return String.format("get%s%s", Character.toUpperCase(first), fieldName.substring(1));
    }

    protected final String castFieldToSetter(String fieldName) {
        if (fieldName.isEmpty()) {
            return fieldName;
        }
        char first = fieldName.charAt(0);
        if (!Character.isLowerCase(first)) {
            return fieldName;
        }
        return String.format("set%s%s", Character.toUpperCase(first), fieldName.substring(1));
    }

}

package pers.u8f23.fabric.plugin.api.config;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import java.util.Map;

public interface ApiDefinition {
    String API_TYPE_SUBMIT = "submit";
    String API_TYPE_EVALUATE = "evaluate";

    @Input
    String getName();

    @Input
    Property<String> getType();

    @Input
    NamedDomainObjectContainer<ApiMethodDefinition> getMethods();

    default void registerMethod(String name, Map<String, String> parameters) {
        ApiMethodDefinition def = getMethods().create(name);
        def.getParameters().putAll(parameters);
    }
}

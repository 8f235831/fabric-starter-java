package pers.u8f23.fabric.plugin.api.config;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import java.util.Map;

public interface ClassesDefinition {

    @Input
    NamedDomainObjectContainer<PojoDefinition> getPojos();

    default void pojos(Action<? super NamedDomainObjectContainer<PojoDefinition>> action) {
        action.execute(getPojos());
    }

    default void registerPojo(String pojoName, Map<String, String> pojoFields) {
        PojoDefinition def = getPojos().create(pojoName);
        def.getFields().putAll(pojoFields);
    }

    @Input
    Property<String> getComplexApiClassName();

    @Input
    NamedDomainObjectContainer<ApiDefinition> getApis();

    default void apis(Action<? super NamedDomainObjectContainer<ApiDefinition>> action) {
        action.execute(getApis());
    }

    default void registerApi(String name, String type, Action<ApiDefinition> action) {
        ApiDefinition def = getApis().create(name);
        def.getType().set(type);
        action.execute(def);
    }
}

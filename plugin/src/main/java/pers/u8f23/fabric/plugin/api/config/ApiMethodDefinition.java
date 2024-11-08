package pers.u8f23.fabric.plugin.api.config;

import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;

public interface ApiMethodDefinition {
    @Input
    String getName();

    @Input
    MapProperty<String, String> getParameters();
}

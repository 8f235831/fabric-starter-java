package pers.u8f23.fabric.plugin.api.generators;

import com.squareup.javapoet.*;
import pers.u8f23.fabric.plugin.api.config.ApiDefinition;
import pers.u8f23.fabric.plugin.api.config.ClassesDefinition;
import pers.u8f23.fabric.plugin.api.config.PojoDefinition;

import javax.lang.model.element.Modifier;
import java.util.List;

public final class ChaincodeApiGenerator extends AbstractApiGenerator {
    private final static ClassName POJO_CLASS_ANNOTATION
            = ClassName.get("org.hyperledger.fabric.contract.annotation", "DataType");
    private final static ClassName POJO_FIELD_ANNOTATION
            = ClassName.get("org.hyperledger.fabric.contract.annotation", "Property");
    private final static ClassName POJO_CONSTRUCTOR_PARAM_ANNOTATION
            = ClassName.get("com.owlike.genson.annotation", "JsonProperty");
    private final static ClassName API_METHOD_PARAM_CONTEXT
            = ClassName.get("org.hyperledger.fabric.contract", "Context");

    @Override
    public TypeSpec buildPojoClass(PojoDefinition def) {
        TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(def.getName())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(POJO_CLASS_ANNOTATION);
        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
        CodeBlock.Builder constructorCodeBuilder = CodeBlock.builder();
        def.getFields().get().forEach((fieldName, typeStr) -> {
            ClassName type = declareCustomClass(typeStr);

            // add field.
            typeBuilder.addField(FieldSpec
                    .builder(type, fieldName, Modifier.PRIVATE, Modifier.FINAL)
                    .addAnnotation(POJO_FIELD_ANNOTATION)
                    .build()
            );
            // add getter.
            typeBuilder.addMethod(MethodSpec
                    .methodBuilder(castFieldToGetter(fieldName))
                    .addModifiers(Modifier.PUBLIC)
                    .returns(type)
                    .addCode("return this.$L;", fieldName)
                    .build()
            );
            // add field to constructor.
            constructorBuilder.addParameter(ParameterSpec
                    .builder(type, fieldName)
                    .addAnnotation(AnnotationSpec.builder(POJO_CONSTRUCTOR_PARAM_ANNOTATION).addMember("value", "$S", fieldName).build())
                    .build()
            );
            constructorCodeBuilder.add("this.$L = $L;\n", fieldName, fieldName);
        });
        typeBuilder.addMethod(constructorBuilder.addCode(constructorCodeBuilder.build()).build());
        return typeBuilder.build();
    }

    @Override
    public TypeSpec buildApiClass(ApiDefinition def) {
        TypeSpec.Builder typeBuilder = TypeSpec.interfaceBuilder(def.getName())
                .addModifiers(Modifier.PUBLIC);
        def.getMethods().forEach(methodDef -> {
            MethodSpec.Builder methodBuilder = MethodSpec
                    .methodBuilder(methodDef.getName())
                    .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                    .returns(String.class)
                    .addParameter(API_METHOD_PARAM_CONTEXT, "context");
            methodDef.getParameters().get().forEach((paramName, paramType) -> {
                methodBuilder.addParameter(declareCustomClass(paramType), paramName);
            });
            typeBuilder.addMethod(methodBuilder.build());
        });
        return typeBuilder.build();
    }

    @Override
    public List<TypeSpec> buildOtherClasses(ClassesDefinition classes, String packageName) {
        return List.of(
                generateComplexInterface(classes)
        );
    }

    private TypeSpec generateComplexInterface(ClassesDefinition classes) {
        TypeSpec.Builder typeBuilder = TypeSpec.interfaceBuilder(classes.getComplexApiClassName().get())
                .addModifiers(Modifier.PUBLIC);
        classes.getApis().forEach(apiClass -> typeBuilder.addSuperinterface(declareCustomClass(apiClass.getName())));
        return typeBuilder.build();
    }
}

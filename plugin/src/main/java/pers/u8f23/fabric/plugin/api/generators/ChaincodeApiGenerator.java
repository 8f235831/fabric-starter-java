package pers.u8f23.fabric.plugin.api.generators;

import com.squareup.javapoet.*;
import pers.u8f23.fabric.plugin.api.config.ApiDefinition;
import pers.u8f23.fabric.plugin.api.config.ClassesDefinition;
import pers.u8f23.fabric.plugin.api.config.PojoDefinition;

import javax.lang.model.element.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static pers.u8f23.fabric.plugin.api.Constants.RESPONSE_BODY_CLASS_NAME;

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
        def.getFields().get().forEach((fieldName, fieldType) -> decoratePojoWithField(fieldName, declareCustomClass(fieldType), typeBuilder, constructorBuilder, constructorCodeBuilder));
        typeBuilder.addMethod(constructorBuilder.addCode(constructorCodeBuilder.build()).build());
        return typeBuilder.build();
    }

    @Override
    public TypeSpec buildApiClass(ApiDefinition def) {
        TypeSpec.Builder typeBuilder = TypeSpec.interfaceBuilder(def.getName())
                .addModifiers(Modifier.PUBLIC);
        def.getMethods().forEach(methodDef -> {
            TypeName returnType = ParameterizedTypeName.get(declareCustomClass(RESPONSE_BODY_CLASS_NAME), declareCustomClass(methodDef.getReturnType().get()));
            MethodSpec.Builder methodBuilder = MethodSpec
                    .methodBuilder(methodDef.getName())
                    .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                    .returns(returnType)
                    .addParameter(API_METHOD_PARAM_CONTEXT, "context");
            methodDef.getParameters().get().forEach((paramName, paramType) ->
                    methodBuilder.addParameter(declareCustomClass(paramType), paramName)
            );
            typeBuilder.addMethod(methodBuilder.build());
        });
        return typeBuilder.build();
    }

    @Override
    public List<TypeSpec> buildOtherClasses(ClassesDefinition classes, String packageName) {
        return List.of(
                generateComplexInterface(classes),
                generateResponseBodyInterface()
        );
    }

    private TypeSpec generateComplexInterface(ClassesDefinition classes) {
        TypeSpec.Builder typeBuilder = TypeSpec.interfaceBuilder(classes.getComplexApiClassName().get())
                .addModifiers(Modifier.PUBLIC);
        classes.getApis().forEach(apiClass -> typeBuilder.addSuperinterface(declareCustomClass(apiClass.getName())));
        return typeBuilder.build();
    }

    private TypeSpec generateResponseBodyInterface() {
        TypeVariableName genericClass = TypeVariableName.get("T");
        TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(RESPONSE_BODY_CLASS_NAME)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(POJO_CLASS_ANNOTATION)
                .addTypeVariable(genericClass);
        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
        CodeBlock.Builder constructorCodeBuilder = CodeBlock.builder();
        Map<String, TypeName> map = new LinkedHashMap<>();
        map.put("body", genericClass);
        map.put("code", TypeName.INT);
        map.put("msg", ClassName.get(String.class));
        map.forEach((fieldName, fieldType) -> decoratePojoWithField(fieldName, fieldType, typeBuilder, constructorBuilder, constructorCodeBuilder));
        typeBuilder.addMethod(constructorBuilder.addCode(constructorCodeBuilder.build()).build());
        typeBuilder.addMethod(MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ParameterSpec
                        .builder(genericClass, "body")
                        .addAnnotation(AnnotationSpec.builder(POJO_CONSTRUCTOR_PARAM_ANNOTATION).addMember("value", "$S", "body").build())
                        .build()
                )
                .addStatement("this(body, 0, $S)", "Success")
                .build()
        );
        typeBuilder.addMethod(MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(TypeName.INT, "code")
                .addParameter(String.class, "msg")
                .addStatement("this(null, code, msg)")
                .build()
        );
        typeBuilder.addMethod(MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement("this(null, 0, $S)", "Success")
                .build()
        );
        return typeBuilder.build();
    }

    private void decoratePojoWithField(String fieldName, TypeName fieldType, TypeSpec.Builder typeBuilder, MethodSpec.Builder constructorBuilder, CodeBlock.Builder constructorCodeBuilder) {
        // add field.
        typeBuilder.addField(FieldSpec
                .builder(fieldType, fieldName, Modifier.PRIVATE, Modifier.FINAL)
                .addAnnotation(POJO_FIELD_ANNOTATION)
                .build()
        );
        // add getter.
        typeBuilder.addMethod(MethodSpec
                .methodBuilder(castFieldToGetter(fieldName))
                .addModifiers(Modifier.PUBLIC)
                .returns(fieldType)
                .addCode("return this.$L;", fieldName)
                .build()
        );
        // add field to constructor.
        constructorBuilder.addParameter(ParameterSpec
                .builder(fieldType, fieldName)
                .addAnnotation(AnnotationSpec.builder(POJO_CONSTRUCTOR_PARAM_ANNOTATION).addMember("value", "$S", fieldName).build())
                .build()
        );
        constructorCodeBuilder.add("this.$L = $L;\n", fieldName, fieldName);
    }
}

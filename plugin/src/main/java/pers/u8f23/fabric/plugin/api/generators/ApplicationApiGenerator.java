package pers.u8f23.fabric.plugin.api.generators;

import com.squareup.javapoet.*;
import pers.u8f23.fabric.plugin.api.config.ApiDefinition;
import pers.u8f23.fabric.plugin.api.config.ApiMethodDefinition;
import pers.u8f23.fabric.plugin.api.config.ClassesDefinition;
import pers.u8f23.fabric.plugin.api.config.PojoDefinition;

import javax.lang.model.element.Modifier;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;
import java.util.function.Function;

import static pers.u8f23.fabric.plugin.api.Constants.RESPONSE_BODY_CLASS_NAME;

public final class ApplicationApiGenerator extends AbstractApiGenerator {
    private static final String INJECT_CLASS_NAME = "ContractApiInjectable";
    private static final String INJECTED_CONTRACT_METHOD_NAME = "getContract";
    private static final String INJECTED_GSON_METHOD_NAME = "getGson";
    private static final String PROPOSED_SUBMIT_RES_CLASS_NAME = "ProposedSubmit";

    private static final ClassName LOMBOK_GETTER_ANNOTATION = ClassName.get("lombok", "Getter");
    private static final ClassName LOMBOK_SETTER_ANNOTATION = ClassName.get("lombok", "Setter");
    private static final ClassName LOMBOK_BUILDER_ANNOTATION = ClassName.get("lombok", "Builder");
    private static final ClassName LOMBOK_NO_ARGUS_CONS_ANNOTATION = ClassName.get("lombok", "NoArgsConstructor");
    private static final ClassName LOMBOK_ALL_ARGUS_CONS_ANNOTATION = ClassName.get("lombok", "AllArgsConstructor");
    private static final ClassName LOMBOK_REQ_ARGUS_CONS_ANNOTATION = ClassName.get("lombok", "RequiredArgsConstructor");
    private static final ClassName LOMBOK_TO_STRING_ANNOTATION = ClassName.get("lombok", "ToString");
    private static final ClassName GSON_CLASS = ClassName.get("com.google.gson", "Gson");
    private static final ClassName GSON_SERIALIZED_NAME_ANNOTATION = ClassName.get("com.google.gson.annotations", "SerializedName");
    private static final ClassName GSON_TYPE_TOKEN_CLASS = ClassName.get("com.google.gson.reflect", "TypeToken");
    private static final ClassName API_CONTRACT_INJECT_CLASS = ClassName.get("", INJECT_CLASS_NAME);
    private static final ClassName PROPOSED_SUBMIT_RES_CLASS = ClassName.get("", PROPOSED_SUBMIT_RES_CLASS_NAME);

    private static final ClassName API_CONTRACT_CLASS = ClassName.get("org.hyperledger.fabric.client", "Contract");
    private static final ClassName SUBMITTED_TRANSACTION_CLASS = ClassName.get("org.hyperledger.fabric.client", "SubmittedTransaction");
    private static final ClassName SUBMIT_STATUS_CLASS = ClassName.get("org.hyperledger.fabric.client", "Status");
    private static final ClassName SUBMIT_STATUS_EXCEPTION_CLASS = ClassName.get("org.hyperledger.fabric.client", "CommitStatusException");

    @Override
    public TypeSpec buildPojoClass(PojoDefinition def) {
        TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(def.getName())
                .addAnnotation(LOMBOK_GETTER_ANNOTATION)
                .addAnnotation(LOMBOK_SETTER_ANNOTATION)
                .addAnnotation(LOMBOK_BUILDER_ANNOTATION)
                .addAnnotation(LOMBOK_NO_ARGUS_CONS_ANNOTATION)
                .addAnnotation(LOMBOK_ALL_ARGUS_CONS_ANNOTATION)
                .addAnnotation(LOMBOK_TO_STRING_ANNOTATION)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        def.getFields().get().forEach((fieldName, typeStr) -> decoratePojoWithField(fieldName, declareCustomClass(typeStr), typeBuilder));
        return typeBuilder.build();
    }

    @Override
    public TypeSpec buildApiClass(ApiDefinition def) {
        TypeSpec.Builder typeBuilder = TypeSpec.interfaceBuilder(def.getName());
        typeBuilder.addModifiers(Modifier.PUBLIC)
                .addSuperinterface(API_CONTRACT_INJECT_CLASS);
        Function<ApiMethodDefinition, MethodSpec> methodBuildMethod;
        switch (def.getType().get()) {
            case ApiDefinition.API_TYPE_SUBMIT:
                methodBuildMethod = this::buildApiMethodSubmit;
                break;
            case ApiDefinition.API_TYPE_EVALUATE:
                methodBuildMethod = this::buildApiMethodEvaluate;
                break;
            default:
                methodBuildMethod = __any -> null;
        }
        def.getMethods()
                .stream()
                .map(methodBuildMethod)
                .filter(Objects::nonNull)
                .forEach(typeBuilder::addMethod);
        return typeBuilder.build();
    }

    @Override
    public List<TypeSpec> buildOtherClasses(ClassesDefinition classes, String packageName) {
        return List.of(
                generateContextInjectInterface(),
                generateProposedSubmitResClass(),
                generateComplexInterfaceImpl(classes),
                generateResponseBodyClass()
        );
    }

    private MethodSpec buildApiMethodEvaluate(ApiMethodDefinition def) {
        TypeName returnType = ParameterizedTypeName.get(declareCustomClass(RESPONSE_BODY_CLASS_NAME), declareCustomClass(def.getReturnType().get()));
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(def.getName());
        methodBuilder.returns(returnType)
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT);
        def.getParameters().get().forEach((paramName, paramTypeName) -> {
            ClassName paramType = declareCustomClass(paramTypeName);
            methodBuilder.addParameter(paramType, paramName);
        });
        Set<String> actualParams = def.getParameters().get().keySet();
        String actualParamsStr = actualParams.isEmpty() ? "" : ", " + String.join(", ", actualParams);
        CodeBlock.Builder codeBuilder = CodeBlock.builder()
                .addStatement("byte[] evaluatedBytes = this.$L().evaluateTransaction($S$L)", INJECTED_CONTRACT_METHOD_NAME, def.getName(), actualParamsStr)
                .addStatement("$T reader = new $T(new $T(evaluatedBytes))", Reader.class, InputStreamReader.class, ByteArrayInputStream.class)
                .addStatement("$T gson = this.$L()", GSON_CLASS, INJECTED_GSON_METHOD_NAME)
                .addStatement("$T<$T> typeToken = new $T<>(){}", GSON_TYPE_TOKEN_CLASS, returnType, GSON_TYPE_TOKEN_CLASS)
                .addStatement("return gson.fromJson(reader, typeToken.getType())");
        methodBuilder.addCode(codeBuilder.build())
                .addException(Exception.class);
        return methodBuilder.build();
    }

    private MethodSpec buildApiMethodSubmit(ApiMethodDefinition def) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(def.getName());
        TypeName returnType = ParameterizedTypeName.get(PROPOSED_SUBMIT_RES_CLASS, declareCustomClass(def.getReturnType().get()));
        TypeName responseType = ParameterizedTypeName.get(declareCustomClass(RESPONSE_BODY_CLASS_NAME), declareCustomClass(def.getReturnType().get()));
        methodBuilder.returns(returnType)
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT);
        def.getParameters().get().forEach((paramName, paramTypeName) -> {
            ClassName paramType = declareCustomClass(paramTypeName);
            methodBuilder.addParameter(paramType, paramName);
        });
        Set<String> actualParams = def.getParameters().get().keySet();
        String actualParamsStr = actualParams.isEmpty() ? "" : String.join(", ", actualParams);
        CodeBlock.Builder codeBuilder = CodeBlock.builder()
                .add("$T commit = this.$L()\n", SUBMITTED_TRANSACTION_CLASS, INJECTED_CONTRACT_METHOD_NAME)
                .add("\t.newProposal($S)\n", def.getName())
                .add("\t.addArguments($L)\n", actualParamsStr)
                .add("\t.build()\n")
                .add("\t.endorse()\n")
                .addStatement("\t.submitAsync()")
                .addStatement("$T<$T> typeToken = new $T<>(){}", GSON_TYPE_TOKEN_CLASS, responseType, GSON_TYPE_TOKEN_CLASS)
                .addStatement("return new $T<>(commit, this.$L(), typeToken)", PROPOSED_SUBMIT_RES_CLASS, INJECTED_GSON_METHOD_NAME);
        methodBuilder.addCode(codeBuilder.build())
                .addException(Exception.class);
        return methodBuilder.build();
    }

    private TypeSpec generateContextInjectInterface() {
        TypeSpec.Builder typeBuilder = TypeSpec.interfaceBuilder(INJECT_CLASS_NAME);
        typeBuilder.addModifiers(Modifier.PUBLIC);
        typeBuilder.addMethod(MethodSpec
                .methodBuilder(INJECTED_CONTRACT_METHOD_NAME)
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .returns(API_CONTRACT_CLASS)
                .build()
        );
        typeBuilder.addMethod(MethodSpec
                .methodBuilder(INJECTED_GSON_METHOD_NAME)
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .returns(GSON_CLASS)
                .build()
        );
        return typeBuilder.build();
    }

    private TypeSpec generateProposedSubmitResClass() {
        TypeVariableName genericClass = TypeVariableName.get("T");
        TypeName apiResponseType = ParameterizedTypeName.get(declareCustomClass(RESPONSE_BODY_CLASS_NAME), genericClass);
        TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(PROPOSED_SUBMIT_RES_CLASS_NAME);
        typeBuilder.addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addTypeVariable(genericClass)
                .addAnnotation(LOMBOK_REQ_ARGUS_CONS_ANNOTATION);
        typeBuilder.addField(FieldSpec
                .builder(SUBMITTED_TRANSACTION_CLASS, "transaction")
                .addModifiers(Modifier.FINAL, Modifier.PRIVATE)
                .build()
        );
        typeBuilder.addField(FieldSpec
                .builder(SUBMIT_STATUS_CLASS, "status")
                .addModifiers(Modifier.PRIVATE)
                .build()
        );
        typeBuilder.addField(FieldSpec
                .builder(apiResponseType, "response")
                .addModifiers(Modifier.PRIVATE)
                .build()
        );
        typeBuilder.addField(FieldSpec
                .builder(GSON_CLASS, "gson")
                .addModifiers(Modifier.FINAL, Modifier.PRIVATE)
                .build()
        );
        typeBuilder.addField(FieldSpec
                .builder(ParameterizedTypeName.get(GSON_TYPE_TOKEN_CLASS, apiResponseType), "responseTypeToken")
                .addModifiers(Modifier.FINAL, Modifier.PRIVATE)
                .build()
        );
        typeBuilder.addField(FieldSpec
                .builder(Object.class, "lock")
                .addModifiers(Modifier.FINAL, Modifier.PRIVATE)
                .initializer("new $T()", Object.class)
                .build()
        );
        typeBuilder.addMethod(MethodSpec
                .methodBuilder("blockingGetSubmitStatus")
                .addModifiers(Modifier.PUBLIC)
                .returns(SUBMIT_STATUS_CLASS)
                .addException(SUBMIT_STATUS_EXCEPTION_CLASS)
                .beginControlFlow("if (this.status != null)")
                .addStatement("return this.status")
                .endControlFlow()
                .beginControlFlow("synchronized (this.lock)")
                .beginControlFlow("if (this.status != null)")
                .addStatement("return this.status")
                .endControlFlow()
                .addStatement("this.status = this.transaction.getStatus()")
                .addStatement("return this.status")
                .endControlFlow()
                .build()
        );
        typeBuilder.addMethod(MethodSpec
                .methodBuilder("blockingGetEvaluatedRes")
                .addModifiers(Modifier.PUBLIC)
                .returns(apiResponseType)
                .beginControlFlow("if (this.response != null)")
                .addStatement("return this.response")
                .endControlFlow()
                .beginControlFlow("synchronized (this.lock)")
                .beginControlFlow("if (this.response != null)")
                .addStatement("return this.response")
                .endControlFlow()
                .addStatement("byte[] evaluatedBytes = this.transaction.getResult()")
                .addStatement("$T reader = new $T(new $T(evaluatedBytes))", Reader.class, InputStreamReader.class, ByteArrayInputStream.class)
                .addStatement("this.response = this.gson.fromJson(reader, this.responseTypeToken.getType())")
                .addStatement("return this.response")
                .endControlFlow()
                .build()
        );
        return typeBuilder.build();
    }

    private TypeSpec generateComplexInterfaceImpl(ClassesDefinition classes) {
        TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(classes.getComplexApiClassName().get());
        typeBuilder.addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        typeBuilder.addField(API_CONTRACT_CLASS, "contract", Modifier.FINAL, Modifier.PRIVATE);
        typeBuilder.addField(GSON_CLASS, "gson", Modifier.FINAL, Modifier.PRIVATE);
        typeBuilder.addSuperinterface(API_CONTRACT_INJECT_CLASS);

        MethodSpec constructor = MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(API_CONTRACT_CLASS, "contract")
                .addParameter(GSON_CLASS, "gson")
                .addStatement("this.contract = contract")
                .addStatement("this.gson = gson")
                .build();
        typeBuilder.addMethod(constructor);
        MethodSpec contractInjectOverride = MethodSpec
                .methodBuilder(INJECTED_CONTRACT_METHOD_NAME)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(API_CONTRACT_CLASS)
                .addStatement("return this.contract")
                .build();
        typeBuilder.addMethod(contractInjectOverride);
        MethodSpec gsonInjectOverride = MethodSpec
                .methodBuilder(INJECTED_GSON_METHOD_NAME)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(GSON_CLASS)
                .addStatement("return this.gson")
                .build();
        typeBuilder.addMethod(gsonInjectOverride);

        classes.getApis().forEach(def -> {
            ClassName interfaceClass = ClassName.get("", def.getName());
            typeBuilder.addSuperinterface(interfaceClass);
        });

        return typeBuilder.build();
    }

    public TypeSpec generateResponseBodyClass() {
        TypeVariableName genericClass = TypeVariableName.get("T");
        TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(RESPONSE_BODY_CLASS_NAME)
                .addAnnotation(LOMBOK_GETTER_ANNOTATION)
                .addAnnotation(LOMBOK_SETTER_ANNOTATION)
                .addAnnotation(LOMBOK_BUILDER_ANNOTATION)
                .addAnnotation(LOMBOK_NO_ARGUS_CONS_ANNOTATION)
                .addAnnotation(LOMBOK_ALL_ARGUS_CONS_ANNOTATION)
                .addAnnotation(LOMBOK_TO_STRING_ANNOTATION)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addTypeVariable(genericClass);
        Map<String, TypeName> map = new LinkedHashMap<>();
        map.put("body", genericClass);
        map.put("code", TypeName.INT);
        map.put("msg", ClassName.get(String.class));
        map.forEach((fieldName, fieldType) -> decoratePojoWithField(fieldName, fieldType, typeBuilder));
        return typeBuilder.build();
    }

    private void decoratePojoWithField(String fieldName, TypeName fieldType, TypeSpec.Builder typeBuilder) {
        // add field.
        AnnotationSpec gsonFieldAnnotation = AnnotationSpec
                .builder(GSON_SERIALIZED_NAME_ANNOTATION)
                .addMember("value", "$S", fieldName)
                .build();
        // add field.
        typeBuilder.addField(FieldSpec
                .builder(fieldType, fieldName, Modifier.PRIVATE)
                .addAnnotation(gsonFieldAnnotation)
                .build()
        );
    }
}

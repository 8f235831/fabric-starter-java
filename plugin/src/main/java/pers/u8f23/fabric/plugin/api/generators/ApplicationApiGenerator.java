package pers.u8f23.fabric.plugin.api.generators;

import com.squareup.javapoet.*;
import pers.u8f23.fabric.plugin.api.config.ApiDefinition;
import pers.u8f23.fabric.plugin.api.config.ApiMethodDefinition;
import pers.u8f23.fabric.plugin.api.config.ClassesDefinition;
import pers.u8f23.fabric.plugin.api.config.PojoDefinition;

import javax.lang.model.element.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public final class ApplicationApiGenerator extends AbstractApiGenerator {
    private static final String INJECT_CLASS_NAME = "ContractApiInjectable";
    private static final String INJECTED_CONTRACT_METHOD_NAME = "getContract";
    private static final String PROPOSED_SUBMIT_RES_CLASS_NAME = "ProposedSubmit";

    private static final ClassName LOMBOK_GETTER_ANNOTATION = ClassName.get("lombok", "Getter");
    private static final ClassName LOMBOK_SETTER_ANNOTATION = ClassName.get("lombok", "Setter");
    private static final ClassName LOMBOK_BUILDER_ANNOTATION = ClassName.get("lombok", "Builder");
    private static final ClassName LOMBOK_NO_ARGUS_CONS_ANNOTATION = ClassName.get("lombok", "NoArgsConstructor");
    private static final ClassName LOMBOK_ALL_ARGUS_CONS_ANNOTATION = ClassName.get("lombok", "AllArgsConstructor");
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
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        def.getFields().get().forEach((fieldName, typeStr) -> {
            ClassName type = declareCustomClass(typeStr);
            AnnotationSpec gsonFieldAnnotation = AnnotationSpec
                    .builder(declareCustomClass("com.google.gson.annotations", "SerializedName"))
                    .addMember("value", "$S", fieldName)
                    .build();
            // add field.
            typeBuilder.addField(FieldSpec
                    .builder(type, fieldName, Modifier.PRIVATE)
                    .addAnnotation(gsonFieldAnnotation)
                    .build()
            );
        });
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
                generateComplexInterfaceImpl(classes)
        );
    }

    private MethodSpec buildApiMethodEvaluate(ApiMethodDefinition def) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(def.getName());
        methodBuilder.returns(String.class)
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT);
        def.getParameters().get().forEach((paramName, paramTypeName) -> {
            ClassName paramType = declareCustomClass(paramTypeName);
            methodBuilder.addParameter(paramType, paramName);
        });
        Set<String> actualParams = def.getParameters().get().keySet();
        String actualParamsStr = actualParams.isEmpty() ? "" : ", " + String.join(", ", actualParams);
        CodeBlock.Builder codeBuilder = CodeBlock.builder()
                .addStatement("byte[] evaluatedBytes = $L().evaluateTransaction($S$L)", INJECTED_CONTRACT_METHOD_NAME, def.getName(), actualParamsStr)
                .addStatement("return new String(evaluatedBytes, $T.UTF_8)", StandardCharsets.class);
        methodBuilder.addCode(codeBuilder.build())
                .addException(Exception.class);
        return methodBuilder.build();
    }

    private MethodSpec buildApiMethodSubmit(ApiMethodDefinition def) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(def.getName());
        methodBuilder.returns(PROPOSED_SUBMIT_RES_CLASS)
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT);
        def.getParameters().get().forEach((paramName, paramTypeName) -> {
            ClassName paramType = declareCustomClass(paramTypeName);
            methodBuilder.addParameter(paramType, paramName);
        });
        Set<String> actualParams = def.getParameters().get().keySet();
        String actualParamsStr = actualParams.isEmpty() ? "" : String.join(", ", actualParams);


        CodeBlock.Builder codeBuilder = CodeBlock.builder()
                .add("$T commit = $L()\n", SUBMITTED_TRANSACTION_CLASS, INJECTED_CONTRACT_METHOD_NAME)
                .add("\t.newProposal($S)\n", def.getName())
                .add("\t.addArguments($L)\n", actualParamsStr)
                .add("\t.build()\n")
                .add("\t.endorse()\n")
                .addStatement("\t.submitAsync()")
                .addStatement("return new $T(commit)", PROPOSED_SUBMIT_RES_CLASS);
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
        return typeBuilder.build();
    }

    private TypeSpec generateProposedSubmitResClass() {
        TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(PROPOSED_SUBMIT_RES_CLASS_NAME);
        typeBuilder.addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(LOMBOK_ALL_ARGUS_CONS_ANNOTATION);
        typeBuilder.addField(FieldSpec
                .builder(SUBMITTED_TRANSACTION_CLASS, "transaction")
                .addModifiers(Modifier.FINAL, Modifier.PRIVATE)
                .build()
        );
        typeBuilder.addMethod(MethodSpec
                .methodBuilder("blockingGetEvaluatedRes")
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return new String(transaction.getResult(), $T.UTF_8)", StandardCharsets.class)
                .build()
        );
        typeBuilder.addMethod(MethodSpec
                .methodBuilder("blockingSummit")
                .addModifiers(Modifier.PUBLIC)
                .returns(SUBMIT_STATUS_CLASS)
                .addException(SUBMIT_STATUS_EXCEPTION_CLASS)
                .addStatement("return transaction.getStatus()")
                .build()
        );
        return typeBuilder.build();
    }

    private TypeSpec generateComplexInterfaceImpl(ClassesDefinition classes) {
        TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(classes.getComplexApiClassName().get());
        typeBuilder.addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        typeBuilder.addField(API_CONTRACT_CLASS, "contract", Modifier.FINAL, Modifier.PRIVATE);
        typeBuilder.addSuperinterface(API_CONTRACT_INJECT_CLASS);

        MethodSpec constructor = MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(API_CONTRACT_CLASS, "contract")
                .addStatement("this.contract = contract")
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

        classes.getApis().forEach(def -> {
            ClassName interfaceClass = ClassName.get("", def.getName());
            typeBuilder.addSuperinterface(interfaceClass);
        });

        return typeBuilder.build();
    }
}

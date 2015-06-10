package me.kenzierocks.tuplocity;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.lang.model.element.Modifier;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.*;

public class TupleGenerator {
    private static final List<String> KEYWORDS;
    static {
        // @formatter:off
        // underscore + false/true/null are special
        KEYWORDS =
                ImmutableList.of("_", "abstract", "assert",
                        "boolean", "break", "byte", "case", "catch", "char",
                        "class", "const", "continue", "default", "do",
                        "double", "else", "extends", "false", "final",
                        "finally", "float", "for", "goto", "if", "implements",
                        "import", "instanceof", "int", "interface", "long",
                        "native", "new", "null", "package", "private",
                        "protected", "public", "return", "short", "static",
                        "strictfp", "super", "switch", "synchronized", "this",
                        "throw", "throws", "transient", "true", "try", "void",
                        "volatile", "while");
        // @formatter:on
    }
    private static final TypeName[] EMPTY_TYPENAME_ARRAY = new TypeName[0];
    private static final String PACKAGE = "me.kenzierocks.tuplocity.tuples";
    private static final String TUPLE_ITEMS_FIELD_NAME = "i";

    private static String getPackage(int max, int tuple) {
        String pkgbase =
                String.format(String.format("%%0%dd", String.valueOf(max)
                        .length()), tuple);
        String pkg = "";
        for (int i = 0; i < pkgbase.length() - 1; i++) {
            pkg += ".$" + pkgbase.charAt(i);
        }
        return PACKAGE + pkg;
    }

    private static final char[] FIRST_CHARACTERS;
    private static final char[] REST_CHARACTERS;
    static {
        List<Character> listA = new ArrayList<>();
        List<Character> listB = new ArrayList<>();
        for (char i = 0; i <= 127; i++) {
            if (Character.isJavaIdentifierStart(i)) {
                listA.add(i);
            }
            if (Character.isJavaIdentifierPart(i)) {
                listB.add(i);
            }
        }
        FIRST_CHARACTERS = new char[listA.size()];
        REST_CHARACTERS = new char[listB.size()];
        for (int i = 0; i < FIRST_CHARACTERS.length; i++) {
            FIRST_CHARACTERS[i] = listA.get(i);
        }
        for (int i = 0; i < REST_CHARACTERS.length; i++) {
            REST_CHARACTERS[i] = listB.get(i);
        }
    }

    private static String convertToTypeVariable(int i) {
        String s = "";
        while (i >= FIRST_CHARACTERS.length) {
            s += REST_CHARACTERS[i % REST_CHARACTERS.length];
            i /= REST_CHARACTERS.length;
        }
        if (i >= 0) {
            s = FIRST_CHARACTERS[i] + s;
        }
        return s;
    }

    private final int count;

    public TupleGenerator(int count) {
        this.count = count;
    }

    public int getCount() {
        return this.count;
    }

    public void generateTupleFiles() {
        ClassName tupleInterface = ClassName.get(PACKAGE, "Tuple");
        generateTupleInterface(tupleInterface);
        for (int i = 1; i <= this.count; i++) {
            System.err.println("Generating " + getPackage(this.count, i)
                    + ".Tuple" + i);
            TypeSpec.Builder spec = TypeSpec.classBuilder("Tuple" + i);
            spec.addModifiers(Modifier.PUBLIC, Modifier.FINAL);
            MethodSpec.Builder cons = MethodSpec.constructorBuilder();
            cons.addModifiers(Modifier.PUBLIC);
            List<TypeVariableName> ptVars = new ArrayList<>(i);
            for (int j = 0, typeVarCounter = 0; j < i; j++, typeVarCounter++) {
                String name = convertToTypeVariable(typeVarCounter);
                if (KEYWORDS.contains(name)) {
                    // skip keywords
                    j--;
                    continue;
                }
                ptVars.add(TypeVariableName.get(name));
            }
            ClassName className =
                    ClassName.get(getPackage(this.count, i), "Tuple" + i);
            if (i != 1) {
                int halfway = i / 2;
                int rest = i - halfway;
                setupHalf(cons, ptVars, halfway, 0, halfway, "firstHalf");
                setupHalf(cons, ptVars, rest, halfway, halfway, "secondHalf");
            } else {
                cons.addParameter(ptVars.get(0), "item" + i);
                cons.addCode(CodeBlock
                        .builder()
                        .addStatement("this.$L[$L] = item$L",
                                      TUPLE_ITEMS_FIELD_NAME,
                                      i - 1,
                                      i).build());
            }
            spec.addTypeVariables(ptVars);
            spec.addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
                    .addMember("value", "$S", "unchecked")
                    .addMember("value", "$S", "rawtypes").build());
            for (int j = 0; j < ptVars.size(); j++) {
                TypeName typeVar = ptVars.get(j);
                MethodSpec.Builder mSpec =
                        MethodSpec.methodBuilder("getItem" + (j + 1));
                mSpec.addModifiers(Modifier.PUBLIC);
                mSpec.returns(typeVar);
                mSpec.addCode(CodeBlock
                        .builder()
                        .addStatement("return($T)this.$L[$L]",
                                      typeVar,
                                      TUPLE_ITEMS_FIELD_NAME,
                                      j).build());
                spec.addMethod(mSpec.build());
            }
            spec.addSuperinterface(tupleInterface);
            spec.addMethod(MethodSpec
                    .methodBuilder("toArray")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(Object[].class)
                    .addCode("return this.$L.clone();\n",
                             TUPLE_ITEMS_FIELD_NAME).build());
            FieldSpec.Builder items =
                    FieldSpec.builder(Object[].class,
                                      TUPLE_ITEMS_FIELD_NAME,
                                      Modifier.PRIVATE,
                                      Modifier.FINAL);
            items.initializer("new Object[$L]", i);
            spec.addField(items.build());
            ParameterizedTypeName type =
                    ParameterizedTypeName.get(className, ptVars
                            .toArray(EMPTY_TYPENAME_ARRAY));
            spec.addMethod(cons.build());
            spec.addMethod(makeEquals(i, type));
            spec.addMethod(makeHashCode(i, type));
            spec.addMethod(makeToString(i, type));
            JavaFile file =
                    JavaFile.builder(getPackage(this.count, i), spec.build())
                            .skipJavaLangImports(true).indent("").build();
            try {
                file.writeTo(Paths.get("src/main/java"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void setupHalf(MethodSpec.Builder cons,
            List<TypeVariableName> ptVars, int tupleSize, int start, int end,
            String halfName) {
        cons.addStatement("$T.arraycopy($L.toArray(),0,this.$L,$L,$L)",
                          System.class,
                          halfName,
                          TUPLE_ITEMS_FIELD_NAME,
                          start,
                          end);
        ClassName classNameH =
                ClassName.get(getPackage(this.count, tupleSize), "Tuple"
                        + tupleSize);
        ParameterizedTypeName typeH =
                ParameterizedTypeName
                        .get(classNameH,
                             ptVars.subList(start, start + tupleSize)
                                     .toArray(EMPTY_TYPENAME_ARRAY));
        cons.addParameter(typeH, halfName);
    }

    private void generateTupleInterface(ClassName tupleInterface) {
        TypeSpec tupleSpec =
                TypeSpec.interfaceBuilder(tupleInterface.simpleName())
                        .addModifiers(Modifier.PUBLIC)
                        .addMethod(MethodSpec
                                .methodBuilder("toArray")
                                .addModifiers(Modifier.PUBLIC,
                                              Modifier.ABSTRACT)
                                .returns(Object[].class).build()).build();
        JavaFile file =
                JavaFile.builder(PACKAGE, tupleSpec).skipJavaLangImports(true)
                        .indent("").build();
        try {
            file.writeTo(Paths.get("src/main/java"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private MethodSpec makeEquals(int i, ParameterizedTypeName type) {
        MethodSpec.Builder spec = MethodSpec.methodBuilder("equals");
        spec.addAnnotation(Override.class);
        spec.addModifiers(Modifier.PUBLIC);
        spec.returns(boolean.class);
        spec.addParameter(Object.class, "other");
        CodeBlock.Builder block = CodeBlock.builder();
        block.addStatement("return other==this||"
                                   + "other instanceof $T&&$T.deepEquals(this.$L,(($T)other).$L)",
                           type.rawType,
                           Arrays.class,
                           TUPLE_ITEMS_FIELD_NAME,
                           type.rawType,
                           TUPLE_ITEMS_FIELD_NAME);
        spec.addCode(block.build());
        return spec.build();
    }

    private MethodSpec makeHashCode(int i, ParameterizedTypeName type) {
        MethodSpec.Builder spec = MethodSpec.methodBuilder("hashCode");
        spec.addAnnotation(Override.class);
        spec.addModifiers(Modifier.PUBLIC);
        spec.returns(int.class);
        CodeBlock.Builder block = CodeBlock.builder();
        block.addStatement("return $T.deepHashCode(this.$L)",
                           Arrays.class,
                           TUPLE_ITEMS_FIELD_NAME);
        spec.addCode(block.build());
        return spec.build();
    }

    private MethodSpec makeToString(int i, ParameterizedTypeName type) {
        MethodSpec.Builder spec = MethodSpec.methodBuilder("toString");
        spec.addAnnotation(Override.class);
        spec.addModifiers(Modifier.PUBLIC);
        spec.returns(String.class);
        CodeBlock.Builder block = CodeBlock.builder();
        block.addStatement("$T str=$T.toString(this.$L)",
                           String.class,
                           Arrays.class,
                           TUPLE_ITEMS_FIELD_NAME);
        block.addStatement("return '('+str.substring(1,str.length()-1)+')'");
        spec.addCode(block.build());
        return spec.build();
    }
}

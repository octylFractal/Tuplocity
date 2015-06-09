package me.kenzierocks.tuplocity;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Modifier;

import com.google.common.base.Objects;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

public class TupleGenerator {

    private static final TypeName[] EMPTY_TYPENAME_ARRAY = new TypeName[0];
    private static final String PACKAGE = "me.kenzierocks.tuplocity.tuples";

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

    private final int count;

    public TupleGenerator(int count) {
        this.count = count;
    }

    public int getCount() {
        return this.count;
    }

    public void generateTupleFiles() {
        for (int i = 1; i <= this.count; i++) {
            TypeSpec.Builder spec = TypeSpec.classBuilder("Tuple" + i);
            spec.addModifiers(Modifier.PUBLIC, Modifier.FINAL);
            MethodSpec.Builder cons = MethodSpec.constructorBuilder();
            cons.addModifiers(Modifier.PUBLIC);
            List<TypeName> ptVars = new ArrayList<>(i);
            for (int m = 1; m <= i; m++) {
                TypeVariableName typeVar = TypeVariableName.get("$" + m);
                MethodSpec.Builder mSpec =
                        MethodSpec.methodBuilder("getItem" + m);
                mSpec.addModifiers(Modifier.PUBLIC);
                mSpec.returns(typeVar);
                mSpec.addCode(CodeBlock.builder()
                        .addStatement("return this.item$L", m).build());
                spec.addMethod(mSpec.build());
                spec.addField(typeVar,
                              "item" + m,
                              Modifier.PRIVATE,
                              Modifier.FINAL);
                spec.addTypeVariable(typeVar);
                if (m < i) {
                    ptVars.add(typeVar);
                    cons.addCode(CodeBlock
                            .builder()
                            .addStatement("this.item$L = last.getItem$L()",
                                          m,
                                          m).build());
                }
            }
            if (i > 1) {
                ClassName className =
                        ClassName.get(getPackage(this.count, i - 1), "Tuple"
                                + (i - 1));
                ParameterizedTypeName type =
                        ParameterizedTypeName.get(className, ptVars
                                .toArray(EMPTY_TYPENAME_ARRAY));
                cons.addParameter(type, "last");
            }
            ClassName className =
                    ClassName.get(getPackage(this.count, i), "Tuple" + i);
            ptVars.add(TypeVariableName.get("$" + i));
            cons.addParameter(TypeVariableName.get("$" + i), "item" + i);
            cons.addCode(CodeBlock.builder()
                    .addStatement("this.item$L = item$L", i, i).build());
            TypeName[] typeVars = ptVars.toArray(EMPTY_TYPENAME_ARRAY);
            ParameterizedTypeName type =
                    ParameterizedTypeName.get(className, typeVars);
            spec.addMethod(cons.build());
            spec.addMethod(makeEquals(i, type));
            spec.addMethod(makeHashCode(i, type));
            spec.addMethod(makeToString(i, type));
            JavaFile file =
                    JavaFile.builder(getPackage(this.count, i), spec.build())
                            .skipJavaLangImports(true).indent("    ").build();
            try {
                file.writeTo(Paths.get("src/main/java"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private MethodSpec makeEquals(int i, ParameterizedTypeName type) {
        MethodSpec.Builder spec = MethodSpec.methodBuilder("equals");
        spec.addAnnotation(Override.class);
        spec.addModifiers(Modifier.PUBLIC);
        spec.returns(boolean.class);
        spec.addParameter(Object.class, "other");
        CodeBlock.Builder block = CodeBlock.builder();
        block.beginControlFlow("if (other == this)");
        block.addStatement("return true");
        block.endControlFlow();
        block.beginControlFlow("if (other instanceof $T)", type.rawType);
        // block.add("@$T($S)\n", SuppressWarnings.class, "unchecked");
        TypeName[] typeNames = type.typeArguments.toArray(EMPTY_TYPENAME_ARRAY);
        for (int j = 0; j < typeNames.length; j++) {
            typeNames[j] = WildcardTypeName.subtypeOf(Object.class);
        }
        ParameterizedTypeName wildType =
                ParameterizedTypeName.get(type.rawType, typeNames);
        block.addStatement("$T otherT = ($T) other", wildType, wildType);
        for (int m = 1; m <= i; m++) {
            block.beginControlFlow("if (!(this.$L == otherT.$L || "
                                           + "(this.$L != null && this.$L.equals(otherT.$L))))",
                                   "item" + m,
                                   "item" + m,
                                   "item" + m,
                                   "item" + m,
                                   "item" + m);
            block.addStatement("return false");
            block.endControlFlow();
        }
        block.addStatement("return true");
        block.endControlFlow();
        block.addStatement("return false");
        spec.addCode(block.build());
        return spec.build();
    }

    private static final int GOOD_HASHCODE_MULTIPLIER = 1000003;

    private MethodSpec makeHashCode(int i, ParameterizedTypeName type) {
        MethodSpec.Builder spec = MethodSpec.methodBuilder("hashCode");
        spec.addAnnotation(Override.class);
        spec.addModifiers(Modifier.PUBLIC);
        spec.returns(int.class);
        CodeBlock.Builder block = CodeBlock.builder();
        block.addStatement("int h = 1");
        for (int m = 1; m <= i; m++) {
            block.addStatement("h *= $L", GOOD_HASHCODE_MULTIPLIER);
            block.addStatement("h ^= $L == null ? 0 : $L.hashCode()",
                               "this.item" + m,
                               "this.item" + m);
        }
        block.addStatement("return h");
        spec.addCode(block.build());
        return spec.build();
    }

    private MethodSpec makeToString(int i, ParameterizedTypeName type) {
        MethodSpec.Builder spec = MethodSpec.methodBuilder("toString");
        spec.addAnnotation(Override.class);
        spec.addModifiers(Modifier.PUBLIC);
        spec.returns(String.class);
        CodeBlock.Builder block = CodeBlock.builder();
        block.addStatement("$T str = new $T($S)",
                           StringBuilder.class,
                           StringBuilder.class,
                           "(");
        block.add("str");
        for (int m = 1; m <= i; m++) {
            block.add(".append(this.item$L)", m);
            if (m < i) {
                block.add(".append($S)", ",");
                block.add("\n");
                if (m == 1) {
                    block.indent();
                }
            } else {
                block.add(";\n");
                if (i != 1) {
                    block.unindent();
                }
            }
        }
        block.addStatement("str.append($S)", ")");
        block.addStatement("return str.toString()");
        spec.addCode(block.build());
        return spec.build();
    }
}

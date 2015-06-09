package me.kenzierocks.tuplocity;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Modifier;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

public class TupleGenerator {

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
                                .toArray(new TypeName[0]));
                cons.addParameter(type, "last");
            }
            cons.addParameter(TypeVariableName.get("$" + i), "item" + i);
            cons.addCode(CodeBlock.builder()
                    .addStatement("this.item$L = item$L", i, i).build());
            spec.addMethod(cons.build());
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
}

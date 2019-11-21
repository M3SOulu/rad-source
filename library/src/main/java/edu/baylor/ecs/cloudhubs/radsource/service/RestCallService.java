package edu.baylor.ecs.cloudhubs.radsource.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import edu.baylor.ecs.cloudhubs.radsource.context.RestCall;
import edu.baylor.ecs.cloudhubs.radsource.model.RestTemplateMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class RestCallService {
    public List<RestCall> findRestCalls(File sourceFile) throws IOException {
        List<RestCall> restCalls = new ArrayList<>();

        CompilationUnit cu = StaticJavaParser.parse(sourceFile);

        // don't analyse further if no RestTemplate import exists
        if (!hasRestTemplateImport(cu)) {
            log.debug("no RestTemplate found");
            return null;
        }

        String packageName = findPackage(cu);
        log.debug("package: " + packageName);

        // loop through class declarations
        for (ClassOrInterfaceDeclaration cid : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            String className = cid.getNameAsString();
            log.debug("class: " + className);

            // loop through methods
            for (MethodDeclaration md : cid.findAll(MethodDeclaration.class)) {
                String methodName = md.getNameAsString();
                log.debug("method: " + methodName);

                // loop through method calls
                for (MethodCallExpr mce : md.findAll(MethodCallExpr.class)) {
                    String methodCall = mce.getNameAsString();

                    RestTemplateMethod restTemplateMethod = RestTemplateMethod.findByName(methodCall);

                    if (restTemplateMethod != null) {
                        log.debug("method-call: " + methodCall);

                        Expression scope = mce.getScope().orElse(null);

                        // match field access
                        if (scope != null && scope.isFieldAccessExpr() &&
                                isRestTemplateField(cid, scope.asFieldAccessExpr().getNameAsString())) {

                            log.debug("field-access: " + scope.asFieldAccessExpr().getNameAsString());

                            // everything matched here

                            // construct rest call
                            RestCall restCall = new RestCall();
                            restCall.setSource(sourceFile.getCanonicalPath().toString());
                            restCall.setParentMethod(packageName + "." + className + "." + methodName);
                            restCall.setHttpMethod(restTemplateMethod.getHttpMethod().toString());

                            // find return type
                            resolveReturnType(restCall, cu, mce, restTemplateMethod);
                            log.debug("rest-call: " + restCall);

                            // add to list of restCall
                            restCalls.add(restCall);
                        }
                    }
                }
            }
        }

        return restCalls;
    }

    private String findPackage(CompilationUnit cu) {
        for (PackageDeclaration pd : cu.findAll(PackageDeclaration.class)) {
            return pd.getNameAsString();
        }
        return null;
    }

    // populate return type in restCall
    private void resolveReturnType(RestCall restCall, CompilationUnit cu, MethodCallExpr mce, RestTemplateMethod restTemplateMethod) {
        log.debug("arguments: " + mce.getArguments());

        String returnType = null;
        boolean isCollection = false;

        if (mce.getArguments().size() > restTemplateMethod.getResponseTypeIndex()) {
            String param = mce.getArguments().get(restTemplateMethod.getResponseTypeIndex()).toString();

            if (param.endsWith(".class")) {
                param = param.replace(".class", "");
            }

            if (param.endsWith("[]")) {
                param = param.replace("[]", "");
                isCollection = true;
            }

            log.debug("param: " + param);
            returnType = findFQClassName(cu, param);
        }

        restCall.setReturnType(returnType);
        restCall.setCollection(isCollection);
    }

    private String findFQClassName(CompilationUnit cu, String param) {
        if (param.equals("String")) {
            return "java.lang.String";
        } else if (param.equals("Object")) {
            return "java.lang.Object";
        }

        for (ImportDeclaration id : cu.findAll(ImportDeclaration.class)) {
            if (id.getNameAsString().endsWith(param)) {
                log.debug("import: " + id.getNameAsString());
                return id.getNameAsString();
            }
        }

        return param; // if FQ name not found then return original
    }

    private boolean hasRestTemplateImport(CompilationUnit cu) {
        for (ImportDeclaration id : cu.findAll(ImportDeclaration.class)) {
            if (id.getNameAsString().equals("org.springframework.web.client.RestTemplate")) {
                return true;
            }
        }
        return false;
    }

    private boolean isRestTemplateField(ClassOrInterfaceDeclaration cid, String fieldName) {
        for (FieldDeclaration fd : cid.findAll(FieldDeclaration.class)) {
            if (fd.getElementType().toString().equals("RestTemplate") &&
                    fd.getVariables().toString().contains(fieldName)) {

                return true;
            }
        }
        return false;
    }

    private List<String> findAllRestTemplateFields(ClassOrInterfaceDeclaration cid) {
        List<String> fields = new ArrayList<>();

        for (FieldDeclaration fd : cid.findAll(FieldDeclaration.class)) {
            if (fd.getElementType().toString().equals("RestTemplate")) {
                for (VariableDeclarator variable : fd.getVariables()) {
                    fields.add(variable.getNameAsString());
                }
            }
        }

        return fields;
    }
}
/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformers.gradle.tests;

import java.io.IOException;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class FunctionalTests extends FunctionalTestsBase {
    private static final String BUILD = ":build";

    public FunctionalTests() {
        super("9.0.0");
    }

    @Test
    public void makeFieldAccessible() throws IOException {
        makeFieldAccessible(new GroovyProject(projectDir));
    }

    @Test
    public void makeFieldAccessibleKotlin() throws IOException {
        makeFieldAccessible(new KotlinProject(projectDir));
    }

    private void makeFieldAccessible(GradleProject project) throws IOException {
        project.transformDependency("org.apache.maven:maven-artifact:3.9.11", "transformer.cfg");
        project.writeFile(
            "transformer.cfg",
            "public org.apache.maven.artifact.versioning.DefaultArtifactVersion majorVersion"
        );
        project.writeFile("src/java/Test.java", """
            import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

            public class Test {
                public static void test() {
                    new DefaultArtifactVersion("0").majorVersion;
                }
            }
            """);

        var results = build(BUILD);
        assertTaskSuccess(results, BUILD);
    }

    @Test
    public void makeMethodAccessible() throws IOException {
        makeMethodAccessible(new GroovyProject(projectDir));
    }

    @Test
    public void makeMethodAccessibleKotlin() throws IOException {
        makeMethodAccessible(new KotlinProject(projectDir));
    }

    private void makeMethodAccessible(GradleProject project) throws IOException {
        project.transformDependency("org.ow2.asm:asm:9.7", "transformer.cfg");
        writeMethodTest(project);

        var results = build(BUILD);
        assertTaskSuccess(results, BUILD);
    }

    @Test
    @Disabled // I don't know what the point of the constraint system was so I don't have a good test
    public void makeTransitiveAccessible() throws IOException {
        makeTransitiveAccessible(new GroovyProject(projectDir));
    }

    @Test
    @Disabled // I don't know what the point of the constraint system was so I don't have a good test
    public void makeTransitiveAccessibleKotlin() throws IOException {
        makeTransitiveAccessible(new KotlinProject(projectDir));
    }

    private void makeTransitiveAccessible(GradleProject project) throws IOException {
        project.transformConstraint("org.ow2.asm:asm-tree:9.7", "org.ow2.asm:asm", "transformer.cfg");
        writeMethodTest(project);

        var results = build(BUILD);
        assertTaskSuccess(results, BUILD);
    }

    private void writeMethodTest(GradleProject project) throws IOException {
        project.writeFile(
            "transformer.cfg",
            "public org.objectweb.asm.Attribute <init>(Ljava/lang/String;)V"
        );
        project.writeFile("src/java/Test.java", """
            import org.objectweb.asm.Attribute;

            public class Test {
                public static void test() {
                    new Attribute("123");
                }
            }
            """);
    }
}

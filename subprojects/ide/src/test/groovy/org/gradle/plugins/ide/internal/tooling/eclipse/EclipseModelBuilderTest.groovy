/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.plugins.ide.internal.tooling.eclipse

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.GroovyBasePlugin
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.scala.ScalaBasePlugin
import org.gradle.api.plugins.scala.ScalaPlugin
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.eclipse.model.BuildCommand
import org.gradle.plugins.ide.internal.tooling.EclipseModelBuilder
import org.gradle.plugins.ide.internal.tooling.GradleProjectBuilder
import org.gradle.tooling.internal.gradle.DefaultGradleProject
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Unroll

class EclipseModelBuilderTest extends Specification {

    Project project
    Project child1
    Project child2

    def setup() {
        project = TestUtil.builder().withName("project").build()
        child1 = TestUtil.builder().withName("child1").withParent(project).build()
        child2 = TestUtil.builder().withName("child2").withParent(project).build()
        [project, child1, child2].each { it.pluginManager.apply(EclipsePlugin.class) }
    }

    def "can read natures"() {
        setup:
        project.eclipse.project.natures = ['nature.a', 'nature.b']
        def modelBuilder = createEclipseModelBuilder()

        when:
        def eclipseModel = modelBuilder.buildAll("org.gradle.tooling.model.eclipse.EclipseProject", project)

        then:
        eclipseModel.projectNatures.collect { it.id } == ['nature.a', 'nature.b']
    }

    def "nature list independent from project hierarchy"() {
        setup:
        project.eclipse.project.natures = ['nature.for.root']
        child1.eclipse.project.natures = ['nature.for.child1']
        child2.eclipse.project.natures = ['nature.for.child2']
        def modelBuilder = createEclipseModelBuilder()

        when:
        def eclipseModel = modelBuilder.buildAll("org.gradle.tooling.model.eclipse.EclipseProject", project)

        then:
        eclipseModel.projectNatures.collect { it.id } == ['nature.for.root']
        eclipseModel.children[0].name == 'child1'
        eclipseModel.children[0].projectNatures.collect { it.id } == ['nature.for.child1']
        eclipseModel.children[1].name == 'child2'
        eclipseModel.children[1].projectNatures.collect { it.id } == ['nature.for.child2']
    }

    def "can read build commands"() {
        setup:
        project.eclipse.project.buildCommands = [
            new BuildCommand('buildCommandWithoutArguments', [:]),
            new BuildCommand('buildCommandWithArguments', ['argumentOneKey': 'argumentOneValue', 'argumentTwoKey': 'argumentTwoValue'])
        ]
        def modelBuilder = createEclipseModelBuilder()

        when:
        def eclipseModel = modelBuilder.buildAll("org.gradle.tooling.model.eclipse.EclipseProject", project)

        then:
        eclipseModel.buildCommands.collect { it.name } == ['buildCommandWithoutArguments', 'buildCommandWithArguments']
        eclipseModel.buildCommands.collect { it.arguments } == [[:], ['argumentOneKey': 'argumentOneValue', 'argumentTwoKey': 'argumentTwoValue']]
    }

    def "build command list independent from project hierarchy"() {
        setup:
        project.eclipse.project.buildCommands = [new BuildCommand('rootBuildCommand', ['rootKey': 'rootValue'])]
        child1.eclipse.project.buildCommands = [new BuildCommand('child1BuildCommand', ['child1Key': 'child1Value'])]
        child2.eclipse.project.buildCommands = [new BuildCommand('child2BuildCommand', ['child2Key': 'child2Value'])]
        def modelBuilder = createEclipseModelBuilder()

        when:
        def eclipseModel = modelBuilder.buildAll("org.gradle.tooling.model.eclipse.EclipseProject", project)

        then:
        eclipseModel.buildCommands.collect { it.name } == ['rootBuildCommand']
        eclipseModel.buildCommands.collect { it.arguments } == [['rootKey': 'rootValue']]
        eclipseModel.children[0].name == 'child1'
        eclipseModel.children[0].buildCommands.collect { it.name } == ['child1BuildCommand']
        eclipseModel.children[0].buildCommands.collect { it.arguments } == [['child1Key': 'child1Value']]
        eclipseModel.children[1].name == 'child2'
        eclipseModel.children[1].buildCommands.collect { it.name } == ['child2BuildCommand']
        eclipseModel.children[1].buildCommands.collect { it.arguments } == [['child2Key': 'child2Value']]
    }

    def "no java source settings set for non-JVM projects"() {
        given:
        def modelBuilder = createEclipseModelBuilder()

        when:
        def eclipseModel = modelBuilder.buildAll("org.gradle.tooling.model.eclipse.EclipseProject", project)

        then:
        eclipseModel.javaSourceSettings == null
    }

    @Unroll
    def "default #type language level are set for #projectType projects if compatibility setting not specified"() {
        given:
        def modelBuilder = createEclipseModelBuilder()
        project.plugins.apply(pluginType)

        when:
        def eclipseModel = modelBuilder.buildAll("org.gradle.tooling.model.eclipse.EclipseProject", project)

        then:
        eclipseModel.javaSourceSettings."$languageLevelProperty" == JavaVersion.current()

        where:
        type     | compatibilityProperty | languageLevelProperty | projectType | pluginType
        "source" | "sourceCompatibility" | "sourceLanguageLevel" | "java"      | JavaBasePlugin
        "target" | "targetCompatibility" | "targetBytecodeLevel" | "java"      | JavaBasePlugin
        "source" | "sourceCompatibility" | "sourceLanguageLevel" | "scala"     | ScalaBasePlugin
        "target" | "targetCompatibility" | "targetBytecodeLevel" | "scala"     | ScalaBasePlugin
        "source" | "sourceCompatibility" | "sourceLanguageLevel" | "groovy"    | GroovyBasePlugin
        "target" | "targetCompatibility" | "targetBytecodeLevel" | "groovy"    | GroovyBasePlugin
    }

    def "default language levels are set for JVM projects if compatibility is set to null"() {
        given:
        def modelBuilder = createEclipseModelBuilder()
        project.plugins.apply(pluginType)
        project.sourceCompatibility = null
        project.targetCompatibility = null

        when:
        def eclipseModel = modelBuilder.buildAll("org.gradle.tooling.model.eclipse.EclipseProject", project)

        then:
        eclipseModel.javaSourceSettings.sourceLanguageLevel == org.gradle.api.JavaVersion.current()
        eclipseModel.javaSourceSettings.targetBytecodeLevel == org.gradle.api.JavaVersion.current()

        where:
        pluginType << [JavaPlugin, GroovyPlugin, ScalaPlugin]
    }

    @Unroll
    def "custom #type language level derived Java plugin convention"() {
        given:
        def modelBuilder = createEclipseModelBuilder()
        project.plugins.apply(JavaPlugin)
        project."$compatibilityProperty" = "1.2"

        when:
        def eclipseModel = modelBuilder.buildAll("org.gradle.tooling.model.eclipse.EclipseProject", project)

        then:
        eclipseModel.javaSourceSettings."$languageLevelProperty" == JavaVersion.VERSION_1_2

        where:
        type     | compatibilityProperty | languageLevelProperty
        "source" | "sourceCompatibility" | "sourceLanguageLevel"
        "target" | "targetCompatibility" | "targetBytecodeLevel"
    }

    @Unroll
    def "#type language level derived from eclipse jdt overrules java plugin convention configuration"() {
        given:
        def modelBuilder = createEclipseModelBuilder()
        project.plugins.apply(JavaPlugin)
        project.plugins.apply(EclipsePlugin)
        project."$compatibilityProperty" = "1.2"
        project.eclipse.jdt."$compatibilityProperty" = "1.3"

        when:
        def eclipseModel = modelBuilder.buildAll("org.gradle.tooling.model.eclipse.EclipseProject", project)

        then:
        eclipseModel.javaSourceSettings."$languageLevelProperty" == JavaVersion.VERSION_1_3

        where:
        type     | compatibilityProperty | languageLevelProperty
        "source" | "sourceCompatibility" | "sourceLanguageLevel"
        "target" | "targetCompatibility" | "targetBytecodeLevel"
    }

    @Unroll
    def "multi-project build can have different #type language level per project"() {
        given:
        def modelBuilder = createEclipseModelBuilder()
        child1.plugins.apply(JavaPlugin)
        child1.plugins.apply(EclipsePlugin)
        child1.eclipse.jdt."$compatibilityProperty" = "1.2"
        child2.plugins.apply(JavaPlugin)
        child2.plugins.apply(EclipsePlugin)
        child2."$compatibilityProperty" = "1.3"
        child2.eclipse.jdt."$compatibilityProperty" = "1.1"

        when:
        def eclipseModel = modelBuilder.buildAll("org.gradle.tooling.model.eclipse.EclipseProject", project)

        then:
        eclipseModel.children.find { it.name == "child1" }.javaSourceSettings."$languageLevelProperty" == JavaVersion.VERSION_1_2
        eclipseModel.children.find { it.name == "child2" }.javaSourceSettings."$languageLevelProperty" == JavaVersion.VERSION_1_1

        where:
        type     | compatibilityProperty | languageLevelProperty
        "source" | "sourceCompatibility" | "sourceLanguageLevel"
        "target" | "targetCompatibility" | "targetBytecodeLevel"
    }

    private def createEclipseModelBuilder() {
        def gradleProjectBuilder = Mock(GradleProjectBuilder)
        gradleProjectBuilder.buildAll(_) >> Mock(DefaultGradleProject)
        new EclipseModelBuilder(gradleProjectBuilder)
    }
}

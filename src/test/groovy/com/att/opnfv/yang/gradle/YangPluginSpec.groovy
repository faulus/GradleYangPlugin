package com.att.opnfv.yang.gradle

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class YangPluginSpec extends Specification {
	
	def "Yang plugin registers YangGenerate task and no others"() {
		given:
		Project project = ProjectBuilder.builder().build()
		
		when: "plugin is applied to project"
		project.apply(plugin: YangPlugin)
		
		then: "Yang task is registered"
//		project.afterEvaluate {
			project.tasks.withType(YangGenerateTask).collect { it.name }.sort() == ["yangGenerate"]
	//	}
	}

	def "plugin copies data from extension object"() {
		def	path = "${File.separatorChar}abc"
		given:
		Project project = ProjectBuilder.builder().build()
		project.apply(plugin: YangPlugin)
		
		when: "plugin is applied to project"
		project.extensions.yang.yangFilesRootDir = path
		project.evaluate() // Task is configured after evaluation, so we have to force this
		
		then: "Yang task gets sourcedir set from extension object"
		project.yangGenerate.yangFilesRootDir.getPath().endsWith(path)
	}
}

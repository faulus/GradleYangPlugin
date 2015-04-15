package com.att.opnfv.yang.gradle

import org.gradle.api.Project

class YangExtension {
	private Project	project
	
	Collection<CodeGeneratorConfig>	generators	= new ArrayList<>()
	String						yangFilesRootDir
	String[]					excludeFiles
	boolean						inspectDependencies
	String						yangFilesConfiguration
	String						generatorsConfiguration
	
	YangExtension(Project project) {
		this.project	= project
	}
	
	CodeGeneratorConfig generator(Closure closure) {
		def generator = project.configure(new CodeGeneratorConfig(project), closure)
		generators.add(generator)
		return generator
	}
}

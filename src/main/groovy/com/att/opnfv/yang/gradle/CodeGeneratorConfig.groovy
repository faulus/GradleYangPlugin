package com.att.opnfv.yang.gradle

import org.gradle.api.Project
import org.opendaylight.yangtools.yang2sources.spi.BasicCodeGenerator

class CodeGeneratorConfig {
	private Project				project
	String				generatorClassName
	BasicCodeGenerator	instance
	String				outputDir
	File				outputDirFile
	String				resourceBaseDir
	File				resourceBaseDirFile
	Map					additionalConfiguration
	
	CodeGeneratorConfig(Project project) {
		this.project	= project
		this.resourceBaseDir		= project.getBuildDir().getPath() + File.separator + "classes"
		this.resourceBaseDirFile	= project.file(this.resourceBaseDir)
	}
	
	public void setOutputDir(String outputDir) {
		this.outputDir		= outputDir
		this.outputDirFile	= project.file(outputDir)
	}

	public void setResourceBaseDir(String resourceBaseDir) {
		this.resourceBaseDir		= resourceBaseDir
		this.resourceBaseDirFile	= project.file(resourceBaseDir)
	}
}

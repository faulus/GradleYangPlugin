package com.att.opnfv.yang.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import org.apache.commons.io.FilenameUtils

/**
 * The Gradle Yang plugin will compile any Yang modules found, and will optionally provide any Yang modules
 * found in the classpath, for import to the compiled Yang files.  It will then execute the specified code
 * generators.
 * 
 * The plugin will configure the YangGenerate task to run before the compileJava task, by having the latter
 * depend on the former.  The YangGenerate task will add the output folders for each generator to the
 * main sourceset, so the compileJava task will compile the generated sources in addition to the main source.
 * 
 * It will produce a jar file with the application classes in the root of the jar, along with the specified
 * Yang source files stored in the "META-INF/yang" folder of the resulting jar file.
 * 
 * The classpath used to search for Yang files to import can be limited to a specific configuration, or the
 * default classpath if no configuration is specified.  Similarly, the classpath used to search for code
 * generators can be limited to a specific classpath, if a configuration is specified for it.  Otherwise, it
 * will search the entire classpath.
 * 
 * @author dk068x
 */
class YangPlugin implements Plugin<Project> {
	public static final	YANG_EXT			= 'yang'
	public static final YANG_GENERATE_TASK	= 'yangGenerate'
	public static final SRC_MAIN_RESOURCES	= "src/main/resources/" // Has to end with slash
	public static final META_INF_YANG		= "META-INF/yang"
	
	/** The apply method just has to configure the plugin and the task, it doesn't execute the task. */
	@Override
	public void apply(Project project) {
		project.plugins.apply(JavaPlugin) // Apply Java plugin if it hasn't already been applied
		// Pass project to YangExtension so it can create CodeGeneratorConfig
		YangExtension		yang	= project.extensions.create(YANG_EXT, YangExtension, project)
		YangGenerateTask	task	= project.task(YANG_GENERATE_TASK, type: YangGenerateTask)
		// These things can only be done after the project is evaluated.
		project.afterEvaluate {
			task.init(project, yang)
			project.compileJava.dependsOn task // Source needs to be generated before compilation
			// edge case, but don't do anything else if the value of yangFilesRootDir doesn't exist on FS.
			if (task.yangFilesRootDir && task.yangFilesRootDir.exists()) {
				ProcessResources	processResourcesTask = project.processResources
				processResourcesTask.from(task.yangFilesRootDir) { into(META_INF_YANG) }
				
				Jar	jarTask	= project.jar
				// This exclude is done so that the Yang files aren't copied by default to the root of the jar.
				// In almost all cases, "yangFilesRootDir" will be "src/main/resources/yang", so we'd just
				// have to exclude "yang".  In the unusual case where they are stored somewhere else, then
				// either don't exclude them (because they wouldn't be copied by default), or specify the correct
				// relative path to exclude.
				if (task.yangFilesRootDir.getPath().startsWith(FilenameUtils.normalize(SRC_MAIN_RESOURCES))) {
					jarTask.exclude(task.yangFilesRootDir.getPath() - FilenameUtils.normalize(SRC_MAIN_RESOURCES))
				}
				// This makes the Yang files copy from the source into META-INF/yang in the jar file.
				jarTask.from(task.yangFilesRootDir) { into(META_INF_YANG) }
				
				// This has to be done here instead of in the task execution, because it's possible that the
				// generate task will be "up to date" and not run, but the "compileJava" and "jar" tasks need to
				// know what directories to get source from, and what directories to jar up.
				task.generators.each { CodeGeneratorConfig codeGeneratorConfig ->
					project.sourceSets.main.java.srcDirs		+= codeGeneratorConfig.getOutputDir()
					project.sourceSets.main.resources.srcDirs	+= codeGeneratorConfig.getResourceBaseDir()
				}
			}
		}
	}
}

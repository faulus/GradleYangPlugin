package com.att.opnfv.yang.gradle

import java.io.File;
import java.util.regex.Pattern
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.inject.Inject;

import org.apache.commons.io.filefilter.IOFileFilter
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.InputDirectory
import org.opendaylight.yangtools.yang.model.api.Module
import org.opendaylight.yangtools.yang.model.api.SchemaContext
import org.opendaylight.yangtools.yang.parser.impl.YangParserImpl
import org.opendaylight.yangtools.yang.parser.util.NamedFileInputStream
import org.opendaylight.yangtools.yang2sources.spi.BasicCodeGenerator

import groovy.io.FileType

import com.google.common.collect.Maps;

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xeustechnologies.jcl.JarClassLoader
import org.xeustechnologies.jcl.JclObjectFactory
import groovy.transform.ToString

class YangGenerateTask extends DefaultTask {
	
	private Logger logger	= LoggerFactory.getLogger(TASK_NAME)

	private static final String	DEFAULT_CONFIGURATION_NAME	= "compile"	
	private static final String	DEFAULT_YANG_FILES_ROOT_DIR	= "src/main/yang"	
	private static final String META_INF_YANG_STRING		=
		"META-INF" + File.separator + "yang" + File.separator
	
	Project			project
	
	Collection<CodeGeneratorConfig>	generators
	boolean							inspectDependencies
	Collection<String>				excludeFiles
	String							yangFilesConfiguration
	String							generatorsConfiguration

	Pattern	yangFileNamePattern			= Pattern.compile("\\.yang\$")
	Pattern	yangFileInMetaInfPattern	= Pattern.compile("META-INF/yang/.*\\.yang\$")
		
	public YangGenerateTask() {
		description	= """Compiles Yang modules and generates code from modules
using code generators.

PARAMETERS (r:required, o:optional, N:unbounded):
yangFilesRootDir(r):          Relative or absolute path for where to find Yang source files
inspectDependencies(o):       If true, will search classpath for additional Yang modules for imports
yangFilesConfiguration(o):    If set, will limit Yang module search to the classpath of this config
excludeFiles(o):              Paths to Yang files to exclude from processing
generatorsConfiguration(o):   If set, will limit search for code generators to the classpath of this config
generator(N):                 Configuration for a single code generator
  generatorClassName(r):      Fully-qualified class name of a code generator
  outputDir(r):               Relative/absolute path to write generated source files
  resourceBaseDir(o):         Relative/absolute path to write additional resource files
  additionalConfiguration(o)  Map of additional properties passed to code generator
"""
		group		= "Yang"
	}	
	
	@InputDirectory
	File	yangFilesRootDir
	
	@Input
	def	getGeneratorClasses() {
		if (generators != null) {
			generators.collect { it.generatorClassName }
		}
		else {
			return Collections.emptyList()
		}
	}
	
	@InputFiles
	def getOptionalYangClasspath() {
		return inspectDependencies ? project.configurations[yangFilesConfiguration] : Collections.emptyList() 
	}
	
	@OutputDirectories
	def getOutputDirectories() {
		List<File>	outputDirectories	= new ArrayList<File>()
		if (generators != null) {
			outputDirectories.addAll(generators.collect { it.outputDirFile })
			generators.each {
				if (it.resourceBaseDirFile) {
					outputDirectories.add(it.resourceBaseDirFile)
				}
			}
		}
		return outputDirectories
	}
	
	@TaskAction
	void generate() {
		// Class "org.opendaylight.yangtools.yang2sources.plugin.YangToSourcesProcessor" defines process.
		// Will likely need some of the tools in "org.opendaylight.yangtools.yang2sources.plugin.Util", modified
		// for the Gradle ecosystem, like "findYangFilesInDependencies" for instance.
		// Instantiate YangParserImpl class.
		// Get list of all Yang files in input folder.
		// If "inspectDependencies" is true, add all Yang files found in classpath.
		// Parse all yang module files and create Set<Module> for results.
		// iterate through generators.
		// For each generator:
		//  Load class, verify subtype of BasicCodeGenerator.
		// Run "generateSources" method of BasicCodeGenerator.
		
		if (!yangFilesRootDir) {
			return
		}
		
		logger.info("Generating sources.")
		ContextHolder	context	= processYang(project)
		if (context != null) {
			generateSources(context)
		}
		else {
			logger.error("Unable to build a context.")
		}
	}
	
	private void generateSources(ContextHolder context) {
		if (generators.size() == 0) {
			logger.warn("No code generators provided.")
			return
		}
		
		Map<String, String>	thrownExceptions	= Maps.newHashMap();
		//TODO: Call gSWOG in try/catch block, accumulating exceptions to show after loop.
		generators.each {
			logger.info("Working on $it")
			generateSourcesWithOneGenerator(context, it)
		}
	}
	
	void generateSourcesWithOneGenerator(ContextHolder context, CodeGeneratorConfig codeGeneratorConfig) {
		BasicCodeGenerator	generatorInstance	= codeGeneratorConfig.getInstance()
		if (!generatorInstance) {
			if (generatorsConfiguration == DEFAULT_CONFIGURATION_NAME) {
				Class<?>	clazz	= Class.forName(codeGeneratorConfig.generatorClassName)
				generatorInstance	= (BasicCodeGenerator) clazz.newInstance()
			}
			else {
				// use JCL to load the class.
				JarClassLoader jcl = new JarClassLoader()
				project.configurations[generatorsConfiguration].files.each { File file ->
					jcl.add(file)
				}
				JclObjectFactory factory = JclObjectFactory.getInstance()
				Object	obj	= factory.create(jcl, codeGeneratorConfig.generatorClassName)
				generatorInstance	= (BasicCodeGenerator) obj
			}
		}
		
		generatorInstance.setAdditionalConfig(codeGeneratorConfig.additionalConfiguration)
		generatorInstance.setResourceBaseDir(codeGeneratorConfig.resourceBaseDirFile)
		
		Collection<File>	generatedFiles	=
			generatorInstance.generateSources(context.getContext(),
										  	  codeGeneratorConfig.getOutputDirFile(),
										      context.getYangModules())
	}
	
	private ContextHolder processYang(Project project) {
		List<Closeable>	closeables	= new ArrayList<>();
		
		Collection<String>	fullExcludeFiles	= excludeFiles.collect {
			"$yangFilesRootDir${File.separatorChar}$it".toString()
		}
		
		Collection<InputStream>	yangStreams			= new ArrayList<>()
		Collection<File>		yangFilesInProject	= new ArrayList<>()

		Set<Module>		parsedModules			= Collections.emptySet()
		SchemaContext	resolvedSchemaContext	= null

		try {
			yangFilesRootDir.eachFileRecurse(FileType.FILES) { File file ->
				if (fullExcludeFiles.contains(file.getPath())) {
					logger.info("Excluded \"$file\".")
				}
				else if (!yangFileNamePattern.matcher(file.getPath()).find()) {
					logger.info("File \"$file\" is not a Yang file.")
				}
				else {
					yangFilesInProject << file
					yangStreams << new NamedFileInputStream(file, META_INF_YANG_STRING + file.getName())
				}
			}

			if (inspectDependencies) {
				YangGenerateTask	thisTask	= this
				project.configurations[yangFilesConfiguration].files.each { File file ->
					yangStreams	+= getYangStreamsFromFile(file, excludeFiles)
				}
			}

			closeables.addAll(yangStreams)

			YangParserImpl	parser		= new YangParserImpl();

			Map<InputStream, Module>	moduleMap	= parser.parseYangModelsFromStreamsMapped(yangStreams)

			parsedModules			= new HashSet<>(moduleMap.values())
			resolvedSchemaContext	= parser.resolveSchemaContext(parsedModules)
		}
		finally {
			closeables.each { it.close() }
		}
		
		return new ContextHolder(resolvedSchemaContext, parsedModules)
	}
	
	Collection<InputStream> getYangStreamsFromFile(File cpFile, Collection<String> excludeFiles) {
		Collection<InputStream>	streams	= new ArrayList<>()
		if (cpFile.isDirectory()) {
			cpFile.eachFileRecurse(FileType.FILES) { File file ->
				if (file.getPath().matches(yangFileInMetaInfPattern)) {
					streams << new NamedFileInputStream(file, file.getAbsolutePath());
				}
			}
		}
		else if (cpFile.getPath().endsWith('.zip') || cpFile.getPath().endsWith(".jar")) {
			ZipFile	zipFile	= new ZipFile(cpFile)
			zipFile.entries().findAll { ZipEntry zipEntry ->
				!excludeFiles.contains(zipEntry.getName()) && zipEntry.getName().matches(yangFileInMetaInfPattern)
			}.collect { ZipEntry entry ->
				streams << new NamedInputStreamWrapper(zipFile.getInputStream(entry), entry.getName()) 
			}
		}
		else {
			if (cpFile.getPath().endsWith('.yang')) {
				streams << new NamedFileInputStream(cpFile, cpFile.getAbsolutePath());
			}
		}
		
		return streams
	}
	
	public void init(Project project, YangExtension yang) {
		this.yangFilesRootDir			= project.file(yang.yangFilesRootDir ?: DEFAULT_YANG_FILES_ROOT_DIR)
		this.project					= project
		this.generators					= yang.generators ?: Collections.emptyList()
		this.inspectDependencies		= yang.inspectDependencies
		this.excludeFiles				= yang.excludeFiles.collect { it } ?: Collections.emptyList()
		this.yangFilesConfiguration		= yang.yangFilesConfiguration ?: DEFAULT_CONFIGURATION_NAME
		this.generatorsConfiguration	= yang.generatorsConfiguration ?: DEFAULT_CONFIGURATION_NAME
		
		validate(project);
	}
	
	public boolean validate(Project project) {
		boolean	result	= true;
		// Validate settings.
		if (!generators) {
			logger.error("No generators found, nothing to do.")
			result	= false
		}
		else {
			generators.each { CodeGeneratorConfig generator ->
				if (!generator.generatorClassName) {
					logger.error("A generator is missing a class name")
					result	= false
				}
			}
		}
		
		if (yangFilesConfiguration) {
			if (!project.configurations[yangFilesConfiguration]) {
				logger.error("The specified configuration of \"$yangFilesConfiguration\" does not exist.")
				result	= false;
			}
		}
		
		if (generatorsConfiguration) {
			if (!project.configurations[generatorsConfiguration]) {
				logger.error("The specified configuration of \"$generatorsConfiguration\" does not exist.")
				result	= false;
			}
		}
		
		return result
	}
}

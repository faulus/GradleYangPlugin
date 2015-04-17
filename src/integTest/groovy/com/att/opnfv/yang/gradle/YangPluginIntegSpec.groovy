package com.att.opnfv.yang.gradle

import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult
import groovy.util.FileNameFinder
import spock.lang.Ignore
import org.gradle.api.logging.LogLevel

class YangPluginIntegSpec extends IntegrationSpec {
	
	def setup() {
//		System.setProperty("cleanProjectDir", "true")
	}
	
	def 'yangFilesRootDir exists'() {
		when:
		directory("src/main/yang")
		buildFile << applyPlugin(YangPlugin)
		buildFile << '''
			yang {
				yangFilesRootDir 'src/main/yang'
			}
		'''.stripIndent()

		ExecutionResult result = runTasksSuccessfully('build')

		then:
		!result.wasUpToDate("yangGenerate")
	}
	
	def 'yangFilesRootDir does not exist'() {
		when:
		directory("src/main/yang")
		buildFile << applyPlugin(YangPlugin)
		buildFile << '''
			yang {
				yangFilesRootDir 'src/main/other/yang'
			}
		'''.stripIndent()

		ExecutionResult result = runTasksWithFailure('build')

		then:
		!result.wasUpToDate("yangGenerate")
	}
	
	def 'handle trivial yang file with no code generators'() {
		when:
		directory("src/main/yang")
		File yangFile = createFile("src/main/yang/trivial.yang")
		yangFile << '''
			module trivial {
				namespace "trivial";
				prefix trivial;
			}
		'''.stripIndent()
		buildFile << applyPlugin(YangPlugin)
		buildFile << '''
			yang {
				yangFilesRootDir 'src/main/yang'
			}
		'''.stripIndent()

		ExecutionResult result = runTasksSuccessfully('build')

		then:
		result.standardOutput.contains("No code generators provided")
	}
	
	def 'process yang module with no types with mock generator'() {
		when:
		directory("src/main/yang")
		File yangFile = createFile("src/main/yang/trivial.yang")
		yangFile << '''
			module trivial {
				namespace "trivial";
				prefix trivial;
			}
		'''.stripIndent()
		
		// Note that we use a hand-coded mock here. Typical mocking frameworks can't work here because
		// the test is run in a remote process.
		buildFile << applyPlugin(YangPlugin)
		buildFile << """
			yang {
				yangFilesRootDir 'src/main/yang'
				generator {
					generatorClassName	= 'com.att.opnfv.yang.generator.MockCodeGenerator'
					outputDir 			= 'build/gen'
				}
			}
		""".stripIndent()

		ExecutionResult result = runTasksSuccessfully('build')
		println result
		
		then:
		result.standardOutput.contains("In setAdditionalConfig.")
		result.standardOutput.contains("In generateSources.")
	}

	def 'Rerun with no input changes does not run task'() {
		when:
		directory("src/main/yang")
		File yangFile = createFile("src/main/yang/trivial.yang")
		yangFile << '''
			module trivial {
				namespace "trivial";
				prefix trivial;
			}
		'''.stripIndent()
		
		// Note that we use a hand-coded mock here. Typical mocking frameworks can't work here because
		// the test is run in a remote process.
		buildFile << applyPlugin(YangPlugin)
		buildFile << """
			yang {
				yangFilesRootDir 'src/main/yang'
				generator {
					generatorClassName	= 'com.att.opnfv.yang.generator.MockCodeGenerator'
					outputDir 			= 'build/gen'
				}
			}
		""".stripIndent()

		ExecutionResult result = runTasksSuccessfully('build')
		println result
		
		then:
		result.standardOutput.contains("In setAdditionalConfig.")
		result.standardOutput.contains("In generateSources.")
		
		when:
		// If the task is run again with no input changes, then the task will not run.
		result	= runTasksSuccessfully('yangGenerate')
		
		then:
		result.wasUpToDate("yangGenerate")
	}

	def 'process yang module with no types with CodeGeneratorImpl'() {
		when:
		directory("src/main/yang")
		File yangFile = createFile("src/main/yang/trivial.yang")
		yangFile << '''
			module trivial {
				namespace "trivial";
				prefix trivial;
			}
		'''.stripIndent()
		buildFile << applyPlugin(YangPlugin)
		buildFile << """
			yang {
				yangFilesRootDir 'src/main/yang'
				generator {
					generatorClassName	= 'com.att.opnfv.yang.gradle.CodeGeneratorImpl'
					outputDir 			= 'build/gen'
				}
			}
		""".stripIndent()

		ExecutionResult result = runTasksSuccessfully('build')
		List<String>	javaFiles	= new FileNameFinder().getFileNames(projectDir.getPath(), "build/gen/**/*.java")
		
		then:
		javaFiles.size() == 2
	}

	def 'process Yang module with undefined type'() {
		when:
		directory("src/main/yang")
		File yangFile = createFile("src/main/yang/base.yang")
		yangFile << '''
			module base {
				namespace "base";
				prefix base;
				grouping "group" {
					leaf ip-base { type inet:ip-version; }
				}
			}
		'''.stripIndent()
		buildFile << applyPlugin(YangPlugin)
		buildFile << """
			yang {
				yangFilesRootDir 'src/main/yang'
				generator {
					generatorClassName	= 'com.att.opnfv.yang.generator.MockCodeGenerator'
					outputDir 			= 'build/gen'
				}
			}
		""".stripIndent()

		ExecutionResult result = runTasksWithFailure('build')
		
		then:
		!result.wasUpToDate("yangGenerate")
		result.standardError.contains("No import found with prefix inet")
	}
	
	def 'process Yang module with missing import'() {
		when:
		directory("src/main/yang")
		File yangFile = createFile("src/main/yang/base.yang")
		yangFile << '''
			module base {
				namespace "base";
				prefix base;
				import ietf-inet-types {prefix inet; revision-date "2010-09-24";}
				grouping "group" {
					leaf ip-base { type inet:ip-version; }
				}
			}
		'''.stripIndent()
		buildFile << applyPlugin(YangPlugin)
		buildFile << """
			yang {
				yangFilesRootDir 'src/main/yang'
				generator {
					generatorClassName	= 'com.att.opnfv.yang.generator.MockCodeGenerator'
					outputDir 			= 'build/gen'
				}
			}
		""".stripIndent()

		ExecutionResult result = runTasksWithFailure('build')
		
		then:
		!result.wasUpToDate("yangGenerate")
		result.standardError.contains("Imported module ietf-inet-types not found")
	}

	def 'process Yang module with simple type reference and imported file for type and mock generator'() {
		when:
		directory("src/main/yang")
		File yangFile = createFile("src/main/yang/base.yang")
		yangFile << '''
			module base {
				namespace "base";
				prefix base;
				import ietf-inet-types {prefix inet; revision-date "2010-09-24";}
				grouping "group" {
					leaf ip-base { type inet:ip-version; }
				}
			}
		'''.stripIndent()

		directory("yangImports")
		
		File	importedYangFile	= createFile("yangImports/ietf-inet-types.yang")
		importedYangFile << '''
			module ietf-inet-types {
				namespace "urn:ietf:params:xml:ns:yang:ietf-inet-types";
				prefix "inet";
				revision 2010-09-24 { }
				typedef ip-version {
			  		type enumeration {
			    		enum unknown { value "0"; }
			    		enum ipv4 { value "1"; }
			    		enum ipv6 { value "2"; }
			  		}
				}
			}
		'''
		
		buildFile << applyPlugin(YangPlugin)
		buildFile << """
			configurations {
				yangImports
			}
			dependencies {
				yangImports fileTree(dir: "yangImports", include: "*.yang")
			}
			yang {
				yangFilesConfiguration "yangImports"
				inspectDependencies		true
				yangFilesRootDir 		'src/main/yang'
				generator {
					generatorClassName	= 'com.att.opnfv.yang.generator.MockCodeGenerator'
					outputDir 			= 'build/gen'
				}
			}
		""".stripIndent()

		ExecutionResult result = runTasksSuccessfully('yangGenerate')
		
		then:
		result.standardOutput.contains("In setAdditionalConfig.")
		result.standardOutput.contains("In generateSources.")
	}
	
	def 'rerun task when imported yang files change'() {
		when:
		directory("src/main/yang")
		File yangFile = createFile("src/main/yang/base.yang")
		yangFile << '''
			module base {
				namespace "base";
				prefix base;
				import ietf-inet-types {prefix inet; revision-date "2010-09-24";}
				grouping "group" {
					leaf ip-base { type inet:ip-version; }
				}
			}
		'''.stripIndent()

		directory("yangImports")
		
		File	importedYangFile	= createFile("yangImports/ietf-inet-types.yang")
		importedYangFile << '''
			module ietf-inet-types {
				namespace "urn:ietf:params:xml:ns:yang:ietf-inet-types";
				prefix "inet";
				revision 2010-09-24 { }
				typedef ip-version {
			  		type enumeration {
			    		enum unknown { value "0"; }
			    		enum ipv4 { value "1"; }
			    		enum ipv6 { value "2"; }
			  		}
				}
			}
		'''
		
		buildFile << applyPlugin(YangPlugin)
		buildFile << """
			configurations {
				yangImports
			}
			dependencies {
				yangImports fileTree(dir: "yangImports", include: "*.yang")
			}
			yang {
				yangFilesConfiguration "yangImports"
				inspectDependencies		true
				yangFilesRootDir 		'src/main/yang'
				generator {
					generatorClassName	= 'com.att.opnfv.yang.generator.MockCodeGenerator'
					outputDir 			= 'build/gen'
				}
			}
		""".stripIndent()

		ExecutionResult result = runTasksSuccessfully('yangGenerate')
		
		then:
		result.standardOutput.contains("In setAdditionalConfig.")
		result.standardOutput.contains("In generateSources.")
		
		when:
		// Rerunning task with no changes won't rerun the task.
		result	= runTasksSuccessfully('yangGenerate')
		
		then:
		result.wasUpToDate("yangGenerate")
		
		when:
		// Changing the file will make it run again.
		importedYangFile << " "
		result	= runTasksSuccessfully('yangGenerate')
		
		then:
		!result.wasUpToDate("yangGenerate")

		when:
		// Rerunning task with no changes won't rerun the task.
		result	= runTasksSuccessfully('yangGenerate')
		
		then:
		result.wasUpToDate("yangGenerate")
	}
	
	def 'process yang module with no types with JMXGenerator, no mapping provided'() {
		when:
		directory("src/main/yang")
		File yangFile = createFile("src/main/yang/trivial.yang")
		yangFile << '''
			module trivial {
				namespace "trivial";
				prefix trivial;
			}
		'''.stripIndent()
		buildFile << applyPlugin(YangPlugin)
		buildFile << """
			yang {
				yangFilesRootDir 'src/main/yang'
				generator {
					generatorClassName	= 'com.att.opnfv.yang.gradle.JMXGenerator'
					outputDir 			= 'build/gen'
				}
			}
		""".stripIndent()

		ExecutionResult result = runTasksWithFailure('build')
		
		then:
		result.standardError.contains("No namespace to package mapping provided")
	}

	def 'process yang module with no types with JMXGenerator, simple mapping, no identity'() {
		when:
		directory("src/main/yang")
		File yangFile = createFile("src/main/yang/trivial.yang")
		yangFile << '''
			module trivial {
				namespace "trivial";
				prefix trivial;
			}
		'''.stripIndent()
		buildFile << applyPlugin(YangPlugin)
		buildFile << """
			yang {
				yangFilesRootDir 'src/main/yang'
				generator {
					generatorClassName	= 'com.att.opnfv.yang.gradle.JMXGenerator'
					outputDir 			= 'build/gen'
					additionalConfiguration	= ["namespaceToPackage":"trivial==com.example.trivial"]
				}
			}
		""".stripIndent()

		ExecutionResult result = runTasksSuccessfully('build')
		// No identities in Yang, so nothing to write.
		List<String>	javaFiles	= new FileNameFinder().getFileNames(projectDir.getPath(), "build/gen/**/*.java")
		
		then:
		javaFiles.size() == 0
	}

	def 'process yang module with no types with JMXGenerator, simple mapping, simple identity'() {
		when:
		directory("src/main/yang")
		File yangFile = createFile("src/main/yang/trivial.yang")
		yangFile << '''
			module trivial {
				namespace "trivial";
				prefix trivial;
//				import config { prefix config; revision-date 2013-04-05; }
				identity trivial-base {
         			description "base identity";
        		}
				identity abc {
					//base "config:service-type";
					base trivial-base;
				}
			}
		'''.stripIndent()
		
		directory("yangImports")
		copyResources("yang", "yangImports")
		
		buildFile << applyPlugin(YangPlugin)
		buildFile << """
			configurations {
				yangImports
			}
			dependencies {
				yangImports fileTree(dir: "yangImports", include: "*.yang")
			}
			yang {
				yangFilesRootDir 'src/main/yang'
				yangFilesConfiguration "yangImports"
				inspectDependencies		true
				generator {
					generatorClassName	= 'com.att.opnfv.yang.gradle.JMXGenerator'
					outputDir 			= 'build/gen'
					additionalConfiguration	= ["namespaceToPackage":"trivial==com.example.trivial"]
				}
			}
		""".stripIndent()

		ExecutionResult result = runTasksSuccessfully('build')
		List<String>	javaFiles	= new FileNameFinder().getFileNames(projectDir.getPath(), "build/gen/**/*.java")
		
		then:
		result
	}

	def 'process yang module with no types with JMXGenerator, simple mapping, service-type identity'() {
		when:
		writeHelloWorld("org.opendaylight.dummy")
		directory("src/main/yang")
		File yangFile = createFile("src/main/yang/trivial.yang")
		yangFile << '''
			module trivial {
				namespace "trivial";
				prefix trivial;
				import config { prefix config; revision-date 2013-04-05; }
				identity abc {
					base "config:service-type";
					config:java-class "org.opendaylight.dummy.HelloWorld";
				}
			}
		'''.stripIndent()
		
		directory("yangImports")
		copyResources("yang", "yangImports")
		
		buildFile << applyPlugin(YangPlugin)
		buildFile << """
			configurations {
				yangImports
			}
			dependencies {
				yangImports fileTree(dir: "yangImports", include: "*.yang")
			}
			yang {
				yangFilesRootDir 'src/main/yang'
				yangFilesConfiguration "yangImports"
				inspectDependencies		true
				generator {
					generatorClassName	= 'com.att.opnfv.yang.gradle.JMXGenerator'
					outputDir 			= 'build/gen'
					additionalConfiguration	= ["namespaceToPackage":"trivial==com.example.trivial"]
				}
			}
		""".stripIndent()

		ExecutionResult result = runTasksSuccessfully('build')
		
		then:
		result
	}

	def 'process yang module with no types with mock generator with empty generatorsConfiguration'() {
		when:
		directory("src/main/yang")
		File yangFile = createFile("src/main/yang/trivial.yang")
		yangFile << '''
			module trivial {
				namespace "trivial";
				prefix trivial;
			}
		'''.stripIndent()
		
		// Note that we use a hand-coded mock here. Typical mocking frameworks can't work here because
		// the test is run in a remote process.
		buildFile << applyPlugin(YangPlugin)
		buildFile << """
			configurations {
				generators
			}
			yang {
				generatorsConfiguration "generators"
				yangFilesRootDir 'src/main/yang'
				generator {
					generatorClassName	= 'com.att.opnfv.yang.generator.MockCodeGenerator'
					outputDir 			= 'build/gen'
				}
			}
		""".stripIndent()

		ExecutionResult result = runTasksSuccessfully('build')
		println result
		
		then:
		result.standardOutput.contains("In setAdditionalConfig.")
		result.standardOutput.contains("In generateSources.")
	}

}

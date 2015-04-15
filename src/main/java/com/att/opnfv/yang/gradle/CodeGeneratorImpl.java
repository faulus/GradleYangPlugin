package com.att.opnfv.yang.gradle;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.io.FileUtils;
import org.opendaylight.yangtools.binding.generator.util.BindingGeneratorUtil;
import org.opendaylight.yangtools.sal.binding.generator.api.BindingGenerator;
import org.opendaylight.yangtools.sal.binding.generator.impl.BindingGeneratorImpl;
import org.opendaylight.yangtools.sal.binding.model.api.Type;
import org.opendaylight.yangtools.sal.java.api.generator.YangModuleInfoTemplate;
import org.opendaylight.yangtools.yang.binding.BindingMapping;
import org.opendaylight.yangtools.yang.binding.YangModelBindingProvider;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang2sources.spi.BasicCodeGenerator;

public class CodeGeneratorImpl implements BasicCodeGenerator {
    private static final Logger logger = LoggerFactory.getLogger(CodeGeneratorImpl.class);
    private static final String FS = File.separator;

    private File 				projectBaseDir;
    private Map<String, String> additionalConfig;
    private File 				resourceBaseDir;

	@Override
	public Collection<File> generateSources(SchemaContext	context,
											File 			outputDir,
											Set<Module> 	yangModules) throws IOException {

        final BindingGenerator bindingGenerator = new BindingGeneratorImpl(true);
        final List<Type> types = bindingGenerator.generateTypes(context, yangModules);
        final GeneratorJavaFile generator = new GeneratorJavaFile(types);

        File persistentSourcesDir = null;
        if (additionalConfig != null) {
            String persistenSourcesPath = additionalConfig.get("persistentSourcesDir");
            if (persistenSourcesPath != null) {
                persistentSourcesDir = new File(persistenSourcesPath);
            }
        }
        if (persistentSourcesDir == null) {
            persistentSourcesDir = new File(projectBaseDir, "src" + FS + "main" + FS + "java");
        }

        List<File> result = generator.generateToFile(outputDir, persistentSourcesDir);

        result.addAll(generateModuleInfos(outputDir, yangModules, context));
        
        return result;
	}

	private Collection<? extends File> generateModuleInfos(final File outputBaseDir, final Set<Module> yangModules,
			final SchemaContext context) {
		Builder<File> result = ImmutableSet.builder();
		Builder<String> bindingProviders = ImmutableSet.builder();
		for (Module module : yangModules) {
			Builder<String> currentProvidersBuilder = ImmutableSet.builder();
			// TODO: do not mutate parameters, output of a method is defined by its return value
			Set<File> moduleInfoProviders = generateYangModuleInfo(outputBaseDir, module, context, currentProvidersBuilder);
			ImmutableSet<String> currentProviders = currentProvidersBuilder.build();
			logger.info("Adding ModuleInfo providers {}", currentProviders);
			bindingProviders.addAll(currentProviders);
			result.addAll(moduleInfoProviders);
		}

		result.add(writeMetaInfServices(resourceBaseDir, YangModelBindingProvider.class, bindingProviders.build()));
		return result.build();
	}

    private File writeMetaInfServices(final File outputBaseDir, final Class<YangModelBindingProvider> serviceClass,
            final ImmutableSet<String> services) {
        File metainfServicesFolder = new File(outputBaseDir, "META-INF" + File.separator + "services");
        metainfServicesFolder.mkdirs();
        File serviceFile = new File(metainfServicesFolder, serviceClass.getName());

        String src = Joiner.on('\n').join(services);

        return writeFile(serviceFile, src);
    }

    private Set<File> generateYangModuleInfo(final File outputBaseDir, final Module module, final SchemaContext ctx,
            final Builder<String> providerSourceSet) {
        Builder<File> generatedFiles = ImmutableSet.<File> builder();

        final YangModuleInfoTemplate template = new YangModuleInfoTemplate(module, ctx);
        String moduleInfoSource = template.generate();
        if (moduleInfoSource.isEmpty()) {
            throw new IllegalStateException("Generated code should not be empty!");
        }
        String providerSource = template.generateModelProvider();

        final File packageDir = GeneratorJavaFile.packageToDirectory(outputBaseDir,
                BindingGeneratorUtil.moduleNamespaceToPackageName(module));

        generatedFiles.add(writeJavaSource(packageDir, BindingMapping.MODULE_INFO_CLASS_NAME, moduleInfoSource));
        generatedFiles
                .add(writeJavaSource(packageDir, BindingMapping.MODEL_BINDING_PROVIDER_CLASS_NAME, providerSource));
        providerSourceSet.add(template.getModelBindingProviderName());

        return generatedFiles.build();

    }

    private File writeJavaSource(final File packageDir, final String className, final String source) {
        if (!packageDir.exists()) {
            packageDir.mkdirs();
        }
        final File file = new File(packageDir, className + ".java");
        writeFile(file, source);
        return file;
    }

    private File writeFile(final File file, final String source) {
        try (final OutputStream stream = FileUtils.openOutputStream(file)) {
            try (final Writer fw = new OutputStreamWriter(stream)) {
                try (final BufferedWriter bw = new BufferedWriter(fw)) {
                    bw.write(source);
                }
            } catch (Exception e) {
                logger.error("Could not write file: {}",file,e);
            }
        } catch (Exception e) {
            logger.error("Could not create file: {}",file,e);
        }
        return file;
    }

	@Override
	public void setAdditionalConfig(Map<String, String> additionalConfiguration) {
		this.additionalConfig	= additionalConfiguration;
	}

	@Override
	public void setResourceBaseDir(File resourceBaseDir) {
		this.resourceBaseDir	= resourceBaseDir;
	}

}

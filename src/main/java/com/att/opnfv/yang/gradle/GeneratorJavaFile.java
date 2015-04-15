package com.att.opnfv.yang.gradle;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.opendaylight.yangtools.sal.binding.model.api.CodeGenerator;
import org.opendaylight.yangtools.sal.binding.model.api.GeneratedTransferObject;
import org.opendaylight.yangtools.sal.binding.model.api.Type;
import org.opendaylight.yangtools.sal.java.api.generator.BuilderGenerator;
import org.opendaylight.yangtools.sal.java.api.generator.EnumGenerator;
import org.opendaylight.yangtools.sal.java.api.generator.InterfaceGenerator;
import org.opendaylight.yangtools.sal.java.api.generator.TOGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class GeneratorJavaFile {
	private static final Logger logger	= LoggerFactory.getLogger(GeneratorJavaFile.class);
	
    private final List<CodeGenerator> generators = new ArrayList<>();
    private final Collection<? extends Type> types;

    public GeneratorJavaFile(Collection<? extends Type> types) {
    	this.types	= Preconditions.checkNotNull(types);
    	generators.add(new InterfaceGenerator());
        generators.add(new TOGenerator());
        generators.add(new EnumGenerator());
        generators.add(new BuilderGenerator());
    }

    public List<File> generateToFile(final File generatedSourcesDirectory) throws IOException {
        return generateToFile(generatedSourcesDirectory, generatedSourcesDirectory);
    }

    public List<File> generateToFile(final File generatedSourcesDirectory,
    								 final File persistentSourcesDirectory) throws IOException {
        final List<File> result = new ArrayList<>();
        for (Type type : types) {
            if (type != null) {
                for (CodeGenerator generator : generators) {
                    File generatedJavaFile = null;
                    if (type instanceof GeneratedTransferObject
                            && ((GeneratedTransferObject) type).isUnionTypeBuilder()) {
                        File packageDir = packageToDirectory(persistentSourcesDirectory, type.getPackageName());
                        File file = new File(packageDir, generator.getUnitName(type) + ".java");
                        if (!file.exists()) {
                            generatedJavaFile = generateTypeToJavaFile(persistentSourcesDirectory, type, generator);
                        }
                    } else {
                        generatedJavaFile = generateTypeToJavaFile(generatedSourcesDirectory, type, generator);
                    }
                    if (generatedJavaFile != null) {
                        result.add(generatedJavaFile);
                    }
                }
            }
        }
        return result;
    }

    private File generateTypeToJavaFile(final File parentDir, final Type type, final CodeGenerator generator)
            throws IOException {
        if (parentDir == null) {
            logger.warn("Parent Directory not specified, files will be generated "
                    + "accordingly to generated Type package path.");
        }
        if (type == null) {
            logger.error("Cannot generate Type into Java File because " + "Generated Type is NULL!");
            throw new IllegalArgumentException("Generated Type Cannot be NULL!");
        }
        if (generator == null) {
            logger.error("Cannot generate Type into Java File because " + "Code Generator instance is NULL!");
            throw new IllegalArgumentException("Code Generator Cannot be NULL!");
        }
        final File packageDir = packageToDirectory(parentDir, type.getPackageName());

        if (!packageDir.exists()) {
            packageDir.mkdirs();
        }

        if (generator.isAcceptable(type)) {
            final String generatedCode = generator.generate(type);
            if (generatedCode.isEmpty()) {
                throw new IllegalStateException("Generated code should not be empty!");
            }
            File file = new File(packageDir, generator.getUnitName(type) + ".java");

            if (file.exists()) {
                logger.warn(
                        "Naming conflict for type '{}': file with same name already exists and will not be generated.",
                        type.getFullyQualifiedName());
                return null;
            }

            // This block using NIO was put here to replace the commented-out block following it, initially
            // to get better diagnostics for why it was failing to create these files.  It didn't produce
            // better diagnostics, because it didn't fail.  The failures seem to be related to Windows' limitation
            // of 260 characters for a path name.  Somehow, NIO bypasses that limit.
            file	= Paths.get(file.getAbsolutePath()).toAbsolutePath().toFile();
            try {
            	Path	path	= file.toPath();
            	if (!Files.exists(path.getParent())) {
            		Files.createDirectories(path);
            	}
            	if (!file.exists()) {
            		file	= Files.createFile(path).toFile();
            	}
            }
            catch (Exception ex) {
            	logger.error("Failed to create file");
            }

//            // This shouldn't be necessary, as FileUtils.openOutputStream() says that it will create the
//            // file if it doesn't exist, but this doesn't happen, as of commons-io 2.4.
//            if (!file.exists()) {
//            	logger.info("Parent file exists: " + file.getParentFile().exists());
//            	try {
//            		file.createNewFile();
//            	}
//            	catch (Exception ex) {
//            		logger.error("Creating file failed", ex);
//            	}
//            }
            
            try (final OutputStream stream = FileUtils.openOutputStream(file)) {
                try (final Writer fw = new OutputStreamWriter(stream)) {
                    try (final BufferedWriter bw = new BufferedWriter(fw)) {
                        bw.write(generatedCode);
                    }
                } catch (IOException e) {
                    logger.error("Failed to write generate output into {}", file.getPath(), e);
                    throw e;
                }
            }
            return file;

        }
        return null;
    }

    /**
     * Creates the package directory path as concatenation of
     * <code>parentDirectory</code> and parsed <code>packageName</code>. The
     * parsing of <code>packageName</code> is realized as replacement of the
     * package name dots with the file system separator.
     *
     * @param parentDirectory
     *            <code>File</code> object with reference to parent directory
     * @param packageName
     *            string with the name of the package
     * @return <code>File</code> object which refers to the new directory for
     *         package <code>packageName</code>
     */
    public static File packageToDirectory(final File parentDirectory, final String packageName) {
        if (packageName == null) {
            throw new IllegalArgumentException("Package Name cannot be NULL!");
        }

        final String[] subDirNames = packageName.split("\\.");
        final StringBuilder dirPathBuilder = new StringBuilder();
        dirPathBuilder.append(subDirNames[0]);
        for (int i = 1; i < subDirNames.length; ++i) {
            dirPathBuilder.append(File.separator);
            dirPathBuilder.append(subDirNames[i]);
        }
        return new File(parentDirectory, dirPathBuilder.toString());
    }
}

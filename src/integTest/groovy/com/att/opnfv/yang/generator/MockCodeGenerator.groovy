package com.att.opnfv.yang.generator

import java.io.File
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang2sources.spi.BasicCodeGenerator
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class MockCodeGenerator implements BasicCodeGenerator {
	private Logger logger	= LoggerFactory.getLogger(MockCodeGenerator)
	
	@Override
	public Collection<File> generateSources(SchemaContext context, File outputBaseDir, Set<Module> currentModules) throws IOException {
		logger.info "In generateSources."
	}

	@Override
	public void setAdditionalConfig(Map<String, String> additionalConfiguration) {
		logger.info  "In setAdditionalConfig."
	}

	@Override
	public void setResourceBaseDir(File resourceBaseDir) {
		logger.info  "In setResourceBaseDir."
	}

}

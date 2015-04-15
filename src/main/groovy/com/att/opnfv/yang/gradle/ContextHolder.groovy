package com.att.opnfv.yang.gradle

import java.util.Set;

import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

class ContextHolder {
	private final SchemaContext	context;
	private final Set<Module> 	yangModules;

	ContextHolder(SchemaContext context, Set<Module> yangModules) {
		this.context		= context;
		this.yangModules 	= yangModules;
	}

	SchemaContext getContext() { return context; }

	Set<Module> getYangModules() { return yangModules; }
}

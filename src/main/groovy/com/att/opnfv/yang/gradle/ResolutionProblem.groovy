package com.att.opnfv.yang.gradle

import java.io.File;
import java.util.Collection;

class ResolutionProblem {
	static main(args) {
		ResolutionProblem	problem	= new ResolutionProblem()
		
		problem.doit(args)
	}

	private void doit(String[] args) {
		Collection<File>	list = [new File("a"), new File("b"), new File("c"), new File("d")];
		Collection<String>	excludeFiles	= ["e", "f", "g"]
		if (args.length > 0) {
			File	file = new File(".")
			list.each { File lfile ->
				println lfile
				getYangFilesFromZip(lfile, excludeFiles)
			}
		}
	}

	// Remove "private" to fix it.
	private Collection<File> getYangFilesFromZip(File file, Collection<String> excludeFiles) {
		println "Got here"
	}
}

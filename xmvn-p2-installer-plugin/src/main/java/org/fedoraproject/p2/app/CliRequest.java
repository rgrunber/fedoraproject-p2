/*******************************************************************************
 * Copyright (c) 2014 Red Hat Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.fedoraproject.p2.app;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.beust.jcommander.ParameterException;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.Parameter;

/**
 * @author Mikolaj Izdebski
 */
class CliRequest {
	@Parameter
	private List<String> parameters = new LinkedList<>();

	@Parameter(names = { "-h", "--help" }, help = true, description = "Display usage information")
	private boolean help;

	@Parameter(names = { "-X", "--debug" }, description = "Display debugging information")
	private boolean debug = false;

	@Parameter(names = { "-q", "--quiet" }, description = "Silence warnings and informational messages")
	private boolean quiet = false;

	@Parameter(names = { "-s", "--strict" }, description = "Fail if any artifact cannot be installed")
	private boolean strict = false;

	@Parameter(names = { "-d", "--dry-run" }, description = "Do not install artifact into buildroot, but perform dependency resolution")
	private boolean dryRun = false;

	@Parameter(names = { "-p", "--print-deps" }, description = "Print resolved dependencies in machine-readable form")
	private boolean printDeps = false;

	@Parameter(names = { "-a", "--dropin-dir" }, description = "Directory relative to buildroot where dropins should be placed")
	private String dropinDir = "usr/share/eclipse/dropins";

	@Parameter(names = { "-n", "--name" }, required = true, description = "Name of main dropin")
	private String name;

	@Parameter(names = { "-R", "--install-root" }, description = "Root directory for installation")
	private String root;

	@DynamicParameter(names = "-M", description = "Assign installable unit to dropin")
	private Map<String, String> mappings = new TreeMap<>();

	@DynamicParameter(names = "-D", description = "Define system property")
	private Map<String, String> defines = new TreeMap<>();

	public CliRequest(String[] args) {
		try {
			JCommander jcomm = new JCommander(this, args);
			jcomm.setProgramName(P2InstallerApp.class.getName());

			if (help) {
				System.out.println(P2InstallerApp.class.getName()
						+ ": Install P2 artifacts");
				System.out.println();
				jcomm.usage();
				System.exit(0);
			}

			if (debug && quiet)
				throw new ParameterException(
						"At most one of --quiet and --debug must be given");
			if (debug || quiet)
				System.setProperty("org.slf4j.simpleLogger.defaultLogLevel",
						debug ? "trace" : "error");

			if (root != null == dryRun)
				throw new ParameterException(
						"Exactly one of --install-root and --dry-run must be given");

			for (String param : defines.keySet())
				System.setProperty(param, defines.get(param));
		} catch (ParameterException e) {
			System.err.println(e.getMessage() + ". Specify -h for usage.");
			System.exit(1);
		}
	}

	public List<String> getParameters() {
		return parameters;
	}

	public boolean isStrict() {
		return strict;
	}

	public boolean isDryRun() {
		return dryRun;
	}

	public boolean isPrintDeps() {
		return printDeps;
	}

	public String getDropinDir() {
		return dropinDir;
	}

	public String getName() {
		return name;
	}

	public String getRoot() {
		return root;
	}

	public Map<String, String> getMappings() {
		return mappings;
	}
}

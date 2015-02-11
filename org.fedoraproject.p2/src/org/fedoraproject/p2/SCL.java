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
package org.fedoraproject.p2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

public class SCL {
	private static final String PROP_ECLIPSE_ROOT = "eclipse.root";
	private static final String PROP_NOARCH_DROPINS = "eclipse.dropins.noarch";
	private static final String PROP_ARCH_DROPINS = "eclipse.dropins.archful";
	private static final String PROP_BUNDLES = "eclipse.bundles";
	private static final String PROP_SCL_NAME = "scl.namespace";
	private static final String PROP_SCL_ROOT = "scl.root";

	private final Path eclipseRoot;
	private final Path noarchDropinDir;
	private final Path archDropinDir;
	private final Set<Path> bundleLocations;
	private final String sclName;
	private final Path sclRoot;

	public SCL(Path confFile) {
		Properties prop = new Properties();
		try (InputStream stream = Files.newInputStream(confFile)) {
			prop.load(stream);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		eclipseRoot = getDirectory(prop, PROP_ECLIPSE_ROOT);
		noarchDropinDir = getDirectory(prop, PROP_NOARCH_DROPINS);
		archDropinDir = getDirectory(prop, PROP_ARCH_DROPINS);
		bundleLocations = getDirectories(prop, PROP_BUNDLES);
		sclName = getString(prop, PROP_SCL_NAME);
		sclRoot = getDirectory(prop, PROP_SCL_ROOT);
	}

	private Path getDirectory(Properties prop, String key) {
		Set<Path> paths = getDirectories(prop, key);
		if (paths.isEmpty())
			return null;
		if (paths.size() > 1)
			throw new RuntimeException(key + " property in eclipse.conf "
					+ "must contain at most one directory");
		return paths.iterator().next();
	}

	private Set<Path> getDirectories(Properties prop, String key) {
		String value = getString(prop, key);
		if (value == null)
			return Collections.emptySet();
		Set<Path> dirs = new LinkedHashSet<>();
		for (String part : value.split(",")) {
			Path dir = Paths.get(part);
			if (dir.isAbsolute() && Files.isDirectory(dir))
				dirs.add(dir);
		}
		return dirs;
	}

	private String getString(Properties prop, String key) {
		String value = prop.getProperty(key);
		if (value == null || value.isEmpty())
			return null;
		return value;
	}

	public Path getEclipseRoot() {
		return eclipseRoot;
	}

	public Path getNoarchDropinDir() {
		return noarchDropinDir;
	}

	public Path getArchDropinDir() {
		return archDropinDir;
	}

	public Set<Path> getDropinDirs() {
		Set<Path> set = new LinkedHashSet<>();
		if (archDropinDir != null)
			set.add(archDropinDir);
		if (noarchDropinDir != null)
			set.add(noarchDropinDir);
		return set;
	}

	public Set<Path> getBundleLocations() {
		return bundleLocations;
	}

	public String getSclName() {
		return sclName;
	}

	public Path getSclRoot() {
		return sclRoot;
	}

	public Path getTestBundleDir() {
	    Path dir = null;
	    if (bundleLocations.size() > 0) {
	        // First value should be the standard location
	        dir = bundleLocations.iterator().next();
	    }
	    return dir;
	}
}

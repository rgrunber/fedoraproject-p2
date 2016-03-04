/*******************************************************************************
 * Copyright (c) 2014-2016 Red Hat Inc.
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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Class containing various Fedora Eclipse filesystem utilities.
 */
public class EclipseSystemLayout {

	/**
	 * Populate the sets containing platform, internal, and external OSGi bundle directories.
	 * @param scl The software collection for which initialization be performed.
	 * @param platformDirs A set expected to contain paths for platform directories.
	 * @param internalDirs A set expected to contain paths for internal directories.
	 * @param externalDirs A set expected to contain paths for external directories.
	 * @param expandDropins If true, this will locate the individual dropin locations
	 * within the dropins folder. If false, this will simply add the entire dropins
	 * folder.
	 */
	public static void initLocations (SCL scl, Set<Path> platformDirs, Set<Path> internalDirs, Set<Path> externalDirs, boolean expandDropins) {
		// Eclipse Platform locations (platform)
		Path eclipseDir = scl.getEclipseRoot();
		if (eclipseDir != null) {
			Path pluginsDir = eclipseDir.resolve("plugins");
			Path featuresDir = eclipseDir.resolve("features");
			if (Files.isDirectory(pluginsDir)) {
				platformDirs.add(pluginsDir);
			}
			if (Files.isDirectory(featuresDir)) {
				platformDirs.add(featuresDir);
			}
		}

		for (Path dropinsDir : scl.getDropinDirs()) {
			if (expandDropins) {
				try(DirectoryStream<Path> stream = Files.newDirectoryStream(dropinsDir)) {
					for (Path dropin : stream) {
						Path realDropin = dropin.resolve("eclipse");
						if (!Files.isDirectory(realDropin)) {
							realDropin = dropin;
						}
						internalDirs.add(realDropin);
					}
				} catch (IOException e) {
					// ignore
				}
			} else {
				internalDirs.add(dropinsDir);
			}
		}

		// OSGi bundle locations (external)
		for (Path javaDir : scl.getBundleLocations()) {
			externalDirs.add(javaDir);
		}
	}

	/**
	 * Get a set of user defined paths to search for additional OSGi bundles.
	 * @return A set of String folder paths that may contain OSGi bundles.
	 */
	public static Set<Path> getUserDefinedBundleLocations() {
		Set<Path> result = new LinkedHashSet<>();
		String value = System.getProperty("fedora.p2.repos");
		if (value != null) {
			for (String str : value.split(",")) {
				Path path = Paths.get(str);
				if (Files.exists(path)) {
					result.add(path);
				}
			}
		}
		return result;
	}

	/**
	 * Get a set of roots the software collections installed on this system.
	 * 
	 * @return an ordered set of software collection roots
	 */
	public static List<Path> getSclConfFiles() {
		try {
			return getSclConfFiles(System.getenv("JAVACONFDIRS"));
		} catch (IllegalArgumentException e) {
			throw new RuntimeException(
					"Invalid JAVACONFDIRS environmental variable", e);
		}
	}

	/**
	 * Get a set of roots the software collections from given Java configuration
	 * directory list.
	 * 
	 * @param confDirs
	 *            colon-delimited list of Java configuration directories
	 * @return An ordered set of software collection roots
	 */
	public static List<Path> getSclConfFiles(String confDirs) {
		// Empty or unset JAVACONFDIRS means that no SCLs are enabled
		if (confDirs == null || confDirs.isEmpty()) {
			confDirs = "/etc/java";
		} else {
			confDirs += ":/etc/java";
		}

		List<Path> confFiles = new ArrayList<>();
		for (String confDirStr : confDirs.split(":")) {
			Path confDir = Paths.get(confDirStr);
			Path confFile = confDir.resolve("eclipse.conf");
			if (Files.isRegularFile(confFile)) {
				confFiles.add(confFile);
			}
		}

		return confFiles;
	}

	public static List<URI> getRepositories() {
		try {
			List<Path> sclConfs = getSclConfFiles();
			List<URI> uris = new ArrayList<>();
			for (Path conf : sclConfs) {
				SCL scl = new SCL(conf);
				Set<Path> allLocations = new LinkedHashSet<>();
				initLocations(scl, allLocations, allLocations, allLocations,
						false);
				for (Path loc : allLocations) {
					String fragment = scl.getSclName() != null ? "#"
							+ scl.getSclName() : "";
					URI uri = new URI("fedora:" + loc + fragment);
					uris.add(uri);
				}
			}

			for (Path loc : getUserDefinedBundleLocations()) {
				uris.add(new URI("fedora:" + loc));
			}

			return uris;
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}
}

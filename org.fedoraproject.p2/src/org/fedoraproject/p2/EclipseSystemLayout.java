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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Class containing various Fedora Eclipse filesystem utilities.
 */
public class EclipseSystemLayout {

	/**
	 * Populate the sets containing platform, internal, and external OSGi bundle directories.
	 * @param root The path relative to which OSGi bundle discovery should begin.
	 * (eg. '/', or '/opt/my/scl/')
	 * @param platformDirs A set expected to contain paths for platform directories.
	 * @param internalDirs A set expected to contain paths for internal directories.
	 * @param externalDirs A set expected to contain paths for external directories.
	 * @param expandDropins If true, this will locate the individual dropin locations
	 * within the dropins folder. If false, this will simply add the entire dropins
	 * folder.
	 */
	public static void initLocations (Path root, Set<String> platformDirs, Set<String> internalDirs, Set<String> externalDirs, boolean expandDropins) {
		Path prefix = root.resolve("usr");
		for (String lib : Arrays.asList("share", "lib", "lib64")) {
			Path libDir = prefix.resolve(lib);

			// Eclipse Platform locations (platform)
			Path eclipseDir = libDir.resolve("eclipse");
			if (Files.isDirectory(eclipseDir)) {
				Path pluginsDir = eclipseDir.resolve("plugins");
				Path featuresDir = eclipseDir.resolve("features");
				if (Files.isDirectory(pluginsDir)) {
					platformDirs.add(pluginsDir.toString());
				}
				if (Files.isDirectory(featuresDir)) {
					platformDirs.add(featuresDir.toString());
				}

				// Eclipse Dropins locations (internal)
				Path dropinsDir = eclipseDir.resolve("dropins");
				if (Files.isDirectory(dropinsDir)) {
					if (expandDropins) {
						try {
							for (Path dropin : Files.newDirectoryStream(dropinsDir)) {
								Path realDropin = dropin;
								if (!Files.isDirectory(dropin.resolve("plugins"))) {
									realDropin = dropin.resolve("eclipse");
									internalDirs.add(realDropin.toString());
								}
							}
						} catch (IOException e) {
							// ignore
						}
					} else {
						internalDirs.add(dropinsDir.toString());
					}
				}
			}

			// OSGi bundle locations (external)
			for (String javaVersion : Arrays.asList("", "1.5.0", "1.6.0", "1.7.0", "1.8.0")) {
				String versionSuffix = !javaVersion.equals("") ? "-" + javaVersion : "";
				Path javaDir = libDir.resolve("java" + versionSuffix);
				if (Files.isDirectory(javaDir)) {
					externalDirs.add(javaDir.toString());
				}
			}
		}
	}

	/**
	 * Get a set of user defined paths to search for additional OSGi bundles.
	 * @return A set of String folder paths that may contain OSGi bundles.
	 */
	public static Set<String> getUserDefinedBundleLocations() {
		Set<String> result = new HashSet<String>();
		String value = System.getProperty("fedora.p2.repos");
		if (value != null) {
			String[] paths = value.split(",");
			for (String path : paths) {
				if (Paths.get(path).toFile().exists()) {
					result.add(path);
				}
			}
		}
		return result;
	}

	/**
	 * Get a set of the software collections installed on this system.
	 * @return A set of the software collections installed on this system.
	 */
	public static Set<String> getSCLRoots() {
		String jconfdirs = System.getProperty("JAVACONFDIRS");
		Set<String> roots = new HashSet<String>();
		roots.add("/");
		if (jconfdirs != null && !jconfdirs.isEmpty()) {
			String[] jconfRoots = jconfdirs.split(":");
			for (String jconfRoot : jconfRoots) {
				roots.add(jconfRoot.replace("etc/java", ""));
			}
		}
		return roots;
	}

}

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
import java.util.LinkedHashSet;
import java.util.Properties;
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
		Set<String> result = new LinkedHashSet<>();
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
	 * Get a set of roots the software collections installed on this system.
	 * 
	 * @return An ordered set of software collection roots
	 */
	public static Set<String> getSCLRoots() {
		try {
			return getSCLRoots(System.getenv("JAVACONFDIRS"));
		} catch (IOException e) {
			throw new RuntimeException(e);
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
	 * @throws IOException
	 *             if I/O error occurs
	 */
	public static Set<String> getSCLRoots(String confDirs) throws IOException {
		// Empty or unset JAVACONFDIRS means that no SCLs are enabled
		if (confDirs == null || confDirs.isEmpty())
			confDirs = "/etc/java";

		Set<String> roots = new LinkedHashSet<>();
		for (String confDirStr : confDirs.split(":")) {
			Path confDir = getRealDir(confDirStr);
			Path confFile = confDir.resolve("java.conf");

			// Skip missing config files (javapackages-tools skips them too)
			if (Files.exists(confFile)) {
				roots.add(getRootFromConfig(confFile).toString());
			}
		}

		return roots;
	}

	/**
	 * Obtain SCL root by parsing Java configuration file.
	 * <p>
	 * This function first tries to use ROOT property if defined. If not then it
	 * falls back to using JAVA_LIBDIR and obtains value of root from its value.
	 * 
	 * @param confFile
	 *            SCL Java configuration file
	 * @return absolute path to SCL root
	 * @throws IOException
	 *             if I/O error occurs
	 */
	private static Path getRootFromConfig(Path confFile) throws IOException {
		Properties conf = new Properties();
		conf.load(Files.newInputStream(confFile));

		if (conf.containsKey("ROOT")) {
			return getRealDir(conf.getProperty("ROOT"));
		}
		if (!conf.containsKey("JAVA_LIBDIR"))
			throw new IllegalArgumentException("Configuration file " + confFile
					+ " contains neither ROOT nor JAVA_LIBDIR property");
		Path javaDir = getRealDir(conf.getProperty("JAVA_LIBDIR"));

		int n = javaDir.getNameCount();
		if (n < 3 || !javaDir.getName(n - 3).toString().equals("usr")
				|| !javaDir.getName(n - 2).toString().equals("share")
				|| !javaDir.getName(n - 1).toString().equals("java")) {
			throw new IllegalArgumentException("Java library directory "
					+ javaDir + ", which was defined in " + confFile
					+ " configuration file, does not end with /usr/share/java/"
					+ " (you should define ROOT property in this case)");
		}

		// Skip the /usr/share/java/ part
		return javaDir.getParent().getParent().getParent();
	}

	private static Path getRealDir(String dir) throws IOException {
		Path path = Paths.get(dir);
		if (!path.toRealPath().equals(path))
			throw new IllegalArgumentException(
					"path is not absolute real path: " + path);
		if (!Files.isDirectory(path))
			throw new IllegalArgumentException(
					"path does not represent a directory: " + path);
		return path;
	}

}

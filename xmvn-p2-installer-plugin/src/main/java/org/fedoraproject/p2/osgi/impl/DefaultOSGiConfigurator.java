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
package org.fedoraproject.p2.osgi.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.fedoraproject.xmvn.artifact.Artifact;
import org.fedoraproject.xmvn.artifact.DefaultArtifact;
import org.fedoraproject.xmvn.resolver.ResolutionRequest;
import org.fedoraproject.xmvn.resolver.Resolver;

import org.fedoraproject.p2.osgi.OSGiConfigurator;

/**
 * @author Mikolaj Izdebski
 */
@Named
@Singleton
public class DefaultOSGiConfigurator implements OSGiConfigurator {
	private final Logger logger = LoggerFactory
			.getLogger(DefaultOSGiConfigurator.class);

	private static final Artifact BUNDLES_EXTERNAL = new DefaultArtifact(
			"org.eclipse.tycho", "tycho-bundles-external", "zip", "SYSTEM");

	private final Resolver resolver;

	@Inject
	public DefaultOSGiConfigurator(Resolver resolver) {
		this.resolver = resolver;
	}

	@Override
	public Collection<Path> getBundles() {
		try {
			Set<Path> bundles = new LinkedHashSet<>();

			Path bundlesExternal = resolver.resolve(
					new ResolutionRequest(BUNDLES_EXTERNAL)).getArtifactPath();
			if (bundlesExternal == null)
				throw new RuntimeException("Unable to locate "
						+ BUNDLES_EXTERNAL);
			logger.debug("Using bundles from: {}", bundlesExternal);

			Path tempDir = Files.createTempDirectory("xmvn-p2-equinox-");
			tempDir.toFile().deleteOnExit();
			unzip(bundlesExternal, tempDir);
			Path installationPath = tempDir.resolve("eclipse").resolve(
					"plugins");

			for (Path file : Files.newDirectoryStream(installationPath)) {
				if (file.getFileName().toString()
						.startsWith("org.eclipse.osgi_"))
					continue;

				logger.debug("Using external OSGi bundle: {}", file);
				bundles.add(file);
			}

			return Collections.unmodifiableCollection(bundles);
		} catch (IOException e) {
			throw new RuntimeException("Unable to extract Equinox runtime", e);
		}
	}

	@Override
	public Collection<String> getExportedPackages() {
		return Arrays.asList("org.fedoraproject.p2.installer", "org.slf4j");
	}

	private void unzip(Path zip, Path dest) throws IOException {
		byte[] buffer = new byte[1024];

		try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {

			ZipEntry ze;
			while ((ze = zis.getNextEntry()) != null) {
				Path newFile = dest.resolve(Paths.get(ze.getName()));

				if (ze.isDirectory()) {
					Files.createDirectories(newFile);
				} else {
					Files.createDirectories(newFile.getParent());

					try (OutputStream fos = Files.newOutputStream(newFile)) {
						int len;
						while ((len = zis.read(buffer)) > 0)
							fos.write(buffer, 0, len);
					}
				}
			}
		}
	}
}

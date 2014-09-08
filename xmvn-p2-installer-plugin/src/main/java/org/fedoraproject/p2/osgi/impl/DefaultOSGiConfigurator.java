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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

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
			"org.eclipse.tycho", "tycho-bundles-external", "txt", "manifest",
			"SYSTEM");

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

			for (String line : Files.readAllLines(bundlesExternal,
					StandardCharsets.UTF_8)) {
				Path file = Paths.get(line).toAbsolutePath();
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
}

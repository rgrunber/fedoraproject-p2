/*******************************************************************************
 * Copyright (c) 2014, 2018 Red Hat Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.fedoraproject.p2;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Dictionary;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.equinox.internal.p2.publisher.eclipse.FeatureParser;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.publisher.eclipse.BundlesAction;
import org.eclipse.equinox.p2.publisher.eclipse.Feature;
import org.eclipse.equinox.p2.publisher.eclipse.FeaturesAction;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.BundleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An index for bundles (OSGi, Feature) under a specified location.
 */
public class FedoraBundleIndex {

	private File root;
	private Map <IArtifactKey, File> index;
	private final Logger logger = LoggerFactory.getLogger(FedoraBundleIndex.class);

	public FedoraBundleIndex (File root) {
		this.root = root;
		index = new LinkedHashMap<> ();
	}

	public Collection<File> getAllBundles (String classifier) {
		if (! index.isEmpty()) {
			return filterBundles(classifier);
		}
		gatherAllBundles(root);
		return filterBundles(classifier);
	}

	private Collection<File> filterBundles (String classifier) {
		Set<File> res = new LinkedHashSet<> ();
		for (Entry<IArtifactKey, File> e : index.entrySet()) {
			if (e.getKey().getClassifier().equals(classifier)) {
				res.add(e.getValue());
			}
		}
		return res;
	}

	public Collection<IArtifactKey> getAllArtifactKeys () {
		if (index.isEmpty()) {
			gatherAllBundles(root);
		}
		return index.keySet();
	}

	public File getFileForKey (IArtifactKey key) {
		if (index.isEmpty()) {
			gatherAllBundles(root);
		}
		return index.get(key);
	}

	public IArtifactKey getKeyForFile (File file) {
		if (index.isEmpty()) {
			gatherAllBundles(root);
		}
		for (Entry<IArtifactKey, File> e : index.entrySet()) {
			if (e.getValue().equals(file)) {
				return e.getKey();
			}
		}
		return null;
	}

	public boolean containsKey (IArtifactKey key) {
		if (index.isEmpty()) {
			gatherAllBundles(root);
		}
		return index.containsKey(key);
	}

	private void gatherAllBundles (File dir) {
		FeatureParser parser = new FeatureParser();
		for (File file : dir.listFiles()) {
			String id = null;
			String version = null;
			if (file.isDirectory() && file.canRead()) {
				gatherAllBundles(file);
			} else if (file.getName().endsWith(".jar")) {
					try {
						Dictionary<String, String> manifest = BundlesAction.loadManifest(file);
						if (manifest != null) {
							String bsn = manifest.get("Bundle-SymbolicName");
							if (bsn != null) {
								id = ManifestElement.parseHeader("Bundle-SymbolicName", bsn)
										[0].getValue();
								version = manifest.get("Bundle-Version");
								putInIndex(BundlesAction.createBundleArtifactKey(id, version), file);
							}
						}
					} catch (IOException | BundleException | IllegalArgumentException e) {
						// Skip bundle if invalid or improper arguments for artifact creation
					}
			} else if (file.getName().equals("feature.xml")) {
				Feature feature = parser.parse(file.getParentFile());
				id = feature.getId();
				version = feature.getVersion();
				putInIndex(FeaturesAction.createFeatureArtifactKey(id, version), file.getParentFile());
			} else if (file.getName().equals("MANIFEST.MF")
					&& file.getParentFile().getName().equals("META-INF")) {
				try {
					File bundleDir = file.getParentFile().getParentFile();
					Dictionary<String, String> manifest = BundlesAction.loadManifest(bundleDir);
					if (manifest != null && "dir".equals(manifest.get("Eclipse-BundleShape"))) {
						String bsn = manifest.get("Bundle-SymbolicName");
						if (bsn != null) {
							id = ManifestElement.parseHeader("Bundle-SymbolicName", bsn)
									[0].getValue();
							version = manifest.get("Bundle-Version");
							putInIndex(BundlesAction.createBundleArtifactKey(id, version), bundleDir);
						}
					}
				} catch (IOException | BundleException | IllegalArgumentException e) {
					// Skip bundle if invalid or improper arguments for artifact creation
				}
			}
		}
	}

	private void putInIndex (IArtifactKey key, File file) {
		boolean isSameFile = false;
		File prev = index.put(key, file);
		if (prev != null) {
			try {
				isSameFile = file.getCanonicalFile().equals(prev.getCanonicalFile());
			} catch (IOException e) {
			}
			if (!isSameFile) {
				logger.warn("Multiple artifacts detected for {}", key.toString());
				logger.warn("{} and {} have the same ID and version.", prev.getAbsolutePath(), file.getAbsolutePath());
				logger.warn("{} will be preferred.", file.getAbsolutePath());
			}
		}
		logger.debug("Artifact: {} File: {}", key.toString(), file.getAbsolutePath());
	}
}

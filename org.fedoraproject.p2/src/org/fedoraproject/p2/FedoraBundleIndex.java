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

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.equinox.internal.p2.publisher.eclipse.FeatureParser;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.publisher.eclipse.BundlesAction;
import org.eclipse.equinox.p2.publisher.eclipse.Feature;
import org.eclipse.equinox.p2.publisher.eclipse.FeaturesAction;
import org.osgi.framework.BundleException;

public class FedoraBundleIndex {

	private File root;
	private Map <IArtifactKey, File> index;

	public FedoraBundleIndex (File root) {
		this.root = root;
		index = new HashMap<IArtifactKey, File> ();
	}

	public Collection<File> getAllBundles (String classifier) {
		if (! index.isEmpty()) {
			return filterBundles(classifier);
		}
		gatherAllBundles(root);
		return filterBundles(classifier);
	}

	private Collection<File> filterBundles (String classifier) {
		Set<File> res = new HashSet<File> ();
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
							id = manifest.get("Bundle-SymbolicName");
							version = manifest.get("Bundle-Version");
							index.put(BundlesAction.createBundleArtifactKey(id, version), file);
						}
					} catch (IOException | BundleException e) {
					}
			} else if (file.getName().equals("feature.xml")) {
				Feature feature = parser.parse(file.getParentFile());
				id = feature.getId();
				version = feature.getVersion();
				index.put(FeaturesAction.createFeatureArtifactKey(id, version), file.getParentFile());
			}
		}
	}

}

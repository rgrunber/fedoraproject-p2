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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IVersionedId;
import org.eclipse.equinox.p2.publisher.eclipse.BundlesAction;
import org.eclipse.equinox.p2.publisher.eclipse.FeaturesAction;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * This acts as a front-end for all interactions/queries regarding the
 * locations and metadata associated with system bundles (OSGi, Feature).
 */
public class FedoraBundleRepository {

	private Set<IInstallableUnit> platformUnits;
	private Set<IInstallableUnit> internalUnits;
	private Set<IInstallableUnit> externalUnits;
	private Map<String, IMetadataRepository> metaRepos;
	private Map<String, FedoraBundleIndex> fbindices;

	public FedoraBundleRepository(File root) {
		metaRepos = new HashMap<String, IMetadataRepository>();
		fbindices = new HashMap<String, FedoraBundleIndex>();

		Set<String> platformLocations = new LinkedHashSet<>();
		Set<String> dropinsLocations = new LinkedHashSet<>();
		Set<String> externalLocations = new LinkedHashSet<>();
		EclipseSystemLayout.initLocations(root.toPath(), platformLocations, dropinsLocations, externalLocations, true);

		List<String> allLocations = new ArrayList<String> ();
		allLocations.addAll(platformLocations);
		allLocations.addAll(dropinsLocations);
		allLocations.addAll(externalLocations);

		BundleContext bc = Activator.getContext();
		ServiceReference<?> sr = (ServiceReference<?>) bc.getServiceReference(IProvisioningAgentProvider.SERVICE_NAME);
		IProvisioningAgentProvider pr = (IProvisioningAgentProvider) bc.getService(sr);
		try {
			IProvisioningAgent agent = pr.createAgent(null);
			IMetadataRepositoryManager metadataRM = (IMetadataRepositoryManager) agent.getService(IMetadataRepositoryManager.SERVICE_NAME);
			for (String loc : allLocations) {
				try {
					Path repoPath = Paths.get(root.getAbsolutePath(), loc);
					if (Files.exists(repoPath)) {
						IMetadataRepository metaRepo = metadataRM.loadRepository(new URI("fedora:" + repoPath), new NullProgressMonitor());
						FedoraBundleIndex index = new FedoraBundleIndex(new File(root.getAbsolutePath() + loc));
						metaRepos.put(loc, metaRepo);
						fbindices.put(loc, index);
					}
				} catch (ProvisionException e) {
					// ignore and continue if there are repository issues
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		platformUnits = enumerateUnits(platformLocations);

		internalUnits = enumerateUnits(dropinsLocations);
		internalUnits.removeAll(platformUnits);

		externalUnits = enumerateUnits(externalLocations);
		externalUnits.removeAll(platformUnits);

		Set<IInstallableUnit> commonUnits = new LinkedHashSet<>(internalUnits);
		commonUnits.retainAll(externalUnits);

		internalUnits.removeAll(commonUnits);
		externalUnits.removeAll(commonUnits);

		for (IInstallableUnit unit : commonUnits) {
			try {
				Path path = lookupBundle(unit);
				if (path == null)
					continue;
				path = path.toRealPath();
				for (String dropin : dropinsLocations) {
					if (path.startsWith(Paths.get(dropin)))
						internalUnits.add(unit);
					else
						externalUnits.add(unit);
				}
			} catch (IOException e) {
			}
		}
	}
	
	/**
	 * @return A set of installable units reachable from given locations.
	 */
	private Set<IInstallableUnit> enumerateUnits(Set<String> locations){
		Set<IInstallableUnit> candidates = new LinkedHashSet<IInstallableUnit>();
		for (String loc : locations) {
			IMetadataRepository repo = metaRepos.get(loc);
			if (repo != null) {
				candidates.addAll(repo.query(QueryUtil.ALL_UNITS, new NullProgressMonitor()).toUnmodifiableSet());
			}
		}
		return candidates;
	}
	
	/**
	 * @return A set of installable units part of the Eclipse platform installation.
	 */
	public Set<IInstallableUnit> getPlatformUnits() {
		return Collections.unmodifiableSet(platformUnits);
	}

	/**
	 * @return A set of installable units that are discovered by the Eclipse platform at runtime.
	 * This refers to the 'dropins' mechanism of bundle discovery. Any platform units that are
	 * also present as internal units are ignored.
	 */
	public Set<IInstallableUnit> getInternalUnits() {
		return Collections.unmodifiableSet(internalUnits);
	}

	/**
	 * @return a set of installable units that are OSGi bundles, but not in a location for
	 * discovery, or inclusion as part of Eclipse. Any platform or internal units that are
	 * also present as external units are ignored.
	 */
	public Set<IInstallableUnit> getExternalUnits() {
		return Collections.unmodifiableSet(externalUnits);
	}

	/**
	 * Retrieve the system path corresponding to the given ID and Version.
	 * This assumes the artifact is an OSGi bundle.
	 * @param key an ID and Version represented as an {@link IVersionedId}.
	 * @return The system path on which to find the specified {@link IVersionedId}
	 * or <code>null</code> if no such bundle could be found.
	 */
	public Path lookupBundle (IVersionedId key) {
		for (FedoraBundleIndex index : fbindices.values()) {
			IArtifactKey artKey;
			if (key.getId().endsWith(".feature.jar") || key.getId().endsWith(".feature.group")) {
				// classifier = 'org.eclipse.update.feature'
				String adjustedID = key.getId().replaceAll("\\.feature\\.(jar|group)", "");
				artKey = FeaturesAction.createFeatureArtifactKey(adjustedID, key.getVersion().toString());
			} else {
				// classifier = 'osgi.bundle'
				artKey = BundlesAction.createBundleArtifactKey(key.getId(), key.getVersion().toString());
			}
			if (index.containsKey(artKey)) {
				return index.getFileForKey(artKey).toPath();
			}
		}
		// Either the unit doesn't exist, or it's a meta-unit (p2.inf)
		return null;
	}

}

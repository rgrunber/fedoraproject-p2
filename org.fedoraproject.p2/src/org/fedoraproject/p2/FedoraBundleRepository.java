/*******************************************************************************
 * Copyright (c) 2014-2015 Red Hat Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.fedoraproject.p2;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * This acts as a front-end for all interactions/queries regarding the
 * locations and metadata associated with system bundles (OSGi, Feature).
 */
public class FedoraBundleRepository extends AbstractBundleRepository {

	private Map<Path, IMetadataRepository> metaRepos;
	private Set<Path> dropinsLocations = new LinkedHashSet<>();

	public FedoraBundleRepository(SCL scl) {
		metaRepos = new LinkedHashMap<>();

		Set<Path> platformLocations = new LinkedHashSet<>();
		Set<Path> externalLocations = new LinkedHashSet<>();
		EclipseSystemLayout.initLocations(scl, platformLocations, dropinsLocations, externalLocations, true);

		Set<Path> allLocations = new LinkedHashSet<>();
		allLocations.addAll(platformLocations);
		allLocations.addAll(dropinsLocations);
		allLocations.addAll(externalLocations);

		BundleContext bc = Activator.getContext();
		ServiceReference<?> sr = (ServiceReference<?>) bc.getServiceReference(IProvisioningAgentProvider.SERVICE_NAME);
		IProvisioningAgentProvider pr = (IProvisioningAgentProvider) bc.getService(sr);
		try {
			IProvisioningAgent agent = pr.createAgent(null);
			IMetadataRepositoryManager metadataRM = (IMetadataRepositoryManager) agent.getService(IMetadataRepositoryManager.SERVICE_NAME);
			for (Path repoPath : allLocations) {
				try {
					String fragment = scl.getSclName() != null ? "#" + scl.getSclName() : "";
					URI uri = new URI("fedora:" + repoPath + fragment);
					IMetadataRepository metaRepo = metadataRM.loadRepository(uri, new NullProgressMonitor());
					metaRepos.put(repoPath, metaRepo);
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
	}

	/**
	 * @return A set of installable units reachable from given locations.
	 */
	private Set<IInstallableUnit> enumerateUnits(Set<Path> locations){
		Set<IInstallableUnit> candidates = new LinkedHashSet<>();
		for (Path loc : locations) {
			IMetadataRepository repo = metaRepos.get(loc);
			if (repo != null) {
				candidates.addAll(repo.query(QueryUtil.ALL_UNITS, new NullProgressMonitor()).toUnmodifiableSet());
			}
		}
		return candidates;
	}
	
	@Override
	public Set<Path> getDropinsLocations() {
		return Collections.unmodifiableSet(dropinsLocations);
	}
}

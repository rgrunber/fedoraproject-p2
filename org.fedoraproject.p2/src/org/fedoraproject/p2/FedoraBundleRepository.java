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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import org.eclipse.equinox.p2.metadata.expression.ExpressionUtil;
import org.eclipse.equinox.p2.metadata.expression.IExpression;
import org.eclipse.equinox.p2.publisher.eclipse.BundlesAction;
import org.eclipse.equinox.p2.publisher.eclipse.FeaturesAction;
import org.eclipse.equinox.p2.query.IQuery;
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

	private static final IExpression nomatchIU_IDAndVersion = ExpressionUtil.parse("id != $0 && version != $1");
	private Set<String> platformLocations = new HashSet<String> ();
	private Set<String> dropinsLocations = new HashSet<String> ();
	private Set<String> externalLocations = new HashSet<String> ();
	private File root;
	private Map<String, IMetadataRepository> metaRepos;
	private Map<String, FedoraBundleIndex> fbindices;

	public FedoraBundleRepository(File root) {
		this.root = root;
		metaRepos = new HashMap<String, IMetadataRepository>();
		fbindices = new HashMap<String, FedoraBundleIndex>();

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
	}

	/**
	 * @return A set of installable units part of the Eclipse platform installation.
	 * Any external units that are also part of the platform installation are ignored.
	 */
	public Set<IInstallableUnit> getPlatformUnits() {
		IQuery<IInstallableUnit> noExternalUnits = createUnitExclusionQuery(getExternalUnits());

		Set<IInstallableUnit> candidates = new HashSet<IInstallableUnit>();
		for (String loc : platformLocations) {
			IMetadataRepository repo = metaRepos.get(loc);
			if (repo != null) {
				candidates.addAll(repo.query(noExternalUnits, new NullProgressMonitor()).toUnmodifiableSet());
			}
		}
		return candidates;
	}

	/**
	 * @return A set of installable units that are discovered by the Eclipse platform at runtime.
	 * This refers to the 'dropins' mechanism of bundle discovery. Any external units that are
	 * also present as internal units are ignored.
	 */
	public Set<IInstallableUnit> getInternalUnits() {
		IQuery<IInstallableUnit> noExternalUnits = createUnitExclusionQuery(getExternalUnits());

		Set<IInstallableUnit> candidates = new HashSet<IInstallableUnit>();
		for (String loc : dropinsLocations) {
			IMetadataRepository repo = metaRepos.get(loc);
			if (repo != null) {
				candidates.addAll(repo.query(noExternalUnits, new NullProgressMonitor()).toUnmodifiableSet());
			}
		}
		return candidates;
	}

	/**
	 * @return a set of installable units that are OSGi bundles, but not in a location for
	 * discovery, or inclusion as part of Eclipse.
	 */
	public Set<IInstallableUnit> getExternalUnits() {
		Set<IInstallableUnit> candidates = new HashSet<IInstallableUnit>();
		for (String loc : externalLocations) {
			IMetadataRepository repo = metaRepos.get(loc);
			if (repo != null) {
				candidates.addAll(repo.query(QueryUtil.ALL_UNITS, new NullProgressMonitor()).toUnmodifiableSet());
			}
		}
		return candidates;
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

	/**
	 * Create a query excluding all specified installabe units.
	 * @param units a set of installable units from which to create a negation query.
	 * @return a query that will exclude all specified installable units.
	 */
	private IQuery<IInstallableUnit> createUnitExclusionQuery (Set<IInstallableUnit> units) {
		IQuery<IInstallableUnit> noUnits = QueryUtil.createIUAnyQuery();
		for (IInstallableUnit u : units) {
			noUnits = QueryUtil.createCompoundQuery(noUnits,
					QueryUtil.createMatchQuery(nomatchIU_IDAndVersion, u.getId(), u.getVersion()),
					true);
		}
		return noUnits;
	}

}

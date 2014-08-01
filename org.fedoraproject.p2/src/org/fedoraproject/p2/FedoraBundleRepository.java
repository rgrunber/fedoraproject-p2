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
import java.util.Arrays;
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
	private static final String [] javaVersions = new String [] { "", "1.5.0", "1.6.0", "1.7.0", "1.8.0" };
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

		initLocations(platformLocations, dropinsLocations, externalLocations);

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

	private void initLocations (Set<String> platformDirs, Set<String> internalDirs, Set<String> externalDirs) {
		Path prefix = root.toPath().resolve("usr");
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
				}
			}

			// OSGi bundle locations (external)
			for (String javaVersion : Arrays.asList(javaVersions)) {
				String versionSuffix = !javaVersion.equals("") ? "-" + javaVersion : "";
				Path javaDir = libDir.resolve("java" + versionSuffix);
				if (Files.isDirectory(javaDir)) {
					externalDirs.add(javaDir.toString());
				}
			}
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
			if (key.getId().endsWith(".feature.jar")) {
				// classifier = 'org.eclipse.update.feature'
				String adjustedID = key.getId().replace(".feature.jar", "");
				artKey = FeaturesAction.createFeatureArtifactKey(adjustedID, key.getVersion().toString());
			} else {
				// classifier = 'osgi.bundle'
				artKey = BundlesAction.createBundleArtifactKey(key.getId(), key.getVersion().toString());
			}
			if (index.containsKey(artKey)) {
				return index.getFileForKey(artKey).toPath();
			}
		}
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

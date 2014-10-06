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
package org.fedoraproject.p2.installer.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IInstallableUnitFragment;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.equinox.p2.query.IQuery;
import org.eclipse.equinox.p2.query.IQueryable;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.fedoraproject.p2.FedoraBundleRepository;
import org.fedoraproject.p2.installer.Dropin;
import org.fedoraproject.p2.installer.EclipseInstallationRequest;
import org.fedoraproject.p2.installer.EclipseInstallationResult;
import org.fedoraproject.p2.installer.EclipseInstaller;
import org.fedoraproject.p2.installer.Provide;

/**
 * @author Mikolaj Izdebski
 */
public class DefaultEclipseInstaller implements EclipseInstaller {
	private final Logger logger = LoggerFactory
			.getLogger(DefaultEclipseInstaller.class);

	private Set<IInstallableUnit> reactor;

	private Set<IInstallableUnit> platform;

	private Set<IInstallableUnit> internal;

	private Set<IInstallableUnit> external;

	private Map<IInstallableUnit, Set<IInstallableUnit>> reactorRequires;

	private Set<Package> metapackages;

	@Override
	public EclipseInstallationResult performInstallation(
			EclipseInstallationRequest request) throws Exception {
		logger.info("Indexing system bundles and features...");
		// TODO: allow configuring roots for SCL support
		List<Path> prefixes = request.getPrefixes();
		if (prefixes.isEmpty())
			prefixes = Collections.singletonList(Paths.get("/"));
		if (prefixes.size() > 1)
			throw new UnsupportedOperationException(
					"Multiple prefixes (SCLs) are not yet supported");
		FedoraBundleRepository index = new FedoraBundleRepository(prefixes
				.iterator().next().toFile());
		platform = index.getPlatformUnits();
		internal = index.getInternalUnits();
		external = index.getExternalUnits();

		logger.info("Populating all artifacts...");
		Repository reactorRepo = Repository.createTemp();
		Director.publish(reactorRepo, request.getPlugins(),
				request.getFeatures());
		reactor = reactorRepo.getAllUnits();

		dump("Platform units", platform);
		dump("Internal units", internal);
		dump("External units", external);
		dump("Reactor contents", reactor);

		Map<String, Set<IInstallableUnit>> packages = new LinkedHashMap<>();

		for (Entry<String, String> entry : request.getPackageMappings()
				.entrySet()) {
			String unitId = entry.getKey();
			String packageId = entry.getValue();

			Set<IInstallableUnit> pkg = packages.get(packageId);
			if (pkg == null) {
				pkg = new LinkedHashSet<>();
				packages.put(packageId, pkg);
			}

			IInstallableUnit unit = reactorRepo.findUnit(unitId, null);
			if (unit == null)
				throw new RuntimeException(
						"Unresolvable unit present in package mappings: "
								+ unitId);

			pkg.add(unit);
		}

		createMetapackages(packages);
		resolveDeps();
		Package.detectStrongComponents(metapackages);
		Package.expandVirtualPackages(metapackages, request.getMainPackageId());

		Set<Dropin> dropins = new LinkedHashSet<>();

		for (Package metapkg : metapackages) {
			for (Entry<String, Set<IInstallableUnit>> entry : metapkg
					.getPackageMap().entrySet()) {
				String name = entry.getKey();
				logger.info("Creating dropin {}...", name);
				Dropin dropin = new Dropin(name, request
						.getTargetDropinDirectory().resolve(name));
				dropins.add(dropin);

				Set<IInstallableUnit> content = entry.getValue();
				dump("Metapackage contents", content);
				Set<IInstallableUnit> symlinks = new LinkedHashSet<>();
				symlinks.addAll(content);
				content.retainAll(reactor);
				symlinks.removeAll(content);
				dump("Dropin physical units", content);
				dump("Dropin symlinks", symlinks);

				logger.debug("Creating runnable repository...");
				Repository packageRepo = Repository.createTemp();
				Director.mirror(packageRepo, reactorRepo, content);
				Path installationPath = dropin.getPath().resolve("eclipse");
				Repository runnableRepo = Repository.create(request
						.getBuildRoot().resolve(installationPath));
				Director.repo2runnable(runnableRepo, packageRepo);
				Files.delete(request.getBuildRoot().resolve(installationPath)
						.resolve("artifacts.jar"));
				Files.delete(request.getBuildRoot().resolve(installationPath)
						.resolve("content.jar"));

				Path pluginsDir = runnableRepo.getLocation().resolve("plugins");
				for (IInstallableUnit iu : symlinks) {
					Files.createDirectories(pluginsDir);
					Path path = index.lookupBundle(iu);
					if (path == null) {
						logger.error(
								"Unable to locate dependency in index: {}", iu);
					} else {
						String baseName = iu.getId() + "_" + iu.getVersion();
						String suffix = Files.isDirectory(path) ? "" : ".jar";
						Files.createSymbolicLink(
								pluginsDir.resolve(baseName + suffix), path);
						logger.debug("Linked external dependency {} => {}",
								baseName + suffix, path);
					}
				}

				for (IInstallableUnit unit : content) {
					for (IArtifactKey artifact : unit.getArtifacts()) {
						String baseName = artifact.getId() + "_"
								+ artifact.getVersion();

						Path path = null;
						for (String artifactName : Arrays.asList(baseName,
								baseName + ".jar")) {
							for (String type : Arrays.asList("plugins",
									"features")) {
								if (Files.exists(runnableRepo.getLocation()
										.resolve(type).resolve(artifactName)))
									path = installationPath.resolve(type)
											.resolve(artifactName);
							}
						}

						if (path == null)
							throw new Exception(
									"Unable to determine path for artifact "
											+ artifact);

						Provide provide = new Provide(artifact.getId(),
								artifact.getVersion().toString(), Paths
										.get("/").resolve(path));
						dropin.addProvide(provide);

						Set<IInstallableUnit> requires = reactorRequires
								.get(unit);
						requires.removeAll(content);
						Iterator<IInstallableUnit> it = requires.iterator();
						if (it.hasNext()) {
							StringBuilder sb = new StringBuilder(it.next()
									.getId());
							while (it.hasNext())
								sb.append(',').append(it.next().getId());
							provide.setProperty("osgi.requires", sb.toString());
						}
					}
				}
			}
		}

		return new EclipseInstallationResult(dropins);
	}

	private void createMetapackages(
			Map<String, Set<IInstallableUnit>> partialPackageMap) {
		metapackages = new LinkedHashSet<>();
		Set<IInstallableUnit> unprocesseduUnits = new LinkedHashSet<>(reactor);

		for (Entry<String, Set<IInstallableUnit>> entry : partialPackageMap
				.entrySet()) {
			String name = entry.getKey();
			Set<IInstallableUnit> contents = entry.getValue();
			metapackages.add(Package.creeatePhysical(name, contents));
			unprocesseduUnits.removeAll(contents);
		}

		for (IInstallableUnit unit : unprocesseduUnits) {
			metapackages.add(Package.creeateVirtual(unit, false));
		}
	}

	public void resolveDeps() throws ProvisionException, IOException {
		reactorRequires = new LinkedHashMap<>();
		IQueryable<IInstallableUnit> queryable = createQueryable();

		Map<IInstallableUnit, Package> metapackageLookup = new LinkedHashMap<>();
		for (Package metapackage : metapackages)
			for (IInstallableUnit unit : metapackage.getContents())
				metapackageLookup.put(unit, metapackage);

		LinkedList<Package> toProcess = new LinkedList<>(metapackages);
		while (!toProcess.isEmpty()) {
			Package metapackage = toProcess.removeFirst();
			for (IInstallableUnit iu : metapackage.getContents()) {
				logger.debug("##### IU {}", iu);

				Set<IInstallableUnit> requires = new LinkedHashSet<>();
				reactorRequires.put(iu, requires);

				for (IRequirement req : getRequirements(iu)) {
					logger.debug("    Requires: {}", req);

					IQuery<IInstallableUnit> query = QueryUtil
							.createMatchQuery(req.getMatches());
					Set<IInstallableUnit> matches = queryable
							.query(query, null).toUnmodifiableSet();
					if (matches.isEmpty()) {
						if (req.getMin() == 0)
							logger.info(
									"Unable to satisfy optional dependency from {} to {}",
									iu, req);
						else
							logger.warn(
									"Unable to satisfy dependency from {} to {}",
									iu, req);
						continue;
					}

					Set<IInstallableUnit> resolved = new LinkedHashSet<>(
							matches);
					resolved.retainAll(reactor);
					if (!resolved.isEmpty()) {
						for (IInstallableUnit match : resolved) {
							logger.debug("      => {} (reactor)", match);

							if (reactor.contains(iu)) {
								Package dep = metapackageLookup.get(match);
								metapackage.addDependency(dep);
							}
						}

						requires.addAll(matches);
						continue;
					}

					resolved.addAll(matches);
					resolved.retainAll(platform);
					if (!resolved.isEmpty()) {
						if (logger.isDebugEnabled()) {
							for (IInstallableUnit match : resolved)
								logger.debug("      => {} (part of platform)",
										match);
						}

						continue;
					}

					requires.addAll(matches);

					resolved.addAll(matches);
					resolved.retainAll(internal);
					if (!resolved.isEmpty()) {
						if (logger.isDebugEnabled()) {
							for (IInstallableUnit match : resolved) {
								logger.debug("      => {} (dropins)", match);
							}
						}

						continue;
					}

					if (matches.size() > 1)
						logger.warn(
								"More than one external bundle satisfies dependency from {} to {}",
								iu, req);

					if (!external.containsAll(matches))
						throw new RuntimeException(
								"Requirement was resolved from unknown repository");

					for (IInstallableUnit match : matches) {
						logger.debug(
								"      => {} (external, will be symlinked)",
								match);

						Package dep = metapackageLookup.get(match);
						if (dep == null) {
							dep = Package.creeateVirtual(match, true);
							metapackageLookup.put(match, dep);
							toProcess.add(dep);
							metapackages.add(dep);
						}

						metapackage.addDependency(dep);
					}
				}
			}
		}
	}

	private IQueryable<IInstallableUnit> createQueryable()
			throws ProvisionException, IOException {
		Repository unionMetadataRepo = Repository.createTemp();

		Director.mirrorMetadata(unionMetadataRepo, platform);
		Director.mirrorMetadata(unionMetadataRepo, internal);
		Director.mirrorMetadata(unionMetadataRepo, external);
		Director.mirrorMetadata(unionMetadataRepo, reactor);

		return unionMetadataRepo.getMetadataRepository();
	}

	private static Collection<IRequirement> getRequirements(IInstallableUnit iu) {
		List<IRequirement> requirements = new ArrayList<IRequirement>(
				iu.getRequirements());
		requirements.addAll(iu.getMetaRequirements());

		if (iu instanceof IInstallableUnitFragment) {
			IInstallableUnitFragment fragment = (IInstallableUnitFragment) iu;
			requirements.addAll(fragment.getHost());
		}

		for (Iterator<IRequirement> iterator = requirements.iterator(); iterator
				.hasNext();) {
			IRequirement req = iterator.next();
			if (req.getMax() == 0)
				iterator.remove();
		}

		return requirements;
	}

	private void dump(String message, Set<IInstallableUnit> units) {
		logger.debug("{}:", message);
		Set<String> sorted = new TreeSet<>();
		for (IInstallableUnit unit : units)
			sorted.add(unit.toString());
		for (String unit : sorted)
			logger.debug("  * {}", unit);
		if (sorted.isEmpty())
			logger.debug("  (none)");
	}
}

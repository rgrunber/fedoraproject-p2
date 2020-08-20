/*******************************************************************************
 * Copyright (c) 2014-2016 Red Hat Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.fedoraproject.p2.installer.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IInstallableUnitFragment;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.equinox.p2.publisher.IPublisherInfo;
import org.eclipse.equinox.p2.publisher.IPublisherResult;
import org.eclipse.equinox.p2.publisher.PublisherInfo;
import org.eclipse.equinox.p2.publisher.PublisherResult;
import org.eclipse.equinox.p2.publisher.actions.JREAction;
import org.eclipse.equinox.p2.query.IQuery;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.fedoraproject.p2.CompoundBundleRepository;
import org.fedoraproject.p2.EclipseSystemLayout;
import org.fedoraproject.p2.IFedoraBundleRepository;
import org.fedoraproject.p2.P2Utils;
import org.fedoraproject.p2.SCL;
import org.fedoraproject.p2.installer.Dropin;
import org.fedoraproject.p2.installer.EclipseArtifact;
import org.fedoraproject.p2.installer.EclipseInstallationRequest;
import org.fedoraproject.p2.installer.EclipseInstallationResult;
import org.fedoraproject.p2.installer.EclipseInstaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mikolaj Izdebski
 */
public class DefaultEclipseInstaller implements EclipseInstaller {
	private final Logger logger = LoggerFactory
			.getLogger(DefaultEclipseInstaller.class);

	private Set<IInstallableUnit> reactor;

	private Map<IInstallableUnit, Set<IInstallableUnit>> reactorRequires;

	private Set<Package> metapackages;

	private Map<IInstallableUnit, Package> metapackageLookup;

	private LinkedList<Package> toProcess;

	private IFedoraBundleRepository index;

	private Set<String> ignoreOptional;

	private Set<IInstallableUnit> unitCache;

	@Override
	public EclipseInstallationResult performInstallation(
			EclipseInstallationRequest request) throws Exception {
		logger.info("Creating reactor repository...");
		Repository reactorRepo = Repository.createTemp();
		Set<Path> plugins = new LinkedHashSet<>();
		Set<Path> features = new LinkedHashSet<>();
		Map<Path, EclipseArtifact> reactorMap = new LinkedHashMap<>();
		for (EclipseArtifact artifact : request.getArtifacts()) {
			Path path = artifact.getPath();
			reactorMap.put(path, artifact);
			if (artifact.isFeature())
				features.add(path);
			else
				plugins.add(path);
		}
		Director.publish(reactorRepo, plugins, features);
		reactor = reactorRepo.getAllUnits();
		// Remove all host localization fragments
		reactor.removeAll(reactor.stream()
                .filter(u -> u.getId().endsWith("translated_host_properties"))
                .collect(Collectors.toSet()));
		Set<Path> reactorPaths = reactor.stream().map(u -> P2Utils.getPath(u)).collect(Collectors.toSet());
		request.getArtifacts().stream().filter(a -> !reactorPaths.contains(a.getPath()))
				.forEach(a -> logger.error("Not a valid {}: {}", a.isFeature() ? "feature" : "plugin", a.getPath()));
		if (reactor.stream().collect(Collectors.summingInt(u -> u.getArtifacts().size()))
				!= plugins.size() + features.size()) {
			throw new RuntimeException("Reactor contains invalid plugin or feature");
		}

		ignoreOptional = request.getOptionalDepsIgnored();

		logger.info("Indexing system bundles and features...");
		List<Path> sclConfs = request.getConfigFiles();
		if (sclConfs.isEmpty())
			sclConfs = EclipseSystemLayout.getSclConfFiles();
		List<SCL> scls = sclConfs.stream().map(c -> new SCL(c)).collect(Collectors.toList());
		index = new CompoundBundleRepository(scls);

		SCL currentScl = scls.iterator().next();
		String namespace = currentScl.getSclName();

		P2Utils.dump("Platform units", index.getPlatformUnits());
		P2Utils.dump("Internal units", index.getInternalUnits());
		P2Utils.dump("External units", index.getExternalUnits());
		P2Utils.dump("Reactor contents", reactor);

		Map<String, Set<IInstallableUnit>> packages = new LinkedHashMap<>();

		for (IInstallableUnit unit : reactor) {
			Path path = P2Utils.getPath(unit);
			EclipseArtifact provide = reactorMap.get(path);
			if (provide == null) {
				logger.debug("Skipped unit {}: provide is null", unit);
				continue;
			}
			String packageId = provide.getTargetPackage();
			if (packageId == null)
				continue;

			Set<IInstallableUnit> pkg = packages.get(packageId);
			if (pkg == null) {
				pkg = new LinkedHashSet<>();
				packages.put(packageId, pkg);
			}
			pkg.add(unit);
		}

		createMetapackages(packages);
		resolveDeps();
		Package.detectStrongComponents(metapackages);
		Package.splitSplittable(metapackages);
		Package.expandVirtualPackages(metapackages, request.getMainPackageId());

		Set<Dropin> dropins = new LinkedHashSet<>();

		for (Package metapkg : metapackages) {
			for (Entry<String, Set<IInstallableUnit>> entry : metapkg
					.getPackageMap().entrySet()) {
				String name = entry.getKey();
				Set<IInstallableUnit> content = entry.getValue();

				// Find if any IUs in this package use or contain native components
				boolean archfulDropin = false;
				for (IInstallableUnit unit : content) {
					EclipseArtifact provide = reactorMap.get(P2Utils.getPath(unit));
					if (provide != null && provide.isNative()) {
						archfulDropin = true;
					}
				}

				// Determine the dropins directory to use
				Path dropinDir;
				if (name.endsWith("-tests")) {
					dropinDir = currentScl.getTestBundleDir();
				} else {
					if (archfulDropin) {
						dropinDir = currentScl.getArchDropletDir();
					} else {
						dropinDir = currentScl.getNoarchDropletDir();
					}
				}
				if (dropinDir == null)
					throw new RuntimeException(
							"Current SCL is not capable of holding Eclipse plugins.");
				dropinDir = Paths.get("/").relativize(dropinDir);

				logger.info("Creating {} dropin {}...", archfulDropin ? "archful" : "noarch", name);
				Dropin dropin = new Dropin(name, dropinDir.resolve(name));
				dropins.add(dropin);

				P2Utils.dump("Metapackage contents", content);
				Set<IInstallableUnit> symlinks = new LinkedHashSet<>();
				symlinks.addAll(content);
				content.retainAll(reactor);
				symlinks.removeAll(content);
				P2Utils.dump("Dropin physical units", content);
				P2Utils.dump("Dropin symlinks", symlinks);

				Path installationPath = dropin.getPath();
				if (request.getBuildRoot() != null) {
					Repository dropinRepo = Repository.createTemp();
					Set<Path> dropinPaths = new LinkedHashSet<> (plugins);
					dropinPaths.addAll(
							symlinks.stream().map(u -> P2Utils.getPath(u))
									.collect(Collectors.toSet()));
					Director.publish(dropinRepo, dropinPaths, features);
					createRunnableRepository(dropinRepo, request
							.getBuildRoot().resolve(installationPath), content, symlinks);
				}

				for (IInstallableUnit unit : content) {
					for (IArtifactKey artifact : unit.getArtifacts()) {
						EclipseArtifact provide = reactorMap.get(P2Utils.getPath(unit));
						String type = provide.isFeature() ? "features" : "plugins";
						String artifactName = artifact.getId() + "_"
								+ artifact.getVersion();
						if (!P2Utils.isBundleShapeDir(unit)) {
							artifactName += ".jar";
						}
						Path path = installationPath.resolve(type).resolve(
								artifactName);
						if (provide.getInstalledPath() != null)
							throw new RuntimeException(
									"One provide has multiple artifacts: "
											+ provide.getInstalledPath()
											+ " and " + path);
						provide.setInstalledPath(Paths.get("/").resolve(path));

						provide.setId(artifact.getId());
						provide.setVersion(artifact.getVersion().toString());
						dropin.addProvide(provide);

						if (namespace != null && !namespace.isEmpty())
							provide.setProperty("osgi.namespace", namespace);

						Set<IInstallableUnit> requires = reactorRequires
								.get(unit);
						requires.removeAll(content);
						// Remove all fragments from requires generation
						requires.removeAll(requires.stream().filter(
						        r -> r.getProvidedCapabilities().stream().anyMatch(
						                p -> p.getNamespace().equals("osgi.fragment")))
                                        .collect(Collectors.toSet()));
						if (!requires.isEmpty()) {
							provide.setProperty("osgi.requires", requires
									.stream().map(u -> P2Utils.toString(u))
									.collect(Collectors.joining(",")));
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

	private void resolveDeps() {
		reactorRequires = new LinkedHashMap<>();
		unitCache = new LinkedHashSet<>();

		metapackageLookup = new LinkedHashMap<>();
		for (Package metapackage : metapackages)
			for (IInstallableUnit unit : metapackage.getContents())
				metapackageLookup.put(unit, metapackage);

		toProcess = new LinkedList<>(metapackages);
		while (!toProcess.isEmpty()) {
			Package metapackage = toProcess.removeFirst();
			for (IInstallableUnit iu : metapackage.getContents()) {
				logger.debug("##### IU {}", iu);

				Set<IInstallableUnit> requires = new LinkedHashSet<>();
				reactorRequires.put(iu, requires);

				for (IRequirement req : getRequirements(iu, ignoreOptional))
					resolveRequirement(iu, req);
			}
		}
	}

	private void resolveRequirement(IInstallableUnit iu, IRequirement req) {
		logger.debug("    Requires: {}", req);

		if (tryResolveRequirementFrom(iu, req, reactor, "reactor",
				reactor.contains(iu), true))
			return;

		if (tryResolveRequirementFrom(iu, req, index.getPlatformUnits(),
				"platform", false, false))
			return;

		if (tryResolveRequirementFrom(iu, req, index.getInternalUnits(),
				"internal", false, true))
			return;

		if (tryResolveRequirementFrom(iu, req, index.getExternalUnits(),
				"external", true, true))
			return;

		if (tryResolveRequirementFrom(iu, req, getMetaUnits(),
				"meta", false, false))
			return;

		if (req.getMin() == 0)
			logger.info("Unable to satisfy optional dependency from {} to {}",
					iu, req);
		else
			logger.warn("Unable to satisfy dependency from {} to {}", iu, req);
	}

	private boolean tryResolveRequirementFrom(IInstallableUnit iu,
			IRequirement req, Set<IInstallableUnit> repo, String desc,
			boolean generateDep, boolean generateReq) {
		IQuery<IInstallableUnit> query = QueryUtil.createMatchQuery(req
				.getMatches());
		List<IInstallableUnit> matches = Arrays.asList(query.perform(repo.iterator())
				.toUnmodifiableSet().toArray(new IInstallableUnit[0]));
		if (matches.isEmpty())
			return false;

		IInstallableUnit match = null;
		if (matches.size() > 1) {
		    logger.warn(
		            "More than one {} unit satisfies dependency from {} to {}",
		            desc, iu, req);

			for (IInstallableUnit u : matches) {
				if (unitCache.contains(u)) {
					match = u;
					break;
				}
			}
		}
		if (match == null) {
            Collections.sort(matches, new Comparator<IInstallableUnit>() {
                @Override
                public int compare(IInstallableUnit u, IInstallableUnit v) {
                    int vRet = u.getVersion().compareTo(v.getVersion());
                    if (vRet == 0) {
                        return u.getProvidedCapabilities().size() <= v.getProvidedCapabilities().size() ? -1 : 1;
                    } else {
                        return -vRet;
                    }
                }
            });
            match = matches.get(0);
        }

		unitCache.add(match);
		logger.debug("      => {} ({})", match, desc);

		if (generateDep) {
		    Package dep = metapackageLookup.get(match);
		    if (dep == null) {
		        dep = Package.creeateVirtual(match, true);
		        metapackageLookup.put(match, dep);
		        toProcess.add(dep);
		        metapackages.add(dep);
		    }
		    Package metapackage = metapackageLookup.get(iu);
		    metapackage.addDependency(dep);
		}

		if (generateReq) {
			Set<IInstallableUnit> requires = reactorRequires.get(iu);
			requires.add(match);
		}

		return true;
	}

	private static Collection<IRequirement> getRequirements(IInstallableUnit iu, Set<String> ignoreOptional) {
		List<IRequirement> requirements = new ArrayList<>(
				iu.getRequirements());
		requirements.addAll(iu.getMetaRequirements());

		if (iu instanceof IInstallableUnitFragment) {
			IInstallableUnitFragment fragment = (IInstallableUnitFragment) iu;
			requirements.addAll(fragment.getHost());
		}

		for (Iterator<IRequirement> iterator = requirements.iterator(); iterator
				.hasNext();) {
			IRequirement req = iterator.next();
			if (req.getMax() == 0 || (ignoreOptional.contains(iu.getId()) && req.getMin() == 0))
				iterator.remove();
		}

		return requirements;
	}

	private void createRunnableRepository(Repository reactorRepo,
			Path installationPath, Set<IInstallableUnit> content,
			Set<IInstallableUnit> symlinks) throws Exception {
		logger.debug("Creating runnable repository...");
		Repository packageRepo = Repository.createTemp();
		Set<IInstallableUnit> dropinContent = new LinkedHashSet<>(content);
		dropinContent.addAll(symlinks);
		Director.mirror(packageRepo, reactorRepo, dropinContent);
		Repository runnableRepo = Repository.create(installationPath);
		Director.repo2runnable(runnableRepo, packageRepo);
		Files.delete(installationPath.resolve("artifacts.jar"));
		Files.delete(installationPath.resolve("content.jar"));

		Path pluginsDir = runnableRepo.getLocation().resolve("plugins");
		for (IInstallableUnit iu : symlinks) {
			Files.createDirectories(pluginsDir);
			Path path = P2Utils.getPath(iu);
			if (path == null) {
				logger.error("Unable to locate dependency in index: {}", iu);
			} else {
				String baseName = iu.getId() + "_" + iu.getVersion();
				String suffix = Files.isDirectory(path) ? "" : ".jar";
				P2Utils.delete(pluginsDir.resolve(baseName + suffix).toFile());
				Files.createSymbolicLink(pluginsDir.resolve(baseName + suffix),
						path);
				logger.debug("Linked external dependency {} => {}", baseName
						+ suffix, path);
			}
		}
	}

	private static Set<IInstallableUnit> getMetaUnits() {
		IPublisherInfo info = new PublisherInfo();
		IPublisherResult result = new PublisherResult();
		JREAction jreAction = new JREAction((String) null);
		jreAction.perform(info, result, new NullProgressMonitor());
		IQueryResult<IInstallableUnit> units = result.query(
				QueryUtil.createIUAnyQuery(), new NullProgressMonitor());
		return units.toUnmodifiableSet();
	}
}

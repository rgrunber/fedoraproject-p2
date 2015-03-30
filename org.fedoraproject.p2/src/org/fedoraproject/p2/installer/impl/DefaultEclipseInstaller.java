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
package org.fedoraproject.p2.installer.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IInstallableUnitFragment;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.equinox.p2.query.IQuery;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.fedoraproject.p2.CompoundBundleRepository;
import org.fedoraproject.p2.EclipseSystemLayout;
import org.fedoraproject.p2.IFedoraBundleRepository;
import org.fedoraproject.p2.P2Utils;
import org.fedoraproject.p2.SCL;
import org.fedoraproject.p2.installer.Dropin;
import org.fedoraproject.p2.installer.EclipseInstallationRequest;
import org.fedoraproject.p2.installer.EclipseInstallationResult;
import org.fedoraproject.p2.installer.EclipseInstaller;
import org.fedoraproject.p2.installer.EclipseArtifact;
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

	private boolean ignoreOptional;

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
		Set<Path> reactorPaths = reactor.stream().map(u -> P2Utils.getPath(u)).collect(Collectors.toSet());
		request.getArtifacts().stream().filter(a -> !reactorPaths.contains(a.getPath()))
				.forEach(a -> logger.error("Not a valid {}: {}", a.isFeature() ? "feature" : "plugin", a.getPath()));
		if (reactor.stream().collect(Collectors.summingInt(u -> u.getArtifacts().size()))
				!= plugins.size() + features.size()) {
			throw new RuntimeException("Reactor contains invalid plugin or feature");
		}

		ignoreOptional = request.ignoreOptional();

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
		Package.expandVirtualPackages(metapackages, request.getMainPackageId());

		Set<Dropin> dropins = new LinkedHashSet<>();

		for (Package metapkg : metapackages) {
			for (Entry<String, Set<IInstallableUnit>> entry : metapkg
					.getPackageMap().entrySet()) {
				String name = entry.getKey();
				logger.info("Creating dropin {}...", name);
				// TODO decide whether install to archful or noarch dropin dir
				Path dropinDir = currentScl.getNoarchDropinDir();
				if (name.endsWith("-tests")) {
				    dropinDir = currentScl.getTestBundleDir();
				}
				if (dropinDir == null)
					throw new RuntimeException(
							"Current SCL is not capable of holding Eclipse plugins.");
				dropinDir = Paths.get("/").relativize(dropinDir);
				Dropin dropin = new Dropin(name, dropinDir.resolve(name));
				dropins.add(dropin);

				Set<IInstallableUnit> content = entry.getValue();
				P2Utils.dump("Metapackage contents", content);
				Set<IInstallableUnit> symlinks = new LinkedHashSet<>();
				symlinks.addAll(content);
				content.retainAll(reactor);
				symlinks.removeAll(content);
				P2Utils.dump("Dropin physical units", content);
				P2Utils.dump("Dropin symlinks", symlinks);

				Path installationPath = dropin.getPath().resolve("eclipse");
				if (request.getBuildRoot() != null) {
					createRunnableRepository(reactorRepo, request
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
		Set<IInstallableUnit> matches = query.perform(repo.iterator())
				.toUnmodifiableSet();
		if (matches.isEmpty())
			return false;
		if (matches.size() > 1)
			logger.warn(
					"More than one {} unit satisfies dependency from {} to {}",
					desc, iu, req);

		for (IInstallableUnit match : matches) {
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
		}

		if (generateReq) {
			Set<IInstallableUnit> requires = reactorRequires.get(iu);
			requires.addAll(matches);
		}

		return true;
	}

	private static Collection<IRequirement> getRequirements(IInstallableUnit iu, boolean ignoreOptional) {
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
			if (req.getMax() == 0 || (ignoreOptional && req.getMin() == 0))
				iterator.remove();
		}

		return requirements;
	}

	private void createRunnableRepository(Repository reactorRepo,
			Path installationPath, Set<IInstallableUnit> content,
			Set<IInstallableUnit> symlinks) throws Exception {
		logger.debug("Creating runnable repository...");
		Repository packageRepo = Repository.createTemp();
		Director.mirror(packageRepo, reactorRepo, content);
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
				Files.createSymbolicLink(pluginsDir.resolve(baseName + suffix),
						path);
				logger.debug("Linked external dependency {} => {}", baseName
						+ suffix, path);
			}
		}
	}
}

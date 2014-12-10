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
import java.util.TreeSet;

import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IInstallableUnitFragment;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.equinox.p2.metadata.ITouchpointData;
import org.eclipse.equinox.p2.metadata.ITouchpointInstruction;
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
import org.fedoraproject.p2.installer.Provide;
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
		Director.publish(reactorRepo, request.getPlugins(),
				request.getFeatures());
		reactor = reactorRepo.getAllUnits();
		ignoreOptional = request.ignoreOptional();

		logger.info("Indexing system bundles and features...");
		List<Path> sclConfs = request.getConfigFiles();
		if (sclConfs.isEmpty())
			sclConfs = EclipseSystemLayout.getSclConfFiles();
		List<SCL> scls = new ArrayList<>(sclConfs.size());
		for (Path conf : sclConfs) {
			scls.add(new SCL(conf));
		}
		index = new CompoundBundleRepository(scls);

		SCL currentScl = scls.iterator().next();
		String namespace = currentScl.getSclName();

		dump("Platform units", index.getPlatformUnits());
		dump("Internal units", index.getInternalUnits());
		dump("External units", index.getExternalUnits());
		dump("Reactor contents", reactor);

		Map<String, Set<IInstallableUnit>> packages = new LinkedHashMap<>();

		for (Entry<String, String> entry : request.getPackageMappings()
				.entrySet()) {
			String unit = entry.getKey();
			String unitId = unit.split("_")[0];
			String unitVer = unit.split("_")[1];
			String packageId = entry.getValue();

			Set<IInstallableUnit> pkg = packages.get(packageId);
			if (pkg == null) {
				pkg = new LinkedHashSet<>();
				packages.put(packageId, pkg);
			}

			IInstallableUnit installableUnit = reactorRepo.findUnit(unitId, unitVer);
			if (installableUnit == null)
				throw new RuntimeException(
						"Unresolvable unit present in package mappings: "
								+ unit);

			pkg.add(installableUnit);
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
				if (dropinDir == null)
					throw new RuntimeException(
							"Current SCL is not capable of holding Eclipse plugins.");
				dropinDir = Paths.get("/").relativize(dropinDir);
				Dropin dropin = new Dropin(name, dropinDir.resolve(name));
				dropins.add(dropin);

				Set<IInstallableUnit> content = entry.getValue();
				dump("Metapackage contents", content);
				Set<IInstallableUnit> symlinks = new LinkedHashSet<>();
				symlinks.addAll(content);
				content.retainAll(reactor);
				symlinks.removeAll(content);
				dump("Dropin physical units", content);
				dump("Dropin symlinks", symlinks);

				Path installationPath = dropin.getPath().resolve("eclipse");
				if (request.getBuildRoot() != null) {
					createRunnableRepository(reactorRepo, request
							.getBuildRoot().resolve(installationPath), content, symlinks);
				}

				for (IInstallableUnit unit : content) {
					String type = "plugins";
					if (unit.getId().endsWith(".feature.group")
							|| unit.getId().endsWith(".feature.jar"))
						type = "features";
					for (IArtifactKey artifact : unit.getArtifacts()) {
						String artifactName = artifact.getId() + "_"
								+ artifact.getVersion();
						if (!isBundleShapeDir(unit)) {
							artifactName += ".jar";
						}
						Path path = installationPath.resolve(type).resolve(
								artifactName);

						Provide provide = new Provide(artifact.getId(),
								artifact.getVersion().toString(), Paths
										.get("/").resolve(path),
								"features".equals(type));
						dropin.addProvide(provide);

						if (namespace != null && !namespace.isEmpty())
							provide.setProperty("osgi.namespace", namespace);

						Set<IInstallableUnit> requires = reactorRequires
								.get(unit);
						requires.removeAll(content);
						Iterator<IInstallableUnit> it = requires.iterator();
						if (it.hasNext()) {
							StringBuilder sb = new StringBuilder(
									P2Utils.toString(it.next()));
							while (it.hasNext())
								sb.append(',').append(
										P2Utils.toString(it.next()));
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

	private boolean isBundleShapeDir(IInstallableUnit u) {
		for (ITouchpointData d : u.getTouchpointData()) {
			ITouchpointInstruction i = d.getInstruction("zipped");
			if (i != null && "true".equals(i.getBody())) {
				return true;
			}
		}
		return false;
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

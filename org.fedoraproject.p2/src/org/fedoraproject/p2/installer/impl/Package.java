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

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mikolaj Izdebski
 */
public class Package {
	private static final Logger logger = LoggerFactory.getLogger(Package.class);

	private final Set<IInstallableUnit> virtual = new LinkedHashSet<>();

	private final Map<String, Set<IInstallableUnit>> physical = new LinkedHashMap<>();

	private final Set<Package> deps = new LinkedHashSet<>();

	private final Set<Package> revdeps = new LinkedHashSet<>();

	private int index;

	private int lowlink;

	private boolean isSplittable;

	public static Package creeatePhysical(String name,
			Set<IInstallableUnit> contents) {
		Package metapackage = new Package();

		metapackage.physical.put(name, new LinkedHashSet<>(contents));

		return metapackage;
	}

	public static Package creeateVirtual(IInstallableUnit unit,
			boolean isSplittable) {
		Package metapackage = new Package();

		metapackage.virtual.add(unit);
		metapackage.isSplittable = isSplittable;

		return metapackage;
	}

	private Package split() {
		Package v = new Package();
		v.isSplittable = true;
		v.virtual.addAll(virtual);
		v.deps.addAll(deps);

		Iterator<Package> iu = revdeps.iterator();
		Package u = iu.next();
		u.deps.remove(this);
		iu.remove();
		u.deps.add(v);
		v.revdeps.add(u);

		for (Package w : deps) {
			w.revdeps.add(v);
		}
		return v;
	}

	private void merge(Package v) {
		if (virtual.isEmpty() && v.virtual.isEmpty()) {
			physical.putAll(v.physical);
		} else if (virtual.isEmpty() && !v.virtual.isEmpty()) {
			physical.values().iterator().next().addAll(v.virtual);
		} else if (!virtual.isEmpty() && v.virtual.isEmpty()) {
			physical.putAll(v.physical);
			physical.values().iterator().next().addAll(virtual);
			virtual.clear();
		} else {
			virtual.addAll(v.virtual);
		}

		deps.addAll(v.deps);
		for (Package w : v.deps) {
			w.revdeps.remove(v);
			w.revdeps.add(this);
		}

		revdeps.addAll(v.revdeps);
		for (Package w : v.revdeps) {
			w.deps.remove(v);
			w.deps.add(this);
		}

		deps.remove(this);
		revdeps.remove(this);

		isSplittable &= v.isSplittable;
	}

	public Set<IInstallableUnit> getContents() {
		if (!virtual.isEmpty())
			return Collections.unmodifiableSet(virtual);

		Set<IInstallableUnit> contents = new LinkedHashSet<>();
		for (Set<IInstallableUnit> partialContents : physical.values())
			contents.addAll(partialContents);

		return Collections.unmodifiableSet(contents);
	}

	public Map<String, Set<IInstallableUnit>> getPackageMap() {
		return Collections.unmodifiableMap(physical);
	}

	public void addDependency(Package dep) {
		if (dep != this) {
			deps.add(dep);
			dep.revdeps.add(this);
		}
	}

	private static Set<Package> splittable(Set<Package> V) {
		return V.stream().filter(v -> v.isSplittable && v.revdeps.size() > 1).collect(Collectors.toSet());
	}

	public static void splitSplittable(Set<Package> V) {
		Set<Package> Vs = new LinkedHashSet<>(splittable(V));
		while (!Vs.isEmpty()) {
			Iterator<Package> vi = Vs.iterator();
			Package v = vi.next();
			vi.remove();
			while (v.revdeps.size() > 1) {
				V.add(v.split());
				Vs.addAll(splittable(v.deps));
			}
		}
	}

	public static void detectStrongComponents(Set<Package> V) {
		AtomicInteger index = new AtomicInteger(0);
		Stack<Package> S = new Stack<>();

		for (Package v : new LinkedHashSet<>(V)) {
			if (v.index == 0)
				strongconnect(V, v, index, S);
		}
	}

	private static void strongconnect(Set<Package> V, Package v,
			AtomicInteger index, Stack<Package> S) {
		v.index = v.lowlink = index.incrementAndGet();
		S.push(v);

		for (Package w : new LinkedHashSet<>(v.deps)) {
			if (w.index == 0) {
				strongconnect(V, w, index, S);

				v.lowlink = Math.min(v.lowlink, w.lowlink);
			} else if (S.contains(w)) {
				v.lowlink = Math.min(v.lowlink, w.index);
			}
		}

		if (v.lowlink == v.index) {
			Set<String> cycles = new LinkedHashSet<>();
			if (!v.physical.isEmpty()) {
				cycles.add(v.physical.keySet().iterator().next());
			}
			for (Package w; (w = S.pop()) != v; V.remove(w)) {
				v.merge(w);
				if (!w.physical.isEmpty()) {
					cycles.add(w.physical.keySet().iterator().next());
				}
			}
			if (cycles.size() > 1) {
				logger.warn("###################################");
				logger.warn("###################################");
				logger.warn("Cycle detected among the following packages :");
				StringBuffer cycleNames = new StringBuffer();
				for (String name : cycles) {
						cycleNames.append(",");
						cycleNames.append(name);
				}
				logger.warn(cycleNames.substring(1));
				logger.warn("Please review mappings and dependencies.");
				logger.warn("###################################");
				logger.warn("###################################");
			}
		}
	}

	public static void expandVirtualPackages(Set<Package> metapackages,
			String mainName) {
		Package main = null;
		for (Package w : metapackages) {
			if (w.physical.get(mainName) != null)
				main = w;
		}

		Set<Package> unmerged = new LinkedHashSet<>();

		main_loop: for (;;) {
			unmerged.clear();

			for (Iterator<Package> iw = metapackages.iterator(); iw.hasNext();) {
				Package w = iw.next();
				if (w.virtual.isEmpty())
					continue;

				if (w.revdeps.isEmpty()) {
					if (main != null) {
						main.merge(w);
						iw.remove();
					} else {
						w.physical
								.put(mainName, new LinkedHashSet<>(w.virtual));
						w.virtual.clear();
						main = w;
					}

					continue main_loop;
				}

				if (w.revdeps.size() == 1) {
					iw.remove();
					for (Package v : w.revdeps) {
						v.merge(w);
					}

					continue main_loop;
				}

				unmerged.add(w);
			}

			if (unmerged.isEmpty())
				return;

			for (Package metapackage : unmerged) {
				metapackage.dump();
			}

			throw new RuntimeException("There are " + unmerged.size()
					+ " unmerged virtual metapackages");
		}
	}

	private void dumpContents() {
		if (virtual.isEmpty()) {
			for (Entry<String, Set<IInstallableUnit>> entry : physical
					.entrySet()) {
				logger.info("  Physical package {}:", entry.getKey());

				for (IInstallableUnit unit : entry.getValue())
					logger.info("    * {}", unit);
			}
		} else {
			logger.info("  Virtual package:");

			for (IInstallableUnit unit : virtual) {
				logger.info("    * {}", unit);
			}
		}
	}

	public void dump() {
		dumpContents();

		logger.info("  Required by:");
		for (Package mp : revdeps)
			mp.dumpContents();

		logger.info("===================================");
	}
}

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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mikolaj Izdebski
 */
public class Package {
	private final Logger logger = LoggerFactory.getLogger(Package.class);

	private final Set<IInstallableUnit> virtual = new LinkedHashSet<>();

	private final Map<String, Set<IInstallableUnit>> physical = new LinkedHashMap<>();

	private final Set<Package> deps = new LinkedHashSet<>();

	private final Set<Package> revdeps = new LinkedHashSet<>();

	private int index;

	private int lowlink;

	public static Package creeatePhysical(String name,
			Set<IInstallableUnit> contents) {
		Package metapackage = new Package();

		metapackage.physical.put(name, new LinkedHashSet<>(contents));

		return metapackage;
	}

	public static Package creeateVirtual(IInstallableUnit unit) {
		Package metapackage = new Package();

		metapackage.virtual.add(unit);

		return metapackage;
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
		deps.add(dep);
		dep.revdeps.add(this);
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

		for (Package w : v.deps) {
			if (v.index == 0) {
				strongconnect(V, w, index, S);

				v.lowlink = Math.min(v.lowlink, w.lowlink);
			} else if (S.contains(w)) {
				v.lowlink = Math.min(v.lowlink, w.lowlink);
			}
		}

		if (v.lowlink == v.index) {
			for (Package w; (w = S.pop()) != v; V.remove(w)) {
				v.merge(w);
			}
		}
	}

	public static void expandVirtualPackages(Set<Package> metapackages) {
		Package main = null;
		for (Package w : metapackages) {
			if (w.physical.get("") != null)
				main = w;
		}

		Set<Package> unmerged = new LinkedHashSet<>();

		main_loop: for (;;) {
			unmerged.clear();

			for (Package w : metapackages) {
				if (w.virtual.isEmpty())
					continue;

				if (w.revdeps.isEmpty()) {
					if (main != null) {
						main.merge(w);
						metapackages.remove(w);
					} else {
						w.physical.put("", new LinkedHashSet<>(w.virtual));
						w.virtual.clear();
						main = w;
					}

					continue main_loop;
				}

				if (w.revdeps.size() == 1) {
					metapackages.remove(w);
					w.revdeps.iterator().next().merge(w);

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

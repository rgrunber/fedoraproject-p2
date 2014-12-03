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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.internal.p2.touchpoint.eclipse.PublisherUtil;
import org.eclipse.equinox.p2.core.IPool;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.publisher.IPublisherInfo;
import org.eclipse.equinox.p2.publisher.IPublisherResult;
import org.eclipse.equinox.p2.publisher.PublisherInfo;
import org.eclipse.equinox.p2.publisher.PublisherResult;
import org.eclipse.equinox.p2.publisher.eclipse.FeaturesAction;
import org.eclipse.equinox.p2.query.IQuery;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.IRepositoryReference;
import org.eclipse.equinox.p2.repository.IRunnableWithProgress;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;

public class FedoraMetadataRepository implements IMetadataRepository {

	private IProvisioningAgent agent;
	private URI location;
	private Set<IInstallableUnit> unitCache;

	public FedoraMetadataRepository(IProvisioningAgent agent, URI location) {
		this.agent = agent;
		this.location = location;
		this.unitCache = new LinkedHashSet<> ();
	}

	@Override
	public URI getLocation() {
		return location;
	}

	@Override
	public String getName() {
		return "Fedora Metadata Repository " + location;
	}

	@Override
	public String getType() {
		return this.getClass().getName();
	}

	@Override
	public String getVersion() {
		return "0.0.1";
	}

	@Override
	public String getDescription() {
		return "Fedora p2 Metadata Repository";
	}

	@Override
	public String getProvider() {
		return "Fedora";
	}

	@Override
	public Map<String, String> getProperties() {
		return new LinkedHashMap<> ();
	}

	@Override
	public String getProperty(String key) {
		return null;
	}

	@Override
	public IProvisioningAgent getProvisioningAgent() {
		return agent;
	}

	@Override
	//TODO: We could support this but let's be immutable for now.
	/**
	 * Our metadata is determined by our artifacts so there is
	 * no reason to modify it independently, unless we want
	 * to hide/expose certain things. We'd have to create some
	 * file to save that state though.
	 */
	public boolean isModifiable() {
		return false;
	}

	@Override
	public String setProperty(String key, String value) {
		return null;
	}

	@Override
	public String setProperty(String key, String value, IProgressMonitor monitor) {
		return null;
	}

	@Override
	public Object getAdapter(Class adapter) {
		return null;
	}

	@Override
	public IQueryResult<IInstallableUnit> query(IQuery<IInstallableUnit> query,
			IProgressMonitor monitor) {
		return query.perform(getAllSystemIUs().iterator());
	}

	private Set<IInstallableUnit> getAllSystemIUs() {
	    if (unitCache.isEmpty()) {
	        FedoraBundleIndex index = new FedoraBundleIndex(new File(location.getPath()));
	        Collection<File> bundlePlugins = index.getAllBundles("osgi.bundle");
	        Collection<File> bundleFeatures = index.getAllBundles("org.eclipse.update.feature");

	        for (File bundleFile : bundlePlugins) {
	            IArtifactKey key = index.getKeyForFile(bundleFile);
	            IInstallableUnit unit = PublisherUtil.createBundleIU(key, bundleFile);
	            P2Utils.setPath(unit, bundleFile);
	            unitCache.add(unit);
	        }

	        if (! bundleFeatures.isEmpty()) {
	            IPublisherInfo info = new PublisherInfo();
	            IPublisherResult result = new PublisherResult();
	            FeaturesAction fAction = new FeaturesAction(bundleFeatures.toArray(new File[0]));
	            fAction.perform(info, result, new NullProgressMonitor());
	            IQueryResult<IInstallableUnit> units = result.query(QueryUtil.createIUAnyQuery(), new NullProgressMonitor());
	            unitCache.addAll(units.toUnmodifiableSet());
	        }
	    }

		for (IInstallableUnit unit : unitCache)
			P2Utils.setSclNamespace(unit, location.getFragment());

	    return unitCache;
	}

	@Override
	public void addInstallableUnits(
			Collection<IInstallableUnit> installableUnits) {
	}

	@Override
	public void addReferences(
			Collection<? extends IRepositoryReference> references) {
	}

	@Override
	public Collection<IRepositoryReference> getReferences() {
		return Collections.emptyList();
	}

	@Override
	public boolean removeInstallableUnits(
			Collection<IInstallableUnit> installableUnits) {
		return false;
	}

	@Override
	public void removeAll() {
	}

	@Override
	public IStatus executeBatch(IRunnableWithProgress runnable,
			IProgressMonitor monitor) {
		return null;
	}

	@Override
	public void compress(IPool<IInstallableUnit> iuPool) {
	}

}

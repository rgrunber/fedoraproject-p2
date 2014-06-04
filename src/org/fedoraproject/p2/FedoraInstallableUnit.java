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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.ICopyright;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IInstallableUnitFragment;
import org.eclipse.equinox.p2.metadata.ILicense;
import org.eclipse.equinox.p2.metadata.IProvidedCapability;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.equinox.p2.metadata.ITouchpointData;
import org.eclipse.equinox.p2.metadata.ITouchpointType;
import org.eclipse.equinox.p2.metadata.IUpdateDescriptor;
import org.eclipse.equinox.p2.metadata.MetadataFactory;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.metadata.expression.IMatchExpression;

public class FedoraInstallableUnit implements IInstallableUnit {

	String id, version;

	public FedoraInstallableUnit(String id, String version){
		this.id = id;
		this.version = version;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public Version getVersion() {
		return Version.create(version);
	}

	@Override
	public int compareTo(IInstallableUnit o) {
		int res = id.compareTo(o.getId());
		if (res == 0) {
			return version.compareTo(o.getVersion().toString());
		}
		return res;
	}

	@Override
	public Collection<IArtifactKey> getArtifacts() {
		return Arrays.asList((IArtifactKey)new FedoraArtifactKey("osgi.bundle", id, getVersion()));
	}

	@Override
	public IMatchExpression<IInstallableUnit> getFilter() {
		return null;
	}

	@Override
	public Collection<IInstallableUnitFragment> getFragments() {
		return null;
	}

	@Override
	public Map<String, String> getProperties() {
		return null;
	}

	@Override
	public String getProperty(String key) {
		return null;
	}

	@Override
	public String getProperty(String key, String locale) {
		return null;
	}

	@Override
	public Collection<IProvidedCapability> getProvidedCapabilities() {
		return Arrays.asList(MetadataFactory.createProvidedCapability(IInstallableUnit.NAMESPACE_IU_ID, id, getVersion()));
	}

	@Override
	public Collection<IRequirement> getRequirements() {
		return Collections.EMPTY_LIST;
	}

	@Override
	public Collection<IRequirement> getMetaRequirements() {
		return Collections.EMPTY_LIST;
	}

	@Override
	public Collection<ITouchpointData> getTouchpointData() {
		return Collections.EMPTY_LIST;
	}

	@Override
	public ITouchpointType getTouchpointType() {
		return MetadataFactory.createTouchpointType("org.eclipse.equinox.p2.osgi", Version.createOSGi(0, 0, 1));
	}

	@Override
	public boolean isResolved() {
		return false;
	}

	@Override
	public boolean isSingleton() {
		return false;
	}

	@Override
	public boolean satisfies(IRequirement candidate) {
		return false;
	}

	@Override
	public IInstallableUnit unresolved() {
		return this;
	}

	@Override
	public IUpdateDescriptor getUpdateDescriptor() {
		return null;
	}

	@Override
	public Collection<ILicense> getLicenses() {
		return null;
	}

	@Override
	public Collection<ILicense> getLicenses(String locale) {
		return null;
	}

	@Override
	public ICopyright getCopyright() {
		return null;
	}

	@Override
	public ICopyright getCopyright(String locale) {
		return null;
	}

}
